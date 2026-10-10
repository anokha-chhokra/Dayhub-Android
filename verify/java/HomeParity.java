import app.dayhub.data.*;
import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Task;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class HomeParity {
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

    static Integer i(JSONObject o, String k) throws Exception {
        return o.isNull(k) ? null : Integer.valueOf(o.getInt(k));
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));

        JSONArray promptList = root.getJSONArray("promptList");
        eq(Prompts.ALL.size(), promptList.length(), "prompt count");
        for (int k = 0; k < promptList.length(); k++) {
            JSONObject p = promptList.getJSONObject(k);
            eq(Prompts.ALL.get(k).id, p.getString("id"), "prompt id " + k);
            eq(Prompts.ALL.get(k).title, p.getString("title"), "prompt title " + k);
            eq(Prompts.ALL.get(k).text, p.getString("text"), "prompt text " + k);
        }
        JSONArray tags = root.getJSONArray("tags");
        eq(Prompts.TAG_SUGGESTIONS.size(), tags.length(), "tag count");
        for (int k = 0; k < tags.length(); k++) eq(Prompts.TAG_SUGGESTIONS.get(k), tags.getString(k), "tag " + k);
        JSONArray days = root.getJSONArray("prompts");
        for (int k = 0; k < days.length(); k++) {
            JSONObject d = days.getJSONObject(k);
            eq(Prompts.forDay(d.getString("day")).id, d.getString("id"), "prompt for " + d.getString("day"));
        }

        JSONArray scenarios = root.getJSONArray("scenarios");
        int dashboards = 0;
        for (int n = 0; n < scenarios.length(); n++) {
            JSONObject sc = scenarios.getJSONObject(n);
            DayHubData data = DayHubData.inMemory();
            data.importAll(sc.getJSONObject("doc"));
            JSONArray queries = sc.getJSONArray("queries");
            for (int q = 0; q < queries.length(); q++) {
                JSONObject c = queries.getJSONObject(q);
                JSONObject w = c.getJSONObject("expected");
                HomeData h = HomeData.compute(data, c.getString("today"), s(c, "now"));
                String id = "scenario " + n + " query " + q + " " + c.getString("today") + " " + c.opt("now");
                dashboards++;
                eq(h.today, w.getString("today"), id + " today");
                eq(h.name, w.getString("name"), id + " name");

                JSONArray att = w.getJSONArray("attention");
                eq(h.attention.size(), att.length(), id + " attention size");
                for (int k = 0; k < att.length(); k++) {
                    JSONArray row = att.getJSONArray(k);
                    Insights.Item it = h.attention.get(k);
                    List<Object> mine = Arrays.asList(it.type, it.id, it.title, it.dueOn, it.overdue, it.priority, it.icon, it.remindAt, it.overByMinor);
                    for (int f = 0; f < 9; f++) {
                        Object wv = row.isNull(f) ? null : row.get(f);
                        Object gv = mine.get(f);
                        if (wv instanceof Number && gv instanceof Number) { wv = ((Number) wv).longValue(); gv = ((Number) gv).longValue(); }
                        eq(gv, wv, id + " attention " + k + " field " + f);
                    }
                }

                JSONObject t = w.getJSONObject("tasks");
                eq(h.tasks.total, t.getInt("total"), id + " tasks total");
                eq(h.tasks.doneToday, t.getInt("doneToday"), id + " doneToday");
                JSONArray ids = t.getJSONArray("ids"), done = t.getJSONArray("done");
                eq(h.tasks.list.size(), ids.length(), id + " task count");
                for (int k = 0; k < ids.length(); k++) {
                    Task task = h.tasks.list.get(k);
                    eq(task.id, ids.getInt(k), id + " task " + k + " id");
                    eq(task.done, done.getBoolean(k), id + " task " + k + " done");
                }

                JSONObject sp = w.getJSONObject("spend");
                eq(h.spend.month, sp.getString("month"), id + " month");
                eq(h.spend.currency, sp.getString("currency"), id + " currency");
                eq(h.spend.totalMinor, sp.getLong("totalMinor"), id + " total");
                eq(h.spend.budgetMinor, sp.getLong("budgetMinor"), id + " budget");
                eq(h.spend.todayMinor, sp.getLong("todayMinor"), id + " todaySpend");
                JSONObject pace = sp.getJSONObject("pace");
                eq(h.spend.pace.status, pace.getString("status"), id + " status");
                eq(h.spend.pace.daysLeft, pace.getInt("daysLeft"), id + " daysLeft");
                eq(h.spend.pace.projectedMinor, pace.isNull("projectedMinor") ? null : Long.valueOf(pace.getLong("projectedMinor")), id + " projected");
                eq(h.spend.pace.perDayLeftMinor, pace.isNull("perDayLeftMinor") ? null : Long.valueOf(pace.getLong("perDayLeftMinor")), id + " perDay");

                JSONObject hb = w.getJSONObject("habits");
                eq(h.habits.doneCount, hb.getInt("doneCount"), id + " habits done");
                eq(h.habits.total, hb.getInt("total"), id + " habits total");
                JSONArray hl = hb.getJSONArray("list");
                eq(h.habits.list.size(), hl.length(), id + " habit count");
                for (int k = 0; k < hl.length(); k++) {
                    JSONObject wh = hl.getJSONObject(k);
                    HabitDay hd = h.habits.list.get(k);
                    eq(hd.habit.id, wh.getInt("id"), id + " habit " + k + " id");
                    eq(hd.scheduled, wh.getBoolean("scheduled"), id + " habit " + k + " scheduled");
                    eq(hd.value, wh.getInt("value"), id + " habit " + k + " value");
                    eq(hd.done, wh.getBoolean("done"), id + " habit " + k + " done");
                    eq(hd.pointsToday, wh.getInt("pointsToday"), id + " habit " + k + " points");
                }

                JSONObject st = w.getJSONObject("stats");
                eq(h.stats.totalPoints, st.getInt("totalPoints"), id + " totalPoints");
                eq(h.stats.todayPoints, st.getInt("todayPoints"), id + " todayPoints");
                eq(h.stats.streak, st.getInt("streak"), id + " streak");
                eq(h.stats.longestStreak, st.getInt("longestStreak"), id + " longestStreak");

                JSONObject jr = w.getJSONObject("journal");
                eq(h.journal.prompt.id, jr.getString("promptId"), id + " prompt");
                eq(h.journal.todayCount, jr.getInt("todayCount"), id + " todayCount");
                eq(h.journal.latestMood, i(jr, "latestMood"), id + " latestMood");
                eq(h.journal.quickMoodId, i(jr, "quickMoodId"), id + " quickMoodId");
                eq(h.music == null ? null : Integer.valueOf(h.music.id), i(w, "musicId"), id + " music");
            }
        }
        System.out.println("IDENTICAL to the web app: " + dashboards + " dashboards, " + days.length() + " prompt days, " + promptList.length() + " prompts (" + checks + " comparisons)");
    }
}
