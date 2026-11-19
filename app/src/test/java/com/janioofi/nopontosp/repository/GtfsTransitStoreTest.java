package com.janioofi.nopontosp.repository;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GtfsTransitStoreTest {

    @Test
    public void periodsUseClockTimeBoundariesIncludingAfterMidnightTrips() {
        assertTrue(GtfsTransitStore.TimePeriod.DAWN.matches(0));
        assertTrue(GtfsTransitStore.TimePeriod.DAWN.matches(5 * 3600 + 59 * 60));
        assertFalse(GtfsTransitStore.TimePeriod.DAWN.matches(6 * 3600));
        assertTrue(GtfsTransitStore.TimePeriod.MORNING.matches(6 * 3600));
        assertFalse(GtfsTransitStore.TimePeriod.MORNING.matches(12 * 3600));
        assertTrue(GtfsTransitStore.TimePeriod.AFTERNOON.matches(12 * 3600));
        assertFalse(GtfsTransitStore.TimePeriod.AFTERNOON.matches(18 * 3600));
        assertTrue(GtfsTransitStore.TimePeriod.EVENING.matches(18 * 3600));
        assertTrue(GtfsTransitStore.TimePeriod.DAWN.matches(25 * 3600));
    }

    @Test
    public void calendarDayMapsToServiceDayFilter() {
        assertEquals(GtfsTransitStore.DayType.SUNDAY,
                GtfsTransitStore.dayTypeForCalendarDay(Calendar.SUNDAY));
        assertEquals(GtfsTransitStore.DayType.SATURDAY,
                GtfsTransitStore.dayTypeForCalendarDay(Calendar.SATURDAY));
        assertEquals(GtfsTransitStore.DayType.WEEKDAY,
                GtfsTransitStore.dayTypeForCalendarDay(Calendar.MONDAY));
    }

    @Test
    public void gtfsTimesKeepHoursBeyondMidnight() {
        assertEquals("25:05", GtfsTransitStore.formatTime(25 * 3600 + 5 * 60));
    }
}
