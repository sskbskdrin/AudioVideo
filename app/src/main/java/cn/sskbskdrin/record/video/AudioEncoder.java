package cn.sskbskdrin.record.video;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * @author sskbskdrin
 * @date 2019/March/26
 */
public class AudioEncoder extends Thread implements VideoRecord.Track {
    private static final String TAG = "AudioEncoder";

    private static final int TIMEOUT_USEC = 10000; // 编码超时时间us
    private static final String MIME_TYPE = "audio/mp4a-latm";

    private MediaCodec mMediaCodec;                // API >= 16(Android4.1.2)
    private volatile boolean isExit = false;
    private WeakReference<VideoRecord.Muxer> mediaMuxer;
    private MediaCodec.BufferInfo mBufferInfo;
    private AudioRecord audioRecord;
    private int sampleRate;
    private int bitRate;

    private int bufferSize;
    private boolean isReady;
    private boolean isRunning;
    private int mTrackIndex;

    private long prevOutputPTSUs;

    public AudioEncoder(int sampleRate, VideoRecord.Muxer muxer) {
        this.sampleRate = sampleRate;
        this.bitRate = 16 * sampleRate / 8;

        mediaMuxer = new WeakReference<>(muxer);
    }

    @Override
    public void prepare() {
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaCodecInfo audioCodecInfo = selectAudioCodec(MIME_TYPE);
        if (audioCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }

        MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, 1);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        isReady = true;
        Log.d(TAG, "音频编码器准备完成");
    }

    private static MediaCodecInfo selectAudioCodec(final String mimeType) {
        MediaCodecInfo result = null;
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {    // skipp decoder
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    if (result == null) {
                        result = codecInfo;
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    @Override
    public boolean running() {
        return isRunning;
    }

    @Override
    public void run() {
        isRunning = true;
        if (audioRecord != null) {
            audioRecord.startRecording();
        }
        byte[] buffer = new byte[bufferSize];
        while (isRunning) {
            if (audioRecord != null) {
                int readBytes = audioRecord.read(buffer, 0, bufferSize);
                if (readBytes >= 0) {
                    //                    buffer.position(readBytes);
                    //                    buffer.flip();
                    Log.i(TAG, "解码音频数据:" + readBytes);
                    try {
                        encode(buffer, readBytes, getPTSUs());
                    } catch (Exception e) {
                        Log.e(TAG, "解码音频(Audio)数据 失败");
                        e.printStackTrace();
                    }
                }
            }
        }
        readyStop();
        Log.e(TAG, "音频编码结束");
    }

    private void encode(final byte[] buffer, final int length, final long presentationTimeUs) {
        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        /*向编码器输入数据*/
        if (inputBufferIndex >= 0) {
            final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            if (buffer != null) {
                inputBuffer.put(buffer);
            }
            if (length <= 0) {
                Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length, System.nanoTime() / 1000, 0);
            }
        } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            // wait for MediaCodec encoder is ready to encode
            // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
            // will wait for maximum TIMEOUT_USEC(10msec) on each call
        }

        /*获取解码后的数据*/
        VideoRecord.Muxer muxer = mediaMuxer.get();
        if (muxer == null) {
            Log.w(TAG, "muxer is unexpectedly null");
            return;
        }
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();

        int encoderStatus;
        do {
            encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                muxer = this.mediaMuxer.get();
                if (muxer != null) {
                    Log.e(TAG, "添加音轨 INFO_OUTPUT_FORMAT_CHANGED ");
                    mTrackIndex = muxer.addTrack(this, mMediaCodec.getOutputFormat());
                }
            } else if (encoderStatus < 0) {
                Log.e(TAG, "encoderStatus < 0");
            } else {
                final ByteBuffer encodedData = outputBuffers[encoderStatus];
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0 && muxer.running()) {
                    mBufferInfo.presentationTimeUs = getPTSUs();
                    muxer.addData(mTrackIndex, encodedData, mBufferInfo);
                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                    Log.e(TAG, "发送音频数据 " + mBufferInfo.size);
                }
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
            }
        } while (encoderStatus >= 0);
    }

    private void readyStop() {
        Log.d(TAG, "readyStop: 音频编码器");
        try {
            audioRecord.stop();
            mMediaCodec.stop();
            mMediaCodec.release();
        } catch (Exception e) {
            Log.e(TAG, "音频编码器readyStop() 异常:" + e.toString());
        }
    }

    /**
     * get next encoding presentationTimeUs
     */
    private long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs) {
            result = (prevOutputPTSUs - result) + result;
        }
        return result;
    }

    @Override
    public void exit() {
        Log.w(TAG, "音频exit()");
        isRunning = false;
        isReady = false;
    }
}
