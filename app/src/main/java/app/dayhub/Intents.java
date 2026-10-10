package app.dayhub;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** The intents other parts of the phone use to reach Day Hub: its notifications and the focus guard. */
public final class Intents {
    private Intents() { }

    /** Brings Day Hub to the front (or opens it), like tapping its icon. */
    public static Intent open(Context c) {
        return new Intent(c, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    public static PendingIntent openPending(Context c) {
        return PendingIntent.getActivity(c, 0, open(c), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
