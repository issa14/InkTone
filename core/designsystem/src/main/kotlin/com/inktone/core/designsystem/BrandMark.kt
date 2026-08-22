package com.inktone.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource

/**
 * Marque InkTone — le glyphe du splash, réutilisable dans l'interface.
 *
 * L'asset ne porte QUE sa forme (alpha) : la couleur vient d'un
 * [ColorFilter], pas du fichier. C'est ce qui permet une seule copie là où
 * le splash en a deux (`drawable-nodpi` / `drawable-night-nodpi`, glyphe
 * noir et glyphe blanc).
 *
 * Ce choix n'est pas qu'une économie d'octets : les variantes `-night` sont
 * choisies par la configuration SYSTÈME, alors que le thème d'InkTone peut
 * être forcé en clair ou en sombre indépendamment d'elle
 * (`AppThemeMode.LIGHT`/`DARK`). Sur un système sombre avec l'app forcée en
 * clair, le qualificateur aurait servi le glyphe blanc sur une surface
 * claire — invisible. La teinte, elle, dérive du thème réellement résolu.
 *
 * Le splash garde ses deux variantes qualifiées : il s'affiche avant toute
 * composition, aucun `ColorFilter` n'est disponible à ce moment.
 */
@Composable
fun InkToneBrandMark(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Image(
        painter = painterResource(R.drawable.ic_brand_inktone),
        contentDescription = null, // décoratif : le nom « InkTone » est juste à côté
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}
