package app.dayhub.data;

import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Task;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Feature 28: what a tap on a widget does to the data, applied at once. A tick on a task, a step up or down
 * on a habit and a tap on a mood face are saved straight into Day Hub's data. They follow the rules of the
 * web app's widget queue (task done, habit log, mood), so a tap gives the same result as the same action
 * made inside the app. No Android code, so it can be checked on a plain JVM.
 */
public final class WidgetTaps {
    private WidgetTaps() { }

    /** What happened: whether anything was saved, and something to tell the person (or null). */
    public static final class Result {
        public final boolean changed;
        public final String message;

        Result(boolean changed, String message) {
            this.changed = changed;
            this.message = message;
        }
    }

    private static final Result NOTHING = new Result(false, null);

    /** The most a habit counter can reach from a widget. */
    private static final long MAX_VALUE = 10_000_000L;

    /** What a "+" tap logs: one step more. A check habit is switched on or off instead. */
    public static int valueAfterPlus(String kind, int value, int step) {
        if ("check".equals(kind)) return value >= 1 ? 0 : 1;
        return (int) Math.min(MAX_VALUE, (long) value + Math.max(1, step));
    }

    /** What a "-" tap logs: one step less, never below nothing. A check habit is switched on or off. */
    public static int valueAfterMinus(String kind, int value, int step) {
        if ("check".equals(kind)) return value >= 1 ? 0 : 1;
        return Math.max(0, value - Math.max(1, step));
    }

    /** Ticks a task off. Ignored if it is gone or already done (a second tap). */
    public static Result taskDone(DayHubData data, int taskId, String day, String time) {
        Task t = data.getTask(taskId);
        if (t == null || t.done) return NOTHING;
        try {
            data.updateTask(taskId, new JSONObject().put("done", true).put("today", day).put("time", time));
            data.commit();
            return new Result(true, null);
        } catch (DataError e) {
            data.rollback();
            return new Result(false, e.getMessage());
        } catch (JSONException e) {
            data.rollback();
            return new Result(false, "Could not update the task");
        }
    }

    /** One step up or down on a habit that is on today's list. */
    public static Result habitStep(DayHubData data, int habitId, boolean up, String day, String time) {
        HabitProgress progress = new HabitProgress(data);
        HabitDay h = progress.oneHabit(habitId, day);
        if (h == null || h.habit.archived || !h.scheduled) return NOTHING;
        int next = up ? valueAfterPlus(h.habit.kind, h.value, h.habit.step) : valueAfterMinus(h.habit.kind, h.value, h.habit.step);
        if (next == h.value) return NOTHING;
        try {
            JSONObject body = new JSONObject().put("day", day).put("value", Math.max(0, next)).put("today", day).put("time", time);
            HabitProgress.LogResult r = progress.logHabit(habitId, body);
            data.commit();
            return new Result(true, badges(r.progress));
        } catch (DataError e) {
            data.rollback();
            return new Result(false, e.getMessage());
        } catch (JSONException e) {
            data.rollback();
            return new Result(false, "Could not save that");
        }
    }

    /**
     * A tap on a mood face. A second tap within 30 minutes of a mood-only check-in corrects that check-in
     * instead of adding another, as on the Home screen.
     */
    public static Result mood(DayHubData data, int mood, String day, String time) {
        if (mood < 1 || mood > 5) return NOTHING;
        try {
            HabitProgress progress = new HabitProgress(data);
            Integer quickId = HomeData.compute(data, day, time).journal.quickMoodId;
            JSONObject body = new JSONObject().put("mood", mood).put("today", day);
            HabitProgress.EntryResult r = quickId != null
                    ? progress.updateEntry(quickId, body)
                    : progress.createEntry(body.put("day", day).put("time", time));
            data.commit();
            String badge = badges(r.progress);
            return new Result(true, "Mood saved " + Moods.emoji(mood) + (badge == null ? "" : ". " + badge));
        } catch (DataError e) {
            data.rollback();
            return new Result(false, e.getMessage());
        } catch (JSONException e) {
            data.rollback();
            return new Result(false, "Could not save the mood");
        }
    }

    private static String badges(HabitProgress.Synced progress) {
        if (progress.newBadges.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("New badge: ");
        for (int i = 0; i < progress.newBadges.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(progress.newBadges.get(i).icon).append(' ').append(progress.newBadges.get(i).name);
        }
        return sb.toString();
    }
}
