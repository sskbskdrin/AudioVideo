package cn.sskbskdrin.record.screen;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.record.R;
import cn.sskbskdrin.record.video.VideoRecord;

/**
 * @author sskbskdrin
 * @date 2019/April/23
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenRecorderActivity extends BaseActivity {

    VirtualDisplay display;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);
        findViewById(R.id.screen_stop).setOnClickListener(v -> {
            VideoRecord.stop();
            if (display != null) {
                display.release();
            }
        });
        MediaProjectionManager mediaProjectionManager =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 10);

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRecord() {
        VideoRecord.startRecord(720, 1280, true);
        MediaProjectionManager mediaProjectionManager =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        //        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 10);
        MediaProjection projection = mediaProjectionManager.getMediaProjection(RESULT_OK, resultData);
        //
        //        DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        display = projection.createVirtualDisplay("myScreen", 720, 1280, 1,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, VideoRecord.getVideoSurface(), new VirtualDisplay.Callback() {
            @Override
            public void onPaused() {
                super.onPaused();
            }

            @Override
            public void onResumed() {
                super.onResumed();
            }

            @Override
            public void onStopped() {
                super.onStopped();
            }
        }, null);
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
