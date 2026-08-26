package com.inktone.feature.reader

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.RenderedPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import java.nio.IntBuffer

/**
 * Rendu d'un PDF page par page (Lot 12, Palier 2, tâche 12.8) — bitmap
 * PDFium affiché tel quel, jamais de reflow du texte sous-jacent
 * (ADR-017). [renderPage] est fourni par l'appelant (le ViewModel
 * enveloppe [com.inktone.domain.service.FixedPageDocument]) : ce
 * composant ne connaît jamais PDFium ni `infrastructure/parser`
 * directement (règle de dépendance, Blueprint §4.7).
 *
 * **Rendu en tuiles simplifié (écart déclaré vs la recherche initiale).**
 * La recherche envisageait un découpage en grille de tuiles indépendantes
 * pour la zone zoomée. Ce palier retient une version plus simple mais
 * réelle : au repos, la page est rendue à la largeur du viewport ; au-delà
 * d'un zoom soutenu (après un temps mort du geste), une seule
 * re-rasterisation à une résolution plus élevée remplace le bitmap —
 * bornée à [MAX_RENDER_SCALE] fois la largeur du viewport, jamais un
 * bitmap non borné qui risquerait un `OutOfMemoryError`. Un vrai
 * découpage en tuiles (rendu du seul rectangle visible à la demande)
 * reste une amélioration future si la netteté à très fort zoom s'avère
 * insuffisante en usage réel.
 */
@Composable
fun FixedPageContent(
    pageCount: Int,
    currentPageIndex: Int,
    onPageIndexChanged: (Int) -> Unit,
    onPageOffsetChanged: (Float) -> Unit,
    renderPage: suspend (pageIndex: Int, targetWidthPx: Int) -> RenderedPage?,
    invertColors: (pageIndex: Int) -> Boolean,
    reduceMotion: Boolean,
    // Bug reel trouve sur appareil (2026-08-26) — page noire definitive a
    // l'ouverture d'un PDF. `renderPage` rend `null` tant que le document
    // PDFium n'est pas ouvert, et l'ouverture se termine APRES la premiere
    // composition (voir ReaderUiState.isFixedPageReady). Sans cette cle,
    // l'effet de rendu ne repartait jamais : ses cles (page, viewport) ne
    // changeaient plus. Symptome revelateur observe a la verification
    // device : swiper vers la page suivante PUIS revenir affichait tout
    // correctement — changer `pageIndex` re-clait l'effet a la main.
    isRenderReady: Boolean,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 0) return

    val pagerState = rememberPagerState(initialPage = currentPageIndex.coerceIn(0, pageCount - 1)) { pageCount }

    // Lot 12, tache 12.8 — cache LruCache limite a 5 pages (active,
    // N-1, N+1, N-2, N+2) avec recyclage Bitmap.inBitmap sur les entrees
    // evincees pour eviter les a-coups du ramasse-miettes (decision
    // actee du plan). Cree UNE fois par FixedPageContent, partage entre
    // toutes les pages du HorizontalPager.
    // Lot 22, Palier C, tâche 8 — agrandi a 7 (N-3..N+3) pour laisser de
    // la marge au pre-rendu des voisins immediats ci-dessous sans les
    // evincer aussitot lors d'une navigation sequentielle.
    val bitmapCache = remember { BitmapCache(maxSize = 7) }

    // Lot 22, Palier C, tâche 8 — largeur de viewport de la page active,
    // remontee par FixedPage pour le pre-rendu des pages voisines
    // ci-dessous (toutes les pages du pager partagent la meme largeur).
    var activeViewportWidthPx by remember { mutableStateOf(0) }

    // Pre-rend les pages adjacentes a la page etablie (settledPage, pas
    // currentPage qui bouge des le franchissement du seuil de 50% —
    // attendre l'immobilite du geste evite de faire concurrence au rendu
    // de la page active sur le dispatcher JNI a thread unique). Supprime
    // le blanc au swipe (constat 5 du Lot 22) sans pre-synthese de fond.
    LaunchedEffect(pagerState.settledPage, activeViewportWidthPx, isRenderReady) {
        if (!isRenderReady || activeViewportWidthPx <= 0) return@LaunchedEffect
        val settled = pagerState.settledPage
        for (neighbor in listOf(settled - 1, settled + 1)) {
            if (neighbor !in 0 until pageCount) continue
            if (bitmapCache.get(neighbor) != null) continue
            val rendered = try {
                renderPage(neighbor, activeViewportWidthPx)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Echec du pre-rendu PDF de la page $neighbor", throwable)
                null
            } ?: continue
            bitmapCache.put(neighbor, bitmapCache.createBitmap(rendered))
        }
    }

    // Meme garde que le mode SCROLL/PAGED existant (isProgrammaticScroll,
    // voir ReaderScreen) : une navigation programmatique (table des
    // matieres, reprise de lecture, recherche) ne doit pas etre reprise
    // comme un geste utilisateur par l'effet symetrique ci-dessous.
    var isProgrammaticPageChange by remember { mutableStateOf(false) }

    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex && currentPageIndex in 0 until pageCount) {
            isProgrammaticPageChange = true
            try {
                if (reduceMotion) {
                    pagerState.scrollToPage(currentPageIndex)
                } else {
                    pagerState.animateScrollToPage(currentPageIndex)
                }
            } finally {
                isProgrammaticPageChange = false
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (!isProgrammaticPageChange && pagerState.currentPage != currentPageIndex) {
            onPageIndexChanged(pagerState.currentPage)
        }
    }

    HorizontalPager(state = pagerState, modifier = modifier.fillMaxSize()) { pageIndex ->
        FixedPage(
            pageIndex = pageIndex,
            isActivePage = pageIndex == pagerState.currentPage,
            renderPage = renderPage,
            invertColors = invertColors(pageIndex),
            onPageOffsetChanged = onPageOffsetChanged,
            bitmapCache = bitmapCache,
            isRenderReady = isRenderReady,
            onViewportWidthChanged = { activeViewportWidthPx = it },
        )
    }
}

