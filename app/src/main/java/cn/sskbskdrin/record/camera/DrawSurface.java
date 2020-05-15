package cn.sskbskdrin.record.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        workThread = new WorkThread("DrawThread");
        workThread.start();
        workThread.setHandlerCallback(this);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
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

    public void send(RectF rect) {
        rectF.left = rect.left * width;
        rectF.top = rect.top * height;
        rectF.right = rect.right * width;
        rectF.bottom = rect.bottom * height;
        postInvalidate();
    }

    private RectF rectF = new RectF();
    int width;
    int height;

    Rect src;
    Rect dest;
    Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    protected void onDraw(Canvas canvas) {
        if (width == 0) {
            width = getMeasuredWidth();
            height = getMeasuredHeight();
        }
        canvas.drawColor(0);
        if (cacheBitmap != null) {
            if (src == null || src.right != cacheBitmap.getWidth() || src.bottom != cacheBitmap.getHeight()) {
                bitmapPaint.setAlpha(0xa0);
                src = new Rect(0, 0, cacheBitmap.getWidth(), cacheBitmap.getHeight());

                float scale = Math.max(src.right * 1f / width, src.bottom * 1f / height);
                dest = new Rect(0, 0, (int) (src.right/ scale), (int) (src.bottom / scale));
            }
            canvas.drawBitmap(cacheBitmap, src, dest, bitmapPaint);
        }
        if (rectF != null) {
            canvas.drawRect(rectF, paint);
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

    public enum AlignMode {
        LEFT_TOP, LEFT_CENTER, LEFT_BOTTOM, TOP_CENTER, RIGHT_TOP, RIGHT_CENTER, RIGHT_BOTTOM, BOTTOM_CENTER, CENTER
    }

    public static void drawText(Canvas canvas, String text, float x, float y, AlignMode mode, Paint paint) {
        Rect r = new Rect();
        paint.getTextBounds(text, 0, text.length(), r);
        float height = r.height();
        y -= r.bottom;
        switch (mode) {
            case LEFT_TOP:
                paint.setTextAlign(Paint.Align.LEFT);
                y += height;
                break;
            case LEFT_CENTER:
                paint.setTextAlign(Paint.Align.LEFT);
                y += height / 2;
                break;
            case LEFT_BOTTOM:
                paint.setTextAlign(Paint.Align.LEFT);
                break;
            case TOP_CENTER:
                paint.setTextAlign(Paint.Align.CENTER);
                y += height;
                break;
            case RIGHT_TOP:
                paint.setTextAlign(Paint.Align.RIGHT);
                y += height;
                break;
            case RIGHT_CENTER:
                paint.setTextAlign(Paint.Align.RIGHT);
                y += height / 2;
                break;
            case RIGHT_BOTTOM:
                paint.setTextAlign(Paint.Align.RIGHT);
                break;
            case BOTTOM_CENTER:
                paint.setTextAlign(Paint.Align.CENTER);
                break;
            case CENTER:
                paint.setTextAlign(Paint.Align.CENTER);
                y += height / 2;
                break;
        }
        canvas.drawText(text, x, y, paint);
    }
}
