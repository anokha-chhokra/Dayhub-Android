package app.dayhub;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import app.dayhub.data.StopwatchMath;

/**
 * Feature 23: the focus stopwatch, an old pocket stopwatch drawn with the canvas. The dial is
 * re-engraved for whatever length is set: a 25 minute focus gets numerals every 5 minutes and a tick for
 * each minute; 2 hours gets numerals every 20 minutes. The red hand sits at 12 and the yellow wedge is the
 * time still to come. When the length changes, the numerals fade in again and the hand makes one turn.
 *
 * It is drawn in a 240 x 262 box, like the web app's, and scaled to the width it is given.
 */
public final class StopwatchView extends View {
    private static final float W = 240f;
    private static final float H = 262f;
    private static final float CX = 120f;
    private static final float CY = 148f;
    private static final float FACE = 88f;
    private static final float TICK_OUT = 85f;
    private static final float NUMERALS = 63f;
    private static final int CASE = Color.parseColor("#E8B93F");

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int ink;
    private final int paper;
    private final int highlight;
    private final int red;
    private final int muted;

    private StopwatchMath.Plan plan;
    private float handDeg;
    private float wedgeDeg; // where the yellow wedge starts: 0 = the whole dial still to come
    private float numeralAlpha = 1f;
    private ValueAnimator turn;
    private ValueAnimator inkFade;

    public StopwatchView(Context c) {
        super(c);
        ink = c.getColor(R.color.ink);
        paper = c.getColor(R.color.tile);
        highlight = c.getColor(R.color.hi);
        red = c.getColor(R.color.red);
        muted = c.getColor(R.color.muted);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        setContentDescription("Stopwatch");
        setIdle(45);
    }

    private boolean reducedMotion() {
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Wound to {@code minutes}, hand at 12 and the whole dial still to come. */
    public void setIdle(int minutes) {
        StopwatchMath.Plan next = StopwatchMath.dialPlan(minutes);
        boolean first = plan == null;
        boolean changed = plan == null || plan.span != next.span;
        plan = next;
        cancelAnimations();
        handDeg = 0;
        wedgeDeg = 0;
        numeralAlpha = 1f;
        setContentDescription("Stopwatch set for " + StopwatchMath.minutesLabel(plan.span));
        if (changed && !first && !reducedMotion()) {
            // One full turn of the hand tells you the dial was re-engraved for the new length.
            turn = ValueAnimator.ofFloat(0f, 360f);
            turn.setDuration(650);
            turn.setInterpolator(new DecelerateInterpolator(2f));
            turn.addUpdateListener(a -> {
                handDeg = (float) a.getAnimatedValue();
                invalidate();
            });
            turn.start();
            inkFade = ValueAnimator.ofFloat(0f, 1f);
            inkFade.setDuration(350);
            inkFade.addUpdateListener(a -> {
                numeralAlpha = (float) a.getAnimatedValue();
                invalidate();
            });
            inkFade.start();
        }
        invalidate();
    }

    private void cancelAnimations() {
        if (turn != null) turn.cancel();
        if (inkFade != null) inkFade.cancel();
        turn = null;
        inkFade = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimations();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, Math.round(w * H / W));
    }

    // ---------- drawing ----------

    private void solid(Canvas c, float cx, float cy, float r, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawCircle(cx, cy, r, fill);
    }

    private void outlined(Canvas c, float cx, float cy, float r, int color, float width) {
        solid(c, cx, cy, r, color);
        stroke.setColor(ink);
        stroke.setStrokeWidth(width);
        c.drawCircle(cx, cy, r, stroke);
    }

