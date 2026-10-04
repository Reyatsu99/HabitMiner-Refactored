package com.habitminer.analytics

import java.time.ZoneId

enum class InsightKind { DARK, MOVING, CHARGING, LATE_NIGHT, PLACE }

/** A plain-language sentence about how context relates to phone use. */
data class ContextInsight(
    val kind: InsightKind,
    val headline: String,
    val detail: String,
    /** Used for ranking; larger is more noteworthy. */
    val weight: Double,
)

/** Screen time per place (only when Wi-Fi places are enabled). */
data class PlaceUsage(
    val place: String,
    val totalMs: Long,
    val topApp: String?,
    val days: Int,
) {
    val perDayMs: Long get() = if (days == 0) totalMs else totalMs / days
}

/**
 * Joins each app session with the nearest context snapshot and turns the result into
 * a handful of readable insights ("45% of your Evony time is in the dark").
 */
object ContextInsights {
    private const val MIN_APP_MS = 20 * TimeUtil.MINUTE
    private const val MIN_SHARE = 0.35f

    private class Acc {
        var total = 0L
        var knownLight = 0L
        var dark = 0L
        var knownMotion = 0L
        var moving = 0L
        var movingSessions = 0
        var knownCharging = 0L
        var charging = 0L
        var lateNight = 0L
    }

    fun compute(
        sessions: List<UsageSession>,
        samples: List<ContextSample>,
        zone: ZoneId,
        periodLabel: String = "this week",
    ): List<ContextInsight> {
        if (sessions.isEmpty()) return emptyList()
        val index = SampleIndex(samples)
        val perApp = mutableMapOf<String, Acc>()
        val overall = Acc()

        for (s in sessions) {
            val mid = s.start + (s.end - s.start) / 2
            val sensors = index.nearestWithSensors(mid)
            val any = index.nearest(mid)
            val accs = listOf(overall, perApp.getOrPut(s.appName) { Acc() })
            val hour = TimeUtil.hourOf(mid, zone)
            for (a in accs) {
                a.total += s.durationMs
                ContextLabels.light(sensors?.lightLux)?.let {
                    a.knownLight += s.durationMs
                    if (it == Light.DARK) a.dark += s.durationMs
                }
                sensors?.let { ContextLabels.motion(it) }?.let {
                    a.knownMotion += s.durationMs
                    if (it != Motion.STILL) {
                        a.moving += s.durationMs
                        a.movingSessions++
                    }
                }
                if (any != null) {
                    a.knownCharging += s.durationMs
                    if (any.isCharging) a.charging += s.durationMs
                }
                if (hour < 5) a.lateNight += s.durationMs
            }
        }

        val out = mutableListOf<ContextInsight>()

        // Dark use per app.
        perApp.filter { it.value.knownLight >= MIN_APP_MS }
            .map { it.key to it.value.dark.toFloat() / it.value.knownLight }
            .filter { it.second >= MIN_SHARE }
            .maxByOrNull { it.second }
            ?.let { (app, share) ->
                out.add(
                    ContextInsight(
                        InsightKind.DARK,
                        "${Format.percent(share)} of your $app time is in the dark",
                        "Based on the light sensor while you used $app $periodLabel.",
                        share * 2.0,
                    ),
                )
            }
        if (overall.dark >= 30 * TimeUtil.MINUTE) {
            out.add(
                ContextInsight(
                    InsightKind.DARK,
                    "${Format.duration(overall.dark)} of phone use in the dark",
                    "Total $periodLabel, measured with the light sensor.",
                    overall.dark.toDouble() / TimeUtil.HOUR,
                ),
            )
        }

        // Use while moving (walking or more).
        if (overall.movingSessions >= 3) {
            val topMovingApp =
                perApp.filter { it.value.movingSessions > 0 }.maxByOrNull { it.value.movingSessions }?.key
            out.add(
                ContextInsight(
                    InsightKind.MOVING,
                    "Used your phone while on the move ${overall.movingSessions} times",
                    if (topMovingApp != null) "Mostly $topMovingApp. Detected with the motion sensor." else "Detected with the motion sensor.",
                    overall.movingSessions / 5.0,
                ),
            )
        }

        // Charging.
        perApp.filter { it.value.knownCharging >= MIN_APP_MS }
            .map { it.key to it.value.charging.toFloat() / it.value.knownCharging }
            .filter { it.second >= 0.5f }
            .maxByOrNull { it.second }
            ?.let { (app, share) ->
                out.add(
                    ContextInsight(
                        InsightKind.CHARGING,
                        "${Format.percent(share)} of your $app time is while charging",
                        "Plugged-in sessions $periodLabel.",
                        share * 1.2,
                    ),
                )
            }

        // After midnight.
        if (overall.lateNight >= 30 * TimeUtil.MINUTE) {
            val topLate = perApp.maxByOrNull { it.value.lateNight }?.key
            out.add(
                ContextInsight(
                    InsightKind.LATE_NIGHT,
                    "${Format.duration(overall.lateNight)} of screen time after midnight",
                    if (topLate != null) "Mostly $topLate, between 00:00 and 05:00 $periodLabel." else "Between 00:00 and 05:00 $periodLabel.",
                    overall.lateNight.toDouble() / TimeUtil.HOUR,
                ),
            )
        }

        return out.sortedByDescending { it.weight }.take(4)
    }

    /** Screen time per Wi-Fi place, using the nearest snapshot's place within 20 minutes. */
    fun byPlace(
        sessions: List<UsageSession>,
        samples: List<ContextSample>,
        zone: ZoneId,
        placeName: (String) -> String,
    ): List<PlaceUsage> {
        val placed = samples.filter { it.place != null }
        if (placed.isEmpty()) return emptyList()
        val index = SampleIndex(placed)
        val totals = mutableMapOf<String, Long>()
        val apps = mutableMapOf<String, MutableMap<String, Long>>()
        val days = mutableMapOf<String, MutableSet<java.time.LocalDate>>()
        for (s in sessions) {
            val sample = index.nearest(s.start + (s.end - s.start) / 2, 20 * TimeUtil.MINUTE) ?: continue
            val place = sample.place ?: continue
            totals[place] = (totals[place] ?: 0L) + s.durationMs
            val m = apps.getOrPut(place) { mutableMapOf() }
            m[s.appName] = (m[s.appName] ?: 0L) + s.durationMs
            days.getOrPut(place) { mutableSetOf() }.add(TimeUtil.dateOf(s.start, zone))
        }
        return totals.map { (place, total) ->
            PlaceUsage(
                place = placeName(place),
                totalMs = total,
                topApp = apps[place]?.maxByOrNull { it.value }?.key,
                days = days[place]?.size ?: 0,
            )
        }.sortedByDescending { it.totalMs }
    }
}
