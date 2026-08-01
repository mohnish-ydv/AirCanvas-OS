package com.mohnish.aircanvas.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.camera.core.CameraSelector;

import com.mohnish.aircanvas.gesture.AdaptiveGestureProfile;

public final class AppSettings {
    public enum PerformanceProfile {
        ECO("Eco", 72L),
        BALANCED("Balanced", 42L),
        SMOOTH("Smooth", 30L);

        public final String label;
        public final long frameIntervalMs;

        PerformanceProfile(String label, long frameIntervalMs) {
            this.label = label;
            this.frameIntervalMs = frameIntervalMs;
        }
    }

    private static final String PREFS = "aircanvas.settings";
    private final SharedPreferences preferences;

    public AppSettings(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * One-time migration for installs upgraded from the stricter v2.1 gesture build.
     * It clears only learned pinch thresholds, keeps explicit Eco choices, and moves
     * legacy/default performance settings to the new low-latency profile.
     */
    public void applyPinchRescueV22Migration() {
        if (preferences.getBoolean("pinchRescueV22Migrated", false)) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit()
                .remove("adaptivePinchMean")
                .remove("adaptivePinchSamples")
                .remove("adaptiveOpenMean")
                .remove("adaptiveOpenSamples")
                .remove("adaptiveRevision")
                .putBoolean("pinchRescueV22Migrated", true);

        if (!preferences.contains("gestureSensitivity")
                || preferences.getFloat("gestureSensitivity", 0.72f) < 0.65f) {
            editor.putFloat("gestureSensitivity", 0.72f);
        }
        if (!preferences.contains("cursorSmoothing")
                || preferences.getFloat("cursorSmoothing", 0.46f) > 0.56f) {
            editor.putFloat("cursorSmoothing", 0.46f);
        }
        String profile = preferences.getString("performanceProfile", null);
        if (!PerformanceProfile.ECO.name().equals(profile)
                && !PerformanceProfile.SMOOTH.name().equals(profile)) {
            editor.putString("performanceProfile", PerformanceProfile.SMOOTH.name());
        }
        editor.apply();
    }


    /**
     * One-time migration for the rebuilt v2.3 pinch geometry. Learned ratios from
     * the old single-palm-scale detector are not comparable, so they must not be
     * reused. Existing explicit Eco choices remain respected.
     */
    public void applyPinchReliabilityV23Migration() {
        if (preferences.getBoolean("pinchReliabilityV23Migrated", false)) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit()
                .remove("adaptivePinchMean")
                .remove("adaptivePinchSamples")
                .remove("adaptiveOpenMean")
                .remove("adaptiveOpenSamples")
                .remove("adaptiveRevision")
                .putBoolean("pinchReliabilityV23Migrated", true);

        if (preferences.getFloat("gestureSensitivity", 0.76f) < 0.76f) {
            editor.putFloat("gestureSensitivity", 0.76f);
        }
        if (preferences.getFloat("cursorSmoothing", 0.42f) > 0.42f) {
            editor.putFloat("cursorSmoothing", 0.42f);
        }
        String profile = preferences.getString("performanceProfile", null);
        if (!PerformanceProfile.ECO.name().equals(profile)) {
            editor.putString("performanceProfile", PerformanceProfile.SMOOTH.name());
        }
        editor.apply();
    }

    public boolean onboardingComplete() {
        return preferences.getBoolean("onboardingComplete", false);
    }

    public void setOnboardingComplete(boolean complete) {
        preferences.edit().putBoolean("onboardingComplete", complete).apply();
    }

    public int lensFacing() {
        return preferences.getInt("lensFacing", CameraSelector.LENS_FACING_FRONT);
    }

    public void setLensFacing(int facing) {
        preferences.edit().putInt("lensFacing", facing).apply();
    }

    public float gestureSensitivity() {
        return preferences.getFloat("gestureSensitivity", 0.76f);
    }

    public void setGestureSensitivity(float value) {
        preferences.edit().putFloat("gestureSensitivity", clamp(value)).apply();
    }

    public float cursorSmoothing() {
        return preferences.getFloat("cursorSmoothing", 0.42f);
    }

    public void setCursorSmoothing(float value) {
        preferences.edit().putFloat("cursorSmoothing", clamp(value)).apply();
    }

    public boolean showLandmarks() {
        return preferences.getBoolean("showLandmarks", true);
    }

    public void setShowLandmarks(boolean show) {
        preferences.edit().putBoolean("showLandmarks", show).apply();
    }

    public boolean hackerMaskEnabled() {
        return preferences.getBoolean("hackerMaskEnabled", false);
    }

    public void setHackerMaskEnabled(boolean enabled) {
        preferences.edit().putBoolean("hackerMaskEnabled", enabled).apply();
    }

    public float hackerMaskCenterX() {
        return preferences.getFloat("hackerMaskCenterX", 0.5f);
    }

    public float hackerMaskCenterY() {
        return preferences.getFloat("hackerMaskCenterY", 0.34f);
    }

    public float hackerMaskSize() {
        return preferences.getFloat("hackerMaskSize", 0.34f);
    }

    public void setHackerMaskGeometry(float centerX, float centerY, float size) {
        preferences.edit()
                .putFloat("hackerMaskCenterX", clampRange(centerX, 0.15f, 0.85f))
                .putFloat("hackerMaskCenterY", clampRange(centerY, 0.16f, 0.76f))
                .putFloat("hackerMaskSize", clampRange(size, 0.20f, 0.60f))
                .apply();
    }

    public boolean showGrid() {
        return preferences.getBoolean("showGrid", true);
    }

    public void setShowGrid(boolean show) {
        preferences.edit().putBoolean("showGrid", show).apply();
    }

    public boolean smartSnap() {
        return preferences.getBoolean("smartSnap", true);
    }

    public void setSmartSnap(boolean enabled) {
        preferences.edit().putBoolean("smartSnap", enabled).apply();
    }


    public boolean landscapeFirst() {
        return preferences.getBoolean("landscapeFirst", true);
    }

    public void setLandscapeFirst(boolean enabled) {
        preferences.edit().putBoolean("landscapeFirst", enabled).apply();
    }

    public AdaptiveGestureProfile.Snapshot adaptiveGestureSnapshot() {
        return new AdaptiveGestureProfile.Snapshot(
                preferences.getFloat("adaptivePinchMean", 0.13f),
                preferences.getInt("adaptivePinchSamples", 0),
                preferences.getFloat("adaptiveOpenMean", 1.05f),
                preferences.getInt("adaptiveOpenSamples", 0),
                preferences.getLong("adaptiveRevision", 0L)
        );
    }

    public void setAdaptiveGestureSnapshot(AdaptiveGestureProfile.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        preferences.edit()
                .putFloat("adaptivePinchMean", snapshot.pinchMean())
                .putInt("adaptivePinchSamples", snapshot.pinchSamples())
                .putFloat("adaptiveOpenMean", snapshot.openMean())
                .putInt("adaptiveOpenSamples", snapshot.openSamples())
                .putLong("adaptiveRevision", snapshot.revision())
                .apply();
    }

    public void resetAdaptiveGestureProfile() {
        preferences.edit()
                .remove("adaptivePinchMean")
                .remove("adaptivePinchSamples")
                .remove("adaptiveOpenMean")
                .remove("adaptiveOpenSamples")
                .remove("adaptiveRevision")
                .apply();
    }

    public PerformanceProfile performanceProfile() {
        String name = preferences.getString("performanceProfile", PerformanceProfile.SMOOTH.name());
        try {
            return PerformanceProfile.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return PerformanceProfile.SMOOTH;
        }
    }

    public void setPerformanceProfile(PerformanceProfile profile) {
        preferences.edit().putString("performanceProfile", profile.name()).apply();
    }

    private static float clampRange(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return (minimum + maximum) * 0.5f;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) {
            return 0.5f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
