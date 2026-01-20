package com.android.miloratts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;
import java.util.HashMap;

public class CheckVoiceData extends Activity {
    
    // 支持的语言列表（与成功的实现保持一致）
    private static final String[] supportedLanguages = new String[]{
        "zho-CHN",  // 中文中国
        "eng-USA"   // 英文美国
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ArrayList<String> available = new ArrayList<>();
        ArrayList<String> unavailable = new ArrayList<>();
        HashMap<String, Boolean> requestedLanguages = new HashMap<>();
        
        // 检查是否有特定的语言检查请求
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        
        if (extras != null) {
            // 获取请求检查的语言列表
            ArrayList<String> checkFor = extras.getStringArrayList("checkVoiceDataFor");
            if (checkFor != null) {
                for (int i = 0; i < checkFor.size(); i++) {
                    String language = checkFor.get(i);
                    if (language != null && language.length() > 0) {
                        requestedLanguages.put(language, Boolean.TRUE);
                    }
                }
            }
        }
        
        // 决定返回哪些语言
        for (int i = 0; i < supportedLanguages.length; i++) {
            if (requestedLanguages.size() > 0) {
                // 如果有特定请求，只返回被请求的语言
                if (requestedLanguages.containsKey(supportedLanguages[i])) {
                    available.add(supportedLanguages[i]);
                }
            } else {
                // 如果没有特定请求，返回所有支持的语言
                available.add(supportedLanguages[i]);
            }
        }
        
        // 创建结果 Intent
        Intent resultIntent = new Intent();
        
        // 使用和成功实现相同的键名
        resultIntent.putStringArrayListExtra("availableVoices", available);
        resultIntent.putStringArrayListExtra("unavailableVoices", unavailable);
        
        // 设置结果码：如果有请求且至少有一个匹配，返回1，否则返回0
        int resultCode = (requestedLanguages.size() > 0 && available.size() > 0) ? 
            TextToSpeech.Engine.CHECK_VOICE_DATA_PASS : 
            TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL;
        
        setResult(resultCode, resultIntent);
        finish();
    }
}