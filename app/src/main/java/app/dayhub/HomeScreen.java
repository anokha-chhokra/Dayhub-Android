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
import app.dayhub.data.DayHubData;
import app.dayhub.data.HomeData;
import app.dayhub.data.Insights;
import app.dayhub.data.Model.Task;
import app.dayhub.data.Validate;

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
    private final TaskActions taskActions;
    private final JournalActions journalActions;
    private final ExpenseActions expenseActions;
    private final Navigator navigator;

    private final LinearLayout head;
    private final HandDrawnCard attention;
    private final HandDrawnCard tasks;
    private final QuickJournalTile journal;
    private final HandDrawnCard spend;
    private final MusicTile music;
    private final Runnable refresh = this::refresh;

    public HomeScreen(Activity activity, DayHubData data, Overlays overlays,
                      TaskActions taskActions, JournalActions journalActions, ExpenseActions expenseActions,
                      Navigator navigator) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.taskActions = taskActions;
        this.journalActions = journalActions;
        this.expenseActions = expenseActions;
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
        journal = new QuickJournalTile(activity, data, overlays, journalActions, refresh, () -> navigator.go("journal"));
        column.addView(journal, tileParams());
        spend = tile(column);
        column.addView(new FocusTile(activity), tileParams());
        music = new MusicTile(activity, data, overlays, refresh, () -> navigator.go("music"));
        column.addView(music, tileParams());
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
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Sketch.label(activity, DateLabels.weekdayDate(activity), 15, false, R.color.muted));
        text.addView(Sketch.label(activity, DateLabels.greeting(home.name), 30, true, R.color.ink));
        String streak = home.stats.streak > 0 ? "\uD83D\uDD25 " + home.stats.streak + "-day streak \u00B7 " : "";
        text.addView(Sketch.label(activity, streak + home.stats.totalPoints + " points", 16, false, R.color.muted),
                rowParams(4));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView gear = Sketch.label(activity, "\u2699\uFE0F", 26, false, R.color.ink);
        gear.setContentDescription("Settings");
        gear.setPadding(Sketch.dp(activity, 12), Sketch.dp(activity, 6), Sketch.dp(activity, 4), Sketch.dp(activity, 6));
        gear.setOnClickListener(v -> navigator.go("settings"));
        row.addView(gear);
        head.addView(row);
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
        LinearLayout title = new LinearLayout(activity);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(Sketch.label(activity, "Today", 22, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton add = new HandDrawnButton(activity, "+ Add", false);
        add.setOnClickListener(v -> taskActions.openSheet(null));
        title.addView(add);
        tasks.addView(title);

        List<Task> open = home.openTasks();
        int shown = Math.min(MAX_TASKS_SHOWN, open.size());
        if (shown == 0) {
            tasks.addView(Sketch.label(activity,
                    home.tasks.doneToday > 0 ? "All done for today. Add one to get going."
                            : "No tasks yet. Add one to get going.",
                    16, false, R.color.muted), rowParams(10));
        }
        for (int i = 0; i < shown; i++) {
            tasks.addView(TaskRowView.build(activity, open.get(i), home.today, true, taskActions), rowParams(4));
        }
        String meta = home.tasks.doneToday + " done today" + (open.size() > shown ? " \u00B7 " + (open.size() - shown) + " more" : "");
        tasks.addView(footer(meta, "All tasks", () -> navigator.go("tasks")), rowParams(8));
    }

    // ---------- spending ----------

    private void drawSpend(HomeData home) {
        DashboardInsights.Spend s = home.spend;
        boolean hasBudget = s.budgetMinor > 0;
        boolean over = hasBudget && s.totalMinor > s.budgetMinor;
        int percent = hasBudget ? (int) Math.min(100, Math.round((double) s.totalMinor / s.budgetMinor * 100)) : 0;

        spend.removeAllViews();
        LinearLayout spendTitle = new LinearLayout(activity);
        spendTitle.setOrientation(LinearLayout.HORIZONTAL);
        spendTitle.setGravity(Gravity.CENTER_VERTICAL);
        spendTitle.addView(Sketch.label(activity, "Spending", 22, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton addExpense = new HandDrawnButton(activity, "+ Expense", false);
        addExpense.setOnClickListener(v -> expenseActions.openSheet(home.today));
        spendTitle.addView(addExpense);
        spend.addView(spendTitle);
        spend.addView(Sketch.label(activity, MoneyFormat.money(s.totalMinor, s.currency), 34, true, R.color.ink), rowParams(6));
        spend.addView(Sketch.label(activity, hasBudget
                ? (over ? "Over budget by " + MoneyFormat.money(s.totalMinor - s.budgetMinor, s.currency)
                        : MoneyFormat.money(s.budgetMinor - s.totalMinor, s.currency) + " left of "
                                + MoneyFormat.money(s.budgetMinor, s.currency))
                : "this month · set a budget in Settings", 15, false, over ? R.color.red : R.color.muted));
        if (hasBudget) spend.addView(new HandDrawnBar(activity, percent, over), rowParams(10));
        String pace = MoneyFormat.paceText(s.pace, s.currency);
        if (!pace.isEmpty()) {
            spend.addView(Sketch.label(activity, pace, 14, false,
                    s.pace.status.equals("watch") ? R.color.red : R.color.muted), rowParams(8));
        }
        spend.addView(footer("Today: " + MoneyFormat.money(s.todayMinor, s.currency), "Details",
                () -> navigator.go("spend")), rowParams(8));
    }
}
