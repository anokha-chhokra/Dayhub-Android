package app.dayhub;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A tappable row with a hand-drawn tick box, a label and an optional small line under it. */
public final class HandDrawnCheckRow extends LinearLayout {
    private static int nextSeed = 3000;

    private final TextView box;
    private final TextView text;
    private final LinearLayout column;
    private final TextView subtitle;
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

        column = new LinearLayout(c);
        column.setOrientation(VERTICAL);
        text = Sketch.label(c, label, 17, false, R.color.ink);
        column.addView(text);
        subtitle = Sketch.label(c, "", 14, false, R.color.muted);
        subtitle.setVisibility(View.GONE);
        column.addView(subtitle);
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Sketch.dp(c, 12);
        addView(column, lp);

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
        text.setPaintFlags(checked && strikeWhenChecked
                ? text.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                : text.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
    }

    private boolean strikeWhenChecked;

    /** Cross the label out while ticked, like a finished task. */
    public HandDrawnCheckRow strikeWhenChecked() {
        strikeWhenChecked = true;
        return this;
    }

    /** A small line under the label, in the muted colour or, when {@code warn}, in red. */
    public HandDrawnCheckRow setSubtitle(CharSequence s, boolean warn) {
        subtitle.setText(s);
        subtitle.setTextColor(getContext().getColor(warn ? R.color.red : R.color.muted));
        subtitle.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
        return this;
    }

    /** When set, tapping the label runs this instead of ticking; only the box ticks. */
    public HandDrawnCheckRow onLabelClick(Runnable r) {
        column.setOnClickListener(v -> r.run());
        return this;
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
