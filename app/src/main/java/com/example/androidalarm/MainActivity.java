package com.example.androidalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.androidalarm.databinding.ActivityMainBinding;

import java.util.Locale;

/**
 * Single screen: pick a time, then enable or cancel the alarm. Also handles the
 * two permission gates modern Android imposes on alarm apps: notifications
 * (Android 13+) and exact-alarm scheduling (Android 12+).
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.notifications_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.timePicker.setIs24HourView(true);
        binding.timePicker.setHour(AlarmScheduler.getHour(this));
        binding.timePicker.setMinute(AlarmScheduler.getMinute(this));

        binding.setButton.setOnClickListener(v -> onSetClicked());
        binding.cancelButton.setOnClickListener(v -> {
            AlarmScheduler.cancel(this);
            refreshStatus();
        });

        maybeRequestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void onSetClicked() {
        if (!canScheduleExactAlarms()) {
            requestExactAlarmPermission();
            return;
        }
        int hour = binding.timePicker.getHour();
        int minute = binding.timePicker.getMinute();
        AlarmScheduler.schedule(this, hour, minute);
        Toast.makeText(this,
                getString(R.string.alarm_set_for, formatTime(hour, minute)),
                Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void refreshStatus() {
        if (AlarmScheduler.isEnabled(this)) {
            binding.statusText.setText(getString(R.string.status_on,
                    formatTime(AlarmScheduler.getHour(this), AlarmScheduler.getMinute(this))));
        } else {
            binding.statusText.setText(R.string.status_off);
        }
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        return alarmManager.canScheduleExactAlarms();
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toast.makeText(this, R.string.grant_exact_alarm, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @NonNull
    private String formatTime(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }
}