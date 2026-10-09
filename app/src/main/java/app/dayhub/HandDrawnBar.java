package app.dayhub;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/** A hand-drawn progress bar: a wobbly track with a yellow (or red, when over) fill. */
public final class HandDrawnBar extends LinearLayout {
    private static int nextSeed = 5000;

    /** @param percent 0 to 100 */
    public HandDrawnBar(Context c, int percent, boolean over) {
        super(c);
        setOrientation(HORIZONTAL);
        setBackground(new HandDrawnDrawable(c, c.getColor(R.color.track), 0f, nextSeed++));
        int pad = Sketch.dp(c, 4);
        setPadding(pad, pad, pad, pad);
        setWeightSum(100f);

        int shown = Math.max(0, Math.min(100, percent));
        if (shown > 0) {
            View fill = new View(c);
            fill.setBackground(new HandDrawnDrawable(c, c.getColor(over ? R.color.red : R.color.hi), 0f, nextSeed++));
            addView(fill, new LayoutParams(0, Sketch.dp(c, 16), shown));
        }
        if (shown < 100) {
            addView(new View(c), new LayoutParams(0, Sketch.dp(c, 16), 100 - shown));
        }
        setContentDescription(shown + "% of budget used");
    }
}
