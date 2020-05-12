package cn.sskbskdrin.record;

import android.util.Log;

/**
 * @author sskbskdrin
 * @date 2019/April/9
 */
public class YUVLib {
    private static final String TAG = "YUVLib";

    static {
        System.loadLibrary("yuv");
    }

    public enum Format {

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

        public int getValue() {
            return value;
        }
    }

    private static int color(byte Y, byte U, byte V) {
        int y = 0xff & Y;
        int u = 0xff & U - 128;
        int v = 0xff & V - 128;
        int y65535 = y << 16;// 0xffff=65535;
        int r = y65535 + 89829 * v;// 1.370705*0xffff=89829.15
        int g = y65535 - 457435 * v + 22127 * u; // 0.698001*0xffff=45743.49 0.337633*0xffff=22126.78
        int b = y65535 + 113535 * u;// 1.732446*0x3fff=113535.85
        r = clamp(r) >> 16;
        g = clamp(g) >> 16;
        b = clamp(b) >> 16;
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int clamp(int v) {
        return v < 65535 ? 65535 : (v > 0xffffff ? 0xffffff : v);
    }

    public static void yuv420spToARGB(byte[] src, int[] dest, int width, int height) {
        int uv = width * height;
        for (int i = 0; i < height; i++) {
            int line = i * width;
            int index, uvIndex;
            for (int j = 0; j < width; j++) {
                index = line + j;
                uvIndex = uv + index >> 1;
                dest[index] = color(src[index], src[uvIndex + 1], src[uvIndex]);
            }
        }
    }


    public static void toABGR(byte[] src, byte[] dest, int width, int height, Format format) {
        toABGR(src, dest, width, height, format, 0);
    }

    public static void toABGR(byte[] src, byte[] dest, int width, int height, Format format, int rotate) {
        toBGRA(src, dest, width, height, format, rotate, false);
    }

    public static void toBGRA(byte[] src, byte[] dest, int width, int height, Format format, int rotate,
                              boolean mirror) {
        toBGRA(src, dest, null, width, height, format, rotate, mirror);
    }

    /**
     * @param src    源数据
     * @param dest   目标数据
     * @param clip   目标数据裁剪 [x,y,w,h]
     * @param width  源数据宽
     * @param height 源数据高
     * @param format 源数据格式
     * @param rotate 旋转角度，0，90，180，270
     * @param mirror 镜像
     */
    private static byte[] cache = {};

    public static void toBGRA(byte[] src, byte[] dest, int[] clip, int width, int height, Format format, int rotate,
                              boolean mirror) {
        long start = System.currentTimeMillis();
        if (cache.length != dest.length) {
            cache = new byte[dest.length];
            Log.v(TAG, "new cache: ");
        }
        nativeByteToBGRA(src, dest, cache, clip, width, height, format.value, rotate, mirror);
        Log.v(TAG, "toArgb: time=" + (System.currentTimeMillis() - start));
    }

    public static void toBGRA(byte[] srcY, byte[] srcU, byte[] srcV, byte[] dest, byte[] cache, int[] clip, int width
        , int height, Format format, int rotate, boolean mirror) {
        long start = System.currentTimeMillis();
        if (cache.length != dest.length) {
            cache = new byte[dest.length];
            Log.v(TAG, "new cache: ");
        }
        nativeYUVToBGRA(srcY, srcU, srcV, dest, cache, clip, width, height, format.value, rotate, mirror);
        Log.v(TAG, "toArgb: time=" + (System.currentTimeMillis() - start));
    }

    private static native void nativeByteToBGRA(byte[] src, byte[] dest, byte[] cache, int[] clip, int width,
                                                int height, int format, int rotate, boolean mirror);

    private static native void nativeYUVToBGRA(byte[] srcY, byte[] srcU, byte[] srcV, byte[] dest, byte[] cache,
                                               int[] clip, int width, int height, int format, int rotate,
                                               boolean mirror);

    public static native void nativeBGRAToColor(byte[] src, int[] colors, int size);

    private static native void nToArgb(byte[] src, byte[] dest, int width, int height, int format, int rotate);
}
