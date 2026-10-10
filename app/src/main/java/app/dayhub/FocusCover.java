package app.dayhub;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import app.dayhub.data.FocusSession;
import app.dayhub.data.HoldToEnd;

/**
 * Feature 26: the fallback for phones that will not let the focus guard open Day Hub from the background.
 * It is a full-screen "Focus mode is on" page laid over the other app (an accessibility overlay, which needs
 * no extra permission), with Back to Day Hub and the eight-second hold to end focus early. The guard shows
 * it only when the jump back has not worked, and drops it the moment Day Hub is in front again.
 */
final class FocusCover {
    private final AccessibilityService service;
    private WindowManager windows;
    private View cover;

    FocusCover(AccessibilityService service) {
        this.service = service;
    }

    boolean isShown() {
        return cover != null;
    }

    private WindowManager windowManager() {
        if (windows == null) {
            // A service's own window service already carries the token that allows an accessibility overlay;
            // a window context made from a service has no display and would throw.
            windows = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        }
        return windows;
    }

    private TextView text(CharSequence s, int sp, boolean bold, int colorRes) {
        TextView t = Sketch.label(service, s, sp, bold, colorRes);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** Covers the screen for the running timer. Does nothing if it is already up, or the timer is over. */
    void show(FocusSession s) {
        if (cover != null || !s.isActive(System.currentTimeMillis())) return;

        LinearLayout box = new LinearLayout(service);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(service.getColor(R.color.paper));
        box.setPadding(Sketch.dp(service, 32), Sketch.dp(service, 64), Sketch.dp(service, 32), Sketch.dp(service, 64));

        String until = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(s.endsAt));
        box.addView(text("🎯 Focus mode is on", 28, true, R.color.ink));
        TextView sub = text("Only Day Hub, Messages and WhatsApp work until " + until + ".", 18, false, R.color.muted);
        sub.setPadding(0, Sketch.dp(service, 12), 0, Sketch.dp(service, 28));
        box.addView(sub);

        HandDrawnButton back = new HandDrawnButton(service, "Back to Day Hub", true);
        back.setOnClickListener(v -> {
            try {
                service.startActivity(Intents.open(service));
            } catch (RuntimeException ignored) {
                // Android refused again; the cover stays and the hold below still works
            }
        });
        box.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        HoldButton end = new HoldButton(service, "Hold " + HoldToEnd.HOLD_MS / 1000 + "s to end early");
        end.setContentDescription("Hold for " + HoldToEnd.HOLD_MS / 1000 + " seconds to end focus early");
        end.setOnHeld(() -> FocusController.end(service)); // the guard hides the cover when it hears focus has ended
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        endParams.topMargin = Sketch.dp(service, 40);
        box.addView(end, endParams);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try {
            windowManager().addView(box, lp);
            cover = box;
        } catch (RuntimeException e) {
            cover = null; // not allowed on this phone: the jump back is all there is
            Log.w("DayHubFocus", "Could not show the focus cover", e);
        }
    }

    void hide() {
        if (cover == null) return;
        try {
            windowManager().removeView(cover);
        } catch (RuntimeException ignored) {
            // already gone
        }
        cover = null;
    }
}
