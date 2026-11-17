package com.inktone.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * État vide d'un écran ou d'un onglet : icône, titre, message facultatif et
 * action facultative (audit UI DS-16). Défile quand le texte grossi ne tient
 * plus, et reste centré sinon.
 */
@Composable
fun InkToneEmptyState(
    icon: AppSymbol,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) {
    StateMessageLayout(
        icon = icon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = title,
        message = message,
        modifier = modifier,
        action = action,
    )
}

/**
 * État d'erreur pleine page (audit UI DS-17). Le message reste en
 * `onSurfaceVariant` : un long texte en couleur d'erreur se lit mal ; l'icône
 * suffit à signaler l'échec.
 */
@Composable
fun InkToneErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    StateMessageLayout(
        icon = AppSymbol.Error,
        iconTint = MaterialTheme.colorScheme.error,
        title = title,
        message = message,
        modifier = modifier,
        action = {
            Button(onClick = onRetry) { Text("Réessayer") }
            if (secondaryActionLabel != null) {
                TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
            }
        },
    )
}

@Composable
private fun StateMessageLayout(
    icon: AppSymbol,
    iconTint: Color,
    title: String?,
    message: String?,
    modifier: Modifier,
    action: (@Composable ColumnScope.() -> Unit)?,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Hauteur minimale = la zone visible : centré tant que tout tient,
        // défilable dès que le contenu la dépasse.
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else 0.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(72.dp))
                if (title != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (action != null) {
                    Spacer(Modifier.height(16.dp))
                    action()
                }
            }
        }
    }
}
