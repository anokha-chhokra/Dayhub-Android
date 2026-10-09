package app.dayhub;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Habit;

/**
 * One habit with its controls. A daily check has a tick box; a goal or limit shows its progress as a
 * bar with minus and plus buttons. Tapping the name opens the editor.
 */
public final class HabitRowView {
    private HabitRowView() {}

    public static View build(Context c, HabitDay h, String day, HabitActions actions) {
        Habit habit = h.habit;
        if (habit.kind.equals("check")) return checkRow(c, h, day, actions);
        return countRow(c, h, day, actions);
    }

    private static View checkRow(Context c, HabitDay h, String day, HabitActions actions) {
        Habit habit = h.habit;
        HandDrawnCheckRow row = new HandDrawnCheckRow(c, habit.icon + " " + habit.title)
                .strikeWhenChecked()
                .setSubtitle(HabitProgressText.detail(h), false)
                .onLabelClick(() -> actions.openSheet(habit));
        row.setChecked(h.done);
        row.setOnChange(() -> actions.log(h, day, row.isChecked() ? 1 : 0));
        return row;
    }

    private static HandDrawnButton stepButton(Context c, String label, boolean enabled, Runnable onClick) {
        HandDrawnButton b = new HandDrawnButton(c, label, false);
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.4f);
        b.setPadding(Sketch.dp(c, 14), Sketch.dp(c, 8), Sketch.dp(c, 17), Sketch.dp(c, 11));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private static View countRow(Context c, HabitDay h, String day, HabitActions actions) {
        Habit habit = h.habit;
        boolean over = habit.kind.equals("limit") && h.value > habit.target;

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Sketch.dp(c, 6), 0, Sketch.dp(c, 6));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setClickable(true);
        text.setOnClickListener(v -> actions.openSheet(habit));
        text.addView(Sketch.label(c, habit.icon + " " + habit.title, 17, false, R.color.ink));
        text.addView(Sketch.label(c, HabitProgressText.detail(h), 14, false, over ? R.color.red : R.color.muted));
        int pct = (int) Math.min(100, Math.round((double) h.value / habit.target * 100));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = Sketch.dp(c, 6);
        text.addView(new HandDrawnBar(c, pct, over), barParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout stepper = new LinearLayout(c);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.addView(stepButton(c, "−", h.value > 0, () -> actions.log(h, day, h.value - habit.step)));
        TextView value = Sketch.label(c, String.valueOf(h.value), 20, true, R.color.ink);
        value.setGravity(Gravity.CENTER);
        stepper.addView(value, new LinearLayout.LayoutParams(Sketch.dp(c, 44), ViewGroup.LayoutParams.WRAP_CONTENT));
        stepper.addView(stepButton(c, "+", true, () -> actions.log(h, day, h.value + habit.step)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = Sketch.dp(c, 8);
        row.addView(stepper, sp);
        return row;
    }
}
