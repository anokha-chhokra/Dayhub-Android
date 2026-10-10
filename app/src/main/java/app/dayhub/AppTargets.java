package app.dayhub;

import android.content.Intent;

/**
 * Feature 27: lets something outside the app (a widget) open Day Hub on a particular screen or job: a
 * screen ("tasks", "habits", "journal", "spend", "music", "settings", "home") or a job ("task", "expense",
 * "note", "focus"). If Day Hub is not ready yet (still opening its data), the request waits for the shell.
 */
public final class AppTargets {
    static final String EXTRA_TARGET = "app.dayhub.TARGET";

    private ShellScreen shell;
    private String pending;

    /** Remembers the target in an intent, and acts on it at once if the screens are up. */
    public void offer(Intent intent) {
        if (intent == null) return;
        String target = intent.getStringExtra(EXTRA_TARGET);
        if (target == null || target.isEmpty()) return;
        intent.removeExtra(EXTRA_TARGET); // so it is not replayed when the activity is recreated
        pending = target;
        apply();
    }

    /** The screens are ready: do anything that was waiting. */
    public void attach(ShellScreen shell) {
        this.shell = shell;
        apply();
    }

    private void apply() {
        if (shell == null || pending == null) return;
        String target = pending;
        pending = null;
        shell.post(() -> shell.openTarget(target));
    }
}
