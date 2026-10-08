package app.dayhub;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A dark hand-drawn toast with an optional Undo action. Closes itself after a few seconds. */
public final class UndoToast extends LinearLayout {
    private static final long PLAIN_MS = 2500;
    private static final long UNDO_MS = 5000;

    private final Runnable onGone;
    private final long lifetimeMs;
    private final Runnable autoClose = () -> dismiss(true);
    private boolean gone;

    public UndoToast(Context c, CharSequence message, Runnable undo, Runnable onGone) {
        super(c);
        this.onGone = onGone;
        this.lifetimeMs = undo != null ? UNDO_MS : PLAIN_MS;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(new HandDrawnDrawable(c, c.getColor(R.color.ink), 0f, 8000));
        setPadding(Sketch.dp(c, 18), Sketch.dp(c, 12), Sketch.dp(c, 12), Sketch.dp(c, 12));

        TextView text = Sketch.label(c, message, 16, false, R.color.paper);
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        if (undo != null) {
            TextView action = Sketch.label(c, "Undo", 16, true, R.color.hi);
            action.setPadding(Sketch.dp(c, 14), Sketch.dp(c, 6), Sketch.dp(c, 8), Sketch.dp(c, 6));
            action.setClickable(true);
            action.setOnClickListener(v -> {
                if (gone) return;
                undo.run();
                dismiss(true);
            });
            addView(action);
        }
    }

    /** Adds the toast at the bottom of the host, {@code bottomMargin} px above its bottom edge. */
    public void show(ViewGroup host, int bottomMargin) {
        Context c = getContext();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.setMargins(Sketch.dp(c, 16), 0, Sketch.dp(c, 16), bottomMargin);
        host.addView(this, lp);
        setAlpha(0f);
        setTranslationY(Sketch.dp(c, 24));
        animate().alpha(1f).translationY(0f).setDuration(180);
        postDelayed(autoClose, lifetimeMs);
    }

    public void dismiss(boolean animate) {
        if (gone) return;
        gone = true;
        removeCallbacks(autoClose);
        Runnable finish = () -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            onGone.run();
        };
        if (animate) {
            animate().alpha(0f).translationY(Sketch.dp(getContext(), 16)).setDuration(150)
                    .withEndAction(finish);
        } else {
            finish.run();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(autoClose);
        super.onDetachedFromWindow();
    }
}
