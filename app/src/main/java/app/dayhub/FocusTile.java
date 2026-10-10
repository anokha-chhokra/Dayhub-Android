package app.dayhub;

import android.app.Activity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.StopwatchMath;

/**
 * Feature 23: the Focus tile on Home. An old pocket stopwatch whose dial is re-engraved for the length
 * you choose, with presets, a box to type the minutes, and plus and minus 5 buttons beneath it.
 * (Starting the timer comes with the next features.)
 */
public final class FocusTile extends HandDrawnCard {
    private static final int[] PRESETS = {15, 25, 45, 60, 90, 120};

    private final Activity activity;
    private final StopwatchView watch;
    private final HandDrawnField minutesBox;
    private final TextView readout;
    private final LinearLayout chips;
    private int minutes = 45;
    private boolean writingBox;

    public FocusTile(Activity activity) {
        super(activity);
        this.activity = activity;

        addView(Sketch.label(activity, "Focus", 22, true, R.color.ink));
        addView(Sketch.label(activity, "Wind the stopwatch to a length. The dial is engraved for it.", 15, false, R.color.muted),
                rowParams(6));

        watch = new StopwatchView(activity);
        LinearLayout.LayoutParams watchParams = new LinearLayout.LayoutParams(Sketch.dp(activity, 260), ViewGroup.LayoutParams.WRAP_CONTENT);
        watchParams.gravity = Gravity.CENTER_HORIZONTAL;
        watchParams.topMargin = Sketch.dp(activity, 6);
        addView(watch, watchParams);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(chips);
        addView(scroll, rowParams(12));

        LinearLayout stepper = new LinearLayout(activity);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        HandDrawnButton less = new HandDrawnButton(activity, "− 5", false);
        less.setContentDescription("5 minutes less");
        less.setOnClickListener(v -> setMinutes(nudged(-5), false, false));
        minutesBox = new HandDrawnField(activity, "min");
        minutesBox.setSingleLine(true);
        minutesBox.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutesBox.setFilters(new InputFilter[] {new InputFilter.LengthFilter(3)});
        minutesBox.setGravity(Gravity.CENTER);
        minutesBox.setContentDescription("Focus length in minutes");
        HandDrawnButton more = new HandDrawnButton(activity, "+ 5", false);
        more.setContentDescription("5 minutes more");
        more.setOnClickListener(v -> setMinutes(nudged(5), false, false));
        stepper.addView(less);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(Sketch.dp(activity, 84), ViewGroup.LayoutParams.WRAP_CONTENT);
        boxParams.leftMargin = Sketch.dp(activity, 10);
        stepper.addView(minutesBox, boxParams);
        TextView unit = Sketch.label(activity, "min", 16, false, R.color.muted);
        unit.setPadding(Sketch.dp(activity, 8), 0, Sketch.dp(activity, 10), 0);
        stepper.addView(unit);
        stepper.addView(more);
        LinearLayout.LayoutParams stepperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepperParams.gravity = Gravity.CENTER_HORIZONTAL;
        stepperParams.topMargin = Sketch.dp(activity, 12);
        addView(stepper, stepperParams);

        readout = Sketch.label(activity, "", 14, false, R.color.muted);
        readout.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(readout, rowParams(6));

        minutesBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (!writingBox) typed(s.toString());
            }
        });
        // Leaving the box shows the length that is really set (a typed 999 becomes 480).
        minutesBox.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                writeBox(String.valueOf(minutes));
                readout.setText(describe(minutes));
            }
        });
        setMinutes(minutes, false, false);
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    /** The length chosen so far, in minutes. */
    public int minutes() {
        return minutes;
    }

    private int nudged(int by) {
        Integer n = StopwatchMath.clampMinutes(minutes + by);
        return n == null ? minutes : n;
    }

    private static String presetLabel(int m) {
        if (m < 60) return m + " min";
        double h = m / 60.0;
        return (h == Math.floor(h) ? String.valueOf((int) h) : String.format(java.util.Locale.ROOT, "%.1f", h)) + " h";
    }

    private static String describe(int m) {
        return m >= 60 ? "That is " + StopwatchMath.minutesLabel(m) + "." : "";
    }

    private void writeBox(String s) {
        writingBox = true;
        minutesBox.setText(s);
        minutesBox.setSelection(minutesBox.getText().length());
        writingBox = false;
    }

    /** Called as the person types in the minutes box. */
    private void typed(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (!digits.equals(raw)) writeBox(digits);
        Integer n = StopwatchMath.clampMinutes(digits);
        if (n != null) {
            boolean over = digits.length() > 0 && new java.math.BigInteger(digits).compareTo(java.math.BigInteger.valueOf(StopwatchMath.MAX_MINUTES)) > 0;
            setMinutes(n, true, over);
        }
    }

    private void setMinutes(int m, boolean typing, boolean over) {
        minutes = m;
        watch.setIdle(m);
        chips.removeAllViews();
        for (int preset : PRESETS) {
            HandDrawnButton chip = new HandDrawnButton(activity, presetLabel(preset), preset == m);
            chip.setOnClickListener(v -> setMinutes(preset, false, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Sketch.dp(activity, 10);
            chips.addView(chip, lp);
        }
        if (!typing) writeBox(String.valueOf(m));
        readout.setText(over ? "Longest focus is " + StopwatchMath.minutesLabel(StopwatchMath.MAX_MINUTES) + "." : describe(m));
    }
}
