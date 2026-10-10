import app.dayhub.data.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Write verification and the blank-data guard (feature 21). */
public class VerifyTest {
    static int checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            System.out.println("FAIL " + what);
            System.exit(1);
        }
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] a) throws Exception {
        byte[] wanted = b("{\"tasks\":[]}\n");
        AtomicInteger sleeps = new AtomicInteger();
        WriteVerifier.Pause pause = ms -> {
            check(ms == 400, "pauses 400 ms before the second look");
            sleeps.incrementAndGet();
        };

        // The file holds exactly what was written: confirmed at once, no waiting.
        check(WriteVerifier.confirms(wanted, limit -> wanted.clone(), pause) && sleeps.get() == 0, "exact match");

        // The reader is asked for one byte more than expected, so a longer file shows up.
        long[] asked = new long[1];
        WriteVerifier.confirms(wanted, limit -> {
            asked[0] = limit;
            return wanted.clone();
        }, pause);
        check(asked[0] == wanted.length + 1, "asks for one extra byte");

        // A cloud provider still showing the old content for a moment: the second look confirms.
        AtomicInteger looks = new AtomicInteger();
        check(WriteVerifier.confirms(wanted, limit -> looks.incrementAndGet() == 1 ? b("old content") : wanted.clone(), pause)
                && sleeps.get() == 1 && looks.get() == 2, "old content on the first look, right on the second");

        // Still wrong after the second look: the backup is not confirmed.
        sleeps.set(0);
        looks.set(0);
        check(!WriteVerifier.confirms(wanted, limit -> {
            looks.incrementAndGet();
            return b("something else");
        }, pause) && looks.get() == 2 && sleeps.get() == 1, "wrong twice is a failure");

        // A tail left from an older, longer backup is a failure.
        byte[] withTail = Arrays.copyOf(wanted, wanted.length + 5);
        check(!WriteVerifier.confirms(wanted, limit -> withTail.clone(), ms -> {}), "leftover tail");

        // A truncated file is a failure.
        check(!WriteVerifier.confirms(wanted, limit -> Arrays.copyOf(wanted, wanted.length - 1), ms -> {}), "cut short");

        // The provider will not let us read it back: the write did not fail, so that is not a failure.
        check(WriteVerifier.confirms(wanted, limit -> { throw new IOException("denied"); }, pause), "cannot read back (IOException)");
        check(WriteVerifier.confirms(wanted, limit -> { throw new SecurityException("denied"); }, pause), "cannot read back (SecurityException)");

        // Interrupted while waiting: not confirmed, and the interrupt is kept.
        check(!WriteVerifier.confirms(wanted, limit -> b("nope"), ms -> { throw new InterruptedException(); }) && Thread.interrupted(), "interrupted");

        // Empty content is verified like any other.
        check(WriteVerifier.confirms(new byte[0], limit -> new byte[0], pause), "empty file");
        check(!WriteVerifier.confirms(new byte[0], limit -> b("x"), ms -> {}), "empty expected, something there");

        // The real thing against a file on disk: write, read back, compare; then a shorter write over a longer one.
        Path file = Files.createTempFile("backup", ".json");
        byte[] longer = b("{\"v\":1,\"tasks\":[{\"id\":1,\"title\":\"a long title that makes the file big\"}]}\n");
        byte[] shorter = b("{\"v\":1}\n");
        Files.write(file, longer);
        Files.write(file, shorter); // truncating write, as the backup does
        check(WriteVerifier.confirms(shorter, limit -> Arrays.copyOf(Files.readAllBytes(file), (int) Math.min(limit, Files.size(file))), pause),
                "a shorter backup over a longer one reads back exactly");
        // What a write that did not truncate would leave behind is caught.
        Files.write(file, shorter);
        byte[] bad = Arrays.copyOf(longer, longer.length);
        System.arraycopy(shorter, 0, bad, 0, shorter.length);
        Files.write(file, bad);
        check(!WriteVerifier.confirms(shorter, limit -> Arrays.copyOf(Files.readAllBytes(file), (int) Math.min(limit, Files.size(file))), ms -> {}),
                "a write that left the old tail is caught");
        Files.delete(file);

        // The guard: only an automatic backup of an empty Day Hub over a file with data is refused.
        for (boolean manual : new boolean[] {true, false}) {
            for (boolean blank : new boolean[] {true, false}) {
                for (long bytes : new long[] {0, 1, 5000}) {
                    boolean want = !manual && blank && bytes > 0;
                    check(BackupGuard.shouldSkip(manual, blank, bytes) == want, "guard manual=" + manual + " blank=" + blank + " bytes=" + bytes);
                }
            }
        }
        check(!BackupGuard.shouldSkip(true, true, 9999), "a manual backup is always allowed");
        check(!BackupGuard.shouldSkip(false, true, 0), "an empty Day Hub may fill an empty file");
        check(!BackupGuard.shouldSkip(false, false, 9999), "a Day Hub with data may replace a file");
        check(BackupGuard.shouldSkip(false, true, 9999), "automatic: empty Day Hub must not replace a file with data");

        // The guard with the real engine: a fresh one is blank, one with a task is not.
        DayHubData fresh = DayHubData.inMemory();
        check(BackupGuard.shouldSkip(false, fresh.isBlank(), 1234), "fresh install is guarded");
        fresh.createTask(new org.json.JSONObject("{\"title\":\"x\"}"));
        check(!BackupGuard.shouldSkip(false, fresh.isBlank(), 1234), "real data is backed up");

        System.out.println("VERIFY AND GUARD OK (" + checks + " checks)");
    }
}
