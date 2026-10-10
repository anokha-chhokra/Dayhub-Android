import app.dayhub.data.*;
import app.dayhub.data.Model.Task;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class TasksParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    static String project(Task t) {
        return t.id + "|" + t.title + "|" + t.dueOn + "|" + t.priority + "|" + t.done + "|" + t.doneOn + "|" + t.doneTime;
    }

    static String project(JSONObject o) throws Exception {
        return o.getInt("id") + "|" + o.getString("title") + "|" + (o.isNull("dueOn") ? null : o.getString("dueOn")) + "|"
                + o.getInt("priority") + "|" + o.getBoolean("done") + "|" + (o.isNull("doneOn") ? null : o.getString("doneOn")) + "|"
                + (o.isNull("doneTime") ? null : o.getString("doneTime"));
    }

    static List<String> projected(List<Task> tasks) {
        List<String> out = new ArrayList<>();
        for (Task t : tasks) out.add(project(t));
        return out;
    }

    static List<String> projected(JSONArray a) throws Exception {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(project(a.getJSONObject(i)));
        return out;
    }

    static List<Integer> ids(List<Task> tasks) {
        List<Integer> out = new ArrayList<>();
        for (Task t : tasks) out.add(t.id);
        return out;
    }

    static List<Integer> ids(JSONArray a) throws Exception {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.getInt(i));
        return out;
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        String today = root.getString("today");
        JSONArray ops = root.getJSONArray("ops");
        DayHubData d = DayHubData.inMemory();
        int refused = 0;
        for (int i = 0; i < ops.length(); i++) {
            JSONObject op = ops.getJSONObject(i);
            String id = "op " + i + " " + op.getString("kind") + " " + op.opt("body") + " id=" + op.opt("id");
            String error = null;
            int status = 0;
            try {
                switch (op.getString("kind")) {
                    case "create":
                        d.createTask(new JSONObject(op.getJSONObject("body").toString()));
                        break;
                    case "patch":
                        d.updateTask(op.getInt("id"), new JSONObject(op.getJSONObject("body").toString()));
                        break;
                    default:
                        d.deleteTask(op.getInt("id"));
                }
            } catch (DataError e) {
                error = e.getMessage();
                status = e.status;
            }
            if (error != null) refused++;
            eq(error == null ? "ok" : "refused", op.getInt("status") < 400 ? "ok" : "refused", id + " accepted?");
            if (error != null) {
                eq(status, op.getInt("status"), id + " status");
                eq(error, op.getString("error"), id + " message");
            }
            JSONObject after = op.getJSONObject("after");
            eq(projected(d.listTasks("open")), projected(after.getJSONArray("open")), id + " open list");
            eq(projected(d.listTasks("done")), projected(after.getJSONArray("done")), id + " done list");
            eq(projected(d.listTasks("all")), projected(after.getJSONArray("all")), id + " all list");
            TaskGroups g = TaskGroups.of(d.listTasks("open"), today);
            JSONObject wg = after.getJSONObject("groups");
            eq(ids(g.overdue), ids(wg.getJSONArray("overdue")), id + " overdue group");
            eq(ids(g.today), ids(wg.getJSONArray("today")), id + " today group");
            eq(ids(g.upcoming), ids(wg.getJSONArray("upcoming")), id + " upcoming group");
            eq(ids(g.noDate), ids(wg.getJSONArray("noDate")), id + " no-date group");
        }
        System.out.println("IDENTICAL to the web app: " + ops.length() + " task operations (" + (ops.length() - refused) + " accepted, "
                + refused + " refused), " + checks + " comparisons");
    }
}
