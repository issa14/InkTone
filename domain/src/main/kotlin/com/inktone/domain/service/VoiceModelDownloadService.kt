package com.inktone.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction domaine du telechargement de voix (Tache 8.7, ADR-018) —
 * `VoiceModelDownloader` (Tache 5.6, infrastructure/tts) est deja fait et
 * teste mais n'a jamais ete cable a une UI. `feature/onboarding` n'a pas
 * le droit de dependre de `infrastructure/tts` directement (Blueprint
 * §12.4, matrice de dependances) — cette interface est le point de
 * passage.
 */
interface VoiceModelDownloadService {
    fun downloadDefaultVoiceModel(): Flow<VoiceDownloadProgress>
}

sealed interface VoiceDownloadProgress {
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : VoiceDownloadProgress
    data object Complete : VoiceDownloadProgress
    data class Failed(val message: String) : VoiceDownloadProgress
}
