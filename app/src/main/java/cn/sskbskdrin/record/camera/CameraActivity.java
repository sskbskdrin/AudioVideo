package cn.sskbskdrin.record.camera;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.log.L;
import cn.sskbskdrin.record.NativeUtils;
import cn.sskbskdrin.record.R;
import cn.sskbskdrin.record.camera.manager.AutoFocusManager;

public class CameraActivity extends BaseActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final String TAG = "MainActivity";

    Camera camera;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;

    int width = 1280;
    int height = 720;
    ImageView imageView;
    boolean cut = false;
    private AutoFocusManager autoFocusManager;

    private Camera2Manager camera2Manager;

    ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 1);

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Camera2Manager.CameraListener listener = new Camera2Manager.CameraListener() {
        @Override
        public void getSurfaceList(@NotNull ArrayList<Surface> list) {
            reader.setOnImageAvailableListener(reader1 -> {
                Image image = reader1.acquireNextImage();
                if (cut) {
                    fill(CameraUtil.getBytesFromImage(image, ImageFormat.NV21));
                    cut = false;
                    L.d(TAG, "image w=${" + image.getWidth() + "} h=${" + image.getHeight() + "}");
                }
                image.close();
            }, camera2Manager.getMBackgroundHandler());
            list.add(surfaceHolder.getSurface());
//            list.add(reader.getSurface());
        }

        @NotNull
        @Override
        public Size getVideoSize(@NotNull StreamConfigurationMap map) {
            Log.d(TAG, Arrays.toString(map.getOutputFormats()));
            Log.d(TAG, Arrays.toString(map.getOutputSizes(ImageFormat.YV12)));
            Log.d(TAG, Arrays.toString(map.getOutputSizes(SurfaceHolder.class)));
            return new Size(width, height);
        }

        @NotNull
        @Override
        public Size getPreviewSize(@NotNull StreamConfigurationMap map) {
            surfaceView.post(() -> surfaceHolder.setFixedSize(width, height));
            return new Size(width, height);
        }

        @Nullable
        @Override
        public String getCameraId(@NotNull CameraManager manager) {
            try {
                return manager.getCameraIdList()[0];
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            return "";
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);

        checkPermission(1001, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);

        surfaceView = findViewById(R.id.surface_view);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        imageView = findViewById(R.id.img);
        findViewById(R.id.next).setOnClickListener(v -> cut = true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        //                startCamera();
        camera2Manager = new Camera2Manager(this);
        camera2Manager.setCameraListener(listener);
        camera2Manager.open();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        //                stopCamera();
        camera2Manager.close();
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (cut) {
            fill(bytes);
            cut = false;
        }
    }

    private void fill(final byte[] bytes) {
        new CutImage(imageView, width, height).execute(bytes);
    }

    private void startCamera() {
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        camera.setDisplayOrientation(90);

        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        L.d(TAG, parameters.getSupportedPreviewFormats().toString());

        // 这个宽高的设置必须和后面编解码的设置一样，否则不能正常处理
        parameters.setPreviewSize(width, height);

        Point size = CameraUtil.findBestSurfacePoint(new Point(width, height),
            new Point(surfaceHolder.getSurfaceFrame().width(), surfaceHolder.getSurfaceFrame().height()));
        surfaceView.getLayoutParams().width = size.x;
        surfaceView.getLayoutParams().height = size.y;
        surfaceView.requestLayout();
        try {
            camera.setParameters(parameters);
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(this);
            camera.startPreview();

            autoFocusManager = new AutoFocusManager(camera);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭摄像头
     */
    private void stopCamera() {
        if (camera != null) {
            autoFocusManager.stop();
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera = null;
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
            pixels = NativeUtils.nativeNV21toARGB(temp, width, height);
            //            pixels = NativeUtils.YV12toARGB(temp, width, height);
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
            return NativeUtils.nativeRotateNV(bytes, w, h, degree);
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
