# Reconstruction UI — Partie 1 : Fondations

**Dépend de :** Phase 8 (close), audit UX legacy (`INVENTAIRE_UI_LEGACY.md`), journal de décision (message précédent)
**Précède :** Parties 2 (Bibliothèque), 3 (Lecteur), 4 (Réglages/Signets/Recherche), 5 (Statistiques/À propos/Vérification)
**Principe :** tout ce qui suit est un prérequis dur pour les 4 parties suivantes — rien n'y est optionnel ou reportable.

---

## Tâche 1.0 — Corriger le bug d'intégration trouvé pendant l'audit, avant toute nouvelle construction

**Rappel du diagnostic** : le slot `floatingActionButton` de `LibraryScreen` contient toujours le bouchon temporaire (Search/Stats/Settings/Import empilés) posé avant que le vrai drawer existe — jamais retiré. Combiné à un champ de recherche en dur toujours ouvert dans la barre d'outils, c'est la cause directe du « chaos » constaté.

```kotlin
// app/src/main/kotlin/com/inktone/app/InkToneNavHost.kt — SUPPRIMER le Row bouchon,
// le remplacer par le seul import (le drawer gere deja Search/Stats/Settings) :
composable<LibraryRoute> {
    LibraryScreen(
        onNavigateToReader = { publicationId -> navController.navigate(ReaderRoute(publicationId)) },
        onOpenBookmarks = { navController.navigate(BookmarksRoute) },
        floatingActionButton = { ImportPickerButton() }, // plus rien d'autre ici
    )
}
```

Search/Stats/Settings doivent être atteignables **uniquement** depuis le drawer (Partie 2) — c'est tout l'intérêt d'avoir un drawer plutôt que des icônes éparpillées. Le champ de recherche repliable (icône → champ) est traité dans la Partie 2 (`LibraryToolbar`), pas ici — cette tâche ne fait que retirer le bouchon, pas reconstruire la barre.

**Critère de validation :** capture d'écran de `LibraryScreen` après ce retrait — un seul bouton flottant (import), pas quatre.

**Commit :** `Retire le bouchon de navigation temporaire du slot FAB (bug trouve pendant l'audit UX)`

---

## Tâche 1.1 — Vérifications techniques (9bis.0), avec le détail complet trouvé pendant l'audit

### 1.1.1 — `CustomHighlightToolbar` : reproduire le pattern exact du legacy, pas une version simplifiée

**Ce qu'on sait maintenant, précisément** (lecture intégrale du legacy) : le mécanisme sûr n'est pas un seul `SelectionContainer` global, mais **un `SelectionContainer` par phrase individuelle** (`SelectableSentence`), chacun avec son propre `LocalTextToolbar` intercepté. C'est cette granularité fine qui évite le piège « comportement non défini » de `SelectionContainer` + `LazyColumn`.

```kotlin
// Spike a executer avant la Partie 3, reproduisant exactement le pattern legacy :
@Composable
private fun SelectableSentenceSpike(sentenceText: String, onSelected: (String) -> Unit) {
    val defaultToolbar = LocalTextToolbar.current
    val toolbar = remember(sentenceText) {
        object : TextToolbar {
            override val status get() = defaultToolbar.status
            override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
                onSelected(sentenceText) // a remplacer par la vraie extraction d'offset, Partie 3
            }
            override fun hide() { defaultToolbar.hide() }
        }
    }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer { Text(sentenceText) }
    }
}
```

