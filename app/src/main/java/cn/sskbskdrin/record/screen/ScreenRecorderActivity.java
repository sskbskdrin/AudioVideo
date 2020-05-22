package cn.sskbskdrin.record.screen;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.record.R;
import cn.sskbskdrin.record.video.VideoRecord;

/**
 * @author sskbskdrin
 * @date 2019/April/23
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenRecorderActivity extends BaseActivity {
    private static final String TAG = "ScreenRecorderActivity";

    VirtualDisplay display;
    SurfaceView surfaceView;
    MediaProjection projection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);
        surfaceView = findViewById(R.id.screen_surface);
        findViewById(R.id.screen_stop).setOnClickListener(v -> {
            VideoRecord.stop();
            if (display != null) {
                display.release();
            }
            if (projection != null) {
                projection.stop();
            }
        });
        MediaProjectionManager mediaProjectionManager =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 10);
        HttpServer server = new HttpServer();
        server.start();
        HttpServer.getFavicon(this);

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRecord() {
        //        VideoRecord.startRecord(720, 1280, true);
        MediaProjectionManager mediaProjectionManager =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        //        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 10);
        projection = mediaProjectionManager.getMediaProjection(RESULT_OK, resultData);
        //
        //        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        ImageReader reader = ImageReader.newInstance(720, 720, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                if (image == null) {
                    return;
                }
                Image.Plane plane = image.getPlanes()[0];
                Log.d(TAG, "onImageAvailable: planes len="+image.getPlanes().length);

                Bitmap bitmap = Bitmap.createBitmap(720,720, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(plane.getBuffer());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                com(bitmap).compress(Bitmap.CompressFormat.JPEG,100,out);
                Log.d(TAG, "onImageAvailable: len="+out.toByteArray().length);
                out.reset();
                out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG,100,out);
                Log.d(TAG, "onImageAvailable: len="+out.toByteArray().length);

                Log.d(TAG, "onImageAvailable: getPixelStride=" + plane.getPixelStride());
                Log.d(TAG, "onImageAvailable: getRowStride=" + plane.getRowStride());
                Log.d(TAG, "onImageAvailable: remaining="+plane.getBuffer().remaining());
                Log.d(TAG, "onImageAvailable: " + image.getWidth() + "," + image.getHeight());
                image.close();
            }
        }, null);
        display = projection.createVirtualDisplay("myScreen", 720, 1280, 1,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY, reader
            .getSurface(), new VirtualDisplay.Callback() {}, null);
    }

    private Bitmap com(Bitmap bitmap){
        Bitmap temp = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(temp);
        canvas.drawBitmap(bitmap,0,0,null);
        return temp;
    }

    Intent resultData;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == 10) {
            resultData = data;
            startRecord();
        }
    }
}
