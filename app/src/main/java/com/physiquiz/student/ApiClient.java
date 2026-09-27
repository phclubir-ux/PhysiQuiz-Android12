package com.physiquiz.student;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ApiClient {
    public static class ApiException extends Exception {
        public final int status;
        public ApiException(String message, int status) {
            super(message);
            this.status = status;
        }
    }

    private String baseUrl;
    private String authToken;

    public ApiClient(String baseUrl, String authToken) {
        this.baseUrl = normalize(baseUrl);
        this.authToken = authToken == null ? "" : authToken;
    }

    public void setBaseUrl(String value) { this.baseUrl = normalize(value); }
    public void setAuthToken(String value) { this.authToken = value == null ? "" : value; }
    public String getBaseUrl() { return baseUrl; }

    public JSONObject get(String path) throws Exception {
        return request("GET", path, null, "");
    }

    public JSONObject post(String path, JSONObject body) throws Exception {
        return request("POST", path, body == null ? new JSONObject() : body, "");
    }

    public JSONObject postAttempt(String path, JSONObject body, String attemptToken) throws Exception {
        return request("POST", path, body == null ? new JSONObject() : body, attemptToken);
    }

    public JSONObject getAttempt(String path, String attemptToken) throws Exception {
        return request("GET", path, null, attemptToken);
    }

    private JSONObject request(String method, String path, JSONObject body, String attemptToken) throws Exception {
        if (baseUrl.isEmpty()) throw new ApiException("آدرس سایت تنظیم نشده است.", 0);
        URL url = new URL(baseUrl + (path.startsWith("/") ? path : "/" + path));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "PhysiQuizAndroid/1.0");
        if (!authToken.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + authToken);
            c.setRequestProperty("X-PhysiQuiz-Auth", authToken);
        }
        if (attemptToken != null && !attemptToken.isEmpty()) {
            c.setRequestProperty("X-PhysiQuiz-Token", attemptToken);
        }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(data); }
        }

        int status = c.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
        String text = read(stream);
        c.disconnect();

        JSONObject json;
        try { json = text == null || text.trim().isEmpty() ? new JSONObject() : new JSONObject(text); }
        catch (Exception e) { throw new ApiException("پاسخ سرور قابل خواندن نیست (HTTP " + status + ").", status); }

        if (status < 200 || status >= 300) {
            String message = json.optString("message", "خطا در ارتباط با سرور (HTTP " + status + ").");
            throw new ApiException(message, status);
        }
        return json;
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
        }
        return b.toString();
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return "";
        if (!s.startsWith("https://")) return "";
        return s;
    }
}
