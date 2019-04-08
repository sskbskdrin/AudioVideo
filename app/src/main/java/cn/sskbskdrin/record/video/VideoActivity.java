package cn.sskbskdrin.record.video;

import android.Manifest;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import java.io.IOException;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.record.R;

/**
 * 音视频混合界面
 *
 * @author sskbskdrin
 */
public class VideoActivity extends BaseActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    Camera camera;
    SurfaceHolder surfaceHolder;
    int width = 1920;
    int height = 1080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_media_muxer);

        checkPermission(1001, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);

        findViewById(R.id.startStop).setOnClickListener(view -> {
            if (view.getTag().toString().equalsIgnoreCase("stop")) {
                view.setTag("begin");
                ((TextView) view).setText("开始");
                stop();
                isStart = false;
            } else {
                view.setTag("stop");
                ((TextView) view).setText("停止");
                start();
                isStart = true;
            }
        });

        SurfaceView surfaceView = findViewById(R.id.camera_surface);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.i("MainActivity", "enter surfaceCreated method");
        this.surfaceHolder = surfaceHolder;
        startCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.i("MainActivity", "enter surfaceDestroyed method");
        if (isStart) {
            stop();
        }
        stopCamera();
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (isStart) {
            VideoRecord.addVideoDate(bytes);
        }
    }

    boolean isStart = false;

    void start() {
        VideoRecord.startRecord(width, height);
    }

    void stop() {
        VideoRecord.stop();
    }

    //----------------------- 摄像头操作相关 --------------------------------------

    private void startCamera() {
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        camera.setDisplayOrientation(90);
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        parameters.setRotation(180);

        // 这个宽高的设置必须和后面编解码的设置一样，否则不能正常处理
        parameters.setPreviewSize(width, height);

        try {
            camera.setParameters(parameters);
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(VideoActivity.this);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭摄像头
     */
    private void stopCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera = null;
        }
    }


}