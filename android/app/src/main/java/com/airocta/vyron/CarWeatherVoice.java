package com.airocta.vyron;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import androidx.car.app.CarContext;
import androidx.car.app.media.CarAudioRecord;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONObject;

/** A short, user-initiated weather voice query through the Android Auto microphone. */
final class CarWeatherVoice {
    interface Listener { void onUpdate(String state, String question, String answer); }

    private final CarContext context;
    private final Listener listener;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS).build();
    private volatile WebSocket socket;
    private volatile CarAudioRecord recorder;
    private AudioFocusRequest focus;
    private volatile boolean setupReady;
    private String question = "";
    private String answer = "";

    CarWeatherVoice(CarContext context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onUpdate("Allow microphone in VYRON phone settings", "", "");
            stop(); return;
        }
        String override = SecretStore.load(context);
        String key = override == null || override.trim().isEmpty()
                ? BuildConfig.STARTER_GEMINI_API_KEY : override;
        if (key == null || key.isEmpty()) {
            listener.onUpdate("Add Gemini key on phone", "", "");
            stop(); return;
        }
        listener.onUpdate("Connecting to Gemini Live", "", "");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (active.get() && !setupReady) finish("Weather voice connection timed out");
        }, 15000);
        String endpoint = "wss://generativelanguage.googleapis.com/ws/"
                + "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
                + android.net.Uri.encode(key);
        socket = client.newWebSocket(new Request.Builder().url(endpoint).build(),
                new WebSocketListener() {
                    @Override public void onOpen(WebSocket ws, Response response) {
                        if (!active.get()) { ws.close(1000, "Stopped"); return; }
                        try {
                            WeatherRepository.Snapshot weather = WeatherRepository.current();
                            String facts = weather == null ? "Current weather is unavailable."
                                    : "Current local weather: " + weather.temperature + " Celsius, "
                                    + weather.condition + ", wind " + weather.wind + " km/h, humidity "
                                    + weather.humidity + " percent. Updated " + weather.updated + ".";
                            String instruction = "You are the VYRON weather assistant in a car. "
                                    + "Answer only weather questions, briefly and safely. "
                                    + "Do not invent a forecast, route, or other facts. " + facts;
                            JSONObject setup = new JSONObject()
                                    .put("model", "models/gemini-3.1-flash-live-preview")
                                    .put("generationConfig", new JSONObject()
                                            .put("responseModalities", new JSONArray().put("AUDIO")))
                                    .put("inputAudioTranscription", new JSONObject())
                                    .put("outputAudioTranscription", new JSONObject())
                                    .put("systemInstruction", new JSONObject().put("parts",
                                            new JSONArray().put(new JSONObject().put("text", instruction))));
                            ws.send(new JSONObject().put("setup", setup).toString());
                        } catch (Exception ignored) { finish("Weather voice setup failed"); }
                    }

                    @Override public void onMessage(WebSocket ws, String text) {
                        if (!active.get()) return;
                        try {
                            JSONObject message = new JSONObject(text);
                            if (message.has("setupComplete")) {
                                setupReady = true;
                                new Thread(CarWeatherVoice.this::record, "vyron-car-weather-mic").start();
                            }
                            JSONObject content = message.optJSONObject("serverContent");
                            if (content == null) return;
                            JSONObject input = content.optJSONObject("inputTranscription");
                            if (input != null && !input.optString("text").isEmpty()) {
                                question = input.optString("text");
                                listener.onUpdate("Weather question", question, answer);
                            }
                            JSONObject output = content.optJSONObject("outputTranscription");
                            if (output != null && !output.optString("text").isEmpty()) {
                                String next = output.optString("text");
                                answer = next.startsWith(answer) ? next
                                        : answer.endsWith(next) ? answer : answer + next;
                                listener.onUpdate("VYRON weather", question, answer);
                            }
                            if (content.optBoolean("turnComplete")) finish("Weather ready");
                        } catch (Exception ignored) { /* Ignore non-content event. */ }
                    }

                    @Override public void onMessage(WebSocket ws, ByteString bytes) {
                        onMessage(ws, bytes.utf8());
                    }

                    @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                        if (active.get()) finish(response != null && response.code() == 429
                                ? "Gemini Live quota reached" : "Weather voice unavailable");
                    }

                    @Override public void onClosed(WebSocket ws, int code, String reason) {
                        if (active.get()) finish("Weather voice ended");
                    }
                });
    }

    private void record() {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        try {
            if (!active.get()) return;
            if (manager != null) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build())
                        .setOnAudioFocusChangeListener(value -> {
                            if (value == AudioManager.AUDIOFOCUS_LOSS)
                                finish("Car microphone interrupted");
                        }).build();
                if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    finish("Car microphone is busy"); return;
                }
            }
            CarAudioRecord input = CarAudioRecord.create(context);
            recorder = input;
            input.startRecording();
            listener.onUpdate("Listening for weather question", question, answer);
            byte[] buffer = new byte[CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE];
            long started = System.currentTimeMillis();
            long lastVoice = 0;
            boolean heardVoice = false;
            while (active.get() && System.currentTimeMillis() - started < 12000) {
                int count = input.read(buffer, 0, buffer.length);
                if (count < 0) break; // Driver dismissed the car's microphone overlay.
                if (count == 0) continue;
                int peak = 0;
                for (int i = 0; i + 1 < count; i += 2) {
                    int sample = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
                    peak = Math.max(peak, Math.abs(sample));
                }
                if (peak > 1200) { heardVoice = true; lastVoice = System.currentTimeMillis(); }
                WebSocket ws = socket;
                if (ws != null && ws.queueSize() < 500_000) {
                    JSONObject audio = new JSONObject()
                            .put("data", Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP))
                            .put("mimeType", "audio/pcm;rate=16000");
                    ws.send(new JSONObject().put("realtimeInput",
                            new JSONObject().put("audio", audio)).toString());
                }
                if (heardVoice && System.currentTimeMillis() - lastVoice > 1200) break;
            }
            input.stopRecording();
            recorder = null;
            if (!active.get()) return;
            if (!heardVoice) { finish("No weather question heard"); return; }
            WebSocket ws = socket;
            if (ws != null) ws.send("{\"realtimeInput\":{\"audioStreamEnd\":true}}");
            listener.onUpdate("Getting weather answer", question, answer);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (active.get()) finish(answer.isEmpty()
                        ? "Weather response timed out" : "Weather ready");
            }, 15000);
        } catch (Exception ignored) { if (active.get()) finish("Car microphone unavailable"); }
        finally { if (manager != null && focus != null) manager.abandonAudioFocusRequest(focus); }
    }

    private void finish(String state) {
        if (!active.get()) return;
        listener.onUpdate(state, question, answer);
        stop();
    }

    void stop() {
        if (!active.getAndSet(false)) return;
        CarAudioRecord input = recorder;
        recorder = null;
        if (input != null) try { input.stopRecording(); } catch (Exception ignored) {}
        WebSocket ws = socket;
        if (ws != null) ws.close(1000, "Done");
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager != null && focus != null) manager.abandonAudioFocusRequest(focus);
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
