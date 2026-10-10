package app.dayhub.data;

import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature 27: what the widgets show, a small and already-formatted picture of the Home screen worked out
 * from the app's stored data. The web app's widgets were fed this picture by the page; here the widgets read
 * the data directly, so they are never out of date. The shape and the wording match the web app's snapshot.
 * It has no Android code, so it can be checked on a plain JVM.
 */
public final class WidgetSnapshot {
    /** The wording that depends on the phone's language and clock, supplied by the caller. */
    public interface Labels {
        String money(long minor, String currency);

        String dueLabel(String dueOn, String today);

        String greeting(String name);
    }

    public static final class Attention {
        public String title = "", sub = "", go = "";
        public boolean bad;
    }

    public static final class TaskRow {
        public int id;
        public String title = "", sub = "";
        public boolean star, overdue;
    }

    public static final class Habit {
        public int id;
        public String title = "", icon = "", kind = "check", unit = "";
        public int value, target = 1, step = 1;
        public boolean done;
    }

    public static final class Spend {
        public String total = "", line = "", today = "";
        public int pct;
        public boolean over, hasBudget;
    }

    /** The most items the widgets are ever given, like the web app's snapshot. */
    public static final int MAX_ATTENTION = 4;
    public static final int MAX_LISTED = 15;

    public String day = "", name = "", greeting = "";
    public int points, streak, taskCount, doneToday, habitsDone, habitsTotal;
    public int mood;
    public String moodEmoji = "";
    public int journalCount;
    public final List<Attention> attention = new ArrayList<>();
    public final List<TaskRow> tasks = new ArrayList<>();
    public final List<Habit> habits = new ArrayList<>();
    public final Spend spend = new Spend();

    /** The picture is from a different day than {@code today}, so its habit counts are not today's. */
    public boolean isStale(String today) {
        return today == null || !today.equals(day);
    }

    /** Works the picture out from Home's numbers, the way the web app's buildSnapshot does. */
    public static WidgetSnapshot build(HomeData home, Labels labels) {
        WidgetSnapshot s = new WidgetSnapshot();
        String day = home.today;
        s.day = day;
        s.name = home.name == null ? "" : home.name;
        s.greeting = labels.greeting(home.name);
        s.points = home.stats.totalPoints;
        s.streak = home.stats.streak;

        for (int i = 0; i < home.attention.size() && i < MAX_ATTENTION; i++) {
            s.attention.add(attentionText(home.attention.get(i), day, home.spend.currency, labels));
        }

        List<Task> open = home.openTasks();
        for (int i = 0; i < open.size() && i < MAX_LISTED; i++) {
            Task t = open.get(i);
            TaskRow row = new TaskRow();
            row.id = t.id;
            row.title = t.title;
            row.star = t.priority != 0;
            boolean late = t.dueOn != null && !t.dueOn.isEmpty() && t.dueOn.compareTo(day) < 0;
            row.overdue = late;
            row.sub = t.dueOn == null || t.dueOn.isEmpty() ? ""
                    : late ? "Overdue · " + labels.dueLabel(t.dueOn, day) : labels.dueLabel(t.dueOn, day);
            s.tasks.add(row);
        }
        s.taskCount = open.size();
        s.doneToday = home.tasks.doneToday;

        for (int i = 0; i < home.habits.list.size() && i < MAX_LISTED; i++) {
            HabitDay d = home.habits.list.get(i);
            Habit h = new Habit();
            h.id = d.habit.id;
            h.title = d.habit.title;
            h.icon = d.habit.icon == null ? "" : d.habit.icon;
            h.kind = d.habit.kind;
            h.value = d.value;
            h.target = Math.max(1, d.habit.target);
            h.step = Math.max(1, d.habit.step);
            h.unit = d.habit.unit == null ? "" : d.habit.unit;
            h.done = d.done;
            s.habits.add(h);
        }
        s.habitsDone = home.habits.doneCount;
        s.habitsTotal = home.habits.total;

        DashboardInsights.Spend sp = home.spend;
        boolean hasBudget = sp.budgetMinor > 0;
        boolean over = hasBudget && sp.totalMinor > sp.budgetMinor;
        s.spend.hasBudget = hasBudget;
        s.spend.over = over;
        s.spend.pct = hasBudget ? (int) Math.min(100, Math.round((double) sp.totalMinor / sp.budgetMinor * 100)) : 0;
        s.spend.total = labels.money(sp.totalMinor, sp.currency);
        s.spend.line = hasBudget
                ? (over ? "Over budget by " + labels.money(sp.totalMinor - sp.budgetMinor, sp.currency)
                        : labels.money(sp.budgetMinor - sp.totalMinor, sp.currency) + " left of " + labels.money(sp.budgetMinor, sp.currency))
                : "this month";
        s.spend.today = labels.money(sp.todayMinor, sp.currency);

        s.journalCount = home.journal.todayCount;
        s.mood = home.journal.latestMood == null ? 0 : home.journal.latestMood;
        s.moodEmoji = Moods.emoji(home.journal.latestMood);
        return s;
    }

    private static Attention attentionText(Insights.Item item, String today, String currency, Labels labels) {
        Attention a = new Attention();
        switch (item.type) {
            case "habit":
                a.title = ((item.icon == null ? "" : item.icon) + " " + item.title).trim();
                a.sub = "Planned for " + item.remindAt + ", not done yet";
                a.go = "habits";
                break;
            case "journal":
                a.title = item.title;
                a.sub = "Nothing written yet today";
                a.go = "note";
                break;
            case "budget":
                a.title = item.title;
                a.sub = "Over by " + labels.money(item.overByMinor == null ? 0 : item.overByMinor, currency);
                a.bad = true;
                a.go = "spend";
                break;
            default:
                boolean overdue = Boolean.TRUE.equals(item.overdue);
                a.title = item.title;
                a.sub = overdue ? "Overdue · " + labels.dueLabel(item.dueOn, today) : "Due today";
                a.bad = overdue;
                a.go = "tasks";
        }
        return a;
    }
}
