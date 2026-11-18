package com.inktone.feature.reader

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.inktone.core.designsystem.InkToneTextField

/**
 * Saisie d'un numéro de page pour un PDF, où le sommaire n'existe pas : un livre
 * de 400 pages ne se parcourait que page par page (audit UX B4). [onGo] reçoit
 * l'index (0 pour la première page).
 */
@Composable
fun GoToPageDialog(
    currentPage: Int,
    pageCount: Int,
    onGo: (pageIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(currentPage.toString()) }
    val pageIndex = parsePageInput(input, pageCount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aller à la page") },
        text = {
            OutlinedTextField(
                shape = InkToneTextField.shape,
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                isError = input.isNotEmpty() && pageIndex == null,
                supportingText = { Text("Entre 1 et $pageCount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = pageIndex != null, onClick = { pageIndex?.let(onGo) }) { Text("Aller") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Index de page (0 pour la première) d'un numéro saisi de 1 à [pageCount], `null` sinon. */
internal fun parsePageInput(input: String, pageCount: Int): Int? {
    val page = input.trim().toIntOrNull() ?: return null
    return if (page in 1..pageCount) page - 1 else null
}
