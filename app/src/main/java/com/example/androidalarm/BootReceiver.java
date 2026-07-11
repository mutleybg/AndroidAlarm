package com.example.androidalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the saved alarm after the device reboots. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AlarmScheduler.rescheduleAllEnabled(context);
        }
    }
}