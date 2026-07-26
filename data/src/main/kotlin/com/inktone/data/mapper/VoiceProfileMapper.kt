package com.inktone.data.mapper

import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.infrastructure.database.entity.VoiceProfileEntity

fun VoiceProfile.toEntity(): VoiceProfileEntity = VoiceProfileEntity(
    id = id, engine = engine.name, voice = voice, language = language,
    speed = speed, pitch = pitch, volume = volume, style = style,
)

fun VoiceProfileEntity.toDomain(): VoiceProfile = VoiceProfile(
    id = id, engine = TtsEngineId.valueOf(engine), voice = voice, language = language,
    speed = speed, pitch = pitch, volume = volume, style = style,
)
