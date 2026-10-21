package cn.qingheng.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MealTimingTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    @Test fun defaultsMatchThreeMealsAndSnacksNeverRemind() {
        assertEquals(listOf(540, 780, 1140), Meal.regular.map { it.defaultMinutes })
        assertFalse(Meal.SNACK.remindable)
        assertFalse(Meal.UNSPECIFIED.remindable)
        assertEquals(Meal.UNSPECIFIED, Meal.from("old-value"))
    }
    @Test fun schedulesTodayBeforeTimeAndTomorrowAtOrAfterTime() {
        assertEquals(Instant.parse("2026-10-20T01:00:00Z"), MealTiming.next(Instant.parse("2026-10-20T00:59:59Z"), shanghai, 540))
        assertEquals(Instant.parse("2026-10-21T01:00:00Z"), MealTiming.next(Instant.parse("2026-10-20T01:00:00Z"), shanghai, 540))
        assertEquals(Instant.parse("2026-10-21T01:00:00Z"), MealTiming.next(Instant.parse("2026-10-20T02:00:00Z"), shanghai, 540))
    }
    @Test fun followsLocalDateAcrossYearBoundary() {
        assertEquals(Instant.parse("2026-12-31T16:00:00Z"), MealTiming.next(Instant.parse("2026-12-31T15:59:00Z"), shanghai, 0))
    }
    @Test fun respectsDaylightSavingInsteadOfAdding24Hours() {
        val newYork = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T13:00:00Z"), MealTiming.next(Instant.parse("2026-03-07T15:00:00Z"), newYork, 540))
    }
    @Test fun snoozeIsExactlyThirtyMinutesEvenAcrossMidnight() {
        assertEquals(Instant.parse("2026-10-20T16:15:00Z"), MealTiming.snooze(Instant.parse("2026-10-20T15:45:00Z")))
    }
    @Test fun recordedFoodTakesPriorityOverStaleSkippedFlag() {
        assertEquals("已记录", MealTiming.status(true, true))
        assertEquals("这一餐没吃", MealTiming.status(false, true))
        assertEquals("待记录", MealTiming.status(false, false))
    }
}
