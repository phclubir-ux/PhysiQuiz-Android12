package com.physiquiz.student;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * آیکون‌های خطی تک‌رنگ (سبک Feather) کشیده‌شده با Canvas خالص — بدون vector XML،
 * بنابراین مشکل رندر vector این پروژه کاملاً دور زده می‌شود. رنگ‌پذیر با برند.
 */
public class MonoIcon extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String name = "home";

    public MonoIcon(Context c) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    public MonoIcon set(String n) { name = n; invalidate(); return this; }
    public MonoIcon color(int c) { p.setColor(c); invalidate(); return this; }

    @Override protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        if (s <= 0) return;
        p.setStrokeWidth(Math.max(1.5f, s / 11f));
        path.reset();
        switch (name) {
            case "home":
                path.moveTo(3.5f, 11.5f); path.lineTo(12f, 4.5f); path.lineTo(20.5f, 11.5f);
                path.moveTo(6f, 10f); path.lineTo(6f, 20f); path.lineTo(18f, 20f); path.lineTo(18f, 10f);
                path.moveTo(10f, 20f); path.lineTo(10f, 14.5f); path.lineTo(14f, 14.5f); path.lineTo(14f, 20f);
                break;
            case "exam":
                rr(path, 5.5f, 4f, 18.5f, 20.5f, 2f);
                path.moveTo(9.5f, 2.5f); path.lineTo(14.5f, 2.5f); path.lineTo(14.5f, 5.5f); path.lineTo(9.5f, 5.5f); path.close();
                path.moveTo(9f, 11f); path.lineTo(15f, 11f);
                path.moveTo(9f, 15f); path.lineTo(15f, 15f);
                break;
            case "chart":
                path.moveTo(5f, 4f); path.lineTo(5f, 19.5f); path.lineTo(20f, 19.5f);
                path.moveTo(9.5f, 16.5f); path.lineTo(9.5f, 11f);
                path.moveTo(13.5f, 16.5f); path.lineTo(13.5f, 8f);
                path.moveTo(17.5f, 16.5f); path.lineTo(17.5f, 12.5f);
                break;
            case "folder":
                path.moveTo(3.5f, 6.5f); path.lineTo(9f, 6.5f); path.lineTo(11f, 8.5f); path.lineTo(20.5f, 8.5f);
                path.lineTo(20.5f, 18.5f); path.lineTo(3.5f, 18.5f); path.close();
                break;
            case "user":
                path.addCircle(12f, 8f, 3.6f, Path.Direction.CW);
                path.moveTo(4.5f, 20.5f);
                path.quadTo(12f, 12.5f, 19.5f, 20.5f);
                break;
            case "eye":
                path.moveTo(2.5f, 12f);
                path.quadTo(12f, 3.5f, 21.5f, 12f);
                path.quadTo(12f, 20.5f, 2.5f, 12f);
                path.close();
                path.addCircle(12f, 12f, 3.1f, Path.Direction.CW);
                break;
            case "eyeoff":
                path.moveTo(2.5f, 12f);
                path.quadTo(12f, 3.5f, 21.5f, 12f);
                path.quadTo(12f, 20.5f, 2.5f, 12f);
                path.close();
                path.moveTo(5.5f, 19f); path.lineTo(18.5f, 5f);
                break;
            case "refresh":
                path.addCircle(12f, 12f, 7f, Path.Direction.CW);
                path.moveTo(19.5f, 5f); path.lineTo(19.5f, 9.8f); path.lineTo(15f, 9.4f);
                break;
        }
        canvas.save();
        canvas.scale(s / 24f, s / 24f);
        canvas.drawPath(path, p);
        canvas.restore();
    }

    private static void rr(Path p, float l, float t, float r, float b, float rad) {
        p.moveTo(l + rad, t); p.lineTo(r - rad, t); p.quadTo(r, t, r, t + rad);
        p.lineTo(r, b - rad); p.quadTo(r, b, r - rad, b);
        p.lineTo(l + rad, b); p.quadTo(l, b, l, b - rad);
        p.lineTo(l, t + rad); p.quadTo(l, t, l + rad, t); p.close();
    }
}
