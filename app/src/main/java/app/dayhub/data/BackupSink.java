package app.dayhub.data;

import java.io.IOException;

/**
 * Where a backup goes: the one file the person chose. The Android side implements this over the
 * system file picker's lasting access; tests use a fake one.
 */
public interface BackupSink {
    /** Whether the person has chosen a backup file. */
    boolean configured();

    /** Whether to back up automatically (after changes and when leaving the app). */
    boolean auto();

    /** How big the backup file's content is now (0 for a new, empty file). */
    long fileBytes();

    /** Replaces the whole file with {@code text} and confirms it was saved, or throws. */
    void write(String text) throws IOException;
}
