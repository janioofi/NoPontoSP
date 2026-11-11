package com.janioofi.nopontosp;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeModeManager {

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    private static final String PREFERENCES = "appearance_settings";
    private static final String KEY_MODE = "theme_mode";

    private ThemeModeManager() {}

    public static String getMode(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_MODE, MODE_SYSTEM);
    }

    public static void setMode(Context context, String mode) {
        context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode)
                .apply();
        apply(mode);
    }

    public static void applySavedMode(Context context) {
        apply(getMode(context));
    }

    private static void apply(String mode) {
        if (MODE_LIGHT.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (MODE_DARK.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
