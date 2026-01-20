package com.android.miloratts;

import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class TtsService extends TextToSpeechService {
    private static final String TAG = "MiloraTTS";
    private static final String API_URL = "https://api.milorapart.top/apis/mbAIsc";
    private SynthesisTask currentTask;

    @Override
    protected String[] onGetLanguage() {
        return new String[]{"zho-CHN", "eng-USA"};
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        Log.d(TAG, "检查语言: " + lang + "-" + country);
        if ("zho".equals(lang) || "eng".equals(lang)) {
            return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        }
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    protected Set<String> onGetFeaturesForLanguage(String lang, String country, String variant) {
        Set<String> features = new HashSet<>();
        features.add("networkTts");
        features.add("networkTimeoutMs");
        return features;
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    protected void onStop() {
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        String text = request.getCharSequenceText().toString();

        if (text == null || text.trim().isEmpty()) {
            callback.done();
            return;
        }
        Pattern pattern = Pattern.compile("[\\p{L}\\p{N}]");
        if (!pattern.matcher(text).find()) {
            Log.i(TAG, "文本只包含符号或空格，已忽略: \"" + text + "\"");
            callback.done();
            return;
        }

        if (currentTask != null && currentTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.w(TAG, "上一个合成任务尚未完成，正在强制取消...");
            currentTask.cancel(true);
        }

        Log.i(TAG, "合成请求: " + request.getLanguage() + " - " +
              (text.length() > 30 ? text.substring(0, 30) + "..." : text));

        currentTask = new SynthesisTask(text, callback);
        currentTask.execute();
    }

    private class SynthesisTask extends AsyncTask<Void, Void, Boolean> {
        private final String text;
        private final SynthesisCallback callback;

        SynthesisTask(String text, SynthesisCallback callback) {
            this.text = text;
            this.callback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                return synthesizeText(text, callback);
            } catch (Exception e) {
                Log.e(TAG, "合成任务失败", e);
                callback.error();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (isCancelled()) {
                return;
            }
            if (success) {
                callback.done();
            } else {
                callback.error();
            }
        }
    }

    private String generateCacheKey(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(text.getBytes("UTF-8"));
            BigInteger bi = new BigInteger(1, hash);
            return bi.toString(16) + ".mp3";
        } catch (Exception e) {
            Log.e(TAG, "生成缓存键失败", e);
            return text.replaceAll("[^a-zA-Z0-9.-]", "_") + ".mp3";
        }
    }

    private boolean synthesizeText(String text, SynthesisCallback callback) {
        File cacheDir = getCacheDir();
        if (cacheDir == null) {
            Log.e(TAG, "无法获取缓存目录，直接进行网络请求");
            return downloadAndDecode(text, callback, null);
        }
        
        String cacheKey = generateCacheKey(text);
        File cachedFile = new File(cacheDir, cacheKey);

        if (cachedFile.exists() && cachedFile.length() > 0) {
            Log.i(TAG, "缓存命中！从文件加载: " + cacheKey);
            FileInputStream fis = null;
            ByteArrayOutputStream baos = null;
            try {
                fis = new FileInputStream(cachedFile);
                baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                byte[] mp3Data = baos.toByteArray();
                cachedFile.setLastModified(System.currentTimeMillis());
                return decodeMp3ToPcm(mp3Data, callback);
            } catch (java.io.IOException e) {
                Log.e(TAG, "读取缓存文件失败", e);
                return downloadAndDecode(text, callback, cachedFile);
            } finally {
                try {
                    if (fis != null) fis.close();
                    if (baos != null) baos.close();
                } catch (java.io.IOException e) {
                    Log.e(TAG, "关闭缓存读取流失败", e);
                }
            }
        } else {
            Log.i(TAG, "缓存未命中，从网络请求: " + cacheKey);
            return downloadAndDecode(text, callback, cachedFile);
        }
    }

    private boolean downloadAndDecode(String text, SynthesisCallback callback, File cacheFile) {
        try {
            String encodedText = URLEncoder.encode(text, "UTF-8");
            String apiCall = API_URL + "?text=" + encodedText + "&format=mp3";
            
            Log.d(TAG, "调用API: " + apiCall);
            
            String jsonResponse = httpGet(apiCall);
            if (jsonResponse == null || !jsonResponse.contains("\"code\":200")) {
                Log.e(TAG, "API返回错误或非200状态: " + jsonResponse);
                callback.error(TextToSpeech.ERROR_NETWORK);
                return false;
            }

            String audioUrl = parseJsonUrl(jsonResponse);
            if (audioUrl == null) {
                Log.e(TAG, "无法从JSON中解析出URL: " + jsonResponse);
                callback.error(TextToSpeech.ERROR_INVALID_REQUEST);
                return false;
            }
            
            byte[] mp3Data = httpGetBytes(audioUrl);
            if (mp3Data == null || mp3Data.length == 0) {
                callback.error(TextToSpeech.ERROR_NETWORK);
                return false;
            }
            
            Log.d(TAG, "下载MP3完成，大小: " + mp3Data.length + " bytes");

            if (cacheFile != null) {
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(cacheFile);
                    fos.write(mp3Data);
                    Log.i(TAG, "已缓存音频到: " + cacheFile.getName());
                    pruneCache(cacheFile.getParentFile());
                } catch (java.io.IOException e) {
                    Log.e(TAG, "写入缓存文件失败", e);
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (java.io.IOException e) {
                            Log.e(TAG, "关闭缓存写入流失败", e);
                        }
                    }
                }
            }
            
            return decodeMp3ToPcm(mp3Data, callback);
            
        } catch (Exception e) {
            Log.e(TAG, "下载和解码流程失败", e);
            callback.error(TextToSpeech.ERROR_NETWORK);
            return false;
        }
    }

    private void pruneCache(final File cacheDir) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                SharedPreferences prefs = getSharedPreferences("MiloraTtsPrefs", MODE_PRIVATE);
                int cacheLimit = prefs.getInt("cache_limit", 50);

                if (cacheDir == null || !cacheDir.isDirectory()) {
                    return null;
                }
                try {
                    File[] files = cacheDir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith(".mp3");
                        }
                    });

                    if (files == null || files.length <= cacheLimit) {
                        return null;
                    }

                    Log.i(TAG, "缓存数量 " + files.length + ", 超出限制 " + cacheLimit);

                    Arrays.sort(files, new Comparator<File>() {
                        @Override
                        public int compare(File f1, File f2) {
                            return Long.compare(f1.lastModified(), f2.lastModified());
                        }
                    });

                    int filesToDelete = files.length - cacheLimit;
                    Log.i(TAG, "准备删除 " + filesToDelete + " 个最旧的缓存文件...");
                    for (int i = 0; i < filesToDelete; i++) {
                        if (files[i].delete()) {
                            Log.d(TAG, "已删除旧缓存: " + files[i].getName());
                        } else {
                            Log.w(TAG, "删除旧缓存失败: " + files[i].getName());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "清理缓存时出错", e);
                }
                return null;
            }
        }.execute();
    }

    private boolean decodeMp3ToPcm(byte[] mp3Data, SynthesisCallback callback) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        File tempFile = null;

        try {
            tempFile = File.createTempFile("tts_audio", ".mp3", getCacheDir());
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(tempFile);
                fos.write(mp3Data);
            } finally {
                if (fos != null) { try { fos.close(); } catch (java.io.IOException e) { /* ignore */ } }
            }
            
            extractor = new MediaExtractor();
            extractor.setDataSource(tempFile.getAbsolutePath());

            MediaFormat format = null;
            String mime = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                format = extractor.getTrackFormat(i);
                mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    break;
                }
            }

            if (format == null || mime == null) {
                Log.e(TAG, "在MP3中未找到音轨");
                callback.error(TextToSpeech.ERROR_INVALID_REQUEST);
                return false;
            }

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            MediaFormat outputFormat = codec.getOutputFormat();
            int sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

            Log.d(TAG, "解码器真实输出格式: " + sampleRate + "Hz, " + channelCount + "声道");
            callback.start(sampleRate, pcmEncoding, channelCount);

            final long timeoutUs = 10000;
            ByteBuffer[] inputBuffers = codec.getInputBuffers();
            ByteBuffer[] outputBuffers = codec.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean isExtractorDone = false;

            while (!Thread.currentThread().isInterrupted()) {
                if (!isExtractorDone) {
                    int inputBufIndex = codec.dequeueInputBuffer(timeoutUs);
                    if (inputBufIndex >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[inputBufIndex];
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isExtractorDone = true;
                        } else {
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs);
                if (outputBufIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufIndex];
                    final byte[] chunk = new byte[bufferInfo.size];
                    outputBuffer.get(chunk);
                    outputBuffer.clear();

                    if (chunk.length > 0) {
                        if (callback.audioAvailable(chunk, 0, chunk.length) == TextToSpeech.STOPPED) {
                            break;
                        }
                    }
                    codec.releaseOutputBuffer(outputBufIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = codec.getOutputBuffers();
                }
            }
            
            return true;

        } catch (Exception e) {
            Log.e(TAG, "MediaCodec解码失败", e);
            callback.error(TextToSpeech.ERROR_SYNTHESIS);
            return false;
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception e) { Log.e(TAG, "停止解码器失败", e); }
                try { codec.release(); } catch (Exception e) { Log.e(TAG, "释放解码器失败", e); }
            }
            if (extractor != null) {
                try { extractor.release(); } catch (Exception e) { Log.e(TAG, "释放提取器失败", e); }
            }
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String httpGet(String urlStr) {
        final int MAX_RETRIES = 3;
        final int TIMEOUT_MS = 15000;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP GET 请求失败，响应码: " + conn.getResponseCode());
                    continue;
                }

                InputStream input = conn.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                input.close();
                return output.toString("UTF-8");

            } catch (Exception e) {
                Log.w(TAG, "HTTP GET 尝试 " + attempt + "/" + MAX_RETRIES + " 失败: " + e.getMessage());
                if (attempt == MAX_RETRIES) {
                    Log.e(TAG, "HTTP GET 达到最大重试次数，放弃。", e);
                    return null;
                }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return null;
    }
    
    private byte[] httpGetBytes(String urlStr) {
        final int MAX_RETRIES = 3;
        final int TIMEOUT_MS = 15000;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP GET字节请求失败，响应码: " + conn.getResponseCode());
                    continue;
                }

                InputStream input = conn.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                input.close();
                return output.toByteArray();

            } catch (Exception e) {
                Log.w(TAG, "HTTP GET字节 尝试 " + attempt + "/" + MAX_RETRIES + " 失败: " + e.getMessage());
                if (attempt == MAX_RETRIES) {
                    Log.e(TAG, "HTTP GET字节 达到最大重试次数，放弃。", e);
                    return null;
                }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return null;
    }
    
    private String parseJsonUrl(String json) {
        try {
            String urlKey = "\"url\":\"";
            int urlKeyIndex = json.indexOf(urlKey);
            if (urlKeyIndex == -1) {
                Log.e(TAG, "JSON中没有找到url键");
                return null;
            }
            
            int start = urlKeyIndex + 7;
            int end = json.indexOf("\"", start);
            
            if (end > start) {
                return json.substring(start, end);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析JSON失败", e);
        }
        return null;
    }
}