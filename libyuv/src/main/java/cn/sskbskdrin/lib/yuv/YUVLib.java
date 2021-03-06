package cn.sskbskdrin.lib.yuv;

import android.graphics.ImageFormat;
import android.util.Log;
import android.util.SparseArray;

/**
 * @author sskbskdrin
 * @date 2019/April/9
 */
public class YUVLib {
    private static final String TAG = "YUVLib";

    static {
        System.loadLibrary("yuv");
    }

    private enum Format {

        /*YUV420sp*/
        /**
         * YYYY
         * YYYY
         * YYYY
         * YYYY
         * VUVU
         * VUVU
         */
        NV21((byte) 'N', (byte) 'V', (byte) '2', (byte) '1'),
        /**
         * YYYY
         * YYYY
         * YYYY
         * YYYY
         * UVUV
         * UVUV
         */
        NV12((byte) 'N', (byte) 'V', (byte) '1', (byte) '2'),

        /*YUV420p*/
        /**
         * YYYY
         * YYYY
         * YYYY
         * YYYY
         * VV
         * VV
         * UU
         * UU
         */
        YV12((byte) 'Y', (byte) 'V', (byte) '1', (byte) '2'),
        /**
         * YYYY
         * YYYY
         * YYYY
         * YYYY
         * UU
         * UU
         * VV
         * VV
         */
        I420((byte) 'I', (byte) '4', (byte) '2', (byte) '0');

        int value;

        Format(byte a, byte b, byte c, byte d) {
            value = a & 0xff;
            value |= (b & 0xff) << 8;
            value |= (c & 0xff) << 16;
            value |= (d & 0xff) << 24;
        }

        public static int getValue(int format) {
            switch (format) {
                case ImageFormat.NV21:
                    format = YUVLib.Format.NV21.value;
                    break;
                case ImageFormat.YV12:
                    format = YUVLib.Format.YV12.value;
                    break;
                case ImageFormat.YUV_420_888:
                    format = YUVLib.Format.I420.value;
                    break;
                default:
                    throw new IllegalArgumentException("format " + format + " not support");
            }
            return format;
        }
    }

    public static void init() {
        r1 = new int[255];
        g1 = new int[255];
        g2 = new int[255];
        b1 = new int[255];
        for (int i = 0; i < 255; i++) {
            r1[i] = (int) ((i - 128) * 1.370705);
            g1[i] = (int) ((i - 128) * 0.698001);
            g2[i] = (int) ((i - 128) * 0.337633);
            b1[i] = (int) ((i - 128) * 1.732446);
        }
    }

    static int[] r1;
    static int[] g1, g2;
    static int[] b1;

    private static void colorT(byte Y, byte U, byte V, byte[] dest, int index) {
        int y = 0xff & Y;
        int u = 0xff & U;
        int v = 0xff & V;
        int r = y + r1[v];
        int g = y - g1[v] - g2[u];
        int b = y + b1[u];
        dest[index] = (byte) clamp(r);
        dest[index + 1] = (byte) clamp(g);
        dest[index + 2] = (byte) clamp(b);
        dest[index + 3] = (byte) 0xff;
    }

