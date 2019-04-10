package cn.sskbskdrin.record.camera;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.log.L;
import cn.sskbskdrin.record.R;
import cn.sskbskdrin.record.YUVLib;

public class CameraActivity extends BaseActivity implements SurfaceHolder.Callback, BaseCamera.CameraListener {
    private static final String TAG = "MainActivity";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private int width = 1280;
    private int height = 720;
    private ImageView imageView;
    private boolean cut = false;
    private boolean isCamera2 = true;
    private BaseCamera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);

        checkPermission(1001, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);

        surfaceView = findViewById(R.id.camera_surface);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        imageView = findViewById(R.id.camera_img);
        imageView.setOnClickListener(v -> imageView.setImageDrawable(null));
        findViewById(R.id.camera_next).setOnClickListener(v -> cut = true);
        ((CheckBox) findViewById(R.id.camera_check)).setOnCheckedChangeListener((buttonView, isChecked) -> {
            isCamera2 = isChecked;
            reStartCamera();
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        reStartCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        if (camera != null) {
            camera.fixSurfaceView(surfaceView);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        stopCamera();
    }

    private void reStartCamera() {
        stopCamera();
        if (isCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera = new Camera2Manager(this, this);
            } else {
                showToast("不支持Camera2");
                return;
            }
        } else {
            camera = new Camera1Manager(this);
        }
        camera.setCameraListener(this);
        camera.setFrameCallback(true);
        camera.setPreviewFormat(ImageFormat.YV12);
        camera.open();
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
        HashMap<String,Object> map = new HashMap<>();
        map.remove("adb");
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
        surfaceView.post(() -> surfaceHolder.setFixedSize(width, height));
        return new Point(width, height);
    }

    @Override
    public BaseCamera.CameraID getCameraId() {
        return null;
    }

    @Override
    public void onPreviewFrame(byte[] data) {
        if (cut) {
            new CutImage(imageView, width, height).execute(data);
            cut = false;
        }
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

            rotate(src, dest, YUVLib.Format.YV12, 90);

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
