package com.inktone.feature.library

import com.inktone.core.designsystem.InkToneSpacing

import com.inktone.core.designsystem.emphasized
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.formatPercent
import com.inktone.domain.model.Publication
import java.io.File

/**
 * Carte de couverture de bibliothèque — image réelle (Coil) ou dégradé
 * de repli par hash du titre, badge de progression, menu d'actions.
 *
 * Pattern legacy porté et amélioré :
 * - Couverture via Coil (`AsyncImage` + `crossfade`) depuis `coverUri`
 * - Repli : dégradé déterministe basé sur `title.hashCode()`
 * - Badge de progression : cercle semi-transparent, pourcentage ≥1%
 *   (jamais 0% si progression > 0)
 * - Menu 3-points : icône `MoreVert` blanche, simple shadow (plus de
 *   fond circulaire — retour UX, trop lourd visuellement)
 * - Favori : badge cœur plein affiché UNIQUEMENT quand le livre est
 *   favori (coin supérieur droit) ; la bascule vit dans le menu
 *   3-points (« Ajouter aux favoris » / « Retirer des favoris »)
 * - Accessibilité : `contentDescription` sur le badge et le menu
 *
 * @param progressPercent  0..100, calculé par l'appelant à partir de
 *   [ReadingState]. Si 0, le badge est masqué.
 * @param showTitle  affiche le titre sous la couverture (désactivé
 *   en mode GRID_COVERS_ONLY)
 * @param showOverlays  menu 3-points superposé à la couverture —
 *   désactivé pour la miniature du mode Liste
 *   ([PublicationListRow] porte ses propres contrôles, lot 2b.4).
 */
