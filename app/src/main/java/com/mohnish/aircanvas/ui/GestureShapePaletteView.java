package com.mohnish.aircanvas.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.mohnish.aircanvas.gesture.PaletteSelectionGate;
import com.mohnish.aircanvas.model.ShapeKind;

/** Thumbs-up shape palette with stable hover and pinch-release confirmation. */
public final class GestureShapePaletteView extends View {
    public interface Listener {
        void onShapeChosen(ShapeKind shape);
    }

    private static final ShapeKind[] SHAPES = ShapeKind.values();

    private final Paint scrimPaint = new Paint();
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panel = new RectF();
    private final RectF[] cards = new RectF[SHAPES.length];
    private final PaletteSelectionGate selectionGate = new PaletteSelectionGate();

    private Listener listener;
    private ShapeKind activeShape = ShapeKind.RECTANGLE;
    private float cursorX;
    private float cursorY;
    private boolean cursorVisible;

    public GestureShapePaletteView(Context context) {
        this(context, null);
    }

    public GestureShapePaletteView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setVisibility(GONE);
        setClickable(true);
        setFocusable(true);
        scrimPaint.setColor(0xB8020914);
        panelPaint.setColor(0xF40A1729);
        cardPaint.setColor(0xE6152942);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.5f));
        borderPaint.setColor(0x665EE7F7);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(dp(3f));
        cursorPaint.setColor(0xFF5EE7F7);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(dp(4f));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(UiKit.PRIMARY);
        titlePaint.setColor(UiKit.TEXT);
        titlePaint.setTextSize(dp(20f));
        titlePaint.setFakeBoldText(true);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(12.5f));
        labelPaint.setFakeBoldText(true);
        hintPaint.setColor(UiKit.MUTED);
        hintPaint.setTextSize(dp(12f));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void open(ShapeKind activeShape) {
        animate().cancel();
        this.activeShape = activeShape == null ? ShapeKind.RECTANGLE : activeShape;
        cursorVisible = false;
        selectionGate.open(SHAPES.length, SystemClock.uptimeMillis());
        setVisibility(VISIBLE);
        setAlpha(0f);
        setScaleX(0.985f);
        setScaleY(0.985f);
        bringToFront();
        requestFocus();
        layoutCards();
        animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120L)
                .start();
        invalidate();
    }

    public void close() {
        animate().cancel();
        setVisibility(GONE);
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
        selectionGate.close();
        cursorVisible = false;
    }

    public boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    public void updateCursor(float x, float y) {
        layoutCards();
        cursorX = x;
        cursorY = y;
        cursorVisible = true;
        selectionGate.updateHover(findCard(x, y), SystemClock.uptimeMillis());
        invalidate();
    }

    public boolean beginPinchSelection() {
        boolean armed = selectionGate.beginPinch(SystemClock.uptimeMillis());
        invalidate();
        return armed;
    }

    @Nullable
    public ShapeKind commitPinchSelection() {
        int selectedIndex = selectionGate.commitPinch(SystemClock.uptimeMillis());
        if (selectedIndex < 0) {
            invalidate();
            return null;
        }
        return choose(selectedIndex);
    }

    public void cancelPinchSelection() {
        selectionGate.cancelPinch();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                updateCursor(event.getX(), event.getY());
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                updateCursor(event.getX(), event.getY());
                performClick();
                int index = findCard(event.getX(), event.getY());
                if (index >= 0) {
                    choose(index);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                cursorVisible = false;
                selectionGate.cancelPinch();
                invalidate();
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

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        layoutCards();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        layoutCards();
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), scrimPaint);
        canvas.drawRoundRect(panel, dp(26f), dp(26f), panelPaint);
        canvas.drawText(
                "THUMBS-UP SHAPE PALETTE",
                panel.left + dp(22f),
                panel.top + dp(34f),
                titlePaint
        );
        canvas.drawText(
                "Hover a shape, pinch, then release. No more swipe-by-swipe selection.",
                panel.left + dp(22f),
                panel.top + dp(56f),
                hintPaint
        );

        long now = SystemClock.uptimeMillis();
        for (int index = 0; index < SHAPES.length; index++) {
            RectF card = cards[index];
            if (card == null) {
                continue;
            }
            boolean hovered = index == selectionGate.hoveredIndex();
            boolean armed = index == selectionGate.armedIndex();
            boolean active = SHAPES[index] == activeShape;
            cardPaint.setColor(armed
                    ? 0xFF1B5260
                    : hovered ? 0xFF173B51 : active ? 0xFF28224A : 0xE6152942);
            borderPaint.setColor(armed || hovered
                    ? 0xFF5EE7F7
                    : active ? 0xFFA889FF : 0x665EE7F7);
            borderPaint.setStrokeWidth(dp(armed ? 3.2f : hovered ? 2.6f : 1.3f));
            canvas.drawRoundRect(card, dp(15f), dp(15f), cardPaint);
            canvas.drawRoundRect(card, dp(15f), dp(15f), borderPaint);
            labelPaint.setColor(hovered ? UiKit.PRIMARY : UiKit.TEXT);
            canvas.drawText(
                    SHAPES[index].label,
                    card.centerX(),
                    card.centerY() + dp(4f),
                    labelPaint
            );
        }

        if (cursorVisible) {
            canvas.drawCircle(cursorX, cursorY, dp(15f), cursorPaint);
            canvas.drawCircle(cursorX, cursorY, dp(3f), cursorPaint);
            if (selectionGate.hoveredIndex() >= 0
                    && selectionGate.armedIndex() < 0) {
                float progress = selectionGate.hoverProgress(now);
                RectF ring = new RectF(
                        cursorX - dp(20f), cursorY - dp(20f),
                        cursorX + dp(20f), cursorY + dp(20f)
                );
                canvas.drawArc(ring, -90f, progress * 360f, false, progressPaint);
                if (progress < 1f) {
                    postInvalidateOnAnimation();
                }
            }
        }
    }

    private void layoutCards() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        boolean landscape = getWidth() > getHeight();
        float margin = dp(landscape ? 12f : 16f);
        float maxWidth = landscape ? dp(900f) : dp(590f);
        float maxHeight = landscape ? dp(390f) : dp(610f);
        float panelWidth = Math.min(getWidth() - margin * 2f, maxWidth);
        float panelHeight = Math.min(getHeight() - margin * 2f, maxHeight);
        float left = (getWidth() - panelWidth) * 0.5f;
        float top = (getHeight() - panelHeight) * 0.5f;
        panel.set(left, top, left + panelWidth, top + panelHeight);

        int columns = landscape ? 5 : 3;
        int rows = (int) Math.ceil(SHAPES.length / (float) columns);
        float gap = dp(8f);
        float gridTop = panel.top + dp(72f);
        float gridBottom = panel.bottom - dp(14f);
        float gridWidth = panel.width() - dp(28f);
        float cardWidth = (gridWidth - gap * (columns - 1)) / columns;
        float cardHeight = (gridBottom - gridTop - gap * (rows - 1)) / rows;
        for (int index = 0; index < SHAPES.length; index++) {
            int row = index / columns;
            int column = index % columns;
            float cardLeft = panel.left + dp(14f) + column * (cardWidth + gap);
            float cardTop = gridTop + row * (cardHeight + gap);
            if (cards[index] == null) {
                cards[index] = new RectF();
            }
            cards[index].set(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
        }
    }

    @Nullable
    private ShapeKind choose(int index) {
        if (index < 0 || index >= SHAPES.length) {
            return null;
        }
        ShapeKind selected = SHAPES[index];
        if (listener != null) {
            listener.onShapeChosen(selected);
        }
        close();
        return selected;
    }

    private int findCard(float x, float y) {
        for (int index = 0; index < cards.length; index++) {
            RectF card = cards[index];
            if (card != null && card.contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
