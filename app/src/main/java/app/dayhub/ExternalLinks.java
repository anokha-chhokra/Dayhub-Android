package app.dayhub;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

/**
 * Opens a web link in another app (the YouTube app or the browser). Day Hub itself never goes
 * online, which is why it has no internet permission.
 */
public final class ExternalLinks {
    private ExternalLinks() {}

    /** Returns false if no app could open the link. */
    public static boolean open(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
