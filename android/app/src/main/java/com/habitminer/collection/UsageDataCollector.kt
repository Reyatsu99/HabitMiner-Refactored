package com.habitminer.collection

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.habitminer.data.AppUsageEntity
import com.habitminer.domain.AppIdentityResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageDataCollector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appIdentityResolver: AppIdentityResolver,
    ) {
        private val appCategoryMap: Map<String, String> =
            mapOf(
                "com.instagram.android" to "SOCIAL",
                "com.facebook.katana" to "SOCIAL",
                "com.twitter.android" to "SOCIAL",
                "com.whatsapp" to "COMMUNICATION",
                "org.telegram.messenger" to "COMMUNICATION",
                "com.snapchat.android" to "SOCIAL",
                "com.google.android.youtube" to "ENTERTAINMENT",
                "com.netflix.mediaclient" to "ENTERTAINMENT",
                "com.spotify.music" to "ENTERTAINMENT",
                "com.google.android.apps.maps" to "NAVIGATION",
                "com.google.android.gm" to "PRODUCTIVITY",
                "com.microsoft.launcher" to "PRODUCTIVITY",
                "com.microsoft.teams" to "PRODUCTIVITY",
                "com.slack" to "PRODUCTIVITY",
                "com.notion.id" to "PRODUCTIVITY",
                "com.google.android.apps.docs" to "PRODUCTIVITY",
                "com.google.android.apps.sheets" to "PRODUCTIVITY",
                "com.google.android.calendar" to "PRODUCTIVITY",
                "com.amazon.mShop.android.shopping" to "SHOPPING",
                "com.flipkart.android" to "SHOPPING",
                "com.gamestar.perfectpiano" to "GAMING",
                "com.supercell.clashofclans" to "GAMING",
                "com.mojang.minecraftpe" to "GAMING",
                "com.pubg.imobile" to "GAMING",
                "com.reddit.frontpage" to "SOCIAL",
                "com.linkedin.android" to "PRODUCTIVITY",
                "com.google.android.keep" to "PRODUCTIVITY",
                "com.microsoft.office.word" to "PRODUCTIVITY",
                "com.microsoft.office.excel" to "PRODUCTIVITY",
                "jp.naver.line.android" to "COMMUNICATION",
                "com.viber.voip" to "COMMUNICATION",
                "com.skype.raider" to "COMMUNICATION",
                "com.zhiliaoapp.musically" to "ENTERTAINMENT",
                "com.google.android.apps.tachyon" to "COMMUNICATION",
                "com.duolingo" to "EDUCATION",
                "org.khanacademy.android" to "EDUCATION",
                "com.coursera.app" to "EDUCATION",
                "com.amazon.kindle" to "EDUCATION",
                "com.google.android.apps.podcasts" to "ENTERTAINMENT",
                "com.amazon.music" to "ENTERTAINMENT",
                "com.gaana" to "ENTERTAINMENT",
                "com.jio.media.jiocinema" to "ENTERTAINMENT",
                "com.hotstar" to "ENTERTAINMENT",
                "com.google.android.apps.fitness" to "HEALTH",
                "com.nike.plusgps" to "HEALTH",
                "com.strava" to "HEALTH",
                "com.ola.client" to "NAVIGATION",
                "com.ubercab" to "NAVIGATION",
                "com.google.android.dialer" to "COMMUNICATION",
                "com.android.contacts" to "COMMUNICATION",
            )

        fun getTimeSlot(hourOfDay: Int): String {
            return when (hourOfDay) {
                in 6..11 -> "MORNING"
                in 12..16 -> "AFTERNOON"
                in 17..21 -> "EVENING"
                else -> "NIGHT"
            }
        }

        /**
         * Counts today's unlocks from the system event log (KEYGUARD_HIDDEN, API 28+).
         * Unlike our USER_PRESENT receiver this also sees unlocks that happened while
         * HabitMiner was not running. Returns null when the platform can't provide it.
         */
        fun countUnlocksSince(sinceMs: Long): Int? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usageStatsManager.queryEvents(sinceMs, System.currentTimeMillis()) ?: return null
            val event = UsageEvents.Event()
            var count = 0
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) count++
            }
            return count
        }

        fun getDayType(dayOfWeek: Int): String {
            return if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                "WEEKEND"
            } else {
                "WEEKDAY"
            }
        }

        private fun getCategoryForPackage(packageName: String): String {
            return try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                when (info.category) {
                    ApplicationInfo.CATEGORY_GAME -> "GAMING"
                    ApplicationInfo.CATEGORY_AUDIO,
                    ApplicationInfo.CATEGORY_VIDEO,
                    -> "ENTERTAINMENT"
                    ApplicationInfo.CATEGORY_SOCIAL -> "SOCIAL"
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "PRODUCTIVITY"
                    ApplicationInfo.CATEGORY_UNDEFINED -> appCategoryMap[packageName] ?: "OTHER"
                    else -> appCategoryMap[packageName] ?: "OTHER"
                }
            } catch (e: PackageManager.NameNotFoundException) {
                appCategoryMap[packageName] ?: "OTHER"
            }
        }

        suspend fun collectUsageSince(
            sinceMs: Long,
            prevStoredPackage: String? = null,
            isHistorical: Boolean = false,
        ): List<AppUsageEntity> {
            val endMs = System.currentTimeMillis()
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            // Re-read a short overlap so a session already in progress at the previous
            // worker boundary has its RESUMED event available. Stable IDs below make
            // overlapping reads idempotent in Room.
            val queryStart = (sinceMs - 24 * 60 * 60 * 1000L).coerceAtLeast(0L)
            val events = usageStatsManager.queryEvents(queryStart, endMs)

            val result = mutableListOf<AppUsageEntity>()
            val startTimes = mutableMapOf<String, Long>()
            val activeCounts = mutableMapOf<String, Int>()

            // Pre-compute event type constants outside the hot loop
            val foregroundEvent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    UsageEvents.Event.MOVE_TO_FOREGROUND
                }
            val backgroundEvent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    UsageEvents.Event.ACTIVITY_PAUSED
                } else {
                    @Suppress("DEPRECATION")
                    UsageEvents.Event.MOVE_TO_BACKGROUND
                }
            val stoppedEvent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    UsageEvents.Event.ACTIVITY_STOPPED
                } else {
                    -1
                }

            val screenOffEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 16 else -1 // SCREEN_NON_INTERACTIVE
            val keyguardShownEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 18 else -1 // KEYGUARD_SHOWN
            val deviceShutdownEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 26 else -1 // DEVICE_SHUTDOWN

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                val cls = event.className ?: "unknown_class"

                // If screen turns off or device shuts down, close all active sessions
                if (event.eventType == screenOffEvent || event.eventType == keyguardShownEvent || event.eventType == deviceShutdownEvent) {
                    val activePkgs = startTimes.keys.toList()
                    for (activePkg in activePkgs) {
                        val start = startTimes.remove(activePkg)
                        activeCounts.remove(activePkg)
                        if (start != null) {
                            val duration = event.timeStamp - start
                            if (duration > 2000) {
                                val cal = Calendar.getInstance().apply { timeInMillis = start }
                                result.add(
                                    AppUsageEntity(
                                        id = stableSessionId(activePkg, start),
                                        packageName = activePkg,
                                        appName = appIdentityResolver.getAppName(activePkg),
                                        appCategory = getCategoryForPackage(activePkg),
                                        startTime = start,
                                        endTime = event.timeStamp,
                                        durationMs = duration,
                                        timeSlot = getTimeSlot(cal.get(Calendar.HOUR_OF_DAY)),
                                        dayType = getDayType(cal.get(Calendar.DAY_OF_WEEK)),
                                        isHistorical = isHistorical,
                                    ),
                                )
                            }
                        }
                    }
                    continue
                }

                // Exclude home/launcher packages, this app, and system UI from session tracking
                if (appIdentityResolver.isLauncher(pkg) || pkg == context.packageName || pkg == "com.android.systemui") {
                    continue
                }

                if (event.eventType == foregroundEvent) {
                    val count = activeCounts.getOrDefault(pkg, 0)
                    if (count == 0) {
                        startTimes[pkg] = event.timeStamp
                    }
                    activeCounts[pkg] = count + 1
                } else if (event.eventType == backgroundEvent || event.eventType == stoppedEvent) {
                    val count = activeCounts.getOrDefault(pkg, 0)
                    // ACTIVITY_STOPPED acts as a force-close (process kill): drain counter to 0
                    val newCount = if (event.eventType == stoppedEvent) 0 else (count - 1).coerceAtLeast(0)
                    if (newCount == 0) {
                        activeCounts.remove(pkg)
                        val start = startTimes.remove(pkg)
                        if (start != null) {
                            val duration = event.timeStamp - start
                            if (duration > 2000) { // filter > 2000ms
                                val cal = Calendar.getInstance().apply { timeInMillis = start }
                                result.add(
                                    AppUsageEntity(
                                        id = stableSessionId(pkg, start),
                                        packageName = pkg,
                                        appName = appIdentityResolver.getAppName(pkg),
                                        appCategory = getCategoryForPackage(pkg),
                                        startTime = start,
                                        endTime = event.timeStamp,
                                        durationMs = duration,
                                        timeSlot = getTimeSlot(cal.get(Calendar.HOUR_OF_DAY)),
                                        dayType = getDayType(cal.get(Calendar.DAY_OF_WEEK)),
                                        isHistorical = isHistorical,
                                    ),
                                )
                            }
                        }
                    } else {
                        activeCounts[pkg] = newCount
                    }
                    if (count == 0) {
                        // Fallback: missed foreground event — clear any orphaned start time
                        startTimes.remove(pkg)
                    }
                }
            }

            // Include sessions still foregrounded when this collection ran. A later
            // overlapping collection replaces this row with the longer duration.
            for ((pkg, start) in startTimes) {
                // Defensive cap at 4 hours (14400000 ms) in case of missed close events
                val maxAllowedEnd = start + 14400000L
                val actualEnd = if (endMs > maxAllowedEnd) maxAllowedEnd else endMs
                val duration = actualEnd - start
                if (duration > 2000L) {
                    val cal = Calendar.getInstance().apply { timeInMillis = start }
                    result.add(
                        AppUsageEntity(
                            id = stableSessionId(pkg, start),
                            packageName = pkg,
                            appName = appIdentityResolver.getAppName(pkg),
                            appCategory = getCategoryForPackage(pkg),
                            startTime = start,
                            endTime = actualEnd,
                            durationMs = duration,
                            timeSlot = getTimeSlot(cal.get(Calendar.HOUR_OF_DAY)),
                            dayType = getDayType(cal.get(Calendar.DAY_OF_WEEK)),
                            isHistorical = isHistorical,
                        ),
                    )
                }
            }

            result.sortBy { it.startTime }

            // Merge contiguous or overlapping sessions of the same package (gap < 5 mins)
            val mergedResult = mergeContiguousSessions(result)

            var tempPrev: String? = null
            for (i in mergedResult.indices) {
                mergedResult[i] = mergedResult[i].copy(previousPackageName = tempPrev)
                tempPrev = mergedResult[i].packageName
            }

            val newSessions = mergedResult.filter { it.endTime > sinceMs }.toMutableList()
            if (newSessions.isNotEmpty() && newSessions.first().previousPackageName == null) {
                newSessions[0] = newSessions[0].copy(previousPackageName = prevStoredPackage)
            }

            return newSessions
        }

        fun mergeContiguousSessions(sessions: List<AppUsageEntity>): MutableList<AppUsageEntity> {
            val mergedResult = mutableListOf<AppUsageEntity>()
            for (session in sessions) {
                if (mergedResult.isEmpty()) {
                    mergedResult.add(session)
                } else {
                    val prev = mergedResult.last()
                    // If same package and gap is less than 5 minutes (300,000 ms)
                    if (prev.packageName == session.packageName && (session.startTime - prev.endTime) <= 300000L) {
                        val newEnd = maxOf(prev.endTime, session.endTime)
                        val newDuration = newEnd - prev.startTime
                        mergedResult[mergedResult.lastIndex] =
                            prev.copy(
                                endTime = newEnd,
                                durationMs = newDuration,
                            )
                    } else {
                        mergedResult.add(session)
                    }
                }
            }
            return mergedResult
        }

        private fun stableSessionId(
            packageName: String,
            startTime: Long,
        ): Long =
            "$packageName:$startTime".fold(0xcbf29ce484222325UL.toLong()) { hash, char ->
                (hash xor char.code.toLong()) * 0x100000001b3L
            }.let { if (it == 0L) 1L else it }

        suspend fun collectHistoricalData(): List<AppUsageEntity> {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -7)
            return collectUsageSince(cal.timeInMillis, isHistorical = true)
        }
    }
