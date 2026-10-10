import app.dayhub.data.*;
import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class HabitsParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    static String s(JSONObject o, String k) throws Exception {
        return o.isNull(k) ? null : o.getString(k);
    }

    static String habitKey(Habit h) {
        return h.id + "|" + h.title + "|" + h.icon + "|" + h.kind + "|" + h.unit + "|" + h.target + "|" + h.step + "|" + h.points + "|" + h.days + "|" + h.remindAt + "|" + h.archived;
    }

    static String habitKey(JSONObject o) throws Exception {
        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < o.getJSONArray("days").length(); i++) days.add(o.getJSONArray("days").getInt(i));
        return o.getInt("id") + "|" + o.getString("title") + "|" + o.getString("icon") + "|" + o.getString("kind") + "|" + o.getString("unit") + "|"
                + o.getInt("target") + "|" + o.getInt("step") + "|" + o.getInt("points") + "|" + days + "|" + s(o, "remindAt") + "|" + o.getBoolean("archived");
    }

    static String dayKey(HabitDay d) {
        return d.habit.id + "|" + d.scheduled + "|" + d.value + "|" + d.done + "|" + d.pointsToday;
    }

    static String dayKey(JSONObject o) throws Exception {
        return o.getInt("id") + "|" + o.getBoolean("scheduled") + "|" + o.getInt("value") + "|" + o.getBoolean("done") + "|" + o.getInt("pointsToday");
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        String today = root.getString("today");
        JSONArray ops = root.getJSONArray("ops");
        DayHubData d = DayHubData.inMemory();
        HabitProgress progress = new HabitProgress(d);
        int refused = 0;
        for (int i = 0; i < ops.length(); i++) {
            JSONObject op = ops.getJSONObject(i);
            String kind = op.getString("kind");
            String id = "op " + i + " " + kind + " " + op.opt("body") + " id=" + op.opt("id");
            String error = null;
            int status = 0;
            HabitProgress.LogResult log = null;
            try {
                switch (kind) {
                    case "create": d.createHabit(new JSONObject(op.getJSONObject("body").toString())); break;
                    case "patch": d.updateHabit(op.getInt("id"), new JSONObject(op.getJSONObject("body").toString())); break;
                    case "log": log = progress.logHabit(op.getInt("id"), new JSONObject(op.getJSONObject("body").toString())); break;
                    default: d.archiveHabit(op.getInt("id"));
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
            if (log != null) {
                JSONObject w = op.getJSONObject("logResult");
                eq(dayKey(log.habit), dayKey(w.getJSONObject("habit")), id + " logged habit");
                eq(log.progress.stats.totalPoints, w.getJSONObject("stats").getInt("totalPoints"), id + " points after log");
                eq(log.progress.stats.todayPoints, w.getJSONObject("stats").getInt("todayPoints"), id + " today points after log");
                eq(log.progress.stats.streak, w.getJSONObject("stats").getInt("streak"), id + " streak after log");
                List<String> badges = new ArrayList<>();
                for (Badges.Badge b : log.progress.newBadges) badges.add(b.id);
                List<String> want = new ArrayList<>();
                for (int k = 0; k < op.getJSONArray("newBadges").length(); k++) want.add(op.getJSONArray("newBadges").getString(k));
                eq(badges, want, id + " new badges");
            }

            JSONObject after = op.getJSONObject("after");
            List<String> mine = new ArrayList<>(), theirs = new ArrayList<>();
            for (Habit h : d.listHabits(true)) mine.add(habitKey(h));
            for (int k = 0; k < after.getJSONArray("habits").length(); k++) theirs.add(habitKey(after.getJSONArray("habits").getJSONObject(k)));
            eq(mine, theirs, id + " habits");

            mine = new ArrayList<>();
            theirs = new ArrayList<>();
            for (HabitLog l : d.listLogs(null, null)) mine.add(l.habitId + "|" + l.day + "|" + l.value + "|" + l.time);
            for (int k = 0; k < after.getJSONArray("logs").length(); k++) {
                JSONObject l = after.getJSONArray("logs").getJSONObject(k);
                theirs.add(l.getInt("habitId") + "|" + l.getString("day") + "|" + l.getInt("value") + "|" + s(l, "time"));
            }
            eq(mine, theirs, id + " logs");

            JSONArray week = after.getJSONArray("week");
            List<HabitProgress.DaySummary> summaries = progress.recentDays(today, 7);
            for (int k = 0; k < week.length(); k++) {
                JSONObject wd = week.getJSONObject(k);
                String day = wd.getString("day");
                mine = new ArrayList<>();
                theirs = new ArrayList<>();
                for (HabitDay hd : progress.habitsForDay(day, false)) mine.add(dayKey(hd));
                int sched = 0, done = 0;
                for (int j = 0; j < wd.getJSONArray("list").length(); j++) {
                    JSONObject h = wd.getJSONArray("list").getJSONObject(j);
                    theirs.add(dayKey(h));
                    if (h.getBoolean("scheduled")) {
                        sched++;
                        if (h.getBoolean("done")) done++;
                    }
                }
                eq(mine, theirs, id + " habits on " + day);
                eq(summaries.get(k).day, day, id + " week day " + k);
                eq(summaries.get(k).scheduled, sched, id + " week scheduled " + day);
                eq(summaries.get(k).done, done, id + " week done " + day);
            }

            HabitProgress.StatsView view = progress.statsView(today);
            JSONObject ws = after.getJSONObject("stats");
            eq(view.stats.totalPoints, ws.getInt("totalPoints"), id + " totalPoints");
            eq(view.stats.todayPoints, ws.getInt("todayPoints"), id + " todayPoints");
            eq(view.stats.streak, ws.getInt("streak"), id + " streak");
            eq(view.stats.longestStreak, ws.getInt("longestStreak"), id + " longestStreak");
            mine = new ArrayList<>();
            theirs = new ArrayList<>();
            for (Badges.Status b : view.badges) mine.add(b.badge.id + "=" + b.unlockedOn);
            for (int k = 0; k < ws.getJSONArray("badges").length(); k++) {
                JSONArray b = ws.getJSONArray("badges").getJSONArray(k);
                theirs.add(b.getString(0) + "=" + (b.isNull(1) ? null : b.getString(1)));
            }
            eq(mine, theirs, id + " badges");
        }
        System.out.println("IDENTICAL to the web app: " + ops.length() + " habit operations (" + (ops.length() - refused) + " accepted, "
                + refused + " refused), " + checks + " comparisons");
    }
}
