package com.budanga.parcheslol.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Riot publishes the notes on Tuesday or Wednesday between 18 and 20 UTC; the window gives a few hours more. */
private val WINDOW_DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
private const val WINDOW_START_HOUR = 18
private val WINDOW_STEP: Duration = Duration.ofMinutes(30)

/** Wait before the next patch-day check: 30 minutes inside the window, else until the next window opens. */
fun patchWindowDelay(now: Instant): Duration {
    val t = now.atZone(ZoneOffset.UTC)
    if (t.dayOfWeek in WINDOW_DAYS && t.hour >= WINDOW_START_HOUR) return WINDOW_STEP
    var start = t.withHour(WINDOW_START_HOUR).withMinute(0).withSecond(0).withNano(0)
    if (!start.isAfter(t)) start = start.plusDays(1)
    while (start.dayOfWeek !in WINDOW_DAYS) start = start.plusDays(1)
    return Duration.between(t, start)
}
