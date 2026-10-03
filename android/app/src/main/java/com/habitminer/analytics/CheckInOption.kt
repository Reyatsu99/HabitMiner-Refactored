package com.habitminer.analytics

/** Answers offered by the "what are you doing?" check-in. */
enum class CheckInOption(val key: String, val label: String, val emoji: String) {
    STUDYING("studying", "Studying or working", "📚"),
    RELAXING("relaxing", "Relaxing or entertainment", "🎮"),
    SOCIALISING("socialising", "With people", "🗣️"),
    COMMUTING("commuting", "Commuting or walking", "🚶"),
    EATING("eating", "Eating", "🍽️"),
    RESTING("resting", "In bed or resting", "🛏️"),
    ;

    companion object {
        fun fromKey(key: String): CheckInOption? = entries.firstOrNull { it.key == key }
    }
}
