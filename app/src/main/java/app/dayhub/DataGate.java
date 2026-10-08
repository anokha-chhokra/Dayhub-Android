package app.dayhub;

import android.app.Activity;
import android.widget.FrameLayout;

import app.dayhub.data.DamagedDataException;
import app.dayhub.data.DayHubData;

import java.io.IOException;

/**
 * Feature 6: opens the saved data when the app starts. If it opens, the app carries on; if it
 * cannot be used, the damaged-data screen takes the place of the app until the person recovers.
 */
public final class DataGate {
    public interface Ready {
        void onReady(DayHubData data);
    }

    private DataGate() {}

    /** Opens the data and calls {@code ready} with it, now or after a recovery. */
    public static void open(Activity activity, FrameLayout content, Overlays overlays,
                            ActivityResults results, Ready ready) {
        DataStore store = new DataStore(activity);
        DamagedDataException problem;
        try {
            ready.onReady(DayHubData.open(store));
            return;
        } catch (DamagedDataException e) {
            problem = e;
        } catch (IOException e) {
            problem = new DamagedDataException(
                    "Day Hub could not open its saved data (" + e.getMessage() + ").", null);
        }
        content.addView(new DamagedDataScreen(activity, overlays, results, store, problem, data -> {
            content.removeAllViews();
            ready.onReady(data);
        }));
    }
}
