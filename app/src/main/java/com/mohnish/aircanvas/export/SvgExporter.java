package com.mohnish.aircanvas.export;

import com.mohnish.aircanvas.model.Bounds;
import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.DesignDocument;
import com.mohnish.aircanvas.model.SpatialMesh;

import java.util.Locale;

public final class SvgExporter {
    private SvgExporter() {
    }

    public static String encode(DesignDocument document) {
        StringBuilder svg = new StringBuilder(Math.max(2048, document.elements.size() * 320));
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(number(document.pageWidth))
                .append(' ')
                .append(number(document.pageHeight))
                .append("\" width=\"")
                .append(number(document.pageWidth))
                .append("\" height=\"")
                .append(number(document.pageHeight))
                .append("\">\n")
                .append("<title>")
                .append(escape(document.name))
                .append("</title>\n")
                .append("<defs><marker id=\"aircanvas-arrow\" viewBox=\"0 0 10 10\" ")
                .append("refX=\"9\" refY=\"5\" markerWidth=\"7\" markerHeight=\"7\" ")
                .append("orient=\"auto-start-reverse\"><path d=\"M 0 0 L 10 5 L 0 10 z\" ")
                .append("fill=\"#79E8F2\"/></marker></defs>\n");
        for (CanvasElement element : document.elements) {
            appendElement(svg, element);
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    private static void appendElement(StringBuilder svg, CanvasElement element) {
        svg.append("<g id=\"").append(escape(element.id)).append("\"");
        if (element.opacity < 0.999f) {
            svg.append(" opacity=\"").append(number(element.opacity)).append("\"");
        }
        svg.append(">\n");
        switch (element.type) {
            case LINE -> appendLine(svg, element);
            case STROKE -> appendStroke(svg, element);
            case ELLIPSE -> appendEllipse(svg, element);
            case TRIANGLE -> appendPolygon(svg, element, triangle(element.bounds()));
            case HEXAGON -> appendPolygon(svg, element, hexagon(element.bounds()));
            case STAR -> appendPolygon(svg, element, star(element.bounds()));
            case DIAMOND -> appendPolygon(svg, element, diamond(element.bounds()));
            case STICKY -> appendPolygon(svg, element, sticky(element.bounds()));
            case RECTANGLE, FRAME, TEXT -> appendPolygon(svg, element, rectangle(element.bounds()));
            case CUBE, SPHERE, CYLINDER, PYRAMID, CONE -> appendMesh(svg, element);
        }
        if (!element.text.isEmpty()
                && element.type != CanvasElement.Type.LINE
                && element.type != CanvasElement.Type.STROKE) {
            Bounds visual = element.visualBounds();
            svg.append("<text x=\"")
                    .append(number(visual.centerX()))
                    .append("\" y=\"")
                    .append(number(visual.centerY()))
                    .append("\" fill=\"#F4F8FF\" text-anchor=\"middle\" ")
                    .append("dominant-baseline=\"middle\" font-family=\"sans-serif\" ")
                    .append("font-size=\"")
                    .append(number(Math.max(18f, Math.min(42f, visual.height() * 0.25f))))
                    .append("\">")
                    .append(escape(element.text))
                    .append("</text>\n");
        }
        svg.append("</g>\n");
    }

    private static void appendLine(StringBuilder svg, CanvasElement element) {
        float[] points = new float[4];
        element.projectPoint(element.x1, element.y1, points, 0);
        element.projectPoint(element.x2, element.y2, points, 2);
        svg.append("<line x1=\"").append(number(points[0]))
                .append("\" y1=\"").append(number(points[1]))
                .append("\" x2=\"").append(number(points[2]))
                .append("\" y2=\"").append(number(points[3]))
                .append("\" ").append(strokeStyle(element))
                .append(" marker-end=\"url(#aircanvas-arrow)\"/>\n");
    }

    private static void appendStroke(StringBuilder svg, CanvasElement element) {
        if (element.points.size() < 2) {
            return;
        }
        float[] point = new float[2];
        svg.append("<polyline points=\"");
        for (int index = 0; index + 1 < element.points.size(); index += 2) {
            element.projectPoint(
                    element.points.get(index),
                    element.points.get(index + 1),
                    point,
                    0
            );
            if (index > 0) {
                svg.append(' ');
            }
            svg.append(number(point[0])).append(',').append(number(point[1]));
        }
        svg.append("\" fill=\"none\" ").append(strokeStyle(element)).append("/>\n");
    }

    private static void appendEllipse(StringBuilder svg, CanvasElement element) {
        Bounds bounds = element.bounds();
        float[] points = new float[64];
        for (int index = 0; index < 32; index++) {
            double angle = index * Math.PI * 2d / 32d;
            float x = bounds.centerX() + (float) Math.cos(angle) * bounds.width() * 0.5f;
            float y = bounds.centerY() + (float) Math.sin(angle) * bounds.height() * 0.5f;
            points[index * 2] = x;
            points[index * 2 + 1] = y;
        }
        appendPolygon(svg, element, points);
    }

    private static void appendPolygon(
            StringBuilder svg,
            CanvasElement element,
            float[] source
    ) {
        float[] point = new float[2];
        svg.append("<polygon points=\"");
        for (int index = 0; index + 1 < source.length; index += 2) {
            element.projectPoint(source[index], source[index + 1], point, 0);
            if (index > 0) {
                svg.append(' ');
            }
            svg.append(number(point[0])).append(',').append(number(point[1]));
        }
        svg.append("\" fill=\"")
                .append(color(element.fillColor))
                .append("\" fill-opacity=\"")
                .append(number(alpha(element.fillColor)))
                .append("\" ")
                .append(strokeStyle(element))
                .append("/>\n");
    }

    private static void appendMesh(StringBuilder svg, CanvasElement element) {
        for (SpatialMesh.Face face : element.meshProjection().faces()) {
            float[] points = face.points();
            if (points.length < 6) {
                continue;
            }
            svg.append("<polygon points=\"");
            for (int index = 0; index + 1 < points.length; index += 2) {
                if (index > 0) {
                    svg.append(' ');
                }
                svg.append(number(points[index]))
                        .append(',')
                        .append(number(points[index + 1]));
            }
            int shaded = shadeColor(element.fillColor, face.light());
            svg.append("\" fill=\"")
                    .append(color(shaded))
                    .append("\" fill-opacity=\"")
                    .append(number(alpha(shaded)))
                    .append("\" ")
                    .append(strokeStyle(element))
                    .append("/>\n");
        }
    }

    private static String strokeStyle(CanvasElement element) {
        return "stroke=\"" + color(element.strokeColor)
                + "\" stroke-opacity=\"" + number(alpha(element.strokeColor))
                + "\" stroke-width=\"" + number(element.strokeWidth)
                + "\" stroke-linecap=\"round\" stroke-linejoin=\"round\"";
    }

    private static float[] rectangle(Bounds b) {
        return new float[]{
                b.left, b.top,
                b.right, b.top,
                b.right, b.bottom,
                b.left, b.bottom
        };
    }

    private static float[] triangle(Bounds b) {
        return new float[]{
                b.centerX(), b.top,
                b.right, b.bottom,
                b.left, b.bottom
        };
    }

    private static float[] diamond(Bounds b) {
        return new float[]{
                b.centerX(), b.top,
                b.right, b.centerY(),
                b.centerX(), b.bottom,
                b.left, b.centerY()
        };
    }

    private static float[] hexagon(Bounds b) {
        float inset = b.width() * 0.23f;
        return new float[]{
                b.left + inset, b.top,
                b.right - inset, b.top,
                b.right, b.centerY(),
                b.right - inset, b.bottom,
                b.left + inset, b.bottom,
                b.left, b.centerY()
        };
    }

    private static float[] sticky(Bounds b) {
        float fold = Math.min(b.width(), b.height()) * 0.18f;
        return new float[]{
                b.left, b.top,
                b.right - fold, b.top,
                b.right, b.top + fold,
                b.right, b.bottom,
                b.left, b.bottom
        };
    }

    private static float[] star(Bounds b) {
        float[] result = new float[20];
        float outer = Math.min(b.width(), b.height()) * 0.5f;
        float inner = outer * 0.46f;
        for (int index = 0; index < 10; index++) {
            double angle = -Math.PI * 0.5d + index * Math.PI / 5d;
            float radius = index % 2 == 0 ? outer : inner;
            result[index * 2] = b.centerX() + (float) Math.cos(angle) * radius;
            result[index * 2 + 1] = b.centerY() + (float) Math.sin(angle) * radius;
        }
        return result;
    }

    private static String color(int argb) {
        return String.format(Locale.US, "#%06X", argb & 0xFFFFFF);
    }

    private static float alpha(int argb) {
        return ((argb >>> 24) & 0xFF) / 255f;
    }

    private static int shadeColor(int color, float light) {
        float multiplier = 0.55f + clamp(light, 0.25f, 1f) * 0.52f;
        int red = Math.round(clamp(((color >> 16) & 0xFF) * multiplier, 0f, 255f));
        int green = Math.round(clamp(((color >> 8) & 0xFF) * multiplier, 0f, 255f));
        int blue = Math.round(clamp((color & 0xFF) * multiplier, 0f, 255f));
        return (color & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String number(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
