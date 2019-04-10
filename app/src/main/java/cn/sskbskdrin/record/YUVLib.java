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
        NV21((byte) 'N', (byte) 'V', (byte) '2', (byte) '1'), NV12((byte) 'N', (byte) 'V', (byte) '1', (byte) '2'),
        YV12((byte) 'Y', (byte) 'V', (byte) '1', (byte) '2');
        int value;

        Format(byte a, byte b, byte c, byte d) {
            value = a & 0xff;
            value |= (b & 0xff) << 8;
            value |= (c & 0xff) << 16;
            value |= (d & 0xff) << 24;
        }
    }

    public static void toRGBA(byte[] src, byte[] dest, int width, int height, Format format) {
        toRGBA(src, dest, width, height, format, 0);
    }

    public static void toRGBA(byte[] src, byte[] dest, int width, int height, Format format, int rotate) {
        long start = System.currentTimeMillis();

        nativeToArgb(src, dest, width, height, format.value, rotate);

        Log.d(TAG, "toArgb: time=" + (System.currentTimeMillis() - start));
    }

    private static native void nativeToArgb(byte[] src, byte[] dest, int width, int height, int format, int rotate);
}
