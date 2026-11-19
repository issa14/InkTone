package com.inktone.feature.onboarding

import com.inktone.core.designsystem.InkToneBrand
import com.inktone.core.designsystem.InkToneSpacing

import com.inktone.core.designsystem.emphasized
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.NarrativeAccentFamily

// Couleur de marque fixe sur cet écran d'identité (retour Issa, vérification
// device) : la couleur dynamique ne garantirait pas le violet InkTone ici.

/** Thème réellement appliqué, pas celui du système : l'app peut forcer le clair ou le sombre. */
@Composable
private fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
private fun accentColor(): Color = if (isAppInDarkTheme()) InkToneBrand.violetLight else InkToneBrand.violet

/**
 * Lot 10, Tâche 10.2 — `HorizontalPager` à trois cartes. Retour Issa
 * (vérification device) : chaque page est encapsulée dans une `Card`
 * centrale (pas des éléments flottant en plein écran), la couleur
 * signature InkTone remplace les teintes par défaut du Material Theme
 * sur les éléments d'accentuation, avec une variante claire en mode
 * sombre pour rester lisible.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onDone: (importFirstBook: Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val accent = accentColor()

    LaunchedEffect(state.hasCompleted) {
        if (state.hasCompleted) onDone(state.importFirstBook)
    }

    Scaffold { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                Box(Modifier.fillMaxSize().padding(start = InkToneSpacing.xl, end = InkToneSpacing.xl, top = InkToneSpacing.lg)) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        when (page) {
                            0 -> WelcomeCard(accent)
                            1 -> FeaturesCard(accent)
                            else -> ReadyCard(
                                accent,
                                onImport = { viewModel.onIntent(OnboardingIntent.Complete(importFirstBook = true)) },
                                onLater = { viewModel.onIntent(OnboardingIntent.Complete()) },
                            )
                        }
                    }
                }
            }

            // Barre basse hors de la carte : posés dessus, ces boutons
            // chevauchaient le texte dès qu'il s'allongeait (audit UI P1-15).
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = InkToneSpacing.lg, vertical = InkToneSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (pagerState.currentPage < 2) {
                        TextButton(onClick = { viewModel.onIntent(OnboardingIntent.Complete()) }) {
                            Text("Passer", color = accent)
                        }
                    }
                }
                PageIndicators(pageCount = 3, currentPage = pagerState.currentPage, accent = accent)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    // Le balayage seul n'était pas découvrable (audit UX C12).
                    if (pagerState.currentPage < 2) {
                        TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                            Text("Suivant", color = accent)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contenu d'une carte, centré quand il tient et défilable sinon : à grande
 * police, le bouton de la dernière carte sortait de l'écran (audit UI P1-15).
 */
@Composable
private fun OnboardingPage(
    horizontalPadding: Dp = 24.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = horizontalPadding, vertical = InkToneSpacing.xl),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** Illustrations réduites à grande police, pour laisser la place au texte. */
@Composable
private fun isLargeFontScale(): Boolean = LocalDensity.current.fontScale > 1.3f

/**
 * Sous-lot 2a — l'icône de l'application (cacatoès + livre) remplace
 * l'ancienne illustration livre générique + ondes sonores. La version
 * monochrome (drawable-nodpi) est tintée avec la couleur d'accent,
 * qui s'adapte automatiquement au thème clair/sombre via [accentColor].
 */
