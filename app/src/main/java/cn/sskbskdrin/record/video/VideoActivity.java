package cn.sskbskdrin.record.video;

import android.Manifest;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.log.L;
import cn.sskbskdrin.record.R;
import cn.sskbskdrin.record.YUV;
import cn.sskbskdrin.record.camera.BaseCamera;
import cn.sskbskdrin.record.camera.Camera1Manager;
import cn.sskbskdrin.record.camera.manager.CameraConfigurationUtils;

/**
 * 音视频混合界面
 *
 * @author sskbskdrin
 */
public class VideoActivity extends BaseActivity implements SurfaceHolder.Callback, BaseCamera.CameraListener {
    private static final String TAG = "VideoActivity";

    Camera1Manager camera;
    SurfaceHolder surfaceHolder;
    int width = 1080;
    int height = 1920;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

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

    boolean isStart = false;

    void start() {
        VideoRecord.startRecord(1080, 1920);
    }

    void stop() {
        VideoRecord.stop();
    }

    //----------------------- 摄像头操作相关 --------------------------------------

    private void startCamera() {
        camera = new Camera1Manager(this);
        camera.setFrameCallback(true);
        camera.setPreviewFormat(ImageFormat.NV21);
        camera.open();
        if (!thread.isAlive()) {
            thread.start();
        }
    }

    /**
     * 关闭摄像头
     */
    private void stopCamera() {
        if (camera != null) {
            camera.close();
            camera = null;
        }
    }


    @Override
    public void getSurfaceList(ArrayList<Surface> list) {

    }

    @Override
    public SurfaceHolder getSurfaceHolder() {
        return surfaceHolder;
    }

    @Override
    public Point getPreviewSize(List<Point> list) {
        L.append("support preview=");
        for (Point p : list) {
            L.append(p.x + "x" + p.y + ",");
        }
        L.i(TAG, "");
        findViewById(R.id.camera_surface).post(() -> surfaceHolder.setFixedSize(1920, 1080));
        return new Point(1920, 1080);
    }

    @Override
    public BaseCamera.CameraID getCameraId() {
        return null;
    }

    @Override
    public void onPreviewFrame(final byte[] data) {
        if (isStart) {
            if (complate && sHandler != null) {
                complate = false;
                sHandler.post(() -> {
                    byte[] temp = YUV.rotateNV(data, height, width, 90);
                    temp = YUV.NV21toNV12(temp, width, height);
                    VideoRecord.addVideoDate(temp);
                    complate = true;
                });
            }
        }
    }

    Handler sHandler = null;
    boolean complate = true;
    HandlerThread thread = new HandlerThread("decode frame") {
        @Override
        protected void onLooperPrepared() {
            sHandler = new Handler(getLooper());
        }
    };

}