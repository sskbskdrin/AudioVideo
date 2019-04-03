package cn.sskbskdrin.record.audio;

import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author sskbskdrin
 * @date 2019/April/3
 */
public class AudioRecordManager {

    private AudioParams DEFAULT_FORMAT = new AudioParams(8000, 1, 16);

    private AudioRecord record;

    private Thread recordThread;

    private boolean recording = false;

    private RandomAccessFile file;

    public void startRecord(String filePath, RecordCallback callback) {
        startRecord(filePath, DEFAULT_FORMAT, callback);
    }

    public void startRecord(String filePath, AudioParams params, RecordCallback callback) {
        int channelCount = params.getChannelCount();
        int bits = params.getBits();

        final boolean storeFile = filePath != null && !filePath.isEmpty();

        startRecord(params, (bytes, len) -> {
            if (storeFile) {
                if (file == null) {
                    File f = new File(filePath);
                    if (f.exists()) {
                        f.delete();
                    }
                    try {
                        file = new RandomAccessFile(f, "rw");
                        file.write(getWaveFileHeader(0, params.simpleRate, channelCount, bits));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (len > 0) {
                    try {
                        file.write(bytes, 0, len);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        // 因为在前面已经写入头信息，所以这里要减去头信息才是数据的长度
                        int length = (int) file.length() - 44;
                        file.seek(0);
                        file.write(getWaveFileHeader(length, params.simpleRate, channelCount, bits));
                        file.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (callback != null) {
                callback.onRecord(bytes, len);
            }
        });
    }

    public void startRecord(AudioParams params, RecordCallback callback) {
        int simpleRate = params.simpleRate;
        int channelConfig = params.getChannelConfig();
        int audioFormat = params.getEncodingFormat();
        // 根据AudioRecord提供的api拿到最小缓存大小
        int bufferSize = AudioRecord.getMinBufferSize(simpleRate, channelConfig, audioFormat);
        //创建Record对象
        record = new AudioRecord(MediaRecorder.AudioSource.MIC, simpleRate, channelConfig, audioFormat, bufferSize);
        recordThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            record.startRecording();
            recording = true;
            while (recording) {
                int read = record.read(buffer, 0, bufferSize);
                // 将数据回调到外部
                if (read > 0 && callback != null) {
                    callback.onRecord(buffer, read);
                }
            }
            if (callback != null) {
                // len 为-1时表示结束
                callback.onRecord(buffer, -1);
                recording = false;
            }
            //释放资源
            release();
        });
        recordThread.start();
    }

    public void stop() {
        recording = false;
    }

    public void release() {
        recording = false;
        if (record != null) {
            record.stop();
            record.release();
        }
        record = null;
        file = null;
        recordThread = null;
    }

    private static byte[] getWaveFileHeader(int totalDataLen, int sampleRate, int channelCount, int bits) {
        byte[] header = new byte[44];
        // RIFF/WAVE header
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';

        int fileLength = totalDataLen + 36;
        header[4] = (byte) (fileLength & 0xff);
        header[5] = (byte) (fileLength >> 8 & 0xff);
        header[6] = (byte) (fileLength >> 16 & 0xff);
        header[7] = (byte) (fileLength >> 24 & 0xff);
        //WAVE
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        // 'fmt ' chunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        // 4 bytes: size of 'fmt ' chunk
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;

        // pcm format = 1
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channelCount;
        header[23] = 0;

        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) (sampleRate >> 8 & 0xff);
        header[26] = (byte) (sampleRate >> 16 & 0xff);
        header[27] = (byte) (sampleRate >> 24 & 0xff);

        int byteRate = sampleRate * bits * channelCount / 8;
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) (byteRate >> 8 & 0xff);
        header[30] = (byte) (byteRate >> 16 & 0xff);
        header[31] = (byte) (byteRate >> 24 & 0xff);
        // block align
        header[32] = (byte) (channelCount * bits / 8);
        header[33] = 0;
        // bits per sample
        header[34] = (byte) bits;
        header[35] = 0;
        //data
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalDataLen & 0xff);
        header[41] = (byte) (totalDataLen >> 8 & 0xff);
        header[42] = (byte) (totalDataLen >> 16 & 0xff);
        header[43] = (byte) (totalDataLen >> 24 & 0xff);
        return header;
    }

    public interface RecordCallback {
        /**
         * 数据回调
         *
         * @param bytes 数据
         * @param len   数据有效长度，-1时表示数据结束
         */
        void onRecord(byte[] bytes, int len);
    }
}
