package cn.sskbskdrin.record.camera

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.widget.Toast
import cn.sskbskdrin.log.L
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


private const val TAG = "Camera2Manager"

private const val OPEN_CAMERA = 1001
private const val CLOSE_CAMERA = 2001

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2Manager(private val context: Context) {
    private val ORIENTATIONS = SparseIntArray(4)

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    /**
     * A reference to the opened [CameraDevice].
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * A reference to the current [CameraCaptureSession] for
     * preview.
     */
    private var mPreviewSession: CameraCaptureSession? = null

    /**
     * The [Size] of camera preview.
     */
    private var mPreviewSize: Size? = null

    /**
     * The [Size] of video recording.
     */
    private var mVideoSize: Size? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    var mBackgroundHandler: Handler? = null
        private set

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    private val mStateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            startPreview()
            mCameraOpenCloseLock.release()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }
    }

    private var listener: CameraListener? = null

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        if (mBackgroundHandler == null) {
            object : HandlerThread("CameraBackground") {
                override fun onLooperPrepared() {
                    mBackgroundHandler = Handler(looper)
                }
            }.start()
        }
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        mBackgroundHandler?.looper?.quitSafely()
        mBackgroundHandler = null
    }

    /**
     * Tries to open a [CameraDevice]. The result is listened by `mStateCallback`.
     */
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        L.d(TAG, "openCamera: 打开相机")
        if (mCameraDevice != null) {
            L.w(TAG, "camera already opened")
            return
        }
        startBackgroundThread()
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            var cameraId = listener?.getCameraId(manager)
            if (cameraId == null) {
                // 默认用后摄像头
                for (name in manager.cameraIdList) {
                    val characteristics = manager.getCameraCharacteristics(name)
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = name
                        break
                    }
                }
            }
            cameraId = cameraId ?: ""

            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: throw RuntimeException("Cannot get available preview/video sizes")
            characteristics.keys.forEach { key ->
                if (key != CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) {
                    L.append(key.name + " ===>" + characteristics.get(key).toString() + "\n")
                }
            }
            L.i(TAG, "end")
            mVideoSize = listener?.getVideoSize(map)
            mPreviewSize = listener?.getPreviewSize(map)

            L.d(TAG, "openCamera: videoSize=$mVideoSize preview=$mPreviewSize")

            manager.openCamera(cameraId, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            L.e("openCamera exception", e)
            Toast.makeText(context, "Cannot access the camera.", Toast.LENGTH_SHORT).show()
        } catch (e: NullPointerException) {
            L.e("openCamera exception", e)
        } catch (e: InterruptedException) {
            L.e("openCamera exception", e)
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }
    }

    private fun closeCamera() {
        L.d(TAG, "closeCamera: 关闭相机")
        try {
            mCameraOpenCloseLock.acquire()
            closePreviewSession()
            mCameraDevice?.close()
            mCameraDevice = null
            stopBackgroundThread()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.")
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Start the camera preview.
     */
    private fun startPreview() {
        L.d(TAG, "startPreview: 开始预览")
        if (null == mCameraDevice || null == mPreviewSize) {
            return
        }
        val device = mCameraDevice!!
        try {
            closePreviewSession()

            val mPreviewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            val list = ArrayList<Surface>(4)
            listener?.getSurfaceList(list)

            list.forEach { surface -> mPreviewBuilder.addTarget(surface) }

            device.createCaptureSession(list, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    mPreviewSession = session
                    updatePreview(mPreviewBuilder)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * 拍照
     */
    private fun takePicture() {
        if (mCameraDevice == null) {
            return
        }
        val captureBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

        if (captureBuilder != null) {
            // 创建拍照需要的CaptureRequest.Builder
            try {
                // 将imageReader的surface作为CaptureRequest.Builder的目标
//                captureBuilder.addTarget(mImageReaderJPG.getSurface())
                // 自动对焦
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                // 自动曝光
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                // 获取手机方向
//                val rotation = getWindowManager().getDefaultDisplay().getRotation()
                // 根据设备方向计算设置照片的方向
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(90))
                mPreviewSession?.capture(captureBuilder.build(), null, mBackgroundHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Update the camera preview. [.startPreview] needs to be called in advance.
     */
    private fun updatePreview(builder: CaptureRequest.Builder) {
        L.d(TAG, "updatePreview: 更新预览视图")
        if (mCameraDevice != null) {
            try {
                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                mPreviewSession?.setRepeatingRequest(builder.build(), null, mBackgroundHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    private fun closePreviewSession() {
        L.d(TAG, "closePreviewSession: ${mPreviewSession != null}")
        mPreviewSession?.close()
        mPreviewSession = null
    }

    fun setCameraListener(listener: CameraListener) {
        this.listener = listener
    }

    fun open() {
        if (mBackgroundHandler == null) {
            object : HandlerThread("CameraBackground") {
                override fun onLooperPrepared() {
                    mBackgroundHandler = BackgroundHandler(this@Camera2Manager)
                    Message.obtain(mBackgroundHandler, OPEN_CAMERA).sendToTarget()
                }
            }.start()
        }
    }

    fun close() {
        if (mBackgroundHandler == null) {
            Message.obtain(mBackgroundHandler, CLOSE_CAMERA).sendToTarget()
        }
    }

    private class BackgroundHandler(manager: Camera2Manager) : Handler() {
        private val mWeakManager: WeakReference<Camera2Manager> = WeakReference(manager)

        override fun handleMessage(msg: Message?) {
            val manager = mWeakManager.get()
            if (manager != null && msg != null) {
                when (msg.what) {
                    OPEN_CAMERA -> manager.openCamera()
                    CLOSE_CAMERA -> {
                        manager.closeCamera()
                        looper.quitSafely()
                    }
                }
            }
        }
    }

    interface CameraListener {
        fun getSurfaceList(list: ArrayList<Surface>)
        fun getVideoSize(map: StreamConfigurationMap): Size
        fun getPreviewSize(map: StreamConfigurationMap): Size
        fun getCameraId(manager: CameraManager): String?
    }

}