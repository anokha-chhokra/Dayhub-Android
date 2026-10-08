package app.dayhub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;

import app.dayhub.data.DayHubData;

/** The only activity in the app. Each feature lives in its own class and is hooked in here. */
public class MainActivity extends Activity {
    private FrameLayout content;
    private Overlays overlays;
    private ActivityResults results;
    private ShellScreen shell;
    private DayHubData data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        content = EdgeToEdgeShell.install(this);
        overlays = new Overlays(this);
        results = new ActivityResults(this);
        // Open the saved data; if it is unusable the damaged-data screen is shown instead.
        DataGate.open(this, content, overlays, results, opened -> {
            data = opened;
            shell = new ShellScreen(this, overlays);
            content.addView(shell);
        });
    }

    /** The root container that screens are added to. */
    public FrameLayout content() {
        return content;
    }

    /** The opened data, or null while the damaged-data screen is showing. */
    public DayHubData data() {
        return data;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (!results.dispatch(requestCode, resultCode, intent)) {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    /** Back closes an open sheet first, then returns to Home, and only then leaves the app. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!overlays.handleBack() && !(shell != null && shell.handleBack())) super.onBackPressed();
    }
}
