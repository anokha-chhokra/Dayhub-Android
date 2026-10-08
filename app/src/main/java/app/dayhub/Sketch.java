package app.dayhub;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

/** Shared bits of the hand-drawn look: the handwriting font, sizes and text helpers. */
public final class Sketch {
    /** Built-in handwriting-style family, so no font file or download is needed. */
    public static final Typeface FONT = Typeface.create("casual", Typeface.NORMAL);
    public static final Typeface FONT_BOLD = Typeface.create("casual", Typeface.BOLD);

    private Sketch() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** A plain handwriting-font text view. */
    public static TextView label(Context c, CharSequence text, float sp, boolean bold, int colorRes) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(bold ? FONT_BOLD : FONT);
        t.setTextColor(c.getColor(colorRes));
        return t;
    }
}
