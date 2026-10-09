package app.dayhub;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Shows amounts the way the web app does: whole amounts without decimals, Indian grouping for rupees. */
public final class MoneyFormat {
    private MoneyFormat() {}

    /** @param minor an amount in paise or cents */
    public static String money(long minor, String currency) {
        double value = minor / 100.0;
        boolean whole = minor % 100 == 0;
        try {
            Locale locale = currency.equals("INR") ? Locale.forLanguageTag("en-IN") : Locale.getDefault();
            NumberFormat f = NumberFormat.getCurrencyInstance(locale);
            f.setCurrency(Currency.getInstance(currency));
            f.setMinimumFractionDigits(whole ? 0 : 2);
            f.setMaximumFractionDigits(2);
            return f.format(value);
        } catch (IllegalArgumentException e) {
            return currency + " " + String.format(Locale.ROOT, "%.2f", value);
        }
    }

    /**
     * One plain sentence about how the month's spending is going, or an empty string when there is
     * nothing to say (no budget, or already over).
     */
    public static String paceText(app.dayhub.data.Insights.Pace pace, String currency) {
        if (pace.status.equals("none") || pace.status.equals("over")) return "";
        StringBuilder sb = new StringBuilder();
        if (pace.perDayLeftMinor != null && pace.daysLeft > 0) {
            sb.append("About ").append(money(pace.perDayLeftMinor, currency)).append(" a day for the next ")
                    .append(pace.daysLeft).append(pace.daysLeft == 1 ? " day" : " days");
        }
        if (pace.status.equals("watch")) {
            if (sb.length() > 0) sb.append(" \u00B7 ");
            sb.append("at this pace the month ends near ").append(money(pace.projectedMinor, currency));
        }
        return sb.toString();
    }

    /** "123.45" for 12345, the form the journal expenses list takes. */
    public static String decimal(long minor) {
        return (minor / 100) + "." + (minor % 100 < 10 ? "0" : "") + (minor % 100);
    }
}