@Composable
fun BookCover(
    publication: Publication,
    // `null` quand la couverture est une simple vignette d'une ligne cliquable :
    // un `clickable` vide consommait l'appui, la ligne ne le recevait jamais.
    onClick: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    progressPercent: Int = 0,
    // Livre entamé : sa progression peut être inconnue (longueurs de
    // chapitres pas encore calculées) sans qu'il soit « non commencé ».
    isStarted: Boolean = progressPercent > 0,
    showTitle: Boolean = true,
    showOverlays: Boolean = true,
    onTogglePin: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var showActionsSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // E.3 — Support content:// URI (SAF) en plus de file://
    //
    // AUDIT_REACTIVITE_UX §4.6 — `File(...).exists()` était évalué en
    // composition, donc un `stat()` disque à CHAQUE composition de chaque
    // couverture visible. Le fichier est maintenant passé sans vérifier
    // son existence : Coil échoue silencieusement la requête si absent,
    // et le dégradé de repli ([CoverPlaceholder]) reste visible dessous
    // (voir §4.7) — même résultat visuel, sans I/O sur le thread de rendu.
    val coverModel: Any? = when {
        publication.coverUri == null -> null
        publication.coverUri!!.startsWith("content://") -> Uri.parse(publication.coverUri)
        else -> File(publication.coverUri!!)
    }

    // E.2 — contentDescription global pour TalkBack
    val a11yLabel = buildString {
        append(publication.title)
        append(", ")
        append(
            when {
                progressPercent > 0 -> "$progressPercent% lu"
                isStarted -> "Commencé"
                else -> "Non commencé"
            },
        )
        if (publication.isPinned) append(", épinglé")
    }

    // Aucune transition partagée couverture ↔ Lecteur (retirée) : la
    // couverture et l'écran de lecture ne sont pas le même contenu, le
    // morphing étirait la vignette en plein écran — le « bloc coloré » vu à
    // la fermeture d'un livre. La navigation porte une transition unique
    // (`InkToneNavHost`).
    Box(
        modifier = modifier
            .aspectRatio(0.7f)
            .semantics { contentDescription = a11yLabel }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (coverModel != null) {
            // AUDIT_REACTIVITE_UX §4.7 — `SubcomposeAsyncImage` subcompose
            // ses emplacements `loading`/`error`, coût payé par vignette ;
            // ici les deux affichaient de toute façon le même
            // `CoverPlaceholder`, qui n'achetait rien. Le dégradé de repli
            // reste dessous en permanence (chargement ET erreur, y compris
            // le fichier absent de §4.6 ci-dessus) ; `AsyncImage` le
            // recouvre seulement une fois l'image effectivement décodée.
            CoverPlaceholder(title = publication.title, showTitle = !showTitle)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverModel)
                    .crossfade(true)
                    .build(),
                // Le titre est déjà porté par la sémantique de la couverture.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CoverPlaceholder(title = publication.title, showTitle = !showTitle)
        }

        // Badge de progression — coin inférieur droit
        // Sans surimpressions (miniature de la carte « Reprendre », qui affiche
        // déjà sa barre), pas de badge non plus.
        if (showOverlays && progressPercent > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    // Pastille qui s'élargit avec son texte : un disque fixe de 32 dp
                    // laissait déborder « 100 % » à grande police (audit UI P2-6).
                    .defaultMinSize(minWidth = 32.dp, minHeight = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp)
                    .semantics { contentDescription = "Progression ${formatPercent(progressPercent)}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatPercent(progressPercent),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.emphasized(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Menu 3-points — coin inférieur gauche, ouvre le popup d'actions.
        // Disque sombre sous l'icône : une ombre seule laissait le glyphe blanc
        // invisible sur une couverture claire (audit UI P2-6).
        if (showOverlays) {
            // Zone tactile de 48 dp (audit UX C3), mais icône calée dans le coin :
            // centrée dans la zone, le glyphe fin flottait à 24 dp du bord.
            Box(
                contentAlignment = Alignment.BottomStart,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 20.dp),
                        role = Role.Button,
                        onClick = { showActionsSheet = true },
                    ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(start = InkToneSpacing.xs, bottom = InkToneSpacing.xs)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    AppIcon(
                        AppSymbol.MoreActions,
                        contentDescription = "Actions sur « ${publication.title} »",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Témoin d'épinglage — coin supérieur gauche. Un livre épinglé
        // remonte en tête quel que soit le tri ; sans témoin, rien
        // n'expliquait pourquoi il échappait à l'ordre choisi.
        if (showOverlays && publication.isPinned) {
            AppIcon(
                symbol = AppSymbol.Pin,
                contentDescription = null, // porté par le libellé global de la couverture
                selected = true,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        clip = false,
                        spotColor = Color.Black.copy(alpha = 0.7f),
                    ),
            )
        }

        // Badge favori — coin supérieur droit, visible et plein UNIQUEMENT
        // quand le livre est favori. Indicateur non interactif (la bascule
        // vit dans le menu 3-points) : cœur violet de marque « Deadly
        // Depths » (colorScheme.primary), sans fond.
        if (showOverlays && publication.isFavorite) {
            AppIcon(
                symbol = AppSymbol.Favorite,
                contentDescription = "Favori",
                selected = true,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        clip = false,
                        spotColor = Color.Black.copy(alpha = 0.7f),
                    ),
            )
        }

        // Titre (optionnel — masqué en GRID_COVERS_ONLY)
        if (showTitle) {
            Text(
                text = publication.title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(InkToneSpacing.sm),
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (showActionsSheet) {
        BookActionsSheet(
            publication = publication,
            onDismiss = { showActionsSheet = false },
            onToggleFavorite = onToggleFavorite,
            onTogglePin = onTogglePin,
            onShowDetails = { showDetailsSheet = true },
            onRequestDelete = { showDeleteConfirm = true },
        )
    }
    if (showDetailsSheet) {
        BookDetailsSheet(publication = publication, onDismiss = { showDetailsSheet = false })
    }
    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            publicationTitle = publication.title,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * Placeholder affiché pendant le chargement Coil ou quand aucune
 * couverture n'est disponible. Dégradé déterministe basé sur le hash
 * du titre — deux livres au même titre auront le même dégradé.
 *
 * [showTitle] contrôle le titre centré : masqué quand [BookCover] affiche
 * déjà son propre libellé en bas, pour éviter la superposition des deux
 * textes.
 */
@Composable
private fun CoverPlaceholder(title: String, showTitle: Boolean) {
    val gradient = rememberCoverGradient(title)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradient)
            // Voile vers le bas : le titre blanc reste lisible sur les teintes claires (audit UI P2-6).
            .background(brush = PlaceholderVeil),
        contentAlignment = Alignment.Center,
    ) {
        if (showTitle) {
            Text(
                text = title,
                modifier = Modifier.padding(InkToneSpacing.sm),
                color = Color.White,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// AUDIT_REACTIVITE_UX §4.8 — hissée hors composition : la liste (8 paires
// de Color) était réallouée à chaque composition de rememberCoverGradient,
// malgré son nom, pour CHAQUE vignette sans couverture.
private val CoverGradientPalette = listOf(
    Color(0xFF1565C0) to Color(0xFF1E88E5), // Bleu
    Color(0xFF6A1B9A) to Color(0xFF8E24AA), // Violet
    Color(0xFF1B5E20) to Color(0xFF2E7D32), // Vert
    Color(0xFFBF360C) to Color(0xFFE65100), // Orange
    Color(0xFFC62828) to Color(0xFFE53935), // Rouge
    Color(0xFF00695C) to Color(0xFF00897B), // Teal
    Color(0xFF283593) to Color(0xFF3949AB), // Indigo
    Color(0xFF4E342E) to Color(0xFF6D4C41), // Brun
)

private val PlaceholderVeil = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))

/**
 * Dégradé déterministe basé sur le hash du titre — 3 couleurs parmi
 * 8 prédéfinies, sélectionnées par [title.hashCode()].
 */
@Composable
private fun rememberCoverGradient(title: String): Brush = remember(title) {
    val pair = CoverGradientPalette[title.hashCode().mod(CoverGradientPalette.size)]
    Brush.verticalGradient(listOf(pair.first, pair.second))
}

