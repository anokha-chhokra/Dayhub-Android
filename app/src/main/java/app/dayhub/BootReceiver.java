package app.dayhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import app.dayhub.widgets.WidgetUpdater;

/**
 * Feature 29: after a restart, or after Day Hub is updated, Android has forgotten every alarm. Set the reminder
 * alarm again, put the focus timer's alarm and notification back if one is still running, and redraw the widgets.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderScheduler.reschedule(context);
        FocusController.resume(context);
        WidgetUpdater.updateAll(context);
    }
}
