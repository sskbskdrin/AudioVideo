package cn.sskbskdrin.record.audio;

import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author sskbskdrin
 * @date 2019/April/3
 */
public class AudioTrackManager {
    private AudioTrack audioTrack;

    private boolean playing = false;

    public AudioParams playWav(String filepath, AudioRecordManager.RecordCallback callback) {
        RandomAccessFile file = null;
        AudioParams params = null;
        try {
            file = new RandomAccessFile(filepath, "r");
            params = readWavHeader(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (params == null) {
            return null;
        }
        int simpleRate = params.simpleRate;
        int channelConfig = params.getOutChannelConfig();
        int audioFormat = params.getEncodingFormat();
        int minBufSize = AudioTrack.getMinBufferSize(simpleRate, channelConfig, audioFormat);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, simpleRate, channelConfig, audioFormat, minBufSize,
            AudioTrack.MODE_STREAM);
        final RandomAccessFile finalFile = file;
        new Thread(() -> {
            playing = true;
            audioTrack.play();
            byte[] buffer = new byte[minBufSize];
            try {
                finalFile.seek(44);
                while (playing) {
                    int read = finalFile.read(buffer);
                    if (read > 0) {
                        audioTrack.write(buffer, 0, read);
                    } else {
                        finalFile.close();
                        playing = false;
                        audioTrack.stop();
                        audioTrack.release();
                    }
                    if (callback != null) {
                        callback.onRecord(buffer, read);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        return params;
    }

    public static AudioParams readWavHeader(RandomAccessFile file) throws IOException {
        file.seek(22);
        byte channelCount = file.readByte();
        file.seek(24);
        int sampleRate = file.readByte() & 0xff;
        sampleRate |= (file.readByte() & 0xff) << 8;
        sampleRate |= (file.readByte() & 0xff) << 16;
        sampleRate |= (file.readByte() & 0xff) << 24;

        file.seek(34);
        byte bits = file.readByte();
        return new AudioParams(sampleRate, channelCount, bits);
    }
}
