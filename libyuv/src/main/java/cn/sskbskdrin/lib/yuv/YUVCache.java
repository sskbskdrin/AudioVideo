package cn.sskbskdrin.lib.yuv;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.nio.ByteBuffer;

/**
 * Created by keayuan on 2020/5/13.
 *
 * @author keayuan
 */
public class YUVCache {
    private static final String TAG = "YUVCache";
    private SparseArray<byte[]> cacheArray = new SparseArray<>(6);

    private byte[] cache = {};
    private byte[] dest = {};
    private Point size = new Point();
    private Bitmap bitmap;

    private void checkCache(int size) {
        recycler();
        cache = cacheArray.get(size);
        if (cache == null) {
            cache = new byte[size];
            cacheArray.put(size, cache);
        }
    }

    private void checkDest(int size) {
        if (dest == null || dest.length != size) {
            // 如果不为空，先缓存起来
            if (dest != null) {
                cacheArray.put(dest.length, dest);
            }
            dest = cacheArray.get(size);
            // 如果没有，或者跟cache相同，重新创建
            if (dest == null || dest == cache) {
                dest = new byte[size];
            } else {
                // 可以使用，移出缓存区
                cacheArray.remove(size);
            }
        }
    }

    private void recycler() {
        if (dest != null) {
            cacheArray.remove(dest.length);
        }
        if (cache != null) {
            cacheArray.put(cache.length, cache);
        }
    }

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
    public Point transformRGBA(byte[] src, int[] clip, int width, int height, int format, int rotate, boolean mirror,
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
        checkDest(w * h * mul);
        checkCache(w * h * mul);
        YUVLib.byteToRGBA(src, dest, cache, clip, width, height, format, rotate, mirror, hasAlpha);
        size.x = w;
        size.y = h;
        recycler();
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
    public Point transformRGBA(byte[] srcY, byte[] srcU, byte[] srcV, int[] clip, int width, int height, int rotate,
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
        checkDest(w * h * mul);
        YUVLib.yuvToRGBA(srcY, srcU, srcV, dest, cache, clip, width, height, ImageFormat.YUV_420_888, rotate, mirror);
        size.x = w;
        size.y = h;
        recycler();
        return size;
    }

    public byte[] toggleAlpha(boolean hasAlpha) {
        int step = hasAlpha ? 4 : 3;
        int size = dest.length / step;

        checkCache(size * (hasAlpha ? 3 : 4));

        byte[] rgb = dest;
        byte[] rgba = cache;
        if (hasAlpha) {
            rgba = dest;
            rgb = cache;
        }
        YUVLib.RGBToRGBA(rgb, rgba, size, hasAlpha);
        dest = hasAlpha ? rgb : rgba;
        cache = hasAlpha ? rgba : rgb;
        recycler();
        return dest;
    }

    public byte[] scaleRGBA(int width, int height, int quality) {
        checkCache(width * height * 4);
        YUVLib.scaleRGBA(dest, size.x, size.y, cache, width, height, quality);
        byte[] temp = dest;
        dest = cache;
        cache = temp;
        recycler();
        return dest;
    }

    public byte[] getRGBA() {
        return dest;
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
        transformRGBA(srcY, srcU, srcV, clip, width, height, rotate, mirror, true);
        if (bitmap == null || bitmap.getWidth() != size.x || bitmap.getHeight() != size.y) {
            bitmap = Bitmap.createBitmap(size.x, size.y, Bitmap.Config.ARGB_8888);
        }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(dest));
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
        transformRGBA(src, clip, width, height, format, rotate, mirror, true);
        if (bitmap == null || bitmap.getWidth() != size.x || bitmap.getHeight() != size.y) {
            bitmap = Bitmap.createBitmap(size.x, size.y, Bitmap.Config.ARGB_8888);
        }
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

}
