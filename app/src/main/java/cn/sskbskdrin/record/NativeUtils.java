package cn.sskbskdrin.record;

import android.util.Log;

/**
 * @author sskbskdrin
 * @date 2019/March/29
 */
public class NativeUtils {
    private static final String TAG = "NativeUtils";

    static {
        System.loadLibrary("native-lib");
    }

    private static int color(byte Y, byte U, byte V) {
        int y = 0xff & Y;
        int u = 0xff & U;
        int v = 0xff & V;
        int r = y + (int) (1.370705f * (v - 128));
        int g = y - (int) (0.698001f * (v - 128) + 0.337633f * (u - 128));
        int b = y + (int) (1.732446f * (u - 128));
        r = clamp(r);
        g = clamp(g);
        b = clamp(b);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public static byte[] NV21toNV12(byte[] data, int width, int height) {
        byte temp;
        for (int i = width * height; i < data.length; i += 2) {
            temp = data[i];
            data[i] = data[i + 1];
            data[i + 1] = temp;
        }
        return data;
    }

    /**
     * YYYY
     * YYYY
     * VU
     * VU
     *
     * @param data
     * @param width
     * @param height
     * @return
     */
    public static int[] NV21toARGB(byte[] data, int width, int height) {
        int size = width * height;
        int[] pixels = new int[size];
        byte y, u, v;
        int index;
        for (int h = 0; h < height; h++) {
            int line = width * (h / 2);
            for (int w = 0; w < width; w++) {
                index = (w % 2 == 0 ? w : w - 1) + line;

                y = data[width * h + w];
                u = data[size + index + 1];
                v = data[size + index];

                pixels[width * h + w] = color(y, u, v);
            }
        }
        return pixels;
    }

    /**
     * YYYY
     * YYYY
     * UV
     * UV
     *
     * @param data
     * @param width
     * @param height
     * @return
     */
    public static int[] NV12toARGB(byte[] data, int width, int height) {
        int size = width * height;
        int[] pixels = new int[size];
        byte y, u, v;
        int index;
        for (int h = 0; h < height; h++) {
            int line = width * (h / 2);
            for (int w = 0; w < width; w++) {
                index = (w % 2 == 0 ? w : w - 1) + line;

                y = data[width * h + w];
                v = data[size + index + 1];
                u = data[size + index];

                pixels[width * h + w] = color(y, u, v);
            }
        }
        return pixels;
    }

    /**
     * YYYY
     * YYYY
     * VV
     * UU
     *
     * @param data
     * @param width
     * @param height
     * @return
     */
    public static int[] YV12toARGB(byte[] data, int width, int height) {
        final int size = width * height;
        final int indexU = size + size / 4;
        int[] pixels = new int[size];
        byte y, u, v;
        int index;
        for (int h = 0; h < height; h++) {
            int line = (width / 2) * (h / 2);
            for (int w = 0; w < width; w++) {
                index = w / 2 + line;
                if (1382400 <= index + indexU) {
                    break;
                }
                y = data[width * h + w];
                v = data[size + index];
                u = data[indexU + index];
                pixels[width * h + w] = color(y, u, v);
            }
        }
        return pixels;
    }

    private static byte[] rotate270(byte[] data, int width, int height) {
        int size = width * height;
        byte[] temp = new byte[data.length];
        int index = 0;
        for (int i = width - 1; i >= 0; i--) {
            for (int j = 0; j < height; j++) {
                temp[index++] = data[j * width + i];
            }
        }

        for (int i = width - 1; i >= 0; i -= 2) {
            for (int j = 0; j < height / 2; j++) {
                temp[index++] = data[size + j * width + i - 1];
                temp[index++] = data[size + j * width + i];
            }
        }

        return temp;
    }

    private static byte[] rotate180(byte[] data, int width, int height) {
        int size = width * height;
        byte[] temp = new byte[data.length];
        int index = 0;
        for (int i = height - 1; i >= 0; i--) {
            for (int j = width - 1; j >= 0; j--) {
                temp[index++] = data[i * width + j];
            }
        }
        for (int i = data.length - 1; i >= size; i -= 2) {
            temp[index++] = data[i - 1];
            temp[index++] = data[i];
        }
        return temp;
    }

    private static byte[] rotate90(byte[] data, int width, int height) {
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

    public static byte[] rotateYV90(byte[] data, int width, int height) {
        int size = width * height;
        byte[] temp = new byte[data.length];
        int index = 0;
        for (int i = 0; i < width; i++) {
            for (int j = height - 1; j >= 0; j--) {
                temp[index++] = data[j * width + i];
            }
        }
        int indexU = (int) (size * 1.25f);
        width /= 2;
        height /= 2;
        for (int i = 0; i < width; i++) {
            for (int j = height - 1; j >= 0; j--) {
                temp[index++] = data[size + j * width + i];
                temp[indexU++] = data[(int) (size * 1.25) + j * width + i];
            }
        }
        return temp;
    }

    public static byte[] rotateYV180(byte[] data, int width, int height) {
        int size = width * height;
        byte[] temp = new byte[data.length];
        int index = 0;
        for (int i = height - 1; i >= 0; i--) {
            for (int j = width - 1; j >= 0; j--) {
                temp[index++] = data[i * width + j];
            }
        }
        int indexU = (int) (size * 1.25f);
        width /= 2;
        height /= 2;
        for (int i = height - 1; i >= 0; i--) {
            for (int j = width - 1; j >= 0; j--) {
                temp[index++] = data[size + i * width + j];
                temp[indexU++] = data[(int) (size * 1.25) + i * width + j];
            }
        }
        return temp;
    }

    public static byte[] rotateYV270(byte[] data, int width, int height) {
        int size = width * height;
        byte[] temp = new byte[data.length];
        int index = 0;
        for (int i = width - 1; i >= 0; i--) {
            for (int j = 0; j < height; j++) {
                temp[index++] = data[j * width + i];
            }
        }
        int indexU = (int) (size * 1.25f);
        width /= 2;
        height /= 2;
        for (int i = width - 1; i >= 0; i--) {
            for (int j = 0; j < height; j++) {
                temp[index++] = data[size + j * width + i];
                temp[indexU++] = data[(int) (size * 1.25) + j * width + i];
            }
        }
        return temp;
    }

    public static byte[] rotateYV(byte[] bytes, int width, int height, int degree) {
        if (degree == 0) {
            return bytes;
        } else if (degree == 90) {
            return rotateYV90(bytes, width, height);
        } else if (degree == 180) {
            return rotateYV180(bytes, width, height);
        } else if (degree == 270) {
            return rotateYV270(bytes, width, height);
        } else {
            Log.w(TAG, "rotate fail, degree=" + degree);
        }
        return bytes;
    }

    public static byte[] rotateNV(byte[] bytes, int width, int height, int degree) {
        if (degree == 0) {
            return bytes;
        } else if (degree == 90) {
            return rotate90(bytes, width, height);
        } else if (degree == 180) {
            return rotate180(bytes, width, height);
        } else if (degree == 270) {
            return rotate270(bytes, width, height);
        } else {
            Log.w(TAG, "rotate fail, degree=" + degree);
        }
        return bytes;
    }

    public static native int[] nativeNV21toARGB(byte[] bytes, int width, int height);

    public static native int[] nativeNV12toARGB(byte[] bytes, int width, int height);

    public static native int[] nativeYV12toARGB(byte[] bytes, int width, int height);

    public static native byte[] nativeRotateNV(byte[] bytes, int width, int height, int degree);

}
