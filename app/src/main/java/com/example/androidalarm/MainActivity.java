package com.example.androidalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The main screen: a single alarm panel filling the screen. Every edit is saved
 * and (re)scheduled immediately. Also handles the two permission gates modern
 * Android imposes on alarm apps: notifications (Android 13+) and exact-alarm
 * scheduling (Android 12+).
 */
public class MainActivity extends BaseActivity {

    /** The one alarm this app manages. */
    private static final int ALARM_INDEX = 0;

    /** Prefs for one-off UI state (kept separate from the alarm data). */
    private static final String UI_PREFS = "ui_prefs";
    private static final String KEY_RELIABILITY_HINT_SHOWN = "reliability_hint_shown";

    private ActivityMainBinding binding;
    private AlarmPanelView panel;

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

        panel = binding.alarmPanel;
        panel.setTitle(getString(R.string.alarm_title));
        loadPanelState();
        panel.setOnChangeListener(this::onPanelChanged);

        maybeRequestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-sync from storage: if the alarm fired (and turned itself off) while
        // we were away, reflect that in the switch and countdown label.
        loadPanelState();
    }

    /** Loads the saved alarm settings into the panel (no change callbacks). */
    private void loadPanelState() {
        panel.setState(
                AlarmScheduler.getHour(this, ALARM_INDEX),
                AlarmScheduler.getMinute(this, ALARM_INDEX),
                AlarmScheduler.isEnabled(this, ALARM_INDEX));
    }

    /** The panel changed: enforce the exact-alarm gate, then save and schedule. */
    private void onPanelChanged() {
        boolean enabled = panel.isAlarmEnabled();

        if (enabled && !canScheduleExactAlarms()) {
            panel.setSwitchChecked(false);
            requestExactAlarmPermission();
            return;
        }

        // No weekday repeat in this variant: a plain daysMask of 0 means the
        // alarm fires once and then disables itself (see AlarmScheduler.onFired).
        AlarmScheduler.save(this, ALARM_INDEX,
                panel.getHour(), panel.getMinute(), 0, enabled);
        AlarmScheduler.apply(this, ALARM_INDEX);

        if (enabled) {
            maybeShowReliabilityHint();
        }
    }

    /**
     * The first time an alarm is turned on, explain that some phones (Xiaomi,
     * realme, ...) kill background apps and need Autostart / battery exemptions
     * for alarms to fire reliably. Shown once; offers to open the app settings.
     */
    private void maybeShowReliabilityHint() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_RELIABILITY_HINT_SHOWN, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_RELIABILITY_HINT_SHOWN, true).apply();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hint_title)
                .setMessage(R.string.hint_message)
                .setPositiveButton(R.string.hint_open_settings, (dialog, which) -> openAppSettings())
                .setNegativeButton(R.string.hint_dismiss, null)
                .show();
    }

    /** Opens this app's system settings screen (Autostart / battery / permissions). */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            // No settings activity to handle it; nothing else we can do.
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
}
