package com.handoff.ui;

import android.content.Context;
import android.content.SharedPreferences;

/** App-private appearance, deliberately separate from the remote hook preferences. */
final class UiStyleStore {
    private final SharedPreferences preferences;
    UiStyleStore(Context context) { this(context.getSharedPreferences("handoff_appearance", Context.MODE_PRIVATE)); }
    UiStyleStore(SharedPreferences preferences) { this.preferences = preferences; }
    UiStyle read() {
        try { return UiStyle.parse(preferences.getString("style", UiStyle.MATERIAL.key)); }
        catch (RuntimeException ignored) { return UiStyle.MATERIAL; }
    }
    boolean write(UiStyle style) {
        UiStyle previous = read();
        try {
            if (preferences.edit().putString("style", style.key).commit()) return true;
        } catch (RuntimeException ignored) { }
        // SharedPreferences can update its in-memory map even when disk commit fails.
        // Restore that map so a subsequent night-mode recreation does not apply a rejected choice.
        try { preferences.edit().putString("style", previous.key).commit(); }
        catch (RuntimeException ignored) { }
        return false;
    }
}
