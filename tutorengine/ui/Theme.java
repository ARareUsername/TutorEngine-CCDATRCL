package tutorengine.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;

// Single place for the app's look. Primer Light is the default (projector-safe);
// the toggle switches to Primer Dark live. Badge tokens map card states to
// AtlantaFX style classes so views never hardcode colors.
//
// Headless note: DARK/LIGHT flags and badgeFor() are toolkit-free and covered by
// main() below. apply()/toggle() need a running JavaFX toolkit (FxApp calls them).
public final class Theme {
    public static final boolean LIGHT = false;
    public static final boolean DARK = true;

    private static boolean dark = LIGHT;

    // Card-state badges (AtlantaFX style classes, toolkit-free strings).
    public static final String BADGE_OK = "success";
    public static final String BADGE_LOW_STOCK = "warning";
    public static final String BADGE_FOIL = "accent";

    private Theme() {}

    // qty <= 3 counts as low stock (matches the demo narrative).
    public static String badgeFor(int quantity, boolean isFoil) {
        if (isFoil) return BADGE_FOIL;
        if (quantity <= 3) return BADGE_LOW_STOCK;
        return BADGE_OK;
    }

    public static void apply(boolean useDark) {
        dark = useDark;
        Application.setUserAgentStylesheet(useDark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
    }

    public static void toggle() { apply(!dark); }
    public static boolean isDark() { return dark; }

    public static void main(String[] args) {
        System.out.println("foil -> " + badgeFor(10, true) + " (want accent)");
        System.out.println("qty1 -> " + badgeFor(1, false) + " (want warning)");
        System.out.println("qty18 -> " + badgeFor(18, false) + " (want success)");
        System.out.println("Theme OK. default=" + (isDark() ? "dark" : "light"));
    }
}
