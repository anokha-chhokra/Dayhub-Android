package app.dayhub;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Hand-drawn bottom navigation bar; the selected tab is a yellow hand-drawn tab shape. */
public final class BottomNavBar extends LinearLayout {
    public interface Listener {
        void onTabSelected(int index);
    }

    private final TextView[] tabs;
    private Listener listener;
    private int selected = -1;

    public BottomNavBar(Context c, String[] labels) {
        super(c);
        setOrientation(HORIZONTAL);
        setBackground(new HandDrawnDrawable(c, c.getColor(R.color.tile), 0f, 7000));
        setPadding(Sketch.dp(c, 8), Sketch.dp(c, 6), Sketch.dp(c, 8), Sketch.dp(c, 6));

        tabs = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = new TextView(c);
            t.setText(labels[i]);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(Sketch.dp(c, 2), Sketch.dp(c, 12), Sketch.dp(c, 2), Sketch.dp(c, 12));
            t.setClickable(true);
            t.setOnClickListener(v -> select(index, true));
            addView(t, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            tabs[i] = t;
            style(i, false);
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void select(int index, boolean notify) {
        if (index == selected) return;
        if (selected >= 0) style(selected, false);
        selected = index;
        style(index, true);
        if (notify && listener != null) listener.onTabSelected(index);
    }

    private void style(int index, boolean on) {
        Context c = getContext();
        TextView t = tabs[index];
        t.setTypeface(on ? Sketch.FONT_BOLD : Sketch.FONT);
        t.setTextColor(c.getColor(on ? R.color.ink : R.color.muted));
        t.setBackground(on ? new HandDrawnDrawable(c, c.getColor(R.color.hi), 0f, 7100 + index) : null);
    }
}
