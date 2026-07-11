package com.example.androidalarm;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.androidalarm.databinding.ViewAlarmPanelBinding;

/**
 * The controls for a single alarm: a title, Hour and Min numeric fields, an
 * on/off switch, and a row of seven day toggles (Monday-first). Emits a single
 * {@link OnChangeListener#onChanged()} whenever the user edits anything.
 */
public class AlarmPanelView extends LinearLayout {

    /** Notified after any user edit (time, day toggle or switch). */
    public interface OnChangeListener {
        void onChanged();
    }

    private static final int DAY_COUNT = 7;

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
        buildDayToggles(context);

        TextWatcher watcher = new SimpleTextWatcher(this::notifyChanged);
        binding.hourField.addTextChangedListener(watcher);
        binding.minField.addTextChangedListener(watcher);
        binding.enableSwitch.setOnCheckedChangeListener((b, checked) -> notifyChanged());
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
        binding.hourField.setText(two(hour));
        binding.minField.setText(two(minute));
        for (int i = 0; i < DAY_COUNT; i++) {
            dayViews[i].setSelected((daysMask & (1 << i)) != 0);
        }
        binding.enableSwitch.setChecked(enabled);
        loading = false;
    }

    public int getHour() {
        return clamp(parse(binding.hourField.getText().toString()), 23);
    }

    public int getMinute() {
        return clamp(parse(binding.minField.getText().toString()), 59);
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
        loading = false;
    }

    private void notifyChanged() {
        if (!loading && listener != null) {
            listener.onChanged();
        }
    }

    private static int parse(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
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

    /** TextWatcher that just forwards afterTextChanged to a Runnable. */
    private static final class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleTextWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChange.run();
        }
    }
}
