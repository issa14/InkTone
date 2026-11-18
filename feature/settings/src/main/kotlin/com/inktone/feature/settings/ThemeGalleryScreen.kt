package com.inktone.feature.settings

import com.inktone.core.designsystem.InkToneSectionHeader

import com.inktone.core.designsystem.InkToneSpacing
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.toColor
import com.inktone.domain.model.ReadingTheme

private const val PREVIEW_EXCERPT = "La lumière filtrait doucement à travers les persiennes…"

/**
 * Lot 9, Tâche 9.4 — Galerie de thèmes (UX §Galerie de thèmes). Pas de
 * bouton `+` en topbar : la création passe uniquement par la carte
 * pointillée de la Section 3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGalleryScreen(
    onBack: () -> Unit,
    onOpenStudio: (String?) -> Unit,
    viewModel: ThemeGalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ThemeGalleryEffect.NavigateToStudio -> onOpenStudio(effect.themeId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Galerie de thèmes", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppSymbol.Back, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = InkToneSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(InkToneSpacing.sm),
        ) {
            item {
                // Les vignettes partent de la marge d'écran : titre aligné sur elles.
                InkToneSectionHeader("Ambiances de lecture", startPadding = 0.dp)
            }
            items(state.ambiances.chunked(2)) { row ->
                ThemeCardRow(
                    themes = row,
                    activeId = state.activeThemeId,
                    onTap = { viewModel.onIntent(ThemeGalleryIntent.SelectTheme(it)) },
                )
            }

            item {
                InkToneSectionHeader("Confort et accessibilité", startPadding = 0.dp)
            }
            items(state.accessibility, key = { it.id }) { theme ->
                AccessibilityRow(
                    theme = theme,
                    isActive = theme.id == state.activeThemeId,
                    onClick = { viewModel.onIntent(ThemeGalleryIntent.SelectTheme(theme.id)) },
                )
            }

            item {
                InkToneSectionHeader("Mes thèmes personnalisés", startPadding = 0.dp)
            }
            items((listOf<ReadingTheme?>(null) + state.customThemes).chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.sm)) {
                    row.forEach { theme ->
                        if (theme == null) {
                            CreateThemeCard(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.onIntent(ThemeGalleryIntent.OpenStudio(null)) },
                            )
                        } else {
                            ThemeMiniPreviewCard(
                                theme = theme,
                                isActive = theme.id == state.activeThemeId,
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.onIntent(ThemeGalleryIntent.OpenStudio(theme.id)) },
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                    ) {
                                        AppIcon(AppSymbol.Edit,  contentDescription = "Modifier « ${theme.displayName} »", tint = theme.textColorHex.toColor())
                                    }
                                },
                                onClick = { viewModel.onIntent(ThemeGalleryIntent.SelectTheme(theme.id)) },
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item { Spacer(Modifier.height(InkToneSpacing.xl)) }
        }
    }

}

@Composable
private fun ThemeCardRow(
    themes: List<ReadingTheme>,
    activeId: String,
    onTap: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkToneSpacing.sm)) {
        themes.forEach { theme ->
            ThemeMiniPreviewCard(
                theme = theme,
                isActive = theme.id == activeId,
                modifier = Modifier.weight(1f),
                onClick = { onTap(theme.id) },
            )
        }
        if (themes.size == 1) Spacer(Modifier.weight(1f))
    }
}

/**
 * Carte-aperçu vivant (Section 1/3) : mini-page réelle rendue avec la
 * vraie police et les vraies couleurs du thème, pas une pastille de
 * couleur. Badge « Actif » plein (pas un contour) pour éviter la
 * confusion avec un survol.
 */
@Composable
private fun ThemeMiniPreviewCard(
    theme: ReadingTheme,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable BoxScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.backgroundColorHex.toColor())
            .selectable(selected = isActive, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Text(
                theme.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textColorHex.toColor(),
                fontFamily = theme.fontFamily.toComposeFontFamily(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                PREVIEW_EXCERPT,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textColorHex.toColor(),
                fontFamily = theme.fontFamily.toComposeFontFamily(),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("12", style = MaterialTheme.typography.labelSmall, color = theme.textColorHex.toColor().copy(alpha = 0.6f))
                Box(Modifier.size(10.dp).clip(CircleShape).background(theme.accentColorHex.toColor()))
            }
        }
        if (isActive) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 12.dp),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                // Casse normale : des capitales sont épelées par TalkBack (audit UI P2-11).
                Text(
                    "Actif",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = InkToneSpacing.sm, vertical = 3.dp),
                )
            }
        }
        trailingIcon?.let { it() }
    }
}

@Composable
private fun CreateThemeCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(0.72f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(AppSymbol.AddCircle,  contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(InkToneSpacing.sm))
            Text("Créer un thème", style = MaterialTheme.typography.labelMedium)
            Text("Studio de création", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Section 2 — format liste, délibérément différent des deux autres sections (réglages à activer, pas ambiances à comparer). */
@Composable
private fun AccessibilityRow(theme: ReadingTheme, isActive: Boolean, onClick: () -> Unit) {
    val subtitle = when (theme.id) {
        ReadingTheme.OPEN_DYSLEXIC_ESPACEMENT.id -> "Police adaptée aux troubles de la lecture"
        ReadingTheme.NOIR_ABSOLU_AMOLED.id -> "Contraste maximal et économie d'énergie"
        else -> null
    }
    // Ligne entière sélectionnable : un chevron disait « ouvrir », pas « appliquer » (audit UI P2-15).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (isActive) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else Modifier)
            .selectable(selected = isActive, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = InkToneSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.backgroundColorHex.toColor()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (theme.id == ReadingTheme.NOIR_ABSOLU_AMOLED.id) "OLED" else "Aa",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textColorHex.toColor(),
                fontFamily = theme.fontFamily.toComposeFontFamily(),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = InkToneSpacing.md)) {
            Text(theme.displayName, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (isActive) {
            AppIcon(AppSymbol.Success, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = InkToneSpacing.md))
        }
    }
}

