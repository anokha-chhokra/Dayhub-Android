package app.dayhub.data;

import app.dayhub.data.Model.Entry;
import app.dayhub.data.Model.Expense;
import app.dayhub.data.Model.Habit;
import app.dayhub.data.Model.HabitLog;
import app.dayhub.data.Model.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feature 10: the Timeline (everything that happened on one day, in time order) and the month
 * Calendar, ported from the web app's commit.js. Reads the stored data; changes nothing.
 */
public final class Timeline {
    private final DayHubData data;

    public Timeline(DayHubData data) {
        this.data = data;
    }

    /** Morning, afternoon... for grouping a day's timeline. Null means "no time recorded". */
    public static String partOfDay(String time) {
        if (time == null || time.isEmpty()) return null;
        if (time.compareTo("05:00") < 0) return "Late night";
        if (time.compareTo("12:00") < 0) return "Morning";
        if (time.compareTo("17:00") < 0) return "Afternoon";
        if (time.compareTo("21:00") < 0) return "Evening";
        return "Night";
    }

    // ---------- one day ----------

    /** A habit as it stands on the day being shown. */
    public static final class DayHabit {
        public final Habit habit;
        /** False for a habit archived since, shown only because it was logged that day. */
        public final boolean scheduled;
        public final int value;
        public final boolean done;
        public final int pointsToday;
        public final String loggedTime;

        DayHabit(Habit habit, boolean scheduled, int value, String loggedTime) {
            this.habit = habit;
            this.scheduled = scheduled;
            this.value = value;
            this.done = HabitRules.isDone(habit, value);
            this.pointsToday = HabitRules.dayPoints(habit, value);
            this.loggedTime = loggedTime;
        }
    }

    /** One line of the day. Exactly one of habit, entry, expense and task is set, matching {@link #type}. */
    public static final class Item {
        public final String type; // habit, task, entry or expense
        public final String time; // "HH:MM" or null
        public String part; // Late night, Morning, ... or null
        public DayHabit habit;
        public Entry entry;
        public Expense expense;
        public Task task;
        /** A reminder-time habit that has not been done yet. */
        public boolean pending;
        /** A pending habit whose time has already passed. */
        public boolean missed;

        Item(String type, String time) {
            this.type = type;
            this.time = time;
        }
    }

    public static final class Summary {
        public int points;
        public int habitsDone;
        public int habitsScheduled;
        public int entries;
        public long spentMinor;
        public int tasksDone;
        public int missed;
    }

    public static final class Day {
        public String day;
        public Summary summary = new Summary();
        /** Goal and limit habits, shown as progress rather than at a time. */
        public List<DayHabit> goals = new ArrayList<>();
        /** Check habits with no reminder time that are not done. */
        public List<DayHabit> anytime = new ArrayList<>();
        public List<Item> items = new ArrayList<>();
    }

    private static boolean has(String s) {
        return s != null && !s.isEmpty();
    }

    private static int rank(String type) {
        switch (type) {
            case "habit": return 0;
            case "task": return 1;
            case "entry": return 2;
            default: return 3; // expense
        }
    }

