package app.dayhub.data;

import app.dayhub.data.HabitProgress.HabitDay;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature 29: which reminders there are on a day and which are due. The rules are the web app's reminder
 * check: the daily journal reminder (if a time is set) and each habit that is on today's list, has a reminder
 * time and is not done yet. A reminder is due once the clock has reached its time. No Android code, so the
 * rules can be checked on a plain JVM; the alarms and notifications are built on top of them.
 */
public final class ReminderPlan {
    private ReminderPlan() { }

    public static final String JOURNAL = "journal";

    /** One reminder. {@code key} is "journal" or "h" plus the habit's id, and is how a day's reminders are told apart. */
    public static final class Reminder {
        public final String key;
        public final String time;
        public final String title;
        public final String body;

        Reminder(String key, String time, String title, String body) {
            this.key = key;
            this.time = time;
            this.title = title;
            this.body = body;
        }

        public boolean isJournal() {
            return JOURNAL.equals(key);
        }
    }

    /** True if something has been written in the journal on {@code day}. */
    public static boolean writtenOn(DayHubData data, String day) {
        return !data.listEntries(day, Validate.addDays(day, 1)).isEmpty();
    }

    /**
     * Every reminder still waiting on {@code day}, with its time, in the web app's order: the journal one,
     * then habits. Habits already done that day are not waiting. The journal reminder is included whether
     * or not something has been written (see {@link #dueNow}).
     */
    public static List<Reminder> waiting(DayHubData data, String day) {
        List<Reminder> out = new ArrayList<>();
        String journal = data.getSettings().journalReminder;
        if (journal != null && !journal.isEmpty()) {
            out.add(new Reminder(JOURNAL, journal, "Time to write", "Take a minute for today's journal entry."));
        }
        for (HabitDay h : new HabitProgress(data).habitsForDay(day, false)) {
            String at = h.habit.remindAt;
            if (h.scheduled && at != null && !at.isEmpty() && !h.done) {
                out.add(new Reminder("h" + h.habit.id, at, h.habit.icon + " " + h.habit.title, "Reminder from Day Hub"));
            }
        }
        return out;
    }

    /** The web app's rule: every waiting reminder whose time has been reached by {@code now} ("HH:MM"). */
    public static List<Reminder> due(DayHubData data, String day, String now) {
        List<Reminder> out = new ArrayList<>();
        for (Reminder r : waiting(data, day)) if (r.time.compareTo(now) <= 0) out.add(r);
        return out;
    }

    /**
     * What to tell the person now: the due reminders, except that the journal reminder is dropped if
     * something has already been written today, and (when {@code maxLateMinutes} is not negative) any reminder
     * that is later than that, so a phone that was off all morning does not announce the morning at noon.
     */
    public static List<Reminder> dueNow(DayHubData data, String day, String now, int maxLateMinutes) {
        List<Reminder> out = new ArrayList<>();
        boolean written = writtenOn(data, day);
        for (Reminder r : due(data, day, now)) {
            if (r.isJournal() && written) continue;
            if (maxLateMinutes >= 0) {
                Integer late = Insights.minutesBetween(r.time, now);
                if (late == null || late > maxLateMinutes) continue;
            }
            out.add(r);
        }
        return out;
    }

    /**
     * The time of the next reminder strictly after {@code now}, or null if there is none left today. It
     * skips the journal reminder when something has been written, and habits already done.
     */
    public static String next(DayHubData data, String day, String now) {
        boolean written = writtenOn(data, day);
        String best = null;
        for (Reminder r : waiting(data, day)) {
            if (r.isJournal() && written) continue;
            if (r.time.compareTo(now) > 0 && (best == null || r.time.compareTo(best) < 0)) best = r.time;
        }
        return best;
    }
}
