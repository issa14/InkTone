package com.inktone.core.testing.fake

import com.inktone.domain.service.VoiceDownloadProgress
import com.inktone.domain.service.VoiceModelDownloadScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Planificateur de test : la progression est pilotée par le test, les commandes comptées. */
class FakeVoiceModelDownloadScheduler : VoiceModelDownloadScheduler {
    val progressFlow = MutableStateFlow<VoiceDownloadProgress?>(null)
    override val progress: StateFlow<VoiceDownloadProgress?> get() = progressFlow

    var startCount = 0
        private set
    var cancelCount = 0
        private set

    override fun start() {
        startCount++
    }

    override fun cancel() {
        cancelCount++
    }
}
