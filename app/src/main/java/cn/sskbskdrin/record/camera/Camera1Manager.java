package cn.sskbskdrin.record.camera;

import android.graphics.Point;
import android.hardware.Camera;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.sskbskdrin.log.L;
import cn.sskbskdrin.record.camera.manager.CameraConfigurationUtils;

/**
 * @author sskbskdrin
 * @date 2019/April/1
 */
public class Camera1Manager extends BaseCamera implements Camera.PreviewCallback {
    private static final String TAG = "Camera1Manager";

    boolean isPortrait = true;
    private Camera camera;

    private boolean initialized;
    private boolean previewing;

    public Camera1Manager(@NonNull CameraListener listener) {
        super(listener);
    }

    @Override
    protected void openCamera() {
        L.d(TAG, "openCamera: 打开相机");
        int numCameras = Camera.getNumberOfCameras();
        if (numCameras <= 0) {
            Log.w(TAG, "No cameras!");
            return;
        }

        int id = Camera.CameraInfo.CAMERA_FACING_BACK;
        if (mCameraListener != null) {
            id = CameraID.CAMERA_FRONT.equals(mCameraListener.getCameraId()) ? Camera.CameraInfo.CAMERA_FACING_FRONT
                : Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        int index = 0;
        while (index < numCameras) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(index, cameraInfo);
            if (cameraInfo.facing == id) {
                break;
            }
            index++;
        }

        int cameraId = index;

        if (cameraId < numCameras) {
            Log.i(TAG, "Opening camera #" + cameraId);
            camera = Camera.open(cameraId);
        } else {
            Log.w(TAG, "Requested camera does not exist: " + cameraId);
            camera = null;
            return;
        }

        initCamera();
    }

    private void initCamera() {
        SurfaceHolder holder = null;
        if (mCameraListener != null) {
            holder = mCameraListener.getSurfaceHolder();
        }

        Camera.Parameters parameters = camera.getParameters();
        List<Point> list = new ArrayList<>();
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        if (supportedPreviewSizes != null) {
            for (Camera.Size size : supportedPreviewSizes) {
                list.add(new Point(size.width, size.height));
            }
        }
        if (mCameraListener != null) {
            mPreviewSize = mCameraListener.getPreviewSize(list);
        }

        if (!initialized) {
            initialized = true;
            int width = holder.getSurfaceFrame().width();
            int height = holder.getSurfaceFrame().height();
            Point temp = new Point(width, height);
        }

        String parametersFlattened = parameters.flatten(); // Save
        try {
            parameters.setPreviewSize(mPreviewSize.x, mPreviewSize.y);
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            parameters.setPreviewFormat(mPreviewFormat);
            camera.setPreviewDisplay(holder);
            setDesiredCameraParameters(camera, true);
            camera.setParameters(parameters);
            if (frameCallback) {
                camera.setPreviewCallback(this);
            }
            updateOrientation(90);
        } catch (RuntimeException re) {
            Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
            Log.i(TAG, "Resetting to saved camera params: " + parametersFlattened);
            if (parametersFlattened != null) {
                parameters = camera.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    camera.setParameters(parameters);
                    setDesiredCameraParameters(camera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initFromCameraParameters(Camera camera, Point maxPoint) {
        Camera.Parameters parameters = camera.getParameters();
        Point size = new Point(maxPoint.y, maxPoint.x);
        size = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, size);
        mPreviewSize.x = size.x;
        mPreviewSize.y = size.y;
        Log.i(TAG, "Camera resolution: " + mPreviewSize);
    }

    public void updateOrientation(int or) {
        isPortrait = (or == 90 || or == 270);
        stopPreview();
        camera.setDisplayOrientation(or);
        startPreview();
    }

    public boolean isOpen() {
        return camera != null;
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    private void startPreview() {
        Camera theCamera = camera;
        if (theCamera != null && !previewing) {
            theCamera.startPreview();
            previewing = true;
            autoFocus();
        }
    }

    private void autoFocus() {
        if (previewing && mBackgroundHandler != null && camera != null) {
            camera.autoFocus((success, camera) -> mBackgroundHandler.postDelayed(this::autoFocus, 3000L));
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    private void stopPreview() {
        if (camera != null && previewing) {
            camera.stopPreview();
            previewing = false;
        }
    }

    @Override
    protected void closeCamera() {
        L.d(TAG, "closeCamera: ");
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
        previewing = false;
    }

    private void setDesiredCameraParameters(Camera camera, boolean safeMode) {
        Camera.Parameters parameters = camera.getParameters();
        if (parameters == null) {
            Log.w(TAG, "Device error: no camera parameters are available. Proceeding without " + "configuration.");
            return;
        }
        Log.i(TAG, "Initial camera parameters: " + parameters.flatten());
        if (safeMode) {
            Log.w(TAG, "In camera config safe mode -- most settings will not be honored");
        }
        CameraConfigurationUtils.setFocus(parameters, true, true, safeMode);

        parameters.setPreviewSize(mPreviewSize.x, mPreviewSize.y);
        Log.i(TAG, "Final camera parameters: " + parameters.flatten());
        camera.setParameters(parameters);
        Camera.Parameters afterParameters = camera.getParameters();
        Camera.Size afterSize = afterParameters.getPreviewSize();
        if (afterSize != null && (mPreviewSize.x != afterSize.width || mPreviewSize.y != afterSize.height)) {
            Log.w(TAG, "Camera said it supported preview size " + mPreviewSize.x + 'x' + mPreviewSize.y + ", " + "but"
                + " after setting it, preview size is " + afterSize.width + 'x' + afterSize.height);
            mPreviewSize.x = afterSize.width;
            mPreviewSize.y = afterSize.height;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (frameCallback) {
            sendPreviewFrame(data);
        }
    }
}
