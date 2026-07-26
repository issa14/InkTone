package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey val id: String,
    val engine: String,
    val voice: String,
    val language: String,
    val speed: Float,
    val pitch: Float,
    val volume: Float,
    val style: String?,
)
