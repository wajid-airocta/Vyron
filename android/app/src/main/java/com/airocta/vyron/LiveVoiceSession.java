package com.airocta.vyron;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONArray;
import org.json.JSONObject;

/** One foreground Gemini Live conversation. Audio is 16 kHz PCM in and 24 kHz PCM out. */
final class LiveVoiceSession {
    interface Listener {
        void onState(String state);
        void onTranscript(boolean user, String text);
        void onLevel(float level);
        void onStopped(String message);
    }

    private static final String MODEL = "models/gemini-3.1-flash-live-preview";
    private static final String ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/"
                    + "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
    private static final int INPUT_RATE = 16000;
    private static final int OUTPUT_RATE = 24000;
    private final Context context;
    private final Listener listener;
    private final String name;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final LinkedBlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(80);
    private final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build();
    private volatile WebSocket socket;
    private volatile AudioRecord recorder;
    private volatile AudioTrack speaker;
    private volatile boolean ready;
    private Thread captureThread;
    private Thread playbackThread;
    private String userWords = "";
    private String assistantWords = "";
    private boolean completedTurn;

    LiveVoiceSession(Context context, String name, Listener listener) {
        this.context = context.getApplicationContext();
        this.name = name == null ? "" : name.trim();
        this.listener = listener;
    }

    void start(String key) {
        listener.onState("CONNECTING");
        Request request = new Request.Builder().url(ENDPOINT + "?key=" +
                android.net.Uri.encode(key)).build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                if (!running.get()) { ws.close(1000, "Stopped"); return; }
                try {
                    String identity = name.isEmpty() ? "" : "The user's name is " + name
                            + ". Use it naturally at the start of a conversation or a new topic, not every reply. ";
                    String prompt = "You are VYRON, a concise, natural personal voice assistant on an Android phone. "
                            + identity + "Speak conversationally and answer in the user's language. "
                            + "You can discuss information, but do not claim you executed device or Mac actions unless a tool actually did so.";
                    JSONObject setup = new JSONObject()
                            .put("model", MODEL)
                            .put("inputAudioTranscription", new JSONObject())
                            .put("outputAudioTranscription", new JSONObject())
                            .put("systemInstruction", new JSONObject().put("parts",
                                    new JSONArray().put(new JSONObject().put("text", prompt))))
                            .put("generationConfig", new JSONObject()
                                    .put("responseModalities", new JSONArray().put("AUDIO"))
                                    .put("speechConfig", new JSONObject().put("voiceConfig",
                                            new JSONObject().put("prebuiltVoiceConfig",
                                                    new JSONObject().put("voiceName", "Charon")))));
                    ws.send(new JSONObject().put("setup", setup).toString());
                } catch (Exception error) { fail("Could not configure Gemini Live."); }
            }