    /**
     * Everything that happened (or is planned) on {@code day}, in time order: journal entries,
     * habits, spending and finished tasks.
     *
     * @param today the real today, so past days show unfinished reminders as missed
     * @param now   "HH:MM" or null; only used when {@code day} is today
     */
    public Day day(String day, String today, String now) {
        String next = Validate.addDays(day, 1);
        Map<Integer, HabitLog> logByHabit = new HashMap<>();
        for (HabitLog l : data.listLogs(day, next)) logByHabit.put(l.habitId, l);

        List<DayHabit> habits = new ArrayList<>();
        for (Habit h : data.listHabits(false)) {
            if (!HabitRules.isScheduled(h, day)) continue;
            HabitLog log = logByHabit.get(h.id);
            habits.add(new DayHabit(h, true, log == null ? 0 : log.value, log == null ? null : log.time));
        }
        // Archived habits that were logged that day still belong in that day's history.
        for (Habit h : data.listHabits(true)) {
            if (!h.archived) continue;
            HabitLog log = logByHabit.get(h.id);
            if (log != null) habits.add(new DayHabit(h, false, log.value, log.time));
        }
        List<Entry> entries = data.listEntries(day, next);
        List<Expense> expenses = data.listExpenses(day, next);
        List<Task> tasks = data.doneTasksOn(day);

        Day out = new Day();
        out.day = day;
        for (DayHabit h : habits) {
            if (!h.habit.kind.equals("check")) {
                out.goals.add(h);
                continue;
            }
            if (h.done) {
                Item it = new Item("habit", has(h.loggedTime) ? h.loggedTime : has(h.habit.remindAt) ? h.habit.remindAt : null);
                it.habit = h;
                out.items.add(it);
            } else if (has(h.habit.remindAt)) {
                // planned: still ahead of us, or already past its time without being done
                Integer since = now == null ? null : Insights.minutesBetween(h.habit.remindAt, now);
                boolean late = day.compareTo(today) < 0 || (day.equals(today) && since != null && since > 0);
                Item it = new Item("habit", h.habit.remindAt);
                it.habit = h;
                it.pending = true;
                it.missed = late;
                out.items.add(it);
            } else {
                out.anytime.add(h);
            }
        }
        for (Entry e : entries) {
            Item it = new Item("entry", has(e.time) ? e.time : null);
            it.entry = e;
            out.items.add(it);
        }
        for (Expense e : expenses) {
            Item it = new Item("expense", has(e.time) ? e.time : null);
            it.expense = e;
            out.items.add(it);
        }
        for (Task t : tasks) {
            Item it = new Item("task", has(t.doneTime) ? t.doneTime : null);
            it.task = t;
            out.items.add(it);
        }
        // Time order. Items with no time go last. Within the same minute: habits, tasks, the journal
        // entry, then spending, so "Spent 250" follows the entry it came from.
        for (Item it : out.items) it.part = partOfDay(it.time);
        out.items.sort((a, b) -> {
            int c = (a.time == null ? "99:99" : a.time).compareTo(b.time == null ? "99:99" : b.time);
            return c != 0 ? c : Integer.compare(rank(a.type), rank(b.type));
        });

        int points = 0;
        for (DayHabit h : habits) points += h.pointsToday;
        Summary s = out.summary;
        s.points = points + (entries.isEmpty() ? 0 : HabitRules.JOURNAL_DAY_POINTS);
        for (DayHabit h : habits) {
            if (h.scheduled) s.habitsScheduled++;
            if (h.scheduled && h.done) s.habitsDone++;
        }
        s.entries = entries.size();
        for (Expense e : expenses) s.spentMinor += e.amountMinor;
        s.tasksDone = tasks.size();
        for (Item it : out.items) if (it.missed) s.missed++;
        return out;
    }

    // ---------- one month ----------

    /** What the calendar shows for one day. */
    public static final class Cell {
        public int entries;
        /** Average mood of the day's entries, rounded, or null. */
        public Integer mood;
        public int habitsDone;
        public int habitsScheduled;
    }

    public static final class Month {
        public String month;
        /** Only days with something on them, by "YYYY-MM-DD". */
        public Map<String, Cell> days = new LinkedHashMap<>();
    }

    /** The month "YYYY-MM" at a glance: entries, average mood and habit completion per day. */
    public Month month(String ym) {
        String[] range = Validate.monthRange(ym);
        List<Habit> habits = data.listHabits(true);
        List<HabitLog> logs = data.listLogs(range[0], range[1]);
        Month out = new Month();
        out.month = ym;

        Map<String, List<Integer>> moods = new LinkedHashMap<>();
        for (Entry e : data.listEntries(range[0], range[1])) {
            cell(out, e.day).entries++;
            if (e.mood != null) moods.computeIfAbsent(e.day, k -> new ArrayList<>()).add(e.mood);
        }
        for (Map.Entry<String, List<Integer>> m : moods.entrySet()) {
            double sum = 0;
            for (int v : m.getValue()) sum += v;
            out.days.get(m.getKey()).mood = (int) Math.round(sum / m.getValue().size());
        }

        Map<String, Integer> valueByDayHabit = new HashMap<>();
        Set<String> loggedDays = new HashSet<>();
        List<String> loggedOrder = new ArrayList<>();
        for (HabitLog l : logs) {
            valueByDayHabit.put(l.day + "|" + l.habitId, l.value);
            if (loggedDays.add(l.day)) loggedOrder.add(l.day);
        }
        for (String d : loggedOrder) {
            Cell c = cell(out, d);
            for (Habit h : habits) {
                if (h.archived || !HabitRules.isScheduled(h, d)) continue;
                c.habitsScheduled++;
                if (HabitRules.isDone(h, valueByDayHabit.get(d + "|" + h.id))) c.habitsDone++;
            }
        }
        return out;
    }

    private static Cell cell(Month m, String day) {
        return m.days.computeIfAbsent(day, k -> new Cell());
    }
}
