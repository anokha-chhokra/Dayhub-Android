package app.dayhub.data;

/**
 * Feature 21: when a backup must not be made. A backup replaces the whole file, so an empty Day Hub
 * (a fresh install, say) must never overwrite a file that holds the person's data.
 */
public final class BackupGuard {
    private BackupGuard() {}

    /**
     * @param manual        the person pressed "Back up now" themselves, so they meant it
     * @param dayHubIsBlank {@link DayHubData#isBlank()}
     * @param fileBytes     how big the backup file's content is (0 for a new, empty file)
     * @return true if the backup should be skipped
     */
    public static boolean shouldSkip(boolean manual, boolean dayHubIsBlank, long fileBytes) {
        return !manual && dayHubIsBlank && fileBytes > 0;
    }
}
