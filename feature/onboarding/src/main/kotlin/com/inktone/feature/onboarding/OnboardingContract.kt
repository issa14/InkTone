package com.inktone.feature.onboarding

import com.inktone.domain.service.VoiceDownloadProgress

enum class OnboardingStep { Welcome, CrashConsent, VoiceDownload, Done }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val downloadProgress: VoiceDownloadProgress? = null,
)

sealed interface OnboardingIntent {
    data object Next : OnboardingIntent
    data class SetCrashReporting(val enabled: Boolean) : OnboardingIntent
    data object StartVoiceDownload : OnboardingIntent
}
