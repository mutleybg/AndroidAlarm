package com.example.androidalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Central place for scheduling, cancelling and persisting the app's alarms.
 * Persistence lets {@link BootReceiver} re-arm alarms after a reboot
 * (AlarmManager forgets everything across reboots).
 *
 * <p>The app has {@link #ALARM_COUNT} independent alarms. Each has an hour,
 * minute, a set of weekdays (a 7-bit mask, bit 0 = Monday .. bit 6 = Sunday)
 * and an enabled flag. An alarm with no days selected fires once at the next
 * hour:minute; an alarm with days repeats weekly on those days.
 */
public final class AlarmScheduler {

    /** Number of independent alarms the UI exposes. */
    public static final int ALARM_COUNT = 2;

    private static final String PREFS = "alarm_prefs";
    private static final String KEY_HOUR = "hour";
    private static final String KEY_MINUTE = "minute";
    private static final String KEY_DAYS = "days";
    private static final String KEY_ENABLED = "enabled";

    /** Passed to {@link AlarmReceiver} so it knows which alarm fired. */
    static final String EXTRA_INDEX = "alarm_index";

    /** Distinct request code (hence distinct PendingIntent) per alarm. */
    private static final int REQUEST_CODE_BASE = 1000;

    private AlarmScheduler() {
    }

    // --- Persistence -------------------------------------------------------

    /** Stores an alarm's settings without touching the OS schedule. */
    public static void save(Context context, int index, int hour, int minute,
                            int daysMask, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(key(index, KEY_HOUR), hour)
                .putInt(key(index, KEY_MINUTE), minute)
                .putInt(key(index, KEY_DAYS), daysMask)
                .putBoolean(key(index, KEY_ENABLED), enabled)
                .apply();
    }

    public static int getHour(Context context, int index) {
        return prefs(context).getInt(key(index, KEY_HOUR), index == 0 ? 7 : 8);
    }

    public static int getMinute(Context context, int index) {
        return prefs(context).getInt(key(index, KEY_MINUTE), 0);
    }

    public static int getDaysMask(Context context, int index) {
        return prefs(context).getInt(key(index, KEY_DAYS), 0);
    }

    public static boolean isEnabled(Context context, int index) {
        return prefs(context).getBoolean(key(index, KEY_ENABLED), false);
    }

    // --- Scheduling --------------------------------------------------------

    /**
     * Brings the OS schedule in line with the alarm's saved state: arms it if
     * enabled, cancels it otherwise. Call after any change to the alarm.
     */
    public static void apply(Context context, int index) {
        if (isEnabled(context, index)) {
            schedule(context, index);
        } else {
            cancel(context, index);
        }
    }

    /** Arms an exact alarm for this alarm's next trigger time. */
    private static void schedule(Context context, int index) {
        long triggerAt = nextTrigger(
                getHour(context, index), getMinute(context, index), getDaysMask(context, index));

        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo info =
                new AlarmManager.AlarmClockInfo(triggerAt, showIntent(context, index));
        // setAlarmClock survives Doze and surfaces the alarm in the status bar.
        alarmManager.setAlarmClock(info, alarmPendingIntent(context, index));
    }

    /** Cancels the OS alarm for this index (leaves saved settings alone). */
    public static void cancel(Context context, int index) {
        context.getSystemService(AlarmManager.class)
                .cancel(alarmPendingIntent(context, index));
    }

    /**
     * Called after an alarm fires. A repeating alarm (has days) is re-armed for
     * its next weekday; a one-shot alarm (no days) is turned off.
     */
    public static void onFired(Context context, int index) {
        if (getDaysMask(context, index) == 0) {
            save(context, index, getHour(context, index), getMinute(context, index), 0, false);
        } else {
            schedule(context, index);
        }
    }

    /** Re-arms every enabled alarm; used after a reboot. */
    public static void rescheduleAllEnabled(Context context) {
        for (int i = 0; i < ALARM_COUNT; i++) {
            if (isEnabled(context, i)) {
                schedule(context, i);
            }
        }
    }

    /**
     * Next wall-clock time (ms) for {@code hour:minute}. With no days, that is
     * today if still ahead, else tomorrow. With days, the nearest future day
     * whose weekday bit is set (bit 0 = Monday .. bit 6 = Sunday).
     */
    static long nextTrigger(int hour, int minute, int daysMask) {
        return nextTrigger(hour, minute, daysMask, Calendar.getInstance());
    }

    /**
     * Same as {@link #nextTrigger(int, int, int)} but with an explicit "now",
     * so the day-of-week arithmetic can be unit-tested deterministically. Does
     * not mutate {@code now}.
     */
    static long nextTrigger(int hour, int minute, int daysMask, Calendar now) {
        long nowMillis = now.getTimeInMillis();
        Calendar target = (Calendar) now.clone();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (daysMask == 0) {
            if (target.getTimeInMillis() <= nowMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }
            return target.getTimeInMillis();
        }

        // Look up to 7 days ahead for the first selected weekday in the future.
        for (int offset = 0; offset <= 7; offset++) {
            if (offset > 0) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }
            int bit = mondayIndex(target.get(Calendar.DAY_OF_WEEK));
            boolean daySelected = (daysMask & (1 << bit)) != 0;
            if (daySelected && target.getTimeInMillis() > nowMillis) {
                return target.getTimeInMillis();
            }
        }
        // Unreachable when daysMask != 0, but return something sane.
        return target.getTimeInMillis();
    }

    /** Maps Calendar.DAY_OF_WEEK (SUN=1..SAT=7) to Monday-first index 0..6. */
    private static int mondayIndex(int calendarDayOfWeek) {
        return (calendarDayOfWeek + 5) % 7;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int index, String base) {
        return "a" + index + "_" + base;
    }

    private static PendingIntent alarmPendingIntent(Context context, int index) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_INDEX, index);
        return PendingIntent.getBroadcast(
                context, REQUEST_CODE_BASE + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** PendingIntent used by the system "alarm" icon to open the app. */
    private static PendingIntent showIntent(Context context, int index) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(
                context, REQUEST_CODE_BASE + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
