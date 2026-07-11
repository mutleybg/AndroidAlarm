package com.example.androidalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/**
 * Fired by AlarmManager when an alarm is due. Re-arms repeating alarms (or turns
 * off one-shot ones) and starts {@link AlarmService} to do the ringing.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int index = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0);
        boolean rerun = intent.getIntExtra(AlarmScheduler.EXTRA_RERUN, 0) == 1;

        // Only the original trigger touches the schedule (re-arm next weekday, or
        // disable a one-shot). The automatic re-ring must leave scheduling alone.
        if (!rerun) {
            AlarmScheduler.onFired(context, index);
        }

        // Ring via a foreground service. It surfaces the ring screen through a
        // full-screen-intent notification, which works even when the app has
        // been closed and the device is locked — unlike a startActivity() from
        // this background broadcast, which modern Android blocks.
        Intent svc = new Intent(context, AlarmService.class);
        svc.setAction(AlarmService.ACTION_START);
        svc.putExtra(AlarmScheduler.EXTRA_INDEX, index);
        svc.putExtra(AlarmScheduler.EXTRA_RERUN, rerun ? 1 : 0);
        ContextCompat.startForegroundService(context, svc);
    }
}
