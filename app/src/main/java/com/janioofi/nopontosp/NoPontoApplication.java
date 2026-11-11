package com.janioofi.nopontosp;

import android.app.Application;

public class NoPontoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeModeManager.applySavedMode(this);
    }
}
