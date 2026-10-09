package app.dayhub;

import android.app.Activity;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.HabitProgress;
import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Habit;
import app.dayhub.data.Validate;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Feature 15: the things you can do to a habit. Each one changes the data, saves it, and tells the
 * screen to redraw. Problems come back as a message (or a toast), never a crash.
 */
public final class HabitActions {
    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final Runnable onChanged;

    public HabitActions(Activity activity, DayHubData data, Overlays overlays, Runnable onChanged) {
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.onChanged = onChanged;
    }

    /**
     * Sets a habit's progress for a day (0 clears it) and shows any badge it earns. A check habit
     * counts as done at 1; goals and limits count up.
     */
    public void log(HabitDay habit, String day, int value) {
        String today = Validate.localDate();
        try {
            JSONObject body = new JSONObject().put("day", day).put("value", Math.max(0, value)).put("today", today);
            if (day.equals(today)) body.put("time", DateLabels.nowHHMM());
            HabitProgress.LogResult result = new HabitProgress(data).logHabit(habit.habit.id, body);
            data.commit();
            if (!result.progress.newBadges.isEmpty()) {
                StringBuilder sb = new StringBuilder("New badge: ");
                for (int i = 0; i < result.progress.newBadges.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(result.progress.newBadges.get(i).icon).append(' ').append(result.progress.newBadges.get(i).name);
                }
                overlays.toast(sb.toString());
            }
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        } catch (JSONException e) {
            overlays.toast("Could not save that");
        }
        onChanged.run();
    }

    /** Adds a habit from a body (see {@link DayHubData#createHabit}). Returns an error message, or null. */
    public String create(JSONObject body) {
        try {
            data.createHabit(body);
            data.commit();
        } catch (DataError e) {
            return e.getMessage();
        }
        onChanged.run();
        return null;
    }

    /** Edits a habit from a body. Returns an error message, or null. */
    public String update(int id, JSONObject body) {
        try {
            data.updateHabit(id, body);
            data.commit();
        } catch (DataError e) {
            return e.getMessage();
        }
        onChanged.run();
        return null;
    }

    /** "Deletes" a habit: it is hidden but its history stays, so points and streaks are kept. */
    public void delete(Habit habit) {
        try {
            data.archiveHabit(habit.id);
            data.commit();
            overlays.toast("Habit deleted. Your points and streak are kept.");
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        }
        onChanged.run();
    }

    /** Opens the add (habit == null) or edit sheet. */
    public void openSheet(Habit habit) {
        HabitSheet.open(activity, overlays, this, habit);
    }
}
