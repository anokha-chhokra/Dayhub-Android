import app.dayhub.data.*;
import app.dayhub.data.Model.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class MusicParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    static String key(Music m) {
        return m.id + "|" + m.provider + "|" + m.kind + "|" + m.url + "|" + m.embedUrl + "|" + m.label;
    }

    static String key(JSONObject o) throws Exception {
        return o.getInt("id") + "|" + o.getString("provider") + "|" + o.getString("kind") + "|" + o.getString("url") + "|" + o.getString("embedUrl") + "|" + o.getString("label");
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        JSONArray ops = root.getJSONArray("ops");
        DayHubData d = DayHubData.inMemory();
        int refused = 0;
        for (int i = 0; i < ops.length(); i++) {
            JSONObject op = ops.getJSONObject(i);
            String kind = op.getString("kind");
            String id = "op " + i + " " + kind + " " + op.opt("body") + " id=" + op.opt("id");
            String error = null;
            int status = 0;
            try {
                if (kind.equals("add")) {
                    JSONObject b = op.getJSONObject("body");
                    d.addMusic(b.opt("url"), b.opt("label"), !Boolean.FALSE.equals(b.opt("makeCurrent")));
                } else if (kind.equals("current")) {
                    d.chooseMusic(op.getJSONObject("body").opt("id"));
                } else {
                    d.deleteMusic(Validate.id(op.get("id"))); // the route checks the id first
                }
            } catch (DataError e) {
                error = e.getMessage();
                status = e.status;
            }
            if (error != null) refused++;
            eq(error == null ? "ok" : "refused", op.getInt("status") < 400 ? "ok" : "refused", id + " accepted?");
            if (error != null) {
                eq(status, op.getInt("status"), id + " status");
                eq(error, op.getString("error"), id + " message");
            }
            JSONObject after = op.getJSONObject("after");
            List<String> mine = new ArrayList<>(), theirs = new ArrayList<>();
            for (Music m : d.listMusic()) mine.add(key(m));
            for (int k = 0; k < after.getJSONArray("links").length(); k++) theirs.add(key(after.getJSONArray("links").getJSONObject(k)));
            eq(mine, theirs, id + " saved links");
            Integer current = d.getSettings().currentMusicId;
            eq(current, after.isNull("current") ? null : Integer.valueOf(after.getInt("current")), id + " current");
            eq(current, after.isNull("settingsCurrent") ? null : Integer.valueOf(after.getInt("settingsCurrent")), id + " settings current");
        }
        System.out.println("IDENTICAL to the web app: " + ops.length() + " music operations (" + (ops.length() - refused) + " accepted, "
                + refused + " refused), " + checks + " comparisons");
    }
}
