package com.habitminer.engine

import com.habitminer.analytics.CheckInOption
import com.habitminer.data.DeviationEntity

/**
 * What the Today, History and Insights screens can ask for. [HabitViewModel] implements it;
 * screenshot tests pass a no-op version so screens render without the real data layer.
 */
interface HabitActions {
    fun checkPermissions()

    fun loadHistoricalData()

    fun answerCheckIn(option: CheckInOption)

    fun dismissCheckIn()

    fun giveDeviationFeedback(
        deviation: DeviationEntity,
        value: String,
    )

    fun selectHistoryDate(timeInMillis: Long)
}
