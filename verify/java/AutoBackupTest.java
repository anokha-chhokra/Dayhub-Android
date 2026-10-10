import app.dayhub.DataStore;
import app.dayhub.data.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Automatic backups (feature 22): scheduling, flushing, the guard, failures, and the commit hook. */
public class AutoBackupTest {
    static int checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            System.out.println("FAIL " + what);
            System.exit(1);
        }
    }

    /** A fake backup file. */
    static final class Sink implements BackupSink {
        boolean configured = true;
        boolean auto = true;
        long bytes = 0;
        boolean failing;
        final List<String> written = new ArrayList<>();

        public boolean configured() { return configured; }
        public boolean auto() { return auto; }
        public long fileBytes() { return bytes; }
        public void write(String text) throws IOException {
            if (failing) throw new IOException("Could not confirm that the backup file was saved.");
            written.add(text);
            bytes = text.length();
        }
    }

    /** A fake clock: tasks run only when the test says time has passed. */
    static final class Clock implements AutoBackup.Scheduler {
        Runnable task;
        long delay = -1;
        int posts;
        int cancels;

        public void postDelayed(Runnable t, long ms) { task = t; delay = ms; posts++; }
        public void cancel(Runnable t) { if (task == t) task = null; cancels++; }
        void elapse() {
            Runnable t = task;
            task = null;
            if (t != null) t.run();
        }
    }

    static JSONObject j(String s) throws Exception { return new JSONObject(s); }

    public static void main(String[] a) throws Exception {
        // A change is backed up after the delay, as one backup however many changes there were.
        {
            DayHubData d = DayHubData.inMemory();
            Sink sink = new Sink();
            Clock clock = new Clock();
            AutoBackup auto = new AutoBackup(d, sink, clock);
            d.createTask(j("{\"title\":\"one\"}"));
            auto.noteChange();
            d.createTask(j("{\"title\":\"two\"}"));
            auto.noteChange();
            d.createTask(j("{\"title\":\"three\"}"));
            auto.noteChange();
            check(clock.delay == 15_000 && clock.posts == 3 && clock.task != null, "each change restarts the 15 second wait");
            check(sink.written.isEmpty() && auto.hasUnbackedChanges(), "nothing written before the wait is over");
            clock.elapse();
            check(sink.written.size() == 1, "one backup for a burst of changes");
            check(sink.written.get(0).contains("three") && sink.written.get(0).contains("one"), "it holds everything");
            check(!auto.hasUnbackedChanges(), "clean after a backup");
            auto.flushNow();
            check(sink.written.size() == 1, "leaving the app with nothing new writes nothing");
        }

        // Leaving the app backs up at once and cancels the wait.
        {
            DayHubData d = DayHubData.inMemory();
            Sink sink = new Sink();
            Clock clock = new Clock();
            AutoBackup auto = new AutoBackup(d, sink, clock);
            d.createTask(j("{\"title\":\"x\"}"));
            auto.noteChange();
            auto.flushNow();
            check(sink.written.size() == 1 && clock.task == null, "flush writes now and cancels the wait");
            clock.elapse();
            check(sink.written.size() == 1, "the cancelled wait does not write again");
        }

        // Switched off, or no file chosen: nothing is scheduled or written, but the change is remembered.
        {
            DayHubData d = DayHubData.inMemory();
            Sink sink = new Sink();
            Clock clock = new Clock();
            AutoBackup auto = new AutoBackup(d, sink, clock);
            d.createTask(j("{\"title\":\"x\"}"));
            sink.auto = false;
            auto.noteChange();
            auto.flushNow();
            check(clock.posts == 0 && sink.written.isEmpty() && auto.hasUnbackedChanges(), "automatic backups off");
            sink.auto = true;
            sink.configured = false;
            auto.noteChange();
            auto.flushNow();
            check(clock.posts == 0 && sink.written.isEmpty(), "no file chosen");
            sink.configured = true;
            auto.flushNow();
            check(sink.written.size() == 1, "once a file is chosen the earlier change is backed up on leaving");
        }

        // The guard: a fresh install never replaces a backup that has data, and stays "unbacked" so a later real change goes through.
        {
            DayHubData d = DayHubData.inMemory();
            Sink sink = new Sink();
            sink.bytes = 4096;
            Clock clock = new Clock();
            AutoBackup auto = new AutoBackup(d, sink, clock);
            d.updateSettings(j("{\"name\":\"New phone\"}"));
            auto.noteChange();
            clock.elapse();
            auto.flushNow();
            check(sink.written.isEmpty() && sink.bytes == 4096, "an empty Day Hub does not replace a backup that has data");
            d.createTask(j("{\"title\":\"real\"}"));
            auto.noteChange();
            clock.elapse();
            check(sink.written.size() == 1, "after real data arrives it is backed up");
            // An empty Day Hub may fill an empty file.
            DayHubData fresh = DayHubData.inMemory();
            Sink empty = new Sink();
            AutoBackup a2 = new AutoBackup(fresh, empty, new Clock());
            a2.noteChange();
            a2.flushNow();
            check(empty.written.size() == 1, "an empty Day Hub can fill an empty file");
        }

        // A failing write never throws out of the automatic backup, and is tried again later.
        {
            DayHubData d = DayHubData.inMemory();
            Sink sink = new Sink();
            Clock clock = new Clock();
            AutoBackup auto = new AutoBackup(d, sink, clock);
            d.createTask(j("{\"title\":\"x\"}"));
            sink.failing = true;
            auto.noteChange();
            clock.elapse();
            check(sink.written.isEmpty() && auto.hasUnbackedChanges(), "a failed backup leaves the change unbacked-up");
            auto.flushNow();
            check(sink.written.isEmpty() && auto.hasUnbackedChanges(), "a failed flush does not throw");
            sink.failing = false;
            auto.flushNow();
            check(sink.written.size() == 1 && !auto.hasUnbackedChanges(), "it works once the file does");
        }

        // The backup written is the real backup format and restores into a fresh app.
        {
            DayHubData d = DayHubData.inMemory();
            Sink sink = new Sink();
            AutoBackup auto = new AutoBackup(d, sink, new Clock());
            d.createTask(j("{\"title\":\"Send invoice\",\"priority\":1}"));
            d.createEntry(j("{\"text\":\"hello\\nworld\",\"mood\":4,\"today\":\"2026-10-05\"}"));
            auto.noteChange();
            auto.flushNow();
            DayHubData fresh = DayHubData.inMemory();
            fresh.importAll(sink.written.get(0));
            check(fresh.listTasks("all").get(0).title.equals("Send invoice") && fresh.listEntries(null, null).get(0).text.equals("hello\nworld"),
                    "the automatic backup restores");
        }

        // The hook on the engine: fires once per save that wrote something, never for a clean commit or a failed save.
        {
            File dir = Files.createTempDirectory("hook").toFile();
            DataStore store = new DataStore(new File(dir, "dayhub.json"));
            DayHubData d = DayHubData.open(store);
            int[] fired = new int[1];
            d.setOnCommitted(() -> fired[0]++);
            d.commit();
            check(fired[0] == 0, "a clean commit does not fire");
            d.createTask(j("{\"title\":\"a\"}"));
            check(fired[0] == 0, "changing data alone does not fire");
            d.commit();
            check(fired[0] == 1, "a real save fires once");
            d.commit();
            check(fired[0] == 1, "committing again with nothing new does not fire");
            File blocker = new File(dir, "dayhub.json.tmp");
            check(blocker.mkdir() && new File(blocker, "x").createNewFile(), "setup blocked saves");
            d.createTask(j("{\"title\":\"b\"}"));
            try {
                d.commit();
                check(false, "commit should fail");
            } catch (DataError e) {
                check(e.status == 507, "507");
            }
            check(fired[0] == 1, "a failed save does not fire");
            // Wired to a real AutoBackup through a real engine.
            new File(blocker, "x").delete();
            blocker.delete();
            Sink sink = new Sink();
            Clock clock = new Clock();
            AutoBackup auto = new AutoBackup(d, sink, clock);
            d.setOnCommitted(auto::noteChange);
            d.createTask(j("{\"title\":\"c\"}"));
            d.commit();
            check(clock.task != null, "a save schedules the backup");
            clock.elapse();
            check(sink.written.size() == 1 && sink.written.get(0).contains("\"c\""), "the scheduled backup holds the saved data");
        }

        // BackupRunner: a manual backup with nothing chosen is an error; an automatic one just does nothing.
        {
            Sink sink = new Sink();
            sink.configured = false;
            try {
                BackupRunner.run(DayHubData.inMemory(), sink, true);
                check(false, "manual with no file should fail");
            } catch (IOException e) {
                check(e.getMessage().contains("Choose a backup file"), "message: " + e.getMessage());
            }
            check(BackupRunner.run(DayHubData.inMemory(), sink, false).skipped, "automatic with no file is skipped quietly");
            sink.configured = true;
            sink.bytes = 500;
            check(!BackupRunner.run(DayHubData.inMemory(), sink, true).skipped && sink.written.size() == 1, "a manual backup of an empty Day Hub is allowed");
        }

        System.out.println("AUTO BACKUP OK (" + checks + " checks)");
    }
}
