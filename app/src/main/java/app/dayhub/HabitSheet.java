package app.dayhub;

import android.app.Activity;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.HabitPresets;
import app.dayhub.data.Model.Habit;
import app.dayhub.data.Validate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** The sheet for adding a habit or editing one: name, type, target, days, points and reminder. */
public final class HabitSheet {
    private static final String[] KINDS = {"check", "goal", "limit"};
    private static final String[] KIND_LABELS = {"Daily check", "Goal (count up)", "Limit (stay under)"};
    private static final String[] DAY_LETTERS = {"S", "M", "T", "W", "T", "F", "S"};

    private final Activity activity;
    private final Overlays overlays;
    private final HabitActions actions;
    private final Habit editing; // null when adding

    private final HandDrawnField icon;
    private final HandDrawnField title;
    private final HandDrawnField target;
    private final HandDrawnField unit;
    private final HandDrawnField step;
    private final HandDrawnField points;
    private final LinearLayout targetBox;
    private final List<HandDrawnCheckRow> kindRows = new ArrayList<>();
    private final LinearLayout dayRow;
    private final TextView remindText;
    private final TextView error;
    private final TreeSet<Integer> days = new TreeSet<>();
    private String kind;
    private String remindAt;
    private boolean deleteArmed;
    private BottomSheet sheet;

    public static void open(Activity activity, Overlays overlays, HabitActions actions, Habit habit) {
        new HabitSheet(activity, overlays, actions, habit).show();
    }

    private HabitSheet(Activity activity, Overlays overlays, HabitActions actions, Habit habit) {
        this.activity = activity;
        this.overlays = overlays;
        this.actions = actions;
        this.editing = habit;
        this.kind = habit == null ? "check" : habit.kind;
        this.remindAt = habit == null ? null : habit.remindAt;
        if (habit == null) {
            for (int d = 0; d < 7; d++) days.add(d);
        } else {
            days.addAll(habit.days);
        }

        icon = text("Icon (emoji)", habit == null ? "📌" : habit.icon, InputType.TYPE_CLASS_TEXT, 8);
        title = text("Name", habit == null ? "" : habit.title, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60);
        int numeric = InputType.TYPE_CLASS_NUMBER;
        target = text("Target per day", String.valueOf(habit == null ? 8 : habit.target), numeric, 7);
        unit = text("glasses, steps, cups…", habit == null ? "" : habit.unit, InputType.TYPE_CLASS_TEXT, 20);
        step = text("1", String.valueOf(habit == null ? 1 : habit.step), numeric, 7);
        points = text("Points", String.valueOf(habit == null ? 10 : habit.points), numeric, 3);
        targetBox = new LinearLayout(activity);
        targetBox.setOrientation(LinearLayout.VERTICAL);
        dayRow = new LinearLayout(activity);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        remindText = Sketch.label(activity, "", 16, false, R.color.ink);
        error = Sketch.label(activity, "", 15, false, R.color.red);
        error.setVisibility(View.GONE);
    }

    private HandDrawnField text(String hint, String value, int inputType, int maxLength) {
        HandDrawnField f = new HandDrawnField(activity, hint);
        f.setSingleLine(true);
        f.setInputType(inputType);
        f.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
        f.setText(value);
        f.setSelection(f.getText().length());
        return f;
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    private TextView heading(String s) {
        return Sketch.label(activity, s, 17, true, R.color.ink);
    }

    private LinearLayout pair(String leftLabel, View left, String rightLabel, View right) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            LinearLayout col = new LinearLayout(activity);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(heading(i == 0 ? leftLabel : rightLabel));
            col.addView(i == 0 ? left : right, rowParams(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i == 0) lp.rightMargin = Sketch.dp(activity, 12);
            row.addView(col, lp);
        }
        return row;
    }

