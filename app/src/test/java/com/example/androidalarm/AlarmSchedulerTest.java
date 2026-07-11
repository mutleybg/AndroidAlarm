package com.example.androidalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Deterministic tests for {@link AlarmScheduler#nextTrigger(int, int, int, Calendar)},
 * the day-of-week arithmetic. A fixed UTC clock is passed in so results don't
 * depend on the real time or the machine's time zone.
 *
 * <p>Reference dates: 2026-07-11 is a Saturday, 2026-07-13 a Monday.
 * Weekday mask is Monday-first: bit 0 = Monday .. bit 6 = Sunday.
 */
public class AlarmSchedulerTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    // Monday-first day bits.
    private static final int MON = 1 << 0;
    private static final int TUE = 1 << 1;
    private static final int WED = 1 << 2;
    private static final int SAT = 1 << 5;

    /** Builds a UTC Calendar for the given wall-clock values. */
    private static Calendar at(int year, int month, int day, int hour, int minute) {
        Calendar c = new GregorianCalendar(UTC);
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c;
    }

    private static long expected(int year, int month, int day, int hour, int minute) {
        return at(year, month, day, hour, minute).getTimeInMillis();
    }

    // --- No days selected: one-shot at the next occurrence ------------------

    @Test
    public void noDays_timeLaterToday_firesToday() {
        Calendar now = at(2026, 7, 11, 6, 0);
        assertEquals(expected(2026, 7, 11, 7, 0),
                AlarmScheduler.nextTrigger(7, 0, 0, now));
    }

    @Test
    public void noDays_timeAlreadyPassed_firesTomorrow() {
        Calendar now = at(2026, 7, 11, 8, 0);
        assertEquals(expected(2026, 7, 12, 7, 0),
                AlarmScheduler.nextTrigger(7, 0, 0, now));
    }

    @Test
    public void noDays_exactlyNow_firesTomorrow() {
        // target == now counts as "passed", so it rolls to the next day.
        Calendar now = at(2026, 7, 11, 7, 0);
        assertEquals(expected(2026, 7, 12, 7, 0),
                AlarmScheduler.nextTrigger(7, 0, 0, now));
    }

    // --- Days selected: repeating on those weekdays -------------------------

    @Test
    public void withToday_timeLaterToday_firesToday() {
        // Saturday 06:00, alarm 07:00 on Saturdays -> today.
        Calendar now = at(2026, 7, 11, 6, 0);
        assertEquals(expected(2026, 7, 11, 7, 0),
                AlarmScheduler.nextTrigger(7, 0, SAT, now));
    }

    @Test
    public void withToday_timePassed_firesNextWeek() {
        // Saturday 08:00, alarm 07:00 on Saturdays only -> next Saturday.
        Calendar now = at(2026, 7, 11, 8, 0);
        assertEquals(expected(2026, 7, 18, 7, 0),
                AlarmScheduler.nextTrigger(7, 0, SAT, now));
    }

    @Test
    public void weekdays_fromSaturday_firesNextMonday() {
        // Mon/Tue/Wed selected, now Saturday -> next Monday (matches app demo).
        Calendar now = at(2026, 7, 11, 9, 0);
        assertEquals(expected(2026, 7, 13, 7, 0),
                AlarmScheduler.nextTrigger(7, 0, MON | TUE | WED, now));
    }

    @Test
    public void picksNearestSelectedDay() {
        // Tue+Sat selected, now Saturday after the time -> nearest is Tuesday.
        Calendar now = at(2026, 7, 11, 9, 0);
        assertEquals(expected(2026, 7, 14, 7, 0),
                AlarmScheduler.nextTrigger(7, 0, TUE | SAT, now));
    }

    // --- Property: result is always in the future on a selected weekday -----

    @Test
    public void resultIsAlwaysFutureAndOnSelectedDay() {
        Calendar now = at(2026, 7, 11, 9, 0);
        int mask = MON | WED;
        long trigger = AlarmScheduler.nextTrigger(7, 0, mask, now);

        assertTrue("trigger must be in the future", trigger > now.getTimeInMillis());

        Calendar result = new GregorianCalendar(UTC);
        result.setTimeInMillis(trigger);
        int mondayIndex = (result.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        assertTrue("trigger weekday must be selected",
                (mask & (1 << mondayIndex)) != 0);
        assertEquals(7, result.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, result.get(Calendar.MINUTE));
    }
}
