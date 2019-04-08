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
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.log.L;
import cn.sskbskdrin.record.NativeUtils;
import cn.sskbskdrin.record.R;

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
        //        requestWindowFeature(Window.FEATURE_NO_TITLE);
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
            long start = System.currentTimeMillis();
            int[] pixels;
            byte[] temp = bytes[0];
            System.out.println("byte length=" + temp.length);
            //            temp = NativeUtils.nativeRotateNV(temp, width, height, 270);
            temp = rotate(temp, 90);
            //            pixels = NativeUtils.NV21toARGB(temp, height, width);
            //            pixels = NativeUtils.nativeNV21toARGB(temp, width, height);
            pixels = NativeUtils.YV12toARGB(temp, width, height);
            System.out.println("temp length=" + temp.length);
            System.out.println("switch time=" + (System.currentTimeMillis() - start));

            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565);
            //            return BitmapFactory.decodeByteArray(temp, 0, temp.length);
        }

        private byte[] rotate(byte[] bytes, int degree) {
            int w = width;
            int h = height;
            if (degree % 180 != 0) {
                width = h;
                height = w;
            }
            return NativeUtils.rotateYV(bytes, w, h, degree);
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