    private void metalRect(Canvas c, float x, float y, float w, float h, float radius) {
        rect.set(x, y, x + w, y + h);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(CASE);
        c.drawRoundRect(rect, radius, radius, fill);
        stroke.setColor(ink);
        stroke.setStrokeWidth(3f);
        c.drawRoundRect(rect, radius, radius, stroke);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = getWidth() / W;
        c.save();
        c.scale(s, s);

        // The bow (the ring to hang it by), the pusher and the crown.
        stroke.setColor(ink);
        stroke.setStrokeWidth(4.5f);
        c.drawCircle(CX, 22f, 14f, stroke);

        c.save();
        c.rotate(42f, CX, CY);
        metalRect(c, 114f, 38f, 12f, 14f, 0f);
        metalRect(c, 109f, 30f, 22f, 14f, 4f);
        c.restore();

        metalRect(c, 112f, 40f, 16f, 16f, 0f);
        metalRect(c, 105f, 28f, 30f, 18f, 4f);
        stroke.setColor(ink);
        stroke.setStrokeWidth(1.8f);
        for (float x : new float[] {112f, 120f, 128f}) c.drawLine(x, 31f, x, 43f, stroke);

        // The case and the face.
        solid(c, CX + 3f, CY + 4f, 100f, Color.argb(41, 27, 26, 23));
        outlined(c, CX, CY, 100f, CASE, 4f);
        outlined(c, CX, CY, FACE, paper, 3f);

        // The yellow wedge: the time that is still to come.
        float left = 360f - wedgeDeg;
        if (left > 0.05f) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(highlight);
            rect.set(CX - (FACE - 2), CY - (FACE - 2), CX + (FACE - 2), CY + (FACE - 2));
            if (left >= 359.95f) c.drawOval(rect, fill);
            else c.drawArc(rect, wedgeDeg - 90f, left, true, fill);
        }

        // Ticks and numerals, engraved for this length.
        stroke.setColor(ink);
        for (StopwatchMath.Tick t : plan.ticks) {
            double[] from = StopwatchMath.polar(CX, CY, t.major ? TICK_OUT - 11 : TICK_OUT - 6, t.deg);
            double[] to = StopwatchMath.polar(CX, CY, TICK_OUT, t.deg);
            stroke.setStrokeWidth(t.major ? 3f : 1.4f);
            c.drawLine((float) from[0], (float) from[1], (float) to[0], (float) to[1], stroke);
        }
        text.setTypeface(Sketch.FONT_BOLD);
        text.setColor(ink);
        text.setAlpha(Math.round(255 * numeralAlpha));
        text.setTextSize(20f);
        Paint.FontMetrics fm = text.getFontMetrics();
        for (StopwatchMath.Label l : plan.labels) {
            double[] p = StopwatchMath.polar(CX, CY, NUMERALS, l.deg);
            c.drawText(l.text, (float) p[0], (float) p[1] - (fm.ascent + fm.descent) / 2f, text);
        }

        // What is printed on the face.
        text.setAlpha(255);
        text.setTypeface(Sketch.FONT);
        text.setColor(muted);
        text.setTextSize(19f);
        c.drawText("focus", CX, CY - 21f, text);
        text.setTypeface(Sketch.FONT_BOLD);
        text.setColor(ink);
        text.setTextSize(22f);
        c.drawText(StopwatchMath.faceLength(plan.span), CX, CY + 33f, text);

        // The glint on the glass.
        stroke.setColor(Color.argb(190, 255, 255, 255));
        stroke.setStrokeWidth(3.5f);
        rect.set(CX - 94f, CY - 94f, CX + 94f, CY + 94f);
        c.drawArc(rect, 292f - 90f, 42f, false, stroke);

        // The red hand, and the hub it turns on.
        c.save();
        c.rotate(handDeg, CX, CY);
        stroke.setColor(red);
        stroke.setStrokeWidth(3.4f);
        c.drawLine(CX, CY + 18f, CX, CY - 66f, stroke);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(red);
        path.reset();
        path.moveTo(CX - 4.5f, CY - 64f);
        path.lineTo(CX + 4.5f, CY - 64f);
        path.lineTo(CX, CY - 80f);
        path.close();
        c.drawPath(path, fill);
        c.drawCircle(CX, CY + 18f, 4.5f, fill);
        c.restore();

        solid(c, CX, CY, 7f, ink);
        solid(c, CX, CY, 2.4f, highlight);
        c.restore();
    }
}
