package com.habitminer.engine

import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

@Singleton
open class TimeProvider @Inject constructor() {
    open fun currentTimeMillis(): Long = System.currentTimeMillis()
    open fun getCalendar(): Calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis() }
}
