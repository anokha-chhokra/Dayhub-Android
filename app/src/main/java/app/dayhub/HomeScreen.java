package app.dayhub;

import android.app.Activity;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.dayhub.data.DashboardInsights;
import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.HomeData;
import app.dayhub.data.Insights;
import app.dayhub.data.Model.Task;
import app.dayhub.data.Validate;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Feature 13: the Home dashboard. A greeting with points and streak, then tiles: what needs you now,
 * today's tasks, a quick journal note, this month's spending, focus, music, and (until Settings
 * exists) the backup tools. Every refresh recomputes the numbers from the stored data; the journal
 * note and music link boxes are kept so anything half-typed survives it.
 */
public final class HomeScreen extends ScrollView {
    /** Where a tile's links go: "tasks", "habits", "journal", "spend", ... */
    public interface Navigator {
        void go(String route);
    }

    private static final int MAX_TASKS_SHOWN = 5;

    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final DataTransfer transfer;
    private final Navigator navigator;

    private final LinearLayout head;
    private final HandDrawnCard attention;
    private final HandDrawnCard tasks;
    private final QuickJournalTile journal;
    private final HandDrawnCard spend;
    private final MusicTile music;
    private final Runnable refresh = this::refresh;

    public HomeScreen(Activity activity, DayHubData data, Overlays overlays, DataTransfer transfer,
                      Navigator navigator) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.transfer = transfer;
        this.navigator = navigator;
        setFillViewport(true);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Sketch.dp(activity, 20);
        column.setPadding(pad, pad, pad, pad);
        addView(column);

