package app.dayhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Feature 24: the alarm that goes off when a focus timer is over, even if the app has been closed. */
public class FocusEndReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        FocusController.onAlarm(context);
    }
}
