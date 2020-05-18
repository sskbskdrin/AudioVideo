package cn.sskbskdrin.lib.face;

import android.content.Context;

public class FaceLandmarker {
    private static final String TAG = "FaceLandmarker";

    static {
        System.loadLibrary("SeetaFace");
    }

    public long id;

    public FaceLandmarker(Context context) {
        id = nativeInitLandmarker(FaceDetector.getPath("pd_2_00_pts5.dat", context));
    }

    public int mark(byte[] src, int width, int height, double[] destPoints, int[] rect, int faceCount) {
        long st = System.currentTimeMillis();
        int ret = nativeMark(id, src, width, height, destPoints, rect, faceCount);
        if (LogUtil.isLoggable()) {
            LogUtil.d(TAG, "mark: time=" + (System.currentTimeMillis() - st));
        }
        return ret;
    }

    /**
     * 释放引擎
     */
    public void releaseEngine() {
        if (id != 0) {
            nativeReleaseLandmarker(id);
            id = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (id != 0) {
            nativeReleaseLandmarker(id);
            id = 0;
        }
        super.finalize();
    }

    private static native long nativeInitLandmarker(String markerModelFile);

    private static native int nativeMark(long id, byte[] src, int width, int height, double[] destPoints, int[] rect,
                                         int faceCount);

    private static native void nativeReleaseLandmarker(long id);
}
