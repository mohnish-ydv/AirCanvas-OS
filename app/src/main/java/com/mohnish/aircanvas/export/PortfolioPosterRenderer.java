package com.mohnish.aircanvas.export;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.mohnish.aircanvas.model.DesignDocument;
import com.mohnish.aircanvas.model.ProjectInsights;

public final class PortfolioPosterRenderer {
    private static final int WIDTH = 2400;
    private static final int HEIGHT = 1350;

    private PortfolioPosterRenderer() {
    }

    public static Bitmap render(DesignDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document is required");
        }
        Bitmap poster = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(poster);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(
                0f,
                0f,
                WIDTH,
                HEIGHT,
                new int[]{0xFF06101D, 0xFF101B36, 0xFF211642},
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, WIDTH, HEIGHT, paint);
        paint.setShader(null);

        drawGlow(canvas, paint, 1840f, 180f, 420f, 0x345EE7F7);
        drawGlow(canvas, paint, 220f, 1120f, 360f, 0x30A889FF);

        paint.setColor(0xFF5EE7F7);
        paint.setTextSize(36f);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("AIRCANVAS OS / PORTFOLIO EDITION", 120f, 130f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(92f);
        canvas.drawText("Create in the air.", 120f, 280f, paint);
        canvas.drawText("Shape ideas instantly.", 120f, 385f, paint);

        paint.setColor(0xFFB9C8DC);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(32f);
        canvas.drawText("Gesture-native spatial creation • Smart Ink • True 3D", 120f, 462f, paint);

        String[] badges = {"ON-DEVICE", "SMART INK", "TRUE 3D", "ANDROID NATIVE"};
        float badgeX = 120f;
        for (String badge : badges) {
            badgeX += drawBadge(canvas, paint, badgeX, 525f, badge) + 18f;
        }

        RectF card = new RectF(760f, 120f, 2280f, 1180f);
        paint.setColor(0xEE0A1424);
        canvas.drawRoundRect(card, 48f, 48f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0x665EE7F7);
        canvas.drawRoundRect(card, 48f, 48f, paint);
        paint.setStyle(Paint.Style.FILL);

        Bitmap design = DocumentBitmapRenderer.render(document, 1420, false);
        try {
            RectF target = fitCenter(
                    design.getWidth(),
                    design.getHeight(),
                    new RectF(810f, 185f, 2230f, 1115f)
            );
            canvas.drawBitmap(design, null, target, paint);
        } finally {
            design.recycle();
        }

        ProjectInsights insights = ProjectInsights.analyze(document);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(40f);
        canvas.drawText(document.name, 120f, 700f, paint);
        paint.setColor(0xFFB9C8DC);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(28f);
        drawWrapped(canvas, paint, insights.summary(), 120f, 760f, 540f, 42f);

        paint.setColor(0xFF5EE7F7);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(30f);
        canvas.drawText("DEVELOPER", 120f, 1065f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(48f);
        canvas.drawText("Mohnish Raj", 120f, 1128f, paint);
        paint.setColor(0xFF8FA3BC);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(24f);
        canvas.drawText("Android • Computer Vision • Spatial Interaction", 120f, 1175f, paint);

        paint.setColor(0xFF8FA3BC);
        paint.setTextSize(22f);
        canvas.drawText("Generated inside AirCanvas OS", 120f, 1282f, paint);
        return poster;
    }

    private static float drawBadge(
            Canvas canvas,
            Paint paint,
            float left,
            float top,
            String text
    ) {
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(22f);
        float width = paint.measureText(text) + 44f;
        RectF rect = new RectF(left, top, left + width, top + 58f);
        paint.setColor(0x33FFFFFF);
        canvas.drawRoundRect(rect, 29f, 29f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x555EE7F7);
        canvas.drawRoundRect(rect, 29f, 29f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF4F8FF);
        canvas.drawText(text, left + 22f, top + 37f, paint);
        return width;
    }

    private static void drawGlow(
            Canvas canvas,
            Paint paint,
            float x,
            float y,
            float radius,
            int color
    ) {
        paint.setShader(new android.graphics.RadialGradient(
                x,
                y,
                radius,
                color,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);
    }

    private static RectF fitCenter(float width, float height, RectF bounds) {
        float scale = Math.min(bounds.width() / width, bounds.height() / height);
        float targetWidth = width * scale;
        float targetHeight = height * scale;
        float left = bounds.centerX() - targetWidth * 0.5f;
        float top = bounds.centerY() - targetHeight * 0.5f;
        return new RectF(left, top, left + targetWidth, top + targetHeight);
    }

    private static void drawWrapped(
            Canvas canvas,
            Paint paint,
            String text,
            float x,
            float y,
            float maxWidth,
            float lineHeight
    ) {
        StringBuilder line = new StringBuilder();
        float currentY = y;
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), x, currentY, paint);
                line.setLength(0);
                line.append(word);
                currentY += lineHeight;
            } else {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, currentY, paint);
        }
    }
}
