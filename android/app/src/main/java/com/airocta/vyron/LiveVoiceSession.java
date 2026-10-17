package com.airocta.vyron;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;
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

/** One foreground Gemini Live conversation. Audio is 16 kHz PCM in and 24 kHz PCM out. */
final class LiveVoiceSession {
    interface Listener {
        void onState(String state);
        void onTranscript(boolean user, String text);
        void onLevel(float level);
        void onOutputLevel(float level);
        void onStopped(String message);
    }

    private static final String MODEL = "models/gemini-3.1-flash-live-preview";
    private static final String ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/"
                    + "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
    private static final int INPUT_RATE = 16000;
    private static final int OUTPUT_RATE = 24000;
    private static final int MAX_RECONNECTS = 5;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final PhoneTools tools;
    private final String name;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final LinkedBlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(80);
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build();
    private volatile WebSocket socket;
    private volatile AudioRecord recorder;
    private volatile AudioTrack speaker;
    private volatile AcousticEchoCanceler echoCanceler;
    private volatile NoiseSuppressor noiseSuppressor;
    private volatile boolean speakerWriting;
    private volatile long microphoneMutedUntil;
    private volatile boolean ready;
    private volatile boolean responseComplete;
    private volatile String apiKey;
    private volatile String resumeHandle = "";
    private volatile int reconnects;
    private Thread captureThread;
    private Thread playbackThread;
    private String userWords = "";
    private String assistantWords = "";
    private boolean completedTurn;

    LiveVoiceSession(Context context, String name, PhoneTools tools, Listener listener) {
        this.context = context.getApplicationContext();
        this.name = name == null ? "" : name.trim();
        this.listener = listener;
        this.tools = tools;
    }

    void start(String key) {
        apiKey = key;
        connect();
    }