@Composable
private fun WelcomeCard(accent: Color) {
    OnboardingPage {
        Image(
            painter = painterResource(com.inktone.core.designsystem.R.drawable.app_icon_monochrome),
            contentDescription = null,
            modifier = Modifier.size(if (isLargeFontScale()) 120.dp else 180.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(accent),
        )
        Spacer(Modifier.height(InkToneSpacing.xxl))
        Text("Bienvenue sur InkTone", style = MaterialTheme.typography.headlineMedium.emphasized(), fontFamily = NarrativeAccentFamily, textAlign = TextAlign.Center)
        Spacer(Modifier.height(InkToneSpacing.lg))
        BodyText("Lisez vos livres ou écoutez-les : la voix reprend là où vous en étiez.")
    }
}

/**
 * Carte 2 — retour Issa (test appareil réel) : les deux sous-cartes
 * côte à côte compressaient le texte en un mur illisible. Remplacées par
 * une liste verticale aérée (icône ronde à gauche, titre + description à
 * droite), plus lisible qu'une contrainte de largeur à 50/50.
 */
@Composable
private fun FeaturesCard(accent: Color) {
    OnboardingPage(horizontalPadding = 32.dp, horizontalAlignment = Alignment.Start) {
        Text(
            "Lire et écouter",
            style = MaterialTheme.typography.headlineMedium.emphasized(),
            fontFamily = NarrativeAccentFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(InkToneSpacing.xl)) {
            FeatureRow(
                accent = accent,
                icon = AppSymbol.Reading,
                title = "Votre mise en page",
                body = "Thème, police, taille et marges, réglables sans quitter le livre.",
            )
            FeatureRow(
                accent = accent,
                icon = AppSymbol.Speaking,
                title = "Une voix qui lit pour vous",
                body = "Hors ligne, à la vitesse de votre choix. Avec la voix naturelle, chaque mot prononcé est surligné.",
            )
        }
    }
}

/** Pastille à 48dp et espacement à 12dp (retour Issa) : à 56dp/16dp, la colonne de texte était trop étroite et cassait les descriptions en lignes de deux mots. */
@Composable
private fun FeatureRow(accent: Color, icon: AppSymbol, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(InkToneSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(InkToneSpacing.xs))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyCard(accent: Color, onImport: () -> Unit, onLater: () -> Unit) {
    OnboardingPage {
        // Carte 3 volontairement différenciée de la carte 1 (retour Issa) :
        // icône livre AU CENTRE EXACT de cercles concentriques, pas la
        // même composition que l'accueil.
        // Alpha des bordures dépendant du thème (retour Issa, V2206) : à
        // alpha fixe, les cercles s'effacent sur le fond quasi noir du
        // thème sombre.
        val isDark = isAppInDarkTheme()
        val outerAlpha = if (isDark) 0.35f else 0.15f
        val innerAlpha = if (isDark) 0.55f else 0.3f
        val scale = if (isLargeFontScale()) 0.75f else 1f
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp * scale).clip(CircleShape).border(1.dp, accent.copy(alpha = outerAlpha), CircleShape))
            Box(Modifier.size(112.dp * scale).clip(CircleShape).border(1.dp, accent.copy(alpha = innerAlpha), CircleShape))
            BrandIcon(icon = AppSymbol.Reading, size = 88.dp * scale, tint = accent)
        }
        Spacer(Modifier.height(InkToneSpacing.xxl))
        Text(
            "Ajoutez votre premier livre",
            style = MaterialTheme.typography.headlineMedium.emphasized(),
            fontFamily = NarrativeAccentFamily,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(InkToneSpacing.md))
        BodyText("EPUB, PDF ou TXT, depuis votre téléphone ou un catalogue en ligne.")
        Spacer(Modifier.height(InkToneSpacing.xxl))
        // Contraste forcé (retour Issa) : violet de marque et texte blanc,
        // lisibles en clair comme en sombre, indépendamment de accentColor().
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(containerColor = InkToneBrand.violet, contentColor = Color.White),
        ) {
            Text("Importer un livre")
        }
        TextButton(onClick = onLater) {
            Text("Plus tard", color = accent)
        }
    }
}

/** Icône de marque — remplace les `Canvas` dessinés à la main (retour Issa) : prêt à accueillir un futur `VectorDrawable`/SVG sans changer l'appelant. */
@Composable
private fun BrandIcon(icon: AppSymbol, size: Dp, tint: Color) {
    AppIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = InkToneSpacing.xxl),
    )
}

@Composable
private fun PageIndicators(pageCount: Int, currentPage: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Page ${currentPage + 1} sur $pageCount" },
        horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.sm),
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            Box(
                Modifier
                    .size(if (isActive) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) accent else accent.copy(alpha = 0.25f)),
            )
        }
    }
}
