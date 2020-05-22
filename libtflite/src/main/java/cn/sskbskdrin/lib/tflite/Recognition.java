package cn.sskbskdrin.lib.tflite;

import android.graphics.RectF;

import java.util.Locale;

/**
 * Created by keayuan on 2020/5/14.
 *
 * @author keayuan
 */
public class Recognition {
    public final int id;

    /**
     * Display name for the recognition.
     */
    public final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     */
    public final float confidence;

    /**
     * Optional location within the source image for the location of the recognized object.
     */
    public final RectF location;

    public Recognition(final int id, final String title, final float confidence, final RectF location) {
        this.id = id;
        this.title = title;
        this.confidence = confidence;
        this.location = location;
    }

    public void scaleLocation(int[] rect, int size) {
        scaleLocation(rect, 0, size);
    }

    public void scaleLocation(int[] rect, int offset, int size) {
        rect[offset] = (int) (location.left * size);
        rect[offset + 1] = (int) (location.top * size);
        rect[offset + 2] = (int) ((location.right - location.left) * size);
        rect[offset + 3] = (int) ((location.bottom - location.top) * size);
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "Recognition: id=%d title=%s score=%.4f %s", id, title, confidence,
            location
            .toString());
    }
}
