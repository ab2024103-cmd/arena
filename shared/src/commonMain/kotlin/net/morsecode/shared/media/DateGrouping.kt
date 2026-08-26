package net.morsecode.shared.media

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Month

/** Groups items with an epoch-millis timestamp into day-level buckets (E.3). */
object DateGrouping {

    fun dayBucket(epochMs: Long, tz: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date

    /** "August 26, 2026" style section header. */
    fun headerFor(date: LocalDate): String {
        val month = monthName(date.month)
        return "$month ${date.dayOfMonth}, ${date.year}"
    }

    fun <T> groupByDay(
        items: List<T>,
        epochMs: (T) -> Long,
        tz: TimeZone = TimeZone.currentSystemDefault(),
    ): List<Pair<String, List<T>>> {
        val buckets = LinkedHashMap<LocalDate, MutableList<T>>()
        for (item in items) {
            buckets.getOrPut(dayBucket(epochMs(item), tz)) { mutableListOf() }.add(item)
        }
        return buckets.entries
            .sortedByDescending { it.key }
            .map { (date, list) -> headerFor(date) to list }
    }

    fun monthName(month: Month): String = when (month) {
        Month.JANUARY -> "January"; Month.FEBRUARY -> "February"; Month.MARCH -> "March"
        Month.APRIL -> "April"; Month.MAY -> "May"; Month.JUNE -> "June"
        Month.JULY -> "July"; Month.AUGUST -> "August"; Month.SEPTEMBER -> "September"
        Month.OCTOBER -> "October"; Month.NOVEMBER -> "November"; Month.DECEMBER -> "December"
    }
}
