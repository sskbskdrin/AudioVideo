package cn.sskbskdrin.record.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by keayuan on 2020/5/9.
 *
 * @author keayuan
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2Impl extends CameraManager.ICamera {
    private static final String TAG = "Camera2Impl";
    private WeakReference<Context> mContext;

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private ImageReader mImageReader;
    private Range<Integer> mFpsRange;
    private String mCameraID;

    public static class CameraSizeAccessor implements CameraManager.SizeAccessor {
        @Override
        public int getWidth(Object obj) {
            return ((Size) obj).getWidth();
        }

        @Override
        public int getHeight(Object obj) {
            return ((Size) obj).getHeight();
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            //            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    public boolean isShowView() {
        return false;
    }

    Camera2Impl(Context context) {
        mContext = new WeakReference<>(context);
        mPreviewFormat = ImageFormat.YV12;
    }

    @Override
    public boolean connectCamera() {
        CameraManager.Size size = getViewSize();
        Log.d(TAG, "connectCamera camera w=" + size.width + " h=" + size.height);
        if (!openCamera()) return false;
        try {
            android.hardware.camera2.CameraManager manager = (android.hardware.camera2.CameraManager) mContext.get()
                .getSystemService(Context.CAMERA_SERVICE);

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Log.d(TAG, "connectCamera: support format="+Arrays.toString(map.getOutputFormats()));
            Size[] sizes = map.getOutputSizes(ImageReader.class);

            CameraManager.Size previewSize = mManager.selectCameraFrameSize(Arrays.asList(sizes),
                new CameraSizeAccessor(), size.width, size.height);

            previewWidth = previewSize.width;
            previewHeight = previewSize.height;
            Log.d(TAG, "Set preview size to " + previewWidth + "x" + previewHeight);

            Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            for (Range<Integer> range : ranges) {
                Log.d(TAG, "connectCamera: range=" + range.getLower() + "-" + range.getUpper());
                if (range.getLower() == 30 && range.getUpper() == 30) {
                    mFpsRange = range;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Interrupted while setCameraPreviewSize.", e);
        }
        return true;
    }

    @Override
    void startPreview() {
        if (mCameraDevice != null) {
            createCameraPreviewSession();
        }
    }

    private boolean openCamera() {
        android.hardware.camera2.CameraManager manager = (android.hardware.camera2.CameraManager) mContext.get()
            .getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] camList = manager.getCameraIdList();
            if (camList.length == 0) {
                Log.e(TAG, "Error: camera isn't detected.");
                return false;
            }
            if (mManager.mCameraIndex == CameraManager.CameraId.ANY) {
                mCameraID = camList[0];
            } else {
                for (String cameraID : camList) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
                    int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if ((mManager.mCameraIndex == CameraManager.CameraId.BACK && facing == CameraCharacteristics.LENS_FACING_BACK) || (mManager.mCameraIndex == CameraManager.CameraId.FRONT && facing == CameraCharacteristics.LENS_FACING_FRONT)) {
                        mCameraID = cameraID;
                        break;
                    }
                }
            }
            if (mCameraID != null) {
                Log.i(TAG, "Opening camera: " + mCameraID);
                manager.openCamera(mCameraID, mStateCallback, mManager.getWorkHandler());
                return true;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "OpenCamera - Camera Access Exception", e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "OpenCamera - Illegal Argument Exception", e);
        } catch (SecurityException e) {
            Log.e(TAG, "OpenCamera - Security Exception", e);
        }
        return false;
    }

    private void createCameraPreviewSession() {
        final int w = getViewSize().width, h = getViewSize().height;
        Log.i(TAG, "createCameraPreviewSession(" + w + "x" + h + ")");
        if (w <= 0 || h <= 0) return;
        try {
            if (null == mCameraDevice) {
                Log.e(TAG, "createCameraPreviewSession: camera isn't opened");
                return;
            }
            if (null != mCaptureSession) {
                Log.e(TAG, "createCameraPreviewSession: mCaptureSession is already started");
                return;
            }

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            List<Surface> list = new ArrayList<>();

            if (mManager.mFrameListener != null) {
                mImageReader = ImageReader.newInstance(previewWidth, previewHeight, ImageFormat.YUV_420_888, 2);
                mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireLatestImage();
                        if (image == null) return;
                        getBytesFromImage(image);
                        image.close();
                    }
                }, mManager.getWorkHandler());
                Surface surface = mImageReader.getSurface();

                mPreviewRequestBuilder.addTarget(surface);
                list.add(surface);
            }

            Surface surface = getSurface();
            if (surface != null) {
                mPreviewRequestBuilder.addTarget(surface);
                list.add(surface);
            }

            mCameraDevice.createCaptureSession(list, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    Log.i(TAG, "createCaptureSession::onConfigured");
                    if (null == mCameraDevice) {
                        return; // camera is already closed
                    }
                    mCaptureSession = cameraCaptureSession;
                    try {
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mFpsRange);

                        //                        mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, clip);

                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null,
                            mManager.getWorkHandler());
                        Log.i(TAG, "CameraPreviewSession has been started");
                    } catch (Exception e) {
                        Log.e(TAG, "createCaptureSession failed", e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "createCameraPreviewSession failed");
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCameraPreviewSession", e);
        }
    }

    @Override
    public void disconnectCamera() {
        Log.d(TAG, "closeCamera: 关闭相机");
        if (mCaptureSession != null) {
            mCaptureSession.close();
        }
        mCaptureSession = null;
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
        mCameraDevice = null;
    }

    @Override
    public void takePicture(CameraManager manager) {

    }

    private void getBytesFromImage(Image image) {
        //获取源数据，如果是YUV格式的数据planes.length = 3
        //plane[i]里面的实际数据可能存在byte[].length <= capacity (缓冲区总大小)
        final Image.Plane[] planes = image.getPlanes();
        //数据有效宽度，一般的，图片width <= rowStride，这也是导致byte[].length <= capacity的原因
        // 所以我们只取width部分
        int width = image.getWidth();
        int height = image.getHeight();
        //        Log.d(TAG, "getBytesFromImage: planes len=" + planes.length + " " + width + "x" + height);

        //此处用来装填最终的YUV数据，需要1.5倍的图片大小，因为Y U V 比例为 4:1:1
        //        byte[] yuvBytes = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] yuvBytes = new byte[width * height];
        //目标数组的装填到的位置
        int dstIndex = 0;

        //临时存储uv数据的
        byte[] uBytes = new byte[width * height / 4];
        byte[] vBytes = new byte[width * height / 4];
        int uIndex = 0;
        int vIndex = 0;

        int pixelsStride, rowStride;
        for (int i = 0; i < planes.length; i++) {
            pixelsStride = planes[i].getPixelStride();
            rowStride = planes[i].getRowStride();

            ByteBuffer buffer = planes[i].getBuffer();

            //如果pixelsStride==2，一般的Y的buffer长度=640*480，UV的长度=640*480/2-1
            //源数据的索引，y的数据是byte中连续的，u的数据是v向左移以为生成的，两者都是偶数位为有效数据
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            //            Log.d(TAG, "getBytesFromImage: " + i + " bytes len=" + bytes.length);

            int srcIndex = 0;
            if (i == 0) {
                //直接取出来所有Y的有效区域，也可以存储成一个临时的bytes，到下一步再copy
                for (int j = 0; j < height; j++) {
                    System.arraycopy(bytes, srcIndex, yuvBytes, dstIndex, width);
                    srcIndex += rowStride;
                    dstIndex += width;
                }
            } else if (i == 1) {
                //根据pixelsStride取相应的数据
                for (int j = 0; j < height / 2; j++) {
                    for (int k = 0; k < width / 2; k++) {
                        uBytes[uIndex++] = bytes[srcIndex];
                        srcIndex += pixelsStride;
                    }
                    if (pixelsStride == 2) {
                        srcIndex += rowStride - width;
                    } else if (pixelsStride == 1) {
                        srcIndex += rowStride - width / 2;
                    }
                }
            } else if (i == 2) {
                //根据pixelsStride取相应的数据
                for (int j = 0; j < height / 2; j++) {
                    for (int k = 0; k < width / 2; k++) {
                        vBytes[vIndex++] = bytes[srcIndex];
                        srcIndex += pixelsStride;
                    }
                    if (pixelsStride == 2) {
                        srcIndex += rowStride - width;
                    } else if (pixelsStride == 1) {
                        srcIndex += rowStride - width / 2;
                    }
                }
            }
        }
        if (mManager != null) {
            mManager.onPreviewFrame(yuvBytes, uBytes, vBytes);
        }

        //根据要求的结果类型进行填充
        /*
        switch (format) {
            case ImageFormat.YUV_420_888:
                System.arraycopy(uBytes, 0, yuvBytes, dstIndex, uBytes.length);
                System.arraycopy(vBytes, 0, yuvBytes, dstIndex + uBytes.length, vBytes.length);
                break;
            case ImageFormat.YV12:
                System.arraycopy(vBytes, 0, yuvBytes, dstIndex, vBytes.length);
                System.arraycopy(uBytes, 0, yuvBytes, dstIndex + vBytes.length, uBytes.length);
                break;
            case ImageFormat.NV21:
                for (int i = 0; i < vBytes.length; i++) {
                    yuvBytes[dstIndex++] = vBytes[i];
                    yuvBytes[dstIndex++] = uBytes[i];
                }
                break;
            default:
                for (int i = 0; i < vBytes.length; i++) {
                    yuvBytes[dstIndex++] = uBytes[i];
                    yuvBytes[dstIndex++] = vBytes[i];
                }
                break;
        }
        */
    }
}
