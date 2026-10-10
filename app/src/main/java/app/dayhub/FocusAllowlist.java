package app.dayhub;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.dayhub.data.FocusPolicy;

/**
 * Feature 25: the apps found on this particular phone that a focus timer must allow: the keyboards, the
 * messages app and the dialer. Also finds the launchers for Messages and WhatsApp for the buttons on the tile.
 */
final class FocusAllowlist {
    private static Set<String> cached = new HashSet<>();
    private static long cachedAt;

    private FocusAllowlist() { }

    /** Looked up at most every 30 seconds: the guard asks on every window change. */
    static synchronized Set<String> onThisPhone(Context c) {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 30_000 && !cached.isEmpty()) return cached;
        Set<String> out = new HashSet<>();
        try {
            InputMethodManager imm = c.getSystemService(InputMethodManager.class);
            if (imm != null) for (InputMethodInfo i : imm.getEnabledInputMethodList()) out.add(i.getPackageName());
        } catch (RuntimeException ignored) {
            // keyboards are optional extras
        }
        try {
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            if (tm != null) {
                String dialer = tm.getDefaultDialerPackage();
                if (dialer != null) out.add(dialer);
            }
        } catch (RuntimeException ignored) {
            // no telephony
        }
        out.addAll(messagingApps(c));
        cached = out;
        cachedAt = now;
        return out;
    }

    /**
     * The phone's text-messaging app: the one set as default for SMS, plus the messaging apps that came with the
     * phone. A messaging app the person installed (Telegram, a second SMS app) is not let in unless it is the default.
     */
    static Set<String> messagingApps(Context c) {
        Set<String> out = new HashSet<>();
        PackageManager pm = c.getPackageManager();
        try {
            String def = Telephony.Sms.getDefaultSmsPackage(c);
            if (def != null) out.add(def);
        } catch (RuntimeException ignored) {
            // no telephony
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING);
        Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
        for (Intent i : new Intent[] {main, sms}) {
            try {
                List<ResolveInfo> found = pm.queryIntentActivities(i, 0);
                for (ResolveInfo r : found) {
                    if (r.activityInfo == null || r.activityInfo.applicationInfo == null) continue;
                    int flags = r.activityInfo.applicationInfo.flags;
                    if ((flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                        out.add(r.activityInfo.packageName);
                    }
                }
            } catch (RuntimeException ignored) {
                // package visibility: whatever could be seen is enough
            }
        }
        return out;
    }

    /** The Intent that opens the person's messages app, or null if there is none. */
    static Intent messagesLauncher(Context c) {
        PackageManager pm = c.getPackageManager();
        String def = null;
        try {
            def = Telephony.Sms.getDefaultSmsPackage(c);
        } catch (RuntimeException ignored) {
            // no telephony
        }
        if (def != null) {
            Intent i = pm.getLaunchIntentForPackage(def);
            if (i != null) return i;
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return main.resolveActivity(pm) != null ? main : null;
    }

    static Intent whatsAppLauncher(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String pkg : FocusPolicy.WHATSAPP) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        return null;
    }
}
