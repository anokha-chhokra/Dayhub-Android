package app.dayhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import app.dayhub.data.DayHubData;
import app.dayhub.data.ReminderPlan;
import app.dayhub.data.Validate;

/** Feature 29: the alarm for the next reminder. Shows what is due, then sets the alarm for the one after. */
public class ReminderReceiver extends BroadcastReceiver {
    /** A reminder later than this (the phone was off, or Android held the alarm back) is skipped, not announced. */
    private static final int MAX_LATE_MINUTES = 90;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            fire(context);
        } finally {
            ReminderScheduler.reschedule(context);
        }
    }

    static void fire(Context c) {
        DayHubData data = ReminderData.read(c);
        if (data == null || !data.getSettings().notifications) return;
        String day = Validate.localDate();
        for (ReminderPlan.Reminder r : ReminderPlan.dueNow(data, day, DateLabels.nowHHMM(), MAX_LATE_MINUTES)) {
            if (ReminderState.shown(c, day, r.key)) continue;
            ReminderNotifications.post(c, r);
            ReminderState.markShown(c, day, r.key);
        }
    }
}
