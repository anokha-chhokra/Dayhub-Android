package app.dayhub;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.Model.Task;

/**
 * One task in a list: a tick box, the title (tap to edit) with its due date underneath, and, in the
 * full list, a star and a bin. The compact form used on Home shows only a star mark.
 */
public final class TaskRowView {
    private TaskRowView() {}

    private static TextView iconButton(Context c, String glyph, String description, Runnable onClick) {
        TextView b = Sketch.label(c, glyph, 22, false, R.color.ink);
        b.setGravity(Gravity.CENTER);
        b.setContentDescription(description);
        b.setClickable(true);
        b.setPadding(Sketch.dp(c, 10), Sketch.dp(c, 8), Sketch.dp(c, 10), Sketch.dp(c, 8));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    public static View build(Context c, Task task, String today, boolean compact, TaskActions actions) {
        boolean overdue = !task.done && task.dueOn != null && task.dueOn.compareTo(today) < 0;
        String label = task.dueOn == null ? "" : DateLabels.dueLabel(c, task.dueOn, today);

        HandDrawnCheckRow check = new HandDrawnCheckRow(c, task.title)
                .strikeWhenChecked()
                .setSubtitle(overdue ? "Overdue · " + label : label, overdue)
                .onLabelClick(() -> actions.openSheet(task));
        check.setChecked(task.done);
        check.setOnChange(() -> actions.setDone(task, check.isChecked()));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(check, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (compact) {
            if (task.priority != 0) {
                TextView star = Sketch.label(c, "★", 22, false, R.color.ink);
                star.setContentDescription("Starred");
                row.addView(star);
            }
        } else {
            row.addView(iconButton(c, task.priority != 0 ? "★" : "☆",
                    (task.priority != 0 ? "Unstar " : "Star ") + task.title, () -> actions.toggleStar(task)));
            row.addView(iconButton(c, "🗑️", "Delete " + task.title, () -> actions.deleteWithUndo(task)));
        }
        return row;
    }
}
