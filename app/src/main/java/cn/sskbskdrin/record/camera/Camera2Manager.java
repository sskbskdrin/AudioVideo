package cn.sskbskdrin.record.camera;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import cn.sskbskdrin.log.L;

/**
 * @author sskbskdrin
 * @date 2019/April/4
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Manager extends BaseCamera {
    private static final String TAG = "Camera2Manager";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray(4);

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public Camera2Manager(@NonNull Context context, @NonNull CameraListener listener) {
        super(listener);
        this.context = context;
    }

    private Context context;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mPreviewSession;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
            mCameraOpenCloseLock.release();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }
    };

    private ImageReader reader;

    @SuppressLint("MissingPermission")
    @Override
    protected void openCamera() {
        L.d(TAG, "openCamera: 打开相机");
        if (mCameraDevice != null) {
            L.w(TAG, "camera already opened");
            return;
        }
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            int id = CameraCharacteristics.LENS_FACING_BACK;
            if (mCameraListener != null) {
                id = CameraID.CAMERA_FRONT.equals(mCameraListener.getCameraId()) ?
                    CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
            }

            String cameraId = "";
            for (String name : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(name);
                if (Objects.equals(characteristics.get(CameraCharacteristics.LENS_FACING), id)) {
                    cameraId = name;
                    break;
                }
            }

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }

            Size[] sizes = map.getOutputSizes(mPreviewFormat);

            List<Point> list = new ArrayList<>();
            if (sizes != null) {
                for (Size s : sizes) {
                    list.add(new Point(s.getWidth(), s.getHeight()));
                }
            }
            if (mCameraListener != null) {
                mPreviewSize = mCameraListener.getPreviewSize(list);
            }
            L.d(TAG, "openCamera:  preview=" + mPreviewSize);

            for (CameraCharacteristics.Key<?> key : characteristics.getKeys()) {
                if (key != CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) {
                    L.append(key.getName() + " ===>" + characteristics.get(key) + "\n");
                }
            }
            L.i(TAG, "end");

            manager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            L.e("openCamera exception", e);
            Toast.makeText(context, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
        } catch (NullPointerException e) {
            L.e("openCamera exception", e);
        } catch (InterruptedException e) {
            L.e("openCamera exception", e);
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private void startPreview() {
        if (null == mCameraDevice || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();

            CaptureRequest.Builder mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            ArrayList<Surface> list = new ArrayList<>(4);
            if (mCameraListener != null) {
                mCameraListener.getSurfaceList(list);
                SurfaceHolder holder = mCameraListener.getSurfaceHolder();
                if (holder != null) {
                    if (!list.contains(holder.getSurface())) {
                        list.add(holder.getSurface());
                    }
                }
            }

            if (frameCallback) {
                reader = ImageReader.newInstance(mPreviewSize.x, mPreviewSize.y, ImageFormat.YUV_420_888, 1);
                reader.setOnImageAvailableListener(reader1 -> {
                    Image image = reader1.acquireNextImage();
                    sendPreviewFrame(CameraUtil.getBytesFromImage(image, mPreviewFormat));
                    image.close();
                }, mBackgroundHandler);
                list.add(reader.getSurface());
            }

            for (Surface surface : list) {
                mPreviewBuilder.addTarget(surface);
            }

            mCameraDevice.createCaptureSession(list, new CameraCaptureSession.StateCallback() {
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mPreviewSession = session;
                    updatePreview(mPreviewBuilder);
                }

                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview(CaptureRequest.Builder builder) {
        L.d(TAG, "updatePreview: 更新预览视图");
        if (mCameraDevice != null) {
            try {
                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                mPreviewSession.setRepeatingRequest(builder.build(), null, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 拍照
     */
    private void takePicture() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            // 创建拍照需要的CaptureRequest.Builder
            CaptureRequest.Builder captureBuilder =
                mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            //                captureBuilder.addTarget(mImageReaderJPG.getSurface())
            // 自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 获取手机方向
            //                val rotation = getWindowManager().getDefaultDisplay().getRotation()
            // 根据设备方向计算设置照片的方向
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(90));
            mPreviewSession.capture(captureBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
        }
        mPreviewSession = null;
    }

    @Override
    protected void closeCamera() {
        L.d(TAG, "closeCamera: 关闭相机");
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (mCameraDevice != null) {
                mCameraDevice.close();
            }
            mCameraDevice = null;
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }
}
