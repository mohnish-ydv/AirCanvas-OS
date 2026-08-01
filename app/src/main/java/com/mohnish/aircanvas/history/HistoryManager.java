package com.mohnish.aircanvas.history;

import com.mohnish.aircanvas.model.DesignDocument;

import java.util.ArrayDeque;
import java.util.Deque;

public final class HistoryManager {
    private final int capacity;
    private final long byteBudget;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private long undoBytes;
    private long redoBytes;

    public HistoryManager(int capacity) {
        this(capacity, 16L * 1024L * 1024L);
    }

    public HistoryManager(int capacity, long byteBudget) {
        this.capacity = Math.max(1, capacity);
        this.byteBudget = Math.max(512L * 1024L, byteBudget);
    }

    public void checkpoint(DesignDocument current) {
        if (current == null) {
            return;
        }
        Snapshot snapshot = snapshotOf(current);
        undo.push(snapshot);
        undoBytes += snapshot.estimatedBytes;
        undoBytes = trim(undo, undoBytes);
        redo.clear();
        redoBytes = 0L;
    }

    public DesignDocument undo(DesignDocument current) {
        if (undo.isEmpty() || current == null) {
            return current;
        }
        Snapshot currentSnapshot = snapshotOf(current);
        redo.push(currentSnapshot);
        redoBytes += currentSnapshot.estimatedBytes;
        redoBytes = trim(redo, redoBytes);
        Snapshot restored = undo.pop();
        undoBytes -= restored.estimatedBytes;
        return restored.document;
    }

    public DesignDocument redo(DesignDocument current) {
        if (redo.isEmpty() || current == null) {
            return current;
        }
        Snapshot currentSnapshot = snapshotOf(current);
        undo.push(currentSnapshot);
        undoBytes += currentSnapshot.estimatedBytes;
        undoBytes = trim(undo, undoBytes);
        Snapshot restored = redo.pop();
        redoBytes -= restored.estimatedBytes;
        return restored.document;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void clear() {
        undo.clear();
        redo.clear();
        undoBytes = 0L;
        redoBytes = 0L;
    }

    private Snapshot snapshotOf(DesignDocument source) {
        DesignDocument copy = source.copy();
        return new Snapshot(copy, estimateBytes(copy));
    }

    private long trim(Deque<Snapshot> stack, long bytes) {
        while (stack.size() > capacity
                || (bytes > byteBudget && stack.size() > 1)) {
            bytes -= stack.removeLast().estimatedBytes;
        }
        return Math.max(0L, bytes);
    }

    private static long estimateBytes(DesignDocument document) {
        long bytes = 512L + document.name.length() * 2L + document.template.length() * 2L;
        for (com.mohnish.aircanvas.model.CanvasElement element : document.elements) {
            bytes += 448L + element.text.length() * 2L;
            // Point coordinates are boxed Floats in the editable model.
            bytes += element.points.size() * 20L;
        }
        return bytes;
    }

    private record Snapshot(DesignDocument document, long estimatedBytes) {
    }
}
