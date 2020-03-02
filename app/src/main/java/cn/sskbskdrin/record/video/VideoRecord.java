package cn.sskbskdrin.record.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

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

    private MuxerThread mMuxerThread;
    private VideoEncoder videoEncoder;
    //    private  AudioEncoder audioEncoder;

    private static VideoRecord mInstance;
    private static boolean mUseSurface;

    public static boolean startRecord(int width, int height, boolean useSurface) {
        mUseSurface = useSurface;
        return startRecord(width, height);
    }

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

                //                mInstance.audioEncoder.exit();
                //                mInstance.audioEncoder.join();

                mInstance.mMuxerThread.exit();
                mInstance.mMuxerThread.join();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mUseSurface = false;
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

    private boolean audio = true;
    private boolean video;

    private void start() {
        mMuxerThread = new MuxerThread();
        mMuxerThread.prepare();
        mMuxerThread.setOnAddTrackCallback(track -> {
            if (track == videoEncoder) {
                video = true;
            }
            //            if (track == audioEncoder) {
            //                audio = true;
            //            }
            if (audio && video) {
                mMuxerThread.begin();
            }
        });

        videoEncoder = new VideoEncoder(width, height, FRAME_RATE, COMPRESS_RATIO, mMuxerThread);
        videoEncoder.setInputSurface(mUseSurface);
        videoEncoder.prepare();
        videoEncoder.start();

        //        audioEncoder = new AudioEncoder(SIMPLE_RATE, mMuxerThread);
        //        audioEncoder.prepare();
        //        audioEncoder.start();
    }

    public static Surface getVideoSurface() {
        return mInstance.videoEncoder.getSurface();
    }

    public interface Muxer extends Status {
        void begin();

        void addData(Track track, ByteBuffer buffer, MediaCodec.BufferInfo info);

        void addTrack(Track track, MediaFormat mediaFormat);
    }

    public interface Track extends Status {

    }

    public interface Status {
        void prepare();

        boolean isReady();

        boolean running();

        void exit();
    }

}
