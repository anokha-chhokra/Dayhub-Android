import app.dayhub.data.*;
import app.dayhub.data.Model.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class JournalParity {
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

    static String key(Entry e) {
        return e.id + "|" + e.day + "|" + e.time + "|" + JSONObject.quote(e.text) + "|" + e.mood + "|" + e.tags + "|" + e.promptId + "|" + e.wordCount;
    }

    static String key(JSONObject o) throws Exception {
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < o.getJSONArray("tags").length(); i++) tags.add(o.getJSONArray("tags").getString(i));
        return o.getInt("id") + "|" + o.getString("day") + "|" + s(o, "time") + "|" + JSONObject.quote(o.getString("text")) + "|"
                + (o.isNull("mood") ? null : Integer.valueOf(o.getInt("mood"))) + "|" + tags + "|" + s(o, "promptId") + "|" + o.getInt("wordCount");
    }

    static String key(Expense e) {
        return e.id + "|" + e.amountMinor + "|" + e.category + "|" + e.note + "|" + e.spentOn + "|" + e.entryId + "|" + e.time;
    }

    static String key(JSONObject o, boolean expense) throws Exception {
        return o.getInt("id") + "|" + o.getLong("amountMinor") + "|" + o.getString("category") + "|" + s(o, "note") + "|" + o.getString("spentOn") + "|"
                + (o.isNull("entryId") ? null : Integer.valueOf(o.getInt("entryId"))) + "|" + s(o, "time");
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        String today = root.getString("today");
        JSONArray ops = root.getJSONArray("ops");
        DayHubData d = DayHubData.inMemory();
        HabitProgress progress = new HabitProgress(d);
        Timeline timeline = new Timeline(d);
        int refused = 0;
        for (int i = 0; i < ops.length(); i++) {
            JSONObject op = ops.getJSONObject(i);
            String kind = op.getString("kind");
            String id = "op " + i + " " + kind + " " + op.opt("body") + " id=" + op.opt("id");
            String error = null;
            int status = 0;
            HabitProgress.EntryResult result = null;
            int expensesBefore = d.listExpenses(null, null).size();
            try {
                switch (kind) {
                    case "create": result = progress.createEntry(new JSONObject(op.getJSONObject("body").toString())); break;
                    case "patch": result = progress.updateEntry(op.getInt("id"), new JSONObject(op.getJSONObject("body").toString())); break;
                    default: d.deleteEntry(op.getInt("id"));
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
            if (result != null) {
                List<String> badges = new ArrayList<>();
                for (Badges.Badge b : result.progress.newBadges) badges.add(b.id);
                List<String> want = new ArrayList<>();
                for (int k = 0; k < op.getJSONArray("newBadges").length(); k++) want.add(op.getJSONArray("newBadges").getString(k));
                eq(badges, want, id + " new badges");
                eq(d.listExpenses(null, null).size() - expensesBefore, op.getJSONArray("createdExpenses").length(), id + " expenses created");
            }

            JSONObject after = op.getJSONObject("after");
            List<String> mine = new ArrayList<>(), theirs = new ArrayList<>();
            for (Entry e : d.listEntries(null, null)) mine.add(key(e));
            for (int k = 0; k < after.getJSONArray("entries").length(); k++) theirs.add(key(after.getJSONArray("entries").getJSONObject(k)));
            eq(mine, theirs, id + " entries");

            mine = new ArrayList<>();
            theirs = new ArrayList<>();
            for (Expense e : d.listExpenses(null, null)) mine.add(key(e));
            for (int k = 0; k < after.getJSONArray("expenses").length(); k++) theirs.add(key(after.getJSONArray("expenses").getJSONObject(k), true));
            eq(mine, theirs, id + " expenses");

            JSONObject tls = after.getJSONObject("timelines");
            Iterator<?> days = tls.keys();
            while (days.hasNext()) {
                String day = (String) days.next();
                JSONObject w = tls.getJSONObject(day);
                Timeline.Day t = timeline.day(day, today, "14:00");
                JSONObject ws = w.getJSONObject("summary");
                eq(t.summary.points, ws.getInt("points"), id + " " + day + " points");
                eq(t.summary.entries, ws.getInt("entries"), id + " " + day + " entries");
                eq(t.summary.spentMinor, ws.getLong("spentMinor"), id + " " + day + " spent");
                eq(t.summary.habitsDone, ws.getInt("habitsDone"), id + " " + day + " habitsDone");
                mine = new ArrayList<>();
                theirs = new ArrayList<>();
                for (Timeline.Item it : t.items) {
                    int ref = it.entry != null ? it.entry.id : it.expense != null ? it.expense.id : it.task != null ? it.task.id : it.habit.habit.id;
                    mine.add(it.type + "|" + it.time + "|" + it.part + "|" + ref);
                }
                for (int k = 0; k < w.getJSONArray("items").length(); k++) {
                    JSONObject it = w.getJSONArray("items").getJSONObject(k);
                    theirs.add(it.getString("type") + "|" + s(it, "time") + "|" + s(it, "part") + "|" + it.getInt("ref"));
                }
                eq(mine, theirs, id + " " + day + " timeline");
            }

            List<Integer> monthIds = new ArrayList<>(), wantMonth = new ArrayList<>();
            for (Entry e : d.listEntries("2026-10-01", "2026-11-01")) monthIds.add(e.id);
            for (int k = 0; k < after.getJSONArray("month").length(); k++) wantMonth.add(after.getJSONArray("month").getInt(k));
            eq(monthIds, wantMonth, id + " month list");

            HabitProgress.StatsView view = progress.statsView(today);
            JSONObject ws = after.getJSONObject("stats");
            eq(view.stats.totalPoints, ws.getInt("totalPoints"), id + " totalPoints");
            eq(view.stats.totalEntries, ws.getInt("totalEntries"), id + " totalEntries");
            eq(view.stats.totalWords, ws.getInt("totalWords"), id + " totalWords");
            eq(view.stats.streak, ws.getInt("streak"), id + " streak");
            mine = new ArrayList<>();
            theirs = new ArrayList<>();
            for (Badges.Status b : view.badges) mine.add(b.badge.id + "=" + b.unlockedOn);
            for (int k = 0; k < ws.getJSONArray("badges").length(); k++) {
                JSONArray b = ws.getJSONArray("badges").getJSONArray(k);
                theirs.add(b.getString(0) + "=" + (b.isNull(1) ? null : b.getString(1)));
            }
            eq(mine, theirs, id + " badges");
        }
        System.out.println("IDENTICAL to the web app: " + ops.length() + " journal operations (" + (ops.length() - refused) + " accepted, "
                + refused + " refused), " + checks + " comparisons");
    }
}
