package com.inktone.feature.reader.rendering

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.inktone.domain.model.BookBlock
import com.inktone.domain.service.EpubResourceResolver

/**
 * Rendu d'un [BookBlock] — unité atomique du flux de lecture.
 *
 * ## Pont TTS ↔ UI — Algorithme O(log n)
 *
 * Quand le TTS signale un mot à l'offset global `charOffset` :
 * 1. Trouver le bloc contenant cet offset via recherche dichotomique
 *    sur `blocks.map { it.globalOffsetRange!!.start }`.
 * 2. `val localOffset = charOffset - block.globalOffsetRange!!.start`.
 * 3. Appliquer le surlignage dans le [BasicTextField] de CE bloc uniquement.
 *
 * @param block Le bloc à rendre.
 * @param baseTextStyle Style de texte de base (taille, police, couleur du thème).
 * @param resolver Résolveur de ressources EPUB (pour les images).
 * @param publicationId ID de la publication (pour les images).
 */
@Composable
fun BookBlockItem(
    block: BookBlock,
    baseTextStyle: TextStyle,
    resolver: EpubResourceResolver? = null,
    publicationId: String = "",
    modifier: Modifier = Modifier,
) {
    when (block) {
        is BookBlock.ParagraphBlock -> {
            val textStyle = BookBlockStyleMapper.textStyleFor(block, baseTextStyle)
            val annotated = remember(block.richText) {
                BookBlockStyleMapper.buildAnnotatedString(block.richText)
            }
            val textFieldValue = remember(annotated) { TextFieldValue(annotated) }
            BasicTextField(
                value = textFieldValue,
                onValueChange = {},
                readOnly = true,
                textStyle = textStyle,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .semantics { contentDescription = block.richText.plainText },
            )
        }

        is BookBlock.HeadingBlock -> {
            val textStyle = BookBlockStyleMapper.textStyleFor(block, baseTextStyle)
            Text(
                text = block.richText.plainText,
                style = textStyle,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
        }

        is BookBlock.ImageBlock -> {
            val imgWidth = block.intrinsicWidth
            val imgHeight = block.intrinsicHeight
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .then(
                        if (imgWidth != null && imgHeight != null) {
                            Modifier.size(
                                width = imgWidth.dp,
                                height = imgHeight.dp,
                            )
                        } else {
                            Modifier.heightIn(min = 100.dp, max = 300.dp)
                        },
                    )
                    .semantics {
                        block.alt?.let { contentDescription = it }
                    },
            ) {
                if (resolver != null && publicationId.isNotEmpty()) {
                    AsyncImage(
                        model = EpubImageKey(publicationId, block.href),
                        contentDescription = block.alt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // Placeholder si pas de resolver (fallback)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .semantics {
                                block.alt?.let { contentDescription = it }
                                    ?: run { contentDescription = "Image" }
                            },
                    )
                }
            }
        }

        is BookBlock.SeparatorBlock -> {
            HorizontalDivider(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics { invisibleToUser() },
                color = Color.Gray.copy(alpha = 0.3f),
            )
        }
    }
}
