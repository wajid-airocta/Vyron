package com.airocta.vyron;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.util.Locale;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

/** A borderless phone HUD: live device readings on rings around the voice reactor. */
final class ReactorView extends View {
    private static final int CYAN = Color.rgb(0, 220, 244);
    private static final int BLUE = Color.rgb(40, 127, 176);
    private static final int GREEN = Color.rgb(0, 223, 153);
    private static final int DIM = Color.rgb(38, 76, 87);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random stars = new Random(1494);
    private final Context appContext;
    private final long started = SystemClock.uptimeMillis();
    private JSONObject readings = new JSONObject();
    private boolean sampling;
    private String state = "READY";
    private float micLevel;
    private float outputLevel;
    private float displayedLevel;
    private long lastSample;

    ReactorView(Context context) {
        super(context);
        appContext = context.getApplicationContext();
        sample();
    }

    void setState(String next) { state = next; invalidate(); }
    void setMicLevel(float level) { micLevel = Math.max(0, Math.min(1, level)); invalidate(); }
    void setOutputLevel(float level) { outputLevel = Math.max(0, Math.min(1, level)); invalidate(); }

    private void sample() {
        if (sampling) return;
        sampling = true;
        lastSample = SystemClock.uptimeMillis();
        new Thread(() -> {
            JSONObject next = PhoneSystemRepository.snapshot(appContext);
            post(() -> { readings = next; sampling = false; invalidate(); });
        }, "vyron-reactor-metrics").start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (SystemClock.uptimeMillis() - lastSample > 3000) sample();
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float cx = width / 2, cy = height * .5f;
        float radius = Math.min(width * .30f, height * .30f);
        float labelRadius = radius + 35 * density;
        long age = SystemClock.uptimeMillis() - started;
        boolean speaking = "SPEAKING".equals(state);
        boolean thinking = "THINKING".equals(state) || "PROCESSING".equals(state);
        float activity = speaking ? outputLevel : micLevel;
        displayedLevel += (activity - displayedLevel) * (activity > displayedLevel ? .36f : .16f);
        float seconds = age / 1000f;
        float speed = speaking ? 1.8f : thinking ? 1.5f : 1f;
        JSONObject data = readings;
        canvas.drawColor(Color.BLACK);
        stars.setSeed(1494);
        for (int i = 0; i < 68; i++) {
            float x = stars.nextFloat() * width;
            float y = stars.nextFloat() * height;
            float phase = (float) Math.sin(age / 2000f + i * .87f);
            paint.setColor(Color.argb(13 + (int) (12 * (phase + 1)), 52, 155, 180));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, (i % 7 == 0 ? 1f : .5f) * density, paint);
        }
        for (int i = 0; i < 4; i++) {
            float r = radius - i * 12 * density;
            paint.setColor(i == 0 ? Color.rgb(13, 107, 128) : DIM);
            paint.setStrokeWidth(i == 0 ? 1.5f * density : .7f * density);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, r, paint);
        }
        for (int i = 0; i < 72; i++) {
            float a = (float) Math.toRadians(i * 5);
            float inner = radius - ((i % 6 == 0) ? 9 : 4) * density;
            paint.setColor(i % 6 == 0 ? CYAN : BLUE);
            paint.setStrokeWidth(i % 6 == 0 ? 1.2f * density : .7f * density);
            canvas.drawLine(cx + (float) Math.cos(a) * inner,
                    cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * radius,
                    cy + (float) Math.sin(a) * radius, paint);
        }
        float cpu = percent(data, "system_cpu_percent");
        boolean appCpu = Float.isNaN(cpu);
        if(appCpu)cpu=percent(data,"vyron_cpu_percent");
        float mem = percent(data, "ram_used_percent");
        float battery = percent(data, "battery_percent");
        float available = (float) data.optDouble("storage_available_gb", Double.NaN);
        float total = (float) data.optDouble("storage_total_gb", Double.NaN);
        float storage = Float.isNaN(available) || total <= 0 ? Float.NaN : 1 - available / total;
        float down = (float) data.optDouble("network_receive_kb_per_second", Double.NaN);
        float link = (float) data.optDouble("link_downstream_estimate_mbps", Double.NaN);
        float net = Float.isNaN(down) || Float.isNaN(link) || link <= 0
                ? Float.NaN : down / (link * 125);
        float temperature = (float) data.optDouble("battery_temperature_c", Double.NaN);
        drawProgress(canvas, cx, cy, radius + 7 * density, cpu, CYAN, -107, 29, 3 * density);
        drawProgress(canvas, cx, cy, radius + 7 * density, mem, Color.rgb(255,145,24), -65, 29, 3 * density);
        drawProgress(canvas, cx, cy, radius + 7 * density, net, GREEN, -22, 29, 3 * density);
        drawProgress(canvas, cx, cy, radius + 7 * density,
                Float.isNaN(temperature) ? Float.NaN : temperature / 60f,
                Color.rgb(255,93,153), 55, 29, 3 * density);
        drawProgress(canvas, cx, cy, radius + 7 * density, battery, BLUE, 92, 29, 3 * density);
        drawProgress(canvas, cx, cy, radius * .57f, storage, GREEN, 0, 360, 2.5f * density);
        // The Mac HUD uses independent, alternating shells. Voice quickens them.
        drawOrbit(canvas,cx,cy,radius*.94f,seconds*36f*speed,2,118,CYAN,1.9f*density);
        drawOrbit(canvas,cx,cy,radius*.84f,-seconds*48f*speed,3,82,BLUE,1.5f*density);
        drawOrbit(canvas,cx,cy,radius*.75f,seconds*65f*speed,1,150,GREEN,1.5f*density);
        drawOrbit(canvas,cx,cy,radius*.64f,-seconds*78f*speed,4,64,CYAN,1.2f*density);
        drawOrbit(canvas,cx,cy,radius*.54f,seconds*94f*speed,2,128,BLUE,1.2f*density);
        float spectrumRadius = radius * .42f;
        for (int i=0;i<60;i++) {
            float angle=(float)(i*Math.PI*2/60);
            float wobble=.55f+.45f*(float)Math.sin(seconds*6+i*.42f);
            float spike=radius*(.015f+displayedLevel*.20f*wobble);
            float x=(float)Math.cos(angle), y=(float)Math.sin(angle);
            paint.setColor(speaking ? CYAN : GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.3f*density);
            canvas.drawLine(cx+x*spectrumRadius,cy+y*spectrumRadius,
                    cx+x*(spectrumRadius+spike),cy+y*(spectrumRadius+spike),paint);
        }
        paint.setColor(Color.argb(55+(int)(110*displayedLevel),0,188,205));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius * (.38f + displayedLevel*.055f), paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(cx, cy, radius * .35f, paint);
        String storageText = Float.isNaN(available) ? "N/A" :
                (available >= 10 ? String.format(Locale.US, "%.0f", available) :
                        String.format(Locale.US, "%.1f", available)) + " GB";
        drawText(canvas, storageText, cx, cy - 2 * density,
                Math.min(19 * density, radius * .18f), Color.rgb(188,234,238), true);
        drawText(canvas, "SPACE FREE", cx, cy + 15 * density, 8 * density, CYAN, true);
        drawText(canvas, state, cx, cy + 29 * density, 8 * density,
                speaking ? CYAN : GREEN, true);

        drawMetric(canvas, cx, cy, labelRadius, -90, appCpu ? "APP CPU" : "CPU",
                Float.isNaN(cpu) ? "N/A" : Math.round(cpu * 100) + "%", CYAN, density);
        drawMetric(canvas, cx, cy, labelRadius, -48, "MEM",
                Float.isNaN(mem) ? "N/A" : Math.round(mem * 100) + "%", Color.rgb(255,145,24), density);
        drawMetric(canvas, cx, cy, labelRadius, -5, "NET", rate(down), GREEN, density);
        drawMetric(canvas, cx, cy, labelRadius, 35, "GPU", "N/A", BLUE, density);
        drawMetric(canvas, cx, cy, labelRadius, 72, "BATT TEMP",
                Float.isNaN(temperature) ? "N/A" : String.format(Locale.US, "%.1f°C", temperature),
                Color.rgb(255,93,153), density);
        drawMetric(canvas, cx, cy, labelRadius, 108, "BATT",
                Float.isNaN(battery) ? "N/A" : Math.round(battery * 100) + "%", BLUE, density);
        long uptime = data.optLong("uptime_minutes", -1);
        drawMetric(canvas, cx, cy, labelRadius, 148, "UPTIME",
                uptime < 0 ? "N/A" : uptime / 60 + "h " + uptime % 60 + "m", GREEN, density);
        JSONArray visible = data.optJSONArray("processes_visible_to_app");
        drawMetric(canvas, cx, cy, labelRadius, 190, "PROC",
                visible == null ? "N/A" : visible.length() + " VIS", CYAN, density);
        drawMetric(canvas, cx, cy, labelRadius, 230, "OS",
                "Android " + data.optString("android_version", "?"), Color.rgb(255,193,0), density);
        postInvalidateDelayed(33);
    }

    private void drawOrbit(Canvas canvas,float cx,float cy,float radius,float start,
                           int count,float sweep,int color,float width){
        RectF bounds=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        for(int i=0;i<count;i++)
            canvas.drawArc(bounds,start+i*360f/count,sweep,false,paint);
    }

    private void drawProgress(Canvas canvas, float cx, float cy, float radius, float progress,
                              int color, float start, float sweep, float thickness) {
        RectF bounds = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(DIM);
        canvas.drawArc(bounds, start, sweep, false, paint);
        if (!Float.isNaN(progress)) {
            paint.setColor(color);
            canvas.drawArc(bounds, start, sweep * Math.max(0, Math.min(1, progress)), false, paint);
        }
    }

    private static float percent(JSONObject data, String key) {
        if (!data.has(key) || data.isNull(key)) return Float.NaN;
        return Math.max(0, Math.min(1, (float) data.optDouble(key) / 100f));
    }

    private static String rate(float kb) {
        if (Float.isNaN(kb)) return "N/A";
        if (kb >= 1024) return String.format(Locale.US, "%.1fMB/s", kb / 1024f);
        return String.format(Locale.US, "%.0fKB/s", kb);
    }

    private void drawMetric(Canvas canvas, float cx, float cy, float radius, float angle,
                            String label, String value, int color, float dp) {
        float radians = (float) Math.toRadians(angle);
        float x = cx + (float) Math.cos(radians) * radius;
        float y = cy + (float) Math.sin(radians) * radius;
        drawText(canvas, label, x, y - 4 * dp, 8.5f * dp, Color.rgb(115,185,204), true);
        drawText(canvas, value, x, y + 11 * dp, 12.5f * dp, color, true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        float marker = radius - 17 * dp;
        canvas.drawCircle(cx + (float) Math.cos(radians) * marker,
                cy + (float) Math.sin(radians) * marker, 1.4f * dp, paint);
    }

    private void drawText(Canvas canvas, String text, float x, float y, float size,
                          int color, boolean center) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextAlign(center ? Paint.Align.CENTER : Paint.Align.LEFT);
        canvas.drawText(text, x, y, paint);
    }
}
