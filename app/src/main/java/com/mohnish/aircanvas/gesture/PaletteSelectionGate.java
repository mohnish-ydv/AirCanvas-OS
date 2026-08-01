package com.mohnish.aircanvas.gesture;

/**
 * Deterministic timing gate for gesture command palettes.
 *
 * <p>A pinch may begin a few frames before the cursor has fully settled on a
 * card. The old implementation permanently rejected that attempt. This gate
 * now remembers that the fingers are down and late-arms the hovered card as
 * soon as the short hover guard is satisfied, while still committing only on
 * release.</p>
 */
public final class PaletteSelectionGate {
    public static final long OPEN_GUARD_MS = 60L;
    public static final long MIN_HOVER_TO_ARM_MS = 30L;
    public static final long HOVER_DWELL_MS = 55L;
    public static final long DROPOUT_GRACE_MS = 220L;

    private int itemCount;
    private int hoveredIndex = -1;
    private int armedIndex = -1;
    private long openedAt;
    private long hoverStartedAt;
    private long lastInsideAt;
    private boolean pinchDown;

    public void open(int itemCount, long now) {
        this.itemCount = Math.max(0, itemCount);
        hoveredIndex = -1;
        armedIndex = -1;
        openedAt = now;
        hoverStartedAt = 0L;
        lastInsideAt = 0L;
        pinchDown = false;
    }

    public void close() {
        itemCount = 0;
        hoveredIndex = -1;
        armedIndex = -1;
        hoverStartedAt = 0L;
        lastInsideAt = 0L;
        pinchDown = false;
    }

    public void updateHover(int index, long now) {
        int normalized = index >= 0 && index < itemCount ? index : -1;
        if (normalized >= 0) {
            lastInsideAt = now;
            if (normalized != hoveredIndex) {
                hoveredIndex = normalized;
                hoverStartedAt = now;
                armedIndex = -1;
            }
            tryArm(now);
            return;
        }
        if (hoveredIndex >= 0 && now - lastInsideAt <= DROPOUT_GRACE_MS) {
            return;
        }
        hoveredIndex = -1;
        hoverStartedAt = 0L;
        armedIndex = -1;
    }

    public boolean beginPinch(long now) {
        pinchDown = true;
        tryArm(now);
        return armedIndex >= 0;
    }

    public int commitPinch(long now) {
        if (!pinchDown) {
            return -1;
        }
        tryArm(now);
        pinchDown = false;
        int selected = armedIndex;
        armedIndex = -1;
        if (selected < 0
                || selected != hoveredIndex
                || now - hoverStartedAt < HOVER_DWELL_MS
                || now - lastInsideAt > DROPOUT_GRACE_MS) {
            return -1;
        }
        return selected;
    }

    public void cancelPinch() {
        pinchDown = false;
        armedIndex = -1;
    }

    public boolean isHoverReady(long now) {
        return hoveredIndex >= 0
                && now - openedAt >= OPEN_GUARD_MS
                && now - hoverStartedAt >= HOVER_DWELL_MS
                && now - lastInsideAt <= DROPOUT_GRACE_MS;
    }

    public float hoverProgress(long now) {
        if (hoveredIndex < 0 || hoverStartedAt <= 0L) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f,
                (now - hoverStartedAt) / (float) HOVER_DWELL_MS));
    }

    public int hoveredIndex() {
        return hoveredIndex;
    }

    public int armedIndex() {
        return armedIndex;
    }

    public boolean isPinchDown() {
        return pinchDown;
    }

    private void tryArm(long now) {
        if (!pinchDown
                || hoveredIndex < 0
                || now - openedAt < OPEN_GUARD_MS
                || now - hoverStartedAt < MIN_HOVER_TO_ARM_MS
                || now - lastInsideAt > DROPOUT_GRACE_MS) {
            return;
        }
        armedIndex = hoveredIndex;
    }
}
