package com.physiquiz.student;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** حلقه درصد متحرک برای صفحه نتیجه آزمون — قوس از صفر تا درصد واقعی کشیده می‌شود. */
public class ProgressRingView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float percent = 0f, shown = 0f;
    private int trackColor = 0x1F000000, arcColor = 0xFF7A7A7A, textColor = 0xFF0F172A;
    private boolean animated = false;

    public ProgressRingView(Context c) {
        super(c);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setColors(int arc, int text) {
        arcColor = arc; textColor = text; invalidate();
    }

    public void setPercent(float pct) {
        percent = Math.max(0f, Math.min(100f, pct));
        if (!animated) {
            animated = true;
            post(() -> {
                ValueAnimator a = ValueAnimator.ofFloat(0f, percent);
                a.setDuration(1100);
                a.setInterpolator(new DecelerateInterpolator());
                a.addUpdateListener(anim -> { shown = (float) anim.getAnimatedValue(); invalidate(); });
                a.start();
            });
        } else { shown = percent; invalidate(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), stroke = Math.max(10f, w * 0.09f);
        float pad = stroke / 2f + 2f;
        bounds.set(pad, pad, w - pad, h - pad);
        trackPaint.setStrokeWidth(stroke); trackPaint.setColor(trackColor);
        arcPaint.setStrokeWidth(stroke);   arcPaint.setColor(arcColor);
        canvas.drawArc(bounds, 0, 360, false, trackPaint);
        canvas.drawArc(bounds, -90, 360f * shown / 100f, false, arcPaint);
        textPaint.setColor(textColor);
        textPaint.setTextSize(w * 0.22f);
        float cy = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(Math.round(shown) + "%", w / 2f, cy, textPaint);
    }
}