    private static int color(byte Y, byte U, byte V) {
        int y = 0xff & Y;
        int u = (0xff & U) - 128;
        int v = (0xff & V) - 128;
        int y65535 = y << 16;// 0xffff=65535;
        int r = y65535 + 89829 * v;// 1.370705*0xffff=89829.15
        int g = y65535 - 45743 * v - 22127 * u; // 0.698001*0xffff=45743.49 0.337633*0xffff=22126.78
        int b = y65535 + 113535 * u;// 1.732446*0x3fff=113535.85
        r = clamp(r >> 16);
        g = clamp(g >> 16);
        b = clamp(b >> 16);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 0xff ? 0xff : v);
    }

    public static void yuv420spToARGB(byte[] src, int[] dest, int width, int height) {
        int uv = width * height;
        for (int i = 0; i < height; i++) {
            int line = i * width;
            int index, uvIndex;
            for (int j = 0; j < width; j++) {
                index = line + j;
                uvIndex = uv + (i >> 1) * width + (j & 0xfffffffe);
                dest[index] = color(src[index], src[uvIndex + 1], src[uvIndex]);
            }
        }
    }

    public static void I420ToARGB(byte[] y, byte[] u, byte[] v, byte[] dest, int width, int height) {
        long st = System.currentTimeMillis();
        for (int i = 0; i < height; i++) {
            int line = i * width;
            int uvIndex = (i >> 1) * (width >> 1);
            for (int j = 0; j < width; j++) {
                int index = line + j;
                int uv = uvIndex + (j >> 1);
                colorT(y[index], u[uv], v[uv], dest, index << 2);
            }
        }
        Log.d(TAG, "I420ToARGB: time=" + (System.currentTimeMillis() - st));
    }

    /**
     * yuv数组转[r,g,b,a]
     *
     * @param src    yuv数据
     * @param dest   存储结果
     * @param cache  缓存
     * @param clip   裁剪区域,旋转后的位置[x,y,w,h]
     * @param width  yuv数据的宽
     * @param height yuv数据的高
     * @param format yuv数据格式
     * @param rotate 旋转角度[0,90,180,270]
     * @param mirror 是否镜像
     */
    public static void byteToRGBA(byte[] src, byte[] dest, byte[] cache, int[] clip, int width, int height,
                                  int format, int rotate, boolean mirror) {
        nativeByteToRGBA(src, dest, cache, clip, width, height, Format.getValue(format), rotate, mirror, true);
    }

    public static void byteToRGBA(byte[] src, byte[] dest, byte[] cache, int[] clip, int width, int height,
                                  int format, int rotate, boolean mirror, boolean hasAlpha) {
        nativeByteToRGBA(src, dest, cache, clip, width, height, Format.getValue(format), rotate, mirror, hasAlpha);
    }

    /**
     * yuv数组转[r,g,b,a]
     *
     * @param srcY   y数据
     * @param srcU   u数据
     * @param srcV   v数据
     * @param dest   存储结果
     * @param cache  缓存
     * @param clip   裁剪区域,旋转后的位置[x,y,w,h]
     * @param width  yuv数据的宽
     * @param height yuv数据的高
     * @param format yuv数据格式
     * @param rotate 旋转角度[0,90,180,270]
     * @param mirror 是否镜像
     */
    public static void yuvToRGBA(byte[] srcY, byte[] srcU, byte[] srcV, byte[] dest, byte[] cache, int[] clip,
                                 int width, int height, int format, int rotate, boolean mirror) {
        nativeYUVToRGBA(srcY, srcU, srcV, dest, cache, clip, width, height, Format.getValue(format), rotate, mirror,
            true);
    }

    public static void splitRGBA(byte[] rgba, byte[] r, byte[] g, byte[] b, boolean hasAlpha) {
        splitRGBA(rgba, r, g, b, null, hasAlpha);
    }

    public static void splitRGBA(byte[] rgba, byte[] r, byte[] g, byte[] b, byte[] a, boolean hasAlpha) {
        nativeSplitRGBA(rgba, r, g, b, a, rgba.length, hasAlpha);
    }

    public static void scaleRGBA(byte[] src, int width, int height, byte[] dest, int dWidth, int dHeight, int quality) {
        nativeScaleRGBA(src, width, height, dest, dWidth, dHeight, quality);
    }

    public static void RGBToRGBA(byte[] rgb, byte[] rgba, int size, boolean reverse) {
        nativeRGBToRGBA(rgb, rgba, size, reverse);
    }

    public static void RGB565ToRGBA(byte[] rgb565, byte[] rgba, int size, boolean reverse) {
        nativeRGB565ToRGBA(rgb565, rgba, size, reverse);
    }

    public static void RGBAToColor(byte[] src, int[] colors, int srcSize) {
        nativeBGRAToColor(src, colors, srcSize);
    }

    private static native void nativeByteToRGBA(byte[] src, byte[] dest, byte[] cache, int[] clip, int width,
                                                int height, int format, int rotate, boolean mirror, boolean hasAlpha);

    private static native void nativeYUVToRGBA(byte[] srcY, byte[] srcU, byte[] srcV, byte[] dest, byte[] cache,
                                               int[] clip, int width, int height, int format, int rotate,
                                               boolean mirror, boolean hasAlpha);

    private static native void nativeSplitRGBA(byte[] rgba, byte[] r, byte[] g, byte[] b, byte[] a, int srcSize,
                                               boolean hasAlpha);

    private static native void nativeBGRAToColor(byte[] src, int[] colors, int srcSize);

    private static native void nativeScaleRGBA(byte[] src, int width, int height, byte[] dest, int dWidth,
                                               int dHeight, int quality);

    private static native void nativeRGBToRGBA(byte[] rgb, byte[] rgba, int size, boolean reverse);

    private static native void nativeRGB565ToRGBA(byte[] rgb565, byte[] rgba, int size, boolean reverse);
}
