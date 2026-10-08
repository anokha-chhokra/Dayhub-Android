package app.dayhub.data;

import app.dayhub.DataStore;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Feature 6: ways out when the saved data cannot be used. Nothing is ever deleted: the unreadable
 * document is moved aside under another name, and the person either starts empty or restores a
 * backup file.
 */
public final class DamagedDataRecovery {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DamagedDataRecovery() {}

    /** The engine to carry on with, and where the unreadable copy was kept (null if there was none). */
    public static final class Result {
        public final DayHubData data;
        public final File keptCopy;

        Result(DayHubData data, File keptCopy) {
            this.data = data;
            this.keptCopy = keptCopy;
        }
    }

    /** Keeps the unreadable copy under another name and opens an empty Day Hub. */
    public static Result startFresh(DataStore store) throws IOException {
        File kept = store.moveAside("dayhub-damaged-" + LocalDateTime.now().format(STAMP) + ".json");
        return new Result(DayHubData.startFresh(store), kept);
    }

    /**
     * Checks that {@code text} is a good backup without changing anything, and says what is in it.
     * Throws a {@link DataError} naming the problem when it is not.
     */
    public static String inspect(String text) {
        return describe(DayHubData.inMemory().importAll(text));
    }

    /** "3 journal entries, 5 tasks, 2 expenses and 1 habits", as the web app words it. */
    public static String describe(Map<String, Integer> counts) {
        return counts.get("entries") + " journal entries, " + counts.get("tasks") + " tasks, "
                + counts.get("expenses") + " expenses and " + counts.get("habits") + " habits";
    }

    /**
     * Replaces the unusable data with a backup. The backup is checked first, so a bad file changes
     * nothing. The unreadable copy is kept aside, and put back if the restored data cannot be saved.
     */
    public static Result restore(DataStore store, String backupText) throws IOException {
        inspect(backupText); // throws before anything is touched
        Result fresh = startFresh(store);
        try {
            fresh.data.importAll(backupText);
            fresh.data.commit();
        } catch (RuntimeException e) {
            if (fresh.keptCopy != null) store.moveBack(fresh.keptCopy);
            throw e;
        }
        return fresh;
    }
}
