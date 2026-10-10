import app.dayhub.data.*;
import org.json.*;
import java.nio.file.*;

public class BlankParity {
    public static void main(String[] a) throws Exception {
        JSONArray cases = new JSONArray(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        check(DayHubData.inMemory().isBlank(), "a fresh engine is blank");
        int blanks = 0;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            DayHubData d = DayHubData.inMemory();
            d.importAll(new JSONObject(c.getJSONObject("doc").toString()));
            boolean want = c.getBoolean("blank");
            if (want) blanks++;
            check(d.isBlank() == want, "case " + i + ": java=" + d.isBlank() + " web=" + want + " " + c.getJSONObject("doc").toString().substring(0, Math.min(120, c.getJSONObject("doc").toString().length())));
        }
        // Adding and removing things flips it.
        DayHubData d = DayHubData.inMemory();
        d.createTask(new JSONObject("{\"title\":\"x\"}"));
        check(!d.isBlank(), "a task makes it not blank");
        d.deleteTask(1);
        check(d.isBlank(), "deleting it makes it blank again");
        d.updateSettings(new JSONObject("{\"name\":\"Asha\"}"));
        d.addMusic("https://youtu.be/dQw4w9WgXcQ", null, true);
        check(d.isBlank(), "settings and music alone do not count");
        System.out.println("IDENTICAL to the web app: " + cases.length() + " data sets (" + blanks + " blank) plus 3 direct checks");
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL " + what);
            System.exit(1);
        }
    }
}
