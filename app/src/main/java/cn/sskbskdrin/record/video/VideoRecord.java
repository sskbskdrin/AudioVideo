package cn.sskbskdrin.record.video;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * @author sskbskdrin
 * @date 2019/March/25
 */
public class VideoRecord {

    private static final int FRAME_RATE = 25; // 帧率
    private static final int IFRAME_INTERVAL = 10; // I帧间隔（GOP）
    private static final int TIMEOUT_USEC = 30000; // 编码超时时间
    private static final int COMPRESS_RATIO = 256;

    private static final int SIMPLE_RATE = 16000;


    private int width;
    private int height;

    MuxerThread mMuxerThread;
    VideoEncoder videoEncoder;
    AudioEncoder audioEncoder;

    private static VideoRecord mInstance;

    public static boolean startRecord(int width, int height) {
        if (mInstance == null) {
            synchronized (VideoRecord.class) {
                if (mInstance == null) {
                    mInstance = new VideoRecord(width, height);
                    mInstance.start();
                    return true;
                }
            }
        }
        return false;
    }

    public static void stop() {
        if (mInstance != null) {
            try {

                mInstance.videoEncoder.exit();
                mInstance.videoEncoder.join();

                mInstance.audioEncoder.exit();
                mInstance.audioEncoder.join();

                mInstance.mMuxerThread.exit();
                mInstance.mMuxerThread.join();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mInstance = null;
        }
    }

    public static void addVideoDate(byte[] data) {
        if (mInstance != null) {
            if (mInstance.videoEncoder.running()) {
                mInstance.videoEncoder.addVideoData(data);
            }
        }
    }

    private VideoRecord(int width, int height) {
        this.width = width;
        this.height = height;
    }

    boolean audio = false;
    boolean video;

    private void start() {
        mMuxerThread = new MuxerThread();
        mMuxerThread.prepare();
        mMuxerThread.setOnAddTrackCallback(track -> {
            if (track == videoEncoder) {
                video = true;
            }
            if (track == audioEncoder) {
                audio = true;
            }
            if (audio && video) {
                mMuxerThread.begin();
            }
        });

        videoEncoder = new VideoEncoder(width, height, FRAME_RATE, COMPRESS_RATIO, mMuxerThread);
        videoEncoder.prepare();
        videoEncoder.start();

        audioEncoder = new AudioEncoder(SIMPLE_RATE, mMuxerThread);
        audioEncoder.prepare();
        audioEncoder.start();
    }

    public interface Muxer extends Status {
        void begin();

        void addData(int trackIndex, ByteBuffer buffer, MediaCodec.BufferInfo info);

        int addTrack(Track track, MediaFormat mediaFormat);
    }

    public interface Track extends Status {

    }

    public interface Status {
        void prepare();

        boolean isReady();

        boolean running();

        void exit();
    }

    public static void main(String[] args) {
        TTT t = new TTT();
        t.start();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        t.getName();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("main end");
    }

    static class TTT extends Thread {
        @Override
        public void run() {
            System.out.println("TTT start");
            try {
                sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("ttt end");
        }
    }
}
