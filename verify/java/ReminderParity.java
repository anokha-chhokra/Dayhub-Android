import app.dayhub.data.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

/**
 * Compares which reminders are due with the web app's own reminder check (run on its real backend), and checks the
 * scheduling rules built on top of it (what to announce now, and when the next alarm is due) against that rule.
 */
public class ReminderParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    static String hhmm(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    static List<String> titles(List<ReminderPlan.Reminder> rs) {
        List<String> out = new ArrayList<>();
        for (ReminderPlan.Reminder r : rs) out.add(r.title + "|" + r.body);
        return out;
    }

    public static void main(String[] a) throws Exception {
        JSONArray scenarios = new JSONArray(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        int queriesDone = 0, scanned = 0, skippedJournal = 0, lateSkipped = 0;
        for (int n = 0; n < scenarios.length(); n++) {
            JSONObject sc = scenarios.getJSONObject(n);
            DayHubData data = DayHubData.inMemory();
            data.importAll(sc.getJSONObject("doc"));
            JSONArray queries = sc.getJSONArray("queries");
            Set<String> days = new LinkedHashSet<>();
            for (int q = 0; q < queries.length(); q++) {
                JSONObject c = queries.getJSONObject(q);
                String day = c.getString("day"), now = c.getString("now");
                days.add(day);
                List<String> want = new ArrayList<>();
                JSONArray shown = c.getJSONArray("shown");
                for (int i = 0; i < shown.length(); i++) want.add(shown.getJSONArray(i).getString(0) + "|" + shown.getJSONArray(i).getString(1));
                eq(titles(ReminderPlan.due(data, day, now)), want, "scenario " + n + " " + day + " " + now + " due");
                queriesDone++;
            }

            // The rules built on the due set: scan every minute of each day.
            for (String day : days) {
                boolean written = ReminderPlan.writtenOn(data, day);
                Set<String> previous = new HashSet<>();
                for (int m = 0; m < 1440; m++) {
                    String now = hhmm(m);
                    List<ReminderPlan.Reminder> due = ReminderPlan.due(data, day, now);
                    Set<String> keys = new HashSet<>();
                    for (ReminderPlan.Reminder r : due) keys.add(r.key);

                    // dueNow(no limit) = due, minus the journal reminder once something is written
                    List<String> expectNow = new ArrayList<>();
                    for (ReminderPlan.Reminder r : due) if (!(r.isJournal() && written)) expectNow.add(r.key);
                    List<String> gotNow = new ArrayList<>();
                    for (ReminderPlan.Reminder r : ReminderPlan.dueNow(data, day, now, -1)) gotNow.add(r.key);
                    eq(gotNow, expectNow, "dueNow without a limit " + day + " " + now);
                    if (written && keys.contains(ReminderPlan.JOURNAL)) skippedJournal++;

                    // with a limit, nothing more than that many minutes late is announced
                    for (ReminderPlan.Reminder r : ReminderPlan.dueNow(data, day, now, 90)) {
                        Integer late = Insights.minutesBetween(r.time, now);
                        eq(late != null && late >= 0 && late <= 90, true, "within the limit " + r.key + " " + r.time + " at " + now);
                    }
                    if (ReminderPlan.dueNow(data, day, now, 90).size() < expectNow.size()) lateSkipped++;

                    // next(): the first minute after now at which another reminder becomes due
                    String next = ReminderPlan.next(data, day, now);
                    String expectNext = null;
                    Set<String> nowDue = new HashSet<>(expectNow);
                    for (int m2 = m + 1; m2 < 1440 && expectNext == null; m2++) {
                        for (ReminderPlan.Reminder r : ReminderPlan.dueNow(data, day, hhmm(m2), -1)) {
                            if (!nowDue.contains(r.key)) {
                                expectNext = hhmm(m2);
                                break;
                            }
                        }
                    }
                    eq(next, expectNext, "next after " + now + " on " + day);
                    scanned++;
                }
            }
        }
        System.out.println("IDENTICAL to the web app: " + queriesDone + " reminder checks; rules held over " + scanned + " minute scans ("
                + skippedJournal + " with the journal reminder skipped, " + lateSkipped + " with late ones held back) (" + checks + " comparisons)");
    }
}
