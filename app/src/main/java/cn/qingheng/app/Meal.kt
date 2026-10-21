package cn.qingheng.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class Meal(val key: String, val label: String, val defaultMinutes: Int?) {
    BREAKFAST("breakfast", "早餐", 9 * 60),
    LUNCH("lunch", "午餐", 13 * 60),
    DINNER("dinner", "晚餐", 19 * 60),
    SNACK("snack", "零食", null),
    UNSPECIFIED("unspecified", "未分类", null);
    val remindable get() = defaultMinutes != null
    companion object {
        val regular get() = entries.filter { it.remindable }
        fun from(key: String?) = entries.find { it.key == key } ?: UNSPECIFIED
    }
}

object MealTiming {
    /** Wall-clock daily alarms follow the device timezone, including DST changes. */
    fun next(now: Instant, zone: ZoneId, minutes: Int): Instant {
        require(minutes in 0..1439)
        val today = now.atZone(zone).toLocalDate()
        fun at(date: LocalDate) = date.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant()
        val candidate = at(today)
        return if (candidate > now) candidate else at(today.plusDays(1))
    }
    fun snooze(now: Instant): Instant = now.plusSeconds(30 * 60)
    fun status(hasFood: Boolean, skipped: Boolean) = when { hasFood -> "已记录"; skipped -> "这一餐没吃"; else -> "待记录" }
}

data class SkippedMeal(val date: String, val meal: String)
