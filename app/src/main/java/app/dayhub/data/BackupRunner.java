package app.dayhub.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Makes one backup. It never replaces a backup that holds data with an empty Day Hub (unless the
 * person asked for it by hand), and the sink confirms the write by reading the file back.
 */
public final class BackupRunner {
    /** What happened: either the backup was skipped on purpose, or this many bytes were written. */
    public static final class Result {
        public final boolean skipped;
        public final long bytes;

        private Result(boolean skipped, long bytes) {
            this.skipped = skipped;
            this.bytes = bytes;
        }
    }

    private BackupRunner() {}

    /**
     * @param manual true when the person pressed Back up now; false for automatic backups
     * @throws IOException when no file is chosen (a manual backup) or the write could not be confirmed
     */
    public static Result run(DayHubData data, BackupSink sink, boolean manual) throws IOException {
        if (!sink.configured()) {
            if (manual) throw new IOException("Choose a backup file first.");
            return new Result(true, 0);
        }
        if (BackupGuard.shouldSkip(manual, data.isBlank(), sink.fileBytes())) return new Result(true, 0);
        String text = DataExport.backup(data).text;
        sink.write(text);
        return new Result(false, text.getBytes(StandardCharsets.UTF_8).length);
    }
}
