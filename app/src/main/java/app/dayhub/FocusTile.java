package app.dayhub;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.FocusSession;
import app.dayhub.data.StopwatchMath;

/**
 * Features 23 and 24: the Focus tile on Home. An old pocket stopwatch whose dial is re-engraved for the
 * length you choose, with presets, a box to type the minutes, plus and minus 5 buttons and a note of what
 * you are focusing on. Start winds it going: the hand follows the clock and the yellow wedge shrinks. The
 * timer lives on disk with an alarm, so it keeps running, and rings, with the app closed.
 */
public final class FocusTile extends HandDrawnCard {
    private static final int[] PRESETS = {15, 25, 45, 60, 90, 120};

    private final Activity activity;
    private final Overlays overlays;
    private final ActivityResults results;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::tick;

    private final TextView stamp;
    private final TextView intro;
    private final StopwatchView watch;
    private final LinearLayout idleGroup;
    private final LinearLayout runGroup;
    private final HandDrawnField minutesBox;
    private final HandDrawnField labelBox;
    private final TextView readout;
    private final LinearLayout chips;
    private final TextView clockText;
    private final TextView runLabel;

    private int minutes = 45;
    private boolean writingBox;
    private boolean running;       // what the tile is showing: a timer in progress
    private long shownStart;       // which timer the dial is showing
    private boolean starting;      // Start was pressed and the timer has not begun yet
    private boolean endedByPerson;

