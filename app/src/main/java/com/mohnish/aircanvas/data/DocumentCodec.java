package com.mohnish.aircanvas.data;

import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.DesignDocument;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DocumentCodec {
    public static final int SCHEMA_VERSION = 3;
    private static final int MAX_ELEMENTS = 2000;
    private static final int MAX_POINT_VALUES = 8192;

    private DocumentCodec() {
    }

    public static String encode(DesignDocument document) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("id", document.id);
            root.put("name", document.name);
            root.put("template", document.template);
            root.put("pageWidth", document.pageWidth);
            root.put("pageHeight", document.pageHeight);
            root.put("createdAt", document.createdAt);
            root.put("updatedAt", document.updatedAt);
            root.put("spatialOverlay", document.spatialOverlay);

            JSONArray elements = new JSONArray();
            for (CanvasElement element : document.elements) {
                JSONObject item = new JSONObject();
                item.put("id", element.id);
                item.put("type", element.type.name());
                item.put("x1", element.x1);
                item.put("y1", element.y1);
                item.put("x2", element.x2);
                item.put("y2", element.y2);
                item.put("text", element.text);
                item.put("strokeColor", element.strokeColor);
                item.put("fillColor", element.fillColor);
                item.put("strokeWidth", element.strokeWidth);
                item.put("groupId", element.groupId == null ? JSONObject.NULL : element.groupId);
                item.put("locked", element.locked);
                item.put("rotationX", element.rotationX);
                item.put("rotationY", element.rotationY);
                item.put("rotationZ", element.rotationZ);
                item.put("opacity", element.opacity);
                item.put("depth", element.depth);
                item.put(
                        "startAnchorId",
                        element.startAnchorId == null ? JSONObject.NULL : element.startAnchorId
                );
                item.put(
                        "endAnchorId",
                        element.endAnchorId == null ? JSONObject.NULL : element.endAnchorId
                );
                JSONArray points = new JSONArray();
                for (Float value : element.points) {
                    points.put(value);
                }
                item.put("points", points);
                elements.put(item);
            }
            root.put("elements", elements);
            return root.toString(2);
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not encode design document", exception);
        }
    }

    public static DesignDocument decode(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("schemaVersion", 1);
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new JSONException("Unsupported design schema: " + version);
        }

        List<CanvasElement> elements = new ArrayList<>();
        Set<String> elementIds = new HashSet<>();
        JSONArray source = root.optJSONArray("elements");
        if (source != null) {
            if (source.length() > MAX_ELEMENTS) {
                throw new JSONException("Project contains too many elements");
            }
            for (int index = 0; index < source.length(); index++) {
                JSONObject item = source.getJSONObject(index);
                List<Float> points = new ArrayList<>();
                JSONArray rawPoints = item.optJSONArray("points");
                if (rawPoints != null) {
                    if (rawPoints.length() > MAX_POINT_VALUES
                            || rawPoints.length() % 2 != 0) {
                        throw new JSONException("Invalid stroke point data");
                    }
                    for (int pointIndex = 0; pointIndex < rawPoints.length(); pointIndex++) {
                        double value = rawPoints.getDouble(pointIndex);
                        if (!Double.isFinite(value)) {
                            throw new JSONException("Stroke point is not finite");
                        }
                        points.add((float) value);
                    }
                }
                String rawGroup = item.isNull("groupId") ? null : item.optString("groupId", null);
                String startAnchor = item.isNull("startAnchorId")
                        ? null
                        : item.optString("startAnchorId", null);
                String endAnchor = item.isNull("endAnchorId")
                        ? null
                        : item.optString("endAnchorId", null);
                CanvasElement.Type type;
                try {
                    type = CanvasElement.Type.valueOf(item.getString("type"));
                } catch (IllegalArgumentException exception) {
                    throw new JSONException("Unknown element type");
                }
                CanvasElement element = new CanvasElement(
                        item.getString("id"),
                        type,
                        (float) item.getDouble("x1"),
                        (float) item.getDouble("y1"),
                        (float) item.getDouble("x2"),
                        (float) item.getDouble("y2"),
                        item.optString("text", ""),
                        item.optInt("strokeColor", 0xFF79E8F2),
                        item.optInt("fillColor", 0xCC14253D),
                        (float) item.optDouble("strokeWidth", 5d),
                        rawGroup,
                        item.optBoolean("locked", false),
                        (float) item.optDouble("rotationX", 0d),
                        (float) item.optDouble("rotationY", 0d),
                        (float) item.optDouble("rotationZ", 0d),
                        (float) item.optDouble("opacity", 1d),
                        (float) item.optDouble("depth", 120d),
                        startAnchor,
                        endAnchor,
                        points
                );
                if (!elementIds.add(element.id)) {
                    throw new JSONException("Project contains duplicate element IDs");
                }
                elements.add(element);
            }
        }

        return new DesignDocument(
                root.optString("id", java.util.UUID.randomUUID().toString()),
                root.optString("name", "Untitled Design"),
                root.optString("template", "Blank"),
                (float) root.optDouble("pageWidth", DesignDocument.DEFAULT_WIDTH),
                (float) root.optDouble("pageHeight", DesignDocument.DEFAULT_HEIGHT),
                root.optLong("createdAt", System.currentTimeMillis()),
                root.optLong("updatedAt", System.currentTimeMillis()),
                root.optBoolean("spatialOverlay", true),
                elements
        );
    }
}
