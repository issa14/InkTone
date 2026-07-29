package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ligne unique — id toujours 0. Choix délibéré de rester sur Room plutôt
 * que d'introduire DataStore comme seconde techno de persistance sans ADR. */
@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 0,
    val theme: String,
    val fontSize: Int,
    val defaultTtsEngine: String,
    val crashReportingEnabled: Boolean,
    val language: String,
    val fontFamily: String = "DEFAULT",
    val reduceMotion: Boolean = false,
)
