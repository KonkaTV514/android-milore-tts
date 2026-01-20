package com.android.miloratts;

import android.app.Application;
import android.util.Log;

public class MiloraApp extends Application {
    private static final String TAG = "MiloraApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Milora TTS 应用启动");
    }
}