    private void connect() {
        if (!running.get()) return;
        ready = false;
        listener.onState("CONNECTING");
        Request request = new Request.Builder().url(ENDPOINT + "?key=" +
                android.net.Uri.encode(apiKey)).build();
        WebSocket next = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                if (!running.get() || (socket!=null && socket!=ws)) {
                    ws.close(1000, "Stopped"); return;
                }
                listener.onState("AUTHENTICATING");
                try {
                    String identity = name.isEmpty() ? "" : "The user's name is " + name
                            + ". Use it naturally at the start of a conversation or a new topic, not every reply. ";
                    String prompt = "You are VYRON, a concise, natural personal voice assistant on an Android phone. "
                            + identity + "Speak conversationally and answer in the user's language. "
                            + "Use phone tools for real device facts and requested actions. Report tool errors honestly. "
                            + "Android restricts other apps' processes; usage history is not a live process list. "
                            + "Only inspect or control another app when the user explicitly asks. "
                            + "Never authorize payments, destructive actions, or permission grants. "
                            + "Do not claim you executed device or Mac actions unless a tool actually did so.";
                    JSONObject setup = new JSONObject()
                            .put("model", MODEL)
                            .put("sessionResumption", resumeHandle.isEmpty() ? new JSONObject() :
                                    new JSONObject().put("handle", resumeHandle))
                            .put("contextWindowCompression",new JSONObject()
                                    .put("slidingWindow",new JSONObject()))
                            .put("inputAudioTranscription", new JSONObject())
                            .put("outputAudioTranscription", new JSONObject())
                            .put("tools",new JSONArray().put(new JSONObject()
                                    .put("functionDeclarations",PhoneTools.declarations())))
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
                if (!running.get() || socket != ws) return;
                try {
                    JSONObject message = new JSONObject(text);
                    JSONObject update=message.optJSONObject("sessionResumptionUpdate");
                    if(update!=null && update.optBoolean("resumable")
                            && !update.optString("newHandle","").isEmpty())
                        resumeHandle=update.optString("newHandle");
                    if(message.has("goAway"))listener.onState("SESSION RENEWING");
                    if (message.has("setupComplete")) {
                        ready = true;
                        if(recorder==null)startAudio();
                        if(running.get())listener.onState("LISTENING");
                    }
                    JSONObject call=message.optJSONObject("toolCall");
                    if(call!=null){
                        JSONArray requests=call.optJSONArray("functionCalls");
                        JSONArray replies=new JSONArray();
                        if(requests!=null)for(int i=0;i<requests.length();i++){
                            JSONObject request=requests.optJSONObject(i);
                            if(request==null)continue;
                            String name=request.optString("name","");
                            JSONObject args=request.optJSONObject("args");
                            JSONObject reply=new JSONObject().put("name",name)
                                    .put("response",new JSONObject().put("result",
                                            tools.execute(name,args==null?new JSONObject():args)));
                            if(request.has("id"))reply.put("id",request.optString("id"));
                            replies.put(reply);
                        }
                        if(replies.length()>0)ws.send(new JSONObject().put("toolResponse",
                                new JSONObject().put("functionResponses",replies)).toString());
                    }
                    JSONObject content = message.optJSONObject("serverContent");
                    if (content == null) return;
                    if (content.optBoolean("interrupted")) {
                        audioQueue.clear();
                        responseComplete = false;
                        listener.onOutputLevel(0);
                        AudioTrack track = speaker;
                        if (track != null) {
                            track.pause();
                            track.flush();
                            track.play();
                        }
                        microphoneMutedUntil=SystemClock.elapsedRealtime()+700;
                    }
                    JSONObject input = content.optJSONObject("inputTranscription");
                    if (input != null && !input.optString("text").isEmpty() && !shouldMuteMicrophone()) {
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
                            microphoneMutedUntil=SystemClock.elapsedRealtime()+900;
                            if (pcm.length > 0 && !audioQueue.offer(pcm)) {
                                audioQueue.poll();
                                audioQueue.offer(pcm);
                            }
                            responseComplete = false;
                            listener.onState("SPEAKING");
                        }
                    }
                    if (content.optBoolean("turnComplete")) {
                        reconnects=0;
                        completedTurn = true;
                        responseComplete = true;
                        if (audioQueue.isEmpty() && !speakerWriting)
                            listener.onState("LISTENING");
                    }
                } catch (Exception ignored) { /* Ignore malformed non-audio event. */ }
            }

            // Gemini Live sends JSON in binary frames; handling text alone leaves setup stuck.
            @Override public void onMessage(WebSocket ws, ByteString bytes) {
                onMessage(ws, bytes.utf8());
            }

