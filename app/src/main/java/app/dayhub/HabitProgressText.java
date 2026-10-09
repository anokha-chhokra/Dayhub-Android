package app.dayhub;

import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Habit;

import java.util.Arrays;
import java.util.List;

/** The short lines of text shown under a habit's name. */
public final class HabitProgressText {
    private static final List<String> DAY_NAMES = Arrays.asList("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");

    private HabitProgressText() {}

    /** "3 / 8 glasses", "2 (max 3 cups)" or "Reminder 07:30", plus the points earned that day. */
    public static String detail(HabitDay h) {
        Habit habit = h.habit;
        String base;
        if (habit.kind.equals("goal")) base = (h.value + " / " + habit.target + " " + habit.unit).trim();
        else if (habit.kind.equals("limit")) base = (h.value + " (max " + habit.target + " " + habit.unit + ")").trim();
        else base = habit.remindAt != null && !habit.remindAt.isEmpty() ? "Reminder " + habit.remindAt : "";
        int pts = h.pointsToday;
        if (pts == 0) return base;
        return base + (base.isEmpty() ? "" : " · ") + (pts > 0 ? "+" : "") + pts + " pts";
    }

    /** "Every day" or "Mon, Wed, Fri". */
    public static String schedule(Habit habit) {
        if (habit.days.size() == 7) return "Every day";
        StringBuilder sb = new StringBuilder();
        for (int d : habit.days) sb.append(sb.length() == 0 ? "" : ", ").append(DAY_NAMES.get(d));
        return sb.toString();
    }
}
