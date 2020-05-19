package cn.sskbskdrin.lib.face;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class FaceDetector {

    static {
        System.loadLibrary("SeetaFace");
    }

    private static final String TAG = FaceDetector.class.getSimpleName();

    private long id;

    public FaceDetector(Context context) {
        id = nativeInitDetection(getPath("fd_2_00.dat", context));
    }

    /**
     * 检测图片
     *
     * @return 识别结果
     */
    public int detect(byte[] src, int width, int height, int[] rect, int maxFaceCount) {
        Log.d(TAG, "detect: len=" + src.length + " " + width + "," + height);
        if (rect.length < maxFaceCount * 4) {
            throw new IllegalArgumentException("rect size too small");
        }
        Arrays.fill(rect, 0);
        long st = System.currentTimeMillis();
        int ret = nativeDetection(id, src, width, height, rect, maxFaceCount);
        if (LogUtil.isLoggable()) {
            LogUtil.d(TAG, "detect: time=" + (System.currentTimeMillis() - st));
        }
        return ret;
    }

    //该函数主要用来完成载入外部模型文件时，获取文件的路径加文件名
    static String getPath(String file, Context context) {
        File outFile = new File(context.getFilesDir(), file);
        if (outFile.exists()) {
            return outFile.getAbsolutePath();
        }
        AssetManager assetManager = context.getAssets();
        BufferedInputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            outFile = new File(context.getFilesDir(), file);
            FileOutputStream os = new FileOutputStream(outFile);
            os.write(data);
            os.close();
            return outFile.getAbsolutePath();
        } catch (IOException ex) {
            LogUtil.e("FileUtil", "Failed to upload a file");
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return "";
    }

    /**
     * 释放引擎
     */
    public void releaseEngine() {
        if (id != 0) {
            nativeReleaseDetection(id);
            id = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (id != 0) {
            nativeReleaseDetection(id);
            id = 0;
        }
        super.finalize();
    }

    private static native long nativeInitDetection(String detectModelFile);

    private static native int nativeDetection(long id, byte[] data, int width, int height, int[] rect,
                                              int maxFaceCount);

    private static native void nativeReleaseDetection(long id);
}
