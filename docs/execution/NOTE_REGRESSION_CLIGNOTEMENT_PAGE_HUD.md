# Note — Régression : clignotement de page en mode pagé lié au HUD

**Statut : diagnostiquée, non corrigée.** Trouvée sur appareil pendant la
vérification manuelle du lot 3c (point 1 : pourcentage figé en swipe).
Le correctif de ce point-là amplifie une fragilité préexistante de
l'ancrage de pagination — décrite ici, pas corrigée, en attente d'une
décision (voir Options ci-dessous).

## Symptôme

En mode pagé, après 3 à 4 swipes, le texte affiché clignote de façon
frénétique (sauts de page en rafale).

## Cause racine, tracée précisément

1. `readingAreaSize` (`ReaderScreen.kt`, `Box` de lecture) est mesuré sur
   une zone dont la hauteur dépend de `isHudVisible` — `ReaderTopBar` et
   `UnifiedControlPanel` sont montés/démontés selon ce booléen, dans le
   même `Column`, ce qui redistribue l'espace vertical de la zone de
   lecture (`Modifier.weight(1f)`) à chaque masquage/réapparition du HUD
   (auto-hide 4s, ou tap explicite).
2. `viewportHeightPx = readingAreaSize.height` entre dans
   `PaginationStyleKey` (`ChapterPaginationState.kt`,
   `paginationStyleKeyFrom`) — la clé du
   `LaunchedEffect(chapter?.index, styleKey)` dans
   `rememberChapterPaginationState`. Un changement de hauteur redéclenche
   donc une **remesure complète** de la pagination du chapitre courant.
3. Cette remesure écrit `ChapterPaginationState.measurement` **plusieurs
   fois de suite**, avec des données de plus en plus complètes (première
   page seule, puis élargissements progressifs par doublement du budget
   de caractères, puis mesure complète du chapitre —
   `ChapterPaginationState.kt`, boucle `while` de la fonction
   `rememberChapterPaginationState`).
4. L'effet d'ancrage de position de `PagedChapterContent`
   (`LaunchedEffect(chapter?.index, pagination.measurement,
   currentSentenceIndex)`) se relance à **chaque** écriture intermédiaire
   de `measurement`, et appelle `pagination.pageIndexAt(chapter.index,
   currentSentenceIndex)` contre une pagination encore incomplète. Si la
   position courante est profonde dans le chapitre (au-delà du préfixe
   déjà mesuré), la page calculée est transitoirement fausse — le pager
   saute vers elle, puis re-saute vers la bonne page une fois la mesure
   complète arrivée. Plusieurs remesures rapprochées (plusieurs
   masquages/réapparitions du HUD pendant une session de swipes)
   produisent donc plusieurs sauts en rafale : le clignotement observé.

## Pourquoi ce n'était pas visible avant le lot 3c

Avant le correctif du point 1 (`onManualPageChange`,
`PagedChapterContent.kt`), `currentSentenceIndex` ne suivait **pas** un
swipe manuel — il restait figé près de sa dernière valeur connue (TTS ou
navigation explicite), presque toujours proche du début du chapitre pour
une session de lecture silencieuse typique. Les mesures partielles
(préfixe du chapitre) contenaient donc déjà la bonne page pour cet
index-là : l'écart entre page « provisoire » et page « finale » était nul
ou minime, donc invisible. Maintenant que `currentSentenceIndex` suit
fidèlement une position profonde dans le chapitre, cet écart devient
grand et visible à chaque remesure.

**Ce n'est donc pas un bug introduit par le correctif du point 1** — le
mécanisme de remesure déclenché par le HUD était déjà fragile, latent
depuis le lot 3a/3b ; le correctif du point 1 l'a seulement rendu
observable.

## Options, non tranchées ici

1. **Filtrer les mesures intermédiaires** — l'effet d'ancrage de
   `PagedChapterContent` ne réagit (`scrollToPage`) qu'à la mesure
   **complète**, pas aux étapes progressives. Nécessite d'exposer un
   indicateur « mesure complète » depuis `ChapterMeasurement`/
   `ChapterPaginationState`. Chirurgical, cible directement la cause
   racine (réaction à des données incomplètes), n'affecte pas le
   comportement du HUD.
2. **Débouncer `viewportHeightPx`** — ne répercuter un changement de
   hauteur vers `styleKey` qu'une fois la transition d'affichage du HUD
   stabilisée (~200-300 ms). Réduit la fréquence des remesures complètes
   déclenchées, mais ne supprime pas le clignotement interne à UNE
   remesure (les étapes progressives restent émises).
3. **Ne plus faire varier la hauteur de la zone de lecture avec le HUD**
   — superposer `ReaderTopBar`/`UnifiedControlPanel` en overlay
   (`Box`) par-dessus une zone de lecture à hauteur constante, plutôt que
   de les empiler dans le `Column` (qui redistribue l'espace). Supprime
   la cause à la racine (plus aucune remesure liée au HUD), mais change
   la structure de layout de `ReaderScreen` au-delà de la pagination —
   plus gros changement, à mesurer contre d'éventuels effets de bord sur
   d'autres interactions HUD (ex. `ReadingRuler`, `currentLineYDp`).

Option 1 est la plus chirurgicale et la moins risquée ; l'option 3 est la
plus proche d'un comportement « premium » (texte qui ne bouge jamais sous
le HUD) mais touche davantage de code. Décision laissée à Issa.
