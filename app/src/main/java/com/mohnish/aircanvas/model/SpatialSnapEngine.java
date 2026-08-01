package com.mohnish.aircanvas.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class SpatialSnapEngine {
    private SpatialSnapEngine() {
    }

    public static MoveSnap snapMove(
            DesignDocument document,
            Set<String> selectedIds,
            Bounds movingBounds,
            float requestedDx,
            float requestedDy,
            float tolerance
    ) {
        if (document == null || movingBounds == null || tolerance <= 0f) {
            return new MoveSnap(requestedDx, requestedDy, Float.NaN, Float.NaN);
        }

        float[] movingX = {
                movingBounds.left + requestedDx,
                movingBounds.centerX() + requestedDx,
                movingBounds.right + requestedDx
        };
        float[] movingY = {
                movingBounds.top + requestedDy,
                movingBounds.centerY() + requestedDy,
                movingBounds.bottom + requestedDy
        };
        float bestAdjustX = 0f;
        float bestAdjustY = 0f;
        float bestDistanceX = tolerance + 1f;
        float bestDistanceY = tolerance + 1f;
        float guideX = Float.NaN;
        float guideY = Float.NaN;

        for (CanvasElement element : document.elements) {
            if (selectedIds != null && selectedIds.contains(element.id)) {
                continue;
            }
            Bounds target = element.visualBounds();
            for (float source : movingX) {
                for (int anchor = 0; anchor < 3; anchor++) {
                    float destination = switch (anchor) {
                        case 0 -> target.left;
                        case 1 -> target.centerX();
                        default -> target.right;
                    };
                    float delta = destination - source;
                    float distance = Math.abs(delta);
                    if (distance <= tolerance && distance < bestDistanceX) {
                        bestDistanceX = distance;
                        bestAdjustX = delta;
                        guideX = destination;
                    }
                }
            }
            for (float source : movingY) {
                for (int anchor = 0; anchor < 3; anchor++) {
                    float destination = switch (anchor) {
                        case 0 -> target.top;
                        case 1 -> target.centerY();
                        default -> target.bottom;
                    };
                    float delta = destination - source;
                    float distance = Math.abs(delta);
                    if (distance <= tolerance && distance < bestDistanceY) {
                        bestDistanceY = distance;
                        bestAdjustY = delta;
                        guideY = destination;
                    }
                }
            }
        }
        return new MoveSnap(
                requestedDx + bestAdjustX,
                requestedDy + bestAdjustY,
                guideX,
                guideY
        );
    }

    public static AnchorSnap nearestAnchor(
            DesignDocument document,
            float x,
            float y,
            float maxDistance,
            String excludedElementId
    ) {
        if (document == null || maxDistance <= 0f) {
            return AnchorSnap.none(x, y);
        }
        String bestElementId = null;
        float bestX = x;
        float bestY = y;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (CanvasElement element : document.elements) {
            if (element.type == CanvasElement.Type.LINE
                    || element.type == CanvasElement.Type.STROKE
                    || element.id.equals(excludedElementId)) {
                continue;
            }
            Bounds bounds = element.visualBounds();
            for (int anchor = 0; anchor < 5; anchor++) {
                float anchorX = switch (anchor) {
                    case 0 -> bounds.left;
                    case 2 -> bounds.right;
                    default -> bounds.centerX();
                };
                float anchorY = switch (anchor) {
                    case 1 -> bounds.top;
                    case 3 -> bounds.bottom;
                    default -> bounds.centerY();
                };
                float candidateDistance = distance(x, y, anchorX, anchorY);
                if (candidateDistance <= maxDistance
                        && candidateDistance < bestDistance) {
                    bestElementId = element.id;
                    bestX = anchorX;
                    bestY = anchorY;
                    bestDistance = candidateDistance;
                }
            }
        }
        return new AnchorSnap(bestElementId, bestX, bestY, bestDistance);
    }

    public static void refreshConnectors(DesignDocument document) {
        if (document == null) {
            return;
        }
        Map<String, CanvasElement> elementsById = new HashMap<>(
                Math.max(16, document.elements.size() * 2)
        );
        for (CanvasElement element : document.elements) {
            elementsById.put(element.id, element);
        }
        for (CanvasElement connector : document.elements) {
            if (connector.type != CanvasElement.Type.LINE) {
                continue;
            }
            CanvasElement start = elementsById.get(connector.startAnchorId);
            CanvasElement end = elementsById.get(connector.endAnchorId);
            if (isAnchorTarget(start)) {
                AnchorSnap anchor = nearestAnchorOn(start, connector.x2, connector.y2);
                connector.x1 = anchor.x;
                connector.y1 = anchor.y;
            } else {
                connector.startAnchorId = null;
            }
            if (isAnchorTarget(end)) {
                AnchorSnap anchor = nearestAnchorOn(end, connector.x1, connector.y1);
                connector.x2 = anchor.x;
                connector.y2 = anchor.y;
            } else {
                connector.endAnchorId = null;
            }
        }
    }

    private static boolean isAnchorTarget(CanvasElement element) {
        return element != null
                && element.type != CanvasElement.Type.LINE
                && element.type != CanvasElement.Type.STROKE;
    }

    private static AnchorSnap nearestAnchorOn(CanvasElement element, float x, float y) {
        Bounds bounds = element.visualBounds();
        float bestX = x;
        float bestY = y;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int anchor = 0; anchor < 5; anchor++) {
            float anchorX = switch (anchor) {
                case 0 -> bounds.left;
                case 2 -> bounds.right;
                default -> bounds.centerX();
            };
            float anchorY = switch (anchor) {
                case 1 -> bounds.top;
                case 3 -> bounds.bottom;
                default -> bounds.centerY();
            };
            float candidateDistance = distance(x, y, anchorX, anchorY);
            if (candidateDistance < bestDistance) {
                bestX = anchorX;
                bestY = anchorY;
                bestDistance = candidateDistance;
            }
        }
        return new AnchorSnap(element.id, bestX, bestY, bestDistance);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    public record MoveSnap(float dx, float dy, float guideX, float guideY) {
        public boolean hasGuideX() {
            return Float.isFinite(guideX);
        }

        public boolean hasGuideY() {
            return Float.isFinite(guideY);
        }
    }

    public record AnchorSnap(
            String elementId,
            float x,
            float y,
            float distance
    ) {
        static AnchorSnap none(float x, float y) {
            return new AnchorSnap(null, x, y, Float.POSITIVE_INFINITY);
        }

        public boolean attached() {
            return elementId != null;
        }
    }
}
