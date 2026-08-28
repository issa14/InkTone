package com.inktone.feature.reader.pagination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.sp
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.BookBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.intl.LocaleList

/**
 * Porteur d'état de pagination hoisté au-dessus du choix de mode de
 * rendu (Tâche 3b.1). Avant ce lot, la mesure et le
 * `VirtualPaginationEngine` vivaient **dans** `PagedChapterContent` — en
 * mode défilement, ce composable n'était jamais monté, donc aucune
 * pagination n'existait, alors que les pages virtuelles sont désormais
 * requises dans les deux modes (décision actée du lot 3b). Un seul
 * calcul, ici, sert à la fois la ligne de statut (3b.4, tous modes) et
 * le rendu du mode pagé — `PagedChapterContent` en devient
 * **consommateur**, il ne mesure plus lui-même.
 *
 * Recréé (`remember`) par `rememberChapterPaginationState` — pas
 * transmis par `ReaderIntent`/`ReaderViewModel` : `TextMeasurer` exige
 * `Density`/`FontFamily.Resolver`/`LayoutDirection`, tous liés à la
 * composition, il ne peut pas vivre dans le ViewModel. Un aller-retour
 * par le ViewModel créerait en plus une boucle état → recomposition →
 * mesure → intent → état à chaque changement de style.
 */
@Stable
class ChapterPaginationState internal constructor(
    internal val engine: VirtualPaginationEngine,
    val baseTextStyle: TextStyle,
) {
    var measurement by mutableStateOf<ChapterMeasurement?>(null)
        internal set

    // Incrémenté à chaque mise à jour effective du cache du moteur : lui
    // seul rend la pagination "observable" par Compose (le cache interne
    // du moteur est une simple Map mutable, sa mutation ne déclenche pas
    // de recomposition par elle-même). Lu (jamais écrit) par chaque
    // méthode ci-dessous pour s'abonner aux mises à jour.
    internal var paginationVersion by mutableIntStateOf(0)
        private set

    internal fun bumpIfChanged(changed: Boolean) {
        if (changed) paginationVersion++
    }

    fun pageCount(chapterIndex: Int): Int {
        paginationVersion
        return engine.pageCount(chapterIndex)
    }

    fun pageIndexAt(chapterIndex: Int, sentenceIndex: Int): Int {
        paginationVersion
        return engine.pageIndexAt(chapterIndex, sentenceIndex)
    }

    fun sentenceRangeOf(chapterIndex: Int, pageIndex: Int): IntRange {
        paginationVersion
        return engine.sentenceRangeOf(chapterIndex, pageIndex)
    }

    fun pageOffsetRange(chapterIndex: Int, pageIndex: Int): IntRange {
        paginationVersion
        return engine.pageOffsetRange(chapterIndex, pageIndex)
    }

    fun pageIndexAtOffset(chapterIndex: Int, charOffset: Int): Int {
        paginationVersion
        return engine.pageIndexAtOffset(chapterIndex, charOffset)
    }

    /**
     * Vrai si la mesure courante couvre TOUTES les phrases du chapitre
     * (mesure complète) — c'est-à-dire qu'aucune page n'est encore en
     * attente d'une mesure partielle. Tant que c'est faux, `pageCount` et
     * `pageIndexAt` peuvent refléter un préfixe borné (3a.3) : la ligne de
     * statut ne doit pas les présenter comme un total final, et l'ancrage
     * de position du mode pagé ne doit pas se recaler sur eux (sinon saut
     * vers `pages.lastIndex` d'une mesure partielle — régression « N/N » et
     * page noire documentée).
     */
    fun isMeasurementComplete(chapter: Chapter?): Boolean =
        isMeasurementComplete(
            measuredSentences = measurement?.sentenceStartOffsets?.size ?: 0,
            totalSentences = measurableOffsetCount(chapter),
        )
}

/**
 * Complétude d'une mesure de pagination : vraie dès que le nombre de
 * phrases mesurées couvre toutes les phrases du chapitre (ou qu'il n'y a
 * aucune phrase — chapitre vide, considéré complet). Fonction pure,
 * extraite pour être testée en JVM sans instancier [ChapterPaginationState].
 */
