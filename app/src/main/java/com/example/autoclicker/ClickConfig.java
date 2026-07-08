package com.example.autoclicker;

final class ClickConfig {
    static final String PREFS_NAME = "auto_clicker";
    static final String KEY_INTERVAL_MS = "interval_ms";
    static final String KEY_CLICK_X = "click_x";
    static final String KEY_CLICK_Y = "click_y";
    static final String KEY_HAS_POINT = "has_point";
    static final String KEY_PANEL_X = "panel_x";
    static final String KEY_PANEL_Y = "panel_y";
    static final String KEY_BALL_X = "ball_x";
    static final String KEY_BALL_Y = "ball_y";
    static final String KEY_RANDOM_RANGE_X_PX = "random_range_x_px";
    static final String KEY_RANDOM_RANGE_Y_PX = "random_range_y_px";
    static final String KEY_RANDOM_DELAY_MS = "random_delay_ms";
    static final String KEY_TAP_DURATION_MS = "tap_duration_ms";
    static final String KEY_PRESS_OFFSET_PX = "press_offset_px";
    static final String KEY_FINGER_COUNT = "finger_count";

    static final long DEFAULT_INTERVAL_MS = 100L;
    static final long MIN_INTERVAL_MS = 50L;
    static final long MAX_INTERVAL_MS = 10_000L;
    static final long DEFAULT_RANDOM_DELAY_MS = 0L;
    static final long MAX_RANDOM_DELAY_MS = 10_000L;
    static final long DEFAULT_TAP_DURATION_MS = 45L;
    static final long MIN_TAP_DURATION_MS = 20L;
    static final long MAX_TAP_DURATION_MS = 1_000L;
    static final int DEFAULT_RANDOM_RANGE_PX = 0;
    static final int MAX_RANDOM_RANGE_PX = 500;
    static final int DEFAULT_PRESS_OFFSET_PX = 0;
    static final int MAX_PRESS_OFFSET_PX = 60;
    static final int DEFAULT_FINGER_COUNT = 1;
    static final int MIN_FINGER_COUNT = 1;
    static final int MAX_FINGER_COUNT = 5;

    private ClickConfig() {
    }
}
