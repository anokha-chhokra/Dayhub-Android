package app.dayhub;

import app.dayhub.data.DayHubData;

/**
 * Feature 28: the data the app has open right now, if it is running. A widget tap must change that same
 * copy: if it opened a second copy of the file, the app's next save would quietly undo the tap. With the
 * app closed there is nothing registered and the tap opens the file itself.
 */
public final class OpenData {
    private static DayHubData current;

    private OpenData() { }

    /** The app opened its data. */
    public static synchronized void set(DayHubData data) {
        current = data;
    }

    /** The app is closing its data; only clears it if it is still the registered one. */
    public static synchronized void clear(DayHubData data) {
        if (current == data) current = null;
    }

    /** The app's open data, or null if the app is not running. */
    public static synchronized DayHubData get() {
        return current;
    }
}
