package app.dayhub;

import android.app.Activity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.HabitProgress;
import app.dayhub.data.HomeData;
import app.dayhub.data.Moods;
import app.dayhub.data.Validate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Journal tile on Home: jot a note, optionally pick a mood, and see spending found in the
 * text offered as expenses to add along with it. With nothing typed, tapping a face is a one-tap
 * mood check-in. The note box is built once and kept across screen refreshes, so a half-typed
 * note survives them.
 */
public final class QuickJournalTile extends HandDrawnCard {
    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final JournalActions journalActions;
    private final Runnable onChanged;

    private final TextView promptLabel;
    private final TextView promptText;
    private final HandDrawnField note;
    private final ExpenseSuggestionsView suggestions;
    private final LinearLayout moodRow;
    private final TextView moodHint;
    private final TextView count;

    private Integer draftMood;
    private Integer latestMood;
    private Integer quickMoodId;
    private String today = Validate.localDate();
    private boolean saving;

    public QuickJournalTile(Activity activity, DayHubData data, Overlays overlays, JournalActions journalActions,
                            Runnable onChanged, Runnable openJournal) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.journalActions = journalActions;
        this.onChanged = onChanged;

        LinearLayout title = new LinearLayout(activity);
        title.setOrientation(HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(Sketch.label(activity, "Journal", 22, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton full = new HandDrawnButton(activity, "Full editor", false);
        full.setOnClickListener(v -> openFullEditor());
        title.addView(full);
        addView(title);

        promptLabel = Sketch.label(activity, "", 14, true, R.color.muted);
        promptText = Sketch.label(activity, "", 15, false, R.color.muted);
        addView(spaced(promptLabel, 8));
        addView(promptText);

        note = new HandDrawnField(activity, "Jot anything. “Spent ₹250 on lunch” works too.");
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(2);
        note.setGravity(Gravity.TOP | Gravity.START);
        note.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                suggestions.schedule(s.toString());
                drawMoodFaces();
            }
        });
        addView(spaced(note, 10));

        suggestions = new ExpenseSuggestionsView(activity, data);
        addView(spaced(suggestions, 4));

        moodRow = new LinearLayout(activity);
        moodRow.setOrientation(HORIZONTAL);
        addView(spaced(moodRow, 10));
        moodHint = Sketch.label(activity, "", 14, false, R.color.muted);
        addView(spaced(moodHint, 4));

        HandDrawnButton add = new HandDrawnButton(activity, "Add to journal", true);
        add.setOnClickListener(v -> saveNote());
        addView(spaced(add, 12));

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        count = Sketch.label(activity, "", 14, false, R.color.muted);
        footer.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView open = Sketch.label(activity, "Open journal", 15, true, R.color.ink);
        open.setPaintFlags(open.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        open.setPadding(Sketch.dp(activity, 8), Sketch.dp(activity, 10), 0, Sketch.dp(activity, 10));
        open.setOnClickListener(v -> openJournal.run());
        footer.addView(open);
        addView(spaced(footer, 6));
    }

    private View spaced(View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        v.setLayoutParams(lp);
        return v;
    }

    /** Redraws the prompt, faces and counts from fresh data; the note being typed is left alone. */
    public void update(HomeData home) {
        today = home.today;
        suggestions.configure(today, home.spend.currency, null);
        latestMood = home.journal.latestMood;
        quickMoodId = home.journal.quickMoodId;
        promptLabel.setText(home.journal.prompt.title);
        promptText.setText(home.journal.prompt.text);
        drawMoodFaces();
        int n = home.journal.todayCount;
        count.setText(n == 0 ? "Nothing written yet today"
                : n + (n == 1 ? " entry" : " entries") + " today"
                + (home.journal.latestMood != null ? " · " + Moods.emoji(home.journal.latestMood) : ""));
    }

    // ---------- the full editor ----------

    /** Opens the full entry sheet with whatever has been typed so far, and clears the quick box. */
    private void openFullEditor() {
        String draftText = note.getText().toString();
        Integer mood = draftMood;
        clearDraft();
        journalActions.openSheet(null, today, draftText.trim().isEmpty() ? null : draftText, mood);
    }

    // ---------- mood ----------

    private boolean hasDraft() {
        return note.getText().toString().trim().length() > 0;
    }

    private void drawMoodFaces() {
        boolean writing = hasDraft();
        Integer shown = writing ? draftMood : latestMood;
        moodRow.removeAllViews();
        for (Moods.Mood m : Moods.ALL) {
            TextView face = new TextView(activity);
            face.setText(m.emoji);
            face.setTextSize(26);
            face.setGravity(Gravity.CENTER);
            face.setContentDescription("Mood: " + m.label);
            face.setBackground(shown != null && shown == m.value
                    ? new HandDrawnDrawable(activity, activity.getColor(R.color.hi), 0f, 8100 + m.value) : null);
            face.setOnClickListener(v -> {
                if (writing) {
                    draftMood = draftMood != null && draftMood == m.value ? null : m.value;
                    drawMoodFaces();
                } else {
                    quickMood(m);
                }
            });
            moodRow.addView(face, new LinearLayout.LayoutParams(0, Sketch.dp(activity, 52), 1f));
        }
        moodHint.setText(writing ? "The mood is saved with this note." : "Tap a face for a one-tap mood check-in.");
    }

    private void quickMood(Moods.Mood m) {
        try {
            HabitProgress progress = new HabitProgress(data);
            JSONObject body = new JSONObject().put("mood", m.value).put("today", today);
            HabitProgress.EntryResult result;
            if (quickMoodId != null) {
                // A second tap soon after the first fixes that check-in instead of adding another.
                result = progress.updateEntry(quickMoodId, body);
            } else {
                result = progress.createEntry(body.put("day", today).put("time", DateLabels.nowHHMM()));
            }
            data.commit();
            overlays.toast("Mood saved " + m.emoji + JournalActions.badgeText(result.progress));
            onChanged.run();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
            onChanged.run();
        } catch (JSONException e) {
            overlays.toast("Could not save the mood");
        }
    }

    // ---------- saving the note ----------

    private void saveNote() {
        String text = note.getText().toString().trim();
        if (text.isEmpty() || saving) return;
        saving = true;
        try {
            JSONArray expenses = suggestions.selected();
            JSONObject body = new JSONObject().put("day", today).put("time", DateLabels.nowHHMM()).put("text", text)
                    .put("tags", new JSONArray()).put("expenses", expenses).put("today", today);
            if (draftMood != null) body.put("mood", draftMood);
            HabitProgress.EntryResult result = new HabitProgress(data).createEntry(body);
            data.commit();
            int added = data.listEntryExpenses(result.entry.id).size();
            clearDraft();
            overlays.toast((added > 0 ? "Saved. " + added + " expense" + (added > 1 ? "s" : "") + " added too."
                    : "Saved to your journal") + JournalActions.badgeText(result.progress));
            onChanged.run();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        } catch (JSONException e) {
            overlays.toast("Could not save the note");
        }
        saving = false;
    }

    private void clearDraft() {
        note.setText("");
        draftMood = null;
        suggestions.reset();
        drawMoodFaces();
    }
}
