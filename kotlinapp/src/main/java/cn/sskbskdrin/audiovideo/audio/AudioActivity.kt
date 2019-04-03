package cn.sskbskdrin.record.audio

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import cn.sskbskdrin.audiovideo.FileUtils
import cn.sskbskdrin.audiovideo.R
import kotlinx.android.synthetic.main.activity_audio.*
import java.io.File

class AudioActivity : Activity() {

    private val manager = AudioRecordManager()
    private var channelId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)
        audio_radio_group.setOnCheckedChangeListener { _, checkedId -> channelId = checkedId }
        audio_radio_group.check(R.id.audio_16_s)
        audio_file_check.setOnCheckedChangeListener { _, isChecked ->
            audio_file_path.text = if (isChecked) FileUtils.getRandomFile(".wav") else ""
        }
        audio_button.setOnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    start()
                }
                MotionEvent.ACTION_UP -> {
                    manager.stop()
                }
            }
            return@setOnTouchListener true
        }

        audio_play_button.setOnClickListener {
            val file = File(audio_file_path.text.toString())
            if (!file.exists()) {
                Toast.makeText(this@AudioActivity, "录音文件不存在", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val params = AudioTrackManager().playWav(audio_file_path.text.toString()) { bytes, length ->
                osc.updatePcmData(bytes, length)
            }
            osc.setParams(params.getChannelCount(), params.getBits())
        }
    }

    private fun start() {
        val format = when (channelId) {
            R.id.audio_8_s -> Format.SINGLE_8_BIT
            R.id.audio_8_d -> Format.DOUBLE_8_BIT
            R.id.audio_16_s -> Format.SINGLE_16_BIT
            R.id.audio_16_d -> Format.DOUBLE_16_BIT
            else -> Format.SINGLE_16_BIT
        }
        val params = AudioParams(8000, format)
        osc.setParams(params.getChannelCount(), params.getBits())
        manager.startRecord(audio_file_path.text.toString(), params) { bytes, len ->
            osc.updatePcmData(bytes, len)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.release()
    }
}
