import app.dayhub.data.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class ExportParity {
    static int checks = 0;

    static String firstDiff(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        int from = Math.max(0, i - 40);
        return "at " + i + "\n  java: " + JSONObject.quote(a.substring(from, Math.min(a.length(), i + 60)))
                + "\n  web:  " + JSONObject.quote(b.substring(from, Math.min(b.length(), i + 60)));
    }

    static void same(String got, String want, String what) {
        checks++;
        if (!got.equals(want)) {
            System.out.println("MISMATCH " + what + " " + firstDiff(got, want));
            System.exit(1);
        }
    }

    static String withoutTime(String json) {
        return json.replaceAll("\"exportedAt\": \"[^\"]*\"", "\"exportedAt\": \"X\"");
    }

    public static void main(String[] a) throws Exception {
        JSONArray cases = new JSONArray(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            DayHubData data = DayHubData.inMemory();
            data.importAll(c.getJSONObject("doc"));
            String id = "case " + i;
            DataExport.Export backup = DataExport.backup(data);
            same(withoutTime(backup.text), withoutTime(c.getString("backupText")), id + " backup json");
            same(DataExport.expensesCsv(data, null).text, c.getString("csvAll"), id + " csv all");
            same(DataExport.expensesCsv(data, "2026-10").text, c.getString("csvOct"), id + " csv october");
            same(DataExport.expensesCsv(data, "2026-11").text, c.getString("csvNov"), id + " csv november");
            same(DataExport.journalMarkdown(data, c.getString("today")).text, c.getString("markdown"), id + " markdown");
            // The backup file must restore into a fresh app unchanged.
            DayHubData again = DayHubData.inMemory();
            again.importAll(backup.text);
            same(withoutTime(DataExport.backup(again).text), withoutTime(backup.text), id + " backup restores to the same data");
        }
        DataExport.Export csv = DataExport.expensesCsv(DayHubData.inMemory(), null);
        same(csv.name + "|" + csv.mimeType + "|" + csv.text, "expenses-all.csv|text/csv|Date,Amount,Category,Note\n", "empty csv");
        same(DataExport.expensesCsv(DayHubData.inMemory(), "2026-10").name, "expenses-2026-10.csv", "month file name");
        same(DataExport.backup(DayHubData.inMemory()).name, "day-hub-backup.json", "backup file name");
        same(DataExport.journalMarkdown(DayHubData.inMemory(), "2026-10-05").name, "day-hub-journal.md", "markdown file name");
        try {
            DataExport.expensesCsv(DayHubData.inMemory(), "oct");
            System.out.println("bad month accepted");
            System.exit(1);
        } catch (DataError e) {
            same(e.getMessage(), "month must look like 2026-10", "bad month message");
        }
        System.out.println("BYTE-IDENTICAL to the web app: " + cases.length() + " data sets x 5 files (" + checks + " comparisons)");
    }
}
