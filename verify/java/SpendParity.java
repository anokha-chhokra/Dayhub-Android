import app.dayhub.data.*;
import app.dayhub.data.Model.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class SpendParity {
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

    static String key(Expense e) {
        return e.id + "|" + e.amountMinor + "|" + e.category + "|" + e.note + "|" + e.spentOn + "|" + e.entryId + "|" + e.time;
    }

    static String key(JSONObject o) throws Exception {
        return o.getInt("id") + "|" + o.getLong("amountMinor") + "|" + o.getString("category") + "|" + s(o, "note") + "|" + o.getString("spentOn") + "|"
                + (o.isNull("entryId") ? null : Integer.valueOf(o.getInt("entryId"))) + "|" + s(o, "time");
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        JSONArray ops = root.getJSONArray("ops");
        DayHubData d = DayHubData.inMemory();
        int refused = 0;
        for (int i = 0; i < ops.length(); i++) {
            JSONObject op = ops.getJSONObject(i);
            String id = "op " + i + " " + op.getString("kind") + " " + op.opt("body") + " id=" + op.opt("id");
            String error = null;
            int status = 0;
            try {
                if (op.getString("kind").equals("add")) d.addExpense(new JSONObject(op.getJSONObject("body").toString()));
                else d.deleteExpense(op.getInt("id"));
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
            List<String> mine = new ArrayList<>(), theirs = new ArrayList<>();
            for (Expense e : d.listExpenses(null, null)) mine.add(key(e));
            for (int k = 0; k < after.getJSONArray("all").length(); k++) theirs.add(key(after.getJSONArray("all").getJSONObject(k)));
            eq(mine, theirs, id + " all expenses");

            JSONObject months = after.getJSONObject("months");
            Iterator<?> it = months.keys();
            while (it.hasNext()) {
                String ym = (String) it.next();
                JSONObject w = months.getJSONObject(ym);
                String[] range = Validate.monthRange(ym);
                DayHubData.Summary sum = d.summarizeExpenses(range[0], range[1]);
                eq(sum.totalMinor, w.getLong("total"), id + " " + ym + " total");
                eq(sum.count, w.getInt("count"), id + " " + ym + " count");
                List<String> cats = new ArrayList<>(), wantCats = new ArrayList<>();
                for (DayHubData.CategoryTotal c : sum.byCategory) cats.add(c.category + "|" + c.totalMinor + "|" + c.count);
                for (int k = 0; k < w.getJSONArray("byCategory").length(); k++) {
                    JSONArray c = w.getJSONArray("byCategory").getJSONArray(k);
                    wantCats.add(c.getString(0) + "|" + c.getLong(1) + "|" + c.getInt(2));
                }
                eq(cats, wantCats, id + " " + ym + " categories");
                List<Integer> ids = new ArrayList<>(), wantIds = new ArrayList<>();
                for (Expense e : d.listExpenses(range[0], range[1])) ids.add(e.id);
                for (int k = 0; k < w.getJSONArray("ids").length(); k++) wantIds.add(w.getJSONArray("ids").getInt(k));
                eq(ids, wantIds, id + " " + ym + " list");
            }
            eq(DataExport.expensesCsv(d, null).text, after.getString("csvAll"), id + " csv all");
            eq(DataExport.expensesCsv(d, "2026-10").text, after.getString("csvOct"), id + " csv october");
        }
        System.out.println("IDENTICAL to the web app: " + ops.length() + " expense operations (" + (ops.length() - refused) + " accepted, "
                + refused + " refused), " + checks + " comparisons");
    }
}
