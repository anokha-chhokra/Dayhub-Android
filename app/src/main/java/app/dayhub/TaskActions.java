package app.dayhub;

import android.app.Activity;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.Model.Task;
import app.dayhub.data.Validate;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Feature 14: the things you can do to a task. Each one changes the data, saves it, and tells the
 * screen to redraw. Problems come back as a message (or a toast), never a crash.
 */
public final class TaskActions {
    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final Runnable onChanged;

    public TaskActions(Activity activity, DayHubData data, Overlays overlays, Runnable onChanged) {
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.onChanged = onChanged;
    }

    /** Adds a task from a body (title, dueOn, priority). Returns an error message, or null on success. */
    public String create(JSONObject body) {
        try {
            data.createTask(body);
            data.commit();
        } catch (DataError e) {
            return e.getMessage();
        }
        onChanged.run();
        return null;
    }

    /** Edits a task from a body (title, dueOn, priority). Returns an error message, or null on success. */
    public String update(int id, JSONObject body) {
        try {
            data.updateTask(id, body);
            data.commit();
        } catch (DataError e) {
            return e.getMessage();
        }
        onChanged.run();
        return null;
    }

    private void change(int id, JSONObject body) throws JSONException {
        String error = update(id, body);
        if (error != null) {
            overlays.toast(error);
            onChanged.run(); // show what is really saved
        }
    }

    public void setDone(Task task, boolean done) {
        try {
            change(task.id, new JSONObject().put("done", done).put("today", Validate.localDate())
                    .put("time", DateLabels.nowHHMM()));
        } catch (JSONException e) {
            overlays.toast("Could not update the task");
        }
    }

    public void toggleStar(Task task) {
        try {
            change(task.id, new JSONObject().put("priority", task.priority != 0 ? 0 : 1));
        } catch (JSONException e) {
            overlays.toast("Could not update the task");
        }
    }

    /** Deletes the task and offers Undo, which adds it back (finished tasks come back finished). */
    public void deleteWithUndo(Task task) {
        try {
            data.deleteTask(task.id);
            data.commit();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
            onChanged.run();
            return;
        }
        onChanged.run();
        overlays.toast("Task deleted", () -> restore(task));
    }

    private void restore(Task task) {
        try {
            JSONObject body = new JSONObject().put("title", task.title)
                    .put("dueOn", task.dueOn == null ? JSONObject.NULL : task.dueOn).put("priority", task.priority);
            Task back = data.createTask(body);
            if (task.done) {
                data.updateTask(back.id, new JSONObject().put("done", true)
                        .put("today", task.doneOn != null ? task.doneOn : Validate.localDate()));
            }
            data.commit();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        } catch (JSONException e) {
            overlays.toast("Could not bring the task back");
        }
        onChanged.run();
    }

    /** Opens the add (task == null) or edit sheet. */
    public void openSheet(Task task) {
        TaskSheet.open(activity, overlays, this, task);
    }
}
