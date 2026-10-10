import app.dayhub.data.*;
import app.dayhub.data.Model.Expense;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class DetectParity {
    static List<Expense> expenses(JSONArray a) throws Exception {
        List<Expense> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Expense e = new Expense();
            e.note = o.isNull("note") ? null : o.getString("note");
            e.category = o.isNull("category") ? null : o.getString("category");
            out.add(e);
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        int checks = 0, bad = 0;
        JSONArray hintCases = root.getJSONArray("hintCases");
        for (int i = 0; i < hintCases.length(); i++) {
            JSONObject hc = hintCases.getJSONObject(i);
            Map<String, ExpenseDetector.Hint> got = ExpenseDetector.buildHints(expenses(hc.getJSONArray("expenses")));
            JSONObject want = hc.getJSONObject("hints");
            if (got.size() != want.length()) { System.out.println("HINT COUNT " + got.size() + " vs " + want.length()); System.exit(1); }
            Iterator<String> keys = want.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject w = want.getJSONObject(k);
                ExpenseDetector.Hint g = got.get(k);
                boolean ok = g != null && g.category.equals(w.getString("category")) && g.n == w.getInt("n") && Math.abs(g.share - w.getDouble("share")) < 1e-12;
                checks++;
                if (!ok) { System.out.println("HINT MISMATCH " + k); System.exit(1); }
            }
        }
        JSONArray cases = root.getJSONArray("cases");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String text = c.getString("text");
            String refDay = c.isNull("refDay") ? null : c.getString("refDay");
            Map<String, ExpenseDetector.Hint> hints = c.isNull("expenses") ? null : ExpenseDetector.buildHints(expenses(c.getJSONArray("expenses")));
            List<ExpenseDetector.Suggestion> got = ExpenseDetector.detect(text, refDay, hints);
            JSONArray want = c.getJSONArray("expected");
            StringBuilder g = new StringBuilder(), w = new StringBuilder();
            for (ExpenseDetector.Suggestion s : got) g.append(s.amountMinor).append('|').append(s.category).append('|').append(s.note).append('|').append(s.daysAgo).append('\n');
            for (int k = 0; k < want.length(); k++) {
                JSONObject s = want.getJSONObject(k);
                w.append(s.getLong("amountMinor")).append('|').append(s.getString("category")).append('|').append(s.getString("note")).append('|').append(s.getInt("daysAgo")).append('\n');
            }
            checks++;
            if (!g.toString().equals(w.toString())) {
                if (bad++ < 8) System.out.println("MISMATCH #" + i + " text=" + JSONObject.quote(text) + " refDay=" + refDay + "\n  java:\n" + g.toString().replace("\n", "\n    ") + "\n  web:\n" + w.toString().replace("\n", "\n    "));
            }
        }
        if (bad > 0) { System.out.println(bad + " of " + cases.length() + " cases differ"); System.exit(1); }
        System.out.println("IDENTICAL to the web app: " + cases.length() + " texts, " + hintCases.length() + " histories (" + checks + " comparisons)");
    }
}
