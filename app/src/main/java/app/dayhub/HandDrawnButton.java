package app.dayhub;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** A hand-drawn button: yellow highlight when primary, paper-coloured otherwise. */
public final class HandDrawnButton extends TextView {
    private static int nextSeed = 500;
    private final HandDrawnDrawable shape;

    public HandDrawnButton(Context c, CharSequence label, boolean primary) {
        super(c);
        shape = new HandDrawnDrawable(c,
                c.getColor(primary ? R.color.hi : R.color.tile), 3f, nextSeed++);
        setBackground(shape);
        setText(label);
        setTypeface(Sketch.FONT_BOLD);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        setTextColor(c.getColor(R.color.ink));
        setGravity(Gravity.CENTER);
        setPadding(Sketch.dp(c, 22), Sketch.dp(c, 12), Sketch.dp(c, 25), Sketch.dp(c, 15));
        setClickable(true);
        setFocusable(true);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        shape.setPressedLook(pressed);
    }
}
