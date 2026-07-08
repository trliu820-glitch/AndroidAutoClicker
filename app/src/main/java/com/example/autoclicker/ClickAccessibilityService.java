package com.example.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class ClickAccessibilityService extends AccessibilityService {
    private static ClickAccessibilityService runningService;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private WindowManager windowManager;
    private SharedPreferences prefs;
    private View controlView;
    private View ballView;
    private View pickerView;
    private WindowManager.LayoutParams controlParams;
    private WindowManager.LayoutParams ballParams;
    private Button startStopButton;
    private TextView statusText;
    private TextView intervalText;
    private TextView ballText;
    private boolean running;
    private boolean hasPoint;
    private float clickX;
    private float clickY;
    private long intervalMs = ClickConfig.DEFAULT_INTERVAL_MS;
    private long randomDelayMs = ClickConfig.DEFAULT_RANDOM_DELAY_MS;
    private int randomRangeXPx = ClickConfig.DEFAULT_RANDOM_RANGE_PX;
    private int randomRangeYPx = ClickConfig.DEFAULT_RANDOM_RANGE_PX;
    private long tapDurationMs = ClickConfig.DEFAULT_TAP_DURATION_MS;
    private int pressOffsetPx = ClickConfig.DEFAULT_PRESS_OFFSET_PX;
    private int fingerCount = ClickConfig.DEFAULT_FINGER_COUNT;

    private final Runnable clickLoop = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            performSingleClick();
            if (running) {
                handler.postDelayed(this, nextDelayMs());
            }
        }
    };

    public static ClickAccessibilityService getRunningService() {
        return runningService;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        runningService = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences(ClickConfig.PREFS_NAME, Context.MODE_PRIVATE);
        loadConfig();
        showControlOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        stopClicking();
    }

    @Override
    public void onDestroy() {
        stopClicking();
        removePickerOverlay();
        removeControlOverlay();
        removeBallOverlay();
        if (runningService == this) {
            runningService = null;
        }
        super.onDestroy();
    }

    public void showControlOverlay() {
        ensureServiceState();
        removeBallOverlay();
        if (controlView != null) {
            updateControlText();
            return;
        }

        controlView = createControlView();
        controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        controlParams.gravity = Gravity.START | Gravity.TOP;
        controlParams.x = prefs.getInt(ClickConfig.KEY_PANEL_X, dp(16));
        controlParams.y = prefs.getInt(ClickConfig.KEY_PANEL_Y, dp(120));

        try {
            windowManager.addView(controlView, controlParams);
        } catch (RuntimeException error) {
            controlView = null;
            Toast.makeText(this, "悬浮控制条显示失败", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopClicking() {
        running = false;
        handler.removeCallbacks(clickLoop);
        updateControlText();
    }

    public void reloadSettings() {
        ensureServiceState();
        loadConfig();
        updateControlText();
    }

    public void reloadInterval() {
        reloadSettings();
    }

    private View createControlView() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(32, 34, 38));
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        panel.setBackground(background);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(titleRow, new LinearLayout.LayoutParams(dp(282), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView handle = new TextView(this);
        handle.setText("连点器");
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(16f);
        handle.setGravity(Gravity.CENTER_VERTICAL);
        handle.setPadding(dp(4), 0, dp(4), 0);
        handle.setOnTouchListener(new DragTouchListener());
        titleRow.addView(handle, new LinearLayout.LayoutParams(0, dp(38), 1f));

        Button ballButton = makeSmallButton("球");
        ballButton.setOnClickListener(v -> collapseToBall());
        titleRow.addView(ballButton, new LinearLayout.LayoutParams(dp(38), dp(38)));

        Button closeButton = makeSmallButton("X");
        closeButton.setOnClickListener(v -> removeControlOverlay());
        titleRow.addView(closeButton, new LinearLayout.LayoutParams(dp(38), dp(38)));

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(232, 232, 232));
        statusText.setTextSize(13f);
        statusText.setPadding(dp(4), dp(6), dp(4), dp(6));
        panel.addView(statusText, new LinearLayout.LayoutParams(dp(282), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(actionRow, new LinearLayout.LayoutParams(dp(282), ViewGroup.LayoutParams.WRAP_CONTENT));

        startStopButton = makeSmallButton("开始");
        startStopButton.setOnClickListener(v -> {
            if (running) {
                stopClicking();
            } else {
                startClicking();
            }
        });
        actionRow.addView(startStopButton, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button pickButton = makeSmallButton("取点");
        pickButton.setOnClickListener(v -> showPickerOverlay());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        pickParams.setMargins(dp(8), 0, 0, 0);
        actionRow.addView(pickButton, pickParams);

        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        intervalRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams intervalRowParams = new LinearLayout.LayoutParams(
                dp(282),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        intervalRowParams.setMargins(0, dp(8), 0, 0);
        panel.addView(intervalRow, intervalRowParams);

        Button minusButton = makeSmallButton("-");
        minusButton.setOnClickListener(v -> adjustInterval(-50L));
        intervalRow.addView(minusButton, new LinearLayout.LayoutParams(dp(42), dp(38)));

        intervalText = new TextView(this);
        intervalText.setTextColor(Color.WHITE);
        intervalText.setTextSize(14f);
        intervalText.setGravity(Gravity.CENTER);
        intervalRow.addView(intervalText, new LinearLayout.LayoutParams(0, dp(38), 1f));

        Button plusButton = makeSmallButton("+");
        plusButton.setOnClickListener(v -> adjustInterval(50L));
        intervalRow.addView(plusButton, new LinearLayout.LayoutParams(dp(42), dp(38)));

        updateControlText();
        return panel;
    }

    private void collapseToBall() {
        removeControlOverlay();
        showBallOverlay();
    }

    private void showBallOverlay() {
        ensureServiceState();
        if (ballView != null) {
            updateControlText();
            return;
        }

        ballText = new TextView(this);
        ballText.setTextColor(Color.WHITE);
        ballText.setTextSize(15f);
        ballText.setGravity(Gravity.CENTER);
        ballText.setPadding(0, 0, 0, dp(1));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.rgb(32, 34, 38));
        background.setStroke(dp(2), Color.rgb(43, 170, 116));
        ballText.setBackground(background);
        ballText.setOnTouchListener(new BallTouchListener());

        ballParams = new WindowManager.LayoutParams(
                dp(58),
                dp(58),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        ballParams.gravity = Gravity.START | Gravity.TOP;
        ballParams.x = prefs.getInt(ClickConfig.KEY_BALL_X, prefs.getInt(ClickConfig.KEY_PANEL_X, dp(16)));
        ballParams.y = prefs.getInt(ClickConfig.KEY_BALL_Y, prefs.getInt(ClickConfig.KEY_PANEL_Y, dp(120)));

        ballView = ballText;
        updateControlText();
        try {
            windowManager.addView(ballView, ballParams);
        } catch (RuntimeException error) {
            ballView = null;
            ballText = null;
            Toast.makeText(this, "悬浮球显示失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void startClicking() {
        loadConfig();
        if (!hasPoint) {
            Toast.makeText(this, "请先取点", Toast.LENGTH_SHORT).show();
            showPickerOverlay();
            return;
        }
        running = true;
        handler.removeCallbacks(clickLoop);
        handler.post(clickLoop);
        updateControlText();
    }

    private void showPickerOverlay() {
        ensureServiceState();
        stopClicking();
        if (pickerView != null) {
            return;
        }

        FrameLayout picker = new FrameLayout(this);
        picker.setBackgroundColor(Color.argb(75, 0, 137, 123));

        TextView prompt = new TextView(this);
        prompt.setText("点击屏幕选择连点位置");
        prompt.setTextColor(Color.WHITE);
        prompt.setTextSize(20f);
        prompt.setGravity(Gravity.CENTER);
        prompt.setBackgroundColor(Color.argb(180, 0, 0, 0));
        picker.addView(prompt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(96),
                Gravity.TOP
        ));

        picker.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_UP) {
                return true;
            }
            saveClickPoint(event.getRawX(), event.getRawY());
            removePickerOverlay();
            Toast.makeText(this, "取点：" + Math.round(clickX) + ", " + Math.round(clickY), Toast.LENGTH_SHORT).show();
            return true;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.START | Gravity.TOP;

        pickerView = picker;
        try {
            windowManager.addView(pickerView, params);
        } catch (RuntimeException error) {
            pickerView = null;
            Toast.makeText(this, "取点层显示失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveClickPoint(float x, float y) {
        clickX = x;
        clickY = y;
        hasPoint = true;
        prefs.edit()
                .putBoolean(ClickConfig.KEY_HAS_POINT, true)
                .putFloat(ClickConfig.KEY_CLICK_X, clickX)
                .putFloat(ClickConfig.KEY_CLICK_Y, clickY)
                .apply();
        updateControlText();
    }

    private void performSingleClick() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        GestureDescription.Builder builder = new GestureDescription.Builder();
        for (int index = 0; index < fingerCount; index++) {
            builder.addStroke(new GestureDescription.StrokeDescription(
                    buildClickPath(metrics),
                    0L,
                    tapDurationMs
            ));
        }
        GestureDescription gesture = builder.build();

        boolean sent = dispatchGesture(gesture, null, null);
        if (!sent) {
            stopClicking();
            Toast.makeText(this, "点击发送失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeControlOverlay() {
        if (controlView == null || windowManager == null) {
            controlView = null;
            return;
        }
        try {
            windowManager.removeView(controlView);
        } catch (RuntimeException ignored) {
        }
        controlView = null;
        startStopButton = null;
        statusText = null;
        intervalText = null;
    }

    private void removeBallOverlay() {
        if (ballView == null || windowManager == null) {
            ballView = null;
            ballText = null;
            return;
        }
        try {
            windowManager.removeView(ballView);
        } catch (RuntimeException ignored) {
        }
        ballView = null;
        ballText = null;
    }

    private void removePickerOverlay() {
        if (pickerView == null || windowManager == null) {
            pickerView = null;
            return;
        }
        try {
            windowManager.removeView(pickerView);
        } catch (RuntimeException ignored) {
        }
        pickerView = null;
    }

    private void adjustInterval(long deltaMs) {
        intervalMs = clamp(intervalMs + deltaMs, ClickConfig.MIN_INTERVAL_MS, ClickConfig.MAX_INTERVAL_MS);
        prefs.edit().putLong(ClickConfig.KEY_INTERVAL_MS, intervalMs).apply();
        updateControlText();
    }

    private void updateControlText() {
        if (startStopButton != null) {
            startStopButton.setText(running ? "停止" : "开始");
        }
        if (statusText != null) {
            String pointText = hasPoint
                    ? Math.round(clickX) + ", " + Math.round(clickY)
                    : "未取点";
            statusText.setText("中心：" + pointText
                    + "\n范围：X±" + randomRangeXPx + " Y±" + randomRangeYPx
                    + "\n等待：" + intervalMs + "+0-" + randomDelayMs + " ms"
                    + "\n按下：" + tapDurationMs + " ms，偏移±" + pressOffsetPx
                    + "\n手指：" + fingerCount);
        }
        if (intervalText != null) {
            intervalText.setText(intervalMs + " ms");
        }
        if (ballText != null) {
            ballText.setText(running ? "运行" : "点");
        }
    }

    private void loadConfig() {
        ensurePrefs();
        intervalMs = prefs.getLong(ClickConfig.KEY_INTERVAL_MS, ClickConfig.DEFAULT_INTERVAL_MS);
        intervalMs = clamp(intervalMs, ClickConfig.MIN_INTERVAL_MS, ClickConfig.MAX_INTERVAL_MS);
        randomDelayMs = prefs.getLong(ClickConfig.KEY_RANDOM_DELAY_MS, ClickConfig.DEFAULT_RANDOM_DELAY_MS);
        randomDelayMs = clamp(randomDelayMs, 0L, ClickConfig.MAX_RANDOM_DELAY_MS);
        randomRangeXPx = prefs.getInt(ClickConfig.KEY_RANDOM_RANGE_X_PX, ClickConfig.DEFAULT_RANDOM_RANGE_PX);
        randomRangeXPx = clamp(randomRangeXPx, 0, ClickConfig.MAX_RANDOM_RANGE_PX);
        randomRangeYPx = prefs.getInt(ClickConfig.KEY_RANDOM_RANGE_Y_PX, ClickConfig.DEFAULT_RANDOM_RANGE_PX);
        randomRangeYPx = clamp(randomRangeYPx, 0, ClickConfig.MAX_RANDOM_RANGE_PX);
        tapDurationMs = prefs.getLong(ClickConfig.KEY_TAP_DURATION_MS, ClickConfig.DEFAULT_TAP_DURATION_MS);
        tapDurationMs = clamp(tapDurationMs, ClickConfig.MIN_TAP_DURATION_MS, ClickConfig.MAX_TAP_DURATION_MS);
        pressOffsetPx = prefs.getInt(ClickConfig.KEY_PRESS_OFFSET_PX, ClickConfig.DEFAULT_PRESS_OFFSET_PX);
        pressOffsetPx = clamp(pressOffsetPx, 0, ClickConfig.MAX_PRESS_OFFSET_PX);
        fingerCount = prefs.getInt(ClickConfig.KEY_FINGER_COUNT, ClickConfig.DEFAULT_FINGER_COUNT);
        fingerCount = clamp(fingerCount, ClickConfig.MIN_FINGER_COUNT, ClickConfig.MAX_FINGER_COUNT);
        hasPoint = prefs.getBoolean(ClickConfig.KEY_HAS_POINT, false);
        clickX = prefs.getFloat(ClickConfig.KEY_CLICK_X, 0f);
        clickY = prefs.getFloat(ClickConfig.KEY_CLICK_Y, 0f);
    }

    private Path buildClickPath(DisplayMetrics metrics) {
        float startX = clamp(clickX + randomOffset(randomRangeXPx), 0f, Math.max(0f, metrics.widthPixels - 1f));
        float startY = clamp(clickY + randomOffset(randomRangeYPx), 0f, Math.max(0f, metrics.heightPixels - 1f));
        float endX = startX;
        float endY = startY;
        if (pressOffsetPx > 0) {
            endX = clamp(startX + randomOffset(pressOffsetPx), 0f, Math.max(0f, metrics.widthPixels - 1f));
            endY = clamp(startY + randomOffset(pressOffsetPx), 0f, Math.max(0f, metrics.heightPixels - 1f));
        }

        Path path = new Path();
        path.moveTo(startX, startY);
        if (pressOffsetPx > 0) {
            path.lineTo(endX, endY);
        }
        return path;
    }

    private long nextDelayMs() {
        if (randomDelayMs <= 0L) {
            return intervalMs;
        }
        return intervalMs + nextLongInclusive(randomDelayMs);
    }

    private long nextLongInclusive(long maxValue) {
        if (maxValue <= 0L) {
            return 0L;
        }
        return Math.round(random.nextDouble() * maxValue);
    }

    private float randomOffset(int radiusPx) {
        if (radiusPx <= 0) {
            return 0f;
        }
        return random.nextInt(radiusPx * 2 + 1) - radiusPx;
    }

    private void ensureServiceState() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        ensurePrefs();
    }

    private void ensurePrefs() {
        if (prefs == null) {
            prefs = getSharedPreferences(ClickConfig.PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private Button makeSmallButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14f);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.round(value * metrics.density);
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private float downRawX;
        private float downRawY;
        private int downX;
        private int downY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (controlParams == null || controlView == null || windowManager == null) {
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = controlParams.x;
                    downY = controlParams.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int nextX = downX + Math.round(event.getRawX() - downRawX);
                    int nextY = downY + Math.round(event.getRawY() - downRawY);
                    if (Math.abs(nextX - controlParams.x) > 1 || Math.abs(nextY - controlParams.y) > 1) {
                        moved = true;
                        controlParams.x = nextX;
                        controlParams.y = nextY;
                        windowManager.updateViewLayout(controlView, controlParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    prefs.edit()
                            .putInt(ClickConfig.KEY_PANEL_X, controlParams.x)
                            .putInt(ClickConfig.KEY_PANEL_Y, controlParams.y)
                            .apply();
                    if (!moved) {
                        view.performClick();
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class BallTouchListener implements View.OnTouchListener {
        private float downRawX;
        private float downRawY;
        private int downX;
        private int downY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (ballParams == null || ballView == null || windowManager == null) {
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = ballParams.x;
                    downY = ballParams.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int nextX = downX + Math.round(event.getRawX() - downRawX);
                    int nextY = downY + Math.round(event.getRawY() - downRawY);
                    if (Math.abs(nextX - ballParams.x) > 1 || Math.abs(nextY - ballParams.y) > 1) {
                        moved = true;
                        ballParams.x = nextX;
                        ballParams.y = nextY;
                        windowManager.updateViewLayout(ballView, ballParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    prefs.edit()
                            .putInt(ClickConfig.KEY_BALL_X, ballParams.x)
                            .putInt(ClickConfig.KEY_BALL_Y, ballParams.y)
                            .apply();
                    if (!moved) {
                        showControlOverlay();
                    }
                    return true;
                default:
                    return true;
            }
        }
    }
}
