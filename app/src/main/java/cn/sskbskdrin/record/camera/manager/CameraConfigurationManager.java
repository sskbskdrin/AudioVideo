/*
 * Copyright (C) 2010 ZXing authors
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

/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 */
public final class CameraConfigurationManager {

    private static final String TAG = "CameraConfiguration";

    private Point cameraResolution;

    public CameraConfigurationManager() {
    }

    /**
     * Reads, one time, values from the camera that are needed by the app.
     */
    public void initFromCameraParameters(Camera camera, Point maxPoint) {
        Camera.Parameters parameters = camera.getParameters();
        Point size = new Point(maxPoint.y, maxPoint.x);
        cameraResolution = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, size);
        Log.i(TAG, "Camera resolution: " + cameraResolution);
        Log.i(TAG, "size resolution: " + size);
    }

    public void setDesiredCameraParameters(Camera camera, boolean safeMode) {
        Camera.Parameters parameters = camera.getParameters();
        if (parameters == null) {
            Log.w(TAG, "Device error: no camera parameters are available. Proceeding without " + "configuration.");
            return;
        }
        Log.i(TAG, "Initial camera parameters: " + parameters.flatten());
        if (safeMode) {
            Log.w(TAG, "In camera config safe mode -- most settings will not be honored");
        }
        initializeTorch(parameters, safeMode);
        CameraConfigurationUtils.setFocus(parameters, true, true, safeMode);

        if (!safeMode) {
            CameraConfigurationUtils.setBarcodeSceneMode(parameters);
            CameraConfigurationUtils.setVideoStabilization(parameters);
            CameraConfigurationUtils.setFocusArea(parameters);
            CameraConfigurationUtils.setMetering(parameters);
        }
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

    public Point getCameraResolution() {
        return cameraResolution;
    }

    public boolean getTorchState(Camera camera) {
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            if (parameters != null) {
                String flashMode = parameters.getFlashMode();
                return Camera.Parameters.FLASH_MODE_ON.equals(flashMode) || Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode);
            }
        }
        return false;
    }

    public void setTorch(Camera camera, boolean newSetting) {
        Camera.Parameters parameters = camera.getParameters();
        doSetTorch(parameters, newSetting, false);
        camera.setParameters(parameters);
    }

    private void initializeTorch(Camera.Parameters parameters, boolean safeMode) {
        boolean currentSetting = FrontLightMode.getMode() == FrontLightMode.ON;
        doSetTorch(parameters, currentSetting, safeMode);
    }

    private void doSetTorch(Camera.Parameters parameters, boolean newSetting, boolean safeMode) {
        CameraConfigurationUtils.setTorch(parameters, newSetting);
        if (!safeMode) {
            CameraConfigurationUtils.setBestExposure(parameters, newSetting);
        }
    }

}
