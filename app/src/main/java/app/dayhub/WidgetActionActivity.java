package app.dayhub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import app.dayhub.widgets.WidgetIntents;
import app.dayhub.widgets.WidgetUpdater;

/**
 * Feature 27: every tap on a widget lands here. It has no screen of its own: it does the job and closes at
 * once, or opens Day Hub on the right screen.
 */
public class WidgetActionActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            handle(getIntent());
        } catch (RuntimeException ignored) {
            // a widget tap must never crash the app
        }
        finish();
    }

    private void handle(Intent i) {
        if (i == null) return;
        String act = i.getStringExtra(WidgetIntents.EXTRA_ACT);
        if (act == null) return;
        String arg = i.getStringExtra(WidgetIntents.EXTRA_ARG);
        switch (act) {
            case WidgetIntents.ACT_FOCUS:
                startFocus(parse(arg, 25));
                break;
            case WidgetIntents.ACT_OPEN:
                open(arg);
                break;
            default:
                break;
        }
        WidgetUpdater.updateAll(this);
    }

    private static int parse(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void open(String target) {
        startActivity(Intents.open(this, target == null || target.isEmpty() ? "home" : target));
    }

    /** Starts focus straight from the widget if the guard is on, otherwise opens the Focus tile to set it up. */
    private void startFocus(int minutes) {
        if (FocusController.guardEnabled(this)) {
            FocusController.start(this, minutes, "");
            startActivity(Intents.open(this, "home"));
        } else {
            open("focus");
        }
    }
}
