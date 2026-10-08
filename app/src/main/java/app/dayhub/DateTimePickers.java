package app.dayhub;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TimePicker;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Consumer;

/** Date and time pickers shown as bottom sheets with spinner wheels and Done / Cancel buttons. */
public final class DateTimePickers {
    private DateTimePickers() {}

    public static void pickDate(Overlays overlays, Context c, LocalDate initial,
                                Consumer<LocalDate> onPicked) {
        DatePicker picker = (DatePicker) LayoutInflater.from(c).inflate(R.layout.picker_date, null);
        picker.init(initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth(), null);
        BottomSheet[] sheet = new BottomSheet[1];
        View body = body(c, picker, () -> {
            sheet[0].dismiss();
            onPicked.accept(LocalDate.of(
                    picker.getYear(), picker.getMonth() + 1, picker.getDayOfMonth()));
        }, () -> sheet[0].dismiss());
        sheet[0] = overlays.sheet("Pick a date", body);
    }

    public static void pickTime(Overlays overlays, Context c, LocalTime initial,
                                Consumer<LocalTime> onPicked) {
        TimePicker picker = (TimePicker) LayoutInflater.from(c).inflate(R.layout.picker_time, null);
        picker.setIs24HourView(DateFormat.is24HourFormat(c));
        picker.setHour(initial.getHour());
        picker.setMinute(initial.getMinute());
        BottomSheet[] sheet = new BottomSheet[1];
        View body = body(c, picker, () -> {
            sheet[0].dismiss();
            onPicked.accept(LocalTime.of(picker.getHour(), picker.getMinute()));
        }, () -> sheet[0].dismiss());
        sheet[0] = overlays.sheet("Pick a time", body);
    }

    private static View body(Context c, View picker, Runnable done, Runnable cancel) {
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(picker);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        HandDrawnButton ok = new HandDrawnButton(c, "Done", true);
        ok.setOnClickListener(v -> done.run());
        HandDrawnButton no = new HandDrawnButton(c, "Cancel", false);
        no.setOnClickListener(v -> cancel.run());
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(c, 12);
        row.addView(ok, gap);
        row.addView(no);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Sketch.dp(c, 12);
        column.addView(row, rowParams);
        return column;
    }
}
