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
import android.util.SparseArray;
import android.view.View;

/**
 * Created by sskbskdrin on 2020/5/12.
 *
 * @author sskbskdrin
 */
public class DrawSurface extends View implements Handler.Callback {
    private static final String TAG = "DrawSurface";
    SparseArray<Action> map = new SparseArray<>();

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
                dest = new Rect(0, 0, (int) (src.right / scale), (int) (src.bottom / scale));
            }
            canvas.drawBitmap(cacheBitmap, src, dest, null);
        }
        if (rectF != null) {
            canvas.drawRect(rectF, paint);
        }
        for (int i = 0; i < map.size(); i++) {
            Action action = map.valueAt(i);
            if (action != null) {
                action.draw(canvas);
            }
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        cacheBitmap = (Bitmap) msg.obj;
        return true;
    }

    public int addAction(Action action) {
        map.put(action.id, action);
        return action.id;
    }

    public int drawBitmap(Bitmap bitmap, int x, int y) {
        Action action = new BitmapAction(bitmap, x, y);
        addAction(action);
        return action.id;
    }

    public int drawBitmap(int id, Bitmap bitmap, int x, int y) {
        Action action = new BitmapAction(id, bitmap, x, y);
        addAction(action);
        postInvalidate();
        return action.id;
    }

    public void drawPoint(float x, float y, int color) {
        drawPoints(new float[]{x, y}, color);
        postInvalidate();
    }

    public void drawPoints(float[] points, int color) {
        //        addAction(new PointAction(points));
        postInvalidate();
    }

    public void drawPoints(double[] points, int color) {
        //        addAction(new PointAction(tr(points)));
        postInvalidate();
    }

    public void drawLine(float x1, float y1, float x2, float y2, int color) {
        addAction(new LineAction(color, x1, y1, x2, y2));
        postInvalidate();
    }

    public void drawRect(int[] rect, int color) {
        addAction(new RectAction(color, tr(rect)));
    }

    public void drawLines(int[] points, int color) {
        addAction(new LineAction(color, tr(points)));
        postInvalidate();
    }

    public void drawLines(int id, int[] points, int color) {
        addAction(new LineAction(id, color, tr(points)));
        postInvalidate();
    }

    public interface DrawListener {
        void draw(Action action);
    }

    private static Float[] tr(int[] arr) {
        Float[] ret = new Float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            ret[i] = (float) arr[i];
        }
        return ret;
    }

    private static Float[] tr(double[] arr) {
        Float[] ret = new Float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            ret[i] = (float) arr[i];
        }
        return ret;
    }

    public static abstract class Action<T> {
        private static int count = 0;
        protected T[] params;
        protected Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final int id;

        private Action() {
            id = -1;
        }

        Action(T... p) {
            params = p;
            paint.setColor(Color.CYAN);
            id = ++count;
        }

        Action(int color, T... p) {
            paint.setColor(color);
            params = p;
            id = ++count;
        }

        Action(int color, int id, T... p) {
            paint.setColor(color);
            params = p;
            this.id = id;
        }

        public abstract void draw(Canvas canvas);
    }

    private static class PointAction extends Action<Float> {
        PointAction(Float[] p) {
            super(p);
        }

        PointAction(int color, Float[] p) {
            super(color, p);
        }

        int[] color;

        PointAction(int[] color, Float p) {
            super(p);
            this.color = color;
        }

        @Override
        public void draw(Canvas canvas) {
            if (params == null || params.length == 0) return;

            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < params.length; i += 2) {
                if (color != null) {
                    paint.setColor(color[i / 2]);
                }
                canvas.drawCircle(params[i], params[i + 1], 1, paint);
            }
        }
    }

    private static class LineAction extends Action<Float> {
        LineAction(Float... p) {
            super(p);
        }

        LineAction(int color, Float... p) {
            super(color, p);
        }

        LineAction(int id, int color, Float... p) {
            super(color, id, p);
        }

        @Override
        public void draw(Canvas canvas) {
            if (params == null || params.length == 0) return;

            paint.setStrokeWidth(2);
            int i = 0;
            for (; i < params.length - 3; i += 2) {
                canvas.drawLine(params[i], params[i + 1], params[i + 2], params[i + 3], paint);
            }
            canvas.drawLine(params[i], params[i + 1], params[0], params[1], paint);
        }
    }

    private static class BitmapAction extends Action<Bitmap> {
        int left;
        int top;

        BitmapAction(int id, Bitmap bitmap, int x, int y) {
            super(0, id, bitmap);
            left = x;
            top = y;
        }

        BitmapAction(Bitmap bitmap, int x, int y) {
            super(bitmap);
            left = x;
            top = y;
        }

        @Override
        public void draw(Canvas canvas) {
            if (params != null && params.length > 0) {
                canvas.drawBitmap(params[0], left, top, null);
            }
        }
    }

    private static class RectAction extends Action<Float> {

        RectAction(int color, Float... rect) {
            super(color, rect);
        }

        @Override
        public void draw(Canvas canvas) {
            if (params != null) {
                for (int i = 0; i < params.length / 4; i++) {
                    canvas.drawRect(params[0], params[1], params[0] + params[2], params[1] + params[3], paint);
                }
            }
        }
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