internal fun isMeasurementComplete(measuredSentences: Int, totalSentences: Int): Boolean =
    measuredSentences >= totalSentences

/**
 * Nombre d'offsets qu'une mesure COMPLÈTE du chapitre peut produire.
 *
 * Bug réel : la complétude se comparait au nombre total de phrases du chapitre,
 * alors que `ChapterTextMeasurer` ne mesure que les blocs de TEXTE. Une seule
 * phrase rattachée à un bloc image (légende) suffisait donc à rendre la mesure
 * éternellement « incomplète » — le compteur de pages disparaissait de la ligne
 * de statut et l'ancrage de position du mode paginé, qui n'agit que sur une
 * mesure complète, ne se recalait jamais.
 *
 * Reproduit ici EXACTEMENT la règle du mesureur (voir
 * `ChapterTextMeasurer.buildBatchAnnotatedString`) : un offset par phrase pour
 * un bloc de texte qui en possède, sinon un seul offset pour le bloc. Toute
 * divergence entre les deux ramènerait le défaut, d'où le test qui les compare
 * sur un même chapitre.
 */
internal fun measurableOffsetCount(chapter: Chapter?): Int {
    val blocks = (chapter?.content as? ChapterContent.Rich)?.blocks ?: return chapter?.sentences?.size ?: 0
    val sentencesByBlock = sentencesByBlockIndex(chapter.sentences)
    return blocks.withIndex()
        .filter { it.value is BookBlock.ParagraphBlock || it.value is BookBlock.HeadingBlock }
        .sumOf { (originalIndex, _) ->
            (sentencesByBlock[originalIndex]?.size ?: 0).coerceAtLeast(1)
        }
}

/**
 * Construit la clé d'invalidation à partir des valeurs **réellement
 * appliquées au rendu** (Tâche 3b.2) — jamais de constante déconnectée
 * du style effectif. Avant ce lot, `lineHeightSp`/`fontFamilyKey`
 * étaient alimentés en dur (`lineHeightSp = fontSizeSp`,
 * `fontFamilyKey = "default"`) : sans conséquence tant qu'aucun réglage
 * d'interligne/police n'existe, mais silencieusement périmé dès que le
 * panneau TT (lot 3c) les exposera — la pagination resterait basée sur
 * l'ancien style sans aucun signal. Lire ces deux valeurs depuis
 * `baseTextStyle` (au lieu de les fabriquer) rend ce défaut impossible
 * par construction : quand 3c fera varier `TextStyle.lineHeight`/
 * `fontFamily`, la clé variera automatiquement avec.
 */
fun paginationStyleKeyFrom(
    baseTextStyle: TextStyle,
    fontSizeSp: Int,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    paddingPx: Int,
    justified: Boolean = false,
): PaginationStyleKey {
    val lineHeightSp = if (baseTextStyle.lineHeight.isSp) baseTextStyle.lineHeight.value.toInt() else fontSizeSp
    val fontFamilyKey = baseTextStyle.fontFamily?.toString() ?: "default"
    return PaginationStyleKey(
        fontSizeSp = fontSizeSp,
        lineHeightSp = lineHeightSp,
        fontFamilyKey = fontFamilyKey,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        paddingPx = paddingPx,
        justified = justified,
    )
}

/**
 * Formule unique de hauteur utile pour les deux modes (Tâche 3b.1) :
 * `hauteurViewport − paddingHaut − paddingBas`, appliquée à la **même**
 * mesure (le `Box` partagé sous `ReaderScreen`, capturé une seule fois
 * au même endroit de l'arborescence pour scroll et pagé). La ligne de
 * statut persistante (3b.4) étant un sibling de ce `Box` dans la
 * `Column`, elle réduit déjà sa hauteur mesurée avant même que ce calcul
 * s'exécute — pas de soustraction supplémentaire à faire ici pour elle.
 */
