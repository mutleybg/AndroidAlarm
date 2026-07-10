package com.example.androidalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Central place for scheduling, cancelling and persisting the single alarm this
 * app supports. Persistence lets {@link BootReceiver} re-arm the alarm after a
 * reboot (AlarmManager forgets everything across reboots).
 */
public final class AlarmScheduler {

    private static final String PREFS = "alarm_prefs";
    private static final String KEY_HOUR = "hour";
    private static final String KEY_MINUTE = "minute";
    private static final String KEY_ENABLED = "enabled";

    /** Fixed request code — we only ever have one alarm, so one PendingIntent. */
    static final int REQUEST_CODE = 1001;

    private AlarmScheduler() {
    }

    /**
     * Schedules an exact alarm for the next occurrence of {@code hour:minute}.
     * If that time has already passed today, it is scheduled for tomorrow.
     */
    public static void schedule(Context context, int hour, int minute) {
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = alarmPendingIntent(context);
        // setAlarmClock survives Doze and surfaces the alarm in the status bar.
        AlarmManager.AlarmClockInfo info =
                new AlarmManager.AlarmClockInfo(target.getTimeInMillis(), showIntent(context));
        alarmManager.setAlarmClock(info, pendingIntent);

        persist(context, hour, minute, true);
    }

    /** Cancels the currently scheduled alarm, if any. */
    public static void cancel(Context context) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        alarmManager.cancel(alarmPendingIntent(context));
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();
    }

    /** Re-arms the persisted alarm; used after a reboot. No-op if disabled. */
    public static void rescheduleIfEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ENABLED, false)) {
            schedule(context, prefs.getInt(KEY_HOUR, 8), prefs.getInt(KEY_MINUTE, 0));
        }
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static int getHour(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HOUR, 8);
    }

    public static int getMinute(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MINUTE, 0);
    }

    private static void persist(Context context, int hour, int minute, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    private static PendingIntent alarmPendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** PendingIntent used by the system "alarm" icon to open the app. */
    private static PendingIntent showIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}