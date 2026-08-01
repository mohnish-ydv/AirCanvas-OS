package com.mohnish.aircanvas.export;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;

import com.mohnish.aircanvas.data.DocumentCodec;
import com.mohnish.aircanvas.model.DesignDocument;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class ExportManager {
    public enum Format {
        PNG("Transparent overlay PNG", "image/png", "png"),
        PORTFOLIO_PNG("Portfolio showcase poster", "image/png", "png"),
        PDF("PDF document", "application/pdf", "pdf"),
        SVG("Editable SVG vector", "image/svg+xml", "svg"),
        JSON("Editable AirCanvas JSON", "application/json", "json");

        public final String label;
        public final String mimeType;
        public final String extension;

        Format(String label, String mimeType, String extension) {
            this.label = label;
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }

    private ExportManager() {
    }

    public static void write(
            Format format,
            OutputStream output,
            DesignDocument document
    ) throws IOException {
        switch (format) {
            case JSON -> output.write(DocumentCodec.encode(document).getBytes(StandardCharsets.UTF_8));
            case SVG -> output.write(SvgExporter.encode(document).getBytes(StandardCharsets.UTF_8));
            case PNG -> writePng(output, document);
            case PORTFOLIO_PNG -> writePortfolioPng(output, document);
            case PDF -> writePdf(output, document);
        }
        output.flush();
    }

    private static void writePng(
            OutputStream output,
            DesignDocument document
    ) throws IOException {
        Bitmap bitmap = DocumentBitmapRenderer.render(document, 2400, true);
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("PNG encoder failed");
            }
        } finally {
            bitmap.recycle();
        }
    }


    private static void writePortfolioPng(
            OutputStream output,
            DesignDocument document
    ) throws IOException {
        Bitmap bitmap = PortfolioPosterRenderer.render(document);
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Portfolio poster encoder failed");
            }
        } finally {
            bitmap.recycle();
        }
    }

    private static void writePdf(
            OutputStream output,
            DesignDocument document
    ) throws IOException {
        Bitmap bitmap = DocumentBitmapRenderer.render(document, 1600, false);
        int pageWidth = bitmap.getWidth();
        int pageHeight = bitmap.getHeight();
        PdfDocument pdf = new PdfDocument();
        try {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    1
            ).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            page.getCanvas().drawColor(Color.WHITE);
            page.getCanvas().drawBitmap(bitmap, 0f, 0f, null);
            pdf.finishPage(page);
            pdf.writeTo(output);
        } finally {
            bitmap.recycle();
            pdf.close();
        }
    }

    public static String safeBaseName(String value) {
        String safe = value == null ? "AirCanvas-Design" : value.trim();
        safe = safe.replaceAll("[^a-zA-Z0-9._-]+", "-");
        safe = safe.replaceAll("-{2,}", "-");
        safe = safe.replaceAll("(^[-.]+|[-.]+$)", "");
        return safe.isEmpty() ? "AirCanvas-Design" : safe;
    }
}
