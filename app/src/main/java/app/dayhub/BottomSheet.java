package app.dayhub;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * A hand-drawn sheet that slides up over a dimmed scrim. Tapping the scrim or pressing back
 * dismisses it. Add it with {@link #show(ViewGroup, int)}; it removes itself when closed.
 */
public final class BottomSheet extends FrameLayout {
    private static int nextSeed = 6000;

    private final View scrim;
    private final LinearLayout panel;
    private final BoundedScrollView bodyScroll;
    private boolean closing;
    private Runnable onDismiss;

    public BottomSheet(Context c, CharSequence title, View body) {
        super(c);
        scrim = new View(c);
        scrim.setBackgroundColor(Color.argb(0x66, 0x1B, 0x1A, 0x17));
        scrim.setOnClickListener(v -> dismiss());
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true); // swallow taps so they do not reach the scrim
        panel.setBackground(new HandDrawnDrawable(c, c.getColor(R.color.tile), 0f, nextSeed++));
        if (title != null) {
            panel.addView(Sketch.label(c, title, 22, true, R.color.ink));
        }
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = Sketch.dp(c, 12);
        // A tall form scrolls inside the sheet instead of running off the screen.
        bodyScroll = new BoundedScrollView(c);
        bodyScroll.setMaxHeight((int) (c.getResources().getDisplayMetrics().heightPixels * 0.7f));
        bodyScroll.addView(body);
        panel.addView(bodyScroll, bodyParams);
        addView(panel);
    }

    public void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    /** Adds the sheet to the host and slides it up. {@code bottomInset} is the nav bar height in px. */
    public void show(ViewGroup host, int bottomInset) {
        Context c = getContext();
        int side = Sketch.dp(c, 10);
        int hidden = Sketch.dp(c, 14); // the bottom outline sits off-screen
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.setMargins(side, 0, side, -hidden);
        panel.setLayoutParams(lp);
        panel.setPadding(Sketch.dp(c, 20), Sketch.dp(c, 18), Sketch.dp(c, 20),
                hidden + Sketch.dp(c, 18) + bottomInset);
        // The keyboard shows and hides after the sheet opens: keep the sheet above it.
        setOnApplyWindowInsetsListener((v, insets) -> {
            int[] in = EdgeToEdgeShell.insetsOf(insets);
            panel.setPadding(Sketch.dp(c, 20), Sketch.dp(c, 18), Sketch.dp(c, 20), hidden + Sketch.dp(c, 18) + in[3]);
            // What is left of the screen once the bars, the keyboard and the sheet's own title are taken off.
            int room = c.getResources().getDisplayMetrics().heightPixels - in[1] - in[3] - Sketch.dp(c, 130);
            bodyScroll.setMaxHeight(Math.max(Sketch.dp(c, 160), room));
            return insets;
        });

        host.addView(this, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200);
        panel.setTranslationY(Sketch.dp(c, 700));
        panel.animate().translationY(0f).setDuration(240);
        requestApplyInsets();
    }

    public void dismiss() {
        if (closing) return;
        closing = true;
        scrim.animate().alpha(0f).setDuration(180);
        panel.animate().translationY(panel.getHeight() + Sketch.dp(getContext(), 24))
                .setDuration(200)
                .withEndAction(() -> {
                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) parent.removeView(this);
                    if (onDismiss != null) onDismiss.run();
                });
    }
}
