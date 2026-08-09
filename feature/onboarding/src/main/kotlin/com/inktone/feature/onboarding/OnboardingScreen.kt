package com.inktone.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Lot 10, Tâche 10.2 — `HorizontalPager` à trois cartes, remplace le
 * `when` sur enum avec `Column`/`Button` nus. Balayage horizontal +
 * bouton « Passer » (cartes 1/2, accessibilité — ne pas dépendre
 * uniquement de la découverte du geste) + « Commencer » (carte 3, seule
 * sortie explicite). Les deux mènent à [onDone].
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel(), onDone: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })

    LaunchedEffect(state.hasCompleted) {
        if (state.hasCompleted) onDone()
    }

    Scaffold { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> WelcomeCard()
                    1 -> FeaturesCard()
                    else -> ReadyCard(onStart = { viewModel.onIntent(OnboardingIntent.Complete) })
                }
            }

            if (pagerState.currentPage < 2) {
                TextButton(
                    onClick = { viewModel.onIntent(OnboardingIntent.Complete) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Text("Passer")
                }
            }

            PageIndicators(
                pageCount = 3,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun WelcomeCard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WelcomeIllustration(modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(1f))
        Spacer(Modifier.height(32.dp))
        Text("Bienvenue sur InkTone", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "Lisez avec les yeux, continuez avec les oreilles. Une expérience de lecture unifiée qui s'adapte à votre rythme.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun FeaturesCard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Conçu pour votre confort", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FeatureBlock(
                modifier = Modifier.weight(1f),
                title = "Lecture sur mesure",
                body = "Thèmes, typographie et mise en page entièrement personnalisables pour un confort visuel absolu.",
                icon = { iconModifier -> BookIconIllustration(modifier = iconModifier) },
            )
            Spacer(Modifier.width(16.dp))
            FeatureBlock(
                modifier = Modifier.weight(1f),
                title = "Narration naturelle",
                body = "Des voix ultra-réalistes et fluides. Ajustez la vitesse et laissez-vous porter par l'histoire.",
                icon = { iconModifier -> AudioIconIllustration(modifier = iconModifier) },
            )
        }
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun FeatureBlock(modifier: Modifier = Modifier, title: String, body: String, icon: @Composable (Modifier) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        icon(Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadyCard(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ReadyIllustration(modifier = Modifier.fillMaxWidth(0.65f).aspectRatio(1f))
        Spacer(Modifier.height(32.dp))
        Text("Votre prochaine histoire vous attend", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Importez vos livres et commencez l'expérience InkTone.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Commencer")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PageIndicators(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            Box(
                Modifier
                    .size(if (isActive) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}
