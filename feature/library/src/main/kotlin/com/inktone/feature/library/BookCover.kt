package com.inktone.feature.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.inktone.domain.model.Publication
import java.io.File

/**
 * Carte de couverture de bibliothèque — image réelle (Coil) ou dégradé
 * de repli par hash du titre, badge de progression, favori.
 *
 * Pattern legacy porté et amélioré :
 * - Couverture via Coil (`AsyncImage` + `crossfade`) depuis `coverUri`
 * - Repli : dégradé déterministe basé sur `title.hashCode()`
 * - Badge de progression : cercle semi-transparent, pourcentage ≥1%
 *   (jamais 0% si progression > 0)
 * - Favori : étoile en haut à droite
 * - Accessibilité : `contentDescription` sur le badge
 *
 * @param progressPercent  0..100, calculé par l'appelant à partir de
 *   [ReadingState]. Si 0, le badge est masqué.
 * @param showTitle  affiche le titre sous la couverture (désactivé
 *   en mode GRID_COVERS_ONLY)
 */
@Composable
fun BookCover(
    publication: Publication,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    progressPercent: Int = 0,
    showTitle: Boolean = true,
) {
    val context = LocalContext.current
    // E.3 — Support content:// URI (SAF) en plus de file://
    val coverModel: Any? = when {
        publication.coverUri == null -> null
        publication.coverUri!!.startsWith("content://") -> Uri.parse(publication.coverUri)
        else -> File(publication.coverUri!!).takeIf { it.exists() }
    }

    // E.2 — contentDescription global pour TalkBack
    val a11yLabel = "${publication.title}, ${if (progressPercent > 0) "$progressPercent% lu" else "Non commencé"}"

    Box(
        modifier = modifier
            .aspectRatio(0.7f)
            .semantics { contentDescription = a11yLabel }
            .clickable(onClick = onClick),
    ) {
        if (coverModel != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverModel)
                    .crossfade(true)
                    .build(),
                contentDescription = publication.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    CoverPlaceholder(title = publication.title)
                },
                error = {
                    CoverPlaceholder(title = publication.title)
                },
            )
        } else {
            CoverPlaceholder(title = publication.title)
        }

        // Bouton favori — coin supérieur droit
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                if (publication.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (publication.isFavorite)
                    "Retirer des favoris"
                else
                    "Ajouter aux favoris",
                tint = if (publication.isFavorite)
                    Color(0xFFFFC107) // Ambre — pas de rouge (rouge = suppression)
                else
                    Color.White.copy(alpha = 0.85f),
            )
        }

        // Badge de progression — coin inférieur droit
        if (progressPercent > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .semantics { contentDescription = "Progression $progressPercent%" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$progressPercent%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // #6 Dots décoratifs — coin inférieur gauche (legacy)
        DecorativeDots(Modifier.align(Alignment.BottomStart).padding(6.dp))

        // Titre (optionnel — masqué en GRID_COVERS_ONLY)
        if (showTitle) {
            Text(
                text = publication.title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Placeholder affiché pendant le chargement Coil ou quand aucune
 * couverture n'est disponible. Dégradé déterministe basé sur le hash
 * du titre — deux livres au même titre auront le même dégradé.
 */
@Composable
private fun CoverPlaceholder(title: String) {
    val gradient = rememberCoverGradient(title)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(8.dp),
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Dégradé déterministe basé sur le hash du titre — 3 couleurs parmi
 * 8 prédéfinies, sélectionnées par [title.hashCode()].
 */
@Composable
private fun rememberCoverGradient(title: String): Brush {
    val colors = listOf(
        Color(0xFF1565C0) to Color(0xFF1E88E5), // Bleu
        Color(0xFF6A1B9A) to Color(0xFF8E24AA), // Violet
        Color(0xFF2E7D32) to Color(0xFF43A047), // Vert
        Color(0xFFE65100) to Color(0xFFF57C00), // Orange
        Color(0xFFC62828) to Color(0xFFE53935), // Rouge
        Color(0xFF00695C) to Color(0xFF00897B), // Teal
        Color(0xFF283593) to Color(0xFF3949AB), // Indigo
        Color(0xFF4E342E) to Color(0xFF6D4C41), // Brun
    )
    val pair = colors[title.hashCode().mod(colors.size)]
    return Brush.verticalGradient(listOf(pair.first, pair.second))
}

// ──── #6 Dots décoratifs (legacy) ────

@Composable
private fun DecorativeDots(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
        Spacer(Modifier.width(3.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
        Spacer(Modifier.width(3.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
    }
}
