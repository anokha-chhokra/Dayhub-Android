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

    /** "123.45" for 12345, the form the journal expenses list takes. */
    public static String decimal(long minor) {
        return (minor / 100) + "." + (minor % 100 < 10 ? "0" : "") + (minor % 100);
    }
}
