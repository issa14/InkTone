package com.inktone.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/** Couleur signature InkTone — utilisée volontairement en littéral sur cet écran de marque (retour Issa, vérification device) : la couleur dynamique (Material You, activée par défaut) ne garantit pas le bordeaux à cet endroit précis, alors que l'onboarding est le premier contact avec l'identité visuelle. */
val InkToneBordeaux = Color(0xFF7A1F3D)

/**
 * Lot 10, Tâche 10.2 — `HorizontalPager` à trois cartes. Retour Issa
 * (vérification device) : chaque page est encapsulée dans une `Card`
 * centrale (pas des éléments flottant en plein écran), la couleur
 * signature InkTone remplace les teintes par défaut du Material Theme
 * sur les éléments d'accentuation.
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
                Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        when (page) {
                            0 -> WelcomeCard()
                            1 -> FeaturesCard()
                            else -> ReadyCard(onStart = { viewModel.onIntent(OnboardingIntent.Complete) })
                        }
                    }
                }
            }

            if (pagerState.currentPage < 2) {
                TextButton(
                    onClick = { viewModel.onIntent(OnboardingIntent.Complete) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
                ) {
                    Text("Passer", color = InkToneBordeaux)
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandIcon(icon = Icons.AutoMirrored.Outlined.MenuBook, size = 96.dp)
        Spacer(Modifier.height(32.dp))
        Text("Bienvenue sur InkTone", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        BodyText("Lisez avec les yeux, continuez avec les oreilles. Une expérience de lecture unifiée qui s'adapte à votre rythme.")
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun FeaturesCard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Conçu pour votre confort",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeatureBlock(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = "Lecture sur mesure",
                body = "Thèmes, typographie et mise en page entièrement personnalisables pour un confort visuel absolu.",
            )
            FeatureBlock(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Narration naturelle",
                body = "Des voix ultra-réalistes et fluides. Ajustez la vitesse et laissez-vous porter par l'histoire.",
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun FeatureBlock(modifier: Modifier = Modifier, icon: ImageVector, title: String, body: String) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = InkToneBordeaux.copy(alpha = 0.04f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, InkToneBordeaux.copy(alpha = 0.15f)),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(InkToneBordeaux.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = InkToneBordeaux, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyCard(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Carte 3 volontairement différenciée de la carte 1 (retour Issa,
        // point 5 du plan) : icône livre entourée de cercles concentriques,
        // pas la même composition que l'accueil.
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).clip(CircleShape).border(1.dp, InkToneBordeaux.copy(alpha = 0.15f), CircleShape))
            Box(Modifier.size(112.dp).clip(CircleShape).border(1.dp, InkToneBordeaux.copy(alpha = 0.3f), CircleShape))
            BrandIcon(icon = Icons.AutoMirrored.Outlined.MenuBook, size = 88.dp)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Votre prochaine histoire vous attend",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        BodyText("Importez vos livres et commencez l'expérience InkTone.")
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(containerColor = InkToneBordeaux),
        ) {
            Text("Commencer")
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Icône de marque — remplace les `Canvas` dessinés à la main (retour Issa) : prêt à accueillir un futur `VectorDrawable`/SVG sans changer l'appelant. */
@Composable
private fun BrandIcon(icon: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Icon(icon, contentDescription = null, tint = InkToneBordeaux, modifier = Modifier.size(size))
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 24.sp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp),
    )
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
                    .background(if (isActive) InkToneBordeaux else InkToneBordeaux.copy(alpha = 0.25f)),
            )
        }
    }
}