            @Override public void onMessage(WebSocket ws, String text) {
                if (!running.get()) return;
                try {
                    JSONObject message = new JSONObject(text);
                    if (message.has("setupComplete")) {
                        ready = true;
                        startAudio();
                        listener.onState("LISTENING");
                    }
                    JSONObject content = message.optJSONObject("serverContent");
                    if (content == null) return;
                    if (content.optBoolean("interrupted")) {
                        audioQueue.clear();
                        AudioTrack track = speaker;
                        if (track != null) {
                            track.pause();
                            track.flush();
                            track.play();
                        }
                    }
                    JSONObject input = content.optJSONObject("inputTranscription");
                    if (input != null && !input.optString("text").isEmpty()) {
                        if (completedTurn) { userWords = ""; assistantWords = ""; completedTurn = false; }
                        userWords = mergeTranscription(userWords, input.optString("text"));
                        listener.onTranscript(true, userWords);
                    }
                    JSONObject output = content.optJSONObject("outputTranscription");
                    if (output != null && !output.optString("text").isEmpty()) {
                        assistantWords = mergeTranscription(assistantWords, output.optString("text"));
                        listener.onTranscript(false, assistantWords);
                    }
                    JSONObject turn = content.optJSONObject("modelTurn");
                    JSONArray parts = turn == null ? null : turn.optJSONArray("parts");
                    if (parts != null) for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.optJSONObject(i);
                        JSONObject inline = part == null ? null : part.optJSONObject("inlineData");
                        if (inline != null && inline.optString("mimeType").startsWith("audio/pcm")) {
                            byte[] pcm = Base64.decode(inline.optString("data"), Base64.DEFAULT);
                            if (pcm.length > 0 && !audioQueue.offer(pcm)) {
                                audioQueue.poll();
                                audioQueue.offer(pcm);
                            }
                            listener.onState("SPEAKING");
                        }
                    }
                    if (content.optBoolean("turnComplete")) {
                        completedTurn = true;
                        listener.onState("LISTENING");
                    }
                } catch (Exception ignored) { /* Ignore malformed non-audio event. */ }
            }

            @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                // Never include the authenticated WebSocket URL or key in user-visible errors.
                int code = response == null ? 0 : response.code();
                fail(code == 401 || code == 403 ? "Gemini rejected the API key. Check API settings."
                        : code == 429 ? "Gemini Live quota reached. Try later or use another key."
                        : code > 0 ? "Gemini Live connection failed (HTTP " + code + ")."
                        : "Gemini Live connection lost. Check network access.");
            }

            @Override public void onClosed(WebSocket ws, int code, String reason) {
                if (running.get()) fail("Gemini Live ended the session (" + code + ").");
            }
        });
    }

    boolean isReady() { return ready && running.get(); }

    private static String mergeTranscription(String previous, String next) {
        if (next.startsWith(previous)) return next;
        if (previous.endsWith(next)) return previous;
        return previous + next;
    }

    void sendText(String prompt) {
        WebSocket ws = socket;
        if (ws == null || !isReady()) return;
        try {
            ws.send(new JSONObject().put("realtimeInput", new JSONObject().put("text", prompt)).toString());
        } catch (Exception ignored) { listener.onState("Could not send text"); }
    }

    private void startAudio() {
        if (!running.get()) return;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Microphone permission is required for voice.");
            return;
        }
        int minInput = AudioRecord.getMinBufferSize(INPUT_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int minOutput = AudioTrack.getMinBufferSize(OUTPUT_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minInput <= 0 || minOutput <= 0) { fail("This device cannot open PCM audio."); return; }
        try {
            AudioRecord input = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    INPUT_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minInput, 6400));
            AudioTrack output = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(OUTPUT_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(minOutput, 12000))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            if (input.getState() != AudioRecord.STATE_INITIALIZED
                    || output.getState() != AudioTrack.STATE_INITIALIZED) {
                input.release(); output.release();
                fail("Could not initialize phone audio.");
                return;
            }
            recorder = input;
            speaker = output;
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager != null) manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            output.play();
            input.startRecording();
            captureThread = new Thread(() -> capture(input), "vyron-live-microphone");
            playbackThread = new Thread(() -> playback(output), "vyron-live-speaker");
            captureThread.start();
            playbackThread.start();
        } catch (Exception error) { fail("Could not start microphone or speaker."); }
    }

    private void capture(AudioRecord input) {
        byte[] buffer = new byte[3200]; // 100 ms of 16 kHz mono PCM16
        while (running.get()) {
            int count = input.read(buffer, 0, buffer.length);
            if (count <= 0) continue;
            WebSocket ws = socket;
            if (ws == null || ws.queueSize() > 1_000_000) continue;
            int peak = 0;
            for (int i = 0; i + 1 < count; i += 2) {
                int value = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
                peak = Math.max(peak, Math.abs(value));
            }
            listener.onLevel(Math.min(1f, peak / 11000f));
            try {
                JSONObject audio = new JSONObject().put("data",
                        Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP))
                        .put("mimeType", "audio/pcm;rate=16000");
                ws.send(new JSONObject().put("realtimeInput",
                        new JSONObject().put("audio", audio)).toString());
            } catch (Exception ignored) { /* The next chunk can still succeed. */ }
        }
    }

    private void playback(AudioTrack output) {
        while (running.get()) {
            try {
                byte[] bytes = audioQueue.poll(250, TimeUnit.MILLISECONDS);
                if (bytes != null) output.write(bytes, 0, bytes.length, AudioTrack.WRITE_BLOCKING);
            } catch (InterruptedException ignored) { return; }
            catch (Exception ignored) { return; }
        }
    }

    private void fail(String message) {
        if (running.get()) { stop(); listener.onStopped(message); }
    }

    void stop() {
        if (!running.getAndSet(false)) return;
        ready = false;
        WebSocket ws = socket;
        if (ws != null) ws.close(1000, "User stopped");
        audioQueue.clear();
        AudioRecord input = recorder;
        recorder = null;
        if (input != null) {
            try { input.stop(); } catch (Exception ignored) {}
            input.release();
        }
        AudioTrack output = speaker;
        speaker = null;
        if (output != null) {
            try { output.pause(); output.flush(); output.stop(); } catch (Exception ignored) {}
            output.release();
        }
        if (captureThread != null) captureThread.interrupt();
        if (playbackThread != null) playbackThread.interrupt();
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager != null) manager.abandonAudioFocus(null);
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
