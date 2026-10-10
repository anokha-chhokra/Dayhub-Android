import app.dayhub.data.*;
import app.dayhub.data.Model.Settings;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class SettingsParity {
    static int checks = 0;

    static void eq(Object got, Object want, String what) {
        checks++;
        if (!Objects.equals(got, want)) {
            System.out.println("MISMATCH " + what + "\n  java: " + got + "\n  web:  " + want);
            System.exit(1);
        }
    }

    static String key(Settings s) {
        return s.name + "|" + s.currency + "|" + s.monthlyBudgetMinor + "|" + s.setupDone + "|" + s.currentMusicId + "|" + s.notifications + "|" + s.journalReminder;
    }

    static String key(JSONObject o) throws Exception {
        return o.getString("name") + "|" + o.getString("currency") + "|" + o.getLong("monthlyBudgetMinor") + "|" + o.getBoolean("setupDone") + "|"
                + (o.isNull("currentMusicId") ? null : Integer.valueOf(o.getInt("currentMusicId"))) + "|" + o.getBoolean("notifications") + "|" + o.getString("journalReminder");
    }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        JSONArray ops = root.getJSONArray("ops");
        DayHubData d = DayHubData.inMemory();
        int refused = 0;
        for (int i = 0; i < ops.length(); i++) {
            JSONObject op = ops.getJSONObject(i);
            String id = "op " + i + " " + op.getJSONObject("body");
            String error = null;
            Settings result = null;
            int status = 0;
            try {
                result = d.updateSettings(new JSONObject(op.getJSONObject("body").toString()));
            } catch (DataError e) {
                error = e.getMessage();
                status = e.status;
            }
            if (error != null) refused++;
            eq(error == null ? "ok" : "refused", op.getInt("status") < 400 ? "ok" : "refused", id + " accepted?");
            if (error != null) {
                eq(status, op.getInt("status"), id + " status");
                eq(error, op.getString("error"), id + " message");
            } else {
                eq(key(result), key(op.getJSONObject("response")), id + " response");
            }
            eq(key(d.getSettings()), key(op.getJSONObject("after")), id + " settings afterwards");
        }
        System.out.println("IDENTICAL to the web app: " + ops.length() + " settings updates (" + (ops.length() - refused) + " accepted, "
                + refused + " refused), " + checks + " comparisons");
    }
}
