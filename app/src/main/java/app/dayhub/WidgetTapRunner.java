package app.dayhub;

import android.content.Context;

import java.io.IOException;

import app.dayhub.data.AutoBackup;
import app.dayhub.data.DamagedDataException;
import app.dayhub.data.DayHubData;
import app.dayhub.data.Validate;
import app.dayhub.data.WidgetTaps;

/**
 * Feature 28: runs a widget tap against Day Hub's data. If the app is running the tap changes the app's own
 * open copy (see {@link OpenData}), which saves, backs up and refreshes the widgets itself. If the app is
 * closed the tap opens the file, makes the change, saves it, and updates the backup file if automatic
 * backups are on. Main thread only.
 */
final class WidgetTapRunner {
    interface Job {
        WidgetTaps.Result run(DayHubData data, String day, String time);
    }

    private WidgetTapRunner() { }

    /** Returns something to tell the person, or null. */
    static String run(Context context, Job job) {
        String day = Validate.localDate();
        String time = DateLabels.nowHHMM();
        DayHubData open = OpenData.get();
        if (open != null) return job.run(open, day, time).message;

        Context c = context.getApplicationContext();
        DataStore store = new DataStore(c);
        if (!store.exists()) return "Open Day Hub once first.";
        try {
            DayHubData data = DayHubData.open(store);
            WidgetTaps.Result r = job.run(data, day, time);
            if (r.changed) {
                AutoBackup backup = new AutoBackup(data, new BackupFileSink(new BackupFile(c, new BackupPrefs(c))), new HandlerScheduler());
                backup.noteChange();
                backup.flushNow();
            }
            return r.message;
        } catch (IOException | DamagedDataException e) {
            return "Day Hub could not open its saved data.";
        }
    }
}
