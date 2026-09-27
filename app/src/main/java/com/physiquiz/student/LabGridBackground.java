package com.physiquiz.student;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * A circuit-board / grid pattern painted behind the screen content, used only for the
 * "lab/neon" dark theme. Draws a bold, clearly-visible dotted grid plus a handful of thin
 * right-angled trace lines (like PCB traces) in the given tint color — meant to actually read as
 * a neon circuit motif, not just a faint texture. Pure Canvas drawing, no bitmap assets, so it costs nothing to ship and
 * scales to any screen size automatically. Safe to use as a background for any screen: it never
 * intercepts touches (extends plain View, no click handling) and draws once per layout pass unless
 * invalidated.
 */
public class LabGridBackground extends View {

    private int tint = 0x00000000;
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tracePath = new Path();
    private float density = 1f;

    public LabGridBackground(Context context) {
        super(context);
        init();
    }

    public LabGridBackground(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(density);
        setWillNotDraw(false);
    }

    /** Sets the base color the grid/traces are drawn in; only the alpha channel of the passed colors is used for the actual pattern (drawn bold/clearly visible by design, not just a faint texture), so pass any opaque accent color. */
    public void setTint(int color) {
        this.tint = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (tint == 0) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int r = android.graphics.Color.red(tint);
        int g = android.graphics.Color.green(tint);
        int b = android.graphics.Color.blue(tint);

        float step = 28 * density;
        dotPaint.setColor(android.graphics.Color.argb(90, r, g, b));
        float radius = density * 1.3f;
        for (float y = step; y < h; y += step) {
            for (float x = step; x < w; x += step) {
                canvas.drawCircle(x, y, radius, dotPaint);
            }
        }

        // A handful of deterministic right-angled "PCB trace" lines for extra texture — positions
        // are derived from the view's own size so they land sensibly on any screen without magic
        // per-device numbers, and stay fixed across redraws (no per-frame randomness/flicker).
        linePaint.setColor(android.graphics.Color.argb(130, r, g, b));
        linePaint.setStrokeWidth(density * 1.6f);
        int traceCount = 7;
        for (int i = 0; i < traceCount; i++) {
            float startX = w * (0.1f + 0.8f * ((i * 37) % 100) / 100f);
            float startY = h * (0.06f + 0.85f * ((i * 61) % 100) / 100f);
            float midDrop = step * (2 + (i % 3));
            float runLength = w * (0.15f + 0.1f * (i % 3));
            tracePath.reset();
            tracePath.moveTo(startX, startY);
            tracePath.lineTo(startX, startY + midDrop);
            tracePath.lineTo(startX + (i % 2 == 0 ? runLength : -runLength), startY + midDrop);
            canvas.drawPath(tracePath, linePaint);
            canvas.drawCircle(startX, startY, density * 1.6f, linePaint);
        }
    }
}
