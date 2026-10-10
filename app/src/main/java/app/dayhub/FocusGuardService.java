package app.dayhub;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import app.dayhub.data.FocusPolicy;
import app.dayhub.data.FocusSession;

/**
 * Features 25 and 26: the lock behind a focus timer. While a timer runs, this watches which app comes to the
 * front (and nothing else: it cannot read what is on the screen) and sends the person back to Day Hub unless
 * that app is Day Hub, text messages, WhatsApp, or something Android needs around them, such as the keyboard
 * and the phone app for calls and emergency alerts (see {@link FocusPolicy}).
 *
 * Android may refuse to let a background service open an activity unless Day Hub is allowed to "Display over
 * other apps". So there are two layers:
 *  1. open Day Hub straight away;
 *  2. in case that did not work, cover the other app with the Focus mode page ({@link FocusCover}), which has
 *     a Back to Day Hub button.
 * Either way the timer always ends by the clock, and holding a button for 8 seconds ends it early.
 */
public class FocusGuardService extends AccessibilityService {
    /** Do not jump back more than about twice a second, however many windows change. */
    private static final long BOUNCE_GAP_MS = 600;
    /** Give the jump back this long to work before covering the other app. */
    private static final long COVER_DELAY_MS = 450;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FocusCover cover;
    private long lastBounce;

    private final Runnable showCover = () -> cover().show(FocusState.get(this));
    /** The timer ran out while the cover was up: take it away (and finish the timer if the alarm has not yet). */
    private final Runnable coverTimeUp = () -> {
        FocusController.expireIfNeeded(this);
        if (!FocusState.isActive(this)) hideCover();
    };
    private final FocusEvents.Listener listener = () -> handler.post(() -> {
        if (!FocusState.isActive(this)) hideCover();
    });

    private FocusCover cover() {
        if (cover == null) cover = new FocusCover(this);
        return cover;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        FocusEvents.add(listener);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        FocusEvents.remove(listener);
        hideCover();
        return super.onUnbind(intent);
    }

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
            hideCover();
            return;
        }
        if (pkg.equals(getPackageName())) {
            // The cover is our own window and reports changes too; only Day Hub's own screen means "back".
            CharSequence cls = event.getClassName();
            if (cls != null && MainActivity.class.getName().contentEquals(cls)) hideCover();
            return;
        }
        if (FocusPolicy.isAllowed(pkg, FocusAllowlist.onThisPhone(this))) {
            hideCover();
            return;
        }
        sendBack(s);
    }

    private void sendBack(FocusSession s) {
        long t = SystemClock.elapsedRealtime();
        if (t - lastBounce >= BOUNCE_GAP_MS) {
            lastBounce = t;
            try {
                startActivity(Intents.open(this));
            } catch (RuntimeException ignored) {
                // Android refused: the cover below still hides the other app
            }
        }
        // Even with "Display over other apps" a phone can still refuse the jump back, so always arm the cover.
        // It is dropped as soon as Day Hub reports that it is in front again.
        if (!cover().isShown()) {
            handler.removeCallbacks(showCover);
            handler.postDelayed(showCover, COVER_DELAY_MS);
            handler.removeCallbacks(coverTimeUp);
            handler.postDelayed(coverTimeUp, Math.max(0, s.endsAt - System.currentTimeMillis()) + 200);
        }
    }

    private void hideCover() {
        handler.removeCallbacks(showCover);
        handler.removeCallbacks(coverTimeUp);
        if (cover != null) cover.hide();
    }
}
