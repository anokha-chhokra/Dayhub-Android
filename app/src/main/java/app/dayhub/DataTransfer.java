package app.dayhub;

import android.app.Activity;
import android.net.Uri;

import app.dayhub.data.DamagedDataRecovery;
import app.dayhub.data.DataError;
import app.dayhub.data.DataExport;
import app.dayhub.data.DayHubData;
import app.dayhub.data.Validate;

import java.io.IOException;

/**
 * Feature 11: moving data in and out. Exports (the JSON backup, the expenses CSV, the Markdown
 * journal) go to a file the person picks with Android's "save as" screen; a restore reads a backup
 * file they pick, shows what is in it, and replaces the current data only after they confirm.
 */
public final class DataTransfer {
    private final Activity activity;
    private final Overlays overlays;
    private final ActivityResults results;
    private final DayHubData data;

    public DataTransfer(Activity activity, Overlays overlays, ActivityResults results, DayHubData data) {
        this.activity = activity;
        this.overlays = overlays;
        this.results = results;
        this.data = data;
    }

    // ---------- export ----------

    public void exportBackup() {
        save(DataExport.backup(data));
    }

    /** @param month "YYYY-MM" for one month, or null for every expense */
    public void exportExpenses(String month) {
        try {
            save(DataExport.expensesCsv(data, month));
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        }
    }

    public void exportJournal() {
        save(DataExport.journalMarkdown(data, Validate.localDate()));
    }

    /** Asks where to put the file, then writes it there. */
    private void save(DataExport.Export file) {
        results.launch(DocumentFiles.createIntent(file.name, file.mimeType), (resultCode, intent) -> {
            if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) return;
            Uri uri = intent.getData();
            try {
                DocumentFiles.writeText(activity, uri, file.text);
                overlays.toast("Saved " + file.name);
            } catch (IOException e) {
                overlays.toast("Could not save the file");
            }
        });
    }

    // ---------- restore ----------

    /** Picks a backup file and, once the person confirms, replaces everything with it. */
    public void restoreFromFile(Runnable onRestored) {
        results.launch(DocumentFiles.pickIntent(), (resultCode, intent) -> {
            if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) return;
            restoreFromUri(intent.getData(), null, onRestored);
        });
    }

    /**
     * Reads the backup at {@code uri}, shows what is in it, and replaces everything with it once the
     * person confirms. {@code name} is how to refer to the file ("the backup file"), or null.
     */
    public void restoreFromUri(android.net.Uri uri, String name, Runnable onRestored) {
        String text;
        try {
            text = DocumentFiles.readText(activity, uri);
        } catch (IOException | RuntimeException e) {
            overlays.toast("Could not read that file");
            return;
        }
        String summary;
        try {
            summary = DamagedDataRecovery.inspect(text); // refuses a bad file before anything changes
        } catch (DataError e) {
            overlays.toast(e.getMessage());
            return;
        }
        RestoreConfirm.show(activity, overlays, name == null ? "This backup" : "\u201C" + name + "\u201D", summary,
                "Restoring replaces everything now saved on this device.",
                () -> replace(text, onRestored));
    }

    private void replace(String backupText, Runnable onRestored) {
        try {
            data.importAll(backupText);
            data.commit(); // if the phone cannot save it, the old data comes back
        } catch (DataError e) {
            overlays.toast(e.getMessage());
            return;
        }
        overlays.toast("Backup restored");
        onRestored.run();
    }
}
