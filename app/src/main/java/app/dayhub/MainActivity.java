package app.dayhub;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

/** The only activity in the app. Each feature lives in its own class and is hooked in here. */
public class MainActivity extends Activity {
    private FrameLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        content = EdgeToEdgeShell.install(this);
    }

    /** The root container that screens are added to. */
    public FrameLayout content() {
        return content;
    }
}
