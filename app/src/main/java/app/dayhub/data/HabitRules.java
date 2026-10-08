package app.dayhub.data;

import app.dayhub.data.Model.Habit;

import java.time.LocalDate;

/**
 * Feature 7: the rules for habit points and schedules, ported from the web app's habits.js. Pure
 * functions of a habit and a value: no storage, no clock. Days are "YYYY-MM-DD" text.
 */
public final class HabitRules {
    /** Points for the first journal entry written on a day. */
    public static final int JOURNAL_DAY_POINTS = 10;

    private HabitRules() {}

    /** 0 = Sunday, like the web app. */
    public static int weekday(String ymd) {
        return LocalDate.parse(ymd).getDayOfWeek().getValue() % 7;
    }

    /** A habit runs on a day if its weekday list includes it and it already existed. */
    public static boolean isScheduled(Habit habit, String ymd) {
        if (habit.createdOn != null && ymd.compareTo(habit.createdOn) < 0) return false;
        return habit.days.contains(weekday(ymd));
    }

    private static int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    /** Points for one habit on one day. Limits cost points when you go over. */
    public static int dayPoints(Habit habit, Integer value) {
        int v = nonNegative(value);
        if (habit.kind.equals("check")) return v >= 1 ? habit.points : 0;
        if (habit.kind.equals("goal")) {
            return (int) Math.round((double) habit.points * Math.min(v, habit.target) / Math.max(1, habit.target));
        }
        // limit: logging something under the limit earns points, going over costs them
        if (v == 0) return 0;
        return v <= habit.target ? habit.points : -habit.points;
    }

    /** "Done" for display: check ticked, goal reached, limit logged and not exceeded. */
    public static boolean isDone(Habit habit, Integer value) {
        int v = nonNegative(value);
        if (habit.kind.equals("check")) return v >= 1;
        if (habit.kind.equals("goal")) return v >= habit.target;
        return v > 0 && v <= habit.target;
    }

    /** "Fine" for a clean day: like done, but a limit you never touched is fine too. */
    public static boolean isOk(Habit habit, Integer value) {
        if (habit.kind.equals("limit")) return nonNegative(value) <= habit.target;
        return isDone(habit, value);
    }

    /** Counts as "showing up": something done, not just logged over a limit. */
    public static boolean isActivity(Habit habit, Integer value) {
        int v = nonNegative(value);
        if (v <= 0) return false;
        if (habit.kind.equals("limit")) return v <= habit.target;
        return true; // any progress on a check or goal counts, even if rounding gives 0 points
    }
}
