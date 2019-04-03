package cn.sskbskdrin.record.camera;

import android.graphics.Point;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

import cn.sskbskdrin.record.camera.manager.AutoFocusManager;
import cn.sskbskdrin.record.camera.manager.CameraConfigurationUtils;

/**
 * @author sskbskdrin
 * @date 2019/April/1
 */
public class Camera1Manager {
    private static final String TAG = "Camera1Manager";

    boolean isPortrait = true;
    private Camera camera;

    private PreviewCallback previewCallback;
    private AutoFocusManager autoFocusManager;
    private Point surfacePoint;
    private boolean initialized;
    private boolean previewing;
    private int requestedCameraId = -1;
    private final Point cameraResolution;

    public Camera1Manager() {
        cameraResolution = new Point();
        previewCallback = new PreviewCallback(cameraResolution);
    }

    private static Camera open(int cameraId) {
        int numCameras = Camera.getNumberOfCameras();
        if (numCameras == 0) {
            Log.w(TAG, "No cameras!");
            return null;
        }

        boolean explicitRequest = cameraId >= 0;

        if (!explicitRequest) {
            // Select a camera if no explicit camera requested
            int index = 0;
            while (index < numCameras) {
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(index, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    break;
                }
                index++;
            }

            cameraId = index;
        }

        Camera camera;
        if (cameraId < numCameras) {
            Log.i(TAG, "Opening camera #" + cameraId);
            camera = Camera.open(cameraId);
        } else {
            if (explicitRequest) {
                Log.w(TAG, "Requested camera does not exist: " + cameraId);
                camera = null;
            } else {
                Log.i(TAG, "No camera facing back; returning camera #0");
                camera = Camera.open(0);
            }
        }
        return camera;
    }

    public synchronized void openDriver(SurfaceHolder holder) throws IOException {
        Camera theCamera = camera;
        if (theCamera == null) {
            theCamera = open(requestedCameraId);
            if (theCamera == null) {
                throw new IOException();
            }
            camera = theCamera;
        }
        theCamera.setPreviewDisplay(holder);
        if (!initialized) {
            initialized = true;
            int width = holder.getSurfaceFrame().width();
            int height = holder.getSurfaceFrame().height();
            Point temp = new Point(width, height);
            initFromCameraParameters(theCamera, temp);
            surfacePoint = findBestSurfacePoint(cameraResolution, temp);
        }

        Camera.Parameters parameters = theCamera.getParameters();
        String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save
        // these, temporarily
        try {
            setDesiredCameraParameters(theCamera, true);
            camera.setPreviewCallback(previewCallback);
        } catch (RuntimeException re) {
            // Driver failed
            Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
            Log.i(TAG, "Resetting to saved camera params: " + parametersFlattened);
            // Reset:
            if (parametersFlattened != null) {
                parameters = theCamera.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    theCamera.setParameters(parameters);
                    setDesiredCameraParameters(theCamera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }
    }

    private void initFromCameraParameters(Camera camera, Point maxPoint) {
        Camera.Parameters parameters = camera.getParameters();
        Point size = new Point(maxPoint.y, maxPoint.x);
        size = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, size);
        cameraResolution.x = size.x;
        cameraResolution.y = size.y;
        Log.i(TAG, "Camera resolution: " + cameraResolution);
    }

    public synchronized void updateOrientation(int or) {
        isPortrait = (or == 90 || or == 270);
        stopPreview();
        camera.setDisplayOrientation(or);
        startPreview();
    }

    public synchronized boolean isOpen() {
        return camera != null;
    }

    /**
     * Closes the camera driver if still in use.
     */
    public synchronized void closeDriver() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    public synchronized void startPreview() {
        Camera theCamera = camera;
        if (theCamera != null && !previewing) {
            theCamera.startPreview();
            previewing = true;
            autoFocusManager = new AutoFocusManager(camera);
        }
    }

    public Point getSurfacePoint() {
        return surfacePoint;
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    public synchronized void stopPreview() {
        if (autoFocusManager != null) {
            autoFocusManager.stop();
            autoFocusManager = null;
        }
        if (camera != null && previewing) {
            camera.stopPreview();
            previewCallback.setHandler(null, 0);
            previewing = false;
        }
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

        parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
        Log.i(TAG, "Final camera parameters: " + parameters.flatten());
        camera.setParameters(parameters);
        Camera.Parameters afterParameters = camera.getParameters();
        Camera.Size afterSize = afterParameters.getPreviewSize();
        if (afterSize != null && (cameraResolution.x != afterSize.width || cameraResolution.y != afterSize.height)) {
            Log.w(TAG, "Camera said it supported preview size " + cameraResolution.x + 'x' + cameraResolution.y + ", "
                + "but after setting it, preview size is " + afterSize.width + 'x' + afterSize.height);
            cameraResolution.x = afterSize.width;
            cameraResolution.y = afterSize.height;
        }
    }

    private static Point findBestSurfacePoint(Point cameraResolution, Point maxPoint) {
        if (cameraResolution == null || maxPoint == null || maxPoint.x == 0 || maxPoint.y == 0) {
            return maxPoint;
        }
        double scaleX, scaleY, scale;
        if (maxPoint.x < maxPoint.y) {
            scaleX = cameraResolution.x * 1.0f / maxPoint.y;
            scaleY = cameraResolution.y * 1.0f / maxPoint.x;
        } else {
            scaleX = cameraResolution.x * 1.0f / maxPoint.x;
            scaleY = cameraResolution.y * 1.0f / maxPoint.y;
        }
        scale = scaleX > scaleY ? scaleX : scaleY;
        Point result = new Point();
        if (maxPoint.x < maxPoint.y) {
            result.x = (int) (cameraResolution.y / scale);
            result.y = (int) (cameraResolution.x / scale);
        } else {
            result.x = (int) (cameraResolution.x / scale);
            result.y = (int) (cameraResolution.y / scale);
        }
        return result;
    }

    public static final class PreviewCallback implements Camera.PreviewCallback {

        private Point cameraResolution;
        private Handler previewHandler;
        private int previewMessage;

        public PreviewCallback(Point p) {
            cameraResolution = p;
        }

        public void setHandler(Handler previewHandler, int previewMessage) {
            this.previewHandler = previewHandler;
            this.previewMessage = previewMessage;
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Handler thePreviewHandler = previewHandler;
            if (cameraResolution != null && thePreviewHandler != null) {
                Message message = thePreviewHandler.obtainMessage(previewMessage, cameraResolution.x,
                    cameraResolution.y, data);
                message.sendToTarget();
                previewHandler = null;
            } else {
                Log.d(TAG, "Got preview callback, but no handler or resolution available");
            }
        }

    }

}
