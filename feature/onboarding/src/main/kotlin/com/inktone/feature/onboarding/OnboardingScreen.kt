package com.inktone.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.service.VoiceDownloadProgress

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel(), onDone: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()

    when (state.step) {
        OnboardingStep.Welcome -> WelcomeStep(onNext = { viewModel.onIntent(OnboardingIntent.Next) })
        OnboardingStep.CrashConsent -> CrashConsentStep(
            onAccept = { viewModel.onIntent(OnboardingIntent.SetCrashReporting(true)) },
            onDecline = { viewModel.onIntent(OnboardingIntent.SetCrashReporting(false)) },
        )
        OnboardingStep.VoiceDownload -> VoiceDownloadStep(
            downloadProgress = state.downloadProgress,
            onStart = { viewModel.onIntent(OnboardingIntent.StartVoiceDownload) },
            onSkip = { viewModel.onIntent(OnboardingIntent.Next) },
        )
        OnboardingStep.Done -> onDone()
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Bienvenue dans InkTone")
        Text("Lecture EPUB avec narration TTS synchronisee mot a mot.")
        Button(onClick = onNext) { Text("Continuer") }
    }
}

/**
 * Texte honnete sur le contenu d'un rapport de crash (Blueprint §10.7,
 * ADR-014) — ce qui y figure (trace, version, modele d'appareil), ce
 * qui n'y figure JAMAIS (contenu des livres, annotations). Defaut :
 * DESACTIVE (opt-in, pas opt-out).
 */
@Composable
private fun CrashConsentStep(onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Rapports de crash")
        Text(
            "En cas d'acceptation, nous envoyons uniquement la trace " +
                "d'erreur, la version de l'app et le modele d'appareil. " +
                "Jamais le contenu de vos livres ni vos annotations. " +
                "Reversible a tout moment dans les reglages.",
        )
        Button(onClick = onAccept) { Text("Activer") }
        Button(onClick = onDecline) { Text("Ne pas activer") }
    }
}

@Composable
private fun VoiceDownloadStep(
    downloadProgress: VoiceDownloadProgress?,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Voix neuronale (optionnel)")
        Text("Ameliore la qualite de la narration. La lecture visuelle reste disponible sans cela.")
        when (downloadProgress) {
            is VoiceDownloadProgress.InProgress ->
                Text("Telechargement : ${downloadProgress.bytesDownloaded} / ${downloadProgress.totalBytes} octets")
            is VoiceDownloadProgress.Failed -> Text("Echec : ${downloadProgress.message}")
            VoiceDownloadProgress.Complete -> Text("Voix installee")
            null -> Unit
        }
        Button(onClick = onStart) { Text("Telecharger") }
        Button(onClick = onSkip) { Text("Passer") }
    }
}
