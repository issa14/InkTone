package com.inktone.feature.library

import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
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
 * @param enableSharedTransition  participe à la transition partagée vers
 *   le Lecteur (clé `"cover-{id}"`). À passer à `false` pour toute
 *   couverture SECONDAIRE d'une publication déjà affichée ailleurs sur
 *   le même écran — voir la note sur l'unicité de la clé ci-dessous.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookCover(
    publication: Publication,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    progressPercent: Int = 0,
    showTitle: Boolean = true,
    showOverlays: Boolean = true,
    onTogglePin: () -> Unit = {},
    onDelete: () -> Unit = {},
    enableSharedTransition: Boolean = true,
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
    val a11yLabel = "${publication.title}, ${if (progressPercent > 0) "$progressPercent% lu" else "Non commencé"}"

    // C.5 — SharedTransition couverture vers Reader.
    //
    // Bug réel trouvé sur appareil : la clé `"cover-{id}"` doit être
    // UNIQUE parmi les composables vivants d'un même écran. La carte
    // « Reprendre la lecture » affiche la publication la plus récemment
    // ouverte, qui reste AUSSI présente dans la grille/liste en dessous
    // (`resumeReadingPublication` dérive de `publications` sans l'en
    // retirer) : depuis que cette carte porte une couverture, deux
    // `BookCover` revendiquaient donc la même clé simultanément.
    // Compose ne peut pas départager deux revendications identiques — il
    // en élit une et l'autre n'est plus dessinée du tout, laissant dans
    // la grille un emplacement VIDE qui occupe pourtant sa place et
    // reste cliquable (symptôme exact remonté). `enableSharedTransition`
    // laisse l'instance canonique (grille/liste) seule propriétaire de
    // la clé ; toute couverture secondaire du même livre s'en abstient.
    val sharedTransitionScope = runCatching {
        com.inktone.core.designsystem.LocalSharedTransitionScope.current
    }.getOrNull()
    val animatedVisibilityScope = runCatching {
        com.inktone.core.designsystem.LocalAnimatedVisibilityScope.current
    }.getOrNull()
    val sharedElementMod = if (enableSharedTransition && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "cover-${publication.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .aspectRatio(0.7f)
            .semantics { contentDescription = a11yLabel }
            .then(sharedElementMod)
            .clickable(onClick = onClick),
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
                contentDescription = publication.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CoverPlaceholder(title = publication.title, showTitle = !showTitle)
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

        // Menu 3-points — coin inférieur gauche, ouvre le popup d'actions
        // (lot 2b.3 : remplace les points décoratifs sans logique).
        // Retour UX : plus de fond circulaire — icône blanche seule,
        // une shadow légère assure le contraste sur couverture claire.
        if (showOverlays) {
            IconButton(
                onClick = { showActionsSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(32.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        clip = false,
                        spotColor = Color.Black.copy(alpha = 0.7f),
                    ),
            ) {
                AppIcon(AppSymbol.MoreActions,  contentDescription = "Actions sur « ${publication.title} »", tint = Color.White)
            }
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
                    .padding(8.dp),
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
            .background(brush = gradient),
        contentAlignment = Alignment.Center,
    ) {
        if (showTitle) {
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
}

// AUDIT_REACTIVITE_UX §4.8 — hissée hors composition : la liste (8 paires
// de Color) était réallouée à chaque composition de rememberCoverGradient,
// malgré son nom, pour CHAQUE vignette sans couverture.
private val CoverGradientPalette = listOf(
    Color(0xFF1565C0) to Color(0xFF1E88E5), // Bleu
    Color(0xFF6A1B9A) to Color(0xFF8E24AA), // Violet
    Color(0xFF2E7D32) to Color(0xFF43A047), // Vert
    Color(0xFFE65100) to Color(0xFFF57C00), // Orange
    Color(0xFFC62828) to Color(0xFFE53935), // Rouge
    Color(0xFF00695C) to Color(0xFF00897B), // Teal
    Color(0xFF283593) to Color(0xFF3949AB), // Indigo
    Color(0xFF4E342E) to Color(0xFF6D4C41), // Brun
)

/**
 * Dégradé déterministe basé sur le hash du titre — 3 couleurs parmi
 * 8 prédéfinies, sélectionnées par [title.hashCode()].
 */
@Composable
private fun rememberCoverGradient(title: String): Brush = remember(title) {
    val pair = CoverGradientPalette[title.hashCode().mod(CoverGradientPalette.size)]
    Brush.verticalGradient(listOf(pair.first, pair.second))
}

