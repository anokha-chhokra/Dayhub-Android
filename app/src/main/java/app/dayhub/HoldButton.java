package app.dayhub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.TextView;

import app.dayhub.data.HoldToEnd;

/**
 * Feature 26: a button that only works if you keep pressing it, so ending a focus session early is a
 * decision and not a slip. A yellow fill grows across it over eight seconds; lifting your finger, or sliding
 * off the button, empties it again. A quick tap does nothing but say how to use it.
 */
public final class HoldButton extends TextView {
    private static int nextSeed = 700;

    private final HoldToEnd hold = new HoldToEnd();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float inset;
    private final float shadow;
    private Runnable onHeld;
    private Runnable onTap;

    private final Runnable finished = this::finishHold;

    public HoldButton(Context c, CharSequence label) {
        super(c);
        float density = c.getResources().getDisplayMetrics().density;
        inset = (HandDrawnDrawable.MARGIN_DP + 1f) * density;
        shadow = 3f * density;
        setBackground(new HandDrawnDrawable(c, c.getColor(R.color.tile), 3f, nextSeed++));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(c.getColor(R.color.hi));
        setText(label);
        setTypeface(Sketch.FONT_BOLD);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        setTextColor(c.getColor(R.color.ink));
        setGravity(Gravity.CENTER);
        setPadding(Sketch.dp(c, 22), Sketch.dp(c, 12), Sketch.dp(c, 25), Sketch.dp(c, 15));
        setMinWidth(Sketch.dp(c, 220));
        setClickable(true);
        setFocusable(true);
        setHapticFeedbackEnabled(false);
    }

    /** What happens once the button has been held for the whole eight seconds. */
    public void setOnHeld(Runnable r) {
        onHeld = r;
    }

    /** What happens when it is only tapped (a hint on how to use it). */
    public void setOnTap(Runnable r) {
        onTap = r;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // A scroll view around us must not take the touch away from a long hold.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                hold.press(SystemClock.uptimeMillis());
                handler.removeCallbacks(finished);
                handler.postDelayed(finished, hold.remainingMs(SystemClock.uptimeMillis()));
                postInvalidateOnAnimation();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (hold.holding() && !insideView(e)) stopHold();
                return true;
            case MotionEvent.ACTION_UP:
                boolean tapped = hold.holding();
                stopHold();
                if (tapped && onTap != null) onTap.run();
                return true;
            case MotionEvent.ACTION_CANCEL:
                stopHold();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private boolean insideView(MotionEvent e) {
        return e.getX() >= 0 && e.getX() <= getWidth() && e.getY() >= 0 && e.getY() <= getHeight();
    }

    private void stopHold() {
        hold.release();
        handler.removeCallbacks(finished);
        invalidate();
    }

    private void finishHold() {
        if (!hold.complete(SystemClock.uptimeMillis())) return;
        stopHold();
        if (onHeld != null) onHeld.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopHold();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float progress = hold.progress(SystemClock.uptimeMillis());
        if (progress > 0f) {
            float left = inset;
            float right = getWidth() - inset - shadow;
            rect.set(left, inset, left + (right - left) * progress, getHeight() - inset - shadow);
            c.drawRect(rect, fill);
            postInvalidateOnAnimation();
        }
        super.onDraw(c); // the text goes on top of the fill
    }

    @Override
    public boolean performClick() {
        // Accessibility "click": a tap cannot end focus, so say how.
        if (onTap != null) onTap.run();
        return super.performClick();
    }
}
