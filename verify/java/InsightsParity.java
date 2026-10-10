import app.dayhub.data.*;
import app.dayhub.data.Model.Task;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class InsightsParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + ": java=" + got + " web=" + want);
            System.exit(1);
        }
    }

    static String s(JSONObject o, String k) throws Exception {
        return o.isNull(k) ? null : o.getString(k);
    }

    static Long l(JSONObject o, String k) throws Exception {
        return o.isNull(k) ? null : Long.valueOf(o.getLong(k));
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));

        JSONArray paces = root.getJSONArray("paces");
        for (int i = 0; i < paces.length(); i++) {
            JSONObject p = paces.getJSONObject(i);
            Insights.Pace g = Insights.spendPace(p.getString("ym"), p.getString("today"), p.getLong("totalMinor"), p.getLong("budgetMinor"));
            JSONObject w = p.getJSONObject("expected");
            String id = "pace " + i + " " + p.getString("ym") + " " + p.getString("today") + " total " + p.getLong("totalMinor") + " budget " + p.getLong("budgetMinor");
            eq(g.daysLeft, w.getInt("daysLeft"), id + " daysLeft");
            eq(g.status, w.getString("status"), id + " status");
            eq(g.projectedMinor, l(w, "projectedMinor"), id + " projected");
            eq(g.perDayLeftMinor, l(w, "perDayLeftMinor"), id + " perDay");
        }

        JSONArray atts = root.getJSONArray("attentions");
        for (int i = 0; i < atts.length(); i++) {
            JSONObject c = atts.getJSONObject(i);
            List<Task> tasks = new ArrayList<>();
            JSONArray ts = c.getJSONArray("tasks");
            for (int k = 0; k < ts.length(); k++) {
                JSONObject t = ts.getJSONObject(k);
                Task x = new Task();
                x.id = t.getInt("id");
                x.title = t.getString("title");
                x.dueOn = s(t, "dueOn");
                x.priority = t.getInt("priority");
                tasks.add(x);
            }
            List<Insights.HabitNudge> habits = new ArrayList<>();
            JSONArray hs = c.getJSONArray("habits");
            for (int k = 0; k < hs.length(); k++) {
                JSONObject h = hs.getJSONObject(k);
                Insights.HabitNudge n = new Insights.HabitNudge();
                n.id = h.getInt("id");
                n.title = h.getString("title");
                n.icon = h.getString("icon");
                n.kind = h.getString("kind");
                n.done = h.getBoolean("done");
                n.scheduled = h.getBoolean("scheduled");
                n.remindAt = s(h, "remindAt");
                habits.add(n);
            }
            String status = null;
            long over = 0;
            if (!c.isNull("spend")) {
                status = c.getJSONObject("spend").getString("status");
                over = c.getJSONObject("spend").getLong("overByMinor");
            }
            List<Insights.Item> got = c.isNull("limit")
                    ? Insights.buildAttention(c.getString("today"), s(c, "now"), tasks, habits, c.getInt("entriesToday"), c.getString("journalReminder"), status, over)
                    : Insights.buildAttention(c.getString("today"), s(c, "now"), tasks, habits, c.getInt("entriesToday"), c.getString("journalReminder"), status, over, c.getInt("limit"));
            JSONArray want = c.getJSONArray("expected");
            eq(got.size(), want.length(), "attention " + i + " size");
            for (int k = 0; k < got.size(); k++) {
                Insights.Item it = got.get(k);
                JSONArray w = want.getJSONArray(k);
                List<Object> mine = Arrays.asList(it.type, it.id, it.title, it.dueOn, it.overdue, it.priority, it.icon, it.remindAt, it.overByMinor);
                for (int f = 0; f < 9; f++) {
                    Object wv = w.isNull(f) ? null : w.get(f);
                    Object gv = mine.get(f);
                    if (wv instanceof Number && gv instanceof Number) {
                        wv = ((Number) wv).longValue();
                        gv = ((Number) gv).longValue();
                    }
                    eq(gv, wv, "attention " + i + " item " + k + " field " + f);
                }
            }
        }

        JSONArray mins = root.getJSONArray("minutes");
        for (int i = 0; i < mins.length(); i++) {
            JSONObject m = mins.getJSONObject(i);
            Integer g = Insights.minutesBetween(s(m, "a"), s(m, "b"));
            eq(g, m.isNull("expected") ? null : Integer.valueOf(m.getInt("expected")), "minutes " + i);
        }
        System.out.println("IDENTICAL to the web app: " + paces.length() + " spend paces, " + atts.length()
                + " attention lists, " + mins.length() + " time pairs (" + checks + " comparisons)");
    }
}
