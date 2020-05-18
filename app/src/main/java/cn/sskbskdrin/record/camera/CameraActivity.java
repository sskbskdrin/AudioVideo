package cn.sskbskdrin.record.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import java.util.concurrent.atomic.AtomicBoolean;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.lib.face.FaceDetector;
import cn.sskbskdrin.lib.face.FaceLandmarker;
import cn.sskbskdrin.lib.yuv.YUVCache;
import cn.sskbskdrin.record.R;

public class CameraActivity extends BaseActivity {
    private static final String TAG = "MainActivity";

    private CameraManager cameraManager;
    private SurfaceView surfaceView;
    private TextureView textureView;
    private DrawSurface drawView;
    AtomicBoolean isCut = new AtomicBoolean(false);

    private boolean v2;
    private boolean front;
    private boolean texture;
    YUVCache factory = new YUVCache();
    FaceDetector detector;
    FaceLandmarker landmarker;
    int[] faceRect;

    CameraManager.CameraFrameListener listener = new CameraManager.CameraFrameListener() {
        @Override
        public void onCameraFrame(byte[] bytes, byte[] uBytes, byte[] vBytes, int format, int width, int height,
                                  boolean v2) {
            Log.d(TAG, String.format("frame: bytes len=%d %dx%d format=%d", bytes.length, width, height, format));
            int rotate = (cameraManager != null ? cameraManager.getOrientation() : 0) + (front ? 180 : 0);
            if (v2) {
                drawView.send(factory.getBitmap(bytes, uBytes, vBytes, null, width, height, rotate, front));
            } else {
                drawView.send(convert(factory.getBitmap(bytes, width, height, format, rotate, front),
                    Bitmap.Config.RGB_565));
                factory.transformRGBA(bytes, null, width, height, format, rotate, false, false);
                if (detector == null) {
                    detector = new FaceDetector(CameraActivity.this);
                    faceRect = new int[8];
                    landmarker = new FaceLandmarker(CameraActivity.this);
                }
                drawView.drawRect(faceRect, 0xfff00000);
                detector.detect(factory.getRGBA(), height, width, faceRect, 1);
                //                landmarker.mark(factory.getRGBA(), height, width, , faceRect, 1);
            }
        }

        private Bitmap convert(Bitmap bitmap, Bitmap.Config config) {
            Bitmap convertedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), config);
            Canvas canvas = new Canvas(convertedBitmap);
            Paint paint = new Paint();
            //            paint.setColor(Color.BLACK);
            canvas.drawBitmap(bitmap, 0, 0, null);
            return convertedBitmap;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera);
        surfaceView = findViewById(R.id.camera_surface);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i(TAG, "surfaceCreated: ");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.i(TAG, "surfaceDestroyed: ");
            }
        });
        drawView = findViewById(R.id.draw_surface);

        textureView = findViewById(R.id.camera_texture);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.i(TAG, "onSurfaceTextureAvailable: ");
                //                if (cameraManager == null) {
                //                    cameraManager = new CameraManager();
                //                    cameraManager.init(CameraActivity.this, surface, width, height, v2);
                //                    cameraManager.setEnabled(true);
                //                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.i(TAG, "onSurfaceTextureSizeChanged: ");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.i(TAG, "onSurfaceTextureDestroyed: ");
                if (cameraManager != null) {
                    cameraManager.release();
                }
                cameraManager = null;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                Log.i(TAG, "onSurfaceTextureUpdated: ");
            }
        });

        CompoundButton.OnCheckedChangeListener changeListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.getId() == R.id.camera_version) {
                    v2 = isChecked;
                } else if (buttonView.getId() == R.id.camera_id) {
                    front = isChecked;
                } else if (buttonView.getId() == R.id.camera_view_id) {
                    texture = isChecked;
                }
                if (cameraManager != null) {
                    cameraManager.release();
                }
                reset();
                change();
            }
        };

        ((CheckBox) findViewById(R.id.camera_version)).setOnCheckedChangeListener(changeListener);
        ((CheckBox) findViewById(R.id.camera_id)).setOnCheckedChangeListener(changeListener);
        ((CheckBox) findViewById(R.id.camera_view_id)).setOnCheckedChangeListener(changeListener);
    }

    private void reset() {
        surfaceView.getLayoutParams().width = -1;
        surfaceView.getLayoutParams().height = 1440;
        surfaceView.requestLayout();

        textureView.getLayoutParams().width = -1;
        textureView.getLayoutParams().height = -1;
        textureView.requestLayout();
        drawView.postDelayed(new Runnable() {
            @Override
            public void run() {
                change();
            }
        }, 200);
    }

    private void change() {
        if (cameraManager != null) {
            cameraManager.release();
        }
        cameraManager = new CameraManager();
        //        cameraManager.init(this, view, v2);
        if (texture) {
            textureView.setVisibility(View.VISIBLE);
            cameraManager.init(this, textureView, v2);
            surfaceView.setVisibility(View.INVISIBLE);
        } else {
            surfaceView.setVisibility(View.VISIBLE);
            cameraManager.init(this, surfaceView, v2);
            textureView.setVisibility(View.INVISIBLE);
        }
        cameraManager.setMaxFrameSize(640, 480);
        cameraManager.setCameraId(front ? CameraManager.CameraId.FRONT : CameraManager.CameraId.BACK);
        cameraManager.setCameraFrameListener(listener);
        cameraManager.setEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: ");
        //        texture = true;
        //        v2 = true;
        reset();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: ");
        if (cameraManager != null) {
            cameraManager.release();
        }
        cameraManager = null;
    }
}
