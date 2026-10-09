package app.dayhub;

import android.os.Handler;
import android.os.Looper;

import app.dayhub.data.AutoBackup;

/** Runs the automatic backup's delayed task on the main thread. */
public final class HandlerScheduler implements AutoBackup.Scheduler {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void postDelayed(Runnable task, long delayMs) {
        handler.postDelayed(task, delayMs);
    }

    @Override
    public void cancel(Runnable task) {
        handler.removeCallbacks(task);
    }
}
