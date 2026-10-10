package app.dayhub;

import android.content.Context;

import java.io.IOException;

import app.dayhub.data.DamagedDataException;
import app.dayhub.data.DayHubData;

/** Feature 29: Day Hub's data for the reminder code, which runs from alarms with the app closed. */
final class ReminderData {
    private ReminderData() { }

    /** The app's open copy if it is running, otherwise the saved file; null if there is nothing usable. */
    static DayHubData read(Context context) {
        DayHubData open = OpenData.get();
        if (open != null) return open;
        try {
            DataStore store = new DataStore(context.getApplicationContext());
            return store.exists() ? DayHubData.open(store) : null;
        } catch (IOException | DamagedDataException | RuntimeException e) {
            return null;
        }
    }
}
