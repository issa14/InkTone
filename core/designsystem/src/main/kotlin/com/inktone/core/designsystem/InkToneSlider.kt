package com.inktone.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Curseur de réglage d'InkTone — forme unique de toutes les valeurs
 * ajustables où **le chiffre compte** : taille de texte, interligne, vitesse
 * et intonation de la voix, volume.
 *
 * ## Pourquoi ne pas utiliser le `Slider` Material 3 tel quel
 *
 * Depuis Material3 1.3, le pouce par défaut est une barre-pilule de 4×44 dp.
 * Massive, elle domine visuellement le réglage qu'elle sert et donne à un
 * panneau de lecture l'aspect d'un panneau système. La variante « Expressive »
 * de 1.4, à piste ondulée, va plus loin encore dans la direction opposée à
 * celle d'une application de lecture.
 *
 * Ici : piste fine de 4 dp, partie inactive discrète, pouce ramené à un petit
 * cercle plein de 20 dp. Le cercle est centré dans une cible tactile de 48 dp —
 * **la zone de toucher ne rétrécit jamais avec le visuel**, sans quoi alléger
 * le pouce reviendrait à rendre le curseur plus difficile à saisir.
 *
 * ## Accessibilité
 *
 * Un `Slider` seul n'annonce à TalkBack ni son libellé ni sa valeur (acquis de
 * la Tâche 9.1.1) : le [contentDescription] les porte explicitement, formatés
 * par [displayFormatter] — le même texte que celui affiché à l'écran, jamais
 * une valeur brute divergente.
 *
 * Pour une valeur continue dont le chiffre n'apprend rien au lecteur — la
 * luminosité —, utiliser [InkToneLevelBar] plutôt que ce curseur.
 *
 * @param label libellé du réglage, aligné à gauche.
 * @param value valeur courante.
 * @param range bornes du réglage.
 * @param onValueChange appelé à chaque déplacement.
 * @param onValueChangeFinished appelé à la fin du geste — à utiliser quand la
 *   valeur est coûteuse à appliquer (repagination) et ne doit l'être qu'une
 *   fois le doigt levé.
 * @param minIcon repère facultatif à gauche de la piste (valeur minimale).
 * @param maxIcon repère facultatif à droite de la piste (valeur maximale).
 * @param steps nombre de crans intermédiaires ; `0` pour un curseur continu.
 * @param displayFormatter rendu de la valeur, à droite du libellé.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkToneSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    minIcon: AppSymbol? = null,
    maxIcon: AppSymbol? = null,
    steps: Int = 0,
    displayFormatter: (Float) -> String = { it.toInt().toString() },
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = displayFormatter(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = InkToneSpacing.sm),
        ) {
            if (minIcon != null) {
                AppIcon(
                    minIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(TRACK_HEIGHT),
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        // Material3 1.3 découpe la piste autour du pouce
                        // (`thumbTrackGapSize`, 6 dp par défaut). Sur un pouce
                        // de 20 dp, cet évidement se lit comme un contour clair
                        // cerclant le point — un halo qu'aucun réglage ne
                        // justifie. La piste passe donc sous le pouce, sans
                        // interruption.
                        thumbTrackGapSize = 0.dp,
                        // Point plein posé au bout de la piste par défaut
                        // depuis la même version : un repère de plus à lire,
                        // pour une borne que les icônes indiquent déjà.
                        drawStopIndicator = null,
                    )
                },
                thumb = {
                    Box(
                        modifier = Modifier.size(TOUCH_TARGET),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(THUMB_DIAMETER)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TOUCH_TARGET)
                    .padding(horizontal = InkToneSpacing.md)
                    .semantics {
                        contentDescription = "$label, valeur ${displayFormatter(value)}"
                    },
            )
            if (maxIcon != null) {
                AppIcon(
                    maxIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Piste volontairement plus fine que les 16 dp de Material3 1.3. */
private val TRACK_HEIGHT = 4.dp

/** Cercle visible du pouce. */
private val THUMB_DIAMETER = 20.dp

/** Cible tactile minimale, indépendante de la taille du pouce visible. */
private val TOUCH_TARGET = 48.dp
