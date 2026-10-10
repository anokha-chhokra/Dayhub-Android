package app.dayhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import app.dayhub.widgets.WidgetUpdater;

/** Feature 27: redraws the widgets when the date, the time or the time zone changes, so "today" is never yesterday's. */
public class WidgetRefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        WidgetUpdater.updateAll(context);
    }
}
