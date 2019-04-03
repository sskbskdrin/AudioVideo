package cn.sskbskdrin.record.audio;

import android.media.AudioFormat;

/**
 * @author sskbskdrin
 * @date 2019/April/3
 */
public class AudioParams {

    enum Format {
        SINGLE_8_BIT, DOUBLE_8_BIT, SINGLE_16_BIT, DOUBLE_16_BIT
    }

    private Format format;
    int simpleRate;

    AudioParams(int simpleRate, Format f) {
        this.simpleRate = simpleRate;
        this.format = f;
    }

    AudioParams(int simpleRate, int channelCount, int bits) {
        this.simpleRate = simpleRate;
        set(channelCount, bits);
    }

    int getBits() {
        return (format == Format.SINGLE_8_BIT || format == Format.DOUBLE_8_BIT) ? 8 : 16;
    }

    int getEncodingFormat() {
        return (format == Format.SINGLE_8_BIT || format == Format.DOUBLE_8_BIT) ? AudioFormat.ENCODING_PCM_8BIT :
            AudioFormat.ENCODING_PCM_16BIT;
    }

    int getChannelCount() {return (format == Format.SINGLE_8_BIT || format == Format.SINGLE_16_BIT) ? 1 : 2;}

    int getChannelConfig() {
        return (format == Format.SINGLE_8_BIT || format == Format.SINGLE_16_BIT) ? AudioFormat.CHANNEL_IN_MONO :
            AudioFormat.CHANNEL_IN_STEREO;
    }

    int getOutChannelConfig() {
        return (format == Format.SINGLE_8_BIT || format == Format.SINGLE_16_BIT) ? AudioFormat.CHANNEL_OUT_MONO :
            AudioFormat.CHANNEL_OUT_STEREO;
    }

    void set(int channelCount, int bits) {
        if ((channelCount != 1 && channelCount != 2) || (bits != 8 && bits != 16)) {
            throw new IllegalArgumentException("不支持其它格式 channelCount=$channelCount bits=$bits");
        }
        if (channelCount == 1) {
            if (bits == 8) {
                format = Format.SINGLE_8_BIT;
            } else {
                format = Format.SINGLE_16_BIT;
            }
        } else {
            if (bits == 8) {
                format = Format.DOUBLE_8_BIT;
            } else {
                format = Format.DOUBLE_16_BIT;
            }
        }
    }
}
