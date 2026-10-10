package app.dayhub.widgets;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Feature 27: redraws every Day Hub widget on the home screens. Cheap and safe to call from anywhere. */
public final class WidgetUpdater {
    private static final long QUIET_MS = 400;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable pending;

    private WidgetUpdater() { }

    public static void updateAll(Context context) {
        Context c = context.getApplicationContext();
        BaseWidget[] all = {new HomeWidget(), new TasksWidget(), new HabitsWidget(), new SpendWidget(), new QuickWidget(), new FocusWidget()};
        for (BaseWidget w : all) {
            try {
                w.updateAll(c);
            } catch (RuntimeException ignored) {
                // one widget failing must not stop the others
            }
        }
    }

    /** Redraws shortly from now; a burst of saves (ticking several things) is drawn once. Main thread only. */
    public static void request(Context context) {
        Context c = context.getApplicationContext();
        if (pending != null) HANDLER.removeCallbacks(pending);
        pending = () -> {
            pending = null;
            updateAll(c);
        };
        HANDLER.postDelayed(pending, QUIET_MS);
    }
}
