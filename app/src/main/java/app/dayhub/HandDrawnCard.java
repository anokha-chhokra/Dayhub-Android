package app.dayhub;

import android.content.Context;
import android.widget.LinearLayout;

/** A vertical container drawn as a hand-drawn card with a hard shadow. */
public final class HandDrawnCard extends LinearLayout {
    private static int nextSeed = 1;

    public HandDrawnCard(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setBackground(new HandDrawnDrawable(c, c.getColor(R.color.tile), 4f, nextSeed++));
        setPadding(Sketch.dp(c, 18), Sketch.dp(c, 16), Sketch.dp(c, 22), Sketch.dp(c, 20));
    }
}
