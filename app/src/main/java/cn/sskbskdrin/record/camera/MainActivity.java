package cn.sskbskdrin.record.camera;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.record.R;
import cn.sskbskdrin.record.YUVFactory;
import cn.sskbskdrin.record.YUVLib;

public class MainActivity extends BaseActivity implements SurfaceHolder.Callback {
    private static final String TAG = "MainActivity";

    private CameraManager cameraManager = new CameraManager();
    private ImageView imageView;
    private SurfaceView view;
    private DrawSurface drawView;
    AtomicBoolean isCut = new AtomicBoolean(false);

    private boolean v2;
    private boolean front;
    YUVFactory factory = new YUVFactory();

    CameraManager.CameraListener listener = new CameraManager.CameraListener() {
        @Override
        public void onCameraStarted(int width, int height) {
        }

        @Override
        public void onCameraStopped() {

        }

        @Override
        public void onCameraFrame(byte[] bytes, byte[] uBytes, byte[] vBytes, int format, int width, int height,
                                  boolean v2) {
            int rotate = cameraManager != null ? cameraManager.getOrientation() : 0;
            if (v2) {
                drawView.send(factory.getBitmap(bytes, uBytes, vBytes, new int[]{205, 205, 300, 400}, width, height,
                    rotate + (front ? 180 : 0), front));
            } else {
                drawView.send(factory.getBitmap(bytes, width, height, format, rotate + (front ? 180 : 0), front));
            }
        }

        @Override
        public void onCameraError() {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        view = findViewById(R.id.camera_surface);
        cameraManager.init(this, view, true);
        cameraManager.setCameraListener(listener);
        imageView = findViewById(R.id.camera_img);
        drawView = findViewById(R.id.draw_surface);

        ((CheckBox) findViewById(R.id.camera_check)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                v2 = isChecked;
                change();
            }
        });

        ((CheckBox) findViewById(R.id.camera_id)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                front = isChecked;
                change();
            }
        });

        view.getHolder().addCallback(this);
        findViewById(R.id.camera_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isCut.set(true);
            }
        });

        byte[] src = new byte[]{0, 80, (byte) 255, (byte) 255, 0, 80, (byte) 255, (byte) 255, 0, 80, (byte) 255,
            (byte) 255, 0, 80, (byte) 255, (byte) 200};
        src = new byte[]{0, 60, 80, 1, 61, 81, 2, 62, 82, 3, 63, 83};
        byte[] r = new byte[4];
        byte[] g = new byte[4];
        byte[] b = new byte[4];
        byte[] a = new byte[4];
        YUVLib.splitRGBA(src, r, g, b, false);
        Log.d(TAG, "onCreate: r=" + Arrays.toString(r));
        Log.d(TAG, "onCreate: g=" + Arrays.toString(g));
        Log.d(TAG, "onCreate: b=" + Arrays.toString(b));
        Log.d(TAG, "onCreate: a=" + Arrays.toString(a));
    }

    private void change() {
        cameraManager.release();
        cameraManager = null;

        cameraManager = new CameraManager();
        cameraManager.init(MainActivity.this, view, v2);
        cameraManager.setCameraId(front ? CameraManager.CameraId.FRONT : CameraManager.CameraId.BACK);
        cameraManager.setCameraListener(listener);
        cameraManager.setEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: ");
        //        cameraManager.setEnabled(true);
        //        view.getHolder().setFixedSize(view.getMeasuredWidth(), view.getMeasuredHeight());
        //        view.getHolder().setFixedSize(cameraManager.getPreviewWidth(), cameraManager.getPreviewHeight());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraManager != null) {
            //            cameraManager.setEnabled(false);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (cameraManager == null) {
            cameraManager = new CameraManager();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (cameraManager != null) {
            cameraManager.setEnabled(true);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (cameraManager != null) {
            cameraManager.release();
        }
        cameraManager = null;
    }

}
