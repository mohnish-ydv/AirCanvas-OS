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
import com.mohnish.aircanvas.model.Tool;

/** Gesture-first command palette with stable-hover and pinch-release confirmation. */
public final class GestureModePaletteView extends View {
    public interface Listener {
        void onToolChosen(Tool tool);
    }

    private static final Tool[] MODES = {
            Tool.SELECT,
            Tool.BLOCK,
            Tool.LINE,
            Tool.PEN,
            Tool.SMART_INK,
            Tool.TEXT,
            Tool.TRANSFORM,
            Tool.ERASE,
            Tool.PRESENT
    };

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
    private final RectF[] cards = new RectF[MODES.length];
    private final PaletteSelectionGate selectionGate = new PaletteSelectionGate();

    private Listener listener;
    private Tool activeTool = Tool.SELECT;
    private float cursorX;
    private float cursorY;
    private boolean cursorVisible;

    public GestureModePaletteView(Context context) {
        this(context, null);
    }

    public GestureModePaletteView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setVisibility(GONE);
        setClickable(true);
        setFocusable(true);
        scrimPaint.setColor(0xB3020914);
        panelPaint.setColor(0xF20A1729);
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
        labelPaint.setTextSize(dp(15f));
        labelPaint.setFakeBoldText(true);
        hintPaint.setColor(UiKit.MUTED);
        hintPaint.setTextSize(dp(12f));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void open(Tool activeTool) {
        animate().cancel();
        this.activeTool = activeTool == null ? Tool.SELECT : activeTool;
        cursorVisible = false;
        selectionGate.open(MODES.length, SystemClock.uptimeMillis());
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
                .setDuration(140L)
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
        long now = SystemClock.uptimeMillis();
        selectionGate.updateHover(findCard(x, y), now);
        invalidate();
    }

    /** Arms the current card. Selection is intentionally committed on release. */
    public boolean beginPinchSelection() {
        boolean armed = selectionGate.beginPinch(SystemClock.uptimeMillis());
        invalidate();
        return armed;
    }

    @Nullable
    public Tool commitPinchSelection() {
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

    @Nullable
    public Tool commitHovered() {
        long now = SystemClock.uptimeMillis();
        if (!selectionGate.isHoverReady(now)) {
            return null;
        }
        return choose(selectionGate.hoveredIndex());
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
                "GESTURE COMMAND PALETTE",
                panel.left + dp(22f),
                panel.top + dp(34f),
                titlePaint
        );
        canvas.drawText(
                "Hover briefly, pinch, then release to confirm.",
                panel.left + dp(22f),
                panel.top + dp(56f),
                hintPaint
        );

        long now = SystemClock.uptimeMillis();
        for (int index = 0; index < MODES.length; index++) {
            RectF card = cards[index];
            if (card == null) {
                continue;
            }
            boolean hovered = index == selectionGate.hoveredIndex();
            boolean armed = index == selectionGate.armedIndex();
            boolean active = MODES[index] == activeTool;
            cardPaint.setColor(armed
                    ? 0xFF1B5260
                    : hovered ? 0xFF173B51 : active ? 0xFF28224A : 0xE6152942);
            borderPaint.setColor(armed || hovered
                    ? 0xFF5EE7F7
                    : active ? 0xFFA889FF : 0x665EE7F7);
            borderPaint.setStrokeWidth(dp(armed ? 3.2f : hovered ? 2.6f : 1.3f));
            canvas.drawRoundRect(card, dp(18f), dp(18f), cardPaint);
            canvas.drawRoundRect(card, dp(18f), dp(18f), borderPaint);
            labelPaint.setColor(hovered ? UiKit.PRIMARY : UiKit.TEXT);
            canvas.drawText(
                    MODES[index].label,
                    card.centerX(),
                    card.centerY() + dp(5f),
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
        float margin = dp(getWidth() > getHeight() ? 12f : 18f);
        float maxWidth = getWidth() > getHeight() ? dp(760f) : dp(560f);
        float maxHeight = getWidth() > getHeight() ? dp(340f) : dp(490f);
        float panelWidth = Math.min(getWidth() - margin * 2f, maxWidth);
        float panelHeight = Math.min(getHeight() - margin * 2f, maxHeight);
        float left = (getWidth() - panelWidth) * 0.5f;
        float top = (getHeight() - panelHeight) * 0.5f;
        panel.set(left, top, left + panelWidth, top + panelHeight);

        int columns = getWidth() > getHeight() ? 5 : 3;
        int rows = (int) Math.ceil(MODES.length / (float) columns);
        float gap = dp(10f);
        float gridTop = panel.top + dp(72f);
        float gridBottom = panel.bottom - dp(16f);
        float gridWidth = panel.width() - dp(32f);
        float cardWidth = (gridWidth - gap * (columns - 1)) / columns;
        float cardHeight = (gridBottom - gridTop - gap * (rows - 1)) / rows;
        for (int index = 0; index < MODES.length; index++) {
            int row = index / columns;
            int column = index % columns;
            float cardLeft = panel.left + dp(16f) + column * (cardWidth + gap);
            float cardTop = gridTop + row * (cardHeight + gap);
            if (cards[index] == null) {
                cards[index] = new RectF();
            }
            cards[index].set(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
        }
    }

    @Nullable
    private Tool choose(int index) {
        if (index < 0 || index >= MODES.length) {
            return null;
        }
        Tool selected = MODES[index];
        if (listener != null) {
            listener.onToolChosen(selected);
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
