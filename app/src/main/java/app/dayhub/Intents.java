package app.dayhub;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** The intents other parts of the phone use to reach Day Hub: its notifications, the focus guard and widgets. */
public final class Intents {
    private Intents() { }

    /** Brings Day Hub to the front (or opens it), like tapping its icon. */
    public static Intent open(Context c) {
        return new Intent(c, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    /** Opens Day Hub on a screen or job (see {@link AppTargets}). */
    public static Intent open(Context c, String target) {
        Intent i = open(c);
        // The data makes each target its own intent: extras alone would not tell two apart.
        i.setData(android.net.Uri.parse("dayhub://open/" + target));
        i.putExtra(AppTargets.EXTRA_TARGET, target);
        return i;
    }

    public static PendingIntent openPending(Context c) {
        return PendingIntent.getActivity(c, 0, open(c), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
