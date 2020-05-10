package cn.sskbskdrin.record.camera;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by keayuan on 2020/4/1.
 *
 * @author keayuan
 */
public class CameraManager implements Handler.Callback {
    private static final String TAG = "CameraManager";

    private static final int WHAT_RELEASE = 0xff0002;
    private static final int WHAT_ENABLE_CHANGE = 0xff0003;
    private static final int WHAT_START_PREVIEW = 0xff0004;
    private static final int WHAT_TAKE_PICTURE = 0xff0005;

    private static final int MAX_UNSPECIFIED = -1;
    private static final int STOPPED = 0;
    private static final int STARTED = 1;

    private int mMaxHeight = MAX_UNSPECIFIED;
    private int mMaxWidth = MAX_UNSPECIFIED;

    private int mMinWidth = 240;
    private int mMinHeight = 240;

    protected CameraId mCameraIndex = CameraId.BACK;
    private boolean mEnabled = false;
    private int mState = STOPPED;
    private CameraListener mListener;

    //    private HandlerThread workThread;
    //    private Handler workHandler;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private WorkThread workThread;
    private ICamera mCamera;

    public enum CameraId {
        BACK, FRONT, ANY
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case WHAT_ENABLE_CHANGE:
                if (msg.arg1 == 1) {
                    mEnabled = false;
                    checkCurrentState();
                }
                mEnabled = msg.arg1 == 1;
                checkCurrentState();
                break;
            case WHAT_TAKE_PICTURE:
                mCamera.takePicture(this);
                break;
            case WHAT_START_PREVIEW:
                mCamera.startPreview();
                break;
            case WHAT_RELEASE:
                mEnabled = false;
                checkCurrentState();
                mCamera = null;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (workThread != null) {
                            workThread.stop();
                            workThread = null;
                        }
                    }
                });
                break;
            default:
                break;
        }
        return true;
    }

    public void init(Activity context, SurfaceView view, boolean v2) {
        if (mCamera != null) {
            throw new IllegalStateException("已经初始化过");
        }
        mCamera = v2 ? new Camera2Impl(context) : new CameraImpl();
        mCamera.mManager = this;
        mCamera.surfaceView = new WeakReference<>(view);
        mCamera.init(view);
        mCamera.surfaceOrientation = getOrientationDegree(context.getWindowManager().getDefaultDisplay().getRotation());
        workThread = new WorkThread("CameraThread");
        workThread.start();
        workThread.setHandlerCallback(this);
    }

    public void init(Activity context, SurfaceTexture texture, int width, int height) {
        if (mCamera != null) {
            throw new IllegalStateException("已经初始化过");
        }
        mCamera = new CameraImpl();
        mCamera.mManager = this;
        mCamera.viewWidth = width;
        mCamera.viewHeight = height;
        mCamera.surfaceTexture = texture;
        mCamera.init(texture);
        mCamera.surfaceOrientation = getOrientationDegree(context.getWindowManager().getDefaultDisplay().getRotation());
        workThread = new WorkThread("CameraThread");
        workThread.start();
        workThread.setHandlerCallback(this);
    }

    Handler getWorkHandler() {
        return workThread.getHandler();
    }

    /**
     * Sets the camera index
     *
     * @param id new camera index
     */
    public void setCameraId(CameraId id) {
        this.mCameraIndex = id;
    }

    public void release() {
        Log.d(TAG, "release: ");
        getWorkHandler().sendEmptyMessage(WHAT_RELEASE);
    }

    public void setEnabled(final boolean enabled) {
        Log.d(TAG, "setEnabled: ");
        getWorkHandler().obtainMessage(WHAT_ENABLE_CHANGE, enabled ? 1 : 0, 0).sendToTarget();
    }

    public void setCameraListener(CameraListener listener) {
        mListener = listener;
    }

    /**
     * This method sets the maximum size that camera frame is allowed to be. When selecting
     * size - the biggest size which less or equal the size set will be selected.
     * As an example - we set setMaxFrameSize(200,200) and we have 176x152 and 320x240 sizes. The
     * preview frame will be selected with 176x152 size.
     * This method is useful when need to restrict the size of preview frame for some reason (for example for video
     * recording)
     *
     * @param maxWidth  - the maximum width allowed for camera frame.
     * @param maxHeight - the maximum height allowed for camera frame
     */
    public void setMaxFrameSize(int maxWidth, int maxHeight) {
        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;
    }

    public void setMinFrameSize(int minWidth, int minHeight) {
        mMinWidth = minWidth;
        mMinHeight = minHeight;
    }

    /**
     * Called when mSyncObject lock is held
     */
    private void checkCurrentState() {
        Log.d(TAG, "call checkCurrentState " + mEnabled);
        int targetState;

        if (mEnabled) {
            targetState = STARTED;
        } else {
            targetState = STOPPED;
        }

        if (targetState != mState) {
            /* The state change detected. Need to exit the current state and enter target state */
            processExitState(mState);
            mState = targetState;
            processEnterState(mState);
        }
    }

    public int getPreviewWidth() {
        return mCamera.previewWidth;
    }

    public int getPreviewHeight() {
        return mCamera.previewHeight;
    }

    private void processEnterState(int state) {
        Log.d(TAG, "call processEnterState: " + state);
        switch (state) {
            case STARTED:
                if (onEnterStartedState()) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) {
                                mListener.onCameraStarted(mCamera.previewWidth, mCamera.previewHeight);
                            }
                        }
                    });
                } else if (mListener != null) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) {
                                mListener.onCameraError();
                            }
                        }
                    });
                }
                break;
            case STOPPED:
                onEnterStoppedState();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null) {
                            mListener.onCameraStopped();
                        }
                    }
                });
                break;
        }
    }

    private void processExitState(int state) {
        Log.d(TAG, "call processExitState: " + state);
        switch (state) {
            case STARTED:
                onExitStartedState();
                break;
            case STOPPED:
                onExitStoppedState();
                break;
        }
    }

    private void onEnterStoppedState() {
        /* nothing to do */
    }

    private void onExitStoppedState() {
        /* nothing to do */
    }

    // NOTE: The order of bitmap constructor and camera connection is important for android 4.1.x
    // Bitmap must be constructed before surface
    private boolean onEnterStartedState() {
        Log.d(TAG, "call onEnterStartedState");
        if (mCamera.connectCamera()) {
            mCamera.fixSurfaceView();
            return true;
        }
        return false;
    }

    private void onExitStartedState() {
        Log.d(TAG, "disconnectCamera: ");
        mCamera.disconnectCamera();
    }

    /**
     * This helper method can be called by subclasses to select camera preview size.
     * It goes over the list of the supported preview sizes and selects the maximum one which
     * fits both values set via setMaxFrameSize() and surface frame allocated for this view
     *
     * @param supportedSizes supportedSizes
     * @param w              surfaceWidth
     * @param h              surfaceHeight
     * @return optimal frame size
     */
    private static Size selectCameraFrameSize(List<?> supportedSizes, SizeAccessor accessor, int w, int h, int maxW,
                                              int maxH, int minW, int minH) {
        int calcWidth = Math.max(w, h);
        int calcHeight = Math.min(w, h);

        int maxAllowedWidth = maxW > 0 ? maxW : Integer.MAX_VALUE;
        int maxAllowedHeight = maxH > 0 ? maxH : Integer.MAX_VALUE;

        List<Size> list = new ArrayList<>(supportedSizes.size());
        for (Object size : supportedSizes) {
            w = accessor.getWidth(size);
            h = accessor.getHeight(size);
            Log.d(TAG, "calculateCameraFrameSize: " + w + "x" + h);
            if (w <= maxAllowedWidth && h <= maxAllowedHeight && w >= minW && h >= minH) {
                list.add(new Size(w, h));
            }
        }
        return findBestSize(list, calcWidth, calcHeight);
    }

    Size selectCameraFrameSize(List<?> list, SizeAccessor accessor, int w, int h) {
        return selectCameraFrameSize(list, accessor, w, h, mMaxWidth, mMaxHeight, mMinWidth, mMinHeight);
    }

    /**
     * width 总是大于等于height
     */
    private static Size findBestSize(List<Size> list, int width, int height) {
        final int viewAspectRatio = 1000 * width / height;
        Collections.sort(list, new Comparator<Size>() {
            @Override
            public int compare(Size a, Size b) {
                int aspectRatioA = 1000 * a.width / a.height;
                int aspectRatioB = 1000 * b.width / b.height;
                if (aspectRatioA == aspectRatioB) {
                    if (a.width > b.width) return -1;
                    if (a.width < b.width) return 1;
                    if (a.height > b.height) return -1;
                    return a.height < b.height ? 1 : 0;
                }
                int distortionA = Math.abs(aspectRatioA - viewAspectRatio);
                int distortionB = Math.abs(aspectRatioB - viewAspectRatio);
                return (distortionA > distortionB) ? 1 : -1;
            }
        });

        StringBuilder builder = new StringBuilder();
        for (Size size : list) {
            builder.append(size.width).append('x').append(size.height).append(' ');
            if (size.width == width && size.height == height) {
                return size;
            }
        }
        Log.d(TAG, "Supported sizes: " + builder);
        return list.get(0);
    }

    private int getOrientationDegree(int windowDegree) {
        switch (windowDegree) {
            case Surface.ROTATION_0:
                return 90;
            case Surface.ROTATION_180:
                return 270;
            case Surface.ROTATION_270:
                return 180;
            case Surface.ROTATION_90:
            default:
                return 0;
        }
    }

    public interface CameraListener {
        /**
         * This method is invoked when camera preview has started. After this method is invoked
         * the frames will start to be delivered to client via the onCameraFrame() callback.
         *
         * @param width  -  the width of the frames that will be delivered
         * @param height - the height of the frames that will be delivered
         */
        void onCameraStarted(int width, int height);

        /**
         * This method is invoked when camera preview has been stopped for some reason.
         * No frames will be delivered via onCameraFrame() callback after this method is called.
         */
        void onCameraStopped();

        /**
         * This method is invoked when delivery of the frame needs to be done.
         * The returned values - is a modified frame which needs to be displayed on the screen.
         */
        void onCameraFrame(byte[] inputFrame, int format, int width, int height);

        void onCameraError();
    }

    interface SizeAccessor {
        int getWidth(Object obj);

        int getHeight(Object obj);
    }

    static class Size {
        public int width;
        public int height;

        public Size(int w, int h) {
            width = w;
            height = h;
        }

        @Override
        public String toString() {
            return width + "x" + height;
        }
    }

    static abstract class ICamera {

        protected int mPreviewFormat = ImageFormat.NV21;

        private WeakReference<SurfaceView> surfaceView;
        protected SurfaceTexture surfaceTexture;
        protected Surface surface;

        protected CameraManager mManager;

        protected int surfaceOrientation;

        private int viewWidth;
        private int viewHeight;

        protected int previewWidth;
        protected int previewHeight;

        protected ICamera() {}

        protected void init(SurfaceView view) {
            surfaceView = new WeakReference<>(view);
        }

        protected void init(SurfaceTexture texture) {
            surfaceTexture = texture;
        }

        protected Size getViewSize() {
            SurfaceView view = getSurfaceView();
            Log.d(TAG, "getViewSize: " + view.getMeasuredWidth());
            return view == null ? new Size(viewWidth, viewHeight) : new Size(view.getMeasuredWidth(),
                view.getMeasuredHeight());
        }

        protected SurfaceView getSurfaceView() {
            return surfaceView == null ? null : surfaceView.get();
        }

        private void fixSurfaceView() {
            SurfaceView view = getSurfaceView();
            if (view == null) {
                return;
            }
            view.post(new Runnable() {
                @Override
                public void run() {
                    Size size = getViewSize();
                    boolean isP = surfaceOrientation % 180 != 0;
                    if (isP) {
                        size = new Size(size.height, size.width);
                    }
                    float scaleW = size.width * 1f / previewWidth;
                    float scaleH = size.height * 1f / previewHeight;
                    int h = size.height;
                    int w = size.width;
                    if (scaleW > scaleH) {
                        w = (int) (previewWidth * scaleH);
                    } else {
                        h = (int) (previewHeight * scaleW);
                    }
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    lp.width = w;
                    lp.height = h;
                    if (isP) {
                        lp.width = h;
                        lp.height = w;
                    }
                    view.requestLayout();
                    //                    w = lp.width < lp.height ? previewWidth : previewHeight;
                    //                    h = lp.width < lp.height ? previewHeight : previewWidth;
                    view.getHolder().setFixedSize(previewWidth, previewHeight);
                    if (mManager != null && mManager.getWorkHandler() != null) {
                        mManager.getWorkHandler().sendEmptyMessage(WHAT_START_PREVIEW);
                    }
                    Log.d(TAG, "fixSurfaceView: lp=" + lp.width + "x" + lp.height + " view=" + size);
                }
            });
        }

        /**
         * This method is invoked shall perform concrete operation to initialize the camera.
         * CONTRACT: as a result of this method variables mFrameWidth and mFrameHeight MUST be
         * initialized with the size of the Camera frames that will be delivered to external processor.
         */
        abstract boolean connectCamera();

        abstract void startPreview();

        /**
         * Disconnects and release the particular camera object being connected to this surface view.
         * Called when syncObject lock is held
         */
        abstract void disconnectCamera();

        abstract void takePicture(CameraManager manager);
    }

    void onPreviewFrame(byte[] data) {
        if (mListener != null && mCamera != null) {
            mListener.onCameraFrame(data, mCamera.mPreviewFormat, mCamera.previewWidth, mCamera.previewHeight);
        }
    }

    public interface OnTakePictureCallback {
        void onTakePicture(byte[] data);
    }

    private OnTakePictureCallback pictureCallback;

    public void takePicture(boolean flash, OnTakePictureCallback callback) {
        this.flash = flash;
        pictureCallback = callback;
        getWorkHandler().post(new Runnable() {
            @Override
            public void run() {
                if (mCamera != null) {
                    mCamera.takePicture(CameraManager.this);
                }
            }
        });
    }

    boolean flash;

    public void takePicture(OnTakePictureCallback callback) {
        takePicture(false, callback);
    }

    void onPicture(byte[] data) {
        if (pictureCallback != null) {
            pictureCallback.onTakePicture(data);
        }
    }
}
