package com.inktone.core.testing.fake

import com.inktone.domain.service.VoiceDownloadProgress
import com.inktone.domain.service.VoiceModelDownloadService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeVoiceModelDownloadService(
    private val progressSequence: List<VoiceDownloadProgress> = listOf(VoiceDownloadProgress.Complete),
    // Lot 20 — reflète l'installation réelle (isDefaultVoiceInstalled) ;
    // vrai par défaut pour ne pas casser les tests existants qui ne
    // testent pas l'état initial.
    private val installed: Boolean = true,
) : VoiceModelDownloadService {
    override fun downloadDefaultVoiceModel(): Flow<VoiceDownloadProgress> = flowOf(*progressSequence.toTypedArray())
    override fun isDefaultVoiceInstalled(): Boolean = installed
}
