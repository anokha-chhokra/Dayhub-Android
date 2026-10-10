package app.dayhub;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import app.dayhub.data.FocusPolicy;
import app.dayhub.data.FocusSession;

/**
 * Feature 25: the lock behind a focus timer. While a timer runs, this watches which app comes to the front
 * (and nothing else: it cannot read what is on the screen) and sends the person back to Day Hub unless that
 * app is Day Hub, text messages, WhatsApp, or something Android needs around them, such as the keyboard,
 * the phone app for calls and emergency alerts (see {@link FocusPolicy}).
 *
 * It only acts while a timer is running, and the timer always ends by the clock, so the lock cannot outlast it.
 * Android may refuse to let a background service open an activity unless Day Hub is allowed to "Display over
 * other apps"; a cover for phones that refuse anyway comes with the next feature.
 */
public class FocusGuardService extends AccessibilityService {
    /** Do not jump back more than about twice a second, however many windows change. */
    private static final long BOUNCE_GAP_MS = 600;

    private long lastBounce;

    @Override
    public void onInterrupt() { }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence name = event.getPackageName();
        if (name == null) return;
        String pkg = name.toString();

        FocusSession s = FocusState.get(this);
        long now = System.currentTimeMillis();
        if (!s.isActive(now)) {
            if (s.hasExpired(now)) FocusController.expireIfNeeded(this); // the alarm has not fired yet
            return;
        }
        if (pkg.equals(getPackageName())) return;
        if (FocusPolicy.isAllowed(pkg, FocusAllowlist.onThisPhone(this))) return;
        sendBack();
    }

    private void sendBack() {
        long t = SystemClock.elapsedRealtime();
        if (t - lastBounce < BOUNCE_GAP_MS) return;
        lastBounce = t;
        try {
            startActivity(Intents.open(this));
        } catch (RuntimeException ignored) {
            // Android refused to start it from the background (see the note above).
        }
    }
}
