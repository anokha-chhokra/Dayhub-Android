package app.dayhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import app.dayhub.widgets.WidgetUpdater;

/**
 * Features 27 and 29: when the date, the time or the time zone changes (and so at midnight) the widgets are redrawn,
 * so "today" is never yesterday's, and the reminder alarm is worked out again for the new day.
 */
public class WidgetRefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        WidgetUpdater.updateAll(context);
        ReminderScheduler.reschedule(context);
    }
}
