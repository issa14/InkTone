package com.inktone.feature.library

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.reducedMotionDuration

/**
 * Tache 9bis.4 — pendant que les couvertures se chargent, rectangles
 * animes plutot qu'un simple spinner : perception de rapidite, standard
 * des apps premium actuelles (Spotify, Kindle), absent du legacy.
 */
@Composable
fun BookCoverShimmer() {
    val shimmerAlpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(reducedMotionDuration(800)), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Box(
        Modifier
            .padding(8.dp)
            .aspectRatio(0.7f)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)),
    )
}

@Composable
fun LibraryShimmerGrid() {
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 120.dp)) {
        items(8) { BookCoverShimmer() }
    }
}
