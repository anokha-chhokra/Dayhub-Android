package app.dayhub;

import android.content.Context;
import android.util.TypedValue;
import android.widget.EditText;

/** A text field drawn as a hand-drawn box on the paper, with handwriting-font text. */
public final class HandDrawnField extends EditText {
    private static int nextSeed = 900;

    public HandDrawnField(Context c, CharSequence hint) {
        super(c);
        setBackground(new HandDrawnDrawable(c, c.getColor(R.color.tile), 0f, nextSeed++));
        setHint(hint);
        setTypeface(Sketch.FONT);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        setTextColor(c.getColor(R.color.ink));
        setHintTextColor(c.getColor(R.color.muted));
        setPadding(Sketch.dp(c, 16), Sketch.dp(c, 12), Sketch.dp(c, 16), Sketch.dp(c, 12));
    }
}
