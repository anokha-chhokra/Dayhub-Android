package app.dayhub;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A tappable row with a hand-drawn tick box and a label; used for checklists and single choices. */
public final class HandDrawnCheckRow extends LinearLayout {
    private static int nextSeed = 3000;

    private final TextView box;
    private final HandDrawnDrawable off;
    private final HandDrawnDrawable on;
    private boolean checked;
    private Runnable onChange;

    public HandDrawnCheckRow(Context c, CharSequence label) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setPadding(0, Sketch.dp(c, 6), 0, Sketch.dp(c, 6));

        int seed = nextSeed++;
        off = new HandDrawnDrawable(c, c.getColor(R.color.tile), 0f, seed);
        on = new HandDrawnDrawable(c, c.getColor(R.color.hi), 0f, seed);
        box = new TextView(c);
        box.setGravity(Gravity.CENTER);
        box.setTypeface(Sketch.FONT_BOLD);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        box.setTextColor(c.getColor(R.color.ink));
        box.setBackground(off);
        addView(box, new LayoutParams(Sketch.dp(c, 34), Sketch.dp(c, 34)));

        TextView text = Sketch.label(c, label, 17, false, R.color.ink);
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Sketch.dp(c, 12);
        addView(text, lp);

        super.setOnClickListener(v -> {
            setChecked(!checked);
            if (onChange != null) onChange.run();
        });
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        box.setBackground(checked ? on : off);
        box.setText(checked ? "✓" : "");
    }

    /** Runs after the person taps the row (not when {@link #setChecked} is called from code). */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    public void setOnClickListener(View.OnClickListener l) {
        throw new UnsupportedOperationException("Use setOnChange");
    }
}
