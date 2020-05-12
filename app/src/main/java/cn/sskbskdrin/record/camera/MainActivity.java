package cn.sskbskdrin.record.camera;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.record.R;
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

    CameraManager.CameraListener listener = new CameraManager.CameraListener() {
        @Override
        public void onCameraStarted(int width, int height) {
        }

        @Override
        public void onCameraStopped() {

        }

        int count = 0;

        @Override
        public void onCameraFrame(byte[] inputFrame, int format, int width, int height) {
            //            if (isCut.get()) {
            //                isCut.set(false);
            if (++count > 20) {
                drawView.send(new MainActivity.CutImage(width, height, cameraManager.getOrientation()).deal(inputFrame));
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
        cameraManager.init(this, view, false);
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

    private static byte[] dest = {};
    private static byte[] src = {};

    private class CutImage {
        private int width;
        private int height;
        private int mW;
        private int mH;
        private int[] clip;
        private int degree;

        private CutImage(int width, int height, int degree) {
            this.width = width;
            this.height = height;
            clip = new int[]{250, 250, width, height};
            this.mW = clip[2];
            this.mH = clip[3];
            this.degree = degree;
        }

        protected Bitmap deal(byte[] s) {
            if (src.length != s.length) {
                src = new byte[s.length];
            }
            if (dest.length != mW * mH * 4) {
                dest = new byte[mW * mH * 4];
            }

            System.arraycopy(s, 0, src, 0, s.length);
            System.out.println("data length=" + src.length);

            YUVLib.toBGRA(src, dest, width, height, YUVLib.Format.NV21, degree + (front ? 180 : 0), front);
            int w = mW;
            int h = mH;
            if (degree % 180 != 0) {
                mW = h;
                mH = w;
            }
            Bitmap bitmap = Bitmap.createBitmap(mW, mH, Bitmap.Config.ARGB_8888);
            //            System.out.println(Arrays.toString(dest));
            //            int[] colors = new int[width * height];
            //            YUVLib.nativeBGRAToColor(dest, colors, colors.length);
            //            bitmap.setPixels(colors, 0, width, 0, 0, width, height);
            //[r,g,b,a]
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(dest));
            return bitmap;
        }
    }
}
