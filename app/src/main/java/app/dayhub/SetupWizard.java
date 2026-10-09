package app.dayhub;

import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.HabitPresets;
import app.dayhub.data.Validate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Feature 12: the first-run setup. Four short steps (name, money, habits, music) and one save at
 * the end, so a half-finished setup never sticks. The questions can be skipped, and someone who
 * already has a backup file can restore it instead.
 */
public final class SetupWizard {
    static final String[][] CURRENCIES = {
        {"INR", "₹ Indian rupee"}, {"USD", "$ US dollar"}, {"EUR", "€ Euro"},
        {"GBP", "£ British pound"}, {"AED", "AED Dirham"}, {"SGD", "S$ Singapore dollar"},
    };
    private static final int STEPS = 4;

    private final MainActivity activity;
    private final FrameLayout content;
    private final DataTransfer transfer;
    private final DayHubData data;
    private final Runnable onReady;
    private final ScrollView root;
    private final LinearLayout column;

    // What has been answered so far.
    private String name = "";
    private String currency = "INR";
    private String budget = "";
    private String musicUrl = "";
    private final Set<String> habits = new LinkedHashSet<>();
    private int step;
    private boolean saving;
    private String error = "";

    /** Shows the wizard if setup has not been done yet; otherwise just carries on. */
    public static void showIfNeeded(MainActivity activity, FrameLayout content, DataTransfer transfer,
                                    DayHubData data, Runnable onReady) {
        if (data.getSettings().setupDone) {
            onReady.run();
            return;
        }
        new SetupWizard(activity, content, transfer, data, onReady).show();
    }

    /** Runs the setup again (from Settings), whether or not it was done before. */
    public static void run(MainActivity activity, FrameLayout content, DataTransfer transfer,
                           DayHubData data, Runnable onReady) {
        new SetupWizard(activity, content, transfer, data, onReady).show();
    }

    private SetupWizard(MainActivity activity, FrameLayout content, DataTransfer transfer,
                        DayHubData data, Runnable onReady) {
        this.activity = activity;
        this.content = content;
        this.transfer = transfer;
        this.data = data;
        this.onReady = onReady;
        // Start from what is already saved. A first run suggests four habits; running setup again adds none.
        app.dayhub.data.Model.Settings saved = data.getSettings();
        this.name = saved.name;
        this.currency = saved.currency;
        long minor = saved.monthlyBudgetMinor;
        this.budget = minor == 0 ? "" : minor % 100 == 0 ? String.valueOf(minor / 100) : MoneyFormat.decimal(minor);
        if (!saved.setupDone) habits.addAll(java.util.Arrays.asList("water", "steps", "workout", "coffee"));

        root = new ScrollView(activity);
        root.setFillViewport(true);
        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Sketch.dp(activity, 20);
        column.setPadding(pad, pad, pad, pad);
        root.addView(column);
    }

    private void show() {
        content.addView(root);
        activity.setBackHandler(() -> {
            if (step == 0 || saving) return false;
            step--;
            draw();
            return true;
        });
        draw();
    }

    private void close() {
        activity.setBackHandler(null);
        content.removeView(root);
        onReady.run();
    }

    // ---------- drawing ----------

