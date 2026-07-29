package com.inktone.core.testing.fake

import com.inktone.domain.service.VoiceDownloadProgress
import com.inktone.domain.service.VoiceModelDownloadService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeVoiceModelDownloadService(
    private val progressSequence: List<VoiceDownloadProgress> = listOf(VoiceDownloadProgress.Complete),
) : VoiceModelDownloadService {
    override fun downloadDefaultVoiceModel(): Flow<VoiceDownloadProgress> = flowOf(*progressSequence.toTypedArray())
}
