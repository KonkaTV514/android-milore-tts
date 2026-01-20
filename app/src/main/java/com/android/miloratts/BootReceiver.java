package com.android.miloratts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d("MiloraTTS", "开机启动: " + action);
        
        // 开机后自动启动TTS服务（可选）
        // Intent serviceIntent = new Intent(context, TtsService.class);
        // context.startService(serviceIntent);
    }
}