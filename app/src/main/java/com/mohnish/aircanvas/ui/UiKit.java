package com.mohnish.aircanvas.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiKit {
    public static final int BACKGROUND = 0xFF07111F;
    public static final int SURFACE = 0xE6101D31;
    public static final int SURFACE_SELECTED = 0xF12B3150;
    public static final int PRIMARY = 0xFF5EE7F7;
    public static final int SECONDARY = 0xFFA889FF;
    public static final int TEXT = 0xFFF4F8FF;
    public static final int MUTED = 0xFFA9B7CB;
    public static final int ERROR = 0xFFFF6B88;

    private UiKit() {
    }

    public static Button button(Context context, String text, boolean compact) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setTextSize(compact ? 12f : 14f);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(context, compact ? 10 : 14), 0, dp(context, compact ? 10 : 14), 0);
        button.setBackground(background(context, SURFACE, compact ? 12f : 16f, 0x485EE7F7));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, compact ? 40 : 46)
        );
        params.setMarginStart(dp(context, 4));
        params.setMarginEnd(dp(context, 4));
        button.setLayoutParams(params);
        return button;
    }

    public static TextView chip(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(12f);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        view.setBackground(background(context, SURFACE, 14f, 0x385EE7F7));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 34)
        ));
        return view;
    }

    public static TextView title(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(16f);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    public static GradientDrawable background(
            Context context,
            int color,
            float radiusDp,
            int strokeColor
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(
                radiusDp * context.getResources().getDisplayMetrics().density
        );
        if (Color.alpha(strokeColor) > 0) {
            drawable.setStroke(dp(context, 1), strokeColor);
        }
        return drawable;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
