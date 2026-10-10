import org.json.*;
import java.nio.file.*;
import java.util.*;

/**
 * Compares the new app.dayhub.data.FocusSession with the web app's own Android FocusSession
 * (app.dayhub.core.FocusSession, compiled from the web project) and with the web page's clock().
 */
public class FocusParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  new: " + got + "\n  web: " + want);
            System.exit(1);
        }
    }

    /** The one deliberate difference: the web one can leave half of an emoji at the cut. */
    static String webCleaned(String web) {
        if (!web.isEmpty() && Character.isHighSurrogate(web.charAt(web.length() - 1))) return web.substring(0, web.length() - 1).trim();
        return web;
    }

    public static void main(String[] a) throws Exception {
        Random r = new Random(7);
        String[] pieces = {"a", "Z", " ", "  ", "\n", "\t", "\r", "\u007f", "\u0001", "é", "日本", "😀", " ", "-", "x"};

        // labels
        List<String> labels = new ArrayList<>(Arrays.asList(null, "", " ", "  Write\nthe report  ", "a\tb\u007fc", "tab\tin\tside",
                new String(new char[200]).replace('\0', 'a'), new String(new char[59]).replace('\0', 'b') + "😀",
                new String(new char[60]).replace('\0', 'c') + "tail", " lead", "trail ", "\n\n", " nbsp "));
        for (int i = 0; i < 6000; i++) {
            StringBuilder b = new StringBuilder();
            int n = r.nextInt(90);
            for (int k = 0; k < n; k++) b.append(pieces[r.nextInt(pieces.length)]);
            labels.add(b.toString());
        }
        for (String l : labels) {
            eq(app.dayhub.data.FocusSession.cleanLabel(l), webCleaned(app.dayhub.core.FocusSession.cleanLabel(l)), "cleanLabel(" + l + ")");
        }

        // minutes and sessions
        int[] mins = {Integer.MIN_VALUE, -100000, -5, -1, 0, 1, 2, 24, 25, 45, 120, 479, 480, 481, 1000, 100000, Integer.MAX_VALUE};
        long[] nows = {0L, 1L, 1_000_000L, 1_760_000_000_000L, Long.MAX_VALUE / 4};
        for (int m : mins) {
            eq(app.dayhub.data.FocusSession.clampMinutes(m), app.dayhub.core.FocusSession.clampMinutes(m), "clampMinutes " + m);
            for (long now : nows) {
                for (String l : new String[] {"", "Write the report", "  x  "}) {
                    app.dayhub.data.FocusSession n = app.dayhub.data.FocusSession.start(now, m, l);
                    app.dayhub.core.FocusSession w = app.dayhub.core.FocusSession.start(now, m, l);
                    String id = "start(" + now + "," + m + ",'" + l + "')";
                    eq(n.active, w.active, id + " active");
                    eq(n.startedAt, w.startedAt, id + " startedAt");
                    eq(n.endsAt, w.endsAt, id + " endsAt");
                    eq(n.minutes, w.minutes, id + " minutes");
                    eq(n.label, w.label, id + " label");
                    long[] probes = {now - 1, now, now + 1, w.endsAt - 1, w.endsAt, w.endsAt + 1, w.endsAt + 5, w.endsAt + 100000};
                    for (long p : probes) {
                        eq(n.isActive(p), w.isActive(p), id + " isActive " + p);
                        eq(n.remainingMs(p), w.remainingMs(p), id + " remainingMs " + p);
                        eq(n.hasExpired(p), w.hasExpired(p), id + " hasExpired " + p);
                    }
                }
            }
        }
        // stored sessions that are not freshly started (what the disk can hold)
        long[][] stored = {{0, 0}, {5, 5}, {100, 50}, {100, 200}, {-5, -1}};
        for (long[] s : stored) {
            for (boolean active : new boolean[] {true, false}) {
                app.dayhub.data.FocusSession n = new app.dayhub.data.FocusSession(active, s[0], s[1], 3, null);
                app.dayhub.core.FocusSession w = new app.dayhub.core.FocusSession(active, s[0], s[1], 3, null);
                for (long p = -2; p < 260; p += 7) {
                    eq(n.isActive(p), w.isActive(p), "stored isActive " + active + Arrays.toString(s) + " " + p);
                    eq(n.hasExpired(p), w.hasExpired(p), "stored hasExpired " + active + Arrays.toString(s) + " " + p);
                    eq(n.remainingMs(p), w.remainingMs(p), "stored remaining " + active + Arrays.toString(s) + " " + p);
                }
                eq(n.label, w.label, "null label");
            }
        }
        eq(app.dayhub.data.FocusSession.idle().active, app.dayhub.core.FocusSession.idle().active, "idle");

        // clock(): against the web page's own function
        JSONArray cases = new JSONArray(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            long ms = c.getLong("ms");
            eq(app.dayhub.data.FocusSession.clock(ms), c.getString("text"), "clock(" + ms + ") vs the page");
            eq(app.dayhub.data.FocusSession.clock(ms), app.dayhub.core.FocusSession.clock(ms), "clock(" + ms + ") vs web Android");
        }

        eq(app.dayhub.data.FocusSession.MIN_MINUTES, app.dayhub.core.FocusSession.MIN_MINUTES, "MIN");
        eq(app.dayhub.data.FocusSession.MAX_MINUTES, app.dayhub.core.FocusSession.MAX_MINUTES, "MAX");
        eq(app.dayhub.data.FocusSession.MAX_LABEL, app.dayhub.core.FocusSession.MAX_LABEL, "MAX_LABEL");
        eq(app.dayhub.data.FocusSession.MAX_MINUTES, app.dayhub.data.StopwatchMath.MAX_MINUTES, "dial ceiling matches");
        System.out.println("IDENTICAL to the web app: " + labels.size() + " labels, " + mins.length + " lengths, " + cases.length()
                + " clock readings (" + checks + " comparisons)");
    }
}
