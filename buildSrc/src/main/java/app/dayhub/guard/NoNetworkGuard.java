package app.dayhub.guard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Feature 30: the guard that keeps Day Hub offline. It fails the build if network code, WebView code, a network
 * permission or a networking library appears anywhere in the app. Day Hub reads and writes only its own files; it
 * has no INTERNET permission and no WebView, and this makes sure that stays true as features are added.
 *
 * It is plain Java with no Gradle or Android code, so Gradle uses it (see app/build.gradle) and the same class is
 * run and tested on its own (verify/run.sh). Comments are ignored, so a comment may say "no WebView".
 */
public final class NoNetworkGuard {
    private NoNetworkGuard() { }

    /** One thing that should not be there: where it is, which rule it breaks, and the offending text. */
    public static final class Violation {
        public final String file;
        public final int line;
        public final String rule;
        public final String text;

        Violation(String file, int line, String rule, String text) {
            this.file = file;
            this.line = line;
            this.rule = rule;
            this.text = text.trim();
        }

        @Override
        public String toString() {
            return file + ":" + line + ": " + rule + ": " + text;
        }
    }

    private static final class Rule {
        final String name;
        final Pattern pattern;

        Rule(String name, String regex) {
            this.name = name;
            this.pattern = Pattern.compile(regex);
        }
    }

    /** Permissions that give an app a network (or Wi-Fi) it has no use for. */
    private static final Rule[] MANIFEST_RULES = {
        new Rule("network permission", "android\\.permission\\.(INTERNET|ACCESS_NETWORK_STATE|CHANGE_NETWORK_STATE|ACCESS_WIFI_STATE"
                + "|CHANGE_WIFI_STATE|NEARBY_WIFI_DEVICES)"),
        new Rule("network setting in the manifest", "usesCleartextTraffic|networkSecurityConfig"),
    };

    private static final Rule[] CODE_RULES = {
        new Rule("WebView", "\\b(WebView\\w*|WebChromeClient|WebSettings|WebResource\\w+|JavascriptInterface|CookieManager"
                + "|WebStorage|ServiceWorker\\w+|WebMessage\\w*)\\b"),
        new Rule("network connection", "\\w*URLConnection\\b|\\.openConnection\\s*\\(|\\bnew\\s+URL\\s*\\(|java\\.net\\.http\\b"
                + "|\\bHttpClient\\b|\\bHttpRequest\\b"),
        new Rule("socket", "\\b(Socket|ServerSocket|DatagramSocket|MulticastSocket|SocketChannel|ServerSocketChannel|DatagramChannel"
                + "|InetAddress|InetSocketAddress|SSLSocket\\w*|SSLContext)\\b|javax\\.net\\.ssl"),
        new Rule("networking library", "okhttp\\d*|retrofit\\d*|com\\.android\\.volley|io\\.ktor|org\\.apache\\.http|android\\.net\\.http"
                + "|com\\.squareup\\.|io\\.grpc|org\\.chromium|org\\.chromium\\.net"),
        new Rule("Android network API", "\\bConnectivityManager\\b|\\bNetworkRequest\\b|\\bNetworkCapabilities\\b|\\bDownloadManager\\b"
                + "|\\bVpnService\\b|android\\.net\\.wifi|\\bWifiManager\\b|\\bNsdManager\\b"),
    };

    /** Library names (group:name) that mean the app is reaching the network, or showing web pages. */
    private static final Pattern NETWORK_LIBRARY = Pattern.compile(
        "okhttp|retrofit|volley|ktor|httpclient|httpcomponents|glide|picasso|coil|fresco|firebase|play-services|apollo|androidx\\.webkit"
        + "|androidx\\.browser|cronet|grpc|crashlytics|analytics|admob|sentry|segment|mixpanel|amplitude|branch\\.io|onesignal|unity3d|appsflyer");

    private static final String[] SOURCE_EXTENSIONS = {".java", ".kt"};

    /**
     * Looks through the project for anything that breaks the rules.
     *
     * @param projectRoot  the folder that holds settings.gradle and app/
     * @param dependencies the app's declared libraries as "group:name:version" (may be empty or null)
     */
    public static List<Violation> scan(Path projectRoot, Collection<String> dependencies) {
        List<Violation> out = new ArrayList<>();
        Path app = projectRoot.resolve("app");
        Path src = app.resolve("src");
        if (Files.isDirectory(src)) {
            for (Path set : children(src)) {
                String name = set.getFileName().toString();
                if (!Files.isDirectory(set) || name.equals("test") || name.equals("androidTest")) continue;
                for (Path file : files(set)) scanSourceFile(projectRoot, file, out);
            }
        }
        for (Path gradle : buildFiles(projectRoot)) scanGradle(projectRoot, gradle, out);

        Path libs = app.resolve("libs");
        if (Files.isDirectory(libs)) {
            for (Path lib : files(libs)) out.add(new Violation(rel(projectRoot, lib), 1, "bundled library", "a library file is bundled; Day Hub uses none"));
        }
        if (dependencies != null) {
            for (String d : dependencies) {
                if (d != null && NETWORK_LIBRARY.matcher(d.toLowerCase()).find()) {
                    out.add(new Violation("app/build.gradle", 1, "networking library", "dependency " + d));
                }
            }
        }
        return out;
    }

