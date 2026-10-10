import app.dayhub.data.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

/**
 * Compares the widget data with the web app's own: the snapshot (buildSnapshot, run on the real backend) and
 * the rows each list widget shows (the web wrapper's WidgetRows, compiled from the web project).
 */
public class WidgetParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    static final WidgetSnapshot.Labels LABELS = new WidgetSnapshot.Labels() {
        @Override public String money(long minor, String currency) { return currency + ":" + String.format(Locale.ROOT, "%.2f", minor / 100.0); }
        @Override public String dueLabel(String dueOn, String today) { return "due(" + dueOn + "|" + today + ")"; }
        @Override public String greeting(String name) { return "hi " + (name == null ? "" : name); }
    };

    /** The snapshot as the web app writes it, so the web wrapper's own SnapshotData can read it back. */
    static JSONObject toJson(WidgetSnapshot s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", 1);
        o.put("day", s.day);
        o.put("name", s.name);
        o.put("greeting", s.greeting);
        o.put("points", s.points);
        o.put("streak", s.streak);
        JSONArray att = new JSONArray();
        for (WidgetSnapshot.Attention a : s.attention) att.put(new JSONObject().put("title", a.title).put("sub", a.sub).put("bad", a.bad).put("go", a.go));
        o.put("attention", att);
        JSONArray ts = new JSONArray();
        for (WidgetSnapshot.TaskRow t : s.tasks) ts.put(new JSONObject().put("id", t.id).put("title", t.title).put("star", t.star).put("sub", t.sub).put("overdue", t.overdue));
        o.put("tasks", ts);
        o.put("taskCount", s.taskCount);
        o.put("doneToday", s.doneToday);
        JSONArray hs = new JSONArray();
        for (WidgetSnapshot.Habit h : s.habits) {
            hs.put(new JSONObject().put("id", h.id).put("title", h.title).put("icon", h.icon).put("kind", h.kind).put("value", h.value)
                    .put("target", h.target).put("step", h.step).put("unit", h.unit).put("done", h.done));
        }
        o.put("habits", hs);
        o.put("habitsDone", s.habitsDone);
        o.put("habitsTotal", s.habitsTotal);
        o.put("spend", new JSONObject().put("total", s.spend.total).put("line", s.spend.line).put("today", s.spend.today)
                .put("pct", s.spend.pct).put("over", s.spend.over).put("hasBudget", s.spend.hasBudget));
        o.put("journal", new JSONObject().put("count", s.journalCount).put("mood", s.mood).put("moodEmoji", s.moodEmoji));
        return o;
    }

    /** The web's null (no mood yet) and our 0 are the same thing. */
    static Object plain(Object v) {
        if (v == JSONObject.NULL) return 0;
        return v;
    }

    static void sameJson(JSONObject mine, JSONObject web, String path) throws Exception {
        for (Iterator<String> it = web.keys(); it.hasNext();) {
            String k = it.next();
            if (k.equals("at")) continue;
            eq(mine.has(k), true, path + "." + k + " present");
            Object w = web.get(k), m = mine.get(k);
            if (w instanceof JSONObject) sameJson((JSONObject) m, (JSONObject) w, path + "." + k);
            else if (w instanceof JSONArray) {
                JSONArray wa = (JSONArray) w, ma = (JSONArray) m;
                eq(ma.length(), wa.length(), path + "." + k + " length");
                for (int i = 0; i < wa.length(); i++) {
                    if (wa.get(i) instanceof JSONObject) sameJson(ma.getJSONObject(i), wa.getJSONObject(i), path + "." + k + "[" + i + "]");
                    else eq(ma.get(i), wa.get(i), path + "." + k + "[" + i + "]");
                }
            } else {
                Object pw = plain(w), pm = plain(m);
                if (pw instanceof Number && pm instanceof Number) eq(((Number) pm).longValue(), ((Number) pw).longValue(), path + "." + k);
                else eq(pm, pw, path + "." + k);
            }
        }
        for (Iterator<String> it = mine.keys(); it.hasNext();) {
            String k = it.next();
            eq(web.has(k), true, path + "." + k + " is something the web app has too");
        }
    }

    static String row(app.dayhub.data.WidgetRows.Row r) {
        return r.type + "|" + r.id + "|" + r.title + "|" + r.sub + "|" + r.icon + "|" + r.right + "|" + r.go + "|" + r.habitKind + "|" + r.progress + "|" + r.flag + "|" + r.done;
    }

    static String row(app.dayhub.core.WidgetRows.Row r) {
        return r.type + "|" + r.id + "|" + r.title + "|" + r.sub + "|" + r.icon + "|" + r.right + "|" + r.go + "|" + r.habitKind + "|" + r.progress + "|" + r.flag + "|" + r.done;
    }

    static void sameRows(List<app.dayhub.data.WidgetRows.Row> mine, List<app.dayhub.core.WidgetRows.Row> web, String what) {
        eq(mine.size(), web.size(), what + " row count");
        for (int i = 0; i < web.size(); i++) eq(row(mine.get(i)), row(web.get(i)), what + " row " + i);
    }

    public static void main(String[] a) throws Exception {
        JSONArray scenarios = new JSONArray(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        int snapshots = 0, rowSets = 0;
        for (int n = 0; n < scenarios.length(); n++) {
            JSONObject sc = scenarios.getJSONObject(n);
            DayHubData data = DayHubData.inMemory();
            data.importAll(sc.getJSONObject("doc"));
            JSONArray queries = sc.getJSONArray("queries");
            for (int q = 0; q < queries.length(); q++) {
                JSONObject c = queries.getJSONObject(q);
                String id = "scenario " + n + " query " + q + " " + c.getString("today") + " " + c.getString("now");
                JSONObject webJson = c.getJSONObject("snapshot");
                HomeData home = HomeData.compute(data, c.getString("today"), c.getString("now"));
                WidgetSnapshot mine = WidgetSnapshot.build(home, LABELS);
                JSONObject mineJson = toJson(mine);
                sameJson(mineJson, webJson, id);
                snapshots++;

                // the rows each list widget shows, from the same snapshot, by the web wrapper's own code
                app.dayhub.core.SnapshotData webSnap = app.dayhub.core.SnapshotData.fromJson(webJson.toString());
                String today = c.getString("today");
                for (String when : new String[] {today, "2000-01-01", null}) {
                    for (int[] m : new int[][] {{5, 5}, {2, 1}, {15, 15}, {0, 0}, {1, 7}}) {
                        sameRows(app.dayhub.data.WidgetRows.home(mine, when, m[0], m[1]), app.dayhub.core.WidgetRows.home(webSnap, when, m[0], m[1]), id + " home@" + when + " " + m[0] + "," + m[1]);
                        rowSets++;
                    }
                    for (int max : new int[] {15, 3, 0, 1}) {
                        sameRows(app.dayhub.data.WidgetRows.tasks(mine, max), app.dayhub.core.WidgetRows.tasks(webSnap, max), id + " tasks " + max);
                        sameRows(app.dayhub.data.WidgetRows.habits(mine, when, max), app.dayhub.core.WidgetRows.habits(webSnap, when, max), id + " habits@" + when + " " + max);
                        rowSets += 2;
                    }
                }
            }
        }
        // no data yet: every list says to open Day Hub
        sameRows(app.dayhub.data.WidgetRows.home(null, "2026-10-10", 5, 5), app.dayhub.core.WidgetRows.home(null, "2026-10-10", 5, 5), "home without data");
        sameRows(app.dayhub.data.WidgetRows.tasks(null, 15), app.dayhub.core.WidgetRows.tasks(null, 15), "tasks without data");
        sameRows(app.dayhub.data.WidgetRows.habits(null, "2026-10-10", 15), app.dayhub.core.WidgetRows.habits(null, "2026-10-10", 15), "habits without data");
        System.out.println("IDENTICAL to the web app: " + snapshots + " snapshots, " + rowSets + " widget row sets (" + checks + " comparisons)");
    }
}
