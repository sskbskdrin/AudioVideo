package cn.sskbskdrin.record.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @author sskbskdrin
 * @date 2019/March/26
 */
public class VideoEncoder extends Thread implements VideoRecord.Track {
    private static final String TAG = "VideoEncoder";

    private static final int TIMEOUT_US = 10000; // 编码超时时间us
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int I_FRAME_INTERVAL = 10; // I帧间隔（GOP）

    private int bitRate;
    private int frameRate;

    private int mWidth;
    private int mHeight;

    private MediaCodec mMediaCodec;  // Android硬编解码器
    private MediaCodec.BufferInfo mBufferInfo; //  编解码Buffer相关信息

    private int mTrackIndex = -1;

    private boolean isReady;
    private volatile boolean isRunning;

    private WeakReference<VideoRecord.Muxer> mediaMuxer; // 音视频混合器

    private ArrayBlockingQueue<byte[]> queue;

    public VideoEncoder(int width, int height, int frameRate, int compressRatio, VideoRecord.Muxer muxer) {
        mWidth = width;
        mHeight = height;
        this.frameRate = frameRate;
        bitRate = width * height * 3 * 8 * frameRate / compressRatio;
        mediaMuxer = new WeakReference<>(muxer);
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    @Override
    public void prepare() {
        isReady = false;
        isRunning = false;
        mBufferInfo = new MediaCodec.BufferInfo();
        queue = new ArrayBlockingQueue<>(10);

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, this.mHeight, this.mWidth);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        MediaCodecInfo mCodecInfo = selectCodec(MIME_TYPE);
        if (mCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        try {
            mMediaCodec = MediaCodec.createByCodecName(mCodecInfo.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        isReady = true;
        Log.d(TAG, "视频编码器准备完成");
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    @Override
    public boolean running() {
        return isRunning;
    }

    public void addVideoData(byte[] data) {
        if (queue != null && isReady) {
            if (queue.size() >= 10) {
                queue.poll();
            }
            queue.offer(data);
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "视频编码器启动");
        isRunning = true;
        while (isRunning) {
            try {
                byte[] data = queue.take();
                if (data.length <= 0) {
                    break;
                }
                encodeFrame(data);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        readyStop();
        Log.e(TAG, "视频编码结束");
    }

    private void readyStop() {
        Log.d(TAG, "readyStop: 视频编码器");
        try {
            mMediaCodec.stop();
            mMediaCodec.release();
        } catch (Exception e) {
            Log.e(TAG, "视频编码器readyStop() 异常:" + e.toString());
        }
    }

    /**
     * 编码每一帧的数据
     *
     * @param input 每一帧的数据
     */
    private void encodeFrame(byte[] input) {
        input = rotate270(input, mWidth, mHeight);
        NV21toNV12(input, mHeight, mWidth);

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();

        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_US);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(input);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, System.nanoTime() / 1000, 0);
        } else {
            Log.e(TAG, "input buffer not available");
        }
        int outputBufferIndex;
        do {
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                VideoRecord.Muxer muxer = mediaMuxer.get();
                if (muxer != null && muxer.isReady()) {
                    Log.d(TAG, "添加视频轨 INFO_OUTPUT_FORMAT_CHANGED ");
                    mTrackIndex = muxer.addTrack(this, mMediaCodec.getOutputFormat());
                }
            } else if (outputBufferIndex < 0) {
                Log.e(TAG, "outputBufferIndex < 0");
            } else {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex + " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {
                    VideoRecord.Muxer muxer = mediaMuxer.get();
                    if (muxer != null) {
                        if (muxer.running()) {
                            outputBuffer.position(mBufferInfo.offset);
                            outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                            muxer.addData(mTrackIndex, outputBuffer, mBufferInfo);
                            Log.d(TAG, "发送视频帧数据 " + mBufferInfo.size);
                        } else {
                            if (mTrackIndex < 0) {
                                mTrackIndex = muxer.addTrack(this, mMediaCodec.getOutputFormat());
                            }
                        }
                    }
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
        } while (outputBufferIndex >= 0);
    }

    @Override
    public void exit() {
        Log.w(TAG, "视频exit()");
        //        addVideoData(new byte[0]);
        isReady = false;
        isRunning = false;
    }

    private static void NV21toNV12(byte[] data, int width, int height) {
        byte temp;
        for (int i = width * height; i < data.length; i += 2) {
            temp = data[i];
            data[i] = data[i + 1];
            data[i + 1] = temp;
        }
    }

    private static byte[] rotate270(byte[] data, int width, int height) {
        int size = width * height;
        byte[] temp = new byte[data.length];
        int index = 0;
        for (int i = 0; i < width; i++) {
            for (int j = height - 1; j >= 0; j--) {
                temp[index++] = data[j * width + i];
            }
        }

        for (int i = 0; i < width; i += 2) {
            for (int j = height / 2 - 1; j >= 0; j--) {
                temp[index++] = data[size + j * width + i];
                temp[index++] = data[size + j * width + i + 1];
            }
        }
        return temp;
    }

}
