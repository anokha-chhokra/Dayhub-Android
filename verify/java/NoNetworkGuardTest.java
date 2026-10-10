import app.dayhub.guard.NoNetworkGuard;
import app.dayhub.guard.NoNetworkGuard.Violation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Checks the no-network guard itself: the real project passes, a clean sample passes, and each kind of network,
 * WebView or library code, planted in a sample project one at a time, is caught under the right rule. Comments
 * and harmless look-alikes (java.net.URI, the word "internet" in text) are not.
 */
public class NoNetworkGuardTest {
    static int checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            System.out.println("FAILED: " + what);
            System.exit(1);
        }
    }

    static void write(Path root, String rel, String text) throws IOException {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.write(f, text.getBytes(StandardCharsets.UTF_8));
    }

    static final String CLEAN_MANIFEST = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "  <!-- No android.permission.INTERNET on purpose; no WebView either. -->\n"
            + "  <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n</manifest>\n";
    static final String CLEAN_CODE = "package app.dayhub;\n"
            + "import java.net.URI;\nimport java.net.URLDecoder;\n"
            + "/** Text only: no WebView, no HttpURLConnection, no socket. The word internet is fine. */\n"
            + "class Ok { // WebView in a comment is fine\n"
            + "  String s = \"rent|bill|internet|wifi\";\n"
            + "  /* new URL(\"x\").openConnection() */\n"
            + "  void f() { java.net.URI u = java.net.URI.create(\"https://example.com\"); }\n}\n";
    static final String CLEAN_GRADLE = "plugins { id 'com.android.application' }\n"
            + "// implementation 'com.squareup.okhttp3:okhttp:4.12.0' is only a comment\n"
            + "dependencies {\n    // Deliberately empty\n}\n";

    static Path sample() throws IOException {
        Path root = Files.createTempDirectory("noNetwork");
        write(root, "app/src/main/AndroidManifest.xml", CLEAN_MANIFEST);
        write(root, "app/src/main/java/app/dayhub/Ok.java", CLEAN_CODE);
        write(root, "app/src/main/res/layout/a.xml", "<LinearLayout><TextView android:text=\"internet\" /></LinearLayout>\n");
        write(root, "app/build.gradle", CLEAN_GRADLE);
        write(root, "settings.gradle", "rootProject.name = 'x'\ninclude ':app'\n");
        return root;
    }

    static void delete(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** Plants one thing and expects exactly that rule to be reported for exactly that file. */
    static void planted(String what, String rel, String content, String rule) throws IOException {
        Path root = sample();
        try {
            write(root, rel, content);
            List<Violation> found = NoNetworkGuard.scan(root, Collections.<String>emptyList());
            boolean hit = false;
            for (Violation v : found) if (v.rule.equals(rule) && v.file.equals(rel)) hit = true;
            check(hit, what + " should be caught as '" + rule + "' in " + rel + " but got " + found);
        } finally {
            delete(root);
        }
    }

    public static void main(String[] a) throws Exception {
        // the real project
        if (a.length > 0) {
            List<Violation> real = NoNetworkGuard.scan(Path.of(a[0]).toAbsolutePath().normalize(), Collections.<String>emptyList());
            check(real.isEmpty(), "the project is offline, but found:\n" + NoNetworkGuard.report(real));
        }

        // a clean sample passes, whatever the comments say
        Path clean = sample();
        try {
            List<Violation> none = NoNetworkGuard.scan(clean, Collections.<String>emptyList());
            check(none.isEmpty(), "a clean sample passes, but found:\n" + NoNetworkGuard.report(none));
        } finally {
            delete(clean);
        }

        String manifest = "app/src/main/AndroidManifest.xml";
        String code = "app/src/main/java/app/dayhub/Bad.java";
        for (String perm : new String[] {"INTERNET", "ACCESS_NETWORK_STATE", "CHANGE_NETWORK_STATE", "ACCESS_WIFI_STATE", "CHANGE_WIFI_STATE", "NEARBY_WIFI_DEVICES"}) {
            planted(perm + " permission", manifest, CLEAN_MANIFEST.replace("</manifest>",
                    "  <uses-permission android:name=\"android.permission." + perm + "\" />\n</manifest>"), "network permission");
        }
        planted("cleartext traffic", manifest, CLEAN_MANIFEST.replace("<manifest ", "<manifest android:usesCleartextTraffic=\"true\" "),
                "network setting in the manifest");
        planted("a network security config file", "app/src/main/res/xml/network_security_config.xml", "<network-security-config/>\n",
                "network setting in the manifest");

        for (String webView : new String[] {"android.webkit.WebView w;", "WebView w = null;", "new WebViewClient() {};", "WebChromeClient c;",
                "WebSettings s;", "CookieManager.getInstance();", "@JavascriptInterface void f() {}", "WebResourceRequest r;"}) {
            planted(webView, code, "package app.dayhub;\nclass Bad { " + webView + " }\n", "WebView");
        }
        planted("a WebView in a layout", "app/src/main/res/layout/web.xml", "<FrameLayout><WebView android:id=\"@+id/w\" /></FrameLayout>\n", "WebView");
        planted("a WebView by full name in a layout", "app/src/main/res/layout/web2.xml", "<android.webkit.WebView />\n", "WebView");

        for (String net : new String[] {"HttpURLConnection c;", "HttpsURLConnection c;", "java.net.URLConnection c;", "Object o = new URL(\"http://x\");",
                "u.openConnection();", "java.net.http.HttpClient c;", "HttpClient c;"}) {
            planted(net, code, "package app.dayhub;\nclass Bad { void f() { " + net + " } }\n", "network connection");
        }
        for (String sock : new String[] {"Socket s;", "ServerSocket s;", "DatagramSocket s;", "InetAddress a;", "SSLSocketFactory f;", "javax.net.ssl.SSLContext c;"}) {
            planted(sock, code, "package app.dayhub;\nclass Bad { " + sock + " }\n", "socket");
        }
        for (String lib : new String[] {"import okhttp3.OkHttpClient;", "import retrofit2.Retrofit;", "import com.android.volley.RequestQueue;",
                "import io.ktor.client.HttpClient;", "import org.apache.http.client.HttpClient;", "import android.net.http.HttpResponseCache;"}) {
            planted(lib, code, "package app.dayhub;\n" + lib + "\nclass Bad { }\n", "networking library");
        }
        for (String api : new String[] {"ConnectivityManager m;", "NetworkRequest r;", "DownloadManager d;", "WifiManager w;"}) {
            planted(api, code, "package app.dayhub;\nclass Bad { " + api + " }\n", "Android network API");
        }
        // Kotlin is scanned too
        planted("Kotlin code", "app/src/main/java/app/dayhub/Bad.kt", "package app.dayhub\nval w = WebView(null)\n", "WebView");

        // libraries in the Gradle files, and bundled ones
        planted("okhttp in build.gradle", "app/build.gradle", CLEAN_GRADLE.replace("// Deliberately empty",
                "implementation 'com.squareup.okhttp3:okhttp:4.12.0'"), "networking library");
        planted("firebase in build.gradle", "app/build.gradle", CLEAN_GRADLE.replace("// Deliberately empty",
                "implementation(\"com.google.firebase:firebase-analytics:21.0.0\")"), "networking library");
        planted("a bundled jar", "app/libs/thing.jar", "x", "bundled library");
        planted("a fileTree of libraries", "app/build.gradle", CLEAN_GRADLE.replace("// Deliberately empty", "implementation fileTree(dir: 'libs', include: ['*.jar'])"),
                "bundled library");

        // dependencies handed in by Gradle
        Path root = sample();
        try {
            List<Violation> deps = NoNetworkGuard.scan(root, Arrays.asList("androidx.core:core:1.13.0", "com.squareup.retrofit2:retrofit:2.9.0"));
            check(deps.size() == 1 && deps.get(0).rule.equals("networking library") && deps.get(0).text.contains("retrofit"),
                    "a networking library handed in by Gradle is caught, and an ordinary one is not: " + deps);
            check(NoNetworkGuard.scan(root, Arrays.asList("androidx.core:core:1.13.0")).isEmpty(), "an ordinary library passes");
        } finally {
            delete(root);
        }

        // harmless look-alikes and test code are left alone
        Path odd = sample();
        try {
            write(odd, "app/src/main/java/app/dayhub/Fine.java", "package app.dayhub;\nclass Fine { String a = \"MusicLinkParser\"; java.net.URI u; int webViewCount; }\n");
            write(odd, "app/src/test/java/app/dayhub/T.java", "package app.dayhub;\nclass T { java.net.HttpURLConnection c; }\n");
            write(odd, "buildSrc/build.gradle", "dependencies { implementation 'com.squareup.okhttp3:okhttp:4.12.0' }\n");
            write(odd, "app/build/generated/x/build.gradle", "implementation 'com.squareup.okhttp3:okhttp:4.12.0'\n");
            List<Violation> none = NoNetworkGuard.scan(odd, Collections.<String>emptyList());
            check(none.isEmpty(), "look-alikes, test code, buildSrc and build output are not flagged: " + none);
        } finally {
            delete(odd);
        }

        // line numbers point at the right line
        Path numbered = sample();
        try {
            write(numbered, "app/src/main/java/app/dayhub/N.java", "package app.dayhub;\n/* a\n b\n c */\n\nclass N { WebView w; }\n");
            List<Violation> v = NoNetworkGuard.scan(numbered, Collections.<String>emptyList());
            check(v.size() == 1 && v.get(0).line == 6, "line numbers survive stripped comments: " + v);
        } finally {
            delete(numbered);
        }

        System.out.println("NO-NETWORK GUARD OK (" + checks + " checks)");
    }
}
