package com.airocta.vyron;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/** A borderless phone HUD: live device readings on rings around the voice reactor. */
final class ReactorView extends View {
    private static final int CYAN = Color.rgb(0, 220, 244);
    private static final int BLUE = Color.rgb(40, 127, 176);
    private static final int GREEN = Color.rgb(0, 223, 153);
    private static final int DIM = Color.rgb(38, 76, 87);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random stars = new Random(1494);
    private final ActivityManager activity;
    private final BatteryManager battery;
    private final long started = SystemClock.uptimeMillis();
    private String state = "READY";
    private float micLevel;
    private int memoryPercent;
    private int batteryPercent;
    private long freeMemoryGb;
    private long lastSample;

    ReactorView(Context context) {
        super(context);
        activity = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        sample();
    }

    void setState(String next) { state = next; invalidate(); }
    void setMicLevel(float level) { micLevel = micLevel * .72f + level * .28f; invalidate(); }

    private void sample() {
        lastSample = SystemClock.uptimeMillis();
        if (activity != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            activity.getMemoryInfo(info);
            if (info.totalMem > 0) {
                memoryPercent = (int) ((info.totalMem - info.availMem) * 100 / info.totalMem);
                freeMemoryGb = Math.round(info.availMem / 1073741824.0);
            }
        }
        if (battery != null) batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (SystemClock.uptimeMillis() - lastSample > 5000) sample();
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float cx = width / 2, cy = height * .48f;
        float radius = Math.min(width * .425f, height * .33f);
        long age = SystemClock.uptimeMillis() - started;
        float spin = (age % 60000) * 360f / 60000f;
        canvas.drawColor(Color.BLACK);
        stars.setSeed(1494);
        for (int i = 0; i < 72; i++) {
            float x = stars.nextFloat() * width;
            float y = stars.nextFloat() * height;
            float phase = (float) Math.sin(age / 1800f + i * .9f);
            paint.setColor(Color.argb(18 + (int) (16 * (phase + 1)), 60, 171, 195));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, (i % 7 == 0 ? 1.1f : .55f) * density, paint);
        }
        for (int i = 0; i < 4; i++) {
            float r = radius - i * 14 * density;
            paint.setColor(i == 0 ? Color.rgb(13, 100, 119) : DIM);
            paint.setStrokeWidth(i == 0 ? 1.5f * density : .8f * density);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, r, paint);
        }
        paint.setStrokeWidth(1.1f * density);
        for (int i = 0; i < 72; i++) {
            float a = (float) Math.toRadians(i * 5 + spin * .08f);
            float inner = radius - ((i % 6 == 0) ? 10 : 5) * density;
            paint.setColor(i % 6 == 0 ? CYAN : BLUE);
            canvas.drawLine(cx + (float) Math.cos(a) * inner,
                    cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * radius,
                    cy + (float) Math.sin(a) * radius, paint);
        }
        drawProgress(canvas, cx, cy, radius - 24 * density, memoryPercent / 100f,
                CYAN, -88, 265, 3.3f * density);
        drawProgress(canvas, cx, cy, radius - 37 * density,
                Math.max(0, batteryPercent) / 100f, GREEN, 90, 240, 3 * density);
        paint.setColor(Color.rgb(0, 157, 178));
        paint.setStrokeWidth(2 * density);
        RectF inner = new RectF(cx - radius * .49f, cy - radius * .49f,
                cx + radius * .49f, cy + radius * .49f);
        canvas.drawArc(inner, spin - 90, 85, false, paint);
        canvas.drawArc(inner, spin + 100, 70, false, paint);
        paint.setColor(Color.argb(75, 0, 154, 174));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius * .38f + micLevel * 6 * density, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(cx, cy, radius * .33f, paint);
        drawText(canvas, "VYRON", cx, cy - 13 * density, 22 * density, CYAN, true);
        drawText(canvas, state, cx, cy + 12 * density, 11 * density,
                "SPEAKING".equals(state) ? GREEN : Color.rgb(172, 232, 239), true);
        drawText(canvas, "MEMORY", cx - radius * .63f, cy - radius * .94f,
                9 * density, Color.rgb(121, 194, 210), true);
        drawText(canvas, memoryPercent + "%", cx - radius * .63f, cy - radius * .79f,
                13 * density, CYAN, true);
        drawText(canvas, "BATTERY", cx + radius * .65f, cy + radius * .82f,
                9 * density, Color.rgb(121, 194, 210), true);
        drawText(canvas, batteryPercent < 0 ? "N/A" : batteryPercent + "%",
                cx + radius * .65f, cy + radius * .97f, 13 * density, GREEN, true);
        drawText(canvas, freeMemoryGb + " GB FREE", cx, cy + radius + 26 * density,
                10 * density, Color.rgb(105, 191, 203), true);
        postInvalidateDelayed(60);
    }

    private void drawProgress(Canvas canvas, float cx, float cy, float radius, float progress,
                              int color, float start, float sweep, float thickness) {
        RectF bounds = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(DIM);
        canvas.drawArc(bounds, start, sweep, false, paint);
        paint.setColor(color);
        canvas.drawArc(bounds, start, sweep * Math.max(0, Math.min(1, progress)), false, paint);
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
