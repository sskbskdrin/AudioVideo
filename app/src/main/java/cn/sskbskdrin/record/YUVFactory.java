package cn.sskbskdrin.record;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by keayuan on 2020/5/13.
 *
 * @author keayuan
 */
public class YUVFactory {
    private static final String TAG = "YUVFactory";

    private byte[] cache = {};
    private byte[] dest = {};
    private Point size = new Point();

    /**
     * 转化成RGBA
     *
     * @param src      yuv数据源
     * @param clip     裁剪
     * @param width    yuv宽
     * @param height   yuv高
     * @param format   yuv数据格式
     * @param rotate   旋转角度
     * @param mirror   是否镜像
     * @param hasAlpha 是否转化出Alpha
     * @return 返回处理的大小
     */
    public Point getRGBA(byte[] src, int[] clip, int width, int height, int format, int rotate, boolean mirror,
                         boolean hasAlpha) {
        rotate = (rotate % 360 + 360) % 360;
        int w = width;
        int h = height;
        if (rotate % 180 != 0) {
            w = height;
            h = width;
        }
        if (clip != null) {
            clip[0] = clip[0] >> 1 << 1;
            clip[1] = clip[1] >> 1 << 1;
            clip[2] = clip[2] >> 1 << 1;
            clip[3] = clip[3] >> 1 << 1;
            if (clip[0] < 0) clip[0] = 0;
            if (clip[1] < 0) clip[1] = 0;

            if (clip[2] + clip[0] > w) clip[2] = w - clip[0];
            if (clip[3] + clip[1] > h) clip[3] = h - clip[1];
            w = clip[2];
            h = clip[3];
        }

        int mul = hasAlpha ? 4 : 3;
        checkCache(w * h * mul);
        YUVLib.byteToRGBA(src, dest, cache, clip, width, height, format, rotate, mirror, hasAlpha);
        size.x = w;
        size.y = h;
        return size;
    }

    /**
     * 转化成RGBA
     *
     * @param srcY     yuv数据源
     * @param clip     裁剪
     * @param width    yuv宽
     * @param height   yuv高
     * @param rotate   旋转角度
     * @param mirror   是否镜像
     * @param hasAlpha 是否转化出Alpha
     * @return 返回处理的大小
     */
    public Point getRGBA(byte[] srcY, byte[] srcU, byte[] srcV, int[] clip, int width, int height, int rotate,
                         boolean mirror, boolean hasAlpha) {
        rotate = (rotate % 360 + 360) % 360;
        int w = width;
        int h = height;
        if (rotate % 180 != 0) {
            w = height;
            h = width;
        }
        if (clip != null) {
            clip[0] = clip[0] >> 1 << 1;
            clip[1] = clip[1] >> 1 << 1;
            clip[2] = clip[2] >> 1 << 1;
            clip[3] = clip[3] >> 1 << 1;
            if (clip[0] < 0) clip[0] = 0;
            if (clip[1] < 0) clip[1] = 0;

            if (clip[2] + clip[0] > w) clip[2] = w - clip[0];
            if (clip[3] + clip[1] > h) clip[3] = h - clip[1];
            w = clip[2];
            h = clip[3];
        }
        int mul = hasAlpha ? 4 : 3;
        checkCache(w * h * mul);
        YUVLib.yuvToRGBA(srcY, srcU, srcV, dest, cache, clip, width, height, ImageFormat.YUV_420_888, rotate, mirror);
        size.x = w;
        size.y = h;
        return size;
    }

    public Bitmap getBitmap(byte[] srcY, byte[] srcU, byte[] srcV, int width, int height) {
        return getBitmap(srcY, srcU, srcV, null, width, height, 0, false);
    }

    public Bitmap getBitmap(byte[] srcY, byte[] srcU, byte[] srcV, int width, int height, int rotate) {
        return getBitmap(srcY, srcU, srcV, null, width, height, rotate, false);
    }

    public Bitmap getBitmap(byte[] srcY, byte[] srcU, byte[] srcV, int[] clip, int width, int height) {
        return getBitmap(srcY, srcU, srcV, clip, width, height, 0, false);
    }

    public Bitmap getBitmap(byte[] srcY, byte[] srcU, byte[] srcV, int[] clip, int width, int height, int rotate) {
        return getBitmap(srcY, srcU, srcV, clip, width, height, rotate, false);
    }

    public Bitmap getBitmap(byte[] srcY, byte[] srcU, byte[] srcV, int[] clip, int width, int height, int rotate,
                            boolean mirror) {
        long start = SystemClock.elapsedRealtimeNanos();
        getRGBA(srcY, srcU, srcV, clip, width, height, rotate, mirror, true);
        Bitmap bitmap = Bitmap.createBitmap(size.x, size.y, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(cache));
        Log.v(TAG, String.format("getBitmap: time=%.2f", (SystemClock.elapsedRealtimeNanos() - start) / 1000_000f));
        return bitmap;
    }

    public Bitmap getBitmap(byte[] src, int width, int height, int format) {
        return getBitmap(src, null, width, height, format, 0, false);
    }

    public Bitmap getBitmap(byte[] src, int width, int height, int format, int rotate) {
        return getBitmap(src, null, width, height, format, rotate, false);
    }

    public Bitmap getBitmap(byte[] src, int width, int height, int format, int rotate, boolean mirror) {
        return getBitmap(src, null, width, height, format, rotate, mirror);
    }

    public Bitmap getBitmap(byte[] src, int[] clip, int width, int height, int format) {
        return getBitmap(src, clip, width, height, format, 0, false);
    }

    public Bitmap getBitmap(byte[] src, int[] clip, int width, int height, int format, int rotate) {
        return getBitmap(src, clip, width, height, format, rotate, false);
    }

    public Bitmap getBitmap(byte[] src, int[] clip, int width, int height, int format, int rotate, boolean mirror) {
        long start = SystemClock.elapsedRealtimeNanos();
        getRGBA(src, clip, width, height, format, rotate, mirror, true);
        Bitmap bitmap = Bitmap.createBitmap(size.x, size.y, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(dest));
        /*
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < 16; i++) {
            builder.append(Integer.toHexString(dest[i] & 0xff)).append(',');
        }
        builder.setLength(builder.length() - 1);
        builder.append(']');
        Log.d(TAG, "getBitmap: " + builder.toString());
         */
        Log.v(TAG, String.format("getBitmap: time=%.2f", (SystemClock.elapsedRealtimeNanos() - start) / 1000_000f));
        return bitmap;
    }

    private void checkCache(int size) {
        if (cache == null || cache.length < size) {
            cache = new byte[size];
        }
        if (dest == null || dest.length < size) {
            dest = new byte[size];
        }
    }
}
