package cn.sskbskdrin.record.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

/**
 * Created by keayuan on 2020/4/1.
 *
 * @author keayuan
 */
class CameraImpl extends CameraManager.ICamera implements Camera.PreviewCallback {
    private static final String TAG = "CameraImpl";
    private static final int MAGIC_TEXTURE_ID = 100;

    private byte[] mBuffer;
    private Camera mCamera;

    public static class CameraSizeAccessor implements CameraManager.SizeAccessor {
        @Override
        public int getWidth(Object obj) {
            return ((Camera.Size) obj).width;
        }

        @Override
        public int getHeight(Object obj) {
            return ((Camera.Size) obj).height;
        }
    }

    private boolean openCamera() {
        mCamera = null;
        if (mManager.mCameraIndex == CameraManager.CameraId.ANY) {
            Log.d(TAG, "Trying to open camera with old open()");
            try {
                mCamera = Camera.open();
            } catch (Exception e) {
                Log.e(TAG, "Camera is not available (in use or does not exist): " + e.getLocalizedMessage());
            }

            if (mCamera == null) {
                boolean connected = false;
                for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                    Log.d(TAG, "Trying to open camera with new open(" + camIdx + ")");
                    try {
                        mCamera = Camera.open(camIdx);
                        connected = true;
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Camera #" + camIdx + " failed to open: " + e.getLocalizedMessage());
                    }
                    if (connected) break;
                }
            }
        } else {
            int localCameraIndex = -1;
            if (mManager.mCameraIndex == CameraManager.CameraId.BACK) {
                Log.i(TAG, "Trying to open back camera");
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                    Camera.getCameraInfo(camIdx, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        localCameraIndex = camIdx;
                        break;
                    }
                }
            } else if (mManager.mCameraIndex == CameraManager.CameraId.FRONT) {
                Log.i(TAG, "Trying to open front camera");
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                    Camera.getCameraInfo(camIdx, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        localCameraIndex = camIdx;
                        break;
                    }
                }
            }
            if (localCameraIndex == -1) {
                Log.e(TAG, mManager.mCameraIndex + " camera not found!");
            } else {
                Log.d(TAG, "Trying to open camera with new open(" + localCameraIndex + ")");
                try {
                    mCamera = Camera.open(localCameraIndex);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Camera #" + localCameraIndex + "failed to open: " + e.getLocalizedMessage());
                }
            }
        }
        return mCamera != null;
    }

    @Override
    boolean connectCamera() {
        CameraManager.Size size = getViewSize();
        Log.d(TAG, "Initialize camera w=" + size.width + " h=" + size.height);
        if (size.width == 0 || size.height == 0) {
            return false;
        }
        if (!openCamera()) return false;

        /* Now set camera parameters */
        Camera.Parameters params = mCamera.getParameters();
        Log.d(TAG, "getSupportedPreviewSizes()");
        List<Camera.Size> sizes = params.getSupportedPreviewSizes();

        /* Image format NV21 causes issues in the Android emulators */
        if (Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("unknown") || Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android " + "SDK built for x86") || Build.MANUFACTURER.contains("Genymotion") || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) || "google_sdk".equals(Build.PRODUCT))
            params.setPreviewFormat(ImageFormat.YV12);  // "generic" or "android" = android emulator
        else params.setPreviewFormat(ImageFormat.NV21);
        params.setPreviewFormat(ImageFormat.NV21);

        mPreviewFormat = params.getPreviewFormat();
        if (!Build.MODEL.equals("GT" + "-I9100")) params.setRecordingHint(true);

        /* Select the size that fits surface considering maximum size allowed */
        CameraManager.Size frameSize = mManager.selectCameraFrameSize(sizes, new CameraSizeAccessor(), size.width,
            size.height);
        Log.d(TAG, "Set preview size to " + frameSize.width + "x" + frameSize.height);
        params.setPreviewSize(frameSize.width, frameSize.height);

        List<String> FocusModes = params.getSupportedFocusModes();
        if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        /* set fps*/
        List<int[]> supportedPreviewFpsRange = params.getSupportedPreviewFpsRange();
        int[] fps = supportedPreviewFpsRange.get(supportedPreviewFpsRange.size() - 1);
        params.setPreviewFpsRange(30000, 30000);

        mCamera.setParameters(params);
        params = mCamera.getParameters();

        previewWidth = params.getPreviewSize().width;
        previewHeight = params.getPreviewSize().height;
        return true;
    }

    @Override
    void startPreview() {
        try {
            int bufferSize = previewWidth * previewHeight;
            bufferSize = bufferSize * ImageFormat.getBitsPerPixel(mPreviewFormat) / 8;
            mBuffer = new byte[bufferSize];
            Log.i(TAG, "Orientation: " + surfaceOrientation);
            mCamera.setDisplayOrientation(surfaceOrientation);
            mCamera.addCallbackBuffer(mBuffer);
            mCamera.setPreviewCallbackWithBuffer(this);

            SurfaceView view = getSurfaceView();
            if (view != null) {
                mCamera.setPreviewDisplay(view.getHolder());
            } else {
                if (surfaceTexture != null) {
                    surfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
                }
                mCamera.setPreviewTexture(surfaceTexture);
            }
            Log.d(TAG, "startPreview w=" + previewWidth + " h=" + previewHeight);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    void disconnectCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.addCallbackBuffer(null);
            mCamera.setPreviewCallback(null);
            mCamera.setPreviewCallbackWithBuffer(null);
            try {
                if (surfaceTexture != null) {
                    mCamera.setPreviewTexture(null);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.release();
            mCamera = null;
            mBuffer = null;
        }
    }

    @Override
    public void takePicture(CameraManager manager) {
        Camera.Parameters params = mCamera.getParameters();
        params.setPictureFormat(ImageFormat.JPEG);
        //                    params.setRotation(90);
        List<Camera.Size> pictureSizes = params.getSupportedPictureSizes();
        CameraManager.Size s = mManager.selectCameraFrameSize(pictureSizes, new CameraSizeAccessor(), previewWidth,
            previewHeight);
        params.setPictureSize(s.width, s.height);

        params.setFlashMode(manager.flash ? Camera.Parameters.FLASH_MODE_ON : Camera.Parameters.FLASH_MODE_OFF);
        mCamera.setParameters(params);
        if (mCamera != null) {
            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    mManager.onPicture(data);
                }
            });
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mCamera != null) {
            mCamera.addCallbackBuffer(mBuffer);
        }
        mManager.onPreviewFrame(data);
    }
}
