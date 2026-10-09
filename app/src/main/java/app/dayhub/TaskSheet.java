package app.dayhub;

import android.app.Activity;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.Model.Task;
import app.dayhub.data.Validate;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;

/** The sheet for adding a task or editing one: what, when, and whether it is starred. */
public final class TaskSheet {
    private final Activity activity;
    private final Overlays overlays;
    private final TaskActions actions;
    private final Task editing; // null when adding
    private final String today = Validate.localDate();

    private final HandDrawnField title;
    private final TextView dueText;
    private final HandDrawnCheckRow star;
    private final TextView error;
    private String dueOn;
    private BottomSheet sheet;

    public static void open(Activity activity, Overlays overlays, TaskActions actions, Task task) {
        new TaskSheet(activity, overlays, actions, task).show();
    }

    private TaskSheet(Activity activity, Overlays overlays, TaskActions actions, Task task) {
        this.activity = activity;
        this.overlays = overlays;
        this.actions = actions;
        this.editing = task;
        this.dueOn = task == null ? null : task.dueOn;

        title = new HandDrawnField(activity, "What needs doing?");
        title.setSingleLine(true);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setFilters(new InputFilter[] {new InputFilter.LengthFilter(200)});
        if (task != null) {
            title.setText(task.title);
            title.setSelection(title.getText().length());
        }
        dueText = Sketch.label(activity, "", 16, false, R.color.ink);
        star = new HandDrawnCheckRow(activity, "Star it (shows first)");
        star.setChecked(task != null && task.priority != 0);
        error = Sketch.label(activity, "", 15, false, R.color.red);
        error.setVisibility(View.GONE);
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    private void drawDue() {
        dueText.setText(dueOn == null ? "No date" : DateLabels.dueLabel(activity, dueOn, today));
    }

    private HandDrawnButton quick(String label, Runnable onClick) {
        HandDrawnButton b = new HandDrawnButton(activity, label, false);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private View buttonRow(HandDrawnButton... buttons) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < buttons.length - 1) lp.rightMargin = Sketch.dp(activity, 12);
            row.addView(buttons[i], lp);
        }
        return row;
    }

    private void show() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Sketch.label(activity, "What needs doing?", 17, true, R.color.ink));
        body.addView(title, rowParams(8));

        body.addView(Sketch.label(activity, "When?", 17, true, R.color.ink), rowParams(14));
        body.addView(dueText, rowParams(4));
        body.addView(buttonRow(
                quick("Today", () -> setDue(today)),
                quick("Tomorrow", () -> setDue(LocalDate.parse(today).plusDays(1).toString()))), rowParams(8));
        body.addView(buttonRow(
                quick("No date", () -> setDue(null)),
                quick("Pick a date", this::pickDate)), rowParams(8));

        body.addView(star, rowParams(10));
        body.addView(error, rowParams(8));

        HandDrawnButton save = new HandDrawnButton(activity, editing != null ? "Save" : "Add task", true);
        save.setOnClickListener(v -> save());
        View actionsRow;
        if (editing != null) {
            HandDrawnButton delete = new HandDrawnButton(activity, "Delete", false);
            delete.setOnClickListener(v -> {
                sheet.dismiss();
                actions.deleteWithUndo(editing);
            });
            actionsRow = buttonRow(save, delete);
        } else {
            actionsRow = buttonRow(save);
        }
        body.addView(actionsRow, rowParams(14));

        drawDue();
        sheet = overlays.sheet(editing != null ? "Edit task" : "New task", body);
        Keyboard.showFor(title);
    }

    private void setDue(String date) {
        dueOn = date;
        drawDue();
    }

    private void pickDate() {
        LocalDate initial = LocalDate.parse(dueOn != null ? dueOn : today);
        DateTimePickers.pickDate(overlays, activity, initial, d -> setDue(d.toString()));
    }

    private void save() {
        error.setVisibility(View.GONE);
        String message;
        try {
            JSONObject body = new JSONObject().put("title", title.getText().toString())
                    .put("dueOn", dueOn == null ? JSONObject.NULL : dueOn)
                    .put("priority", star.isChecked() ? 1 : 0);
            message = editing != null ? actions.update(editing.id, body) : actions.create(body);
        } catch (JSONException e) {
            message = "Could not save the task";
        }
        if (message != null) {
            error.setText(message);
            error.setVisibility(View.VISIBLE);
            return;
        }
        sheet.dismiss();
    }
}
