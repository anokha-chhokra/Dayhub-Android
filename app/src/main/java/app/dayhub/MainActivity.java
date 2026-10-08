package app.dayhub;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

/** A blank full-screen page. The app is built up from here. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.color.paper);
        setContentView(root);
    }
}
