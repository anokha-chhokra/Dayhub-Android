package app.dayhub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A wobbly, pen-drawn rounded box: ink outline, flat fill and an optional hard offset shadow.
 * The wobble comes from a seeded random, so a given shape looks the same every time it is drawn.
 * Views using it should pad their content by at least {@link #MARGIN_DP} plus the shadow offset.
 */
public final class HandDrawnDrawable extends Drawable {
    public static final float STROKE_DP = 2f;
    public static final float WOBBLE_DP = 1.5f;
    public static final float MARGIN_DP = STROKE_DP + WOBBLE_DP;
    private static final float STEP_DP = 30f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float margin;
    private final float wobble;
    private final float step;
    private final float shadowOffset;
    private final long seed;
    private boolean pressed;

    public HandDrawnDrawable(Context c, int fillColor, float shadowDp, long seed) {
        float density = c.getResources().getDisplayMetrics().density;
        this.margin = MARGIN_DP * density;
        this.wobble = WOBBLE_DP * density;
        this.step = STEP_DP * density;
        this.shadowOffset = shadowDp * density;
        this.seed = seed;

        int ink = c.getColor(R.color.ink);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(STROKE_DP * density);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(ink);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(ink);
    }

    /** Pressed shapes sink onto their shadow, like a pushed-in button. */
    public void setPressedLook(boolean pressed) {
        if (this.pressed != pressed) {
            this.pressed = pressed;
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect b) {
        path.reset();
        RectF r = new RectF(b.left + margin, b.top + margin,
                b.right - margin - shadowOffset, b.bottom - margin - shadowOffset);
        if (r.width() <= 0 || r.height() <= 0) return;

        Random rnd = new Random(seed);
        List<float[]> pts = new ArrayList<>();
        addEdge(pts, r.left, r.top, r.right, r.top, rnd);
        addEdge(pts, r.right, r.top, r.right, r.bottom, rnd);
        addEdge(pts, r.right, r.bottom, r.left, r.bottom, rnd);
        addEdge(pts, r.left, r.bottom, r.left, r.top, rnd);

        // Smooth closed curve: every point is a control point between segment midpoints.
        int n = pts.size();
        float[] last = pts.get(n - 1);
        float[] first = pts.get(0);
        path.moveTo((last[0] + first[0]) / 2f, (last[1] + first[1]) / 2f);
        for (int i = 0; i < n; i++) {
            float[] p = pts.get(i);
            float[] q = pts.get((i + 1) % n);
            path.quadTo(p[0], p[1], (p[0] + q[0]) / 2f, (p[1] + q[1]) / 2f);
        }
        path.close();
    }

    /** Adds the start point and evenly spaced jittered points of one edge (not its end point). */
    private void addEdge(List<float[]> pts, float x0, float y0, float x1, float y1, Random rnd) {
        float len = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int n = Math.max(1, Math.round(len / step));
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            pts.add(new float[] {
                    x0 + (x1 - x0) * t + jitter(rnd),
                    y0 + (y1 - y0) * t + jitter(rnd)});
        }
    }

    private float jitter(Random rnd) {
        return (rnd.nextFloat() * 2f - 1f) * wobble;
    }

    @Override
    public void draw(Canvas canvas) {
        if (path.isEmpty()) return;
        if (shadowOffset > 0f && !pressed) {
            canvas.save();
            canvas.translate(shadowOffset, shadowOffset);
            canvas.drawPath(path, shadowPaint);
            canvas.restore();
        }
        canvas.save();
        if (pressed) canvas.translate(shadowOffset * 0.75f, shadowOffset * 0.75f);
        canvas.drawPath(path, fillPaint);
        canvas.drawPath(path, strokePaint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        shadowPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        shadowPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
