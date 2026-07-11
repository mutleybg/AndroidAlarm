package com.example.androidalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fired by AlarmManager when an alarm is due. Re-arms repeating alarms (or turns
 * off one-shot ones) and launches the full-screen ring activity.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int index = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0);

        // Re-arm for the next weekday, or disable if this was a one-shot.
        AlarmScheduler.onFired(context, index);

        Intent ring = new Intent(context, AlarmRingActivity.class);
        ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(ring);
    }
}
