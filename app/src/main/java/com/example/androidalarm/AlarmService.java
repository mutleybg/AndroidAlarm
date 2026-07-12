package com.example.androidalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Foreground service that actually rings the alarm. It is started by
 * {@link AlarmReceiver} when an alarm is due, so the alarm sounds even if the
 * app has been swiped away / closed.
 *
 * <p>It shows a high-priority notification carrying a <em>full-screen intent</em>
 * to {@link AlarmRingActivity}; the system launches that over the lock screen —
 * the sanctioned way to surface an alarm UI from the background, where a plain
 * {@code startActivity()} is blocked on modern Android. The service — not the
 * activity — owns the ring sound, vibration, the one-minute auto-timeout and the
 * single automatic re-ring, so the alarm behaves correctly whether or not the
 * ring screen is actually on top.
 */
public class AlarmService extends Service {

    static final String ACTION_START = "com.example.androidalarm.action.START_RING";
    static final String ACTION_DISMISS = "com.example.androidalarm.action.DISMISS_RING";

    private static final String CHANNEL_ID = "alarm_ring";
    private static final int NOTIFICATION_ID = 3000;

    private Ringtone ringtone;
    private Vibrator vibrator;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable ringTimeout = this::onRingTimeout;

    private int index;
    /** True when this is the automatic re-ring (so it must not schedule another). */
    private boolean rerun;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_DISMISS.equals(action)) {
            int idx = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, index);
            // User stopped it: no automatic re-ring.
            AlarmScheduler.cancelRerun(this, idx);
            stopEverything();
            return START_NOT_STICKY;
        }

        index = intent != null ? intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0) : 0;
        rerun = intent != null && intent.getIntExtra(AlarmScheduler.EXTRA_RERUN, 0) == 1;

        startInForeground();
        startRinging();
        startVibrating();
        // Give up ringing after a minute if the user never dismisses it.
        timeoutHandler.removeCallbacks(ringTimeout);
        timeoutHandler.postDelayed(ringTimeout, AlarmScheduler.RING_TIMEOUT_MS);
        return START_NOT_STICKY;
    }

    /** Reached when the ring was not dismissed within the ring window. */
    private void onRingTimeout() {
        // The first ring gets one more attempt a minute later; the re-ring does
        // not, so the alarm then stays quiet until its next scheduled trigger.
        if (!rerun) {
            AlarmScheduler.scheduleRerun(this, index);
        }
        stopEverything();
    }

    private void stopEverything() {
        timeoutHandler.removeCallbacks(ringTimeout);
        stopRinging();
        stopVibrating();
        // Close the ring screen too, if it happens to be showing.
        sendBroadcast(new Intent(AlarmRingActivity.ACTION_FINISH).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(getString(R.string.alarm_channel_desc));
        // The service plays the alarm sound itself; the channel stays silent.
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);

        Intent ring = new Intent(this, AlarmRingActivity.class);
        ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ring.putExtra(AlarmScheduler.EXTRA_INDEX, index);
        PendingIntent fullScreen = PendingIntent.getActivity(
                this, index, ring,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.alarm_ringing))
                .setContentText(getString(R.string.app_name))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void startRinging() {
        // The alarm sound is bundled with the app (res/raw/alarm_ringtone.mp3).
        Uri alarmUri = Uri.parse(
                "android.resource://" + getPackageName() + "/" + R.raw.alarm_ringtone);
        ringtone = RingtoneManager.getRingtone(this, alarmUri);
        if (ringtone != null) {
            // Play on the alarm stream so it is audible even at low media volume.
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            // Loop so a short ringtone keeps sounding for the whole ring window.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.setLooping(true);
            }
            ringtone.play();
        }
    }

    private void stopRinging() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        ringtone = null;
    }

    private void startVibrating() {
        vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        long[] pattern = {0, 1000, 1000};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
    }

    private void stopVibrating() {
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @Override
    public void onDestroy() {
        timeoutHandler.removeCallbacks(ringTimeout);
        stopRinging();
        stopVibrating();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
