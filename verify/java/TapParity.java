import app.dayhub.data.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

/** Stage C of the widget tap check: the same taps through WidgetTaps, compared with what the web app ended up with. */
public class TapParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    static Object nul(JSONObject o, String k) throws Exception {
        return o.has(k) && !o.isNull(k) ? o.get(k) : null;
    }

    static List<Object> row(Object... xs) throws Exception {
        List<Object> l = new ArrayList<>();
        for (Object x : xs) l.add(x instanceof Number && !(x instanceof Double) ? (Object) ((Number) x).longValue() : x instanceof JSONArray ? list((JSONArray) x) : x);
        return l;
    }

    static List<Object> list(JSONArray a) throws Exception {
        List<Object> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object x = a.get(i);
            l.add(x instanceof Number ? (Object) ((Number) x).longValue() : x);
        }
        return l;
    }

    /** The same projection as the web side: what the person could notice. */
    static Map<String, Object> project(DayHubData data, String day) throws Exception {
        JSONObject exp = data.exportAll();
        Map<String, Object> out = new LinkedHashMap<>();
        List<Object> tasks = new ArrayList<>();
        JSONArray ts = exp.getJSONArray("tasks");
        for (int i = 0; i < ts.length(); i++) {
            JSONObject t = ts.getJSONObject(i);
            tasks.add(row(t.getInt("id"), t.getString("title"), nul(t, "dueOn"), t.has("priority") ? t.getInt("priority") : 0, t.optBoolean("done"), nul(t, "doneOn"), nul(t, "doneTime")));
        }
        out.put("tasks", tasks);
        List<Object> logs = new ArrayList<>();
        JSONArray ls = exp.getJSONArray("habitLogs");
        for (int i = 0; i < ls.length(); i++) {
            JSONObject l = ls.getJSONObject(i);
            logs.add(row(l.getInt("habitId"), l.getString("day"), l.getInt("value"), nul(l, "time")));
        }
        out.put("habitLogs", logs);
        List<Object> journal = new ArrayList<>();
        JSONArray js = exp.getJSONArray("journal");
        for (int i = 0; i < js.length(); i++) {
            JSONObject e = js.getJSONObject(i);
            journal.add(row(e.getInt("id"), e.getString("day"), nul(e, "time"), e.optString("text", ""), nul(e, "mood"),
                    e.has("tags") ? e.getJSONArray("tags") : new JSONArray(), e.has("wordCount") ? e.getInt("wordCount") : 0));
        }
        out.put("journal", journal);
        List<Object> badges = new ArrayList<>();
        JSONObject bs = exp.getJSONObject("badges");
        for (Iterator<String> it = bs.keys(); it.hasNext();) {
            String id = it.next();
            badges.add(id + "@" + bs.get(id));
        }
        Collections.sort((List) badges);
        out.put("badges", badges);
        HomeData.Stats stats = HomeData.compute(data, day, null).stats;
        out.put("stats", row(stats.totalPoints, stats.streak));
        return out;
    }

    static Map<String, Object> fromWeb(JSONObject w) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : new String[] {"tasks", "habitLogs", "journal"}) {
            List<Object> rows = new ArrayList<>();
            JSONArray a = w.getJSONArray(k);
            for (int i = 0; i < a.length(); i++) {
                JSONArray r = a.getJSONArray(i);
                List<Object> l = new ArrayList<>();
                for (int j = 0; j < r.length(); j++) {
                    Object x = r.get(j);
                    l.add(x == JSONObject.NULL ? null : x instanceof Integer || x instanceof Long ? (Object) ((Number) x).longValue()
                            : x instanceof JSONArray ? list((JSONArray) x) : x);
                }
                rows.add(l);
            }
            out.put(k, rows);
        }
        out.put("badges", list(w.getJSONArray("badges")));
        out.put("stats", list(w.getJSONArray("stats")));
        return out;
    }

    public static void main(String[] a) throws Exception {
        JSONArray plan = new JSONArray(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        JSONArray expected = new JSONArray(Files.readString(Path.of(a[1]), java.nio.charset.StandardCharsets.UTF_8));
        eq(plan.length(), expected.length(), "same number of sequences");
        int taps = 0, saved = 0, tasksTicked = 0, habitSteps = 0, moods = 0, corrected = 0;
        for (int n = 0; n < plan.length(); n++) {
            JSONObject item = plan.getJSONObject(n);
            String day = item.getString("day");
            DayHubData data = DayHubData.inMemory();
            data.importAll(item.getJSONObject("doc"));
            JSONArray list = item.getJSONArray("taps");
            for (int k = 0; k < list.length(); k++) {
                JSONObject t = list.getJSONObject(k);
                String time = t.getString("time");
                WidgetTaps.Result r;
                switch (t.getString("type")) {
                    case "task":
                        r = WidgetTaps.taskDone(data, t.getInt("id"), day, time);
                        if (r.changed) tasksTicked++;
                        break;
                    case "habit":
                        r = WidgetTaps.habitStep(data, t.getInt("id"), t.getBoolean("up"), day, time);
                        if (r.changed) habitSteps++;
                        break;
                    default:
                        int before = data.exportAll().getJSONArray("journal").length();
                        r = WidgetTaps.mood(data, t.getInt("mood"), day, time);
                        if (r.changed) {
                            moods++;
                            if (data.exportAll().getJSONArray("journal").length() == before) corrected++;
                        }
                }
                taps++;
                if (r.changed) saved++;
            }
            eq(project(data, day), fromWeb(expected.getJSONObject(n)), "sequence " + n + " (" + day + ", " + list.length() + " taps) final data");
        }
        System.out.println("IDENTICAL to the web app: " + plan.length() + " tap sequences, " + taps + " taps (" + tasksTicked + " ticks, " + habitSteps
                + " habit steps, " + moods + " mood taps of which " + corrected + " corrected a check-in) (" + checks + " comparisons)");
    }
}