            @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                if (!running.get() || socket != ws) return;
                // Never include the authenticated WebSocket URL or key in user-visible errors.
                int code = response == null ? 0 : response.code();
                if(code==401||code==403)fail("Gemini rejected the API key. Check API settings.");
                else if(code==429)fail("Gemini Live quota reached. Try later or use another key.");
                else {
                    if(code==400)resumeHandle="";
                    reconnect(ws,"Gemini Live connection could not be restored.");
                }
            }

            @Override public void onClosed(WebSocket ws, int code, String reason) {
                if (running.get() && socket==ws) {
                    if(code==1007)resumeHandle="";
                    reconnect(ws,"Gemini Live ended the session ("+code+").");
                }
            }
        });
        socket=next;
        main.postDelayed(()->{
            if(running.get() && socket==next && !ready)
                reconnect(next,"Gemini Live did not respond. Check this phone's connection.");
        },15000);
    }

    private synchronized void reconnect(WebSocket failed,String finalMessage){
        if(!running.get() || socket!=failed)return;
        socket=null;
        ready=false;
        audioQueue.clear();
        responseComplete = false;
        failed.cancel();
        int attempt=++reconnects;
        if(attempt>MAX_RECONNECTS){fail(finalMessage);return;}
        listener.onState("RECONNECTING " + attempt + "/" + MAX_RECONNECTS);
        main.postDelayed(this::connect,Math.min(8000,500L << Math.min(attempt-1,4)));
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
            if (input.getState() != AudioRecord.STATE_INITIALIZED) {
                input.release();
                input = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        INPUT_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minInput, 6400));
            }
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
            try{
                if(AcousticEchoCanceler.isAvailable()){
                    echoCanceler=AcousticEchoCanceler.create(input.getAudioSessionId());
                    if(echoCanceler!=null)echoCanceler.setEnabled(true);
                }
                if(NoiseSuppressor.isAvailable()){
                    noiseSuppressor=NoiseSuppressor.create(input.getAudioSessionId());
                    if(noiseSuppressor!=null)noiseSuppressor.setEnabled(true);
                }
            }catch(Exception ignored){/* Some phones provide no usable audio effects. */}
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager != null) manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            output.play();
            input.startRecording();
            if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                fail("The microphone did not start. Check Android microphone access and reopen voice.");
                return;
            }
            final AudioRecord activeInput=input;
            captureThread = new Thread(() -> capture(activeInput), "vyron-live-microphone");
            playbackThread = new Thread(() -> playback(output), "vyron-live-speaker");
            captureThread.start();
            playbackThread.start();
        } catch (Exception error) { fail("Could not start microphone or speaker."); }
    }

    private void capture(AudioRecord input) {
        byte[] buffer = new byte[3200]; // 100 ms of 16 kHz mono PCM16
        while (running.get()) {
            int count = input.read(buffer, 0, buffer.length);
            if(count<0){fail("The microphone stopped. Reopen voice after checking the audio device.");return;}
            if(count==0)continue;
            WebSocket ws = socket;
            if (ws == null || !ready) continue;
            if(shouldMuteMicrophone()){
                listener.onLevel(0);
                continue;
            }
            if (ws.queueSize() > 1_000_000) continue;
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
                if (bytes != null) {
                    speakerWriting=true;
                    try{
                        listener.onOutputLevel(pcmLevel(bytes));
                        int offset=0;
                        while (offset<bytes.length && running.get()) {
                            int accepted=output.write(bytes, offset, bytes.length-offset,
                                    AudioTrack.WRITE_BLOCKING);
                            if(accepted<=0)throw new IllegalStateException("Audio output failed");
                            offset+=accepted;
                        }
                        long now=SystemClock.elapsedRealtime();
                        long duration=offset*1000L/(OUTPUT_RATE*2);
                        microphoneMutedUntil=Math.max(now,microphoneMutedUntil)+duration;
                    }finally{
                        speakerWriting=false;
                    }
                } else {
                    listener.onOutputLevel(0);
                    if (responseComplete && SystemClock.elapsedRealtime()>microphoneMutedUntil) {
                        responseComplete=false;
                        listener.onState("LISTENING");
                    }
                }
            } catch (InterruptedException ignored) { return; }
            catch (Exception ignored) {
                audioQueue.clear();
                fail("The audio output stopped. Reopen voice after checking the audio device.");
                return;
            }
        }
    }

    private static float pcmLevel(byte[] bytes) {
        int peak=0;
        for(int i=0;i+1<bytes.length;i+=2)
            peak=Math.max(peak,Math.abs((short)((bytes[i]&255)|(bytes[i+1]<<8))));
        return Math.min(1f,peak/11000f);
    }

    private boolean isOutputAudible(){
        return speakerWriting || !audioQueue.isEmpty()
                || SystemClock.elapsedRealtime()<microphoneMutedUntil;
    }

    private boolean shouldMuteMicrophone(){return !isHeadsetRoute() && isOutputAudible();}

    private boolean isHeadsetRoute(){
        AudioTrack output=speaker;
        AudioDeviceInfo device=output==null?null:output.getRoutedDevice();
        if(device==null)return false;
        switch(device.getType()){
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return true;
            default:return false;
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
        AcousticEchoCanceler echo=echoCanceler;
        echoCanceler=null;
        if(echo!=null)echo.release();
        NoiseSuppressor noise=noiseSuppressor;
        noiseSuppressor=null;
        if(noise!=null)noise.release();
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