    private void show() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);

        if (editing == null) {
            body.addView(heading("Start from"));
            HorizontalScrollView scroll = new HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(activity);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            for (HabitPresets.Preset p : HabitPresets.ALL) {
                HandDrawnButton chip = new HandDrawnButton(activity, p.icon + " " + p.title, false);
                chip.setOnClickListener(v -> usePreset(p));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Sketch.dp(activity, 10);
                chips.addView(chip, lp);
            }
            scroll.addView(chips);
            body.addView(scroll, rowParams(6));
        }

        body.addView(heading("Name"), rowParams(editing == null ? 14 : 0));
        LinearLayout nameRow = new LinearLayout(activity);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Sketch.dp(activity, 84), ViewGroup.LayoutParams.WRAP_CONTENT);
        iconParams.rightMargin = Sketch.dp(activity, 10);
        nameRow.addView(icon, iconParams);
        nameRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(nameRow, rowParams(6));

        body.addView(heading("Type"), rowParams(14));
        for (int i = 0; i < KINDS.length; i++) {
            HandDrawnCheckRow row = new HandDrawnCheckRow(activity, KIND_LABELS[i]);
            final String k = KINDS[i];
            row.setChecked(k.equals(kind));
            row.setOnChange(() -> {
                kind = k;
                drawKinds();
            });
            kindRows.add(row);
            body.addView(row, rowParams(0));
        }

        targetBox.addView(heading("Target per day"), rowParams(8));
        targetBox.addView(target, rowParams(6));
        targetBox.addView(pair("Unit", unit, "Each tap adds", step), rowParams(12));
        body.addView(targetBox);
        drawKinds();

        body.addView(heading("Repeats on"), rowParams(14));
        body.addView(dayRow, rowParams(6));
        drawDays();

        body.addView(pair("Points", points, "Reminder", reminderControl()), rowParams(14));

        body.addView(error, rowParams(10));

        HandDrawnButton save = new HandDrawnButton(activity, editing != null ? "Save" : "Add habit", true);
        save.setOnClickListener(v -> save());
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(save);
        if (editing != null) {
            HandDrawnButton delete = new HandDrawnButton(activity, "Delete", false);
            delete.setOnClickListener(v -> {
                if (!deleteArmed) {
                    deleteArmed = true;
                    delete.setText("Tap again to delete");
                    return;
                }
                sheet.dismiss();
                actions.delete(editing);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Sketch.dp(activity, 12);
            buttons.addView(delete, lp);
        }
        body.addView(buttons, rowParams(16));

        sheet = overlays.sheet(editing != null ? "Edit habit" : "New habit", body);
        if (editing == null) Keyboard.showFor(title);
    }

    private View reminderControl() {
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(remindText);
        HandDrawnButton set = new HandDrawnButton(activity, "Set time", false);
        set.setOnClickListener(v -> {
            LocalTime initial = remindAt != null ? LocalTime.parse(remindAt) : LocalTime.of(8, 0);
            DateTimePickers.pickTime(overlays, activity, initial, t -> {
                remindAt = String.format(java.util.Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
                drawReminder();
            });
        });
        HandDrawnButton clear = new HandDrawnButton(activity, "None", false);
        clear.setOnClickListener(v -> {
            remindAt = null;
            drawReminder();
        });
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 10);
        row.addView(set, gap);
        row.addView(clear);
        col.addView(row, rowParams(6));
        drawReminder();
        return col;
    }

    private void drawReminder() {
        remindText.setText(remindAt == null ? "No reminder" : remindAt);
    }

    private void drawKinds() {
        for (int i = 0; i < kindRows.size(); i++) kindRows.get(i).setChecked(KINDS[i].equals(kind));
        targetBox.setVisibility(kind.equals("check") ? View.GONE : View.VISIBLE);
    }

    private void drawDays() {
        dayRow.removeAllViews();
        for (int d = 0; d < 7; d++) {
            final int day = d;
            boolean on = days.contains(d);
            TextView chip = Sketch.label(activity, DAY_LETTERS[d], 17, true, R.color.ink);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(new HandDrawnDrawable(activity, activity.getColor(on ? R.color.hi : R.color.tile), 0f, 7300 + d));
            chip.setContentDescription(new String[] {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"}[d]
                    + (on ? ", on" : ", off"));
            chip.setOnClickListener(v -> {
                if (days.contains(day)) {
                    if (days.size() > 1) days.remove(day); // a habit must repeat on at least one day
                } else {
                    days.add(day);
                }
                drawDays();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Sketch.dp(activity, 44), 1f);
            lp.rightMargin = Sketch.dp(activity, d < 6 ? 4 : 0);
            dayRow.addView(chip, lp);
        }
    }

    private void usePreset(HabitPresets.Preset p) {
        title.setText(p.title);
        icon.setText(p.icon);
        kind = p.kind;
        target.setText(String.valueOf(p.target));
        unit.setText(p.unit);
        step.setText(String.valueOf(p.step));
        points.setText(String.valueOf(p.points));
        drawKinds();
    }

    private void save() {
        error.setVisibility(View.GONE);
        String message;
        try {
            JSONArray dayList = new JSONArray();
            for (int d : days) dayList.put(d);
            JSONObject body = new JSONObject().put("title", title.getText().toString())
                    .put("icon", icon.getText().toString()).put("kind", kind)
                    .put("target", target.getText().toString()).put("unit", unit.getText().toString())
                    .put("step", step.getText().toString()).put("points", points.getText().toString())
                    .put("days", dayList).put("remindAt", remindAt == null ? JSONObject.NULL : remindAt)
                    .put("today", Validate.localDate());
            message = editing != null ? actions.update(editing.id, body) : actions.create(body);
        } catch (JSONException e) {
            message = "Could not save the habit";
        }
        if (message != null) {
            error.setText(message);
            error.setVisibility(View.VISIBLE);
            return;
        }
        sheet.dismiss();
    }
}
