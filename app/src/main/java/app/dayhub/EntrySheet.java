package app.dayhub;

import android.app.Activity;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.DayHubData;
import app.dayhub.data.Model.Entry;
import app.dayhub.data.Moods;
import app.dayhub.data.Prompts;
import app.dayhub.data.Validate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The sheet for writing or editing a journal entry: a prompt to start from, how you are, the text,
 * spending found in it, tags, and the day and time.
 */
public final class EntrySheet {
    private static final int MAX_TAGS = 8;

    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final JournalActions actions;
    private final Entry editing; // null when writing a new one
    private final String today = Validate.localDate();

    private final HandDrawnField text;
    private final HandDrawnField tagInput;
    private final ExpenseSuggestionsView suggestions;
    private final LinearLayout promptHost;
    private final LinearLayout moodRow;
    private final LinearLayout tagHost;
    private final TextView dayText;
    private final TextView timeText;
    private final TextView error;
    private final Set<String> tags = new LinkedHashSet<>();
    private Integer mood;
    private String promptId;
    private String day;
    private String time;
    private boolean deleteArmed;
    private BottomSheet sheet;

    public static void open(Activity activity, DayHubData data, Overlays overlays, JournalActions actions,
                            Entry entry, String day, String draftText, Integer draftMood) {
        new EntrySheet(activity, data, overlays, actions, entry, day, draftText, draftMood).show();
    }

