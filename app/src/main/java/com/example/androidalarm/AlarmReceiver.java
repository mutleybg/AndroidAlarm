package com.example.androidalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fired by AlarmManager when the alarm is due. Launches the full-screen ring
 * activity. Because the alarm is one-shot, we do not re-schedule it here — the
 * user must set it again (kept intentionally simple).
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // The alarm has fired, so it is no longer pending. Mark it disabled.
        AlarmScheduler.cancel(context);

        Intent ring = new Intent(context, AlarmRingActivity.class);
        ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(ring);
    }
}