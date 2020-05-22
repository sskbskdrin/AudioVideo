package cn.sskbskdrin.record.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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

    float srcWidth;
    float srcHeight;

    public void setSrcFrame(int width, int height) {
        srcWidth = width;
        srcHeight = height;
    }

    public void send(Bitmap bitmap) {
        cacheBitmap = bitmap;
        postInvalidate();
    }

    Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0);
        //        if (cacheBitmap != null) {
        //            if (src == null || src.right != cacheBitmap.getWidth() || src.bottom != cacheBitmap.getHeight()) {
        //                bitmapPaint.setAlpha(0xa0);
        //                src = new Rect(0, 0, cacheBitmap.getWidth(), cacheBitmap.getHeight());
        //
        //                float scale = Math.max(src.right * 1f / width, src.bottom * 1f / height);
        //                dest = new Rect(0, 0, (int) (src.right / scale), (int) (src.bottom / scale));
        //            }
        //            canvas.drawBitmap(cacheBitmap, src, dest, null);
        //        }
        //        if (rectF != null) {
        //            canvas.drawRect(rectF, paint);
        //        }

        canvas.scale(getWidth() / srcWidth, getWidth() / srcWidth);
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

    public void clean() {
        map.clear();
    }

    private int addAction(Action action) {
        map.put(action.id, action);
        return action.id;
    }

    public void drawBitmap(Bitmap bitmap) {
        drawBitmap(bitmap, 0, 0);
    }

    public void drawBitmap(Bitmap bitmap, int x, int y) {
        Action action = new BitmapAction(bitmap.hashCode(), bitmap, x, y);
        addAction(action);
    }

    public void drawPoint(float x, float y, int color) {
        drawPoints((x + "x" + y).hashCode(), color, 3, new float[]{x, y});
    }

    public void drawPoints(double[] points, int color) {
        drawPoints(points.hashCode(), color, 2, tr(points));
    }

    public void drawPoints(float[] points, int color) {
        drawPoints(points.hashCode(), color, 2, points);
    }

    private void drawPoints(int id, int color, float radius, float[] points) {
        addAction(new PointAction(id, color, radius, points));
    }

    public void drawRect(int[] rect, int color) {
        addAction(new RectAction(rect.hashCode(), color, tr(rect)));
    }

    public void drawText(CharSequence text, float x, float y, AlignMode mode) {
        addAction(new TextAction(text, x, y, mode));
    }

    public void drawText(CharSequence text, float x, float y) {
        addAction(new TextAction(text, x, y, AlignMode.LEFT_TOP));
    }

    public void drawLines(int color, int... points) {
        addAction(new LineAction(points.hashCode(), color, tr(points)));
    }

    public void end() {
        postInvalidate();
    }

    public interface DrawListener {
        void draw(Action action);
    }

    private static float[] tr(int[] arr) {
        float[] ret = new float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            ret[i] = (float) arr[i];
        }
        return ret;
    }

    private static float[] tr(double[] arr) {
        float[] ret = new float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            ret[i] = (float) arr[i];
        }
        return ret;
    }

    public static abstract class Action<T> {
        private static int count = 0;
        protected T params;
        protected Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final int id;

        private Action() {
            id = -1;
        }

        Action(T p) {
            params = p;
            paint.setColor(Color.CYAN);
            id = ++count;
        }

        Action(int color, T p) {
            paint.setColor(color);
            params = p;
            id = ++count;
        }

        Action(int id, int color, T p) {
            paint.setColor(color);
            params = p;
            this.id = id;
        }

        public abstract void draw(Canvas canvas);
    }

    private static class PointAction extends Action<float[]> {
        float radius = 2;

        PointAction(int color, float radius, float[] p) {
            super(color, p);
            this.radius = radius;
        }

        PointAction(int id, int color, float radius, float[] p) {
            super(id, color, p);
            this.radius = radius;
        }

        @Override
        public void draw(Canvas canvas) {
            if (params == null) return;

            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < params.length; i += 2) {
                canvas.drawCircle(params[i], params[i + 1], radius, paint);
            }
        }
    }

    private static class LineAction extends Action<float[]> {
        LineAction(float[] p) {
            super(p);
        }

        LineAction(int color, float[] p) {
            super(color, p);
        }

        LineAction(int id, int color, float[] p) {
            super(id, color, p);
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
            super(id, 0, bitmap);
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
            if (params != null) {
                canvas.drawBitmap(params, left, top, null);
            }
        }
    }

    private static class RectAction extends Action<float[]> {

        RectAction(int color, float[] rect) {
            super(color, rect);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
        }

        RectAction(int id, int color, float[] rect) {
            super(id, color, rect);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
        }

        @Override
        public void draw(Canvas canvas) {
            if (params != null) {
                for (int i = 0; i < params.length - 3; i += 4) {
                    canvas.drawRect(params[i], params[i + 1], params[i] + params[i + 2],
                        params[i + 1] + params[i + 3], paint);
                }
            }
        }
    }

    private static class TextAction extends Action<CharSequence> {

        private float x, y;
        private AlignMode mode;

        TextAction(CharSequence text, float left, float top, AlignMode mode) {
            this(text, left, top, Color.RED);
            paint.setColor(Color.RED);
            x = left;
            y = top;
            this.mode = mode;
        }

        TextAction(CharSequence text, float left, float top, int color) {
            super(text.hashCode(), color, text);
        }

        @Override
        public void draw(Canvas canvas) {
            if (params != null) {
                drawText(canvas, params.toString(), x, y, mode, paint);
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
