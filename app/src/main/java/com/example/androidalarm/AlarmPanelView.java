package com.example.androidalarm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.androidalarm.databinding.ViewAlarmPanelBinding;

/**
 * The controls for a single alarm: a title, Hour and Min scroller pickers, a
 * row of seven day toggles (Monday-first) under a "Days Of Week" heading, and
 * an on/off switch with an On/Off state label. Emits a single
 * {@link OnChangeListener#onChanged()} whenever the user edits anything.
 */
public class AlarmPanelView extends LinearLayout {

    /** Notified after any user edit (time, day toggle or switch). */
    public interface OnChangeListener {
        void onChanged();
    }

    private static final int DAY_COUNT = 7;
    /** Minutes are picked in 5-minute steps (0, 5, ... 55). */
    private static final int MINUTE_STEP = 5;

    private final ViewAlarmPanelBinding binding;
    private final TextView[] dayViews = new TextView[DAY_COUNT];

    @Nullable
    private OnChangeListener listener;
    /** Suppresses callbacks while we load saved state into the views. */
    private boolean loading;

    public AlarmPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        binding = ViewAlarmPanelBinding.inflate(LayoutInflater.from(context), this);

        binding.hourLabel.setText(R.string.label_hour);
        binding.minLabel.setText(R.string.label_min);
        binding.daysLabel.setText(R.string.label_days_of_week);
        setupPickers();
        buildDayToggles(context);

        binding.hourPicker.setOnValueChangedListener((p, o, n) -> notifyChanged());
        binding.minPicker.setOnValueChangedListener((p, o, n) -> notifyChanged());
        binding.enableSwitch.setOnCheckedChangeListener((b, checked) -> {
            updateStateLabel(checked);
            notifyChanged();
        });
        updateStateLabel(binding.enableSwitch.isChecked());
    }

    private void setupPickers() {
        binding.hourPicker.setMinValue(0);
        binding.hourPicker.setMaxValue(23);
        binding.hourPicker.setFormatter(value -> two(value));
        binding.hourPicker.setWrapSelectorWheel(true);

        int minuteSteps = 60 / MINUTE_STEP;
        String[] minuteLabels = new String[minuteSteps];
        for (int i = 0; i < minuteSteps; i++) {
            minuteLabels[i] = two(i * MINUTE_STEP);
        }
        binding.minPicker.setMinValue(0);
        binding.minPicker.setMaxValue(minuteSteps - 1);
        binding.minPicker.setDisplayedValues(minuteLabels);
        binding.minPicker.setWrapSelectorWheel(true);

        // On small screens the panels live inside a ScrollView; make sure a
        // drag on a picker changes its value instead of scrolling the page.
        keepScrollFromStealing(binding.hourPicker);
        keepScrollFromStealing(binding.minPicker);
    }

    /**
     * Stops an ancestor scroll container from intercepting vertical drags on a
     * picker. Returns false so the picker still handles the touch itself.
     */
    @SuppressLint("ClickableViewAccessibility")
    private static void keepScrollFromStealing(NumberPicker picker) {
        picker.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                ViewParent parent = v.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
            }
            return false;
        });
    }

    private void buildDayToggles(Context context) {
        String[] letters = getResources().getStringArray(R.array.day_letters);
        int size = dp(44);
        int margin = dp(3);
        for (int i = 0; i < DAY_COUNT; i++) {
            TextView day = new TextView(context);
            LayoutParams lp = new LayoutParams(0, size, 1f);
            lp.setMargins(margin, 0, margin, 0);
            day.setLayoutParams(lp);
            day.setGravity(Gravity.CENTER);
            day.setBackgroundResource(R.drawable.day_circle_bg);
            day.setText(letters[i]);
            day.setTextColor(getResources().getColor(R.color.black, context.getTheme()));
            day.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            day.setOnClickListener(v -> {
                v.setSelected(!v.isSelected());
                notifyChanged();
            });
            dayViews[i] = day;
            binding.daysRow.addView(day);
        }
    }

    public void setOnChangeListener(@Nullable OnChangeListener listener) {
        this.listener = listener;
    }

    public void setTitle(String title) {
        binding.title.setText(title);
    }

    /** Loads saved settings into the views without firing change callbacks. */
    public void setState(int hour, int minute, int daysMask, boolean enabled) {
        loading = true;
        binding.hourPicker.setValue(clamp(hour, 23));
        binding.minPicker.setValue(clamp(minute / MINUTE_STEP, 60 / MINUTE_STEP - 1));
        for (int i = 0; i < DAY_COUNT; i++) {
            dayViews[i].setSelected((daysMask & (1 << i)) != 0);
        }
        binding.enableSwitch.setChecked(enabled);
        updateStateLabel(enabled);
        loading = false;
    }

    public int getHour() {
        return binding.hourPicker.getValue();
    }

    public int getMinute() {
        return binding.minPicker.getValue() * MINUTE_STEP;
    }

    public int getDaysMask() {
        int mask = 0;
        for (int i = 0; i < DAY_COUNT; i++) {
            if (dayViews[i].isSelected()) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    public boolean isAlarmEnabled() {
        return binding.enableSwitch.isChecked();
    }

    /** Sets the switch without firing a change callback (e.g. to revert it). */
    public void setSwitchChecked(boolean checked) {
        loading = true;
        binding.enableSwitch.setChecked(checked);
        updateStateLabel(checked);
        loading = false;
    }

    private void updateStateLabel(boolean checked) {
        binding.stateLabel.setText(checked ? R.string.state_on : R.string.state_off);
    }

    private void notifyChanged() {
        if (!loading && listener != null) {
            listener.onChanged();
        }
    }

    private static int clamp(int value, int max) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, max);
    }

    private static String two(int value) {
        return String.format(java.util.Locale.US, "%02d", value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
