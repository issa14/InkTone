package com.inktone.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Couleur signature InkTone — utilisée volontairement en littéral sur cet
 * écran de marque (retour Issa, vérification device) : la couleur
 * dynamique (Material You, activée par défaut) ne garantit pas le
 * bordeaux à cet endroit précis, alors que l'onboarding est le premier
 * contact avec l'identité visuelle.
 *
 * [InkToneBordeauxLight] — retour Issa (test appareil réel, mode sombre) :
 * le bordeaux standard manque de contraste sur fond sombre. [accentColor]
 * choisit la bonne variante selon [isSystemInDarkTheme] pour les accents
 * (icônes, indicateurs, texte secondaire) ; le bouton « Commencer » reste
 * volontairement sur le bordeaux standard avec un texte blanc forcé, pas
 * la variante claire (voir [ReadyCard]) — un bouton plein doit rester
 * lisible par son propre contraste fond/texte, indépendamment du thème.
 */
val InkToneBordeaux = Color(0xFF7A1F3D)
val InkToneBordeauxLight = Color(0xFFD67393)

@Composable
private fun accentColor(): Color = if (isSystemInDarkTheme()) InkToneBordeauxLight else InkToneBordeaux

/**
 * Lot 10, Tâche 10.2 — `HorizontalPager` à trois cartes. Retour Issa
 * (vérification device) : chaque page est encapsulée dans une `Card`
 * centrale (pas des éléments flottant en plein écran), la couleur
 * signature InkTone remplace les teintes par défaut du Material Theme
 * sur les éléments d'accentuation, avec une variante claire en mode
 * sombre pour rester lisible.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel(), onDone: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val accent = accentColor()

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
                            0 -> WelcomeCard(accent)
                            1 -> FeaturesCard(accent)
                            else -> ReadyCard(accent, onStart = { viewModel.onIntent(OnboardingIntent.Complete) })
                        }
                    }
                }
            }

            if (pagerState.currentPage < 2) {
                TextButton(
                    onClick = { viewModel.onIntent(OnboardingIntent.Complete) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
                ) {
                    Text("Passer", color = accent)
                }
            }

            PageIndicators(
                pageCount = 3,
                currentPage = pagerState.currentPage,
                accent = accent,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            )
        }
    }
}

/**
 * Retour Issa (vérification device, V2206) : la métaphore sonore du texte
 * (« continuez avec les oreilles ») avait disparu de cette carte — le
 * livre y était seul, identique en composition à la carte 3. [SoundWaves]
 * réintroduit l'évocation audio à côté du livre, sans revenir à un
 * `Canvas` pour le livre lui-même (toujours un [BrandIcon]).
 * Le `Spacer` final de 64dp qui poussait le bloc vers le haut est retiré :
 * `Arrangement.Center` seul recentre correctement la composition.
 */
@Composable
private fun WelcomeCard(accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandIcon(icon = Icons.AutoMirrored.Outlined.MenuBook, size = 96.dp, tint = accent)
            SoundWaves(accent = accent, modifier = Modifier.width(48.dp).height(96.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text("Bienvenue sur InkTone", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        BodyText("Lisez avec les yeux, continuez avec les oreilles. Une expérience de lecture unifiée qui s'adapte à votre rythme.")
    }
}

/** Arcs concentrés évoquant une onde sonore, opacité décroissante vers l'extérieur — pas un dessin du livre, une couche graphique légère à côté de [BrandIcon]. */
@Composable
private fun SoundWaves(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val strokeWidth = 3.dp.toPx()
        val baseRadius = size.height * 0.4f
        repeat(3) { index ->
            val radius = baseRadius + index * (size.height * 0.22f)
            val alpha = 0.5f - index * 0.15f
            drawArc(
                color = accent.copy(alpha = alpha),
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(-radius, size.height / 2f - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
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
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Conçu pour votre confort",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            FeatureRow(
                accent = accent,
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = "Lecture sur mesure",
                body = "Thèmes, typographie et mise en page entièrement personnalisables pour un confort visuel absolu.",
            )
            FeatureRow(
                accent = accent,
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Narration naturelle",
                body = "Des voix ultra-réalistes et fluides. Ajustez la vitesse et laissez-vous porter par l'histoire.",
            )
        }
    }
}

/** Pastille à 48dp et espacement à 12dp (retour Issa) : à 56dp/16dp, la colonne de texte était trop étroite et cassait les descriptions en lignes de deux mots. */
@Composable
private fun FeatureRow(accent: Color, icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyCard(accent: Color, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Carte 3 volontairement différenciée de la carte 1 (retour Issa) :
        // icône livre AU CENTRE EXACT de cercles concentriques, pas la
        // même composition que l'accueil.
        // Alpha des bordures dépendant du thème (retour Issa, V2206) : à
        // alpha fixe, les cercles s'effacent sur le fond quasi noir du
        // thème sombre — un alpha fixe appliqué à une couleur déjà proche
        // du fond passe sous le seuil de perception.
        val isDark = isSystemInDarkTheme()
        val outerAlpha = if (isDark) 0.35f else 0.15f
        val innerAlpha = if (isDark) 0.55f else 0.3f
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).clip(CircleShape).border(1.dp, accent.copy(alpha = outerAlpha), CircleShape))
            Box(Modifier.size(112.dp).clip(CircleShape).border(1.dp, accent.copy(alpha = innerAlpha), CircleShape))
            BrandIcon(icon = Icons.AutoMirrored.Outlined.MenuBook, size = 88.dp, tint = accent)
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
        // Contraste force explicitement (retour Issa) : le bouton plein
        // reste sur le bordeaux standard (pas la variante claire) avec un
        // texte blanc force, lisible en clair comme en sombre par son
        // propre contraste, independamment de accentColor().
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(containerColor = InkToneBordeaux, contentColor = Color.White),
        ) {
            Text("Commencer")
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Icône de marque — remplace les `Canvas` dessinés à la main (retour Issa) : prêt à accueillir un futur `VectorDrawable`/SVG sans changer l'appelant. */
@Composable
private fun BrandIcon(icon: ImageVector, size: Dp, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
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
private fun PageIndicators(pageCount: Int, currentPage: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
