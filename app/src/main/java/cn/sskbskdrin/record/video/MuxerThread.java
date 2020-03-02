package cn.sskbskdrin.record.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;

import cn.sskbskdrin.record.FileUtils;

/**
 * @author sskbskdrin
 * @date 2019/March/26
 */
public class MuxerThread extends Thread implements VideoRecord.Muxer {
    private static final String TAG = "MuxerThread";

    private static final VideoRecord.Track END_TRACK = new VideoRecord.Track() {
        @Override
        public void prepare() {

        }

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public boolean running() {
            return false;
        }

        @Override
        public void exit() {

        }
    };

    private MediaMuxer mediaMuxer;

    private ArrayBlockingQueue<TrackData> queue;
    private SparseArray<ArrayBlockingQueue<TrackData>> queueMap;
    private WeakHashMap<VideoRecord.Track, Integer> mTrackMap;

    private volatile boolean isRunning;
    private boolean isReady;

    private OnAddTrackCallback callback;

    public MuxerThread() {

    }

    @Override
    public void addData(VideoRecord.Track track, ByteBuffer buffer, MediaCodec.BufferInfo info) {
        if (queue != null && isRunning) {
            if (queue.size() >= 20) {
                queue.poll();
            }
            Integer index = mTrackMap.get(track);
            if (index != null) {
                queue.offer(new TrackData(index, buffer, info));
            }
        }
    }

    @Override
    public synchronized void addTrack(VideoRecord.Track track, MediaFormat mediaFormat) {
        Integer index = mTrackMap.get(track);
        if (index != null) {
            return;
        }

        if (mediaMuxer != null) {
            try {
                index = mediaMuxer.addTrack(mediaFormat);
            } catch (Exception e) {
                Log.e(TAG, "addTrack 异常:" + e.toString());
                return;
            }
            Log.e(TAG, "添加Track完成 index=" + index);
            mTrackMap.put(track, index);
            queueMap.put(index, new ArrayBlockingQueue<>(10));
            if (callback != null) {
                callback.onAddTrack(track);
            }
        }
    }

    @Override
    public void prepare() {
        queue = new ArrayBlockingQueue<>(20);
        queueMap = new SparseArray<>(3);
        mTrackMap = new WeakHashMap<>();
        mTrackMap.put(END_TRACK, -1);
        String filePath = FileUtils.getRandomFile(".mp4");
        try {
            mediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        isReady = true;
    }

    @Override
    public void begin() {
        mediaMuxer.start();
        start();
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
        Log.d(TAG, "混合器启动");
        isRunning = true;
        while (isRunning) {
            try {
                TrackData data = queue.take();
                if (data.trackIndex < 0) {
                    break;
                }
                Log.i(TAG, "写入混合数据 " + data.bufferInfo.size);
                mediaMuxer.writeSampleData(data.trackIndex, data.byteBuf, data.bufferInfo);
                //                }
            } catch (InterruptedException e) {
                Log.e(TAG, "队列阻塞中断: ", e);
            } catch (Exception e) {
                Log.e(TAG, "写入混合数据失败!" + e.toString());
            }
        }
        readyStop();
        Log.e(TAG, "混合器退出...");
    }

    private void readyStop() {
        Log.d(TAG, "readyStop: 混合器");
        if (mediaMuxer != null) {
            try {
                mediaMuxer.stop();
                mediaMuxer.release();
            } catch (Exception e) {
                Log.e(TAG, "混合器readyStop() 异常:", e);
            }
            mediaMuxer = null;
        }
    }

    @Override
    public void exit() {
        Log.w(TAG, "混合器exit()");
        addData(END_TRACK, null, null);
        isRunning = false;
        isReady = false;
    }

    public void setOnAddTrackCallback(OnAddTrackCallback callback) {
        this.callback = callback;
    }

    public interface OnAddTrackCallback {
        void onAddTrack(VideoRecord.Track track);
    }

    /**
     * 封装需要传输的数据类型
     */
    private static class TrackData {
        int trackIndex;
        ByteBuffer byteBuf;
        MediaCodec.BufferInfo bufferInfo;

        public TrackData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            this.trackIndex = trackIndex;
            this.byteBuf = byteBuf;
            this.bufferInfo = bufferInfo;
        }
    }
}