**Test à faire réellement, sur device, dans une `LazyColumn` de plusieurs dizaines de phrases** (pas 3 phrases visibles à l'écran — le risque documenté par Compose concerne spécifiquement les éléments **hors écran**) : sélectionner du texte, faire défiler pendant qu'une sélection est active, confirmer qu'aucun crash ni comportement erratique n'apparaît. **Si ça casse uniquement en dehors de l'écran visible, c'est le signal exact que la documentation Compose annonçait — pas un faux positif.**

### 1.1.2 — Navigation Compose 2.8 typée : déjà en place (Tâche 9bis.2 antérieure), à reconfirmer seulement

Pas de nouveau travail — `NavHost`/routes `@Serializable` déjà construits. Reconfirmer juste que la Tâche 1.0 (retrait du bouchon) n'a rien cassé dans le graphe.

**Commit :** `Prototype et valide SelectionContainer par phrase sur LazyColumn longue (spike 1.1.1)`

---

## Tâche 1.2 — Système de design : port + couleur dynamique + bascule manuelle

### 1.2.1 — Port direct depuis legacy

`Color.kt` (195 lignes), `Type.kt` (122), `Shape.kt`, `Spacing.kt`, `AppIcons.kt` (69) → `core/designsystem`, adaptation de package uniquement. Puis exécuter le test de contraste WCAG AA (déjà écrit en Tâche 9.1.3, jamais encore lancé contre de vraies couleurs) sur les palettes portées — corriger toute couleur qui échoue, ne pas supposer que le score « 7/10 » visuel du legacy garantit un contraste conforme.

### 1.2.2 — Couleur dynamique (Material You), repli, et bascule manuelle (décision confirmée)

```kotlin
// core/designsystem/src/main/kotlin/com/inktone/core/designsystem/InkToneTheme.kt
@Composable
fun InkToneTheme(
    useDynamicColor: Boolean = true, // pilote par UserPreferences.useDynamicColor (NOUVEAU champ, non cassant)
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> InkToneDarkColorScheme
        else -> InkToneLightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = InkToneTypography, shapes = InkToneShapes, content = content)
}
```

**Rappel de discipline (déjà posé Tâche 9bis.1.2, toujours vrai)** : ceci pilote le **chrome de l'app** uniquement — jamais les thèmes de lecture (`LIGHT`/`DARK`/`SEPIA`), qui restent des choix éditoriaux de confort de lecture, indépendants du fond d'écran de l'utilisateur.

`domain/src/main/kotlin/com/inktone/domain/model/UserPreferences.kt`, ajouter :
```kotlin
val useDynamicColor: Boolean = true, // NOUVEAU — bascule manuelle, decision confirmee
```
Migration Room correspondante (même gabarit que `fontFamily`/`reduceMotion`, Phase 8) — colonne supplémentaire, testée.

### 1.2.3 — Typographie de lecture dédiée

Échelle séparée du chrome UI (`ReadingTypography`, interligne 1.6em, plus généreux que le texte d'interface) — appliquée uniquement dans `ReaderContent` (Partie 3), pas ici, mais définie ici pour que la Partie 3 n'ait qu'à l'utiliser.

**Commit :** `Porte le systeme de design, ajoute couleur dynamique avec bascule manuelle et typographie de lecture`

---

## Tâche 1.3 — Extension du domaine : `RichBlock`

**Objectif :** intégrer le formatage riche et les images intercalées, **sans casser ce qui fonctionne déjà** (TTS, alignement CTC, recherche FTS, annotations — tous construits sur `Chapter → Paragraph → Sentence`).

### 1.3.0 — Vérification Readium avant tout code, même discipline que toujours

**Point non résolu, à trancher empiriquement avant d'écrire l'extracteur** : est-ce que `Content.Element` de Readium (déjà utilisé depuis la Tâche 3.4/4.1) distingue réellement les titres/citations/poèmes/images au niveau de l'élément, ou faut-il inférer cette information depuis les balises HTML brutes de l'EPUB (`<h1>`, `<blockquote>`, `<img>`) en contournant l'abstraction Readium ? **Ne pas supposer une réponse — vérifier contre les sources Readium 3.0.0, comme pour `ContentTokenizer` en Phase 3.**

```kotlin
// Spike : inspecter reellement ce que Content.elements() retourne sur un
// EPUB de test contenant un titre, une citation et une image - logger le
// type reel de chaque Content.Element, pas suppose.
```

### 1.3.1 — Conception retenue : extension additive, jamais un remplacement

**Décision de conception, motivée par ce qu'on a compris du legacy en le lisant** (`computeStructuralBlockAnchors`) : le legacy lui-même ne remplace pas le flux de phrases par `RichBlock` — il **superpose** des ancres structurelles au flux existant. On reprend ce principe, pas une refonte :

```kotlin
// domain/src/main/kotlin/com/inktone/domain/model/DocumentModel.kt

/** Indice de style de rendu — n'affecte JAMAIS le texte lu par le TTS,
 * l'alignement CTC ou l'indexation FTS, uniquement l'affichage. Extension
 * NON CASSANTE de Paragraph (Tache 1.2, Phase 1) — valeur par defaut
 * NORMAL, tout code existant continue de fonctionner sans modification. */
enum class ParagraphStyle { NORMAL, HEADING, BLOCK_QUOTE, POEM_LINE }

data class Paragraph(
    val index: Int,
    val sentences: List<Sentence>,
    val style: ParagraphStyle = ParagraphStyle.NORMAL, // NOUVEAU, defaut non cassant
)

/** Blocs purement structurels, SANS texte participant au flux de
 * phrases — jamais vus par TTS/CTC/FTS, uniquement intercales au rendu
 * (meme principe que le legacy). */
sealed interface StructuralBlock {
    val anchorAfterParagraphIndex: Int
    data class EpubImage(override val anchorAfterParagraphIndex: Int, val href: String, val altText: String?) : StructuralBlock
    data class SectionBreak(override val anchorAfterParagraphIndex: Int) : StructuralBlock
}

data class Chapter(
    val index: Int,
    val href: String,
    val title: String?,
    val paragraphs: List<Paragraph>,
    val structuralBlocks: List<StructuralBlock> = emptyList(), // NOUVEAU, defaut non cassant
)
```

**Ce qui reste explicitement hors périmètre de cette extension** : le formatage caractère par caractère (gras/italique au sein d'une phrase, `TextSpan` dans le legacy). Le décidé aujourd'hui (« étendre maintenant ») couvre la structure de bloc (titres/citations/poèmes/images) — le formatage inline est une granularité supplémentaire, non demandée dans le journal de décision, à traiter séparément si un besoin réel se présente. Ne pas l'ajouter par anticipation.

### 1.3.2 — Migration Room

Nouvelle colonne sur la table des chapitres si elle existe déjà en base (à vérifier — actuellement le `DocumentModel` est-il persisté tel quel ou reconstruit à chaque ouverture depuis le fichier EPUB ? **Point à confirmer avant d'écrire la migration** : si le contenu n'est jamais stocké en base, cette extension ne touche que l'extraction en mémoire, aucune migration Room n'est nécessaire).

### 1.3.3 — Impact sur `DocumentModelExtractor` (Phase 3/4)

Mise à jour de l'extraction (Tâche 3.4/4.1) pour peupler `style` et `structuralBlocks` à partir de ce que 1.3.0 aura confirmé — **pas avant**, le code d'extraction dépend directement du résultat du spike.

**Commit :** `Etend Paragraph et Chapter (style, blocs structurels) - extension non cassante, verifiee contre Readium`

---

## Tâche 1.4 — Domaine : objectif de lecture quotidien

**Objectif :** le champ nécessaire pour la jauge de la Partie 5 — posé ici pour que rien dans les parties suivantes n'attende une extension de domaine en cours de route (leçon du motif « trous récurrents » signalé plus tôt dans ce fil).

`domain/src/main/kotlin/com/inktone/domain/model/UserPreferences.kt`, ajouter :
```kotlin
val dailyGoalMinutes: Int = 20, // NOUVEAU — objectif par defaut raisonnable, modifiable en reglages (Partie 4)
```

`domain/src/main/kotlin/com/inktone/domain/repository/ReadingSessionRepository.kt`, ajouter :
```kotlin
/** Somme des durees de session pour une date donnee (format "yyyy-MM-dd",
 * coherent avec le legacy) — necessaire pour la jauge quotidienne, absente
 * jusqu'ici (seul getAll() existe, Phase 8bis). */
suspend fun getTotalDurationForDate(date: String): Long
```

**Implémentation Room** : requête `SUM(durationMs) WHERE date(startedAt/1000, 'unixepoch') = :date` (ou équivalent selon le format de stockage réel de `startedAt` — **à vérifier contre le schéma actuel avant d'écrire la requête**, ne pas supposer un format de date déjà présent en base).

**Pas de migration Room nécessaire pour `dailyGoalMinutes`** — c'est un champ de `UserPreferences`, déjà couvert par la table existante ; migration nécessaire uniquement si la colonne elle-même doit être ajoutée (même geste que `fontFamily`/`reduceMotion`, Phase 8).

**Commit :** `Ajoute dailyGoalMinutes et la requete de duree quotidienne (fondation Partie 5)`

---

## Checklist finale de sortie — Partie 1

| # | Critère | Vérification |
|---|---|---|
| 1 | Bouchon FAB retiré, capture d'écran confirmant un seul bouton | Tâche 1.0 |
| 2 | Sélection par phrase individuelle testée sur `LazyColumn` longue, device réel, défilement pendant sélection active | Tâche 1.1.1 |
| 3 | Système de design porté, contraste WCAG AA vérifié (pas supposé) | Tâche 1.2.1 |
| 4 | Couleur dynamique + bascule manuelle fonctionnelles, jamais appliquées aux thèmes de lecture | Tâche 1.2.2 |
| 5 | Capacité réelle de `Content.Element` Readium vérifiée avant d'écrire l'extraction `RichBlock` | Tâche 1.3.0 |
| 6 | `Paragraph`/`Chapter` étendus de façon non cassante, TTS/CTC/FTS non affectés | Tâche 1.3.1, tests de régression existants toujours verts |
| 7 | `dailyGoalMinutes` et requête de durée quotidienne posés | Tâche 1.4 |

Une fois les 7 critères vérifiés — **avec capture d'écran pour le critère 1, pas seulement pour la clôture finale de la Partie 5** — la Partie 1 est close. Toutes les parties suivantes (2 à 5) peuvent alors démarrer sur une fondation vérifiée plutôt que supposée.
