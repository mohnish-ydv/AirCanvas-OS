package com.mohnish.aircanvas.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.mohnish.aircanvas.gesture.LandmarkPoint;

import java.util.List;

public final class HandOverlayView extends View {
    private static final int[][] CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4},
            {0, 5}, {5, 6}, {6, 7}, {7, 8},
            {5, 9}, {9, 10}, {10, 11}, {11, 12},
            {9, 13}, {13, 14}, {14, 15}, {15, 16},
            {13, 17}, {17, 18}, {18, 19}, {19, 20},
            {0, 17}
    };

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<List<LandmarkPoint>> hands = List.of();
    private int inputWidth = 1;
    private int inputHeight = 1;
    private float mappingScale = 1f;
    private float cropX;
    private float cropY;
    private boolean showLandmarks = true;

    public HandOverlayView(Context context) {
        this(context, null);
    }

    public HandOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(false);
        setFocusable(false);
        linePaint.setColor(0xB35EE7F7);
        linePaint.setStrokeWidth(dp(2f));
        linePaint.setStyle(Paint.Style.STROKE);
        pointPaint.setColor(0xE6A889FF);
        pointPaint.setStyle(Paint.Style.FILL);
    }

    public void setHands(
            List<List<LandmarkPoint>> hands,
            int inputWidth,
            int inputHeight
    ) {
        this.hands = hands == null ? List.of() : hands;
        this.inputWidth = Math.max(1, inputWidth);
        this.inputHeight = Math.max(1, inputHeight);
        updateMapping();
        invalidate();
    }

    public void clear() {
        hands = List.of();
        invalidate();
    }

    public void setShowLandmarks(boolean showLandmarks) {
        this.showLandmarks = showLandmarks;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showLandmarks) {
            return;
        }
        for (List<LandmarkPoint> hand : hands) {
            if (hand.size() < 21) {
                continue;
            }
            for (int[] connection : CONNECTIONS) {
                LandmarkPoint start = hand.get(connection[0]);
                LandmarkPoint end = hand.get(connection[1]);
                canvas.drawLine(
                        mapX(start.x),
                        mapY(start.y),
                        mapX(end.x),
                        mapY(end.y),
                        linePaint
                );
            }
            for (LandmarkPoint point : hand) {
                canvas.drawCircle(mapX(point.x), mapY(point.y), dp(3.3f), pointPaint);
            }
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateMapping();
    }

    private float mapX(float normalized) {
        return normalized * inputWidth * mappingScale - cropX;
    }

    private float mapY(float normalized) {
        return normalized * inputHeight * mappingScale - cropY;
    }

    private void updateMapping() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        mappingScale = Math.max(
                getWidth() / (float) inputWidth,
                getHeight() / (float) inputHeight
        );
        cropX = (inputWidth * mappingScale - getWidth()) * 0.5f;
        cropY = (inputHeight * mappingScale - getHeight()) * 0.5f;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