@Composable
fun rememberChapterPaginationState(
    chapter: Chapter?,
    nextChapter: Chapter?,
    currentSentenceIndex: Int,
    fontSizeSp: Int,
    lineHeightSp: Int,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    paddingPx: Int,
    // Lot 9 — police effective (thème actif ou préférence explicite, voir
    // ThemeColors.effectiveFontFamily côté ReaderScreen). Fait
    // délibérément partie du style de MESURE : contrairement à la couleur
    // (3a.1, jamais ici), une police change la largeur du texte et donc
    // la pagination. `null` = police système par défaut.
    fontFamily: FontFamily? = null,
    // P4 — justification + césure. Fait partie du style de MESURE pour la même
    // raison que fontFamily : la césure déplace les points de coupure de ligne,
    // donc change la pagination. La justification seule ne la changerait pas
    // (elle ne fait qu'étirer les blancs), mais les deux vont ensemble par
    // construction (voir UserPreferences.textJustified) et le même TextStyle
    // sert au rendu — les séparer ferait diverger mesure et affichage.
    justified: Boolean = false,
): ChapterPaginationState {
    // Chaque mesure — y compris la première page depuis l'audit §4.3 —
    // s'exécute sur `Dispatchers.Default` avec un `TextMeasurer` dédié.
    // Le `TextMeasurer` de composition n'est PAS thread-safe : un mesureur
    // par mesure d'arrière-plan, à partir des mêmes paramètres de
    // résolution, garantit que deux effets qui se chevauchent (changement
    // de chapitre pendant qu'une mesure tourne encore) ne touchent jamais
    // le même cache depuis deux threads.
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val newBackgroundMeasurer = {
        ChapterTextMeasurer(TextMeasurer(fontFamilyResolver, density, layoutDirection))
    }
    val engine = remember { VirtualPaginationEngine() }
    // Aucune couleur ici : elle ne déplace jamais le texte (3a.1), donc
    // n'a pas sa place dans le style de MESURE. Chaque consommateur
    // (PagedChapterContent) applique sa propre couleur au rendu.
    // 3d.2 — lineHeight fait maintenant partie du style de MESURE réel
    // (au lieu d'être absent) : paginationStyleKeyFrom le lit directement
    // sur ce TextStyle, garde-fou posé en 3b.2 pour que changer
    // l'interligne redéclenche automatiquement la pagination. Lot 9 :
    // fontFamily rejoint ce même style pour la même raison — changer
    // d'ambiance dont la police diffère doit recalculer la pagination.
    val baseTextStyle = remember(fontSizeSp, lineHeightSp, fontFamily, justified) {
        TextStyle(
            fontSize = fontSizeSp.sp,
            lineHeight = lineHeightSp.sp,
            fontFamily = fontFamily,
            textAlign = if (justified) TextAlign.Justify else TextAlign.Unspecified,
            hyphens = if (justified) Hyphens.Auto else Hyphens.None,
            lineBreak = if (justified) LineBreak.Paragraph else LineBreak.Unspecified,
            // Lot 21 (correctif) — locale de césure explicite, mais
            // seulement quand `justified` : posée sans condition, elle
            // s'appliquait aussi hors justification, où elle n'a rien à
            // corriger (`Hyphens.None` ne césure pas) mais influence quand
            // même la sélection de glyphes et la coupure de ligne — un
            // élargissement de comportement non voulu pour tout contenu
            // non francophone. `Hyphens.Auto` sans locale se rabat sur la
            // locale SYSTÈME, pas nécessairement `fr` : sur un appareil
            // configuré en anglais, la césure d'un texte français justifié
            // coupe aux mauvais endroits. Constante `fr` (même choix que
            // FrenchSentenceSplitter) — `Publication.language` existe et
            // est persisté (Publication.kt, rempli à l'import), mais n'est
            // pas encore porté jusqu'à `ReaderUiState` : le brancher est
            // laissé pour quand la césure par langue sera mesurée
            // insuffisante avec `fr` seul. Si la locale devient variable,
            // l'ajouter aux clés du `remember` ET à
            // `paginationStyleKeyFrom` (elle déplace les points de coupure
            // de ligne, donc change la pagination).
            localeList = if (justified) LocaleList("fr") else null,
        )
    }
    val state = remember(engine, baseTextStyle) { ChapterPaginationState(engine, baseTextStyle) }

    val contentWidthPx = viewportWidthPx - paddingPx * 2
    val contentHeightPx = (viewportHeightPx - paddingPx * 2).coerceAtLeast(0)

    val styleKey = remember(baseTextStyle, fontSizeSp, viewportWidthPx, contentHeightPx, paddingPx, justified) {
        paginationStyleKeyFrom(
            baseTextStyle = baseTextStyle,
            fontSizeSp = fontSizeSp,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = contentHeightPx,
            paddingPx = paddingPx,
            justified = justified,
        )
    }

    val currentSentenceIndexAtOpen = rememberUpdatedState(currentSentenceIndex)

    LaunchedEffect(chapter?.index, styleKey) {
        if (chapter == null || contentWidthPx <= 0) return@LaunchedEffect
        val sentences = chapter.sentences
        val totalSentenceCount = sentences.size
        val targetSentenceIndex = currentSentenceIndexAtOpen.value

        // Première page : préfixe borné, coût indépendant de la longueur
        // du chapitre. Mesurée en arrière-plan comme le reste : la KDoc
        // historique (« assez rapide pour rester sur le thread de
        // composition ») n'est pas vérifiée par une mesure et devient
        // fausse dès que la justification + césure sont actives (audit
        // §4.3). Rejouée à chaque cran du curseur de police / rotation,
        // elle doit sortir du thread principal.
        var partial = withContext(Dispatchers.Default) {
            newBackgroundMeasurer().measureFirstPage(chapter, baseTextStyle, contentWidthPx)
        }
        state.measurement = partial
        state.bumpIfChanged(
            engine.updateChapter(chapter.index, styleKey, partial.lines, partial.sentenceStartOffsets, force = true),
        )

        // Reprise en milieu de chapitre : élargit le préfixe jusqu'à
        // couvrir la phrase visée (3a.3).
        var nextBudget = FIRST_PAGE_CHAR_BUDGET
        var widenings = 0
        while (
            targetSentenceIndex >= partial.sentenceStartOffsets.size &&
            partial.sentenceStartOffsets.size < totalSentenceCount &&
            widenings < MAX_PROGRESSIVE_WIDENINGS
        ) {
            nextBudget *= 2
            widenings++
            partial = withContext(Dispatchers.Default) {
                newBackgroundMeasurer().measureFirstPage(chapter, baseTextStyle, contentWidthPx, nextBudget)
            }
            state.measurement = partial
            state.bumpIfChanged(
                engine.updateChapter(chapter.index, styleKey, partial.lines, partial.sentenceStartOffsets, force = true),
            )
        }

        // Complète la pagination du reste du chapitre en arrière-plan —
        // sauf si l'élargissement ci-dessus a déjà tout couvert.
        val full = if (partial.sentenceStartOffsets.size >= totalSentenceCount) {
            partial
        } else {
            withContext(Dispatchers.Default) { newBackgroundMeasurer().measure(chapter, baseTextStyle, contentWidthPx) }
        }
        state.measurement = full
        state.bumpIfChanged(
            engine.updateChapter(chapter.index, styleKey, full.lines, full.sentenceStartOffsets, force = true),
        )

        // Préchargement du chapitre suivant (3a.3, évalué) : le chapitre
        // affiché a priorité (mesuré en premier, ci-dessus).
        if (nextChapter != null) {
            val nextMeasurement = withContext(Dispatchers.Default) {
                newBackgroundMeasurer().measure(nextChapter, baseTextStyle, contentWidthPx)
            }
            engine.updateChapter(nextChapter.index, styleKey, nextMeasurement.lines, nextMeasurement.sentenceStartOffsets)
        }
    }

    return state
}

/** Doit correspondre à la valeur par défaut de `ChapterTextMeasurer.measureFirstPage` — premier palier de la mesure en deux temps (3a.3). */
private const val FIRST_PAGE_CHAR_BUDGET = 6000

/** Nombre maximal de doublements du préfixe pour couvrir une reprise en milieu de chapitre (3a.3) — au-delà, on bascule sur la mesure complète sans attendre plus longtemps. */
private const val MAX_PROGRESSIVE_WIDENINGS = 4
