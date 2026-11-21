package com.habitminer.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AppIdentityResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val packageManager: PackageManager = context.packageManager
        private val appNameCache = ConcurrentHashMap<String, String>()
        private val launcherCache = ConcurrentHashMap<String, Boolean>()

        /** Whether the app is still on the phone (uninstalled apps stay in your history). */
        open fun isInstalled(packageName: String): Boolean =
            try {
                packageManager.getApplicationInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: SecurityException) {
                // Can't tell: don't hide it.
                true
            }

        open fun getAppName(packageName: String): String {
            if (packageName.isBlank()) return ""
            return appNameCache.getOrPut(packageName) {
                try {
                    val info = packageManager.getApplicationInfo(packageName, 0)
                    val label = packageManager.getApplicationLabel(info).toString()
                    label.takeIf { it.isNotBlank() } ?: fallbackLabel(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    fallbackLabel(packageName)
                } catch (e: SecurityException) {
                    fallbackLabel(packageName)
                }
            }
        }

        open fun isLauncher(packageName: String): Boolean {
            if (packageName.isBlank()) return false
            return launcherCache.getOrPut(packageName) {
                val homeIntent =
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                    }

                val resolveInfos =
                    packageManager.queryIntentActivities(
                        homeIntent,
                        PackageManager.MATCH_DEFAULT_ONLY,
                    )
                if (resolveInfos.any { it.activityInfo?.packageName == packageName }) {
                    true
                } else {
                    packageManager.queryIntentActivities(
                        homeIntent,
                        PackageManager.MATCH_ALL,
                    ).any { it.activityInfo?.packageName == packageName }
                }
            }
        }

        open fun getLauncherPackages(): List<String> {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            return packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
        }

        private fun fallbackLabel(packageName: String): String {
            val leaf = packageName.substringAfterLast('.').replace('_', ' ').replace('-', ' ')
            val known =
                mapOf(
                    "youtube" to "YouTube",
                    "gm" to "Gmail",
                    "chrome" to "Chrome",
                    "maps" to "Google Maps",
                    "facebook" to "Facebook",
                    "instagram" to "Instagram",
                    "whatsapp" to "WhatsApp",
                    "messenger" to "Messenger",
                    "tiktok" to "TikTok",
                    "twitter" to "X",
                    "linkedin" to "LinkedIn",
                    "spotify" to "Spotify",
                )
            val knownPackages =
                mapOf(
                    "com.instagram.android" to "Instagram",
                    "com.facebook.katana" to "Facebook",
                    "com.twitter.android" to "X",
                    "com.snapchat.android" to "Snapchat",
                    "com.reddit.frontpage" to "Reddit",
                    "com.google.android.apps.youtube.music" to "YouTube Music",
                )
            return knownPackages[packageName] ?: known[leaf.lowercase()] ?: leaf.split(' ').joinToString(" ") { word ->
                word.replaceFirstChar { char -> char.uppercase() }
            }
        }
    }
