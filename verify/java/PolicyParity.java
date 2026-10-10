import java.util.*;

/** Compares app.dayhub.data.FocusPolicy with the web app's own Android FocusPolicy (app.dayhub.core). */
public class PolicyParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  new: " + got + "\n  web: " + want);
            System.exit(1);
        }
    }

    public static void main(String[] a) {
        eq(app.dayhub.data.FocusPolicy.SELF, app.dayhub.core.FocusPolicy.SELF, "SELF");
        eq(Arrays.asList(app.dayhub.data.FocusPolicy.WHATSAPP), Arrays.asList(app.dayhub.core.FocusPolicy.WHATSAPP), "WHATSAPP");
        eq(app.dayhub.data.FocusPolicy.MESSAGES, app.dayhub.core.FocusPolicy.MESSAGES, "MESSAGES");
        eq(app.dayhub.data.FocusPolicy.PHONE, app.dayhub.core.FocusPolicy.PHONE, "PHONE");
        eq(app.dayhub.data.FocusPolicy.SYSTEM, app.dayhub.core.FocusPolicy.SYSTEM, "SYSTEM");

        List<String> names = new ArrayList<>();
        names.add(null);
        names.add("");
        names.addAll(Arrays.asList(app.dayhub.core.FocusPolicy.WHATSAPP));
        names.add(app.dayhub.core.FocusPolicy.SELF);
        names.addAll(app.dayhub.core.FocusPolicy.MESSAGES);
        names.addAll(app.dayhub.core.FocusPolicy.PHONE);
        names.addAll(app.dayhub.core.FocusPolicy.SYSTEM);
        List<String> known = new ArrayList<>(names.subList(2, names.size()));
        for (String k : known) {
            names.add(k + ".fake");
            names.add("x." + k);
            names.add(k.toUpperCase(Locale.ROOT));
            names.add(" " + k);
            names.add(k + " ");
            names.add(k.substring(0, k.length() - 1));
        }
        for (String s : new String[] {"com.android.chrome", "com.google.android.youtube", "com.instagram.android", "com.android.settings",
                "com.google.android.apps.nexuslauncher", "com.sec.android.app.launcher", "com.whatsapp.fake", "app.dayhub.evil",
                "com.android.vending", "com.vendor.keyboard", "com.vendor.sms", "com.google.android.inputmethod.latin"}) names.add(s);
        Random r = new Random(11);
        String chars = "abcdefghijklmnopqrstuvwxyz.";
        for (int i = 0; i < 3000; i++) {
            StringBuilder b = new StringBuilder();
            for (int k = 0, n = 1 + r.nextInt(30); k < n; k++) b.append(chars.charAt(r.nextInt(chars.length())));
            names.add(b.toString());
        }

        List<Set<String>> extras = new ArrayList<>();
        extras.add(null);
        extras.add(new HashSet<>());
        extras.add(new HashSet<>(Arrays.asList("com.vendor.keyboard", "com.vendor.sms")));
        extras.add(new HashSet<>(Arrays.asList("com.google.android.inputmethod.latin", "com.android.chrome")));
        for (String n : names) {
            for (Set<String> extra : extras) {
                eq(app.dayhub.data.FocusPolicy.isAllowed(n, extra), app.dayhub.core.FocusPolicy.isAllowed(n, extra), "isAllowed(" + n + ", " + extra + ")");
            }
            eq(app.dayhub.data.FocusPolicy.isWhatsApp(n), app.dayhub.core.FocusPolicy.isWhatsApp(n), "isWhatsApp(" + n + ")");
        }
        System.out.println("IDENTICAL to the web app: " + names.size() + " package names x " + extras.size() + " run-time sets (" + checks + " comparisons)");
    }
}