    private static void scanSourceFile(Path root, Path file, List<Violation> out) {
        String name = file.getFileName().toString();
        String rel = rel(root, file);
        boolean isCode = false;
        for (String ext : SOURCE_EXTENSIONS) if (name.endsWith(ext)) isCode = true;
        boolean isXml = name.endsWith(".xml");
        if (!isCode && !isXml) return;

        if (isXml && name.toLowerCase().startsWith("network_security_config")) {
            out.add(new Violation(rel, 1, "network setting in the manifest", "a network security configuration file"));
        }
        String text = read(file);
        if (text == null) return;
        String clean = isCode ? stripCodeComments(text) : stripXmlComments(text);
        String[] lines = clean.split("\n", -1);
        boolean manifest = name.equals("AndroidManifest.xml");
        for (int i = 0; i < lines.length; i++) {
            if (manifest) check(MANIFEST_RULES, rel, i + 1, lines[i], out);
            else if (isCode) check(CODE_RULES, rel, i + 1, lines[i], out);
            else if (isXml) checkXml(rel, i + 1, lines[i], out);
        }
    }

    /** Layouts and other resources: a WebView element, or a view named after one of the network classes. */
    private static void checkXml(String rel, int line, String text, List<Violation> out) {
        Matcher m = Pattern.compile("<\\s*(?:[\\w.]*\\.)?(WebView\\w*)\\b|android\\.webkit\\.WebView|androidx\\.webkit").matcher(text);
        if (m.find()) out.add(new Violation(rel, line, "WebView", text));
    }

    private static void check(Rule[] rules, String rel, int line, String text, List<Violation> out) {
        for (Rule r : rules) {
            if (r.pattern.matcher(text).find()) out.add(new Violation(rel, line, r.name, text));
        }
    }

    private static void scanGradle(Path root, Path file, List<Violation> out) {
        String text = read(file);
        if (text == null) return;
        String[] lines = stripCodeComments(text).split("\n", -1);
        String rel = rel(root, file);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            // Only dependency lines: a quoted "group:name" or a project(...) / files(...) notation.
            boolean notation = l.matches(".*['\"][\\w.\\-]+:[\\w.\\-]+(:[\\w.\\-+]+)?['\"].*");
            if (notation && NETWORK_LIBRARY.matcher(l.toLowerCase()).find()) {
                out.add(new Violation(rel, i + 1, "networking library", l));
            }
            if (l.matches(".*\\bfileTree\\s*\\(.*") || l.matches(".*\\bfiles\\s*\\(.*['\"].*\\.(jar|aar)['\"].*")) {
                out.add(new Violation(rel, i + 1, "bundled library", l));
            }
        }
    }

    // ---------- comments ----------

    /** Removes // and block comments but keeps line numbers, and leaves text inside string literals alone. */
    static String stripCodeComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c && s.charAt(j) != '\n') {
                    if (s.charAt(j) == '\\') j++;
                    j++;
                }
                j = Math.min(n - 1, j);
                b.append(s, i, j + 1);
                i = j + 1;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) if (s.charAt(k) == '\n') b.append('\n');
                i = end;
            } else {
                b.append(c);
                i++;
            }
        }
        return b.toString();
    }

    static String stripXmlComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int start = s.indexOf("<!--", i);
            if (start < 0) {
                b.append(s, i, s.length());
                break;
            }
            b.append(s, i, start);
            int end = s.indexOf("-->", start + 4);
            end = end < 0 ? s.length() : end + 3;
            for (int k = start; k < end; k++) if (s.charAt(k) == '\n') b.append('\n');
            i = end;
        }
        return b.toString();
    }

    // ---------- files ----------

    private static List<Path> children(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> files(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** build.gradle files of the project itself (not buildSrc, build output or the verification folder). */
    private static List<Path> buildFiles(Path root) {
        List<Path> out = new ArrayList<>();
        for (Path f : files(root)) {
            String rel = rel(root, f);
            String name = f.getFileName().toString();
            boolean gradle = name.equals("build.gradle") || name.equals("build.gradle.kts") || name.equals("settings.gradle")
                    || name.equals("settings.gradle.kts");
            boolean skipped = rel.startsWith("buildSrc/") || rel.startsWith("build/") || rel.contains("/build/") || rel.startsWith(".gradle/")
                    || rel.startsWith("verify/") || rel.startsWith(".git/");
            if (gradle && !skipped) out.add(f);
        }
        return out;
    }

    private static String read(Path f) {
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String rel(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    /** The violations as text, one per line, for a build failure message. */
    public static String report(List<Violation> violations) {
        StringBuilder sb = new StringBuilder();
        for (Violation v : violations) sb.append("  ").append(v).append('\n');
        return sb.toString();
    }

    /** Runs the guard on its own: {@code NoNetworkGuard <project folder>}. Exits with 1 if anything is found. */
    public static void main(String[] args) {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        List<Violation> found = scan(root, new ArrayList<String>());
        if (found.isEmpty()) {
            System.out.println("Day Hub is offline: no network code, WebView, network permission or networking library in " + root);
            return;
        }
        System.out.println("Day Hub must stay offline, but " + found.size() + " problem(s) were found:\n" + report(found));
        System.exit(1);
    }
}
