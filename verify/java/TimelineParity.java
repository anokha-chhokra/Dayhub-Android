import app.dayhub.data.*;
import app.dayhub.data.Model.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

/** Runs the Java timeline, calendar and stats on the same data the web app saw, and writes what it got. */
public class TimelineParity {
    static JSONObject habit(Timeline.DayHabit h) throws Exception {
        return new JSONObject().put("id", h.habit.id).put("title", h.habit.title)
                .put("remindAt", h.habit.remindAt == null ? JSONObject.NULL : h.habit.remindAt)
                .put("scheduled", h.scheduled).put("value", h.value).put("done", h.done)
                .put("pointsToday", h.pointsToday).put("loggedTime", h.loggedTime == null ? JSONObject.NULL : h.loggedTime);
    }

    static JSONObject day(Timeline.Day t) throws Exception {
        JSONObject s = new JSONObject().put("points", t.summary.points).put("habitsDone", t.summary.habitsDone)
                .put("habitsScheduled", t.summary.habitsScheduled).put("entries", t.summary.entries)
                .put("spentMinor", t.summary.spentMinor).put("tasksDone", t.summary.tasksDone).put("missed", t.summary.missed);
        JSONArray goals = new JSONArray(), anytime = new JSONArray(), items = new JSONArray();
        for (Timeline.DayHabit h : t.goals) goals.put(habit(h));
        for (Timeline.DayHabit h : t.anytime) anytime.put(habit(h));
        for (Timeline.Item it : t.items) {
            int ref = it.habit != null ? it.habit.habit.id : it.entry != null ? it.entry.id : it.expense != null ? it.expense.id : it.task.id;
            items.put(new JSONObject().put("type", it.type).put("time", it.time == null ? JSONObject.NULL : it.time)
                    .put("part", it.part == null ? JSONObject.NULL : it.part).put("pending", it.pending)
                    .put("missed", it.missed).put("ref", ref));
        }
        return new JSONObject().put("day", t.day).put("summary", s).put("goals", goals).put("anytime", anytime).put("items", items);
    }

    static JSONObject month(Timeline.Month m) throws Exception {
        JSONObject days = new JSONObject();
        for (Map.Entry<String, Timeline.Cell> e : m.days.entrySet()) {
            Timeline.Cell c = e.getValue();
            days.put(e.getKey(), new JSONObject().put("entries", c.entries).put("mood", c.mood == null ? JSONObject.NULL : c.mood)
                    .put("habitsDone", c.habitsDone).put("habitsScheduled", c.habitsScheduled));
        }
        return new JSONObject().put("month", m.month).put("days", days);
    }

    static JSONObject stats(HabitProgress.StatsView v) throws Exception {
        HabitStats s = v.stats;
        JSONArray badges = new JSONArray();
        for (Badges.Status b : v.badges) {
            badges.put(new JSONObject().put("id", b.badge.id).put("unlockedOn", b.unlockedOn == null ? JSONObject.NULL : b.unlockedOn));
        }
        return new JSONObject().put("totalPoints", s.totalPoints).put("todayPoints", s.todayPoints)
                .put("totalEntries", s.totalEntries).put("totalWords", s.totalWords).put("habitDoneCount", s.habitDoneCount)
                .put("perfectDays", s.perfectDays).put("streak", s.streak).put("longestStreak", s.longestStreak)
                .put("nightOwl", s.nightOwl).put("earlyBird", s.earlyBird).put("badges", badges);
    }

    public static void main(String[] a) throws Exception {
        JSONArray scenarios = new JSONArray(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        JSONArray out = new JSONArray();
        for (int i = 0; i < scenarios.length(); i++) {
            JSONObject sc = scenarios.getJSONObject(i);
            DayHubData data = DayHubData.inMemory();
            data.importAll(sc.getJSONObject("doc"));
            Timeline tl = new Timeline(data);
            JSONArray queries = sc.getJSONArray("queries");
            JSONArray results = new JSONArray();
            for (int k = 0; k < queries.length(); k++) {
                JSONObject q = queries.getJSONObject(k);
                switch (q.getString("kind")) {
                    case "day":
                        results.put(day(tl.day(q.getString("day"), q.getString("today"), q.isNull("now") ? null : q.getString("now"))));
                        break;
                    case "month":
                        results.put(month(tl.month(q.getString("month"))));
                        break;
                    default:
                        results.put(stats(new HabitProgress(data).statsView(q.getString("today"))));
                }
            }
            out.put(results);
        }
        Files.writeString(Path.of(a[1]), out.toString(), java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("Java computed " + out.length() + " scenarios");
    }
}
