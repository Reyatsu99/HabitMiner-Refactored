package com.habitminer.collection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.habitminer.data.PrefsKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in place detection from the connected Wi-Fi network.
 *
 * Only a salted SHA-256 hash of the access point's BSSID is kept, so the stored value
 * can't be matched to a real network or location. Android only reveals the BSSID with
 * the location permission (and location services switched on).
 */
@Singleton
class WifiPlaceProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs get() = context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

        fun isEnabled(): Boolean = prefs.getBoolean(PrefsKeys.PLACES_ENABLED, false)

        fun hasPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun setEnabled(enabled: Boolean) {
            prefs.edit().putBoolean(PrefsKeys.PLACES_ENABLED, enabled).apply()
        }

        @Suppress("DEPRECATION")
        fun currentPlaceHash(): String? {
            if (!isEnabled() || !hasPermission()) return null
            return try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val bssid = wifi.connectionInfo?.bssid ?: return null
                if (bssid.isBlank() || bssid == "02:00:00:00:00:00") return null
                hash(bssid.lowercase())
            } catch (e: SecurityException) {
                null
            }
        }

        private fun hash(value: String): String {
            val salt =
                prefs.getString(PrefsKeys.PLACE_SALT, null) ?: UUID.randomUUID().toString().also {
                    prefs.edit().putString(PrefsKeys.PLACE_SALT, it).apply()
                }
            val digest = MessageDigest.getInstance("SHA-256").digest((salt + value).toByteArray())
            return digest.take(6).joinToString("") { "%02x".format(it) }
        }
    }
