import app.dayhub.data.HoldToEnd;
import org.json.*;
import java.nio.file.*;

/** The hold-to-end rule, on a normal JVM, and its eight seconds against the web app's sources. */
public class HoldTest {
    static int checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            System.out.println("FAILED: " + what);
            System.exit(1);
        }
    }

    public static void main(String[] a) throws Exception {
        JSONObject web = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        check(HoldToEnd.HOLD_MS == web.getLong("page"), "same time as the page: " + HoldToEnd.HOLD_MS + " vs " + web.getLong("page"));
        check(HoldToEnd.HOLD_MS == web.getLong("android"), "same time as the web app's Android guard");
        check(HoldToEnd.HOLD_MS / 1000 == web.getLong("coverSeconds"), "same seconds as the cover's text");

        HoldToEnd h = new HoldToEnd();
        long t0 = 10_000;
        check(!h.holding() && h.progress(t0) == 0f && !h.complete(t0 + 99_999), "idle: nothing to finish");
        check(h.remainingMs(t0) == HoldToEnd.HOLD_MS, "idle remaining is the whole hold");

        h.press(t0);
        check(h.holding(), "holding after press");
        check(h.progress(t0) == 0f, "no progress at once");
        check(Math.abs(h.progress(t0 + 4000) - 0.5f) < 1e-6, "half way at 4 seconds");
        check(!h.complete(t0 + 7999), "not complete a millisecond early");
        check(h.complete(t0 + 8000), "complete at exactly 8 seconds");
        check(h.complete(t0 + 80_000), "and after");
        check(h.progress(t0 + 80_000) == 1f, "progress stops at 1");
        check(h.progress(t0 - 5) == 0f, "a clock that went backwards is 0, not negative");
        check(h.remainingMs(t0 + 3000) == 5000 && h.remainingMs(t0 + 9000) == 0, "remaining counts down to 0");

        // a second press while already holding does not restart it
        h.press(t0 + 5000);
        check(h.complete(t0 + 8000), "a repeated press does not reset the hold");

        // lifting early starts over
        h.release();
        check(!h.holding() && !h.complete(t0 + 8000) && h.progress(t0 + 8000) == 0f, "release empties it");
        h.press(t0 + 7000);
        check(!h.complete(t0 + 14_999) && h.complete(t0 + 15_000), "holding again counts from the new press");

        // 7.9 seconds then letting go never ends it
        HoldToEnd g = new HoldToEnd();
        g.press(0);
        check(!g.complete(7900), "7.9 seconds is not enough");
        g.release();
        g.press(7900);
        check(!g.complete(8000) && !g.complete(15_899) && g.complete(15_900), "...and the next try starts from zero");

        System.out.println("HOLD TO END OK: " + HoldToEnd.HOLD_MS + " ms, same as the web app's page and Android guard (" + checks + " checks)");
    }
}
