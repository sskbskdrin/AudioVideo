package cn.sskbskdrin.record.audio

import android.media.AudioManager
import android.media.AudioTrack
import java.io.RandomAccessFile
import kotlin.concurrent.thread


/**
 * @author sskbskdrin
 * @date 2019/March/23
 */

fun readWavHeader(file: RandomAccessFile): AudioParams {
    file.seek(22)
    val channelCount = file.readByte()
    file.seek(24)
    var byte = file.readByte().toInt() and 0xff
    var sampleRate = byte
    byte = file.readByte().toInt() and 0xff
    sampleRate = byte shl 8 or sampleRate
    byte = file.readByte().toInt() and 0xff
    sampleRate = byte shl 16 or sampleRate
    byte = file.readByte().toInt() and 0xff
    sampleRate = byte shl 24 or sampleRate

    file.seek(34)
    val bits = file.readByte()
    return AudioParams(sampleRate, channelCount.toInt(), bits.toInt())
}

class AudioTrackManager {
    private var audioTrack: AudioTrack? = null

    private var playing = false

    fun playWav(filepath: String, block: (data: ByteArray, length: Int) -> Unit = { _, _ ->
    }): AudioParams {
        val file = RandomAccessFile(filepath, "r")
        val params = readWavHeader(file)
        val simpleRate = params.simpleRate
        val channelConfig = params.getOutChannelConfig()
        val audioFormat = params.getEncodingFormat()
        val minBufSize = AudioTrack.getMinBufferSize(simpleRate, channelConfig, audioFormat)
        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC,
                simpleRate,
                channelConfig,
                audioFormat,
                minBufSize,
                AudioTrack.MODE_STREAM)
        thread(start = true) {
            playing = true
            audioTrack?.play()
            val buffer = ByteArray(minBufSize)
            file.seek(44)
            while (playing) {
                val read = file.read(buffer)
                if (read > 0) {
                    audioTrack?.write(buffer, 0, read)
                } else {
                    file.close()
                    playing = false
                    audioTrack?.stop()
                }
                block(buffer, read)
            }
        }
        return params
    }
}