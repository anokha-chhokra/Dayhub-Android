package app.dayhub.data;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * Writes JSON text from plain maps, lists and values, keeping keys in the order they were added and
 * formatting exactly like JavaScript's JSON.stringify, so a file written here reads the same as one
 * written by the web app. Maps must be ordered (LinkedHashMap); null and JSONObject.NULL write null.
 */
public final class JsonText {
    private JsonText() {}

    /** One line, no spaces: JSON.stringify(value). */
    public static String compact(Object tree) {
        StringBuilder sb = new StringBuilder();
        write(sb, tree, -1, 0);
        return sb.toString();
    }

    /** Two-space indent: JSON.stringify(value, null, 2). */
    public static String pretty(Object tree) {
        StringBuilder sb = new StringBuilder();
        write(sb, tree, 2, 0);
        return sb.toString();
    }

    private static void newline(StringBuilder sb, int indent, int depth) {
        sb.append('\n');
        for (int i = 0; i < indent * depth; i++) sb.append(' ');
    }

    private static void write(StringBuilder sb, Object v, int indent, int depth) {
        if (v == null || v == JSONObject.NULL) {
            sb.append("null");
        } else if (v instanceof String) {
            quote(sb, (String) v);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Short
                || v instanceof Byte) {
            sb.append(v);
        } else if (v instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) v;
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                if (indent > 0) newline(sb, indent, depth + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(indent > 0 ? ": " : ":");
                write(sb, e.getValue(), indent, depth + 1);
            }
            if (indent > 0) newline(sb, indent, depth);
            sb.append('}');
        } else if (v instanceof List) {
            List<?> list = (List<?>) v;
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                if (indent > 0) newline(sb, indent, depth + 1);
                write(sb, item, indent, depth + 1);
            }
            if (indent > 0) newline(sb, indent, depth);
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Cannot write " + v.getClass().getName() + " as JSON");
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    boolean lone = Character.isHighSurrogate(c)
                            ? !(i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)))
                            : Character.isLowSurrogate(c) && !(i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
                    if (c < 0x20 || lone) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
