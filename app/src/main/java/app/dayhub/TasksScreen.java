package app.dayhub;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import app.dayhub.data.DayHubData;
import app.dayhub.data.Model.Task;
import app.dayhub.data.TaskGroups;
import app.dayhub.data.Validate;

import java.util.List;

/**
 * Feature 14: the Tasks tab. Open tasks are grouped Overdue / Today / Upcoming / No date; the Done
 * tab lists what has been finished. Tap a title to edit, tick the box to finish or reopen, star to
 * move it up, bin to delete with Undo.
 */
public final class TasksScreen extends ScrollView {
    private final Activity activity;
    private final DayHubData data;
    private final TaskActions actions;
    private final LinearLayout tabs;
    private final LinearLayout list;
    private boolean showDone;

    public TasksScreen(Activity activity, DayHubData data, TaskActions actions) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.actions = actions;
        setFillViewport(true);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Sketch.dp(activity, 20);
        column.setPadding(pad, pad, pad, pad);
        addView(column);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Sketch.label(activity, "Tasks", 32, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton add = new HandDrawnButton(activity, "+ Add", true);
        add.setOnClickListener(v -> actions.openSheet(null));
        head.addView(add);
        column.addView(head);

        tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(tabs, rowParams(14));

        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        column.addView(list, rowParams(6));
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    /** Redraws the tabs and the list from the stored data. */
    public void refresh() {
        drawTabs();
        drawList();
    }

    private void drawTabs() {
        tabs.removeAllViews();
        String[] labels = {"Open", "Done"};
        for (int i = 0; i < labels.length; i++) {
            boolean doneTab = i == 1;
            HandDrawnButton b = new HandDrawnButton(activity, labels[i], doneTab == showDone);
            b.setOnClickListener(v -> {
                showDone = doneTab;
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Sketch.dp(activity, 12);
            tabs.addView(b, lp);
        }
    }

    private void drawList() {
        list.removeAllViews();
        String today = Validate.localDate();
        List<Task> tasks = data.listTasks(showDone ? "done" : "open");
        if (tasks.isEmpty()) {
            HandDrawnCard empty = new HandDrawnCard(activity);
            empty.addView(Sketch.label(activity, showDone ? "Nothing finished yet." : "Nothing to do.", 18, true, R.color.ink));
            if (!showDone) {
                empty.addView(Sketch.label(activity, "Add a task and it shows up here.", 16, false, R.color.muted));
            }
            list.addView(empty, rowParams(10));
            return;
        }
        if (showDone) {
            group("Finished", tasks, today, false);
            return;
        }
        TaskGroups g = TaskGroups.of(tasks, today);
        group("Overdue", g.overdue, today, true);
        group("Today", g.today, today, false);
        group("Upcoming", g.upcoming, today, false);
        group("No date", g.noDate, today, false);
    }

    private void group(String title, List<Task> tasks, String today, boolean warn) {
        if (tasks.isEmpty()) return;
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, title, 20, true, warn ? R.color.red : R.color.ink));
        for (Task t : tasks) {
            View row = TaskRowView.build(activity, t, today, false, actions);
            card.addView(row, rowParams(2));
        }
        list.addView(card, rowParams(12));
    }
}
