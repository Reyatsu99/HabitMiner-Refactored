package com.habitminer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A Wi-Fi place. Only a salted hash of the network is stored, never its name or address. */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val placeHash: String,
    /** Name chosen by the user, or null to use the suggested one. */
    val label: String? = null,
    val firstSeen: Long,
    val lastSeen: Long,
)
