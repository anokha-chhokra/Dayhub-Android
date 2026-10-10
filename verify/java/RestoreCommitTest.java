import app.dayhub.DataStore;
import app.dayhub.data.*;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

/** What DataTransfer.replace does: import a backup, then save; a failed save must bring the old data back. */
public class RestoreCommitTest {
    static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL " + what);
            System.exit(1);
        }
    }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("restore").toFile();
        DataStore store = new DataStore(new File(dir, "dayhub.json"));
        DayHubData data = DayHubData.open(store);
        data.createTask(new JSONObject("{\"title\":\"mine\"}"));
        data.commit();

        DayHubData other = DayHubData.inMemory();
        other.createTask(new JSONObject("{\"title\":\"from backup\"}"));
        String backup = DataExport.backup(other).text;
        check(DamagedDataRecovery.inspect(backup).startsWith("0 journal entries, 1 tasks"), "backup summary");

        // Saving is blocked: the restore must fail and leave everything as it was.
        File blocker = new File(dir, "dayhub.json.tmp");
        check(blocker.mkdir() && new File(blocker, "x").createNewFile(), "setup");
        data.importAll(backup);
        check(data.listTasks("all").get(0).title.equals("from backup"), "imported in memory");
        try {
            data.commit();
            check(false, "commit should fail");
        } catch (DataError e) {
            check(e.status == 507, "507");
        }
        check(data.listTasks("all").size() == 1 && data.listTasks("all").get(0).title.equals("mine"), "old data came back");
        check(DayHubData.open(store).listTasks("all").get(0).title.equals("mine"), "file still has the old data");

        // With room again, the same restore goes through and survives a restart.
        new File(blocker, "x").delete();
        blocker.delete();
        data.importAll(backup);
        data.commit();
        check(DayHubData.open(store).listTasks("all").get(0).title.equals("from backup"), "restore saved");
        System.out.println("RESTORE COMMIT OK");
    }
}
