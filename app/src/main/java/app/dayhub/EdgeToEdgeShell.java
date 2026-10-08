package app.dayhub;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Feature 1: full-screen, edge-to-edge page. Draws behind transparent system bars with dark bar
 * icons on the cream paper, and keeps the returned container clear of the status bar, navigation
 * bar and display cutout so screens added to it never handle insets themselves.
 */
public final class EdgeToEdgeShell {
    private EdgeToEdgeShell() {}

    /** Makes the activity edge-to-edge, sets the root view, and returns the container for screens. */
    public static FrameLayout install(Activity activity) {
        goEdgeToEdge(activity.getWindow());

        FrameLayout content = new FrameLayout(activity);
        content.setBackgroundResource(R.color.paper);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] in = insetsOf(insets);
            v.setPadding(in[0], in[1], in[2], in[3]);
            return insets;
        });
        activity.setContentView(content);
        return content;
    }

    /** The system bar, display cutout and on-screen keyboard insets as {left, top, right, bottom} in pixels. */
    public static int[] insetsOf(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            return new int[] {bars.left, bars.top, bars.right, bars.bottom};
        }
        return new int[] {
                insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()};
    }

    private static void goEdgeToEdge(Window window) {
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                // Dark icons on the cream paper.
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(light, light);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }
}
