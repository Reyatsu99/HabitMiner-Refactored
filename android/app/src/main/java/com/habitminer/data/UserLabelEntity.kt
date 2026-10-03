package com.habitminer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something the user told us: a check-in answer ("what are you doing?") or feedback on a
 * deviation (expected / unusual). These are ground-truth labels for evaluating the
 * models and are included in the data export.
 */
@Entity(tableName = "user_labels", indices = [Index(value = ["kind", "timestamp"]), Index(value = ["refKey"])])
data class UserLabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** [KIND_CHECK_IN] or [KIND_DEVIATION_FEEDBACK]. */
    val kind: String,
    val value: String,
    /** For deviation feedback: the deviation's fingerprint. */
    val refKey: String? = null,
    /** When the prompt was shown, for check-ins answered from a notification. */
    val promptedAt: Long? = null,
    /** Context at answer time: current app, light, motion, charging, place. */
    val contextJson: String? = null,
) {
    companion object {
        const val KIND_CHECK_IN = "CHECK_IN"
        const val KIND_DEVIATION_FEEDBACK = "DEVIATION_FEEDBACK"
        const val FEEDBACK_EXPECTED = "EXPECTED"
        const val FEEDBACK_UNUSUAL = "UNUSUAL"
    }
}
