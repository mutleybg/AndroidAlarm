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
import androidx.core.content.ContextCompat;

import com.example.androidalarm.databinding.ActivityMainBinding;

/**
 * The main screen: two alarm panels, each taking half the screen. Every edit is
 * saved and (re)scheduled immediately. Also handles the two permission gates
 * modern Android imposes on alarm apps: notifications (Android 13+) and
 * exact-alarm scheduling (Android 12+).
 */
public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private AlarmPanelView[] panels;

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

        panels = new AlarmPanelView[]{binding.alarmPanel1, binding.alarmPanel2};
        for (int i = 0; i < panels.length; i++) {
            final int index = i;
            AlarmPanelView panel = panels[i];
            panel.setTitle(getString(R.string.alarm_title, i + 1));
            panel.setState(
                    AlarmScheduler.getHour(this, i),
                    AlarmScheduler.getMinute(this, i),
                    AlarmScheduler.getDaysMask(this, i),
                    AlarmScheduler.isEnabled(this, i));
            panel.setOnChangeListener(() -> onPanelChanged(index));
        }

        maybeRequestNotificationPermission();
    }

    /** A panel changed: enforce the exact-alarm gate, then save and schedule. */
    private void onPanelChanged(int index) {
        AlarmPanelView panel = panels[index];
        boolean enabled = panel.isAlarmEnabled();

        if (enabled && !canScheduleExactAlarms()) {
            panel.setSwitchChecked(false);
            requestExactAlarmPermission();
            return;
        }

        AlarmScheduler.save(this, index,
                panel.getHour(), panel.getMinute(), panel.getDaysMask(), enabled);
        AlarmScheduler.apply(this, index);
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
}
