package com.handoff.ui;

/** Visual profiles only: neither changes hook settings nor implies framework activation. */
enum UiStyle {
    MATERIAL("material", "Material 3 Expressive", R.style.HandoffTheme),
    MIUIX("miuix", "Miuix", R.style.HandoffTheme_Miuix);

    final String key, label;
    final int theme;
    UiStyle(String key, String label, int theme) { this.key = key; this.label = label; this.theme = theme; }
    static UiStyle parse(String key) {
        for (UiStyle style : values()) if (style.key.equals(key)) return style;
        return MATERIAL;
    }
}
