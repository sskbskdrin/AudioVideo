package cn.sskbskdrin.record.audio;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import cn.sskbskdrin.base.BaseActivity;
import cn.sskbskdrin.record.FileUtils;
import cn.sskbskdrin.record.R;

/**
 * @author sskbskdrin
 * @date 2019/April/3
 */
public class AudioActivity extends BaseActivity {

    private AudioRecordManager manager = new AudioRecordManager();
    private int channelId = -1;
    private TextView filePathView;
    private OSCView oscView;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        RadioGroup group = findViewById(R.id.audio_radio_group);

        group.setOnCheckedChangeListener((group1, checkedId) -> channelId = checkedId);
        group.check(R.id.audio_16_s);
        filePathView = findViewById(R.id.audio_file_path);
        ((CheckBox) findViewById(R.id.audio_file_check)).setOnCheckedChangeListener((buttonView, isChecked) -> filePathView.setText(isChecked ? FileUtils.getRandomFile(".wav") : ""));

        findViewById(R.id.audio_button).setOnTouchListener((v, event) -> {
            if (event != null) {
                if (MotionEvent.ACTION_DOWN == event.getAction()) {
                    start();
                } else if (MotionEvent.ACTION_UP == event.getAction()) {
                    manager.stop();
                }
            }
            return true;
        });
        oscView = findViewById(R.id.audio_osc);
        findViewById(R.id.audio_play_button).setOnClickListener(v -> {
            File file = new File(filePathView.getText().toString().trim());
            if (!file.exists()) {
                Toast.makeText(mContext, "录音文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            AudioParams params = new AudioTrackManager().playWav(file.getAbsolutePath(),
                (bytes, length) -> oscView.updatePcmData(bytes, length));
            oscView.setParams(params.getChannelCount(), params.getBits());
        });
    }

    private void start() {
        AudioParams.Format format;
        switch (channelId) {
            case R.id.audio_8_s:
                format = AudioParams.Format.SINGLE_8_BIT;
                break;
            case R.id.audio_8_d:
                format = AudioParams.Format.DOUBLE_8_BIT;
                break;
            case R.id.audio_16_s:
                format = AudioParams.Format.SINGLE_16_BIT;
                break;
            case R.id.audio_16_d:
                format = AudioParams.Format.DOUBLE_16_BIT;
                break;
            default:
                format = AudioParams.Format.SINGLE_16_BIT;
                break;
        }
        AudioParams params = new AudioParams(8000, format);
        oscView.setParams(params.getChannelCount(), params.getBits());
        manager.startRecord(filePathView.getText().toString().trim(), params,
            (bytes, len) -> oscView.updatePcmData(bytes, len));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        manager.release();
    }
}
