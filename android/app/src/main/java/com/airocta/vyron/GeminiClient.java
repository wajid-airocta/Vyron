package com.airocta.vyron;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

final class GeminiClient {
    private static final String MODEL = "gemini-3.1-flash-lite";
    private GeminiClient() {}

    static String ask(String key, JSONArray history) {
        if (key == null || key.trim().isEmpty()) return "Open API settings and add a Gemini key.";
        HttpsURLConnection connection = null;
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent");
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(45000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("x-goog-api-key", key);
            JSONObject body = new JSONObject().put("contents", history);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            InputStream input = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String payload = new String(readLimited(input), StandardCharsets.UTF_8);
            JSONObject response = new JSONObject(payload);
            if (code >= 400) {
                JSONObject error = response.optJSONObject("error");
                String detail = error == null ? "HTTP " + code : error.optString("message", "HTTP " + code);
                return "Gemini error: " + detail.replace(key, "[redacted]");
            }
            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0)
                return "Gemini returned no answer. Try another prompt.";
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) return "Gemini returned no text answer.";
            JSONArray parts = content.optJSONArray("parts");
            StringBuilder answer = new StringBuilder();
            if (parts != null) for (int i = 0; i < parts.length(); i++)
                answer.append(parts.getJSONObject(i).optString("text"));
            return answer.length() == 0 ? "Gemini returned no text answer." : answer.toString();
        } catch (Exception error) {
            return "Gemini is unavailable. Check the phone's internet connection and API key.";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static JSONObject turn(String role, String text) throws Exception {
        return new JSONObject().put("role", role).put("parts",
                new JSONArray().put(new JSONObject().put("text", text)));
    }

    private static byte[] readLimited(InputStream input) throws Exception {
        if (input == null) return new byte[0];
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1 && out.size() < 1024 * 1024)
                out.write(buffer, 0, Math.min(count, 1024 * 1024 - out.size()));
            return out.toByteArray();
        }
    }
}
