package com.inktone.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Barre de niveau d'InkTone — rail arrondi **sans pouce visible**, que l'on
 * saisit n'importe où sur sa surface.
 *
 * Réservée aux valeurs continues dont le chiffre n'apprend rien au lecteur :
 * en pratique la **luminosité**, où seul le résultat à l'écran compte. Pour
 * tout réglage dont la valeur se lit et se compare — taille de texte,
 * interligne, vitesse —, utiliser [InkToneSlider], qui affiche ce chiffre.
 *
 * ## Pourquoi une forme distincte plutôt qu'un curseur de plus
 *
 * C'est la forme d'Apple Books et de la luminosité iOS, et elle est ici un
 * choix d'ergonomie avant d'être un choix d'esthétique : ce réglage se
 * manipule au pouce, en pleine page, souvent dans le noir. Une cible de 32 dp
 * de haut sur toute la largeur se trouve sans regarder, là où il faut viser le
 * pouce d'un curseur. L'absence de pouce évite aussi d'afficher un repère de
 * précision pour une grandeur qui ne se règle qu'à l'œil.
 *
 * Le rendu ne dépend d'aucun état interne : [value] reste la seule source de
 * vérité, ce composant ne fait que la lire et signaler les gestes.
 *
 * @param value niveau courant, entre `0f` et `1f`.
 * @param onValueChange appelé pendant le geste, valeur déjà bornée à `0f..1f`.
 * @param contentDescription annoncé par TalkBack — un rail sans libellé propre
 *   n'annoncerait rien du tout.
 */
@Composable
fun InkToneLevelBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val level = value.coerceIn(0f, 1f)
    // Le geste est capté hors composition : `rememberUpdatedState` évite de
    // relancer le `pointerInput` (et donc d'interrompre un glissement en
    // cours) à chaque nouvelle valeur remontée par l'appelant.
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var widthPx by remember { mutableFloatStateOf(0f) }

    fun report(x: Float) {
        if (widthPx <= 0f) return
        currentOnValueChange((x / widthPx).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                widthPx = placeable.width.toFloat()
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> report(offset.x) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> report(change.position.x) }
            }
            .progressSemantics(level)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(level)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Hauteur du rail : au-delà de la cible tactile de 48 dp en largeur, c'est
 * cette épaisseur qui rend la barre saisissable sans viser.
 */
private val BAR_HEIGHT = 32.dp
