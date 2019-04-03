package cn.sskbskdrin.record.audio

import android.media.AudioFormat

/**
 * @author sskbskdrin
 * @date 2019/March/29
 */

enum class Format {
    SINGLE_8_BIT,
    DOUBLE_8_BIT,
    SINGLE_16_BIT,
    DOUBLE_16_BIT
}

class AudioParams private constructor(var simpleRate: Int) {

    private lateinit var format: Format

    constructor(simpleRate: Int, f: Format) : this(simpleRate) {
        this.format = f
    }

    constructor(simpleRate: Int, channelCount: Int, bits: Int) : this(simpleRate) {
        set(channelCount, bits)
    }

    fun getBits() = if (format == Format.SINGLE_8_BIT || format == Format.DOUBLE_8_BIT) 8 else 16

    fun getEncodingFormat() = if (format == Format.SINGLE_8_BIT || format == Format.DOUBLE_8_BIT) AudioFormat
            .ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT

    fun getChannelCount() = if (format == Format.SINGLE_8_BIT || format == Format.SINGLE_16_BIT) 1 else 2

    fun getChannelConfig() = if (format == Format.SINGLE_8_BIT || format == Format.SINGLE_16_BIT) AudioFormat
            .CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO

    fun getOutChannelConfig() = if (format == Format.SINGLE_8_BIT || format == Format.SINGLE_16_BIT) AudioFormat
            .CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

    fun set(channelCount: Int, bits: Int) {
        if ((channelCount != 1 && channelCount != 2) || (bits != 8 && bits != 16)) {
            throw IllegalArgumentException("不支持其它格式 channelCount=$channelCount bits=$bits")
        }
        format = if (channelCount == 1) {
            if (bits == 8) Format.SINGLE_8_BIT else Format.SINGLE_16_BIT
        } else {
            if (bits == 8) Format.DOUBLE_8_BIT else Format.DOUBLE_16_BIT
        }
    }
}