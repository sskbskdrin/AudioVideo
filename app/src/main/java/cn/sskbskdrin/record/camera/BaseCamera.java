package cn.sskbskdrin.record.camera;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author sskbskdrin
 * @date 2019/April/4
 */
public abstract class BaseCamera {

    private static final int OPEN_CAMERA = 1001;
    private static final int CLOSE_CAMERA = 2001;
    private static final int PREVIEW_FRAME = 3001;

    protected BackgroundHandler mBackgroundHandler;
    protected CameraListener mCameraListener;
    protected Point mPreviewSize;
    protected int mPreviewFormat = ImageFormat.YV12;
    protected boolean frameCallback = false;

    protected BaseCamera(@NotNull CameraListener listener) {
        mCameraListener = listener;
    }

    protected void openCamera() {}

    protected void closeCamera() {
    }

    public void open() {
        if (mBackgroundHandler == null) {
            new HandlerThread("CameraBackground") {
                @Override
                protected void onLooperPrepared() {
                    mBackgroundHandler = new BackgroundHandler(BaseCamera.this);
                    openCamera();
                }
            }.start();
        }
    }

    public void close() {
        if (mBackgroundHandler != null) {
            Message.obtain(mBackgroundHandler, CLOSE_CAMERA).sendToTarget();
            mBackgroundHandler = null;
        }
    }

    public void setPreviewFormat(int format) {
        mPreviewFormat = format;
    }

    public void setCameraListener(CameraListener listener) {
        this.mCameraListener = listener;
    }

    protected void sendPreviewFrame(byte[] data) {
        if (mBackgroundHandler != null) {
            Message.obtain(mBackgroundHandler, PREVIEW_FRAME, data).sendToTarget();
        }
    }

    public void setFrameCallback(boolean callback) {
        frameCallback = callback;
    }

    public BackgroundHandler getBackgroundHandler() {
        return mBackgroundHandler;
    }

    protected static class BackgroundHandler extends Handler {
        private WeakReference<BaseCamera> mWeakManager;

        private BackgroundHandler(BaseCamera manager) {
            mWeakManager = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message msg) {
            BaseCamera manager = mWeakManager.get();
            if (manager != null && msg != null) {
                switch (msg.what) {
                    case OPEN_CAMERA:
                        manager.openCamera();
                        break;
                    case CLOSE_CAMERA:
                        manager.closeCamera();
                        getLooper().quitSafely();
                        break;
                    case PREVIEW_FRAME:
                        if (manager.mCameraListener != null) {
                            manager.mCameraListener.onPreviewFrame((byte[]) msg.obj);
                        }
                        break;
                    default:
                }
            }
        }
    }

    public void fixSurfaceView(SurfaceView view) {
        Point maxPoint = new Point(view.getMeasuredWidth(), view.getMeasuredHeight());
        Point p = findBestSurfacePoint(mPreviewSize, maxPoint);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = p.x;
            params.height = p.y;
            view.setLayoutParams(params);
        }
    }

    private static Point findBestSurfacePoint(Point cameraResolution, Point maxPoint) {
        if (cameraResolution == null || maxPoint == null || maxPoint.x == 0 || maxPoint.y == 0) {
            return maxPoint;
        }
        double scaleX, scaleY, scale;
        if (maxPoint.x < maxPoint.y) {
            scaleX = cameraResolution.x * 1.0f / maxPoint.y;
            scaleY = cameraResolution.y * 1.0f / maxPoint.x;
        } else {
            scaleX = cameraResolution.x * 1.0f / maxPoint.x;
            scaleY = cameraResolution.y * 1.0f / maxPoint.y;
        }
        scale = scaleX > scaleY ? scaleX : scaleY;
        Point result = new Point();
        if (maxPoint.x < maxPoint.y) {
            result.x = (int) (cameraResolution.y / scale);
            result.y = (int) (cameraResolution.x / scale);
        } else {
            result.x = (int) (cameraResolution.x / scale);
            result.y = (int) (cameraResolution.y / scale);
        }
        return result;
    }

    public enum CameraID {
        /**
         * 后置
         */
        CAMERA_BACK,
        /**
         * 前置
         */
        CAMERA_FRONT
    }

    public interface CameraListener {
        /**
         * android.hardware.camera2 使用
         *
         * @param list 添加Surface到list
         */
        void getSurfaceList(ArrayList<Surface> list);

        /**
         * android.hardware.Camera 使用
         */
        SurfaceHolder getSurfaceHolder();

        /**
         * 根据支持的列表选择大小
         *
         * @param list 支持的列表
         */
        Point getPreviewSize(List<Point> list);

        /**
         * 获取前置或后置摄像头
         */
        CameraID getCameraId();

        /**
         * 预览数据回调
         */
        void onPreviewFrame(byte[] data);
    }
}