@Composable
private fun FixedPage(
    pageIndex: Int,
    isActivePage: Boolean,
    renderPage: suspend (pageIndex: Int, targetWidthPx: Int) -> RenderedPage?,
    invertColors: Boolean,
    onPageOffsetChanged: (Float) -> Unit,
    bitmapCache: BitmapCache,
    isRenderReady: Boolean,
    onViewportWidthChanged: (Int) -> Unit,
) {
    var viewportSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Lot 22, Palier C, tâche 8 — seule la page active remonte sa largeur
    // (utilisee par FixedPageContent pour le pre-rendu des voisines) :
    // toutes les pages partagent la meme largeur de viewport, remonter
    // celle d'une page inactive serait redondant.
    LaunchedEffect(isActivePage, viewportSizePx) {
        if (isActivePage && viewportSizePx.width > 0) onViewportWidthChanged(viewportSizePx.width)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Une page qui redevient inactive (swipe vers une autre) perd son
    // zoom - sinon y revenir la retrouve figee au dernier facteur, un
    // etat surprenant pour l'utilisateur.
    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }
    // Multiplicateur de resolution deja "cuit" dans le bitmap charge -
    // distinct de `scale` (transformation GPU live du geste). Tant que
    // les deux ne coincident pas, le bitmap est affiche a `scale /
    // renderedScale` : net une fois la rasterisation haute definition
    // arrivee, deja reactif pendant le geste via graphicsLayer seul.
    var renderedScale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var bitmap by remember(pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    // Lot 21, tâche 8 — une page corrompue ou une OOM ne doit ni crasher
    // l'écran ni rester un écran noir muet : repli gracieux + journal.
    var renderFailed by remember(pageIndex) { mutableStateOf(false) }

    // Lot 12, tache 12.8 — LruCache partage (5 pages max) + recyclage
    // Bitmap.inBitmap : une page deja rendue est servie depuis le cache
    // sans nouvel appel JNI ; une page evincee du cache est conservee
    // dans un pool d'une entree pour etre reutilisee via
    // copyPixelsFromBuffer (inBitmap n'est pas applicable aux IntArray
    // de RenderedPage — pixels bruts, pas de decodage BitmapFactory).
    LaunchedEffect(pageIndex, viewportSizePx, isRenderReady) {
        // Reinitialise avant le garde : un ancien repli ne doit jamais
        // rester affiche si la page redevient simplement non prete
        // (document ferme puis rouvert), plutot que verite obsolete.
        renderFailed = false
        if (viewportSizePx.width <= 0 || !isRenderReady) return@LaunchedEffect

        val cached = bitmapCache.get(pageIndex)
        if (cached != null && cached.width == viewportSizePx.width) {
            bitmap = cached.asImageBitmap()
            renderedScale = 1f
            return@LaunchedEffect
        }

        // L'annulation de cet effet (changement de page, redimensionnement)
        // ne doit jamais etre journalisee comme un echec de rendu ni
        // afficher le repli : seul un VRAI echec (page corrompue, OOM)
        // doit declencher `renderFailed`.
        val rendered = try {
            renderPage(pageIndex, viewportSizePx.width)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Echec du rendu PDF de la page $pageIndex", throwable)
            null
        }
        if (rendered == null) {
            renderFailed = true
            return@LaunchedEffect
        }
        val bmp = bitmapCache.createBitmap(rendered)
        bitmapCache.put(pageIndex, bmp)
        bitmap = bmp.asImageBitmap()
        renderedScale = 1f
    }

    // Rasterisation haute definition au relachement du geste (debounce) -
    // jamais a chaque frame du pincement, qui saturerait le dispatcher
    // JNI a un seul thread (decision actee, tache 12.2/12.7).
    // Cette re-rasterisation n'est pas mise en cache (dimensions
    // differentes de celle au repos, usage ponctuel) — seul le rendu
    // standard a la largeur du viewport est conserve dans le LruCache.
    LaunchedEffect(pageIndex, scale) {
        if (scale <= 1.01f || viewportSizePx.width <= 0) return@LaunchedEffect
        delay(250)
        if (!isActive) return@LaunchedEffect
        val targetScale = scale.coerceAtMost(MAX_RENDER_SCALE)
        val targetWidthPx = (viewportSizePx.width * targetScale).roundToInt()
        // Lot 21, tâche 8 — même repli qu'au rendu standard : une OOM au
        // zoom ne doit pas faire tomber l'écran (la page au repos reste
        // affichée, le bitmap HD est simplement abandonné). L'annulation
        // de cet effet (relachement du geste, nouvelle page) n'est pas un
        // echec de rendu — voir le meme choix sur l'effet standard.
        val rendered = try {
            renderPage(pageIndex, targetWidthPx)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Echec du rendu PDF HD de la page $pageIndex", throwable)
            null
        } ?: return@LaunchedEffect
        val bmp = Bitmap.createBitmap(rendered.pixelsArgb, rendered.widthPx, rendered.heightPx, Bitmap.Config.ARGB_8888)
        bitmap = bmp.asImageBitmap()
        renderedScale = targetScale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSizePx = it }
            // Lot 12, tâche 12.8 — zoom/pinch. On ne consomme les
            // événements que si ≥2 pointeurs (pinch) ou page déjà zoomée.
            // Un swipe 1 doigt non zoomé traverse jusqu'au HorizontalPager
            // (la navigation entre pages fonctionne sans conflit).
            .pointerInput(pageIndex) {
                awaitEachGesture {
                    // Capture l'état initial des pointeurs pour calculer
                    // le zoom (ratio des distances) et le pan (centroïde).
                    var prevCentroid = Offset.Zero
                    var prevDistance = 0f
                    var gestureStarted = false
                    var isMultiTouch = false

                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        isMultiTouch = isMultiTouch || pressedCount >= 2
                        val isZoomedIn = scale > 1.01f

                        if (isMultiTouch || isZoomedIn) {
                            val currentCentroid = event.changes
                                .filter { it.pressed }
                                .map { it.position }
                                .let { positions ->
                                    if (positions.isEmpty()) Offset.Zero
                                    else Offset(
                                        positions.sumOf { it.x.toDouble() }.toFloat() / positions.size,
                                        positions.sumOf { it.y.toDouble() }.toFloat() / positions.size,
                                    )
                                }

                            val currentDistance = if (pressedCount >= 2) {
                                val pts = event.changes.filter { it.pressed }.map { it.position }
                                val (p1, p2) = pts[0] to pts[1]
                                (p1 - p2).getDistance()
                            } else {
                                0f
                            }

                            if (gestureStarted && prevDistance > 0f && currentDistance > 0f) {
                                val zoomChange = currentDistance / prevDistance
                                val panChange = currentCentroid - prevCentroid

                                val newScale = (scale * zoomChange).coerceIn(1f, MAX_GESTURE_SCALE)
                                scale = newScale
                                offsetY += panChange.y
                                if (newScale > 1f && viewportSizePx.height > 0) {
                                    val maxOffsetY = viewportSizePx.height * (newScale - 1f) / 2f
                                    offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
                                    onPageOffsetChanged(((offsetY + maxOffsetY) / (maxOffsetY * 2f)).coerceIn(0f, 1f))
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                    onPageOffsetChanged(0f)
                                }
                            }

                            prevCentroid = currentCentroid
                            prevDistance = currentDistance
                            gestureStarted = true
                            event.changes.forEach { it.consume() }
                        }
                        // Swipe 1 doigt non zoomé : on ne consomme pas →
                        // HorizontalPager reçoit le geste et navigue.
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            val displayScale = if (renderedScale > 0f) scale / renderedScale else scale
            Image(
                bitmap = currentBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = if (invertColors) invertedColorFilter else null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = displayScale
                        scaleY = displayScale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )
        } else if (renderFailed) {
            // Lot 21, tâche 8 — repli gracieux : la page ne se rend pas
            // (page corrompue, OOM). Placeholder explicite plutôt qu'un
            // écran noir muet ; le détail est dans le Log. Le fond du Box
            // parent est toujours noir (theme de lecture PDF) : sans son
            // propre fond, le repli resterait blanc-sur-noir même en
            // thème clair/sépia (`invertColors == false`) — même logique
            // que l'inversion appliquée à l'Image ci-dessus.
            val placeholderBackground = if (invertColors) Color.Black else Color.White
            val placeholderTextColor = if (invertColors) Color.White else Color.Black
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderBackground),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Page illisible",
                    color = placeholderTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Cette page du document ne peut pas être affichée.",
                    color = placeholderTextColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private const val TAG = "FixedPageContent"

private fun RenderedPage.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(pixelsArgb, widthPx, heightPx, Bitmap.Config.ARGB_8888).asImageBitmap()

// ColorMatrix d'inversion de luminance (theme sombre/sepia sur page
// vectorielle, tache 12.11) - fond noir, texte clair, sans toucher la
// teinte (canal alpha inchange).
private val invertedColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private const val MAX_RENDER_SCALE = 3f
private const val MAX_GESTURE_SCALE = 5f

/**
 * Cache de bitmaps PDF avec recyclage (Lot 12, tâche 12.8).
 *
 * **LruCache (5 pages max)** : les pages N-2, N-1, N (active), N+1, N+2
 * restent en mémoire pour une navigation séquentielle fluide. Au-delà,
 * la page la moins récemment utilisée est évincée.
 *
 * **Recyclage Bitmap** : `inBitmap` (BitmapFactory) n'est pas applicable
 * ici car [RenderedPage] transporte des pixels bruts (`IntArray`), pas
 * un flux encodé. À la place, le bitmap évincé est conservé dans un pool
 * d'une entrée et réutilisé via [Bitmap.copyPixelsFromBuffer] si ses
 * dimensions correspondent — une allocation de `IntBuffer` temporaire
 * (négligeable) remplace une allocation de `Bitmap` (coûteuse).
 */
private class BitmapCache(maxSize: Int) : LruCache<Int, Bitmap>(maxSize) {

    /** Pool d'un bitmap évincé, conservé pour recyclage. */
    private var spare: Bitmap? = null

    /**
     * Crée ou recycle un [Bitmap] depuis [page] rendue. Si un bitmap
     * de mêmes dimensions est disponible dans le pool, ses pixels sont
     * écrasés plutôt que de créer un nouvel objet — évite une allocation
     * native + GC pendant la navigation séquentielle.
     */
    fun createBitmap(page: RenderedPage): Bitmap {
        val reusable = spare
        if (reusable != null && !reusable.isRecycled &&
            reusable.width == page.widthPx && reusable.height == page.heightPx
        ) {
            spare = null
            reusable.copyPixelsFromBuffer(IntBuffer.wrap(page.pixelsArgb))
            return reusable
        }
        return Bitmap.createBitmap(page.pixelsArgb, page.widthPx, page.heightPx, Bitmap.Config.ARGB_8888)
    }

    override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
        if (evicted && !oldValue.isRecycled) {
            spare?.recycle()
            spare = oldValue
        }
    }
}
