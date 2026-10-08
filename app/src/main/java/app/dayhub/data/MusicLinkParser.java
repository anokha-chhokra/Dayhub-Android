package app.dayhub.data;

import static app.dayhub.data.DataError.bad;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a pasted YouTube link into a safe embed link, ported from the web app's music.js.
 * Only YouTube is accepted, and only ids that match strict patterns.
 */
public final class MusicLinkParser {
    private static final Pattern VIDEO = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern LIST = Pattern.compile("[A-Za-z0-9_-]{10,64}");
    private static final List<String> HOSTS =
            Arrays.asList("youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com");

    private MusicLinkParser() {}

    /** A link that passed the checks. */
    public static final class Parsed {
        public final String provider = "youtube";
        public final String kind; // video or playlist
        public final String url;
        public final String embedUrl;
        public final String defaultLabel;

        Parsed(String kind, String url, String embedUrl, String defaultLabel) {
            this.kind = kind;
            this.url = url;
            this.embedUrl = embedUrl;
            this.defaultLabel = defaultLabel;
        }
    }

    public static Parsed parse(Object input) {
        if (!(input instanceof String) || ((String) input).trim().isEmpty()) {
            throw bad("Paste a YouTube link");
        }
        URI uri;
        try {
            uri = new URI(((String) input).trim());
        } catch (URISyntaxException e) {
            throw bad("That does not look like a link");
        }
        String scheme = uri.getScheme();
        if (scheme == null) throw bad("That does not look like a link");
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) throw bad("That does not look like a web link");
        if (uri.getHost() == null) throw bad("That does not look like a link");

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        String[] parts = segments(uri.getRawPath());

        if (!host.equals("youtu.be") && !HOSTS.contains(host)) throw bad("Only YouTube links are supported");

        String video = null;
        String list = query(uri.getRawQuery(), "list");
        if (host.equals("youtu.be")) {
            video = parts.length > 0 ? parts[0] : null;
        } else if (parts.length > 0 && parts[0].equals("watch")) {
            video = query(uri.getRawQuery(), "v");
        } else if (parts.length > 0 && Arrays.asList("embed", "shorts", "live", "v").contains(parts[0])) {
            video = parts.length > 1 ? parts[1] : null;
        } else if (parts.length > 0 && parts[0].equals("playlist")) {
            video = null;
        } else {
            throw bad("That YouTube link is not a video or playlist");
        }

        if (video != null && !VIDEO.matcher(video).matches()) video = null;
        if (list != null && !LIST.matcher(list).matches()) list = null;
        if (video == null && list == null) throw bad("Could not find a video or playlist in that YouTube link");
        return build(video, list);
    }

    private static Parsed build(String video, String list) {
        String base = "https://www.youtube-nocookie.com/embed/";
        String embed = video != null
                ? base + video + (list != null ? "?list=" + list : "")
                : base + "videoseries?list=" + list;
        String url = video != null
                ? "https://www.youtube.com/watch?v=" + video + (list != null ? "&list=" + list : "")
                : "https://www.youtube.com/playlist?list=" + list;
        String label = video != null
                ? (list != null ? "YouTube video + playlist" : "YouTube video")
                : "YouTube playlist";
        return new Parsed(video != null ? "video" : "playlist", url, embed, label);
    }

    private static String[] segments(String rawPath) {
        if (rawPath == null) return new String[0];
        return Arrays.stream(rawPath.split("/")).filter(p -> !p.isEmpty()).toArray(String[]::new);
    }

    /** The first value of a query parameter, decoded, or null. */
    private static String query(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (decode(key).equals(name)) return eq < 0 ? "" : decode(pair.substring(eq + 1));
        }
        return null;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}