    private EntrySheet(Activity activity, DayHubData data, Overlays overlays, JournalActions actions,
                       Entry entry, String dayIfNew, String draftText, Integer draftMood) {
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.actions = actions;
        this.editing = entry;
        this.day = entry != null ? entry.day : dayIfNew;
        this.time = entry != null ? entry.time : timeFor(dayIfNew);
        this.mood = entry != null ? entry.mood : draftMood;
        if (entry != null) tags.addAll(entry.tags);
        if (entry == null && day.equals(today)) promptId = Prompts.forDay(today).id;

        text = new HandDrawnField(activity, "Write anything. Even one line counts.");
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setFilters(new InputFilter[] {new InputFilter.LengthFilter(10_000)});
        text.setMinLines(4);
        text.setGravity(Gravity.TOP | Gravity.START);
        text.setText(entry != null ? entry.text : draftText == null ? "" : draftText);

        tagInput = new HandDrawnField(activity, "Add a tag");
        tagInput.setSingleLine(true);
        tagInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(24)});

        suggestions = new ExpenseSuggestionsView(activity, data);
        promptHost = vertical();
        moodRow = new LinearLayout(activity);
        moodRow.setOrientation(LinearLayout.HORIZONTAL);
        tagHost = vertical();
        dayText = Sketch.label(activity, "", 16, false, R.color.ink);
        timeText = Sketch.label(activity, "", 16, false, R.color.ink);
        error = Sketch.label(activity, "", 15, false, R.color.red);
        error.setVisibility(View.GONE);
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(activity);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** Today gets the clock time; any other day starts with no time. */
    private String timeFor(String d) {
        return d.equals(today) ? DateLabels.nowHHMM() : null;
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    private TextView heading(String s) {
        return Sketch.label(activity, s, 17, true, R.color.ink);
    }

    private void show() {
        LinearLayout body = vertical();
        if (editing == null) body.addView(promptHost);
        body.addView(heading("How are you?"), rowParams(editing == null ? 14 : 0));
        body.addView(moodRow, rowParams(6));
        body.addView(heading("Entry"), rowParams(14));
        body.addView(text, rowParams(6));
        body.addView(suggestions, rowParams(6));
        body.addView(heading("Tags"), rowParams(14));
        body.addView(tagHost, rowParams(6));
        LinearLayout addTag = new LinearLayout(activity);
        addTag.setOrientation(LinearLayout.HORIZONTAL);
        addTag.setGravity(Gravity.CENTER_VERTICAL);
        addTag.addView(tagInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton add = new HandDrawnButton(activity, "Add", false);
        add.setOnClickListener(v -> addTagFromInput());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.leftMargin = Sketch.dp(activity, 10);
        addTag.addView(add, addParams);
        body.addView(addTag, rowParams(8));

        body.addView(heading("Day"), rowParams(14));
        body.addView(dayText, rowParams(4));
        HandDrawnButton pickDay = new HandDrawnButton(activity, "Change day", false);
        pickDay.setOnClickListener(v -> pickDay());
        body.addView(pickDay, rowParams(6));

        body.addView(heading("Time"), rowParams(14));
        body.addView(timeText, rowParams(4));
        LinearLayout timeButtons = new LinearLayout(activity);
        timeButtons.setOrientation(LinearLayout.HORIZONTAL);
        HandDrawnButton setTime = new HandDrawnButton(activity, "Set time", false);
        setTime.setOnClickListener(v -> pickTime());
        HandDrawnButton noTime = new HandDrawnButton(activity, "No time", false);
        noTime.setOnClickListener(v -> {
            time = null;
            drawWhen();
        });
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 12);
        timeButtons.addView(setTime, gap);
        timeButtons.addView(noTime);
        body.addView(timeButtons, rowParams(6));

        body.addView(error, rowParams(10));

        HandDrawnButton save = new HandDrawnButton(activity, editing != null ? "Save" : "Save entry", true);
        save.setOnClickListener(v -> save());
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(save);
        if (editing != null) {
            HandDrawnButton delete = new HandDrawnButton(activity, "Delete", false);
            delete.setOnClickListener(v -> {
                if (!deleteArmed) {
                    deleteArmed = true;
                    delete.setText("Tap again to delete");
                    return;
                }
                sheet.dismiss();
                actions.delete(editing);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Sketch.dp(activity, 12);
            buttons.addView(delete, lp);
        }
        body.addView(buttons, rowParams(16));

        text.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                suggestions.schedule(s.toString());
            }
        });
        suggestions.configure(day, data.getSettings().currency, editing != null ? editing.id : null);

        drawPrompt();
        drawMood();
        drawTags();
        drawWhen();
        sheet = overlays.sheet(editing != null ? "Edit entry" : "New entry", body);
        Keyboard.showFor(text);
    }

    // ---------- prompt ----------

    private void drawPrompt() {
        promptHost.removeAllViews();
        Prompts.Prompt chosen = null;
        for (Prompts.Prompt p : Prompts.ALL) if (p.id.equals(promptId)) chosen = p;
        text.setHint(chosen != null ? chosen.text : "Write anything. Even one line counts.");

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip("Free write", promptId == null, () -> {
            promptId = null;
            drawPrompt();
        }));
        for (Prompts.Prompt p : Prompts.ALL) {
            chips.addView(chip(p.title, p.id.equals(promptId), () -> {
                promptId = p.id;
                drawPrompt();
            }));
        }
        scroll.addView(chips);
        promptHost.addView(scroll);
        if (chosen != null) {
            promptHost.addView(Sketch.label(activity, chosen.title + ": " + chosen.text, 15, false, R.color.muted), rowParams(8));
        }
    }

    private View chip(String label, boolean on, Runnable onClick) {
        HandDrawnButton b = new HandDrawnButton(activity, label, on);
        b.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Sketch.dp(activity, 10);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------- mood ----------

    private void drawMood() {
        moodRow.removeAllViews();
        for (Moods.Mood m : Moods.ALL) {
            TextView face = new TextView(activity);
            face.setText(m.emoji);
            face.setTextSize(26);
            face.setGravity(Gravity.CENTER);
            face.setContentDescription(m.label);
            face.setBackground(mood != null && mood == m.value
                    ? new HandDrawnDrawable(activity, activity.getColor(R.color.hi), 0f, 8300 + m.value) : null);
            face.setOnClickListener(v -> {
                mood = mood != null && mood == m.value ? null : m.value;
                drawMood();
            });
            moodRow.addView(face, new LinearLayout.LayoutParams(0, Sketch.dp(activity, 52), 1f));
        }
    }

    // ---------- tags ----------

    private void addTagFromInput() {
        String t = tagInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("#")) t = t.substring(1);
        if (!t.isEmpty() && tags.size() < MAX_TAGS) tags.add(t);
        tagInput.setText("");
        drawTags();
    }

    private void drawTags() {
        tagHost.removeAllViews();
        Set<String> all = new LinkedHashSet<>(Prompts.TAG_SUGGESTIONS);
        all.addAll(tags);
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String t : all) {
            chips.addView(chip("#" + t, tags.contains(t), () -> {
                if (tags.contains(t)) tags.remove(t);
                else if (tags.size() < MAX_TAGS) tags.add(t);
                drawTags();
            }));
        }
        scroll.addView(chips);
        tagHost.addView(scroll);
    }

    // ---------- day and time ----------

    private void drawWhen() {
        dayText.setText(DateLabels.dueLabel(activity, day, today));
        timeText.setText(time == null ? "No time" : time);
    }

    private void pickDay() {
        DateTimePickers.pickDate(overlays, activity, LocalDate.parse(day), d -> {
            String picked = d.isAfter(LocalDate.parse(today)) ? today : d.toString(); // no entries for days that have not happened
            if (editing == null) time = timeFor(picked);
            day = picked;
            suggestions.configure(day, data.getSettings().currency, editing != null ? editing.id : null);
            drawWhen();
        });
    }

    private void pickTime() {
        LocalTime initial = time != null ? LocalTime.parse(time) : LocalTime.now();
        DateTimePickers.pickTime(overlays, activity, initial, t -> {
            time = String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
            drawWhen();
        });
    }

    // ---------- saving ----------

    private void save() {
        error.setVisibility(View.GONE);
        String message;
        try {
            JSONArray tagList = new JSONArray();
            for (String t : tags) tagList.put(t);
            JSONObject body = new JSONObject().put("day", day).put("time", time == null ? JSONObject.NULL : time)
                    .put("text", text.getText().toString()).put("mood", mood == null ? JSONObject.NULL : mood)
                    .put("tags", tagList).put("expenses", suggestions.selected()).put("today", today);
            if (editing == null && promptId != null) body.put("promptId", promptId);
            message = actions.save(editing, body);
        } catch (JSONException e) {
            message = "Could not save the entry";
        }
        if (message != null) {
            error.setText(message);
            error.setVisibility(View.VISIBLE);
            return;
        }
        sheet.dismiss();
    }
}
