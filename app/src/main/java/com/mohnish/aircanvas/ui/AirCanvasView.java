package com.mohnish.aircanvas.ui;

import android.content.Context;
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
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import com.mohnish.aircanvas.export.ExportSizing;
import com.mohnish.aircanvas.gesture.GestureEvent;
import com.mohnish.aircanvas.ink.SmartInkRequest;
import com.mohnish.aircanvas.ink.SmartStrokeInterpreter;
import com.mohnish.aircanvas.model.Bounds;
import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.DesignDocument;
import com.mohnish.aircanvas.model.InkMode;
import com.mohnish.aircanvas.model.ShapeKind;
import com.mohnish.aircanvas.model.SelectionOperations;
import com.mohnish.aircanvas.model.StylePreset;
import com.mohnish.aircanvas.model.SpatialMesh;
import com.mohnish.aircanvas.model.SpatialSnapEngine;
import com.mohnish.aircanvas.model.Tool;
import com.mohnish.aircanvas.model.TransformAxis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AirCanvasView extends View {
    public interface Listener {
        void onMutationStart();

        void onDocumentChanged();

        void onSelectionChanged(int count);

        void onRequestText(float worldX, float worldY, @Nullable CanvasElement existing);

        void onSmartInkRequest(SmartInkRequest request);

        void onUndoGesture();

        void onRedoGesture();

        void onUserFeedback();

        void onCanvasMessage(String message);
    }

    private static final int MAX_EDITABLE_ELEMENTS = 800;

    private enum Action {
        NONE,
        CREATE,
        MOVE_SELECTION,
        RESIZE_SELECTION,
        ROTATE_SELECTION,
        TILT_SELECTION,
        PAN
    }

    private final Paint pagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF pageRect = new RectF();
    private final RectF elementRect = new RectF();
    private final RectF selectionRect = new RectF();
    private final Matrix elementMatrix = new Matrix();
    private final float[] sourceCorners = new float[8];
    private final float[] projectedCorners = new float[8];
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final Map<String, CanvasElement> scaleBaseline = new HashMap<>();
    private final Map<String, TextLayoutCache> textLayoutCache = new HashMap<>();
    private final ScaleGestureDetector scaleDetector;

    private DesignDocument document = new DesignDocument("Untitled Design");
    private Tool tool = Tool.SELECT;
    private ShapeKind activeShape = ShapeKind.RECTANGLE;
    private InkMode inkMode = InkMode.AUTO;
    private TransformAxis transformAxis = TransformAxis.FREE;
    private Listener listener;
    private boolean showGrid = true;
    private boolean smartSnap = true;
    private boolean spatialOverlay = true;
    private boolean presentationMode;
    private boolean multiSelect;
    private boolean fitPending = true;
    private float fitScale = 1f;
    private float zoom = 1f;
    private float panX;
    private float panY;
    private int editorInsetLeft;
    private int editorInsetTop;
    private int editorInsetRight;
    private int editorInsetBottom;
    private int inputWidth = 1;
    private int inputHeight = 1;
    private float airCursorX = -100f;
    private float airCursorY = -100f;
    private boolean airCursorVisible;
    private Action action = Action.NONE;
    private CanvasElement activeElement;
    private float actionStartWorldX;
    private float actionStartWorldY;
    private float lastWorldX;
    private float lastWorldY;
    private float actionStartScreenX;
    private float actionStartScreenY;
    private float lastScreenX;
    private float lastScreenY;
    private float selectionScalePivotX;
    private float selectionScalePivotY;
    private float selectionRotationPivotX;
    private float selectionRotationPivotY;
    private float actionStartAngle;
    private float guideX = Float.NaN;
    private float guideY = Float.NaN;
    private float touchScaleFactor = 1f;
    private boolean viewportScaling;
    private final List<CanvasElement> autoSpinElements = new ArrayList<>();
    private boolean autoSpinActive;
    private long autoSpinLastAt;
    private final Runnable autoSpinFrame = new Runnable() {
        @Override
        public void run() {
            if (!autoSpinActive) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            float deltaSeconds = autoSpinLastAt == 0L
                    ? 1f / 60f
                    : clamp((now - autoSpinLastAt) / 1000f, 1f / 120f, 0.05f);
            autoSpinLastAt = now;
            boolean rotated = false;
            for (CanvasElement element : autoSpinElements) {
                if (element.isSpatial3d() && !element.locked) {
                    element.rotate(
                            18f * deltaSeconds,
                            42f * deltaSeconds,
                            10f * deltaSeconds
                    );
                    rotated = true;
                }
            }
            if (!rotated) {
                stopAutoSpin();
                return;
            }
            invalidate();
            postOnAnimation(this);
        }
    };

    public AirCanvasView(Context context) {
        this(context, null);
    }

    public AirCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        pagePaint.setColor(0xE80A1526);
        pagePaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(0x285EE7F7);
        gridPaint.setStrokeWidth(dp(1f));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(2f));
        selectionPaint.setColor(0xFFA889FF);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(dp(1.2f));
        guidePaint.setColor(0xD65EE7F7);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0xFFA889FF);
        handlePaint.setShadowLayer(dp(8f), 0f, 0f, 0x995EE7F7);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(dp(3f));
        cursorPaint.setColor(0xFF5EE7F7);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        if (selectionCount() > 0 && tool == Tool.TRANSFORM) {
                            cancelAction();
                            touchScaleFactor = 1f;
                            beginAirScale();
                            return true;
                        }
                        viewportScaling = true;
                        cancelAction();
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (!scaleBaseline.isEmpty() && tool == Tool.TRANSFORM) {
                            touchScaleFactor = clamp(
                                    touchScaleFactor * detector.getScaleFactor(),
                                    0.25f,
                                    4f
                            );
                            updateAirScale(touchScaleFactor, 0f);
                            return true;
                        }
                        zoom = clamp(zoom * detector.getScaleFactor(), 0.55f, 4f);
                        invalidate();
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        if (!scaleBaseline.isEmpty() && tool == Tool.TRANSFORM) {
                            endAirScale();
                        }
                        viewportScaling = false;
                    }
                });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setDocument(DesignDocument document) {
        setDocument(document, true);
    }

    public void setDocument(DesignDocument document, boolean resetViewport) {
        stopAutoSpin();
        this.document = document == null ? new DesignDocument("Untitled Design") : document;
        spatialOverlay = this.document.spatialOverlay;
        selectedIds.clear();
        scaleBaseline.clear();
        textLayoutCache.clear();
        action = Action.NONE;
        activeElement = null;
        if (resetViewport) {
            zoom = 1f;
            panX = 0f;
            panY = 0f;
            fitPending = true;
        }
        notifySelectionChanged();
        invalidate();
    }

    public DesignDocument getDocument() {
        return document;
    }

    public void setTool(Tool tool) {
        cancelAction();
        this.tool = tool == null ? Tool.SELECT : tool;
        invalidate();
    }

    public Tool getTool() {
        return tool;
    }

    public ShapeKind getActiveShape() {
        return activeShape;
    }

    public void setActiveShape(ShapeKind shape) {
        activeShape = shape == null ? ShapeKind.RECTANGLE : shape;
        invalidate();
    }

    public ShapeKind cycleShape(int direction) {
        activeShape = activeShape.next(direction);
        invalidate();
        return activeShape;
    }

    public InkMode getInkMode() {
        return inkMode;
    }

    public void setInkMode(InkMode mode) {
        inkMode = mode == null ? InkMode.AUTO : mode;
        invalidate();
    }

    public InkMode cycleInkMode(int direction) {
        inkMode = inkMode.next(direction);
        invalidate();
        return inkMode;
    }

    public TransformAxis getTransformAxis() {
        return transformAxis;
    }

    public TransformAxis cycleTransformAxis(int direction) {
        transformAxis = transformAxis.next(direction);
        invalidate();
        return transformAxis;
    }

    public void setSpatialOverlay(boolean enabled) {
        spatialOverlay = enabled;
        document.spatialOverlay = enabled;
        fitPending = true;
        document.touch();
        changed();
        invalidate();
    }

    public boolean isSpatialOverlay() {
        return spatialOverlay;
    }

    public void setSmartSnap(boolean enabled) {
        smartSnap = enabled;
        guideX = Float.NaN;
        guideY = Float.NaN;
        invalidate();
    }

    public boolean isSmartSnap() {
        return smartSnap;
    }

    public void setPresentationMode(boolean enabled) {
        if (enabled) {
            stopAutoSpin();
        }
        presentationMode = enabled;
        if (enabled) {
            cancelAction();
            clearSelection();
        }
        invalidate();
    }

    public boolean isPresentationMode() {
        return presentationMode;
    }

    public void cancelActiveInteraction() {
        cancelAction();
        airCursorVisible = false;
        invalidate();
    }

    public float gestureScreenX(float normalizedX) {
        return normalizedToScreenX(normalizedX);
    }

    public float gestureScreenY(float normalizedY) {
        return normalizedToScreenY(normalizedY);
    }

    public boolean startAutoSpin() {
        if (autoSpinActive) {
            return true;
        }
        autoSpinElements.clear();
        for (String id : selectedIds) {
            CanvasElement selected = document.find(id);
            if (selected != null && selected.isSpatial3d() && !selected.locked) {
                autoSpinElements.add(selected);
            }
        }
        if (autoSpinElements.isEmpty()) {
            for (CanvasElement element : document.elements) {
                if (element.isSpatial3d() && !element.locked) {
                    autoSpinElements.add(element);
                }
            }
        }
        if (autoSpinElements.isEmpty()) {
            if (listener != null) {
                listener.onCanvasMessage("Add or select an unlocked 3D shape first");
            }
            return false;
        }
        checkpoint();
        autoSpinActive = true;
        autoSpinLastAt = 0L;
        removeCallbacks(autoSpinFrame);
        postOnAnimation(autoSpinFrame);
        return true;
    }

    public void stopAutoSpin() {
        if (!autoSpinActive) {
            autoSpinElements.clear();
            return;
        }
        autoSpinActive = false;
        removeCallbacks(autoSpinFrame);
        autoSpinLastAt = 0L;
        autoSpinElements.clear();
        document.touch();
        changed();
        invalidate();
    }

    public boolean isAutoSpinActive() {
        return autoSpinActive;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        invalidate();
    }

    public void setMultiSelect(boolean enabled) {
        multiSelect = enabled;
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public int selectionCount() {
        return selectedIds.size();
    }

    public void setInputDimensions(int width, int height) {
        inputWidth = Math.max(1, width);
        inputHeight = Math.max(1, height);
    }

    public void applyGesture(GestureEvent event) {
        float screenX = normalizedToScreenX(event.x);
        float screenY = normalizedToScreenY(event.y);
        if (presentationMode || tool == Tool.PRESENT) {
            applyPresentationGesture(event, screenX, screenY);
            return;
        }
        switch (event.type) {
            case CURSOR -> {
                airCursorX = screenX;
                airCursorY = screenY;
                airCursorVisible = true;
                invalidate();
            }
            case PINCH_START -> beginAction(screenX, screenY, true);
            case PINCH_MOVE -> updateAction(screenX, screenY);
            case PINCH_END -> endAction(screenX, screenY);
            case FIST_START -> beginFist(screenX, screenY);
            case FIST_MOVE -> updateFist(event.dx, event.dy);
            case FIST_END -> endAction(screenX, screenY);
            case OPEN_PALM_DWELL -> {
                selectAt(screenToWorldX(screenX), screenToWorldY(screenY), false);
                if (listener != null) {
                    listener.onUserFeedback();
                }
            }
            case SWIPE_LEFT -> {
                if (listener != null) {
                    listener.onUndoGesture();
                }
            }
            case SWIPE_RIGHT -> {
                if (listener != null) {
                    listener.onRedoGesture();
                }
            }
            case TWO_HAND_SCALE_START -> beginAirScale();
            case TWO_HAND_SCALE_UPDATE -> updateAirScale(event.scale, event.rotation);
            case TWO_HAND_SCALE_END -> endAirScale();
            case HAND_LOST -> {
                airCursorVisible = false;
                cancelAction();
                invalidate();
            }
            case POSE, SWIPE_UP, SWIPE_DOWN, MODE_MENU, SHAPE_MENU,
                    AUTO_SPIN_START, AUTO_SPIN_END -> {
                // Routed by MainActivity or rendered by the chrome overlay.
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getPointerCount() > 1 || viewportScaling) {
            return true;
        }
        if (presentationMode || tool == Tool.PRESENT) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                airCursorX = event.getX();
                airCursorY = event.getY();
                airCursorVisible = true;
                invalidate();
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                airCursorVisible = false;
                invalidate();
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                performClick();
            }
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                requestFocus();
                beginAction(event.getX(), event.getY(), false);
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                updateAction(event.getX(), event.getY());
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                performClick();
                endAction(event.getX(), event.getY());
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                cancelAction();
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void applyPresentationGesture(
            GestureEvent event,
            float screenX,
            float screenY
    ) {
        switch (event.type) {
            case CURSOR -> {
                airCursorX = screenX;
                airCursorY = screenY;
                airCursorVisible = true;
            }
            case FIST_START -> action = Action.PAN;
            case FIST_MOVE -> {
                if (action == Action.PAN) {
                    panX += clamp(event.dx, -0.075f, 0.075f) * getWidth();
                    panY += clamp(event.dy, -0.075f, 0.075f) * getHeight();
                }
            }
            case FIST_END -> action = Action.NONE;
            case HAND_LOST -> {
                action = Action.NONE;
                airCursorVisible = false;
            }
            default -> {
                // Presentation is read-only: editing, selection and history
                // gestures are deliberately ignored.
            }
        }
        invalidate();
    }

    private void beginAction(float screenX, float screenY, boolean air) {
        if (fitPending || viewportScaling) {
            return;
        }
        if (autoSpinActive) {
            stopAutoSpin();
        }
        if (presentationMode || tool == Tool.PRESENT) {
            airCursorX = screenX;
            airCursorY = screenY;
            airCursorVisible = true;
            action = Action.NONE;
            invalidate();
            return;
        }
        float worldX = snap(screenToWorldX(screenX));
        float worldY = snap(screenToWorldY(screenY));
        actionStartWorldX = worldX;
        actionStartWorldY = worldY;
        lastWorldX = worldX;
        lastWorldY = worldY;
        actionStartScreenX = screenX;
        actionStartScreenY = screenY;
        lastScreenX = screenX;
        lastScreenY = screenY;

        if (!isInsidePage(worldX, worldY)) {
            action = Action.PAN;
            return;
        }

        switch (tool) {
            case SELECT -> beginSelectionAction(worldX, worldY, screenX, screenY);
            case BLOCK -> {
                if (!canAddElements(1)) {
                    action = Action.NONE;
                    return;
                }
                checkpoint();
                CanvasElement.Type type = "Mind Map".equals(document.template)
                        && activeShape == ShapeKind.RECTANGLE
                        ? CanvasElement.Type.ELLIPSE
                        : activeShape.elementType;
                activeElement = new CanvasElement(type, worldX, worldY, worldX, worldY);
                activeElement.text = defaultBlockText();
                styleNewShape(activeElement);
                document.elements.add(activeElement);
                setSingleSelection(activeElement);
                action = Action.CREATE;
            }
            case LINE -> {
                if (!canAddElements(1)) {
                    action = Action.NONE;
                    return;
                }
                checkpoint();
                SpatialSnapEngine.AnchorSnap anchor = smartSnap
                        ? SpatialSnapEngine.nearestAnchor(
                        document,
                        worldX,
                        worldY,
                        worldTolerance(34f),
                        null
                )
                        : null;
                float startX = anchor != null && anchor.attached() ? anchor.x() : worldX;
                float startY = anchor != null && anchor.attached() ? anchor.y() : worldY;
                activeElement = CanvasElement.line(startX, startY, startX, startY);
                if (anchor != null && anchor.attached()) {
                    activeElement.startAnchorId = anchor.elementId();
                }
                document.elements.add(activeElement);
                setSingleSelection(activeElement);
                action = Action.CREATE;
            }
            case PEN, SMART_INK -> {
                if (!canAddElements(1)) {
                    action = Action.NONE;
                    return;
                }
                checkpoint();
                activeElement = CanvasElement.stroke(worldX, worldY);
                activeElement.strokeColor = tool == Tool.SMART_INK
                        ? 0xFFA889FF
                        : 0xFF5EE7F7;
                activeElement.strokeWidth = tool == Tool.SMART_INK ? 8f : 7f;
                document.elements.add(activeElement);
                setSingleSelection(activeElement);
                action = Action.CREATE;
            }
            case TEXT -> {
                action = Action.NONE;
                if (listener != null) {
                    listener.onRequestText(worldX, worldY, null);
                }
            }
            case TRANSFORM -> beginTransformAction(worldX, worldY, screenX, screenY);
            case ERASE -> {
                CanvasElement hit = document.hitTest(worldX, worldY, worldTolerance(24f));
                if (hit != null && !hit.locked) {
                    checkpoint();
                    deleteElementOrGroup(hit);
                    document.touch();
                    changed();
                }
                action = Action.NONE;
            }
            case PRESENT -> {
                action = Action.NONE;
            }
        }
        invalidate();
    }

    private void beginSelectionAction(float worldX, float worldY, float screenX, float screenY) {
        Bounds selectedBounds = selectionBounds();
        if (selectedBounds != null) {
            float rotateX = worldToScreenX(selectedBounds.centerX());
            float rotateY = worldToScreenY(selectedBounds.top) - dp(38f);
            if (distance(screenX, screenY, rotateX, rotateY) <= dp(26f)) {
                checkpoint();
                action = Action.ROTATE_SELECTION;
                selectionRotationPivotX = selectedBounds.centerX();
                selectionRotationPivotY = selectedBounds.centerY();
                actionStartAngle = angleDegrees(
                        worldToScreenX(selectionRotationPivotX),
                        worldToScreenY(selectionRotationPivotY),
                        screenX,
                        screenY
                );
                captureScaleBaseline();
                return;
            }
        }
        if (selectedBounds != null
                && distance(
                screenX,
                screenY,
                worldToScreenX(selectedBounds.right),
                worldToScreenY(selectedBounds.bottom)
        ) <= dp(32f)) {
            checkpoint();
            action = Action.RESIZE_SELECTION;
            selectionScalePivotX = selectedBounds.left;
            selectionScalePivotY = selectedBounds.top;
            captureScaleBaseline();
            return;
        }

        CanvasElement hit = document.hitTest(worldX, worldY, worldTolerance(28f));
        if (hit == null) {
            if (!multiSelect) {
                clearSelection();
            }
            action = Action.PAN;
            return;
        }

        if (multiSelect) {
            toggleSelection(hit);
            action = Action.NONE;
            return;
        }
        if (!selectedIds.contains(hit.id)) {
            setSingleSelection(hit);
        }
        if (!allSelectionLocked()) {
            checkpoint();
            action = Action.MOVE_SELECTION;
        }
    }

    private void beginTransformAction(
            float worldX,
            float worldY,
            float screenX,
            float screenY
    ) {
        CanvasElement hit = document.hitTest(worldX, worldY, worldTolerance(32f));
        if (hit == null && selectedIds.isEmpty()) {
            action = Action.PAN;
            return;
        }
        if (hit != null && !selectedIds.contains(hit.id)) {
            setSingleSelection(hit);
        }
        if (selectedIds.isEmpty() || allSelectionLocked()) {
            action = Action.NONE;
            return;
        }
        checkpoint();
        action = Action.TILT_SELECTION;
        actionStartScreenX = screenX;
        actionStartScreenY = screenY;
        captureScaleBaseline();
    }

    private void updateAction(float screenX, float screenY) {
        if (action == Action.NONE || fitPending) {
            return;
        }
        float worldX = snap(screenToWorldX(screenX));
        float worldY = snap(screenToWorldY(screenY));
        switch (action) {
            case CREATE -> updateCreation(worldX, worldY);
            case MOVE_SELECTION -> {
                float requestedDx = worldX - lastWorldX;
                float requestedDy = worldY - lastWorldY;
                Bounds moving = selectionBounds();
                if (smartSnap && moving != null) {
                    SpatialSnapEngine.MoveSnap snap = SpatialSnapEngine.snapMove(
                            document,
                            selectedIds,
                            moving,
                            requestedDx,
                            requestedDy,
                            worldTolerance(12f)
                    );
                    guideX = snap.guideX();
                    guideY = snap.guideY();
                    moveSelection(snap.dx(), snap.dy());
                } else {
                    guideX = Float.NaN;
                    guideY = Float.NaN;
                    moveSelection(requestedDx, requestedDy);
                }
            }
            case RESIZE_SELECTION -> {
                float initialDistance = Math.max(
                        1f,
                        distance(
                                actionStartWorldX,
                                actionStartWorldY,
                                selectionScalePivotX,
                                selectionScalePivotY
                        )
                );
                float currentDistance = distance(
                        worldX,
                        worldY,
                        selectionScalePivotX,
                        selectionScalePivotY
                );
                restoreScaleBaseline();
                scaleSelectionInternal(
                        clamp(currentDistance / initialDistance, 0.2f, 5f),
                        selectionScalePivotX,
                        selectionScalePivotY,
                        true
                );
            }
            case ROTATE_SELECTION -> {
                float currentAngle = angleDegrees(
                        worldToScreenX(selectionRotationPivotX),
                        worldToScreenY(selectionRotationPivotY),
                        screenX,
                        screenY
                );
                restoreScaleBaseline();
                rotateSelectionInternal(
                        0f,
                        0f,
                        currentAngle - actionStartAngle,
                        true
                );
            }
            case TILT_SELECTION -> {
                restoreScaleBaseline();
                float horizontal = (screenX - actionStartScreenX) * 0.34f;
                float vertical = (screenY - actionStartScreenY) * 0.34f;
                switch (transformAxis) {
                    case FREE -> rotateSelectionInternal(
                            -vertical,
                            horizontal,
                            0f,
                            true
                    );
                    case X -> rotateSelectionInternal(-vertical, 0f, 0f, true);
                    case Y -> rotateSelectionInternal(0f, horizontal, 0f, true);
                    case Z -> rotateSelectionInternal(0f, 0f, horizontal, true);
                }
            }
            case PAN -> {
                panX += screenX - lastScreenX;
                panY += screenY - lastScreenY;
            }
            case NONE -> {
            }
        }
        lastWorldX = worldX;
        lastWorldY = worldY;
        lastScreenX = screenX;
        lastScreenY = screenY;
        document.touch();
        invalidate();
    }

    private void updateCreation(float worldX, float worldY) {
        if (activeElement == null) {
            return;
        }
        if (activeElement.type == CanvasElement.Type.STROKE) {
            float distance = lastStrokeDistance(activeElement, worldX, worldY);
            if (distance >= 5f / Math.max(0.01f, contentScale())) {
                activeElement.addPoint(worldX, worldY);
            }
        } else if (activeElement.type == CanvasElement.Type.LINE) {
            SpatialSnapEngine.AnchorSnap anchor = smartSnap
                    ? SpatialSnapEngine.nearestAnchor(
                    document,
                    worldX,
                    worldY,
                    worldTolerance(34f),
                    activeElement.id
            )
                    : null;
            activeElement.x2 = anchor != null && anchor.attached() ? anchor.x() : worldX;
            activeElement.y2 = anchor != null && anchor.attached() ? anchor.y() : worldY;
            activeElement.endAnchorId = anchor != null && anchor.attached()
                    ? anchor.elementId()
                    : null;
        } else {
            activeElement.x2 = worldX;
            activeElement.y2 = worldY;
        }
    }

    private void endAction(float screenX, float screenY) {
        SmartInkRequest inkRequest = null;
        if (action == Action.CREATE && activeElement != null) {
            float worldX = snap(screenToWorldX(screenX));
            float worldY = snap(screenToWorldY(screenY));
            float dragDistance = distance(
                    actionStartScreenX,
                    actionStartScreenY,
                    screenX,
                    screenY
            );
            if (dragDistance < dp(18f)) {
                if (activeElement.type == CanvasElement.Type.LINE) {
                    activeElement.x1 = actionStartWorldX - 130f;
                    activeElement.y1 = actionStartWorldY;
                    activeElement.x2 = actionStartWorldX + 130f;
                    activeElement.y2 = actionStartWorldY;
                } else if (activeElement.type == CanvasElement.Type.STROKE) {
                    activeElement.addPoint(actionStartWorldX + 3f, actionStartWorldY + 3f);
                } else {
                    activeElement.x1 = actionStartWorldX - 130f;
                    activeElement.y1 = actionStartWorldY - 70f;
                    activeElement.x2 = actionStartWorldX + 130f;
                    activeElement.y2 = actionStartWorldY + 70f;
                }
            } else {
                updateCreation(worldX, worldY);
            }
            normalizeElement(activeElement);
            if (tool == Tool.SMART_INK
                    && activeElement.type == CanvasElement.Type.STROKE) {
                inkRequest = finishSmartInk(activeElement);
            }
            SpatialSnapEngine.refreshConnectors(document);
            document.touch();
            changed();
        } else if (action == Action.MOVE_SELECTION
                || action == Action.RESIZE_SELECTION
                || action == Action.ROTATE_SELECTION
                || action == Action.TILT_SELECTION) {
            document.touch();
            changed();
        }
        action = Action.NONE;
        activeElement = null;
        scaleBaseline.clear();
        guideX = Float.NaN;
        guideY = Float.NaN;
        invalidate();
        if (inkRequest != null && listener != null) {
            listener.onSmartInkRequest(inkRequest);
        }
    }

    public boolean applySmartInkText(SmartInkRequest request, String recognizedText) {
        if (request == null || recognizedText == null || recognizedText.isBlank()) {
            return false;
        }
        List<CanvasElement> components = new ArrayList<>();
        int count = Math.min(request.elementIds.size(), request.fingerprints.size());
        for (int index = 0; index < count; index++) {
            CanvasElement component = document.find(request.elementIds.get(index));
            if (component == null
                    || component.type != CanvasElement.Type.STROKE
                    || SmartInkRequest.fingerprint(component.points)
                    != request.fingerprints.get(index)) {
                return false;
            }
            components.add(component);
        }
        if (components.isEmpty()) {
            return false;
        }
        String text = recognizedText.trim();
        if (text.isEmpty()) {
            return false;
        }
        text = text.substring(0, Math.min(text.length(), 2000));
        Bounds bounds = request.bounds;
        float width = Math.max(150f, Math.max(bounds.width() * 1.12f, text.length() * 32f));
        float height = Math.max(88f, bounds.height() * 1.12f);
        CanvasElement element = components.get(0);
        element.type = CanvasElement.Type.TEXT;
        element.x1 = bounds.centerX() - width * 0.5f;
        element.x2 = bounds.centerX() + width * 0.5f;
        element.y1 = bounds.centerY() - height * 0.5f;
        element.y2 = bounds.centerY() + height * 0.5f;
        element.text = text;
        element.points.clear();
        element.strokeColor = 0x00000000;
        element.fillColor = 0x00000000;
        element.strokeWidth = 1f;
        for (int index = 1; index < components.size(); index++) {
            CanvasElement extra = components.get(index);
            selectedIds.remove(extra.id);
            textLayoutCache.remove(extra.id);
            document.elements.remove(extra);
        }
        constrainElement(element);
        textLayoutCache.remove(element.id);
        setSingleSelection(element);
        SpatialSnapEngine.refreshConnectors(document);
        document.touch();
        changed();
        invalidate();
        return true;
    }

    private void beginFist(float screenX, float screenY) {
        float worldX = screenToWorldX(screenX);
        float worldY = screenToWorldY(screenY);
        lastWorldX = worldX;
        lastWorldY = worldY;
        if (selectedIds.isEmpty()) {
            CanvasElement hit = document.hitTest(worldX, worldY, worldTolerance(34f));
            if (hit != null) {
                setSingleSelection(hit);
            }
        }
        if (!selectedIds.isEmpty() && !allSelectionLocked()) {
            checkpoint();
            action = Action.MOVE_SELECTION;
        } else {
            action = Action.PAN;
        }
    }

    private void updateFist(float normalizedDx, float normalizedDy) {
        normalizedDx = clamp(normalizedDx, -0.075f, 0.075f);
        normalizedDy = clamp(normalizedDy, -0.075f, 0.075f);
        if (action == Action.MOVE_SELECTION) {
            float dx = normalizedDx * getWidth() / Math.max(0.01f, contentScale());
            float dy = normalizedDy * getHeight() / Math.max(0.01f, contentScale());
            moveSelection(dx, dy);
            document.touch();
        } else if (action == Action.PAN) {
            panX += normalizedDx * getWidth();
            panY += normalizedDy * getHeight();
        }
        invalidate();
    }

    private void beginAirScale() {
        if (selectedIds.isEmpty() || allSelectionLocked()) {
            return;
        }
        Bounds bounds = selectionBounds();
        if (bounds == null) {
            return;
        }
        checkpoint();
        selectionScalePivotX = bounds.centerX();
        selectionScalePivotY = bounds.centerY();
        captureScaleBaseline();
    }

    private void updateAirScale(float factor, float rotation) {
        if (scaleBaseline.isEmpty()) {
            return;
        }
        restoreScaleBaseline();
        scaleSelectionInternal(
                clamp(factor, 0.25f, 4f),
                selectionScalePivotX,
                selectionScalePivotY,
                false
        );
        rotateSelectionInternal(0f, 0f, rotation, true);
        document.touch();
        invalidate();
    }

    private void endAirScale() {
        if (!scaleBaseline.isEmpty()) {
            document.touch();
            changed();
        }
        scaleBaseline.clear();
    }

    public void insertText(float worldX, float worldY, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!canAddElements(1)) {
            return;
        }
        checkpoint();
        CanvasElement element = CanvasElement.node(
                CanvasElement.Type.TEXT,
                clamp(worldX, 140f, document.pageWidth - 140f),
                clamp(worldY, 50f, document.pageHeight - 50f),
                280f,
                100f,
                text.trim()
        );
        element.fillColor = 0x00000000;
        element.strokeColor = 0x00000000;
        document.elements.add(element);
        setSingleSelection(element);
        document.touch();
        changed();
    }

    public void editSelectedText(String text) {
        if (selectedIds.size() != 1 || text == null || text.trim().isEmpty()) {
            return;
        }
        CanvasElement element = document.find(selectedIds.iterator().next());
        if (element == null) {
            return;
        }
        checkpoint();
        String value = text.trim();
        element.text = value.substring(0, Math.min(value.length(), 2000));
        document.touch();
        changed();
    }

    public void requestEditSelectedText() {
        if (selectedIds.size() != 1 || listener == null) {
            return;
        }
        CanvasElement element = document.find(selectedIds.iterator().next());
        if (element != null) {
            Bounds b = element.bounds();
            listener.onRequestText(b.centerX(), b.centerY(), element);
        }
    }

    public boolean deleteSelection() {
        if (unlockedSelectionCount() == 0) {
            return false;
        }
        checkpoint();
        document.elements.removeIf(element ->
                selectedIds.contains(element.id) && !element.locked
        );
        selectedIds.removeIf(id -> document.find(id) == null);
        SpatialSnapEngine.refreshConnectors(document);
        notifySelectionChanged();
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean groupSelection() {
        if (unlockedSelectionCount() < 2) {
            return false;
        }
        checkpoint();
        String group = UUID.randomUUID().toString();
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null && !element.locked) {
                element.groupId = group;
            }
        }
        document.touch();
        changed();
        return true;
    }

    public boolean ungroupSelection() {
        boolean canUngroup = false;
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null && element.groupId != null) {
                canUngroup = true;
                break;
            }
        }
        if (!canUngroup) {
            return false;
        }
        checkpoint();
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null && !element.locked) {
                element.groupId = null;
            }
        }
        document.touch();
        changed();
        return true;
    }

    public boolean scaleSelection(float factor) {
        Bounds bounds = selectionBounds();
        if (bounds == null || allSelectionLocked() || !Float.isFinite(factor)) {
            return false;
        }
        checkpoint();
        scaleSelectionInternal(factor, bounds.centerX(), bounds.centerY(), true);
        document.touch();
        changed();
        return true;
    }

    public boolean rotateSelection(TransformAxis axis, float degrees) {
        if (selectedIds.isEmpty() || allSelectionLocked() || !Float.isFinite(degrees)) {
            return false;
        }
        checkpoint();
        switch (axis == null ? TransformAxis.FREE : axis) {
            case FREE, Z -> rotateSelectionInternal(0f, 0f, degrees, true);
            case X -> rotateSelectionInternal(degrees, 0f, 0f, true);
            case Y -> rotateSelectionInternal(0f, degrees, 0f, true);
        }
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean resetSelectionRotation() {
        if (selectedIds.isEmpty() || allSelectionLocked()) {
            return false;
        }
        checkpoint();
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null && !element.locked) {
                element.setRotation(0f, 0f, 0f);
            }
        }
        SpatialSnapEngine.refreshConnectors(document);
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean duplicateSelection() {
        if (selectedIds.isEmpty()) {
            return false;
        }
        if (!canAddElements(selectedIds.size())) {
            return false;
        }
        checkpoint();
        List<CanvasElement> duplicates = new ArrayList<>();
        for (CanvasElement element : document.elements) {
            if (!selectedIds.contains(element.id)) {
                continue;
            }
            CanvasElement duplicate = element.duplicate();
            duplicate.move(36f, 36f);
            duplicates.add(duplicate);
        }
        if (duplicates.isEmpty()) {
            return false;
        }
        document.elements.addAll(duplicates);
        selectedIds.clear();
        for (CanvasElement duplicate : duplicates) {
            selectedIds.add(duplicate.id);
        }
        notifySelectionChanged();
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean setSelectionOpacity(float opacity) {
        if (selectedIds.isEmpty() || !Float.isFinite(opacity)) {
            return false;
        }
        checkpoint();
        float safeOpacity = clamp(opacity, 0.08f, 1f);
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null && !element.locked) {
                element.opacity = safeOpacity;
            }
        }
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean sendSelectionToBack() {
        if (selectedIds.isEmpty()) {
            return false;
        }
        checkpoint();
        List<CanvasElement> selected = new ArrayList<>();
        document.elements.removeIf(element -> {
            if (selectedIds.contains(element.id)) {
                selected.add(element);
                return true;
            }
            return false;
        });
        document.elements.addAll(0, selected);
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean bringSelectionToFront() {
        if (selectedIds.isEmpty()) {
            return false;
        }
        checkpoint();
        List<CanvasElement> selected = new ArrayList<>();
        document.elements.removeIf(element -> {
            if (selectedIds.contains(element.id)) {
                selected.add(element);
                return true;
            }
            return false;
        });
        document.elements.addAll(selected);
        document.touch();
        changed();
        invalidate();
        return true;
    }


    public boolean applySelectionStyle(StylePreset preset) {
        if (preset == null || selectedIds.isEmpty()) {
            return false;
        }
        checkpoint();
        int changedCount = SelectionOperations.applyStyle(document, selectedIds, preset);
        if (changedCount == 0) {
            return false;
        }
        textLayoutCache.clear();
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean setSelectionStrokeWidth(float width) {
        if (selectedIds.isEmpty() || !Float.isFinite(width)) {
            return false;
        }
        checkpoint();
        int changedCount = SelectionOperations.setStrokeWidth(document, selectedIds, width);
        if (changedCount == 0) {
            return false;
        }
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean setSelectionLocked(boolean locked) {
        if (selectedIds.isEmpty()) {
            return false;
        }
        checkpoint();
        int changedCount = SelectionOperations.setLocked(document, selectedIds, locked);
        if (changedCount == 0) {
            return false;
        }
        document.touch();
        changed();
        notifySelectionChanged();
        invalidate();
        return true;
    }

    public boolean hasLockedSelection() {
        return SelectionOperations.hasLocked(document, selectedIds);
    }

    public boolean alignSelection(SelectionOperations.Alignment alignment) {
        if (selectedIds.size() < 2 || alignment == null) {
            return false;
        }
        checkpoint();
        int changedCount = SelectionOperations.align(document, selectedIds, alignment);
        if (changedCount == 0) {
            return false;
        }
        SpatialSnapEngine.refreshConnectors(document);
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public boolean distributeSelection(SelectionOperations.Distribution distribution) {
        if (selectedIds.size() < 3 || distribution == null) {
            return false;
        }
        checkpoint();
        int changedCount = SelectionOperations.distribute(document, selectedIds, distribution);
        if (changedCount == 0) {
            return false;
        }
        SpatialSnapEngine.refreshConnectors(document);
        document.touch();
        changed();
        invalidate();
        return true;
    }

    public void resetViewport() {
        zoom = 1f;
        panX = 0f;
        panY = 0f;
        fitPending = true;
        invalidate();
    }

    public void setEditorInsets(int left, int top, int right, int bottom) {
        int safeLeft = Math.max(0, left);
        int safeTop = Math.max(0, top);
        int safeRight = Math.max(0, right);
        int safeBottom = Math.max(0, bottom);
        if (editorInsetLeft == safeLeft
                && editorInsetTop == safeTop
                && editorInsetRight == safeRight
                && editorInsetBottom == safeBottom) {
            return;
        }
        editorInsetLeft = safeLeft;
        editorInsetTop = safeTop;
        editorInsetRight = safeRight;
        editorInsetBottom = safeBottom;
        fitPending = true;
        invalidate();
    }

    private void moveSelection(float dx, float dy) {
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null) {
                element.move(dx, dy);
                constrainElement(element);
            }
        }
        SpatialSnapEngine.refreshConnectors(document);
    }

    private void scaleSelectionInternal(
            float factor,
            float pivotX,
            float pivotY,
            boolean refreshConnectors
    ) {
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null) {
                element.scale(factor, pivotX, pivotY);
                constrainElement(element);
            }
        }
        if (refreshConnectors) {
            SpatialSnapEngine.refreshConnectors(document);
        }
    }

    private void rotateSelectionInternal(
            float deltaX,
            float deltaY,
            float deltaZ,
            boolean refreshConnectors
    ) {
        Bounds groupBounds = selectionBounds();
        float pivotX = groupBounds == null ? 0f : groupBounds.centerX();
        float pivotY = groupBounds == null ? 0f : groupBounds.centerY();
        double radians = Math.toRadians(deltaZ);
        boolean orbit = selectedIds.size() > 1 && Math.abs(deltaZ) > 0.001f;
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null) {
                if (orbit) {
                    Bounds before = element.bounds();
                    float localX = before.centerX() - pivotX;
                    float localY = before.centerY() - pivotY;
                    float rotatedCenterX = pivotX
                            + (float) (localX * Math.cos(radians) - localY * Math.sin(radians));
                    float rotatedCenterY = pivotY
                            + (float) (localX * Math.sin(radians) + localY * Math.cos(radians));
                    element.move(
                            rotatedCenterX - before.centerX(),
                            rotatedCenterY - before.centerY()
                    );
                }
                element.rotate(deltaX, deltaY, deltaZ);
            }
        }
        if (refreshConnectors) {
            SpatialSnapEngine.refreshConnectors(document);
        }
    }

    private void captureScaleBaseline() {
        scaleBaseline.clear();
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null) {
                scaleBaseline.put(id, element.copy());
            }
        }
    }

    private void restoreScaleBaseline() {
        for (Map.Entry<String, CanvasElement> entry : scaleBaseline.entrySet()) {
            CanvasElement target = document.find(entry.getKey());
            CanvasElement source = entry.getValue();
            if (target != null) {
                copyGeometry(source, target);
            }
        }
    }

    private static void copyGeometry(CanvasElement source, CanvasElement target) {
        target.x1 = source.x1;
        target.y1 = source.y1;
        target.x2 = source.x2;
        target.y2 = source.y2;
        target.strokeWidth = source.strokeWidth;
        target.rotationX = source.rotationX;
        target.rotationY = source.rotationY;
        target.rotationZ = source.rotationZ;
        target.depth = source.depth;
        target.points.clear();
        target.points.addAll(source.points);
    }

    private void selectAt(float worldX, float worldY, boolean toggle) {
        CanvasElement hit = document.hitTest(worldX, worldY, worldTolerance(34f));
        if (hit == null) {
            if (!toggle) {
                clearSelection();
            }
            return;
        }
        if (toggle) {
            toggleSelection(hit);
        } else {
            setSingleSelection(hit);
        }
    }

    private void setSingleSelection(CanvasElement element) {
        selectedIds.clear();
        addElementOrGroupToSelection(element);
        notifySelectionChanged();
        invalidate();
    }

    private void toggleSelection(CanvasElement element) {
        List<CanvasElement> group = element.groupId == null
                ? List.of(element)
                : document.elementsInGroup(element.groupId);
        boolean everySelected = true;
        for (CanvasElement item : group) {
            everySelected &= selectedIds.contains(item.id);
        }
        for (CanvasElement item : group) {
            if (everySelected) {
                selectedIds.remove(item.id);
            } else {
                selectedIds.add(item.id);
            }
        }
        notifySelectionChanged();
        invalidate();
    }

    private void addElementOrGroupToSelection(CanvasElement element) {
        if (element.groupId == null) {
            selectedIds.add(element.id);
            return;
        }
        for (CanvasElement grouped : document.elementsInGroup(element.groupId)) {
            selectedIds.add(grouped.id);
        }
    }

    private void clearSelection() {
        if (selectedIds.isEmpty()) {
            return;
        }
        selectedIds.clear();
        notifySelectionChanged();
        invalidate();
    }

    private void deleteElementOrGroup(CanvasElement element) {
        if (element.groupId == null) {
            document.elements.remove(element);
        } else {
            document.elements.removeIf(item ->
                    element.groupId.equals(item.groupId) && !item.locked
            );
        }
        selectedIds.removeIf(id -> document.find(id) == null);
        SpatialSnapEngine.refreshConnectors(document);
        notifySelectionChanged();
    }

    private boolean allSelectionLocked() {
        if (selectedIds.isEmpty()) {
            return false;
        }
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null && !element.locked) {
                return false;
            }
        }
        return true;
    }

    private int unlockedSelectionCount() {
        int count = 0;
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element != null && !element.locked) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private Bounds selectionBounds() {
        Bounds result = null;
        for (String id : selectedIds) {
            CanvasElement element = document.find(id);
            if (element == null) {
                continue;
            }
            Bounds visual = element.visualBounds();
            result = result == null ? visual : result.union(visual);
        }
        return result;
    }

    private void checkpoint() {
        if (listener != null) {
            listener.onMutationStart();
        }
    }

    private void changed() {
        if (listener != null) {
            listener.onDocumentChanged();
        }
    }

    private void notifySelectionChanged() {
        if (listener != null) {
            listener.onSelectionChanged(selectedIds.size());
        }
    }

    private boolean canAddElements(int count) {
        if (count <= 0 || document.elements.size() + count <= MAX_EDITABLE_ELEMENTS) {
            return true;
        }
        if (listener != null) {
            listener.onCanvasMessage(
                    "This mobile scene is full (800 objects). Delete or export before adding more."
            );
        }
        return false;
    }

    private void cancelAction() {
        if (action == Action.CREATE
                || action == Action.MOVE_SELECTION
                || action == Action.RESIZE_SELECTION
                || action == Action.ROTATE_SELECTION
                || action == Action.TILT_SELECTION) {
            // Camera tracking can briefly lose a hand or Android can cancel a
            // touch stream. Commit the last stable geometry so the edit is not
            // left half-finished or skipped by autosave/history.
            endAction(lastScreenX, lastScreenY);
            return;
        }
        action = Action.NONE;
        activeElement = null;
        scaleBaseline.clear();
        guideX = Float.NaN;
        guideY = Float.NaN;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        fitPending = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (fitPending) {
            float availableWidth = Math.max(1f,
                    getWidth() - editorInsetLeft - editorInsetRight);
            float availableHeight = Math.max(1f,
                    getHeight() - editorInsetTop - editorInsetBottom);
            fitScale = spatialOverlay
                    ? Math.max(
                    availableWidth / document.pageWidth,
                    availableHeight / document.pageHeight
            )
                    : Math.min(
                    availableWidth * 0.94f / document.pageWidth,
                    availableHeight * 0.90f / document.pageHeight
            );
            fitScale = Math.max(0.05f, fitScale);
            fitPending = false;
        }

        float scale = contentScale();
        float left = worldToScreenX(0f);
        float top = worldToScreenY(0f);
        float right = worldToScreenX(document.pageWidth);
        float bottom = worldToScreenY(document.pageHeight);
        pageRect.set(left, top, right, bottom);
        if (!spatialOverlay) {
            canvas.drawColor(0x2A020914);
            canvas.drawRoundRect(pageRect, dp(18f), dp(18f), pagePaint);
        }

        int save = canvas.save();
        if (!spatialOverlay) {
            canvas.clipRect(left, top, right, bottom);
        }
        canvas.translate(contentCenterX() + panX, contentCenterY() + panY);
        canvas.scale(scale, scale);
        canvas.translate(-document.pageWidth * 0.5f, -document.pageHeight * 0.5f);
        float viewportPadding = 120f / Math.max(0.01f, scale);
        Bounds viewport = new Bounds(
                screenToWorldX(editorInsetLeft) - viewportPadding,
                screenToWorldY(editorInsetTop) - viewportPadding,
                screenToWorldX(getWidth() - editorInsetRight) + viewportPadding,
                screenToWorldY(getHeight() - editorInsetBottom) + viewportPadding
        );
        drawDocument(
                canvas,
                showGrid && !presentationMode,
                !presentationMode,
                viewport
        );
        drawGuides(canvas);
        canvas.restoreToCount(save);

        if (airCursorVisible) {
            float radius = dp(action == Action.NONE ? 12f : 18f);
            cursorPaint.setColor(presentationMode ? 0xFFFF496C : 0xFF5EE7F7);
            canvas.drawCircle(airCursorX, airCursorY, radius, cursorPaint);
            canvas.drawCircle(
                    airCursorX,
                    airCursorY,
                    dp(presentationMode ? 4.5f : 3f),
                    fillPaintWith(presentationMode ? 0xFFFF496C : 0xFF5EE7F7)
            );
            if (presentationMode) {
                Paint halo = fillPaintWith(0x22FF496C);
                canvas.drawCircle(airCursorX, airCursorY, dp(34f), halo);
            }
        }
    }

    private void drawDocument(
            Canvas canvas,
            boolean grid,
            boolean selection,
            @Nullable Bounds viewport
    ) {
        if (grid) {
            float spacing = "Boundary Plan".equals(document.template) ? 40f : 80f;
            for (float x = spacing; x < document.pageWidth; x += spacing) {
                canvas.drawLine(x, 0f, x, document.pageHeight, gridPaint);
            }
            for (float y = spacing; y < document.pageHeight; y += spacing) {
                canvas.drawLine(0f, y, document.pageWidth, y, gridPaint);
            }
        }
        for (CanvasElement element : document.elements) {
            Bounds elementBounds = element.bounds();
            if (viewport != null && !isPotentiallyVisible(element, elementBounds, viewport)) {
                continue;
            }
            drawElement(canvas, element, elementBounds);
        }
        if (selection) {
            drawSelection(canvas);
        }
    }

    private void drawGuides(Canvas canvas) {
        if (presentationMode) {
            return;
        }
        if (Float.isFinite(guideX)) {
            canvas.drawLine(guideX, 0f, guideX, document.pageHeight, guidePaint);
        }
        if (Float.isFinite(guideY)) {
            canvas.drawLine(0f, guideY, document.pageWidth, guideY, guidePaint);
        }
    }

    private void drawElement(
            Canvas canvas,
            CanvasElement element,
            Bounds b
    ) {
        if (element.isSpatial3d()) {
            drawSpatialElement(canvas, element);
            return;
        }
        int transformSave = canvas.save();
        if (element.hasTransform()
                && element.type != CanvasElement.Type.LINE
                && element.type != CanvasElement.Type.STROKE) {
            sourceCorners[0] = b.left;
            sourceCorners[1] = b.top;
            sourceCorners[2] = b.right;
            sourceCorners[3] = b.top;
            sourceCorners[4] = b.right;
            sourceCorners[5] = b.bottom;
            sourceCorners[6] = b.left;
            sourceCorners[7] = b.bottom;
            element.projectPoint(b.left, b.top, projectedCorners, 0);
            element.projectPoint(b.right, b.top, projectedCorners, 2);
            element.projectPoint(b.right, b.bottom, projectedCorners, 4);
            element.projectPoint(b.left, b.bottom, projectedCorners, 6);
            elementMatrix.reset();
            if (elementMatrix.setPolyToPoly(
                    sourceCorners,
                    0,
                    projectedCorners,
                    0,
                    4
            )) {
                canvas.concat(elementMatrix);
            }
        }
        elementRect.set(b.left, b.top, b.right, b.bottom);
        strokePaint.setColor(applyOpacity(element.strokeColor, element.opacity));
        strokePaint.setStrokeWidth(element.strokeWidth);
        fillPaint.setColor(applyOpacity(element.fillColor, element.opacity));

        switch (element.type) {
            case RECTANGLE, FRAME -> {
                float radius = element.type == CanvasElement.Type.FRAME ? 24f : 18f;
                if (Color.alpha(element.fillColor) > 0) {
                    canvas.drawRoundRect(elementRect, radius, radius, fillPaint);
                }
                canvas.drawRoundRect(elementRect, radius, radius, strokePaint);
            }
            case ELLIPSE -> {
                if (Color.alpha(element.fillColor) > 0) {
                    canvas.drawOval(elementRect, fillPaint);
                }
                canvas.drawOval(elementRect, strokePaint);
            }
            case DIAMOND -> {
                path.reset();
                path.moveTo(b.centerX(), b.top);
                path.lineTo(b.right, b.centerY());
                path.lineTo(b.centerX(), b.bottom);
                path.lineTo(b.left, b.centerY());
                path.close();
                if (Color.alpha(element.fillColor) > 0) {
                    canvas.drawPath(path, fillPaint);
                }
                canvas.drawPath(path, strokePaint);
            }
            case TRIANGLE -> {
                path.reset();
                path.moveTo(b.centerX(), b.top);
                path.lineTo(b.right, b.bottom);
                path.lineTo(b.left, b.bottom);
                path.close();
                drawFilledPath(canvas, element, path);
            }
            case HEXAGON -> {
                float inset = b.width() * 0.23f;
                path.reset();
                path.moveTo(b.left + inset, b.top);
                path.lineTo(b.right - inset, b.top);
                path.lineTo(b.right, b.centerY());
                path.lineTo(b.right - inset, b.bottom);
                path.lineTo(b.left + inset, b.bottom);
                path.lineTo(b.left, b.centerY());
                path.close();
                drawFilledPath(canvas, element, path);
            }
            case STAR -> {
                path.reset();
                float outer = Math.min(b.width(), b.height()) * 0.5f;
                float inner = outer * 0.46f;
                for (int index = 0; index < 10; index++) {
                    double angle = -Math.PI * 0.5d + index * Math.PI / 5d;
                    float radius = index % 2 == 0 ? outer : inner;
                    float x = b.centerX() + (float) Math.cos(angle) * radius;
                    float y = b.centerY() + (float) Math.sin(angle) * radius;
                    if (index == 0) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                }
                path.close();
                drawFilledPath(canvas, element, path);
            }
            case STICKY -> drawSticky(canvas, element, b);
            case LINE -> drawArrowLine(canvas, element);
            case STROKE -> drawStroke(canvas, element);
            case TEXT -> {
                // Text is drawn below.
            }
            case CUBE, SPHERE, CYLINDER, PYRAMID, CONE -> {
                // True 3D primitives are handled before the planar transform.
            }
        }
        if (!element.text.isEmpty()
                && element.type != CanvasElement.Type.LINE
                && element.type != CanvasElement.Type.STROKE) {
            drawCenteredText(canvas, element, b);
        }
        canvas.restoreToCount(transformSave);
    }

    private void drawSpatialElement(Canvas canvas, CanvasElement element) {
        SpatialMesh.Projection projection = element.meshProjection();
        strokePaint.setStrokeWidth(element.strokeWidth);
        strokePaint.setColor(applyOpacity(element.strokeColor, element.opacity));
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
                fillPaint.setColor(shadeColor(
                        applyOpacity(element.fillColor, element.opacity),
                        face.light()
                ));
                canvas.drawPath(path, fillPaint);
            }
            canvas.drawPath(path, strokePaint);
        }
        if (!element.text.isEmpty()) {
            drawCenteredText(canvas, element, projection.bounds());
        }
    }

    private void drawFilledPath(Canvas canvas, CanvasElement element, Path target) {
        if (Color.alpha(element.fillColor) > 0) {
            canvas.drawPath(target, fillPaint);
        }
        canvas.drawPath(target, strokePaint);
    }

    private void drawSticky(Canvas canvas, CanvasElement element, Bounds bounds) {
        float fold = Math.min(bounds.width(), bounds.height()) * 0.18f;
        path.reset();
        path.moveTo(bounds.left, bounds.top);
        path.lineTo(bounds.right - fold, bounds.top);
        path.lineTo(bounds.right, bounds.top + fold);
        path.lineTo(bounds.right, bounds.bottom);
        path.lineTo(bounds.left, bounds.bottom);
        path.close();
        drawFilledPath(canvas, element, path);
        path.reset();
        path.moveTo(bounds.right - fold, bounds.top);
        path.lineTo(bounds.right - fold, bounds.top + fold);
        path.lineTo(bounds.right, bounds.top + fold);
        canvas.drawPath(path, strokePaint);
    }

    private void drawArrowLine(Canvas canvas, CanvasElement element) {
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
        canvas.drawLine(startX, startY, endX, endY, strokePaint);
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
        canvas.drawPath(path, strokePaint);
    }

    private void drawStroke(Canvas canvas, CanvasElement element) {
        if (element.points.size() < 2) {
            return;
        }
        path.reset();
        if (element.hasTransform()) {
            element.projectPoint(
                    element.points.get(0),
                    element.points.get(1),
                    projectedCorners,
                    0
            );
            path.moveTo(projectedCorners[0], projectedCorners[1]);
        } else {
            path.moveTo(element.points.get(0), element.points.get(1));
        }
        for (int index = 2; index + 1 < element.points.size(); index += 2) {
            if (element.hasTransform()) {
                element.projectPoint(
                        element.points.get(index),
                        element.points.get(index + 1),
                        projectedCorners,
                        0
                );
                path.lineTo(projectedCorners[0], projectedCorners[1]);
            } else {
                path.lineTo(element.points.get(index), element.points.get(index + 1));
            }
        }
        canvas.drawPath(path, strokePaint);
    }

    private void drawCenteredText(Canvas canvas, CanvasElement element, Bounds bounds) {
        String text = element.text;
        float padding = 20f;
        int width = Math.max(1, Math.round(bounds.width() - padding * 2f));
        float size = clamp(Math.min(bounds.height() * 0.28f, bounds.width() * 0.11f), 18f, 42f);
        textPaint.setTextSize(size);
        textPaint.setColor(applyOpacity(0xFFF4F8FF, element.opacity));
        int signature = 31 * text.hashCode()
                + 17 * width
                + Float.floatToIntBits(size)
                + Float.floatToIntBits(element.opacity);
        TextLayoutCache cached = textLayoutCache.get(element.id);
        StaticLayout layout;
        if (cached != null && cached.signature == signature) {
            layout = cached.layout;
        } else {
            layout = StaticLayout.Builder
                    .obtain(text, 0, text.length(), textPaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setMaxLines(4)
                    .build();
            if (textLayoutCache.size() > 192) {
                textLayoutCache.clear();
            }
            textLayoutCache.put(element.id, new TextLayoutCache(signature, layout));
        }
        int save = canvas.save();
        canvas.translate(
                bounds.left + padding,
                bounds.centerY() - layout.getHeight() * 0.5f
        );
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private void drawSelection(Canvas canvas) {
        Bounds bounds = selectionBounds();
        if (bounds == null) {
            return;
        }
        float expansion = 12f;
        selectionRect.set(
                bounds.left - expansion,
                bounds.top - expansion,
                bounds.right + expansion,
                bounds.bottom + expansion
        );
        canvas.drawRoundRect(selectionRect, 12f, 12f, selectionPaint);
        canvas.drawLine(
                bounds.centerX(),
                bounds.top - 12f,
                bounds.centerX(),
                bounds.top - 52f,
                selectionPaint
        );
        canvas.drawCircle(bounds.right, bounds.bottom, 12f, handlePaint);
        canvas.drawCircle(bounds.centerX(), bounds.top - 52f, 11f, handlePaint);
        if (tool == Tool.TRANSFORM) {
            textPaint.setTextSize(20f);
            textPaint.setColor(0xFFF4F8FF);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(transformAxis.label, bounds.centerX(), bounds.bottom + 42f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    public Bitmap renderDocumentBitmap(int requestedWidth) {
        return renderDocumentBitmap(requestedWidth, spatialOverlay);
    }

    public Bitmap renderDocumentBitmap(int requestedWidth, boolean transparent) {
        ExportSizing.OutputSize size = ExportSizing.fit(
                document.pageWidth,
                document.pageHeight,
                requestedWidth
        );
        int width = size.width();
        int height = size.height();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(transparent ? Color.TRANSPARENT : 0xFF081220);
        float scale = width / document.pageWidth;
        canvas.scale(scale, scale);
        drawDocument(canvas, showGrid && !transparent, false, null);
        return bitmap;
    }

    private static boolean isPotentiallyVisible(
            CanvasElement element,
            Bounds bounds,
            Bounds viewport
    ) {
        float transformPadding = element.hasTransform()
                ? Math.max(bounds.width(), bounds.height())
                : 0f;
        return bounds.right + transformPadding >= viewport.left
                && bounds.left - transformPadding <= viewport.right
                && bounds.bottom + transformPadding >= viewport.top
                && bounds.top - transformPadding <= viewport.bottom;
    }

    private void normalizeElement(CanvasElement element) {
        if (element.type == CanvasElement.Type.LINE || element.type == CanvasElement.Type.STROKE) {
            return;
        }
        float left = Math.min(element.x1, element.x2);
        float right = Math.max(element.x1, element.x2);
        float top = Math.min(element.y1, element.y2);
        float bottom = Math.max(element.y1, element.y2);
        element.x1 = left;
        element.x2 = right;
        element.y1 = top;
        element.y2 = bottom;
        if (element.isSpatial3d()) {
            element.depth = clamp(
                    Math.min(element.x2 - element.x1, element.y2 - element.y1) * 0.72f,
                    48f,
                    2_000f
            );
        }
        constrainElement(element);
    }

    private void constrainElement(CanvasElement element) {
        if (spatialOverlay) {
            return;
        }
        Bounds b = element.bounds();
        float dx = 0f;
        float dy = 0f;
        if (b.left < 0f) {
            dx = -b.left;
        } else if (b.right > document.pageWidth) {
            dx = document.pageWidth - b.right;
        }
        if (b.top < 0f) {
            dy = -b.top;
        } else if (b.bottom > document.pageHeight) {
            dy = document.pageHeight - b.bottom;
        }
        element.move(dx, dy);
    }

    private boolean isInsidePage(float x, float y) {
        return spatialOverlay
                || x >= 0f && x <= document.pageWidth
                && y >= 0f && y <= document.pageHeight;
    }

    private float snap(float value) {
        if (!showGrid) {
            return value;
        }
        float grid = "Boundary Plan".equals(document.template) ? 20f : 10f;
        return Math.round(value / grid) * grid;
    }

    private String defaultBlockText() {
        if (activeShape == ShapeKind.CUBE) {
            return "CUBE";
        }
        if (activeShape == ShapeKind.SPHERE) {
            return "SPHERE";
        }
        if (activeShape == ShapeKind.CYLINDER) {
            return "CYLINDER";
        }
        if (activeShape == ShapeKind.PYRAMID) {
            return "PYRAMID";
        }
        if (activeShape == ShapeKind.CONE) {
            return "CONE";
        }
        if (activeShape == ShapeKind.STICKY) {
            return "NOTE";
        }
        if (activeShape == ShapeKind.FRAME) {
            return "BOUNDARY";
        }
        return switch (document.template) {
            case "Flowchart" -> "PROCESS";
            case "UI Wireframe" -> "COMPONENT";
            case "Mind Map" -> "IDEA";
            case "Boundary Plan" -> "ZONE";
            default -> "BLOCK";
        };
    }

    private void styleNewShape(CanvasElement element) {
        switch (element.type) {
            case STICKY -> {
                element.fillColor = 0xD9F6C85F;
                element.strokeColor = 0xFFFFE39A;
            }
            case STAR -> {
                element.fillColor = 0xB8A889FF;
                element.strokeColor = 0xFFE0D1FF;
            }
            case FRAME -> {
                element.fillColor = 0x22101D31;
                element.strokeColor = 0xFFA889FF;
                element.strokeWidth = 7f;
            }
            case CUBE, SPHERE, CYLINDER, PYRAMID, CONE -> {
                element.fillColor = 0xC05263C7;
                element.strokeColor = 0xFFE8E5FF;
                element.strokeWidth = 4f;
                element.depth = clamp(
                        Math.min(
                                Math.abs(element.x2 - element.x1),
                                Math.abs(element.y2 - element.y1)
                        ) * 0.72f,
                        48f,
                        2_000f
                );
                element.setRotation(-18f, 28f, 0f);
            }
            default -> {
                // Base spatial styling is already set by CanvasElement.
            }
        }
    }

    private float contentScale() {
        return fitScale * zoom;
    }

    private float contentCenterX() {
        return editorInsetLeft
                + (getWidth() - editorInsetLeft - editorInsetRight) * 0.5f;
    }

    private float contentCenterY() {
        return editorInsetTop
                + (getHeight() - editorInsetTop - editorInsetBottom) * 0.5f;
    }

    private float worldToScreenX(float worldX) {
        return contentCenterX() + panX
                + (worldX - document.pageWidth * 0.5f) * contentScale();
    }

    private float worldToScreenY(float worldY) {
        return contentCenterY() + panY
                + (worldY - document.pageHeight * 0.5f) * contentScale();
    }

    private float screenToWorldX(float screenX) {
        return document.pageWidth * 0.5f
                + (screenX - contentCenterX() - panX) / Math.max(0.01f, contentScale());
    }

    private float screenToWorldY(float screenY) {
        return document.pageHeight * 0.5f
                + (screenY - contentCenterY() - panY) / Math.max(0.01f, contentScale());
    }

    private float normalizedToScreenX(float x) {
        float scale = Math.max(
                getWidth() / (float) inputWidth,
                getHeight() / (float) inputHeight
        );
        float crop = (inputWidth * scale - getWidth()) * 0.5f;
        return x * inputWidth * scale - crop;
    }

    private float normalizedToScreenY(float y) {
        float scale = Math.max(
                getWidth() / (float) inputWidth,
                getHeight() / (float) inputHeight
        );
        float crop = (inputHeight * scale - getHeight()) * 0.5f;
        return y * inputHeight * scale - crop;
    }

    private float worldTolerance(float screenDp) {
        return dp(screenDp) / Math.max(0.01f, contentScale());
    }

    private Paint fillPaintWith(int color) {
        fillPaint.setColor(color);
        return fillPaint;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float angleDegrees(
            float centerX,
            float centerY,
            float x,
            float y
    ) {
        return (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
    }

    private static int applyOpacity(int color, float opacity) {
        int alpha = Math.round(Color.alpha(color) * clamp(opacity, 0f, 1f));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private SmartInkRequest finishSmartInk(CanvasElement element) {
        if (element.points.size() < 4) {
            return null;
        }
        SmartStrokeInterpreter.Result result = SmartStrokeInterpreter.interpret(element.points);
        if (inkMode == InkMode.SHAPE) {
            if (result.recognized()) {
                applyInterpretedShape(element, result);
                return null;
            }
            if (listener != null) {
                listener.onCanvasMessage(
                        "Shape unclear — draw one closed outline and release the pinch."
                );
            }
            return null;
        }
        if (inkMode == InkMode.AUTO
                && result.recognized()
                && result.kind() != SmartStrokeInterpreter.Kind.LINE
                && result.confidence() >= 0.74f) {
            applyInterpretedShape(element, result);
            return null;
        }
        // Open and line-like strokes are intentionally routed to handwriting.
        // Separate pinch strokes are batched by MainActivity into one word request.
        return new SmartInkRequest(
                element.id,
                element.points,
                System.currentTimeMillis()
        );
    }

    private void applyInterpretedShape(
            CanvasElement element,
            SmartStrokeInterpreter.Result result
    ) {
        Bounds bounds = result.bounds();
        if (result.kind() == SmartStrokeInterpreter.Kind.LINE) {
            int size = element.points.size();
            element.type = CanvasElement.Type.LINE;
            element.x1 = element.points.get(0);
            element.y1 = element.points.get(1);
            element.x2 = element.points.get(size - 2);
            element.y2 = element.points.get(size - 1);
            element.fillColor = 0x00000000;
        } else {
            element.type = result.kind().elementType;
            element.x1 = bounds.left;
            element.y1 = bounds.top;
            element.x2 = bounds.right;
            element.y2 = bounds.bottom;
            element.fillColor = 0xB845568F;
        }
        element.text = "";
        element.points.clear();
        element.strokeColor = 0xFFE1DEFF;
        element.strokeWidth = 5f;
        normalizeElement(element);
    }

    private static int shadeColor(int color, float light) {
        float safeLight = clamp(light, 0.25f, 1f);
        float multiplier = 0.55f + safeLight * 0.52f;
        int red = Math.round(clamp(Color.red(color) * multiplier, 0f, 255f));
        int green = Math.round(clamp(Color.green(color) * multiplier, 0f, 255f));
        int blue = Math.round(clamp(Color.blue(color) * multiplier, 0f, 255f));
        return Color.argb(Color.alpha(color), red, green, blue);
    }

    private static float lastStrokeDistance(
            CanvasElement element,
            float x,
            float y
    ) {
        if (element.points.size() < 2) {
            return Float.MAX_VALUE;
        }
        int size = element.points.size();
        return distance(
                element.points.get(size - 2),
                element.points.get(size - 1),
                x,
                y
        );
    }

    private record TextLayoutCache(int signature, StaticLayout layout) {
    }
}
