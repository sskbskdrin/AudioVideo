/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.sskbskdrin.record.camera.manager;

import android.graphics.Point;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {
    private static final String TAG = CameraManager.class.getSimpleName();

    private final CameraConfigurationManager configManager;
    /**
     * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
     * clear the handler so it will only receive one message.
     */
    private final PreviewCallback previewCallback;
    boolean isPortrait = true;
    private Camera camera;
    private AutoFocusManager autoFocusManager;
    private Point surfacePoint;
    private boolean initialized;
    private boolean previewing;
    private int requestedCameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;

    public CameraManager() {
        this.configManager = new CameraConfigurationManager();
        previewCallback = new PreviewCallback(configManager);
    }

    public Point getSurfacePoint() {
        return surfacePoint;
    }

    public void findBestSurfacePoint(Point maxPoint) {
        Point cameraResolution = configManager.getCameraResolution();
        if (cameraResolution == null || maxPoint == null || maxPoint.x == 0 || maxPoint.y == 0) return;
        double scaleX, scaleY, scale;
        if (maxPoint.x < maxPoint.y) {
            scaleX = cameraResolution.x * 1.0f / maxPoint.y;
            scaleY = cameraResolution.y * 1.0f / maxPoint.x;
        } else {
            scaleX = cameraResolution.x * 1.0f / maxPoint.x;
            scaleY = cameraResolution.y * 1.0f / maxPoint.y;
        }
        scale = scaleX > scaleY ? scaleX : scaleY;
        if (maxPoint.x < maxPoint.y) {
            surfacePoint.x = (int) (cameraResolution.y / scale);
            surfacePoint.y = (int) (cameraResolution.x / scale);
        } else {
            surfacePoint.x = (int) (cameraResolution.x / scale);
            surfacePoint.y = (int) (cameraResolution.y / scale);
        }
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames into.
     * @throws IOException Indicates the camera driver failed to open.
     */
    public synchronized void openDriver(SurfaceHolder holder) throws IOException {
        Camera theCamera = camera;
        if (theCamera == null) {
            theCamera = OpenCameraInterface.open(requestedCameraId);
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
            surfacePoint = new Point(width, height);
            configManager.initFromCameraParameters(theCamera, surfacePoint);
            findBestSurfacePoint(surfacePoint);
        }

        Camera.Parameters parameters = theCamera.getParameters();
        String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save
        // these, temporarily
        try {
            configManager.setDesiredCameraParameters(theCamera, false);
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
                    configManager.setDesiredCameraParameters(theCamera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }
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

    /**
     * @param newSetting if {@code true}, light should be turned on if currently off. And vice
     *                   versa.
     */
    public synchronized void setTorch(boolean newSetting) {
        if (newSetting != configManager.getTorchState(camera)) {
            if (camera != null) {
                if (autoFocusManager != null) {
                    autoFocusManager.stop();
                }
                configManager.setTorch(camera, newSetting);
                if (autoFocusManager != null) {
                    autoFocusManager.start();
                }
            }
        }
    }
}
