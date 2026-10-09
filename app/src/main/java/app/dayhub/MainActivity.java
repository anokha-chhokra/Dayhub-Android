package app.dayhub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;

import app.dayhub.data.DayHubData;

import java.util.function.BooleanSupplier;

/** The only activity in the app. Each feature lives in its own class and is hooked in here. */
public class MainActivity extends Activity {
    private FrameLayout content;
    private Overlays overlays;
    private ActivityResults results;
    private ShellScreen shell;
    private DayHubData data;
    private BooleanSupplier backHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        content = EdgeToEdgeShell.install(this);
        overlays = new Overlays(this);
        results = new ActivityResults(this);
        // Open the saved data; if it is unusable the damaged-data screen is shown instead.
        DataGate.open(this, content, overlays, results, opened -> {
            data = opened;
            DataTransfer transfer = new DataTransfer(this, overlays, results, opened);
            // First run: ask a few questions before showing the app.
            SetupWizard.showIfNeeded(this, content, transfer, opened, () -> {
                shell = new ShellScreen(this, opened, overlays, results, transfer);
                content.addView(shell);
            });
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (!results.dispatchPermission(requestCode, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /** Coming back to the app: times and counts on Home may have moved on. */
    @Override
    protected void onResume() {
        super.onResume();
        if (shell != null) shell.refresh();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (!results.dispatch(requestCode, resultCode, intent)) {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    /** A screen that wants the back button for itself (e.g. a wizard going to its previous step); null clears it. */
    public void setBackHandler(BooleanSupplier handler) {
        backHandler = handler;
    }

    /** Back closes an open sheet first, then a screen's own step, then returns to Home, and only then leaves the app. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (overlays.handleBack()) return;
        if (backHandler != null && backHandler.getAsBoolean()) return;
        if (!(shell != null && shell.handleBack())) super.onBackPressed();
    }
}
