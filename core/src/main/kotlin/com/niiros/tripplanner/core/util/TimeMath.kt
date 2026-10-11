package com.niiros.tripplanner.core.util

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object TimeMath {
    /** Rounds to the nearest multiple of [minutes] (ties go up). */
    fun roundToMinutes(instant: Instant, minutes: Long): Instant {
        val step = minutes * 60
        val epoch = instant.epochSecond
        val rounded = Math.floorDiv(epoch + step / 2, step) * step
        return Instant.ofEpochSecond(rounded)
    }

    fun floorToMinutes(instant: Instant, minutes: Long): Instant {
        val step = minutes * 60
        return Instant.ofEpochSecond(Math.floorDiv(instant.epochSecond, step) * step)
    }

    /** Rounds a wall-clock time up to the next multiple of [minutes] ("now, rounded to 5 min"). */
    fun ceilLocalTime(time: LocalTime, minutes: Int): LocalTime {
        val total = time.toSecondOfDay()
        val step = minutes * 60
        val ceil = ((total + step - 1) / step) * step
        return LocalTime.ofSecondOfDay((ceil % 86_400).toLong()).truncatedTo(ChronoUnit.MINUTES)
    }

    /** Ceil of a duration in whole minutes, then up to a multiple of [step]. */
    fun ceilMinutes(duration: Duration, step: Long = 1): Long {
        val secs = duration.seconds.coerceAtLeast(0)
        val minutes = (secs + 59) / 60
        return ((minutes + step - 1) / step) * step
    }

    fun overlaps(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Boolean =
        !aStart.isAfter(bEnd) && !aEnd.isBefore(bStart)
}
