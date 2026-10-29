package app.linearthinking.waterglass;

import java.util.Locale;

final class Fmt {
    private Fmt() {}

    private static final String SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    static String pow2(int n) {
        StringBuilder b = new StringBuilder("2");
        for (char ch : Integer.toString(n).toCharArray()) b.append(SUP.charAt(ch - '0'));
        return b.toString();
    }

    static String ml(double ml) {
        return String.format(Locale.US, "%,d ml", Math.round(ml));
    }

    static String volume(double ml) {
        if (ml < 1000) return ml(ml);
        double l = ml / 1000.0;
        if (l < 10) return String.format(Locale.US, "%.1f liters", l);
        return String.format(Locale.US, "%,d liters", Math.round(l));
    }

    static String glasses(double g) {
        if (g < 1) return String.format(Locale.US, "%.0f%% of a glass", g * 100);
        if (g < 10) return String.format(Locale.US, "%.1f glasses", g);
        return String.format(Locale.US, "%,d glasses", Math.round(g));
    }

    static String length(double cm) {
        if (cm < 100) return String.format(Locale.US, "%.0f cm", cm);
        if (cm < 1000) return String.format(Locale.US, "%.2f m", cm / 100.0);
        if (cm < 100000) return String.format(Locale.US, "%,.0f m", cm / 100.0);
        return String.format(Locale.US, "%.2f km", cm / 100000.0);
    }

    static String factor(double f) {
        if (f < 10) return String.format(Locale.US, "%.1f", f);
        return String.format(Locale.US, "%,d", Math.round(f));
    }
}
