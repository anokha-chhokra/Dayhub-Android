import app.dayhub.data.*;
import app.dayhub.data.Model.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class StatsParity {
    static Habit habit(JSONObject o) throws Exception {
        Habit h = new Habit();
        h.id = o.getInt("id"); h.kind = o.getString("kind"); h.target = o.getInt("target"); h.points = o.getInt("points");
        h.createdOn = o.getString("createdOn");
        h.archived = o.optBoolean("archived", false);
        JSONArray d = o.getJSONArray("days");
        h.days = new ArrayList<>();
        for (int i = 0; i < d.length(); i++) h.days.add(d.getInt(i));
        return h;
    }

    static int checks = 0;
    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) { System.out.println("MISMATCH " + what + ": java=" + got + " web=" + want); System.exit(1); }
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0])));
        JSONArray scenarios = root.getJSONArray("scenarios");
        for (int i = 0; i < scenarios.length(); i++) {
            JSONObject sc = scenarios.getJSONObject(i);
            List<Habit> habits = new ArrayList<>();
            for (int k = 0; k < sc.getJSONArray("habits").length(); k++) habits.add(habit(sc.getJSONArray("habits").getJSONObject(k)));
            List<HabitLog> logs = new ArrayList<>();
            for (int k = 0; k < sc.getJSONArray("logs").length(); k++) {
                JSONObject l = sc.getJSONArray("logs").getJSONObject(k);
                HabitLog log = new HabitLog(); log.habitId = l.getInt("habitId"); log.day = l.getString("day"); log.value = l.getInt("value");
                logs.add(log);
            }
            List<Entry> entries = new ArrayList<>();
            for (int k = 0; k < sc.getJSONArray("entries").length(); k++) {
                JSONObject e = sc.getJSONArray("entries").getJSONObject(k);
                Entry en = new Entry(); en.day = e.getString("day"); en.time = e.isNull("time") ? null : e.getString("time"); en.wordCount = e.getInt("wordCount");
                entries.add(en);
            }
            HabitStats s = HabitStats.compute(habits, logs, entries, sc.getString("today"));
            JSONObject w = sc.getJSONObject("expected");
            String id = "scenario " + i;
            eq(s.totalPoints, w.getInt("totalPoints"), id + " totalPoints");
            eq(s.todayPoints, w.getInt("todayPoints"), id + " todayPoints");
            eq(s.totalEntries, w.getInt("totalEntries"), id + " totalEntries");
            eq(s.totalWords, w.getInt("totalWords"), id + " totalWords");
            eq(s.habitDoneCount, w.getInt("habitDoneCount"), id + " habitDoneCount");
            eq(s.perfectDays, w.getInt("perfectDays"), id + " perfectDays");
            eq(s.streak, w.getInt("streak"), id + " streak");
            eq(s.longestStreak, w.getInt("longestStreak"), id + " longestStreak");
            eq(s.nightOwl, w.getBoolean("nightOwl"), id + " nightOwl");
            eq(s.earlyBird, w.getBoolean("earlyBird"), id + " earlyBird");
            List<String> badges = new ArrayList<>();
            for (int k = 0; k < w.getJSONArray("badges").length(); k++) badges.add(w.getJSONArray("badges").getString(k));
            eq(Badges.earnedIds(s), badges, id + " badges");
        }
        JSONArray probes = root.getJSONArray("probes");
        for (int i = 0; i < probes.length(); i++) {
            JSONObject p = probes.getJSONObject(i);
            Habit h = habit(p.getJSONObject("habit"));
            JSONObject w = p.getJSONObject("expected");
            if (p.has("day")) {
                eq(HabitRules.isScheduled(h, p.getString("day")), w.getBoolean("scheduled"), "probe " + i + " scheduled");
            } else {
                Integer v = p.isNull("value") ? null : p.getInt("value");
                String id = "probe " + i + " " + h.kind + " target " + h.target + " points " + h.points + " value " + v;
                eq(HabitRules.dayPoints(h, v), w.getInt("points"), id + " points");
                eq(HabitRules.isDone(h, v), w.getBoolean("done"), id + " done");
                eq(HabitRules.isOk(h, v), w.getBoolean("ok"), id + " ok");
                eq(HabitRules.isActivity(h, v), w.getBoolean("activity"), id + " activity");
            }
        }
        // Badge metadata (icons, names, descriptions, order) matches the web app exactly.
        JSONArray badges = root.getJSONArray("badges");
        eq(Badges.ALL.size(), badges.length(), "badge count");
        for (int i = 0; i < badges.length(); i++) {
            JSONObject b = badges.getJSONObject(i);
            Badges.Badge j = Badges.ALL.get(i);
            eq(j.id, b.getString("id"), "badge id " + i);
            eq(j.icon, b.getString("icon"), "badge icon " + b.getString("id"));
            eq(j.name, b.getString("name"), "badge name " + b.getString("id"));
            eq(j.desc, b.getString("desc"), "badge desc " + b.getString("id"));
        }
        System.out.println("IDENTICAL to the web app: " + scenarios.length() + " scenarios, " + probes.length() + " rule probes, " + badges.length() + " badges (" + checks + " comparisons)");
    }
}