    private void draw() {
        column.removeAllViews();
        column.addView(dots());
        switch (step) {
            case 0: nameStep(); break;
            case 1: moneyStep(); break;
            case 2: habitsStep(); break;
            default: musicStep(); break;
        }
        if (!error.isEmpty()) column.addView(spaced(Sketch.label(activity, error, 15, false, R.color.red), 12));
        column.addView(actions());
        TextView skip = Sketch.label(activity, "Skip setup, I will do it later", 15, false, R.color.muted);
        skip.setPaintFlags(skip.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        skip.setPadding(0, Sketch.dp(activity, 12), 0, Sketch.dp(activity, 12));
        skip.setClickable(true);
        skip.setOnClickListener(v -> finish(true));
        column.addView(spaced(skip, 6));
    }

    private View dots() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < STEPS; i++) {
            View dot = new View(activity);
            dot.setBackground(new HandDrawnDrawable(activity,
                    activity.getColor(i <= step ? R.color.hi : R.color.tile), 0f, 4000 + i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Sketch.dp(activity, 30), Sketch.dp(activity, 18));
            lp.rightMargin = Sketch.dp(activity, 6);
            row.addView(dot, lp);
        }
        row.setContentDescription("Step " + (step + 1) + " of " + STEPS);
        return row;
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    private View spaced(View v, int topDp) {
        v.setLayoutParams(rowParams(topDp));
        return v;
    }

    private void heading(String text) {
        column.addView(spaced(Sketch.label(activity, text, 30, true, R.color.ink), 18));
    }

    private void paragraph(String text) {
        column.addView(spaced(Sketch.label(activity, text, 16, false, R.color.ink), 8));
    }

    private void label(String text, String hint) {
        column.addView(spaced(Sketch.label(activity, text, 17, true, R.color.ink), 18));
        if (hint != null) column.addView(spaced(Sketch.label(activity, hint, 14, false, R.color.muted), 2));
    }

    private HandDrawnField field(String hint, String value, int inputType, int maxLength, Consumer<String> onText) {
        HandDrawnField f = new HandDrawnField(activity, hint);
        f.setSingleLine(true);
        f.setInputType(inputType);
        if (maxLength > 0) f.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
        f.setText(value);
        f.setSelection(f.getText().length());
        f.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                onText.accept(s.toString());
            }
        });
        column.addView(spaced(f, 8));
        return f;
    }

    private interface Consumer<T> {
        void accept(T value);
    }

    private void nameStep() {
        heading("Welcome to Day Hub");
        paragraph("One page for your tasks, habits, journal, spending and music. Four quick questions and you are set.");
        label("What should I call you?", null);
        field("Your name", name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | InputType.TYPE_TEXT_VARIATION_PERSON_NAME, 40, t -> name = t);
        if (data.getSettings().setupDone) return; // restoring is only offered on the very first run
        HandDrawnButton restore = new HandDrawnButton(activity, "I already have a backup file", false);
        restore.setOnClickListener(v -> transfer.restoreFromFile(() -> {
            if (data.getSettings().setupDone) close();
            else {
                name = data.getSettings().name;
                draw();
            }
        }));
        column.addView(spaced(restore, 22));
    }

    private void moneyStep() {
        heading("Money");
        label("Currency", null);
        List<HandDrawnCheckRow> rows = new ArrayList<>();
        for (String[] c : CURRENCIES) {
            HandDrawnCheckRow row = new HandDrawnCheckRow(activity, c[1]);
            row.setChecked(c[0].equals(currency));
            row.setOnChange(() -> {
                currency = c[0];
                for (int i = 0; i < rows.size(); i++) rows.get(i).setChecked(CURRENCIES[i][0].equals(currency));
            });
            rows.add(row);
            column.addView(row, rowParams(0));
        }
        label("Monthly budget", "Leave empty if you do not want one. You can change it any time.");
        field("Optional", budget, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 0, t -> budget = t);
    }

    private void habitsStep() {
        heading("Daily habits");
        paragraph("Pick a few to start with. Each earns points, and finishing things keeps your streak going. "
                + "You can change all of this later.");
        for (HabitPresets.Preset p : HabitPresets.ALL) {
            String meta = p.kind.equals("check") ? ""
                    : p.kind.equals("limit") ? " (max " + p.target + " " + p.unit + ")" : " (" + p.target + " " + p.unit + ")";
            HandDrawnCheckRow row = new HandDrawnCheckRow(activity, p.icon + " " + p.title + meta);
            row.setChecked(habits.contains(p.id));
            row.setOnChange(() -> {
                if (row.isChecked()) habits.add(p.id);
                else habits.remove(p.id);
            });
            column.addView(row, rowParams(0));
        }
    }

    private void musicStep() {
        heading("Music");
        paragraph("Paste a YouTube video or playlist and it plays on your home page. You can skip this.");
        label("Link", null);
        field("YouTube video or playlist link", musicUrl,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 0, t -> musicUrl = t);
    }

    private View actions() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (step > 0) {
            HandDrawnButton back = new HandDrawnButton(activity, "Back", false);
            back.setOnClickListener(v -> {
                if (saving) return;
                step--;
                error = "";
                draw();
            });
            row.addView(back);
        }
        View spacer = new View(activity);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        boolean last = step == STEPS - 1;
        HandDrawnButton next = new HandDrawnButton(activity, saving ? "Saving…" : last ? "Finish" : "Next", true);
        next.setOnClickListener(v -> {
            if (saving) return;
            if (last) {
                finish(false);
            } else {
                step++;
                error = "";
                draw();
            }
        });
        row.addView(next);
        return spaced(row, 24);
    }

    // ---------- saving ----------

    private void finish(boolean skip) {
        if (saving) return;
        saving = true;
        error = "";
        try {
            JSONObject body = new JSONObject().put("name", name);
            if (!skip) {
                JSONArray ids = new JSONArray();
                for (String id : habits) ids.put(id);
                body.put("currency", currency).put("monthlyBudget", budget.trim())
                        .put("habits", ids).put("today", Validate.localDate());
                if (!musicUrl.trim().isEmpty()) body.put("musicUrl", musicUrl.trim());
            }
            data.setup(body);
            data.commit(); // if the phone cannot save it, the setup is undone and the person can try again
        } catch (DataError e) {
            saving = false;
            error = e.getMessage();
            draw();
            return;
        } catch (JSONException e) {
            saving = false;
            error = "Something went wrong. Please try again.";
            draw();
            return;
        }
        close();
    }
}
