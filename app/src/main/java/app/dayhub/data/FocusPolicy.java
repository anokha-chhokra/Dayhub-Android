package app.dayhub.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Feature 25: which apps may be in front while a focus timer runs: Day Hub, text messages and WhatsApp,
 * plus what Android needs around them (the keyboard, permission and file dialogs, the status bar) and the
 * phone app, so an incoming call can always be answered. Everything else is sent back to Day Hub.
 * These are the web app's Android rules, unchanged.
 */
public final class FocusPolicy {
    private FocusPolicy() { }

    public static final String SELF = "app.dayhub";
    public static final String[] WHATSAPP = {"com.whatsapp", "com.whatsapp.w4b"};

    private static Set<String> set(String... names) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
    }

    /** Text message apps by maker. The phone's default one is added at run time as well. */
    public static final Set<String> MESSAGES = set(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.android.messaging", "com.oneplus.mms", "com.coloros.mms", "com.miui.mms", "com.vivo.mms");

    /** The phone app and the call screen: incoming calls must always work. Also emergency alerts. */
    public static final Set<String> PHONE = set(
            "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
            "com.android.incallui", "com.samsung.android.incallui", "com.android.server.telecom",
            "com.android.phone", "com.android.emergency", "com.android.cellbroadcastreceiver",
            "com.google.android.cellbroadcastreceiver", "com.android.cellbroadcastservice");

    /** Parts of Android that appear on top of an allowed app and must not be sent back. */
    public static final Set<String> SYSTEM = set(
            "android", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.documentsui", "com.google.android.documentsui", "com.android.intentresolver",
            "com.android.providers.media.module", "com.google.android.providers.media.module",
            "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts",
            "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera");

    private static final Set<String> FIXED = merge(set(SELF), set(WHATSAPP), MESSAGES, PHONE, SYSTEM);

    @SafeVarargs
    private static Set<String> merge(Set<String>... parts) {
        Set<String> all = new HashSet<>();
        for (Set<String> p : parts) all.addAll(p);
        return Collections.unmodifiableSet(all);
    }

    /**
     * @param pkg   the app that just came to the front
     * @param extra apps found on this phone at run time (default messages app, keyboards, dialer); may be null
     */
    public static boolean isAllowed(String pkg, Set<String> extra) {
        if (pkg == null || pkg.isEmpty()) return true; // nothing to judge: never block on a guess
        return FIXED.contains(pkg) || (extra != null && extra.contains(pkg));
    }

    public static boolean isWhatsApp(String pkg) {
        for (String w : WHATSAPP) if (w.equals(pkg)) return true;
        return false;
    }
}
