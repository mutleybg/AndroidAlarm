package com.example.androidalarm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import androidx.annotation.Nullable;

import com.example.androidalarm.databinding.ViewAlarmPanelBinding;

/**
 * The controls for a single alarm: a title, Hour and Min scroller pickers, an
 * on/off switch, and a state label below it. When the switch is off the label
 * reads "the alarm is off"; when it is on the label shows a two-line countdown
 * to the next trigger, refreshed every {@link #REFRESH_INTERVAL_MS}. Emits a
 * single {@link OnChangeListener#onChanged()} whenever the user edits anything.
 */
public class AlarmPanelView extends LinearLayout {

    /** Notified after any user edit (time or switch). */
    public interface OnChangeListener {
        void onChanged();
    }

    /** Minutes are picked in 5-minute steps (0, 5, ... 55). */
    private static final int MINUTE_STEP = 5;
    /** How often the on-state countdown label is refreshed (ms). */
    private static final long REFRESH_INTERVAL_MS = 10_000L;

    private final ViewAlarmPanelBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Periodically refreshes the countdown label while the alarm is on. */
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshStateLabel();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

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
        setupPickers();

        binding.hourPicker.setOnValueChangedListener((p, o, n) -> {
            refreshStateLabel();
            notifyChanged();
        });
        binding.minPicker.setOnValueChangedListener((p, o, n) -> {
            refreshStateLabel();
            notifyChanged();
        });
        binding.enableSwitch.setOnCheckedChangeListener((b, checked) -> {
            applyEnabledState(checked);
            notifyChanged();
        });
        applyEnabledState(binding.enableSwitch.isChecked());
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

        // On small screens the panel lives inside a ScrollView; make sure a
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

    public void setOnChangeListener(@Nullable OnChangeListener listener) {
        this.listener = listener;
    }

    public void setTitle(String title) {
        binding.title.setText(title);
    }

    /** Loads saved settings into the views without firing change callbacks. */
    public void setState(int hour, int minute, boolean enabled) {
        loading = true;
        binding.hourPicker.setValue(clamp(hour, 23));
        binding.minPicker.setValue(clamp(minute / MINUTE_STEP, 60 / MINUTE_STEP - 1));
        binding.enableSwitch.setChecked(enabled);
        applyEnabledState(enabled);
        loading = false;
    }

    public int getHour() {
        return binding.hourPicker.getValue();
    }

    public int getMinute() {
        return binding.minPicker.getValue() * MINUTE_STEP;
    }

    public boolean isAlarmEnabled() {
        return binding.enableSwitch.isChecked();
    }

    /** Sets the switch without firing a change callback (e.g. to revert it). */
    public void setSwitchChecked(boolean checked) {
        loading = true;
        binding.enableSwitch.setChecked(checked);
        applyEnabledState(checked);
        loading = false;
    }

    /** Updates the label and starts/stops the countdown ticker for a state. */
    private void applyEnabledState(boolean enabled) {
        if (enabled) {
            startTicking();
        } else {
            stopTicking();
            refreshStateLabel();
        }
    }

    /**
     * Refreshes the state label: the "off" text when disabled, or a two-line
     * countdown ("... in XX hours and YY minutes") to the next trigger when on.
     */
    private void refreshStateLabel() {
        if (!binding.enableSwitch.isChecked()) {
            binding.stateLabel.setText(R.string.state_off);
            return;
        }
        long remaining = AlarmScheduler.nextTrigger(getHour(), getMinute(), 0)
                - System.currentTimeMillis();
        if (remaining < 0) {
            remaining = 0;
        }
        long totalMinutes = Math.round(remaining / 60_000.0);
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        binding.stateLabel.setText(
                getContext().getString(R.string.state_on_countdown, hours, minutes));
    }

    private void startTicking() {
        handler.removeCallbacks(ticker);
        // Runs immediately (refreshes the label now), then every interval.
        handler.post(ticker);
    }

    private void stopTicking() {
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyEnabledState(binding.enableSwitch.isChecked());
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTicking();
        super.onDetachedFromWindow();
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
}
