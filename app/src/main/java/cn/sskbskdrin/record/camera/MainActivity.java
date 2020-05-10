package cn.sskbskdrin.record.camera;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
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

        @Override
        public void onCameraFrame(byte[] inputFrame, int format, int width, int height) {
            if (isCut.get()) {
                isCut.set(false);
                new MainActivity.CutImage(imageView, width, height).execute(inputFrame);
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
        cameraManager.init(this, view, false);
        cameraManager.setCameraListener(listener);
        imageView = findViewById(R.id.camera_img);

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

    private static class CutImage extends AsyncTask<byte[], Integer, Bitmap> {
        private WeakReference<ImageView> view;
        private int width;
        private int height;

        private CutImage(ImageView view, int width, int height) {
            this.view = new WeakReference<>(view);
            this.width = width;
            this.height = height;
        }

        @Override
        protected Bitmap doInBackground(byte[]... bytes) {
            byte[] src = bytes[0];
            Bitmap bitmap;
            System.out.println("data length=" + src.length);
            byte[] dest = new byte[width * height * 4];

            rotate(src, dest, YUVLib.Format.NV21, 270);

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(dest));
            return bitmap;
        }

        private void rotate(byte[] src, byte[] dest, YUVLib.Format format, int degree) {
            //            YUVLib.toArgb(src, dest, width, height, format, degree);
            //            YUVLib.toAbgr(src, dest, width, height, format, degree);
            YUVLib.toRGBA(src, dest, width, height, format, degree);
            //            YUV.toArgb(src, dest, width, height, format, degree);

            int w = width;
            int h = height;
            if (degree % 180 != 0) {
                width = h;
                height = w;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            ImageView img = view.get();
            if (img != null) {
                img.setImageBitmap(bitmap);
            }
        }
    }
}
