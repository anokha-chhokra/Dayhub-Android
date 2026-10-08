package app.dayhub;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

/** The only activity in the app. Each feature lives in its own class and is hooked in here. */
public class MainActivity extends Activity {
    private FrameLayout content;
    private Overlays overlays;
    private ShellScreen shell;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        content = EdgeToEdgeShell.install(this);
        overlays = new Overlays(this);
        shell = new ShellScreen(this, overlays);
        content.addView(shell);
    }

    /** The root container that screens are added to. */
    public FrameLayout content() {
        return content;
    }

    /** Back closes an open sheet first, then returns to Home, and only then leaves the app. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!overlays.handleBack() && !shell.handleBack()) super.onBackPressed();
    }
}
