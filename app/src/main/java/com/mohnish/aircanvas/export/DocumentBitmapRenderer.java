package com.mohnish.aircanvas.export;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.mohnish.aircanvas.model.Bounds;
import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.DesignDocument;
import com.mohnish.aircanvas.model.SpatialMesh;

public final class DocumentBitmapRenderer {
    private DocumentBitmapRenderer() {
    }

    public static Bitmap render(
            DesignDocument document,
            int requestedWidth,
            boolean transparent
    ) {
        if (document == null) {
            throw new IllegalArgumentException("Document is required");
        }
        ExportSizing.OutputSize size = ExportSizing.fit(
                document.pageWidth,
                document.pageHeight,
                requestedWidth
        );
        Bitmap bitmap = Bitmap.createBitmap(
                size.width(),
                size.height(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(transparent ? Color.TRANSPARENT : 0xFF081220);
        float scale = size.width() / document.pageWidth;
        canvas.scale(scale, scale);
        new RenderState().drawDocument(canvas, document);
        return bitmap;
    }

    private static final class RenderState {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final Matrix matrix = new Matrix();
        private final float[] sourceCorners = new float[8];
        private final float[] projectedCorners = new float[8];

        RenderState() {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            fill.setStyle(Paint.Style.FILL);
        }

        void drawDocument(Canvas canvas, DesignDocument document) {
            for (CanvasElement element : document.elements) {
                drawElement(canvas, element);
            }
        }

        private void drawElement(Canvas canvas, CanvasElement element) {
            Bounds bounds = element.bounds();
            if (element.isSpatial3d()) {
                drawSpatialElement(canvas, element);
                return;
            }
            int save = canvas.save();
            if (element.hasTransform()
                    && element.type != CanvasElement.Type.LINE
                    && element.type != CanvasElement.Type.STROKE) {
                setCorner(sourceCorners, 0, bounds.left, bounds.top);
                setCorner(sourceCorners, 2, bounds.right, bounds.top);
                setCorner(sourceCorners, 4, bounds.right, bounds.bottom);
                setCorner(sourceCorners, 6, bounds.left, bounds.bottom);
                element.projectPoint(bounds.left, bounds.top, projectedCorners, 0);
                element.projectPoint(bounds.right, bounds.top, projectedCorners, 2);
                element.projectPoint(bounds.right, bounds.bottom, projectedCorners, 4);
                element.projectPoint(bounds.left, bounds.bottom, projectedCorners, 6);
                matrix.reset();
                if (matrix.setPolyToPoly(
                        sourceCorners,
                        0,
                        projectedCorners,
                        0,
                        4
                )) {
                    canvas.concat(matrix);
                }
            }

            rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
            stroke.setColor(applyOpacity(element.strokeColor, element.opacity));
            stroke.setStrokeWidth(element.strokeWidth);
            fill.setColor(applyOpacity(element.fillColor, element.opacity));

            switch (element.type) {
                case RECTANGLE, FRAME -> {
                    float radius = element.type == CanvasElement.Type.FRAME ? 24f : 18f;
                    drawFillIfVisible(element, () ->
                            canvas.drawRoundRect(rect, radius, radius, fill)
                    );
                    canvas.drawRoundRect(rect, radius, radius, stroke);
                }
                case ELLIPSE -> {
                    drawFillIfVisible(element, () -> canvas.drawOval(rect, fill));
                    canvas.drawOval(rect, stroke);
                }
                case DIAMOND -> {
                    path.reset();
                    path.moveTo(bounds.centerX(), bounds.top);
                    path.lineTo(bounds.right, bounds.centerY());
                    path.lineTo(bounds.centerX(), bounds.bottom);
                    path.lineTo(bounds.left, bounds.centerY());
                    path.close();
                    drawFilledPath(canvas, element);
                }
                case TRIANGLE -> {
                    path.reset();
                    path.moveTo(bounds.centerX(), bounds.top);
                    path.lineTo(bounds.right, bounds.bottom);
                    path.lineTo(bounds.left, bounds.bottom);
                    path.close();
                    drawFilledPath(canvas, element);
                }
                case HEXAGON -> {
                    float inset = bounds.width() * 0.23f;
                    path.reset();
                    path.moveTo(bounds.left + inset, bounds.top);
                    path.lineTo(bounds.right - inset, bounds.top);
                    path.lineTo(bounds.right, bounds.centerY());
                    path.lineTo(bounds.right - inset, bounds.bottom);
                    path.lineTo(bounds.left + inset, bounds.bottom);
                    path.lineTo(bounds.left, bounds.centerY());
                    path.close();
                    drawFilledPath(canvas, element);
                }
                case STAR -> drawStar(canvas, element, bounds);
                case STICKY -> drawSticky(canvas, element, bounds);
                case LINE -> drawArrow(canvas, element);
                case STROKE -> drawStroke(canvas, element);
                case TEXT -> {
                    // Text content is drawn below.
                }
                case CUBE, SPHERE, CYLINDER, PYRAMID, CONE -> {
                    // Rendered through the true 3D mesh path above.
                }
            }

            if (!element.text.isEmpty()
                    && element.type != CanvasElement.Type.LINE
                    && element.type != CanvasElement.Type.STROKE) {
                drawText(canvas, element, bounds);
            }
            canvas.restoreToCount(save);
        }

        private void drawSpatialElement(Canvas canvas, CanvasElement element) {
            SpatialMesh.Projection projection = element.meshProjection();
            stroke.setColor(applyOpacity(element.strokeColor, element.opacity));
            stroke.setStrokeWidth(element.strokeWidth);
            for (SpatialMesh.Face face : projection.faces()) {
                float[] points = face.points();
                if (points.length < 6) {
                    continue;
                }
                path.reset();
                path.moveTo(points[0], points[1]);
                for (int index = 2; index + 1 < points.length; index += 2) {
                    path.lineTo(points[index], points[index + 1]);
                }
                path.close();
                if (Color.alpha(element.fillColor) > 0) {
                    fill.setColor(shadeColor(
                            applyOpacity(element.fillColor, element.opacity),
                            face.light()
                    ));
                    canvas.drawPath(path, fill);
                }
                canvas.drawPath(path, stroke);
            }
            if (!element.text.isEmpty()) {
                drawText(canvas, element, projection.bounds());
            }
        }

        private void drawFilledPath(Canvas canvas, CanvasElement element) {
            if (Color.alpha(element.fillColor) > 0) {
                canvas.drawPath(path, fill);
            }
            canvas.drawPath(path, stroke);
        }

        private void drawStar(
                Canvas canvas,
                CanvasElement element,
                Bounds bounds
        ) {
            path.reset();
            float outer = Math.min(bounds.width(), bounds.height()) * 0.5f;
            float inner = outer * 0.46f;
            for (int index = 0; index < 10; index++) {
                double angle = -Math.PI * 0.5d + index * Math.PI / 5d;
                float radius = index % 2 == 0 ? outer : inner;
                float x = bounds.centerX() + (float) Math.cos(angle) * radius;
                float y = bounds.centerY() + (float) Math.sin(angle) * radius;
                if (index == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.close();
            drawFilledPath(canvas, element);
        }

        private void drawSticky(
                Canvas canvas,
                CanvasElement element,
                Bounds bounds
        ) {
            float fold = Math.min(bounds.width(), bounds.height()) * 0.18f;
            path.reset();
            path.moveTo(bounds.left, bounds.top);
            path.lineTo(bounds.right - fold, bounds.top);
            path.lineTo(bounds.right, bounds.top + fold);
            path.lineTo(bounds.right, bounds.bottom);
            path.lineTo(bounds.left, bounds.bottom);
            path.close();
            drawFilledPath(canvas, element);
            path.reset();
            path.moveTo(bounds.right - fold, bounds.top);
            path.lineTo(bounds.right - fold, bounds.top + fold);
            path.lineTo(bounds.right, bounds.top + fold);
            canvas.drawPath(path, stroke);
        }

        private void drawArrow(Canvas canvas, CanvasElement element) {
            float startX = element.x1;
            float startY = element.y1;
            float endX = element.x2;
            float endY = element.y2;
            if (element.hasTransform()) {
                element.projectPoint(element.x1, element.y1, projectedCorners, 0);
                element.projectPoint(element.x2, element.y2, projectedCorners, 2);
                startX = projectedCorners[0];
                startY = projectedCorners[1];
                endX = projectedCorners[2];
                endY = projectedCorners[3];
            }
            canvas.drawLine(startX, startY, endX, endY, stroke);
            float angle = (float) Math.atan2(endY - startY, endX - startX);
            float arrow = 22f;
            path.reset();
            path.moveTo(endX, endY);
            path.lineTo(
                    endX - arrow * (float) Math.cos(angle - 0.55f),
                    endY - arrow * (float) Math.sin(angle - 0.55f)
            );
            path.moveTo(endX, endY);
            path.lineTo(
                    endX - arrow * (float) Math.cos(angle + 0.55f),
                    endY - arrow * (float) Math.sin(angle + 0.55f)
            );
            canvas.drawPath(path, stroke);
        }

        private void drawStroke(Canvas canvas, CanvasElement element) {
            if (element.points.size() < 2) {
                return;
            }
            path.reset();
            mapPoint(element, 0);
            path.moveTo(projectedCorners[0], projectedCorners[1]);
            for (int index = 2; index + 1 < element.points.size(); index += 2) {
                mapPoint(element, index);
                path.lineTo(projectedCorners[0], projectedCorners[1]);
            }
            canvas.drawPath(path, stroke);
        }

        private void mapPoint(CanvasElement element, int pointIndex) {
            float x = element.points.get(pointIndex);
            float y = element.points.get(pointIndex + 1);
            if (element.hasTransform()) {
                element.projectPoint(x, y, projectedCorners, 0);
            } else {
                projectedCorners[0] = x;
                projectedCorners[1] = y;
            }
        }

        private void drawText(
                Canvas canvas,
                CanvasElement element,
                Bounds bounds
        ) {
            float padding = 20f;
            int width = Math.max(1, Math.round(bounds.width() - padding * 2f));
            float size = clamp(
                    Math.min(bounds.height() * 0.28f, bounds.width() * 0.11f),
                    18f,
                    42f
            );
            text.setTextSize(size);
            text.setColor(applyOpacity(0xFFF4F8FF, element.opacity));
            StaticLayout layout = StaticLayout.Builder
                    .obtain(element.text, 0, element.text.length(), text, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setMaxLines(4)
                    .build();
            int save = canvas.save();
            canvas.translate(
                    bounds.left + padding,
                    bounds.centerY() - layout.getHeight() * 0.5f
            );
            layout.draw(canvas);
            canvas.restoreToCount(save);
        }

        private void drawFillIfVisible(
                CanvasElement element,
                Runnable draw
        ) {
            if (Color.alpha(element.fillColor) > 0) {
                draw.run();
            }
        }
    }

    private static void setCorner(
            float[] values,
            int offset,
            float x,
            float y
    ) {
        values[offset] = x;
        values[offset + 1] = y;
    }

    private static int applyOpacity(int color, float opacity) {
        int alpha = Math.round(Color.alpha(color) * clamp(opacity, 0f, 1f));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int shadeColor(int color, float light) {
        float multiplier = 0.55f + clamp(light, 0.25f, 1f) * 0.52f;
        return Color.argb(
                Color.alpha(color),
                Math.round(clamp(Color.red(color) * multiplier, 0f, 255f)),
                Math.round(clamp(Color.green(color) * multiplier, 0f, 255f)),
                Math.round(clamp(Color.blue(color) * multiplier, 0f, 255f))
        );
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

}
