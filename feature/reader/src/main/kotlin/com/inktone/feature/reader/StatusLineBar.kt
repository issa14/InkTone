package com.inktone.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ligne de statut persistante (Tâche 3b.4) — hors HUD, visible en
 * permanence y compris panneau masqué : c'est `ImmersiveReaderChrome`
 * qui gère l'auto-masquage du panneau/barre du haut, cette barre-ci
 * n'y est jamais soumise (appelée en dehors de ce mécanisme dans
 * `ReaderScreen`).
 *
 * Portée du compteur de page : page **dans le chapitre courant**
 * (`Chapitre 3 (12/47)` = page 12 sur 47 du chapitre 3) — lecture la
 * plus naturelle du format cible, non explicitée dans le document ; à
 * confirmer à la vérification device (point 5 de la checklist du lot).
 */
@Composable
fun StatusLineBar(
    chapterNumber: Int,
    pageInChapter: Int,
    pageCountInChapter: Int,
    bookProgression: Float,
    modifier: Modifier = Modifier,
    showPageCounter: Boolean = true,
) {
    val timeText by rememberAlignedClockText()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(timeText, style = MaterialTheme.typography.labelSmall)
        Text(
            chapterCounterText(chapterNumber, pageInChapter, pageCountInChapter, showPageCounter),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(formatProgressionFr(bookProgression), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Texte du compteur de chapitre de la ligne de statut. Sans compteur
 * (`showPageCounter = false`, mesure de pagination encore partielle),
 * seul le numéro de chapitre est affiché : jamais un total partiel
 * présenté comme final (« Chapitre 12 (54/54) » alors que le chapitre
 * continue — voir NOTE_REGRESSION_CLIGNOTEMENT_PAGE_HUD.md et la cause
 * racine de `pageIndexAt` sur mesure partielle).
 */
internal fun chapterCounterText(
    chapterNumber: Int,
    pageInChapter: Int,
    pageCountInChapter: Int,
    showPageCounter: Boolean,
): String = if (showPageCounter) {
    "Chapitre $chapterNumber ($pageInChapter/$pageCountInChapter)"
} else {
    "Chapitre $chapterNumber"
}

/**
 * Heure locale, format 24 h, cadencée à la minute pleine (pas un délai
 * de 60 s depuis l'ouverture de l'écran, qui afficherait une heure en
 * retard de 0 à 59 s). Se réaligne au retour de veille
 * (`Lifecycle.Event.ON_RESUME`) : sans ça, un lecteur laissé en
 * arrière-plan puis repris afficherait une heure figée jusqu'au tick
 * suivant — suivre le cycle de vie plutôt que supposer que la coroutine
 * a continué.
 */
@Composable
private fun rememberAlignedClockText(): State<String> {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val text = remember { mutableStateOf(formatClock24h(System.currentTimeMillis())) }
    LaunchedEffect(resumeTick) {
        text.value = formatClock24h(System.currentTimeMillis())
        delay(alignedDelayToNextMinuteMillis(System.currentTimeMillis()))
        while (true) {
            text.value = formatClock24h(System.currentTimeMillis())
            delay(60_000)
        }
    }
    return text
}

private val clockFormatter = SimpleDateFormat("HH:mm", Locale.FRANCE)

private fun formatClock24h(epochMillis: Long): String = clockFormatter.format(epochMillis)

/**
 * Délai jusqu'à la prochaine minute pleine — extrait en fonction pure
 * pour être testée sans horloge réelle (3b.7, test 9) : le piège est un
 * délai fixe de `60_000` depuis l'ouverture de l'écran, qui affiche une
 * heure en retard de 0 à 59 s sur l'horloge système.
 */
internal fun alignedDelayToNextMinuteMillis(nowMillis: Long): Long = 60_000 - nowMillis % 60_000

/** Une décimale, virgule française — jamais `34.7%` (point), jamais `34,70%` (deux décimales). */
internal fun formatProgressionFr(progression: Float): String {
    val tenthsOfPercent = (progression * 1000).roundToInt().coerceAtLeast(0)
    val whole = tenthsOfPercent / 10
    val decimal = tenthsOfPercent % 10
    return "$whole,$decimal%"
}
