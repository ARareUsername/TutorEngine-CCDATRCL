package tutorengine.ui;

import java.util.prefs.Preferences;

// OWNER: Bondoc, Karl B. — display accessibility tiers. Toolkit-free;
// Preferences are file-backed per OS user (no repo files, survive restarts).
//
// Text tiers set the root font size in px (cascades through the CSS);
// card tiers set preview/tooltip widths (S renders from Scryfall small,
// M/L previews render from the normal variant).
public final class DisplaySettings {
    public enum TextTier {
        STANDARD(13), LARGE(15), XL(17);
        public final int px;
        TextTier(int px) { this.px = px; }
    }

    public enum CardTier {
        S(146, 200), M(240, 300), L(360, 420);
        public final int preview;
        public final int tooltip;
        CardTier(int preview, int tooltip) { this.preview = preview; this.tooltip = tooltip; }
    }

    private static final Preferences PREFS = Preferences.userNodeForPackage(DisplaySettings.class);

    private DisplaySettings() {}

    public static TextTier text() {
        try {
            return TextTier.valueOf(PREFS.get("text", TextTier.STANDARD.name()));
        } catch (Exception e) {
            return TextTier.STANDARD;
        }
    }

    public static void setText(TextTier t) {
        if (t != null) PREFS.put("text", t.name());
    }

    public static CardTier cards() {
        try {
            return CardTier.valueOf(PREFS.get("cards", CardTier.S.name()));
        } catch (Exception e) {
            return CardTier.S;
        }
    }

    public static void setCards(CardTier t) {
        if (t != null) PREFS.put("cards", t.name());
    }

    public static void main(String[] args) {
        System.out.println("defaults: " + text() + " " + cards().preview + "/" + cards().tooltip);
        setText(TextTier.XL);
        setCards(CardTier.L);
        System.out.println("round-trip: " + text() + " " + cards().preview + "/" + cards().tooltip);
        setText(TextTier.STANDARD);
        setCards(CardTier.S);
        System.out.println("restored: " + text() + " " + cards().preview + "/" + cards().tooltip);
        System.out.println("DisplaySettings OK.");
    }
}
