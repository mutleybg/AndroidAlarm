package com.example.androidalarm;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.example.androidalarm.databinding.ActivityRingBinding;

/**
 * Full-screen activity shown when the alarm fires. It plays the default alarm
 * ringtone and vibrates until the user dismisses it. Declared in the manifest
 * with showWhenLocked/turnScreenOn so it appears over the lock screen.
 */
public class AlarmRingActivity extends BaseActivity {

    private Ringtone ringtone;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityRingBinding binding = ActivityRingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.dismissButton.setOnClickListener(v -> finish());

        startRinging();
        startVibrating();
    }

    private void startRinging() {
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        ringtone = RingtoneManager.getRingtone(this, alarmUri);
        if (ringtone != null) {
            ringtone.play();
        }
    }

    private void startVibrating() {
        vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        long[] pattern = {0, 1000, 1000};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
    }

    private void stopAll() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        stopAll();
        super.onDestroy();
    }
}