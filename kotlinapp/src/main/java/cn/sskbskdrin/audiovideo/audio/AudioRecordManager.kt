package cn.sskbskdrin.record.audio

import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

fun getWaveFileHeader(totalDataLen: Long, sampleRate: Int, channelCount: Int, bits: Int): ByteArray {
    val header = ByteArray(44)
    // RIFF/WAVE header
    header[0] = 'R'.toByte()
    header[1] = 'I'.toByte()
    header[2] = 'F'.toByte()
    header[3] = 'F'.toByte()

    val fileLength = totalDataLen + 36
    header[4] = (fileLength and 0xff).toByte()
    header[5] = (fileLength shr 8 and 0xff).toByte()
    header[6] = (fileLength shr 16 and 0xff).toByte()
    header[7] = (fileLength shr 24 and 0xff).toByte()
    //WAVE
    header[8] = 'W'.toByte()
    header[9] = 'A'.toByte()
    header[10] = 'V'.toByte()
    header[11] = 'E'.toByte()
    // 'fmt ' chunk
    header[12] = 'f'.toByte()
    header[13] = 'm'.toByte()
    header[14] = 't'.toByte()
    header[15] = ' '.toByte()
    // 4 bytes: size of 'fmt ' chunk
    header[16] = 16
    header[17] = 0
    header[18] = 0
    header[19] = 0

    // pcm format = 1
    header[20] = 1
    header[21] = 0
    header[22] = channelCount.toByte()
    header[23] = 0

    header[24] = (sampleRate and 0xff).toByte()
    header[25] = (sampleRate shr 8 and 0xff).toByte()
    header[26] = (sampleRate shr 16 and 0xff).toByte()
    header[27] = (sampleRate shr 24 and 0xff).toByte()

    val byteRate = sampleRate * bits * channelCount / 8
    header[28] = (byteRate and 0xff).toByte()
    header[29] = (byteRate shr 8 and 0xff).toByte()
    header[30] = (byteRate shr 16 and 0xff).toByte()
    header[31] = (byteRate shr 24 and 0xff).toByte()
    // block align
    header[32] = (channelCount * bits / 8).toByte()
    header[33] = 0
    // bits per sample
    header[34] = bits.toByte()
    header[35] = 0
    //data
    header[36] = 'd'.toByte()
    header[37] = 'a'.toByte()
    header[38] = 't'.toByte()
    header[39] = 'a'.toByte()
    header[40] = (totalDataLen and 0xff).toByte()
    header[41] = (totalDataLen shr 8 and 0xff).toByte()
    header[42] = (totalDataLen shr 16 and 0xff).toByte()
    header[43] = (totalDataLen shr 24 and 0xff).toByte()
    return header
}

class AudioRecordManager {

    private val DEFAULT_FORMAT = AudioParams(8000, 1, 16)

    private var record: AudioRecord? = null

    private var recordThread: Thread? = null

    private var recording = false

    private var file: RandomAccessFile? = null

    fun startRecord(filePath: String? = null, params: AudioParams = DEFAULT_FORMAT, block: (bytes: ByteArray, len: Int) -> Unit) {

        val channelCount = params.getChannelCount()
        val bits = params.getBits()

        val storeFile = filePath != null && filePath.isNotEmpty()
        startRecord(params) { bytes, len ->
            if (storeFile) {
                if (file == null) {
                    val f = File(filePath)
                    if (f.exists()) {
                        f.delete()
                    }
                    file = RandomAccessFile(f, "rw")
                    file!!.write(getWaveFileHeader(0, params.simpleRate, channelCount, bits))
                }
                if (len > 0) {
                    file?.write(bytes, 0, len)
                } else {
                    val length = file!!.length()

                    file?.seek(0)
                    file?.write(getWaveFileHeader(length, params.simpleRate, channelCount, bits))

                    file?.close()
                }
            }
            block(bytes, len)
        }
    }

    fun startRecord(params: AudioParams, block: (bytes: ByteArray, len: Int) -> Unit) {
        val simpleRate = params.simpleRate
        val channelConfig = params.getChannelConfig()
        val audioFormat = params.getEncodingFormat()
        val bufferSize = AudioRecord.getMinBufferSize(simpleRate, channelConfig, audioFormat)
        record = AudioRecord(MediaRecorder.AudioSource.MIC, simpleRate, channelConfig, audioFormat, bufferSize)
        recordThread = thread {
            record?.startRecording()
            recording = true
            val buffer = ByteArray(bufferSize)
            while (recording) {
                val read = record!!.read(buffer, 0, bufferSize)
                if (read > 0) {
                    block(buffer, read)
                }
            }
            block(ByteArray(0), -1)
            release()
        }
    }

    fun stop() {
        recording = false
    }

    fun release() {
        recording = false
        record?.stop()
        record?.release()
        recordThread = null
        file = null
        record = null
    }
}