package com.mohnish.aircanvas.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Lightweight privacy overlay intentionally placed below the design canvas.
 * It obscures the user's face during screen recording without touching camera frames.
 */
public final class HackerMaskView extends View {
    private final Paint hoodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint neonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hoodPath = new Path();
    private final RectF maskRect = new RectF();
    private final RectF faceRect = new RectF();
    private final RectF eyeBarRect = new RectF();
    private float centerX = 0.5f;
    private float centerY = 0.34f;
    private float size = 0.34f;
    private long lastAnimationTick;
    private int animationStep;

    public HackerMaskView(Context context) {
        this(context, null);
    }

    public HackerMaskView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(false);
        setFocusable(false);
        setVisibility(GONE);

        hoodPaint.setColor(0xF20A0F14);
        hoodPaint.setStyle(Paint.Style.FILL);
        facePaint.setColor(0xFF020507);
        facePaint.setStyle(Paint.Style.FILL);
        neonPaint.setColor(0xFF40FF86);
        neonPaint.setStyle(Paint.Style.STROKE);
        neonPaint.setStrokeWidth(dp(2f));
        neonPaint.setStrokeCap(Paint.Cap.ROUND);
        dimPaint.setColor(0xA6000000);
        dimPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFF40FF86);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(dp(11f));
    }

    public void configure(boolean enabled, float centerX, float centerY, float size) {
        this.centerX = clamp(centerX, 0.15f, 0.85f);
        this.centerY = clamp(centerY, 0.16f, 0.76f);
        this.size = clamp(size, 0.20f, 0.60f);
        setVisibility(enabled ? VISIBLE : GONE);
        invalidate();
    }

    public boolean isMaskEnabled() {
        return getVisibility() == VISIBLE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastAnimationTick >= 90L) {
            lastAnimationTick = now;
            animationStep = (animationStep + 1) % 24;
        }

        float base = Math.min(getWidth(), getHeight()) * size;
        float cx = getWidth() * centerX;
        float cy = getHeight() * centerY;
        float width = base * 1.18f;
        float height = base * 1.46f;
        maskRect.set(cx - width * 0.5f, cy - height * 0.48f,
                cx + width * 0.5f, cy + height * 0.52f);

        hoodPath.reset();
        hoodPath.moveTo(cx, maskRect.top - height * 0.08f);
        hoodPath.cubicTo(
                maskRect.left - width * 0.18f,
                maskRect.top + height * 0.15f,
                maskRect.left - width * 0.12f,
                maskRect.bottom - height * 0.04f,
                cx,
                maskRect.bottom + height * 0.14f
        );
        hoodPath.cubicTo(
                maskRect.right + width * 0.12f,
                maskRect.bottom - height * 0.04f,
                maskRect.right + width * 0.18f,
                maskRect.top + height * 0.15f,
                cx,
                maskRect.top - height * 0.08f
        );
        canvas.drawPath(hoodPath, hoodPaint);

        faceRect.set(
                maskRect.left + width * 0.13f,
                maskRect.top + height * 0.13f,
                maskRect.right - width * 0.13f,
                maskRect.bottom - height * 0.10f
        );
        canvas.drawOval(faceRect, facePaint);

        eyeBarRect.set(
                faceRect.left + width * 0.08f,
                faceRect.top + height * 0.27f,
                faceRect.right - width * 0.08f,
                faceRect.top + height * 0.43f
        );
        canvas.drawRoundRect(eyeBarRect, dp(7f), dp(7f), dimPaint);
        float scanX = eyeBarRect.left + (eyeBarRect.width() * animationStep / 23f);
        canvas.drawLine(scanX, eyeBarRect.top, scanX, eyeBarRect.bottom, neonPaint);

        float eyeY = eyeBarRect.centerY();
        float eyeOffset = eyeBarRect.width() * 0.23f;
        canvas.drawLine(cx - eyeOffset - dp(13f), eyeY, cx - eyeOffset + dp(13f), eyeY, neonPaint);
        canvas.drawLine(cx + eyeOffset - dp(13f), eyeY, cx + eyeOffset + dp(13f), eyeY, neonPaint);

        float circuitTop = faceRect.top + height * 0.52f;
        float circuitBottom = faceRect.bottom - height * 0.12f;
        for (int row = 0; row < 4; row++) {
            float y = circuitTop + (circuitBottom - circuitTop) * row / 3f;
            float inset = (row % 2 == 0 ? 0.18f : 0.27f) * faceRect.width();
            canvas.drawLine(faceRect.left + inset, y, faceRect.right - inset, y, neonPaint);
        }
        canvas.drawLine(cx, circuitTop, cx, circuitBottom, neonPaint);
        canvas.drawCircle(cx, circuitBottom, dp(3.4f), neonPaint);

        canvas.drawText("PRIVACY // HACKER", cx, maskRect.bottom + height * 0.08f, textPaint);
        postInvalidateDelayed(90L);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return (minimum + maximum) * 0.5f;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
