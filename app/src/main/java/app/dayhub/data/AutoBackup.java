package app.dayhub.data;

import java.io.IOException;

/**
 * Feature 22: keeps the backup file current without being asked. A little while after a change is
 * saved (so a burst of changes becomes one backup), and again when the person leaves the app, the
 * whole of Day Hub is written over the backup file, if one is chosen and automatic backups are on.
 * It follows the same rules as a manual backup, including never replacing a backup that has data
 * with an empty Day Hub. Problems are remembered by the sink and never interrupt the person.
 */
public final class AutoBackup {
    /** How long to wait after a change before backing up. */
    public static final long DELAY_MS = 15_000;

    /** Runs a task later; the Android side uses a Handler, tests use a fake. */
    public interface Scheduler {
        void postDelayed(Runnable task, long delayMs);

        void cancel(Runnable task);
    }

    private final DayHubData data;
    private final BackupSink sink;
    private final Scheduler scheduler;
    private final Runnable pending = this::backUp;
    private boolean dirty;

    public AutoBackup(DayHubData data, BackupSink sink, Scheduler scheduler) {
        this.data = data;
        this.sink = sink;
        this.scheduler = scheduler;
    }

    /** Whether something has changed since the last successful backup. */
    public boolean hasUnbackedChanges() {
        return dirty;
    }

    /** Call after anything is saved. Waits a moment so a burst of changes becomes one backup. */
    public void noteChange() {
        dirty = true;
        if (sink.configured() && sink.auto()) {
            scheduler.cancel(pending);
            scheduler.postDelayed(pending, DELAY_MS);
        }
    }

    /** Call when the person leaves the app: backs up at once if anything changed. */
    public void flushNow() {
        scheduler.cancel(pending);
        if (dirty && sink.configured() && sink.auto()) backUp();
    }

    private void backUp() {
        try {
            BackupRunner.Result r = BackupRunner.run(data, sink, false);
            if (!r.skipped) dirty = false;
        } catch (IOException e) {
            // The sink has remembered why; the change stays unbacked-up and is tried again next time.
        }
    }
}
