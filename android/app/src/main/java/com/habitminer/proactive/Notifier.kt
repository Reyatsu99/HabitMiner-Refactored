package com.habitminer.proactive

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.habitminer.analytics.CheckInOption
import com.habitminer.analytics.DigestBuilder
import com.habitminer.analytics.Nudge
import com.habitminer.ui.MainActivity

/** Posts check-ins, nudges and the weekly digest. */
object Notifier {
    const val CHANNEL_CHECKINS = "checkins"
    const val CHANNEL_NUDGES = "nudges"
    const val CHANNEL_DIGEST = "weekly_digest"

    const val ID_CHECKIN = 2001
    const val ID_NUDGE = 2002
    const val ID_DIGEST = 2003

    const val EXTRA_OPEN = "open"
    const val OPEN_CHECKIN = "checkin"
    const val OPEN_INSIGHTS = "insights"
    const val EXTRA_PROMPTED_AT = "prompted_at"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECKINS, "Quick check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Up to 3 short \"what are you doing?\" questions a day, used to check the app's guesses"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NUDGES, "Gentle nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A heads-up after long stretches of scrolling or gaming, especially late at night"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DIGEST, "Weekly summary", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A short summary of your week, every Sunday evening"
            },
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openApp(
        context: Context,
        requestCode: Int,
        open: String?,
        promptedAt: Long? = null,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (open != null) putExtra(EXTRA_OPEN, open)
                if (promptedAt != null) putExtra(EXTRA_PROMPTED_AT, promptedAt)
            }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun answer(
        context: Context,
        option: CheckInOption,
        promptedAt: Long,
    ): PendingIntent {
        val intent =
            Intent(context, CheckInReceiver::class.java).apply {
                action = CheckInReceiver.ACTION_ANSWER
                putExtra(CheckInReceiver.EXTRA_VALUE, option.key)
                putExtra(EXTRA_PROMPTED_AT, promptedAt)
            }
        return PendingIntent.getBroadcast(
            context,
            100 + option.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    @Suppress("MissingPermission")
    private fun post(
        context: Context,
        id: Int,
        builder: NotificationCompat.Builder,
    ) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun postCheckIn(
        context: Context,
        promptedAt: Long,
    ) {
        val builder =
            NotificationCompat.Builder(context, CHANNEL_CHECKINS)
                .setSmallIcon(android.R.drawable.ic_menu_help)
                .setContentTitle("Quick check-in")
                .setContentText("What are you doing right now? One tap helps HabitMiner learn.")
                .setContentIntent(openApp(context, 10, OPEN_CHECKIN, promptedAt))
                .setAutoCancel(true)
                .setTimeoutAfter(45 * 60 * 1000L)
                .addAction(0, "${CheckInOption.STUDYING.emoji} Studying", answer(context, CheckInOption.STUDYING, promptedAt))
                .addAction(0, "${CheckInOption.RELAXING.emoji} Relaxing", answer(context, CheckInOption.RELAXING, promptedAt))
                .addAction(0, "More…", openApp(context, 11, OPEN_CHECKIN, promptedAt))
        post(context, ID_CHECKIN, builder)
    }

    fun postNudge(
        context: Context,
        nudge: Nudge,
    ) {
        val builder =
            NotificationCompat.Builder(context, CHANNEL_NUDGES)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(nudge.title)
                .setContentText(nudge.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.body))
                .setContentIntent(openApp(context, 12, null))
                .setAutoCancel(true)
        post(context, ID_NUDGE, builder)
    }

    fun postDigest(
        context: Context,
        digest: DigestBuilder.Digest,
    ) {
        val style = NotificationCompat.InboxStyle()
        digest.lines.forEach { style.addLine(it) }
        val builder =
            NotificationCompat.Builder(context, CHANNEL_DIGEST)
                .setSmallIcon(android.R.drawable.ic_menu_week)
                .setContentTitle(digest.title)
                .setContentText(digest.lines.firstOrNull() ?: "Tap to see your week")
                .setStyle(style)
                .setContentIntent(openApp(context, 13, OPEN_INSIGHTS))
                .setAutoCancel(true)
        post(context, ID_DIGEST, builder)
    }

    fun cancelCheckIn(context: Context) = NotificationManagerCompat.from(context).cancel(ID_CHECKIN)
}