        head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.VERTICAL);
        column.addView(head);

        attention = tile(column);
        tasks = tile(column);
        journal = new QuickJournalTile(activity, data, overlays, refresh, () -> navigator.go("journal"));
        column.addView(journal, tileParams());
        spend = tile(column);
        column.addView(focusTile(), tileParams());
        music = new MusicTile(activity, data, overlays, refresh);
        column.addView(music, tileParams());
        if (transfer != null) column.addView(dataTile(), tileParams());
    }

    private HandDrawnCard tile(LinearLayout column) {
        HandDrawnCard card = new HandDrawnCard(activity);
        column.addView(card, tileParams());
        return card;
    }

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, 16);
        return lp;
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    // ---------- refreshing ----------

    /** Recomputes everything from the stored data and redraws the tiles. */
    public void refresh() {
        HomeData home = HomeData.compute(data, Validate.localDate(), DateLabels.nowHHMM());
        drawHead(home);
        drawAttention(home);
        drawTasks(home);
        journal.update(home);
        drawSpend(home);
        music.update(home.music);
    }

    private TextView link(String text, Runnable onClick) {
        TextView t = Sketch.label(activity, text, 15, true, R.color.ink);
        t.setPaintFlags(t.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        t.setPadding(Sketch.dp(activity, 8), Sketch.dp(activity, 10), 0, Sketch.dp(activity, 10));
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    /** A small line of muted text on the left with a link on the right. */
    private View footer(String meta, String linkText, Runnable onLink) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Sketch.label(activity, meta, 14, false, R.color.muted),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(link(linkText, onLink));
        return row;
    }

    // ---------- header ----------

    private void drawHead(HomeData home) {
        head.removeAllViews();
        head.addView(Sketch.label(activity, DateLabels.weekdayDate(activity), 15, false, R.color.muted));
        head.addView(Sketch.label(activity, DateLabels.greeting(home.name), 30, true, R.color.ink));
        String streak = home.stats.streak > 0 ? "🔥 " + home.stats.streak + "-day streak · " : "";
        head.addView(Sketch.label(activity, streak + home.stats.totalPoints + " points", 16, false, R.color.muted),
                rowParams(4));
    }

    // ---------- needs attention ----------

    private void drawAttention(HomeData home) {
        attention.removeAllViews();
        boolean urgent = !home.attention.isEmpty();

        LinearLayout title = new LinearLayout(activity);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(Sketch.label(activity, "Needs you now", 22, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView stamp = Sketch.label(activity, urgent ? "DUE" : "CLEAR", 15, true, R.color.ink);
        stamp.setBackground(new HandDrawnDrawable(activity, activity.getColor(urgent ? R.color.hi : R.color.tile), 0f, 9001));
        stamp.setPadding(Sketch.dp(activity, 14), Sketch.dp(activity, 8), Sketch.dp(activity, 14), Sketch.dp(activity, 8));
        stamp.setRotation(-6f);
        title.addView(stamp);
        attention.addView(title);

        if (!urgent) {
            attention.addView(Sketch.label(activity, "Nothing urgent. Nothing overdue, nothing due, nothing missed. Enjoy it.",
                    16, false, R.color.muted), rowParams(10));
            return;
        }
        for (Insights.Item item : home.attention) attention.addView(attentionRow(item, home), rowParams(10));
    }

    private View attentionRow(Insights.Item item, HomeData home) {
        String icon;
        String title;
        String sub;
        String route;
        boolean bad = false;
        switch (item.type) {
            case "habit":
                icon = item.icon == null ? "🔔" : item.icon;
                title = item.title;
                sub = "Planned for " + item.remindAt + ", not done yet";
                route = "journal";
                break;
            case "journal":
                icon = "✍️";
                title = item.title;
                sub = "Nothing written yet today";
                route = "journal";
                break;
            case "budget":
                icon = "⚠️";
                title = item.title;
                sub = "Over by " + MoneyFormat.money(item.overByMinor, home.spend.currency);
                route = "spend";
                bad = true;
                break;
            default:
                bad = Boolean.TRUE.equals(item.overdue);
                icon = bad ? "⚠️" : "📋";
                title = item.title;
                sub = bad ? "Overdue · " + DateLabels.dueLabel(activity, item.dueOn, home.today) : "Due today";
                route = "tasks";
        }
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> navigator.go(route));
        TextView glyph = Sketch.label(activity, icon, 22, false, R.color.ink);
        row.addView(glyph, new LinearLayout.LayoutParams(Sketch.dp(activity, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Sketch.label(activity, title, 17, true, R.color.ink));
        text.addView(Sketch.label(activity, sub, 14, false, bad ? R.color.red : R.color.muted));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    // ---------- today's tasks ----------

    private void drawTasks(HomeData home) {
        tasks.removeAllViews();
        tasks.addView(Sketch.label(activity, "Today", 22, true, R.color.ink));
        List<Task> open = home.openTasks();
        int shown = Math.min(MAX_TASKS_SHOWN, open.size());
        if (shown == 0) {
            tasks.addView(Sketch.label(activity,
                    open.isEmpty() && home.tasks.doneToday > 0 ? "All done for today. Add one to get going."
                            : "No tasks yet. Tasks you add will show up here.",
                    16, false, R.color.muted), rowParams(10));
        }
        for (int i = 0; i < shown; i++) tasks.addView(taskRow(open.get(i), home), rowParams(4));
        String meta = home.tasks.doneToday + " done today" + (open.size() > shown ? " · " + (open.size() - shown) + " more" : "");
        tasks.addView(footer(meta, "All tasks", () -> navigator.go("tasks")), rowParams(8));
    }

    private View taskRow(Task task, HomeData home) {
        boolean overdue = task.dueOn != null && task.dueOn.compareTo(home.today) < 0;
        String label = task.dueOn == null ? "" : DateLabels.dueLabel(activity, task.dueOn, home.today);
        HandDrawnCheckRow row = new HandDrawnCheckRow(activity, (task.priority != 0 ? "★ " : "") + task.title)
                .strikeWhenChecked()
                .setSubtitle(overdue ? "Overdue · " + label : label, overdue);
        row.setChecked(task.done);
        row.setOnChange(() -> setTaskDone(task, row.isChecked(), home.today));
        return row;
    }

    private void setTaskDone(Task task, boolean done, String today) {
        try {
            JSONObject body = new JSONObject().put("done", done).put("today", today).put("time", DateLabels.nowHHMM());
            data.updateTask(task.id, body);
            data.commit();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        } catch (JSONException e) {
            overlays.toast("Could not update the task");
        }
        refresh();
    }

    // ---------- spending ----------

    private static String paceText(Insights.Pace pace, String currency) {
        if (pace.status.equals("none") || pace.status.equals("over")) return "";
        StringBuilder sb = new StringBuilder();
        if (pace.perDayLeftMinor != null && pace.daysLeft > 0) {
            sb.append("About ").append(MoneyFormat.money(pace.perDayLeftMinor, currency)).append(" a day for the next ")
                    .append(pace.daysLeft).append(pace.daysLeft == 1 ? " day" : " days");
        }
        if (pace.status.equals("watch")) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("at this pace the month ends near ").append(MoneyFormat.money(pace.projectedMinor, currency));
        }
        return sb.toString();
    }

    private void drawSpend(HomeData home) {
        DashboardInsights.Spend s = home.spend;
        boolean hasBudget = s.budgetMinor > 0;
        boolean over = hasBudget && s.totalMinor > s.budgetMinor;
        int percent = hasBudget ? (int) Math.min(100, Math.round((double) s.totalMinor / s.budgetMinor * 100)) : 0;

        spend.removeAllViews();
        spend.addView(Sketch.label(activity, "Spending", 22, true, R.color.ink));
        spend.addView(Sketch.label(activity, MoneyFormat.money(s.totalMinor, s.currency), 34, true, R.color.ink), rowParams(6));
        spend.addView(Sketch.label(activity, hasBudget
                ? (over ? "Over budget by " + MoneyFormat.money(s.totalMinor - s.budgetMinor, s.currency)
                        : MoneyFormat.money(s.budgetMinor - s.totalMinor, s.currency) + " left of "
                                + MoneyFormat.money(s.budgetMinor, s.currency))
                : "this month · set a budget in Settings", 15, false, over ? R.color.red : R.color.muted));
        if (hasBudget) spend.addView(new HandDrawnBar(activity, percent, over), rowParams(10));
        String pace = paceText(s.pace, s.currency);
        if (!pace.isEmpty()) {
            spend.addView(Sketch.label(activity, pace, 14, false,
                    s.pace.status.equals("watch") ? R.color.red : R.color.muted), rowParams(8));
        }
        spend.addView(footer("Today: " + MoneyFormat.money(s.todayMinor, s.currency), "Details",
                () -> navigator.go("spend")), rowParams(8));
    }

    // ---------- focus (placeholder until focus mode exists) ----------

    private View focusTile() {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Focus", 22, true, R.color.ink));
        card.addView(Sketch.label(activity,
                "A pocket stopwatch to keep you on one thing. Coming soon.", 16, false, R.color.muted), rowParams(8));
        return card;
    }

    // ---------- backup tools (until Settings takes them over) ----------

    private View dataTile() {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Your data", 22, true, R.color.ink));
        card.addView(Sketch.label(activity, "Save a copy, or restore one.", 16, false, R.color.muted), rowParams(6));
        card.addView(buttonRow("Backup", true, transfer::exportBackup,
                "Restore…", false, () -> transfer.restoreFromFile(this::refresh)), rowParams(14));
        card.addView(buttonRow("Expenses CSV", false, () -> transfer.exportExpenses(null),
                "Journal", false, transfer::exportJournal), rowParams(10));
        return card;
    }

    private View buttonRow(String firstLabel, boolean firstPrimary, Runnable first,
                           String secondLabel, boolean secondPrimary, Runnable second) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        HandDrawnButton a = new HandDrawnButton(activity, firstLabel, firstPrimary);
        a.setOnClickListener(v -> first.run());
        HandDrawnButton b = new HandDrawnButton(activity, secondLabel, secondPrimary);
        b.setOnClickListener(v -> second.run());
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 12);
        row.addView(a, gap);
        row.addView(b);
        return row;
    }
}