    public FocusTile(Activity activity, Overlays overlays, ActivityResults results) {
        super(activity);
        this.activity = activity;
        this.overlays = overlays;
        this.results = results;

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Sketch.label(activity, "Focus", 22, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stamp = Sketch.label(activity, "ON", 17, true, R.color.ink);
        stamp.setBackground(new HandDrawnDrawable(activity, activity.getColor(R.color.hi), 0f, 61));
        stamp.setPadding(Sketch.dp(activity, 14), Sketch.dp(activity, 6), Sketch.dp(activity, 14), Sketch.dp(activity, 6));
        stamp.setVisibility(View.GONE);
        head.addView(stamp);
        addView(head);

        intro = Sketch.label(activity, "Wind the stopwatch to a length, then start. It keeps running if you close "
                + "Day Hub, and rings when the time is up.", 15, false, R.color.muted);
        addView(intro, rowParams(6));

        watch = new StopwatchView(activity);
        LinearLayout.LayoutParams watchParams = new LinearLayout.LayoutParams(Sketch.dp(activity, 260), ViewGroup.LayoutParams.WRAP_CONTENT);
        watchParams.gravity = Gravity.CENTER_HORIZONTAL;
        watchParams.topMargin = Sketch.dp(activity, 6);
        addView(watch, watchParams);

        // ---- before it starts: how long, and what for ----
        idleGroup = new LinearLayout(activity);
        idleGroup.setOrientation(LinearLayout.VERTICAL);
        addView(idleGroup, rowParams(0));

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(chips);
        idleGroup.addView(scroll, rowParams(12));

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
        idleGroup.addView(stepper, stepperParams);

        readout = Sketch.label(activity, "", 14, false, R.color.muted);
        readout.setGravity(Gravity.CENTER_HORIZONTAL);
        idleGroup.addView(readout, rowParams(6));

        labelBox = new HandDrawnField(activity, "What are you focusing on? (optional)");
        labelBox.setSingleLine(true);
        labelBox.setFilters(new InputFilter[] {new InputFilter.LengthFilter(FocusSession.MAX_LABEL)});
        labelBox.setImeOptions(EditorInfo.IME_ACTION_DONE);
        labelBox.setContentDescription("What are you focusing on");
        idleGroup.addView(labelBox, rowParams(10));

        HandDrawnButton start = new HandDrawnButton(activity, "Start focus", true);
        start.setOnClickListener(v -> begin());
        idleGroup.addView(start, rowParams(14));

        // ---- while it runs: the time left, and a way to stop ----
        runGroup = new LinearLayout(activity);
        runGroup.setOrientation(LinearLayout.VERTICAL);
        runGroup.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(runGroup, rowParams(0));

        clockText = Sketch.label(activity, "", 56, true, R.color.ink);
        clockText.setGravity(Gravity.CENTER);
        runGroup.addView(clockText, rowParams(8));
        runLabel = Sketch.label(activity, "", 20, false, R.color.ink);
        runLabel.setGravity(Gravity.CENTER);
        runGroup.addView(runLabel, rowParams(2));
        TextView note = Sketch.label(activity, "It keeps running if you close Day Hub, and rings when time is up.",
                14, false, R.color.muted);
        note.setGravity(Gravity.CENTER);
        runGroup.addView(note, rowParams(8));
        HandDrawnButton end = new HandDrawnButton(activity, "End focus", false);
        end.setOnClickListener(v -> endEarly());
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endParams.topMargin = Sketch.dp(activity, 14);
        runGroup.addView(end, endParams);

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
        refresh();
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

    // ---------- choosing a length ----------

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
        if (!running) watch.setIdle(m);
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

    // ---------- starting and ending ----------

    /** Start was pressed: the crown dips, Android's notification question is asked once, then the timer begins. */
    private void begin() {
        if (starting || running) return;
        starting = true;
        hideKeyboard();
        watch.press();
        handler.postDelayed(this::askThenStart, 160);
    }

    private void askThenStart() {
        boolean ask = Build.VERSION.SDK_INT >= 33 && !FocusNotifications.allowed(activity)
                && !FocusState.askedNotifications(activity);
        if (!ask) {
            startNow();
            return;
        }
        FocusState.setAskedNotifications(activity);
        // The timer runs either way; the answer only decides whether Day Hub can tell you when it ends.
        results.requestPermission(Manifest.permission.POST_NOTIFICATIONS, granted -> startNow());
    }

    private void startNow() {
        starting = false;
        FocusController.start(activity, minutes, labelBox.getText().toString());
        if (!FocusNotifications.allowed(activity)) {
            overlays.toast("Notifications are off, so Day Hub cannot tell you when time is up.");
        }
        refresh();
    }

    private void endEarly() {
        endedByPerson = true;
        FocusController.end(activity);
        overlays.toast("Focus ended early");
        refresh();
    }

    // ---------- what the tile shows ----------

    /** Redraws from the saved timer: call when the app comes back to the front. */
    public void refresh() {
        FocusController.resume(activity); // finishes a timer whose time ran out; re-arms one still running
        FocusSession s = FocusState.get(activity);
        boolean on = s.isActive(System.currentTimeMillis());
        handler.removeCallbacks(ticker);

        if (running && !on) {
            if (!endedByPerson) {
                overlays.toast("Focus complete. Nice work.");
                if (!FocusNotifications.allowed(activity)) buzz();
            }
            endedByPerson = false;
        }

        if (on) {
            if (!running || shownStart != s.startedAt) {
                watch.setRun(s.startedAt, s.endsAt);
                shownStart = s.startedAt;
                hideKeyboard();
            }
            running = true;
            runLabel.setText(s.label);
            runLabel.setVisibility(s.label.isEmpty() ? View.GONE : View.VISIBLE);
            clockText.setText(FocusSession.clock(s.endsAt - System.currentTimeMillis()));
            applyMode(true);
            if (isAttachedToWindow()) scheduleTick(s);
        } else {
            boolean wasRunning = running;
            running = false;
            if (wasRunning) watch.setIdle(minutes);
            applyMode(false);
        }
    }

    private void applyMode(boolean on) {
        stamp.setVisibility(on ? View.VISIBLE : View.GONE);
        intro.setVisibility(on ? View.GONE : View.VISIBLE);
        idleGroup.setVisibility(on ? View.GONE : View.VISIBLE);
        runGroup.setVisibility(on ? View.VISIBLE : View.GONE);
        ViewGroup.LayoutParams lp = watch.getLayoutParams();
        int width = Sketch.dp(activity, on ? 210 : 260);
        if (lp.width != width) {
            lp.width = width;
            watch.setLayoutParams(lp);
        }
    }

    /** Wake just after the displayed second changes. */
    private void scheduleTick(FocusSession s) {
        long left = s.endsAt - System.currentTimeMillis();
        long wait = left % 1000;
        handler.postDelayed(ticker, (wait <= 0 ? 1000 : wait) + 5);
    }

    private void tick() {
        FocusSession s = FocusState.get(activity);
        long now = System.currentTimeMillis();
        if (!s.isActive(now)) {
            refresh(); // time is up (or it was ended elsewhere)
            return;
        }
        clockText.setText(FocusSession.clock(s.endsAt - now));
        scheduleTick(s);
    }

    private void buzz() {
        try {
            Vibrator v = activity.getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(new long[] {0, 120, 80, 120}, -1));
        } catch (RuntimeException e) {
            // Vibrating is a nicety.
        }
    }

    private void hideKeyboard() {
        labelBox.clearFocus();
        minutesBox.clearFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getWindowToken() != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh(); // shown again: the clock has moved on
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }
}
