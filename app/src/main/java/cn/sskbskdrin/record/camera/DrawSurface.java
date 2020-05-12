package cn.sskbskdrin.record.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by sskbskdrin on 2020/5/12.
 *
 * @author sskbskdrin
 */
public class DrawSurface extends View implements Handler.Callback {
    public DrawSurface(Context context) {
        this(context, null);
    }

    public DrawSurface(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawSurface(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    WorkThread workThread;
    Bitmap cacheBitmap;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        workThread = new WorkThread("DrawThread");
        workThread.start();
        workThread.setHandlerCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        workThread.stop();
        workThread = null;
    }

    public void send(Bitmap bitmap) {
        //        workThread.send(0, bitmap);
        cacheBitmap = bitmap;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0);
        if (cacheBitmap != null) {
            canvas.drawBitmap(cacheBitmap, 0, 0, null);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        cacheBitmap = (Bitmap) msg.obj;
        //        Canvas canvas = getHolder().lockCanvas();
        //        canvas.drawColor(0);
        //        canvas.drawBitmap(cacheBitmap, 0, 0, null);
        //        getHolder().unlockCanvasAndPost(canvas);
        return true;
    }
}
