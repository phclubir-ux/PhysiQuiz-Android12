package com.physiquiz.student;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.Random;

/** جشن ذرات رنگی (فقط برای قبولی) — یک ValueAnimator و ۲۶ دایره، بدون بیت‌مپ. */
public class BurstView extends View {
    private static final int N = 26;
    private final float[] ang = new float[N], spd = new float[N], rad = new float[N];
    private final int[] cols = new int[N];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = -1f;
    private int accent = 0xFFFF7A00;

    public BurstView(Context c) { super(c); }
    public void setAccent(int c) { accent = c; }

    public void burst() {
        Random r = new Random();
        for (int i = 0; i < N; i++) {
            ang[i] = (float) (Math.PI * 2 * i / N) + r.nextFloat() * 0.4f;
            spd[i] = 0.55f + r.nextFloat() * 0.45f;
            rad[i] = 2.2f + r.nextFloat() * 2.6f;
            int v = r.nextInt(3);
            cols[i] = v == 0 ? accent : (v == 1 ? 0xFF4ADE80 : 0xFFFFD166);
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(1100);
        a.setInterpolator(new DecelerateInterpolator(0.8f));
        a.addUpdateListener(an -> {
            progress = (float) an.getAnimatedValue();
            invalidate();
            if (progress >= 1f) setVisibility(GONE);
        });
        setVisibility(VISIBLE);
        a.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (progress < 0 || progress > 1) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float dist = Math.min(cx, cy) * 1.05f;
        for (int i = 0; i < N; i++) {
            float d = dist * spd[i] * progress;
            float x = cx + (float) Math.cos(ang[i]) * d;
            float y = cy + (float) Math.sin(ang[i]) * d - progress * progress * 10f;
            paint.setColor(cols[i]);
            paint.setAlpha((int) (255 * (1f - progress)));
            canvas.drawCircle(x, y, rad[i] * (1f - progress * 0.55f), paint);
        }
    }
}
