package cn.sskbskdrin.record.camera;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.lib.face.FaceDetector;
import cn.sskbskdrin.lib.face.FaceLandmarker;
import cn.sskbskdrin.lib.tflite.Recognition;
import cn.sskbskdrin.lib.tflite.TFLiteDetector;
import cn.sskbskdrin.lib.yuv.YUVCache;
import cn.sskbskdrin.lib.yuv.YUVLib;
import cn.sskbskdrin.record.R;

public class CameraActivity extends BaseActivity {
    private static final String TAG = "MainActivity";

    private CameraManager cameraManager;
    private SurfaceView surfaceView;
    private TextureView textureView;
    private DrawSurface drawView;

    private boolean v2;
    private boolean front;
    private boolean texture;
    private boolean face = false;
    private boolean mark = true;
    private boolean tf = true;

    YUVCache factory = new YUVCache();
    FaceDetector detector;
    FaceLandmarker landmarker;
    int[] faceRect;
    double[] points;
    TFLiteDetector tfLiteDetector;
    int[] recognitionRect;
    Bitmap bitmap;

    CameraManager.CameraFrameListener listener = new CameraManager.CameraFrameListener() {
        @Override
        public void onCameraFrame(byte[] bytes, byte[] uBytes, byte[] vBytes, int format, int width, int height,
                                  boolean v2) {
            Log.d(TAG, String.format("frame: bytes len=%d %dx%d format=%d", bytes.length, width, height, format));
            int rotate = (cameraManager != null ? cameraManager.getOrientation() : 0) + (front ? 180 : 0);
            int size = Math.min(width, height);
            int[] clip = new int[]{0, 0, size, size};
            if (v2) {
                factory.transformRGBA(bytes, uBytes, vBytes, clip, width, height, rotate, front, true);
            } else {
                factory.transformRGBA(bytes, clip, width, height, format, rotate, front, true);
            }
            size = 300;
            factory.scaleRGBA(size, size, 0);
            drawView.setSrcFrame(size, size);
            drawView.clean();

            if (bitmap == null) {
                bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            }
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(factory.getRGBA()));
            //            drawView.drawBitmap(bitmap);

            factory.toggleAlpha(true);

            if (face) {
                if (detector == null) {
                    detector = new FaceDetector(CameraActivity.this);
                    faceRect = new int[40];
                    landmarker = new FaceLandmarker(CameraActivity.this);
                    points = new double[100];
                }
                Arrays.fill(points, -1);
                int face = detector.detect(factory.getRGBA(), size, size, faceRect, 10);
                drawView.drawRect(faceRect, 0xffe00000);
                if (mark) {
                    if (face > 0) {
                        landmarker.mark(factory.getRGBA(), size, size, points, faceRect, face);
                    }
                    drawView.drawPoints(points, Color.GREEN);
                }
            }
            if (tf) {
                if (tfLiteDetector == null) {
                    try {
                        //                        tfLiteDetector = TFLiteDetector.create(getAssets(), "mnist.tflite",
                        //                        "label.txt");
                        tfLiteDetector = TFLiteDetector.create(getAssets(), "detect.tflite", "label.txt");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (recognitionRect == null) {
                    recognitionRect = new int[40];
                }
                Arrays.fill(recognitionRect, 0);
                List<Recognition> list = tfLiteDetector.recognize(factory.getRGBA());
                for (int i = 0; i < list.size(); i++) {
                    Recognition res = list.get(i);
                    res.scaleLocation(recognitionRect, i * 4, size);
                    drawView.drawText(res.title, recognitionRect[i * 4] + 2, recognitionRect[i * 4 + 1] + 2);
                }
                drawView.drawRect(recognitionRect, Color.CYAN);
                for (Recognition res : list) {
                    Log.d(TAG, res.toString());
                }
            }
            drawView.end();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera);
        YUVLib.init();
        surfaceView = findViewById(R.id.camera_surface);
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
                } else if (buttonView.getId() == R.id.camera_draw) {
                    drawView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    drawView.clean();
                    return;
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
        ((CheckBox) findViewById(R.id.camera_draw)).setOnCheckedChangeListener(changeListener);
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
