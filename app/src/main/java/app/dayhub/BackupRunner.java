package app.dayhub;

import app.dayhub.data.BackupGuard;
import app.dayhub.data.DataExport;
import app.dayhub.data.DayHubData;

import java.io.IOException;

/**
 * Feature 21: makes one backup. It never replaces a backup that holds data with an empty Day Hub
 * (unless the person asked for it by hand), writes the whole file, and confirms by reading it back.
 */
public final class BackupRunner {
    /** What happened: either the backup was skipped on purpose, or it was written. */
    public static final class Result {
        public final boolean skipped;
        public final BackupFile.Written written;

        private Result(boolean skipped, BackupFile.Written written) {
            this.skipped = skipped;
            this.written = written;
        }
    }

    private BackupRunner() {}

    /**
     * @param manual true when the person pressed Back up now; false for automatic backups
     * @throws IOException when no file is chosen (a manual backup) or the write could not be confirmed
     */
    public static Result run(DayHubData data, BackupFile file, boolean manual) throws IOException {
        if (!file.configured()) {
            if (manual) throw new IOException("Choose a backup file first.");
            return new Result(true, null);
        }
        if (BackupGuard.shouldSkip(manual, data.isBlank(), file.prefs().bytes())) return new Result(true, null);
        return new Result(false, file.write(DataExport.backup(data).text));
    }
}
