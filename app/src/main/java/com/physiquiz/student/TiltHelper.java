package com.physiquiz.student;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;

/** تیلت ظریف با شتاب‌سنج برای حس عمق — فقط روی نما‌های داده‌شده، زاویه محدود، بدون کتابخانه. */
public class TiltHelper implements SensorEventListener {
    private final SensorManager sm;
    private final Sensor sensor;
    private final View[] targets;
    private final float maxDeg;
    private float fx = 0, fy = 0;
    private boolean running;

    public TiltHelper(Context ctx, View[] targets, float maxDeg) {
        this.targets = targets;
        this.maxDeg = maxDeg;
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        sensor = sm != null ? sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
    }

    public void attach() {
        if (sensor == null || running) return;
        running = true;
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    public void detach() {
        if (!running) return;
        running = false;
        sm.unregisterListener(this);
        for (View v : targets) { v.setRotationX(0f); v.setRotationY(0f); }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float x = clamp(e.values[0] / 9.8f);
        float y = clamp(e.values[1] / 9.8f);
        fx = fx * 0.82f + x * 0.18f;  // نرم‌سازی تا لرزش نداشته باشد
        fy = fy * 0.82f + y * 0.18f;
        float rotY = -fx * maxDeg, rotX = fy * maxDeg;
        for (View v : targets) {
            if (v.isAttachedToWindow()) { v.setRotationY(rotY); v.setRotationX(rotX); }
        }
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }
    @Override public void onAccuracyChanged(Sensor s, int a) { }
}
