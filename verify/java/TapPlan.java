import org.json.*;
import java.nio.file.*;
import java.util.*;

import app.dayhub.core.ActionFactory;
import app.dayhub.core.MiniJson;
import app.dayhub.core.SnapshotData;

/**
 * Stage A of the widget tap check. Picks taps for each generated snapshot and works out, with the web
 * wrapper's own code (SnapshotData and ActionFactory), what the web app's widget would have queued for them.
 */
public class TapPlan {
    public static void main(String[] a) throws Exception {
        JSONArray scenarios = new JSONArray(Files.readString(Path.of(a[0]), java.nio.charset.StandardCharsets.UTF_8));
        Random r = new Random(28);
        JSONArray plan = new JSONArray();
        int taps = 0, queued = 0, ignored = 0;
        for (int n = 0; n < scenarios.length(); n++) {
            JSONObject sc = scenarios.getJSONObject(n);
            JSONObject doc = new JSONObject(sc.getJSONObject("doc").toString());
            // Mood-only check-ins the data already has would be "corrected" by the app but not by the web queue
            // (which only knows the taps of its own batch), so take them out: the two sides then start level.
            JSONArray journal = doc.getJSONArray("journal");
            JSONArray kept = new JSONArray();
            for (int i = 0; i < journal.length(); i++) {
                JSONObject e = journal.getJSONObject(i);
                boolean moodOnly = e.optString("text", "").isEmpty() && e.has("mood") && !e.isNull("mood");
                if (!moodOnly) kept.put(e);
            }
            doc.put("journal", kept);

            JSONArray queries = sc.getJSONArray("queries");
            for (int q = 0; q < queries.length(); q++) {
                JSONObject c = queries.getJSONObject(q);
                String day = c.getString("today");
                SnapshotData snap = SnapshotData.fromJson(c.getJSONObject("snapshot").toString());
                List<Integer> ticked = new ArrayList<>();
                JSONArray tapList = new JSONArray();
                JSONArray actions = new JSONArray();
                int minutes = 8 * 60 + r.nextInt(30);
                int count = 3 + r.nextInt(8);
                int lastMoodTap = -1, entryTime = -1;
                for (int k = 0; k < count && minutes < 17 * 60; k++) {
                    int kind = r.nextInt(10);
                    if (kind >= 8 && lastMoodTap >= 0 && minutes - lastMoodTap <= 30 && minutes - entryTime > 30) {
                        // The web queue's 30 minutes slide with each correction; the app's (as on Home) run from when the
                        // check-in was made. Keep clear of the one band where they differ: start a new check-in instead.
                        minutes = lastMoodTap + 31;
                        if (minutes >= 17 * 60) break;
                    }
                    String time = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
                    long at = 1_780_000_000_000L + (long) minutes * 60_000L; // whole minutes, so both sides agree on "within 30 minutes"
                    String id = "t" + n + "-" + q + "-" + k;
                    JSONObject tap = new JSONObject().put("day", day).put("time", time);
                    Map<String, Object> action = null;
                    if (kind < 3) {
                        // a task: usually one that is shown, sometimes one that is not (or already ticked)
                        int taskId = !ticked.isEmpty() && r.nextInt(5) == 0 ? ticked.get(r.nextInt(ticked.size()))
                                : snap.tasks.isEmpty() || r.nextInt(6) == 0 ? 9999 : snap.tasks.get(r.nextInt(snap.tasks.size())).id;
                        tap.put("type", "task").put("id", taskId);
                        boolean shown = false;
                        for (SnapshotData.Task t : snap.tasks) if (t.id == taskId) shown = true;
                        if (shown) {
                            action = ActionFactory.taskDone(id, taskId, day, time, at);
                            ticked.add(taskId);
                        }
                    } else if (kind < 8) {
                        if (snap.habits.isEmpty()) {
                            k--;
                            minutes += 1;
                            continue;
                        }
                        SnapshotData.Habit h = snap.habits.get(r.nextInt(snap.habits.size()));
                        boolean up = r.nextInt(3) != 0;
                        tap.put("type", "habit").put("id", h.id).put("up", up);
                        int value = up ? SnapshotData.valueAfterPlus(h) : SnapshotData.valueAfterMinus(h);
                        if (value != h.value) action = ActionFactory.habitLog(id, h.id, value, day, time, at);
                    } else {
                        int mood = 1 + r.nextInt(5);
                        if (lastMoodTap < 0 || minutes - lastMoodTap > 30) entryTime = minutes; // a new check-in
                        lastMoodTap = minutes;
                        tap.put("type", "mood").put("mood", mood);
                        action = ActionFactory.mood(id, mood, day, time, at);
                    }
                    tapList.put(tap);
                    taps++;
                    if (action != null) {
                        actions.put(new JSONObject(MiniJson.write(action)));
                        snap.apply(parse(MiniJson.write(action)));
                        queued++;
                    } else {
                        ignored++;
                    }
                    minutes += r.nextInt(3) == 0 ? r.nextInt(3) : 5 + r.nextInt(40);
                }
                plan.put(new JSONObject().put("doc", doc).put("day", day).put("taps", tapList).put("actions", actions));
            }
        }
        Files.writeString(Path.of(a[1]), plan.toString(), java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("planned " + plan.length() + " tap sequences: " + taps + " taps, " + queued + " the web would queue, " + ignored + " it would ignore");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String json) {
        return (Map<String, Object>) MiniJson.parse(json);
    }
}
