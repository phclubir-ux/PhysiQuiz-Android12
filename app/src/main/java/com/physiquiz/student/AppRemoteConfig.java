package com.physiquiz.student;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Live WordPress configuration loader. Never silently hides failures. */
public class AppRemoteConfig {
    public static class Result {
        public final JSONObject json;
        public final boolean success;
        public final int status;
        public final String error;
        Result(JSONObject json, boolean success, int status, String error) {
            this.json = json == null ? new JSONObject() : json;
            this.success = success; this.status = status; this.error = error == null ? "" : error;
        }
    }
    public interface Callback { void onResult(Result result); }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final String endpoint;

    public AppRemoteConfig(String baseUrl) {
        endpoint = baseUrl.replaceAll("/+$", "") + "/wp-json/physiquiz/v1/app-config";
    }

    public void load(Callback callback) {
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                String join = endpoint.contains("?") ? "&" : "?";
                URL url = new URL(endpoint + join + "physiquiz_app_config=" + System.currentTimeMillis());
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setUseCaches(false);
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
                c.setRequestProperty("Pragma", "no-cache");
                c.setRequestProperty("User-Agent", "PhysiQuizAndroid/2.4.0");
                int status = c.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
                String body = read(stream);
                if (status < 200 || status >= 300) {
                    callback.onResult(new Result(new JSONObject(), false, status, "HTTP " + status)); return;
                }
                JSONObject json = new JSONObject(body);
                if (json.length() == 0) {
                    callback.onResult(new Result(json, false, status, "پاسخ تنظیمات خالی است.")); return;
                }
                callback.onResult(new Result(json, true, status, ""));
            } catch (Exception e) {
                callback.onResult(new Result(new JSONObject(), false, 0,
                        e.getMessage() == null ? "خطای ارتباط با تنظیمات وردپرس" : e.getMessage()));
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }
}
