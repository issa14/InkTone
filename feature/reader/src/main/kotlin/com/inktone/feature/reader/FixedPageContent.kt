package com.inktone.feature.reader

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.inktone.domain.model.RenderedPage
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
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 0) return

    val pagerState = rememberPagerState(initialPage = currentPageIndex.coerceIn(0, pageCount - 1)) { pageCount }

    // Lot 12, tache 12.8 — cache LruCache limite a 5 pages (active,
    // N-1, N+1, N-2, N+2) avec recyclage Bitmap.inBitmap sur les entrees
    // evincees pour eviter les a-coups du ramasse-miettes (decision
    // actee du plan). Cree UNE fois par FixedPageContent, partage entre
    // toutes les pages du HorizontalPager.
    val bitmapCache = remember { BitmapCache(maxSize = 5) }

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
) {
    var viewportSizePx by remember { mutableStateOf(IntSize.Zero) }

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

    // Lot 12, tache 12.8 — LruCache partage (5 pages max) + recyclage
    // Bitmap.inBitmap : une page deja rendue est servie depuis le cache
    // sans nouvel appel JNI ; une page evincee du cache est conservee
    // dans un pool d'une entree pour etre reutilisee via
    // copyPixelsFromBuffer (inBitmap n'est pas applicable aux IntArray
    // de RenderedPage — pixels bruts, pas de decodage BitmapFactory).
    LaunchedEffect(pageIndex, viewportSizePx) {
        if (viewportSizePx.width <= 0) return@LaunchedEffect

        val cached = bitmapCache.get(pageIndex)
        if (cached != null && cached.width == viewportSizePx.width) {
            bitmap = cached.asImageBitmap()
            renderedScale = 1f
            return@LaunchedEffect
        }

        val rendered = renderPage(pageIndex, viewportSizePx.width) ?: return@LaunchedEffect
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
        val rendered = renderPage(pageIndex, targetWidthPx) ?: return@LaunchedEffect
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
        }
    }
}

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
