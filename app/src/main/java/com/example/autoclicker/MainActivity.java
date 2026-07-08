package com.example.autoclicker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private EditText intervalInput;
    private EditText randomDelayInput;
    private EditText randomRangeXInput;
    private EditText randomRangeYInput;
    private EditText tapDurationInput;
    private EditText pressOffsetInput;
    private EditText fingerCountInput;
    private SharedPreferences prefs;

    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(ClickConfig.PREFS_NAME, Context.MODE_PRIVATE);
        setContentView(createContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        handler.post(statusUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statusUpdater);
    }

    private ScrollView createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 247, 247));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("手机连点器");
        title.setTextColor(Color.rgb(22, 22, 22));
        title.setTextSize(26f);
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchWrapParams(0, 8));

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(55, 55, 55));
        statusView.setTextSize(16f);
        statusView.setGravity(Gravity.START);
        content.addView(statusView, matchWrapParams(0, 20));

        TextView timeHint = new TextView(this);
        timeHint.setText("时间换算：1 秒 = 1000 毫秒，0.5 秒 = 500 毫秒。");
        timeHint.setTextColor(Color.rgb(82, 82, 82));
        timeHint.setTextSize(14f);
        content.addView(timeHint, matchWrapParams(0, 16));

        intervalInput = addNumberField(content, "基础间隔（毫秒）", prefs.getLong(
                ClickConfig.KEY_INTERVAL_MS,
                ClickConfig.DEFAULT_INTERVAL_MS
        ));

        randomDelayInput = addNumberField(content, "随机额外等待 0 到 N（毫秒）", prefs.getLong(
                ClickConfig.KEY_RANDOM_DELAY_MS,
                ClickConfig.DEFAULT_RANDOM_DELAY_MS
        ));

        randomRangeXInput = addNumberField(content, "X 随机半径（像素）", prefs.getInt(
                ClickConfig.KEY_RANDOM_RANGE_X_PX,
                ClickConfig.DEFAULT_RANDOM_RANGE_PX
        ));

        randomRangeYInput = addNumberField(content, "Y 随机半径（像素）", prefs.getInt(
                ClickConfig.KEY_RANDOM_RANGE_Y_PX,
                ClickConfig.DEFAULT_RANDOM_RANGE_PX
        ));

        tapDurationInput = addNumberField(content, "按下时长（毫秒）", prefs.getLong(
                ClickConfig.KEY_TAP_DURATION_MS,
                ClickConfig.DEFAULT_TAP_DURATION_MS
        ));

        pressOffsetInput = addNumberField(content, "按下偏移/抖动半径（像素）", prefs.getInt(
                ClickConfig.KEY_PRESS_OFFSET_PX,
                ClickConfig.DEFAULT_PRESS_OFFSET_PX
        ));

        fingerCountInput = addNumberField(content, "同时点击手指数（1 到 5）", prefs.getInt(
                ClickConfig.KEY_FINGER_COUNT,
                ClickConfig.DEFAULT_FINGER_COUNT
        ));

        Button saveIntervalButton = makeButton("保存参数");
        saveIntervalButton.setOnClickListener(v -> saveSettingsFromInput());
        content.addView(saveIntervalButton, matchWrapParams(0, 12));

        Button accessibilityButton = makeButton("打开无障碍设置");
        accessibilityButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        content.addView(accessibilityButton, matchWrapParams(0, 12));

        Button showOverlayButton = makeButton("显示悬浮控制条");
        showOverlayButton.setOnClickListener(v -> {
            ClickAccessibilityService service = ClickAccessibilityService.getRunningService();
            if (service == null) {
                Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_SHORT).show();
                return;
            }
            service.showControlOverlay();
            Toast.makeText(this, "悬浮控制条已显示", Toast.LENGTH_SHORT).show();
        });
        content.addView(showOverlayButton, matchWrapParams(0, 12));

        Button stopButton = makeButton("停止连点");
        stopButton.setOnClickListener(v -> {
            ClickAccessibilityService service = ClickAccessibilityService.getRunningService();
            if (service != null) {
                service.stopClicking();
            }
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
        });
        content.addView(stopButton, matchWrapParams(0, 12));

        return scrollView;
    }

    private void saveSettingsFromInput() {
        Long intervalMs = parseLongField(intervalInput);
        Long randomDelayMs = parseLongField(randomDelayInput);
        Integer randomRangeXPx = parseIntField(randomRangeXInput);
        Integer randomRangeYPx = parseIntField(randomRangeYInput);
        Long tapDurationMs = parseLongField(tapDurationInput);
        Integer pressOffsetPx = parseIntField(pressOffsetInput);
        Integer fingerCount = parseIntField(fingerCountInput);
        if (intervalMs == null || randomDelayMs == null || randomRangeXPx == null
                || randomRangeYPx == null || tapDurationMs == null || pressOffsetPx == null || fingerCount == null) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
            return;
        }

        intervalMs = clamp(intervalMs, ClickConfig.MIN_INTERVAL_MS, ClickConfig.MAX_INTERVAL_MS);
        randomDelayMs = clamp(randomDelayMs, 0L, ClickConfig.MAX_RANDOM_DELAY_MS);
        randomRangeXPx = clamp(randomRangeXPx, 0, ClickConfig.MAX_RANDOM_RANGE_PX);
        randomRangeYPx = clamp(randomRangeYPx, 0, ClickConfig.MAX_RANDOM_RANGE_PX);
        tapDurationMs = clamp(tapDurationMs, ClickConfig.MIN_TAP_DURATION_MS, ClickConfig.MAX_TAP_DURATION_MS);
        pressOffsetPx = clamp(pressOffsetPx, 0, ClickConfig.MAX_PRESS_OFFSET_PX);
        fingerCount = clamp(fingerCount, ClickConfig.MIN_FINGER_COUNT, ClickConfig.MAX_FINGER_COUNT);

        prefs.edit()
                .putLong(ClickConfig.KEY_INTERVAL_MS, intervalMs)
                .putLong(ClickConfig.KEY_RANDOM_DELAY_MS, randomDelayMs)
                .putInt(ClickConfig.KEY_RANDOM_RANGE_X_PX, randomRangeXPx)
                .putInt(ClickConfig.KEY_RANDOM_RANGE_Y_PX, randomRangeYPx)
                .putLong(ClickConfig.KEY_TAP_DURATION_MS, tapDurationMs)
                .putInt(ClickConfig.KEY_PRESS_OFFSET_PX, pressOffsetPx)
                .putInt(ClickConfig.KEY_FINGER_COUNT, fingerCount)
                .apply();

        intervalInput.setText(String.valueOf(intervalMs));
        randomDelayInput.setText(String.valueOf(randomDelayMs));
        randomRangeXInput.setText(String.valueOf(randomRangeXPx));
        randomRangeYInput.setText(String.valueOf(randomRangeYPx));
        tapDurationInput.setText(String.valueOf(tapDurationMs));
        pressOffsetInput.setText(String.valueOf(pressOffsetPx));
        fingerCountInput.setText(String.valueOf(fingerCount));

        ClickAccessibilityService service = ClickAccessibilityService.getRunningService();
        if (service != null) {
            service.reloadSettings();
        }
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        ClickAccessibilityService service = ClickAccessibilityService.getRunningService();
        long intervalMs = prefs.getLong(ClickConfig.KEY_INTERVAL_MS, ClickConfig.DEFAULT_INTERVAL_MS);
        long randomDelayMs = prefs.getLong(ClickConfig.KEY_RANDOM_DELAY_MS, ClickConfig.DEFAULT_RANDOM_DELAY_MS);
        int randomRangeXPx = prefs.getInt(ClickConfig.KEY_RANDOM_RANGE_X_PX, ClickConfig.DEFAULT_RANDOM_RANGE_PX);
        int randomRangeYPx = prefs.getInt(ClickConfig.KEY_RANDOM_RANGE_Y_PX, ClickConfig.DEFAULT_RANDOM_RANGE_PX);
        long tapDurationMs = prefs.getLong(ClickConfig.KEY_TAP_DURATION_MS, ClickConfig.DEFAULT_TAP_DURATION_MS);
        int pressOffsetPx = prefs.getInt(ClickConfig.KEY_PRESS_OFFSET_PX, ClickConfig.DEFAULT_PRESS_OFFSET_PX);
        int fingerCount = prefs.getInt(ClickConfig.KEY_FINGER_COUNT, ClickConfig.DEFAULT_FINGER_COUNT);
        boolean hasPoint = prefs.getBoolean(ClickConfig.KEY_HAS_POINT, false);

        String pointText = "未取点";
        if (hasPoint) {
            float x = prefs.getFloat(ClickConfig.KEY_CLICK_X, 0f);
            float y = prefs.getFloat(ClickConfig.KEY_CLICK_Y, 0f);
            pointText = Math.round(x) + ", " + Math.round(y);
        }

        String status = "无障碍服务：" + (enabled ? "已开启" : "未开启")
                + "\n服务连接：" + (service == null ? "未连接" : "运行中")
                + "\n点击位置：" + pointText
                + "\n基础间隔：" + intervalMs + " ms"
                + "\n随机等待：0-" + randomDelayMs + " ms"
                + "\n随机范围：X±" + randomRangeXPx + " px，Y±" + randomRangeYPx + " px"
                + "\n按下时长：" + tapDurationMs + " ms"
                + "\n按下偏移：±" + pressOffsetPx + " px"
                + "\n同时手指：" + fingerCount;
        statusView.setText(status);
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) {
            return false;
        }

        ComponentName expected = new ComponentName(this, ClickAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabled = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(enabled)) {
                return true;
            }
        }
        return false;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(16f);
        return button;
    }

    private EditText addNumberField(LinearLayout content, String label, long value) {
        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextColor(Color.rgb(55, 55, 55));
        textView.setTextSize(15f);
        content.addView(textView, matchWrapParams(0, 6));

        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setText(String.valueOf(value));
        content.addView(editText, matchWrapParams(0, 12));
        return editText;
    }

    private Long parseLongField(EditText editText) {
        try {
            return Long.parseLong(editText.getText().toString().trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private Integer parseIntField(EditText editText) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private LinearLayout.LayoutParams matchWrapParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(top), 0, dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
