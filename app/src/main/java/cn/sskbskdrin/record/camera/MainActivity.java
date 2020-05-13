package cn.sskbskdrin.record.camera;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

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

        int count = 0;

        @Override
        public void onCameraFrame(byte[] bytes, byte[] uBytes, byte[] vBytes, int format, int width, int height,
                                  boolean v2) {
            //            if (isCut.get()) {
            //                isCut.set(false);
            if (++count > 10) {
                if (v2) {
                    drawView.send(factory.getBitmap(bytes, uBytes, vBytes, null, width, height,
                        cameraManager.getOrientation()));
                } else {
                    drawView.send(factory.getBitmap(bytes, width, height, format, cameraManager.getOrientation()));
                }
            }
            //            }
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
            (byte) 255, 0, 80, (byte) 255, (byte) 255};
        int[] dest = new int[4];
        YUVLib.nativeBGRAToColor(src, dest, 16);
        for (int i = 0; i < dest.length; i++) {
            Log.d(TAG, "onCreate: " + Integer.toHexString(dest[i]));
        }
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
            cameraManager.setEnabled(false);
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
