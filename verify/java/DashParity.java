import app.dayhub.data.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class DashParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + ": java=" + got + " web=" + want);
            System.exit(1);
        }
    }

    static Object num(Object o) {
        return o instanceof Number ? (Object) ((Number) o).longValue() : o;
    }

    static void compare(DayHubData data, String today, String now, JSONObject spend, JSONArray attention, String label) throws Exception {
        DashboardInsights d = DashboardInsights.compute(data, today, now);
        eq(d.spend.month, spend.getString("month"), label + " month");
        eq(d.spend.currency, spend.getString("currency"), label + " currency");
        eq(d.spend.totalMinor, spend.getLong("totalMinor"), label + " total");
        eq(d.spend.budgetMinor, spend.getLong("budgetMinor"), label + " budget");
        eq(d.spend.todayMinor, spend.getLong("todayMinor"), label + " today");
        JSONObject p = spend.getJSONObject("pace");
        eq(d.spend.pace.status, p.getString("status"), label + " status");
        eq(d.spend.pace.daysLeft, p.getInt("daysLeft"), label + " daysLeft");
        eq(d.spend.pace.projectedMinor, p.isNull("projectedMinor") ? null : Long.valueOf(p.getLong("projectedMinor")), label + " projected");
        eq(d.spend.pace.perDayLeftMinor, p.isNull("perDayLeftMinor") ? null : Long.valueOf(p.getLong("perDayLeftMinor")), label + " perDay");
        eq(d.attention.size(), attention.length(), label + " attention size");
        for (int i = 0; i < attention.length(); i++) {
            JSONObject w = attention.getJSONObject(i);
            Insights.Item g = d.attention.get(i);
            String id = label + " item " + i;
            eq(g.type, w.getString("type"), id + " type");
            eq(g.title, w.optString("title", null), id + " title");
            eq(g.id, w.has("id") ? Integer.valueOf(w.getInt("id")) : null, id + " id");
            eq(g.dueOn, w.has("dueOn") ? w.getString("dueOn") : null, id + " dueOn");
            eq(g.overdue, w.has("overdue") ? Boolean.valueOf(w.getBoolean("overdue")) : null, id + " overdue");
            eq(g.priority, w.has("priority") ? Integer.valueOf(w.getInt("priority")) : null, id + " priority");
            eq(g.icon, w.has("icon") ? w.getString("icon") : null, id + " icon");
            eq(g.remindAt, w.has("remindAt") ? w.getString("remindAt") : null, id + " remindAt");
            eq(g.overByMinor, w.has("overByMinor") ? Long.valueOf(w.getLong("overByMinor")) : null, id + " overBy");
        }
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        DayHubData data = DayHubData.inMemory();
        data.importAll(root.getJSONObject("backup"));
        JSONArray cases = root.getJSONArray("cases");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            compare(data, c.getString("today"), c.isNull("now") ? null : c.getString("now"),
                    c.getJSONObject("spend"), c.getJSONArray("attention"), "dashboard " + c.getString("today") + " " + c.opt("now"));
        }
        DayHubData over = DayHubData.inMemory();
        over.importAll(root.getJSONObject("overBackup"));
        JSONObject o = root.getJSONObject("over");
        compare(over, o.getString("today"), o.getString("now"), o.getJSONObject("spend"), o.getJSONArray("attention"), "over budget");
        System.out.println("Java dashboard matches the web app's dashboard route: " + (cases.length() + 1) + " responses (" + checks + " comparisons)");
    }
}
