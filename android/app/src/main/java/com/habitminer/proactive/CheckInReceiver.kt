package com.habitminer.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitminer.repository.FeedbackRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Saves a check-in answered straight from the notification's action buttons. */
@AndroidEntryPoint
class CheckInReceiver : BroadcastReceiver() {
    @Inject
    lateinit var feedbackRepository: FeedbackRepository

    @Inject
    lateinit var labelContextCapture: LabelContextCapture

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_ANSWER) return
        val value = intent.getStringExtra(EXTRA_VALUE) ?: return
        val promptedAt = intent.getLongExtra(Notifier.EXTRA_PROMPTED_AT, -1L).takeIf { it > 0 }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                feedbackRepository.saveCheckIn(value, promptedAt, labelContextCapture.captureJson())
                Notifier.cancelCheckIn(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.habitminer.action.CHECKIN_ANSWER"
        const val EXTRA_VALUE = "value"
    }
}
