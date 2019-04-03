package cn.sskbskdrin.record;

import android.Manifest;
import android.os.Bundle;
import android.view.View;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.log.L;
import cn.sskbskdrin.log.logcat.LogcatPrinter;
import cn.sskbskdrin.log.logcat.PrettyFormat;
import cn.sskbskdrin.record.audio.AudioActivity;
import cn.sskbskdrin.record.camera.CameraActivity;
import cn.sskbskdrin.record.opengl.OpenGlActivity;
import cn.sskbskdrin.record.video.VideoActivity;

public class MainActivity extends BaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        L.addPinter(new LogcatPrinter(new PrettyFormat()));

        checkPermission(1001, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
        getView(R.id.main_camera).setOnClickListener(this);
        getView(R.id.main_audio).setOnClickListener(this);
        getView(R.id.main_video).setOnClickListener(this);
        getView(R.id.main_open_gl).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Class clazz = null;
        switch (v.getId()) {
            case R.id.main_camera:
                clazz = CameraActivity.class;
                break;
            case R.id.main_audio:
                clazz = AudioActivity.class;
                break;
            case R.id.main_video:
                clazz = VideoActivity.class;
                break;
            case R.id.main_open_gl:
                clazz = OpenGlActivity.class;
                break;
            default:
        }
        if (clazz != null) {
            openActivity(clazz);
        }
    }
}
