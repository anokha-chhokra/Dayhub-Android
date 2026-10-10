package app.dayhub;

import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;

import app.dayhub.data.DayHubData;
import app.dayhub.data.ReminderPlan;
import app.dayhub.data.Validate;

/**
 * Feature 29: while Day Hub is open, a due reminder also shows as a message on screen (as the web app does),
 * whether or not system notifications are on. Each reminder shows once a day.
 */
final class InAppReminders {
    private static final long CHECK_MS = 20_000;

    private final DayHubData data;
    private final Overlays overlays;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> shown = new HashSet<>(); // "day|key"
    private final Runnable check = this::check;

    InAppReminders(DayHubData data, Overlays overlays) {
        this.data = data;
        this.overlays = overlays;
    }

    void start() {
        handler.removeCallbacks(check);
        check();
    }

    void stop() {
        handler.removeCallbacks(check);
    }

    private void check() {
        String day = Validate.localDate();
        for (ReminderPlan.Reminder r : ReminderPlan.dueNow(data, day, DateLabels.nowHHMM(), -1)) {
            if (shown.add(day + "|" + r.key)) overlays.toast(r.title);
        }
        handler.postDelayed(check, CHECK_MS);
    }
}
