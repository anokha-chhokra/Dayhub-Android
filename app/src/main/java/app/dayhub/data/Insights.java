package app.dayhub.data;

import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Task;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Feature 9: small pure helpers that decide what deserves attention right now and how the month's
 * spending is going, ported from the web app's insights.js. No storage and no clock: the caller
 * passes the day and time.
 */
public final class Insights {
    private Insights() {}

    private static Integer toMinutes(String hhmm) {
        if (hhmm == null || hhmm.length() != 5 || hhmm.charAt(2) != ':') return null;
        for (int i : new int[] {0, 1, 3, 4}) {
            char c = hhmm.charAt(i);
            if (c < '0' || c > '9') return null;
        }
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    /** Minutes from {@code earlier} to {@code later} ("HH:MM" each), or null if either is not a time. */
    public static Integer minutesBetween(String earlier, String later) {
        Integer a = toMinutes(earlier);
        Integer b = toMinutes(later);
        return a == null || b == null ? null : b - a;
    }

    private static long daysBetween(String from, String to) {
        return ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to));
    }

    // ---------- spending pace ----------

    /** How the month's spending is going. */
    public static final class Pace {
        public final int daysLeft;
        /** A projection for the whole month; null before day 5, when it would mostly be noise. */
        public final Long projectedMinor;
        /** What can be spent per day for the rest of the month, in whole currency units; null without a budget. */
        public final Long perDayLeftMinor;
        /** "none" (no budget), "ok", "watch" (on course to overspend) or "over". */
        public final String status;

        Pace(int daysLeft, Long projectedMinor, Long perDayLeftMinor, String status) {
            this.daysLeft = daysLeft;
            this.projectedMinor = projectedMinor;
            this.perDayLeftMinor = perDayLeftMinor;
            this.status = status;
        }
    }

    /**
     * @param ym          the month, "2026-10"
     * @param today       "YYYY-MM-DD"
     * @param totalMinor  spent so far this month
     * @param budgetMinor the monthly budget; 0 means none
     */
    public static Pace spendPace(String ym, String today, long totalMinor, long budgetMinor) {
        int y = Integer.parseInt(ym.substring(0, 4));
        int m = Integer.parseInt(ym.substring(5, 7));
        int daysInMonth = YearMonth.of(y, m).lengthOfMonth();
        boolean inThisMonth = today.startsWith(ym);
        int dayOfMonth = inThisMonth ? Integer.parseInt(today.substring(8, 10)) : daysInMonth;
        int daysLeft = inThisMonth ? daysInMonth - dayOfMonth + 1 : 0; // today still counts
        Long projected = dayOfMonth >= 5 && inThisMonth
                ? Long.valueOf(Math.round((double) totalMinor / dayOfMonth * daysInMonth)) : null;
        if (budgetMinor == 0) return new Pace(daysLeft, projected, null, "none");

        long left = budgetMinor - totalMinor;
        String status = "ok";
        if (left < 0) status = "over";
        else if (projected != null && projected > budgetMinor) status = "watch";
        // whole currency units: "About Rs 1,111 a day" reads better than Rs 1,111.11
        Long perDay = daysLeft > 0
                ? Long.valueOf((long) Math.max(0, Math.floor((double) left / daysLeft / 100) * 100)) : null;
        return new Pace(daysLeft, projected, perDay, status);
    }

    // ---------- needs attention ----------

    /** A habit as the attention list needs to see it. */
    public static final class HabitNudge {
        public int id;
        public String title;
        public String icon;
        public String kind;
        public boolean done;
        public boolean scheduled;
        public String remindAt;

        public static HabitNudge of(HabitDay d) {
            HabitNudge n = new HabitNudge();
            n.id = d.habit.id;
            n.title = d.habit.title;
            n.icon = d.habit.icon;
            n.kind = d.habit.kind;
            n.done = d.done;
            n.scheduled = d.scheduled;
            n.remindAt = d.habit.remindAt;
            return n;
        }
    }

    /** One line of the "Needs you now" list. Only the fields that apply to its type are set. */
    public static final class Item {
        public final String type; // task, habit, journal or budget
        public Integer id;
        public String title;
        public String dueOn;
        public Boolean overdue;
        public Integer priority;
        public String icon;
        public String remindAt;
        public Long overByMinor;
        private final int score;

        Item(String type, int score) {
            this.type = type;
            this.score = score;
        }
    }

    /**
     * The "Needs you now" list, most urgent first, at most {@code limit} items.
     *
     * @param now            "HH:MM", or null (without a clock, time-based nudges are skipped)
     * @param tasks          open tasks (those without a due date, or due later, are ignored)
     * @param habits         today's habits
     * @param spendStatus    {@link Pace#status} for this month, or null
     * @param overByMinor    how far over budget, used when the status is "over"
     */
    public static List<Item> buildAttention(String today, String now, List<Task> tasks, List<HabitNudge> habits,
                                            int entriesToday, String journalReminder, String spendStatus,
                                            long overByMinor, int limit) {
        List<Item> items = new ArrayList<>();
        for (Task t : tasks) {
            if (t.dueOn == null || t.dueOn.isEmpty() || t.dueOn.compareTo(today) > 0) continue;
            long overdueDays = daysBetween(t.dueOn, today);
            boolean overdue = overdueDays > 0;
            Item item = new Item("task", (overdue ? 50 + (int) Math.min(overdueDays, 30) : 35) + (t.priority != 0 ? 20 : 0));
            item.id = t.id;
            item.title = t.title;
            item.dueOn = t.dueOn;
            item.overdue = overdue;
            item.priority = t.priority;
            items.add(item);
        }
        if (now != null && !now.isEmpty()) {
            for (HabitNudge hb : habits) {
                if (!"check".equals(hb.kind) || hb.done || !hb.scheduled || hb.remindAt == null || hb.remindAt.isEmpty()) continue;
                Integer late = minutesBetween(hb.remindAt, now);
                if (late != null && late >= 0) {
                    Item item = new Item("habit", 30);
                    item.id = hb.id;
                    item.title = hb.title;
                    item.icon = hb.icon;
                    item.remindAt = hb.remindAt;
                    items.add(item);
                }
            }
            String cutoff = journalReminder == null || journalReminder.isEmpty() ? "20:00" : journalReminder;
            Integer sinceCutoff = minutesBetween(cutoff, now);
            if (entriesToday == 0 && sinceCutoff != null && sinceCutoff >= 0) {
                Item item = new Item("journal", 20);
                item.title = "Write today’s entry";
                items.add(item);
            }
        }
        if ("over".equals(spendStatus)) {
            Item item = new Item("budget", 70);
            item.title = "Over this month’s budget";
            item.overByMinor = overByMinor;
            items.add(item);
        }
        items.sort((a, b) -> Integer.compare(b.score, a.score)); // stable: ties keep their order
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }

    public static List<Item> buildAttention(String today, String now, List<Task> tasks, List<HabitNudge> habits,
                                            int entriesToday, String journalReminder, String spendStatus,
                                            long overByMinor) {
        return buildAttention(today, now, tasks, habits, entriesToday, journalReminder, spendStatus, overByMinor, 4);
    }
}
