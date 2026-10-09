package app.dayhub;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What Android itself remembers about the backup file: which file was chosen, its name, and how the
 * last backup went. Everything about the person's days lives in the data file, not here.
 */
public final class BackupPrefs {
    private final SharedPreferences p;

    public BackupPrefs(Context context) {
        p = context.getApplicationContext().getSharedPreferences("dayhub", Context.MODE_PRIVATE);
    }

    /** The chosen file's address, or null if none was chosen. */
    public String uri() {
        return p.getString("backup.uri", null);
    }

    public String name() {
        return p.getString("backup.name", null);
    }

    /** Size of the file's content after the last backup (or when it was chosen). */
    public long bytes() {
        return p.getLong("backup.bytes", 0);
    }

    /** When the last successful backup finished (milliseconds), or 0 if there has been none. */
    public long lastAt() {
        return p.getLong("backup.lastAt", 0);
    }

    public boolean lastOk() {
        return p.getBoolean("backup.lastOk", true);
    }

    public String lastError() {
        return p.getString("backup.lastError", "");
    }

    public void setFile(String uri, String name, long bytes) {
        p.edit().putString("backup.uri", uri).putString("backup.name", name).putLong("backup.bytes", bytes)
                .putLong("backup.lastAt", 0).putBoolean("backup.lastOk", true).putString("backup.lastError", "").apply();
    }

    public void succeeded(long bytes, long at) {
        p.edit().putLong("backup.bytes", bytes).putLong("backup.lastAt", at).putBoolean("backup.lastOk", true)
                .putString("backup.lastError", "").apply();
    }

    public void failed(String message) {
        p.edit().putBoolean("backup.lastOk", false).putString("backup.lastError", message == null ? "" : message).apply();
    }

    public void clear() {
        p.edit().remove("backup.uri").remove("backup.name").putLong("backup.bytes", 0).putLong("backup.lastAt", 0)
                .putBoolean("backup.lastOk", true).putString("backup.lastError", "").apply();
    }
}
