package app.dayhub.data;

import app.dayhub.data.Model.Task;

import java.util.ArrayList;
import java.util.List;

/** Open tasks sorted into the groups the Tasks screen shows, as the web app does. Order within a group is kept. */
public final class TaskGroups {
    public final List<Task> overdue = new ArrayList<>();
    public final List<Task> today = new ArrayList<>();
    public final List<Task> upcoming = new ArrayList<>();
    public final List<Task> noDate = new ArrayList<>();

    private TaskGroups() {}

    /** @param todayYmd "YYYY-MM-DD" */
    public static TaskGroups of(List<Task> tasks, String todayYmd) {
        TaskGroups g = new TaskGroups();
        for (Task t : tasks) {
            if (t.dueOn == null || t.dueOn.isEmpty()) g.noDate.add(t);
            else if (t.dueOn.compareTo(todayYmd) < 0) g.overdue.add(t);
            else if (t.dueOn.equals(todayYmd)) g.today.add(t);
            else g.upcoming.add(t);
        }
        return g;
    }
}
