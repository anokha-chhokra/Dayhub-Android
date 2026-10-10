import app.dayhub.data.*;
import org.json.*;
import java.nio.file.*;

public class SetupParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!java.util.Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    /** Key-order-independent text of a JSON value. */
    static String canon(Object v) throws Exception {
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            java.util.TreeSet<String> keys = new java.util.TreeSet<>();
            java.util.Iterator<?> it = o.keys();
            while (it.hasNext()) keys.add((String) it.next());
            StringBuilder sb = new StringBuilder("{");
            for (String k : keys) sb.append(JSONObject.quote(k)).append(':').append(canon(o.get(k))).append(',');
            return sb.append('}').toString();
        }
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length(); i++) sb.append(canon(a.get(i))).append(',');
            return sb.append(']').toString();
        }
        if (v instanceof Number) return String.valueOf(((Number) v).longValue());
        if (v == JSONObject.NULL) return "null";
        return v instanceof String ? JSONObject.quote((String) v) : String.valueOf(v);
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        JSONArray presets = root.getJSONArray("presets");
        eq(HabitPresets.ALL.size(), presets.length(), "preset count");
        for (int i = 0; i < presets.length(); i++) {
            JSONObject p = presets.getJSONObject(i);
            HabitPresets.Preset j = HabitPresets.ALL.get(i);
            eq(j.id, p.getString("id"), "preset id " + i);
            eq(j.title, p.getString("title"), "preset title " + j.id);
            eq(j.icon, p.getString("icon"), "preset icon " + j.id);
            eq(j.kind, p.getString("kind"), "preset kind " + j.id);
            eq(j.unit, p.getString("unit"), "preset unit " + j.id);
            eq(j.target, p.getInt("target"), "preset target " + j.id);
            eq(j.step, p.getInt("step"), "preset step " + j.id);
            eq(j.points, p.getInt("points"), "preset points " + j.id);
        }
        JSONArray cases = root.getJSONArray("cases");
        int ok = 0;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            DayHubData d = DayHubData.inMemory();
            String id = "case " + i + " " + c.getJSONObject("body");
            Model.Settings result = null;
            String error = null;
            try {
                result = d.setup(new JSONObject(c.getJSONObject("body").toString()));
            } catch (DataError e) {
                error = e.getMessage();
                eq(e.status, 400, id + " status");
            }
            int status = c.getInt("status");
            eq(error == null ? 200 : 400, status, id + " status");
            if (error != null) {
                eq(error, c.getJSONObject("response").getString("error"), id + " message");
            } else {
                ok++;
                JSONObject want = c.getJSONObject("response");
                eq(result.name, want.getString("name"), id + " name");
                eq(result.currency, want.getString("currency"), id + " currency");
                eq(result.monthlyBudgetMinor, want.getLong("monthlyBudgetMinor"), id + " budget");
                eq(result.setupDone, want.getBoolean("setupDone"), id + " setupDone");
                eq(result.currentMusicId, want.isNull("currentMusicId") ? null : Integer.valueOf(want.getInt("currentMusicId")), id + " music");
            }
            JSONObject after = new JSONObject(d.exportAll().toString());
            after.remove("exportedAt");
            eq(canon(after), canon(c.getJSONObject("after")), id + " saved data");
        }
        System.out.println("IDENTICAL to the web app: " + presets.length() + " presets, " + cases.length() + " setup calls (" + ok + " accepted, " + (cases.length() - ok) + " refused), " + checks + " comparisons");
    }
}
