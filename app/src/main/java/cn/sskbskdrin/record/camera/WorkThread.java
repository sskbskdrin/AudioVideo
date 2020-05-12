package cn.sskbskdrin.record.camera;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by keayuan on 2020/5/8.
 *
 * @author keayuan
 */
public final class WorkThread {
    private Handler workHandler;
    private HandlerThread workThread;
    private AtomicBoolean isRunning = new AtomicBoolean();
    private String workName;

    private Handler.Callback callback;

    public WorkThread(String name) {
        workName = name;
    }

    public void start() {
        if (!isRunning.get()) {
            isRunning.set(true);
            workThread = new HandlerThread(workName == null || workName.length() == 0 ? "workThread" : workName);
            workThread.start();
            workHandler = new Handler(workThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (callback != null) {
                        callback.handleMessage(msg);
                    }
                }
            };
        }
    }

    public void post(Runnable runnable) {
        postDelay(runnable, 0);
    }

    public void postDelay(Runnable runnable, long delay) {
        if (workHandler != null) {
            workHandler.postDelayed(runnable, delay);
        }
    }

    public void send(int what) {
        send(what, null);
    }

    public void send(int what, Object obj) {
        send(what, 0, 0, obj);
    }

    public void send(int what, int arg1, int arg2, Object obj) {
        if (workHandler != null) {
            workHandler.removeMessages(what);
            workHandler.obtainMessage(what, arg1, arg2, obj).sendToTarget();
        }
    }

    public Handler getHandler() {
        return workHandler;
    }

    public void setHandlerCallback(Handler.Callback callback) {
        this.callback = callback;
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public void stop() {
        if (isRunning.get()) {
            isRunning.set(false);
            workHandler.removeCallbacksAndMessages(null);
            workHandler = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                workThread.quitSafely();
            } else {
                workThread.quit();
            }
            try {
                workThread.join();
            } catch (InterruptedException ignored) {
            }
            workThread = null;
        }
    }
}
