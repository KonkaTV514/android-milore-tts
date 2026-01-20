package com.android.miloratts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FilenameFilter;

public class EngineSettings extends Activity {
    
    public static final String PREFS_NAME = "MiloraTtsPrefs";
    public static final String KEY_CACHE_LIMIT = "cache_limit";
    private static final int DEFAULT_CACHE_LIMIT = 50;

    private SharedPreferences prefs;
    private EditText cacheLimitInput;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // 使用线性布局来垂直排列控件
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 50, 50, 50);

        // 应用描述
        TextView textView = new TextView(this);
        textView.setText("Milora TTS 设置\n\n这是一个基于Milora API的系统TTS引擎。\n\n语言支持：中文、英文");
        textView.setTextSize(16);
        textView.setPadding(0, 0, 0, 50);
        mainLayout.addView(textView);
        
        // --- 缓存设置 ---
        TextView cacheLabel = new TextView(this);
        cacheLabel.setText("缓存数量上限 (建议10-200):");
        cacheLabel.setTextSize(14);
        cacheLabel.setPadding(0, 50, 0, 10);
        mainLayout.addView(cacheLabel);

        cacheLimitInput = new EditText(this);
        cacheLimitInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        cacheLimitInput.setSingleLine(true);
        int currentLimit = prefs.getInt(KEY_CACHE_LIMIT, DEFAULT_CACHE_LIMIT);
        cacheLimitInput.setText(String.valueOf(currentLimit));
        mainLayout.addView(cacheLimitInput);

        Button saveBtn = new Button(this);
        saveBtn.setText("保存缓存设置");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCacheLimit();
            }
        });
        mainLayout.addView(saveBtn);

        // --- 其他按钮 ---
        Button clearCacheBtn = new Button(this);
        clearCacheBtn.setText("删除所有缓存文件");
        LinearLayout.LayoutParams clearCacheParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        clearCacheParams.setMargins(0, 50, 0, 0);
        clearCacheBtn.setLayoutParams(clearCacheParams);
        clearCacheBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCache();
            }
        });
        mainLayout.addView(clearCacheBtn);
        
        setContentView(mainLayout);
    }

    private void saveCacheLimit() {
        String valueStr = cacheLimitInput.getText().toString();
        try {
            int newLimit = Integer.parseInt(valueStr);
            if (newLimit >= 0) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(KEY_CACHE_LIMIT, newLimit);
                editor.apply();
                Toast.makeText(this, "保存成功！缓存上限为 " + newLimit, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请输入一个非负数", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void clearCache() {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected void onPreExecute() {
                Toast.makeText(EngineSettings.this, "正在清理缓存...", Toast.LENGTH_SHORT).show();
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                File cacheDir = getCacheDir();
                if (cacheDir == null || !cacheDir.isDirectory()) {
                    return 0;
                }
                
                File[] files = cacheDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().endsWith(".mp3");
                    }
                });

                if (files == null) {
                    return 0;
                }

                int deletedCount = 0;
                for (File file : files) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
                return deletedCount;
            }

            @Override
            protected void onPostExecute(Integer deletedCount) {
                Toast.makeText(EngineSettings.this, 
                    "清理完成！已删除 " + deletedCount + " 个缓存文件。", 
                    Toast.LENGTH_LONG).show();
            }
        }.execute();
    }

    // 移除了 testTts 和 showAbout 方法，因为它们在这个文件中不存在
}