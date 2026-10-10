import app.dayhub.DataStore;
import app.dayhub.data.*;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

/** What SetupWizard.finish does: setup, then commit. */
public class WizardFlowTest {
    static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL " + what);
            System.exit(1);
        }
    }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("wizard").toFile();
        DataStore store = new DataStore(new File(dir, "dayhub.json"));
        DayHubData data = DayHubData.open(store);
        check(!data.getSettings().setupDone, "first run is not set up");

        // A wrong answer is refused and nothing sticks.
        try {
            data.setup(new JSONObject("{\"name\":\"Asha\",\"currency\":\"ZZZ9\",\"habits\":[\"water\"]}"));
            check(false, "bad currency accepted");
        } catch (DataError e) {
            check(e.status == 400, "400");
        }
        check(!data.getSettings().setupDone && data.listHabits(false).isEmpty() && !data.hasUnsavedChanges(), "bad setup changed nothing");

        // Saving is blocked: the setup is undone, so the wizard can show the error and try again.
        File blocker = new File(dir, "dayhub.json.tmp");
        check(blocker.mkdir() && new File(blocker, "x").createNewFile(), "setup blocked saves");
        data.setup(new JSONObject("{\"name\":\"Asha\",\"currency\":\"inr\",\"monthlyBudget\":\"30,000\",\"habits\":[\"water\",\"workout\"],\"today\":\"2026-10-05\",\"musicUrl\":\"https://youtu.be/dQw4w9WgXcQ\"}"));
        check(data.getSettings().setupDone, "set up in memory");
        try {
            data.commit();
            check(false, "commit should fail");
        } catch (DataError e) {
            check(e.status == 507, "507");
        }
        check(!data.getSettings().setupDone && data.listHabits(false).isEmpty() && data.listMusic().isEmpty(), "failed save undid the setup");
        check(!store.exists(), "nothing was written");

        // Room again: the same answers go through, and survive a restart.
        new File(blocker, "x").delete();
        blocker.delete();
        data.setup(new JSONObject("{\"name\":\"Asha\",\"currency\":\"inr\",\"monthlyBudget\":\"30,000\",\"habits\":[\"water\",\"workout\"],\"today\":\"2026-10-05\",\"musicUrl\":\"https://youtu.be/dQw4w9WgXcQ\"}"));
        data.commit();
        DayHubData again = DayHubData.open(store);
        Model.Settings s = again.getSettings();
        check(s.setupDone && s.name.equals("Asha") && s.currency.equals("INR") && s.monthlyBudgetMinor == 3000000L, "answers saved");
        check(again.listHabits(false).size() == 2 && again.listHabits(false).get(0).title.equals("Drink water"), "habits created");
        check(again.listMusic().size() == 1 && s.currentMusicId != null && s.currentMusicId == again.listMusic().get(0).id, "music saved and current");

        // Skipping saves just the name and still marks setup as done.
        DayHubData skipped = DayHubData.inMemory();
        skipped.setup(new JSONObject("{\"name\":\"Sam\"}"));
        check(skipped.getSettings().setupDone && skipped.getSettings().currency.equals("INR") && skipped.listHabits(false).isEmpty(), "skip");
        System.out.println("WIZARD FLOW OK");
    }
}
