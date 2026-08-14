# InkTone — Plan d'action « top-tier ebook reader »

> **Contexte :** Audit croisé du legacy (`legacy/monolith`) et de la réécriture actuelle (Phases 0–9bis).
> Ce plan traite la réécriture comme base et la complète/améliore. Chaque tâche est justifiée, priorisée et estimée. Aucun "à faire plus tard" sans une décision explicite.
>
> **Philosophie directrice :** penser Apple Books × Google Play Books × Kindle pour Android 2026.
> Un reader top-tier = lecture visuelle irréprochable + TTS mot-à-mot fluide + bibliothèque premium + zéro friction.

---

## État des lieux — Récapitulatif de l'audit

### Ce qui est ✅ solide et ne sera pas retouché
| Module | État |
|---|---|
| Architecture (Clean Arch, MVI, Hilt, Room) | ✅ Irréprochable |
| Domain model (Publication, Annotation, Bookmark, ReadingState) | ✅ Complet |
| Pipeline TTS Kokoro + timestamps CTC réels | ✅ Supérieur au legacy |
| Import SAF multi-fichiers + WorkManager | ✅ Complet |
| Grille bibliothèque adaptative, Coil + fallback gradient | ✅ Complet |
| Annotations : création, persistance, affichage | ✅ Complet |
| Signets : création, liste, navigation | ✅ Complet |
| TOC hiérarchique + auto-scroll | ✅ Complet |
| Surlignage mot-à-mot animé (`animateIntAsState`) | ✅ Supérieur au legacy |
| Accessibility preset, `reduceMotion` | ✅ Complet |
| Backup export/import JSON | ✅ Complet |

### Ce qui est 🟡 présent mais insuffisant pour du top-tier
| Module | Lacune principale |
|---|---|
| Reader — rendu du texte | `FlowRow` simple, aucun rendu enrichi (`ParagraphStyle`, images EPUB) |
| Reader — HUD | Pas de panneau réglages lecture interne, pas de font/thème depuis le Reader |
| Reader — mode paginé | Totalement absent — le legacy avait un `HorizontalPager` complet |
| TTS — auto-advance | La lecture s'arrête après chaque phrase — pas de lecture continue |
| Settings UX | Cycle d'enum opaque, pas de picker, SectionGroup illisible |
| Statistics | 3 cartes brutes, pas de DailyGoal, pas de WPM, pas de graphique |
| About | Version hardcodée, liens non cliquables |
| Search | Pas de highlighting du terme dans le snippet, pas d'état vide |

### Ce qui est ❌ absent et bloquant pour le niveau top-tier
| Fonctionnalité | Source |
|---|---|
| **Lecture TTS continue** (auto-advance phrase → phrase) | Critique — sans ça, la TTS est inutilisable |
| **Mode paginé** (swipe horizontal, `HorizontalPager`) | Legacy avait ça, réécriture non |
| **Panneau réglages lecture in-reader** | Font size, thème, interligne depuis le Reader |
| **Panneau TTS in-reader** | Vitesse, voix, minuteur de sommeil — bottom sheet dédié |
| **Rendu riche EPUB** | Titres, citations, images, ruptures de section |
| **Captions TTS** (sous-titres overlay pendant la lecture) | Legacy avait ça |
| **ETA chapitre / temps restant** | `~12 min` affiché quand HUD masqué |
| **Voix picker** | Sélection de la voix TTS depuis Settings et in-reader |
| **Gain audio** (jusqu'à 4×) | Présent dans le legacy Settings |
| **WPM + DailyGoal** dans Statistics | Nécessite `wordsRead` dans ReadingSession |
| **Police système** (`fontScale` Android) respectée | Toggle explicite dans Settings |
| **SharedTransition** couverture → Reader | Standard Android 2026 |
| **Scroll automatique** vers la phrase TTS active | Confort de lecture essentiel |
| **Dialogue de note** sur les annotations | Legacy avait un AlertDialog avec texte de rappel |
| **Error state Reader** | Les erreurs de parsing ne sont jamais surfacées |
| **Confirmation suppression** signets/annotations | UX sécurité basique |

---

## Priorités absolues — Ordre de traitement

```
BLOC A — Moteur de lecture (bloquant)       ← sans ça l'app est brisée
BLOC B — Reader UX (top-tier gate)          ← sans ça l'app est médiocre
BLOC C — Bibliothèque & navigation (polish)
BLOC D — Settings & Statistics (complétude)
BLOC E — Hardening & Release
```

---

## BLOC A — Moteur de lecture continu (CRITIQUE, ~5 tâches)

> **Principe :** un ebook reader sans lecture audio continue n'est pas un ebook reader. C'est le premier bug à corriger.

### A.1 — Auto-advance TTS (lecture continue phrase à phrase)

**Problème précis :** `ReaderViewModel.playCurrentSentence()` synthétise et joue **une seule phrase** puis s'arrête. Zéro boucle d'avancement.

**Solution :**
```kotlin
// ReaderViewModel.kt — remplacer la fin de playCurrentSentence()
private fun playCurrentSentence() {
    viewModelScope.launch {
        val sentences = _state.value.currentChapter?.paragraphs
            ?.flatMap { it.sentences } ?: return@launch
        val index = _state.value.currentSentenceIndex

        if (index >= sentences.size) {
            // Fin de chapitre → auto-avance chapitre suivant si possible
            if (_state.value.hasNextChapter) {
                onIntent(ReaderIntent.NextChapter)
                // Délai court pour laisser le chapitre se charger
                delay(300)
                onIntent(ReaderIntent.PlayCurrentSentence)
            } else {
                _state.update { it.copy(isPlaying = false) }
            }
            return@launch
        }

        val sentence = sentences[index]
        val audioResult = ttsEngine.synthesize(sentence.text, activeVoiceProfile())
        audioSegmentPlayer.play(audioResult) { wordTimestamps ->
            _state.update { it.copy(highlightedWordRange = wordTimestamps) }
        }
        // Avance à la phrase suivante UNIQUEMENT si toujours en lecture
        if (_state.value.isPlaying) {
            _state.update { it.copy(currentSentenceIndex = index + 1) }
            playCurrentSentence() // recursion trampolinée par coroutine
        }
    }
}
```

**Auto-scroll vers la phrase active :**
- Exposer un `ScrollState` partagé depuis `ReaderScreen` via `rememberScrollState`.
- Dans le `LaunchedEffect(state.currentSentenceIndex)` : `scrollState.animateScrollTo(sentenceY.roundToInt())`.
- La position Y de chaque phrase est déjà trackée via `onGloballyPositioned` (ReadingRuler) — réutiliser.

**Commit :** `A.1 — Lecture TTS continue + auto-scroll vers la phrase active`

---

### A.2 — Cancellation propre + `onCleared()`

**Problème :** `audioSegmentPlayer.stop()` n'est jamais appelé en `onCleared()`. Un audio peut jouer après que le ViewModel soit détruit.

```kotlin
override fun onCleared() {
    super.onCleared()
    audioSegmentPlayer.stop()
    sleepTimerJob?.cancel()
}
```

**Commit :** `A.2 — Nettoyage audio et sleep timer sur onCleared`

---

### A.3 — Error state Reader (parse failures surfacées)

**Problème :** `Log.w` silencieux quand un EPUB échoue à parser. L'écran reste vide indéfiniment — l'utilisateur ne sait pas quoi faire.

**Solution :**
- Ajouter `errorMessage: String? = null` dans `ReaderUiState`.
- Dans `openPublication()` catch block : `_state.update { it.copy(errorMessage = e.message) }`.
- Dans `ReaderScreen` : afficher `ErrorState(message, onRetry = { viewModel.onIntent(ReaderIntent.Open(publicationId)) }, onBack = onBack)`.

**Commit :** `A.3 — Surfacer les erreurs de parsing dans le Reader`

---

### A.4 — Single-chapter EPUB — division par zéro

**Problème :** `LibraryViewModel` calcule la progression avec `(chapterCount - 1)` comme diviseur → division par zéro pour un EPUB à 1 chapitre.

```kotlin
// Avant
val progressPercent = if (state.chapterCount > 1) {
    ((state.chapterIndex * 100) / (state.chapterCount - 1)).coerceAtLeast(1)
} else 0

// Après — utiliser charOffset réel depuis ReadingState.locator
val progressPercent = computeProgressPercent(readingState, publication)

private fun computeProgressPercent(rs: ReadingState?, pub: Publication): Int {
    rs ?: return 0
    if (pub.chapterCount <= 0) return 0
    // Locator.computeProgression() est déjà disponible (Phase 1)
    val raw = (rs.locator.computeProgression(pub) * 100).toInt()
    return if (raw > 0) raw.coerceAtLeast(1) else 0
}
```

**Commit :** `A.4 — Progression basée sur Locator.computeProgression, corrige div/0`

---

### A.5 — Voix picker — câbler `VoiceProfile` réel

**Problème :** la voix TTS est hardcodée dans `ReaderViewModel` (`"vp-native-fr"`). Aucun utilisateur ne peut changer la voix.

**Solution minimale :**
- Lire `UserPreferences.activeVoiceProfileId` depuis `PreferencesRepository`.
- `getVoiceProfilesUseCase` existe déjà — l'utiliser dans `SettingsViewModel` pour peupler un picker.
- Câbler `SaveVoiceProfileUseCase` sur la sélection.
- Ajouter `SettingRow("Voix", activeVoiceName) { showVoicePicker = true }` dans `SettingsScreen`.

**Commit :** `A.5 — Voice picker câblé dans Settings et ViewModel`

---

## BLOC B — Reader UX top-tier (~8 tâches)

### B.1 — Mode paginé (`HorizontalPager`)

**Raisonnement :** Apple Books, Kindle, Google Books proposent tous le swipe horizontal comme mode de lecture par défaut. C'est un signal immédiat de qualité.

**Architecture :**
```kotlin
// Dans ReaderUiState
enum class ReadingMode { SCROLL, PAGED }

// Dans ReaderScreen
when (state.readingMode) {
    ReadingMode.SCROLL -> ScrollableChapterContent(sentences, ...)
    ReadingMode.PAGED  -> PagedChapterContent(sentences, pageSize, ...)
}
```

**`PagedChapterContent` :**
- `produceState` sur `Dispatchers.Default` — mesurer la hauteur disponible (`BoxWithConstraints`), découper les phrases en pages par accumulation de hauteur estimée (nb caractères × hauteur par char, jamais sur le thread UI).
- `HorizontalPager(pageCount = pages.size)` — swipe natif avec retour tactile.
- Page virtuelle finale (index == pages.size) : charger le chapitre suivant → `onIntent(ReaderIntent.NextChapter)`.
- Garde-fous : `pages.isNotEmpty()` avant d'afficher le Pager, `!isLoadingChapter` pour éviter les appels concurrents.

**Toggle UI :** bouton dans `UnifiedControlPanel` — `ReadingMode.SCROLL` ↔ `ReadingMode.PAGED`. Persisté dans `UserPreferences`.

**Commit :** `B.1 — Mode paginé HorizontalPager avec pagination asynchrone`

---

### B.2 — Panneau Réglages lecture in-reader (`ReaderSettingsPanel`)

**Raisonnement :** quitter le Reader pour aller dans Settings pour ajuster la taille du texte puis revenir — c'est rédhibitoire pour un reader premium.

**`ReaderSettingsPanel` (ModalBottomSheet, 5 sections) :**
```
┌─────────────────────────────────────────┐
│  Thèmes         [CLAIR] [SOMBRE] [SÉPIA]│  ← cartes visuelles, pas des noms
│  Police         [Défaut] [Serif] [Dyslx]│  ← 3 options max
│  Taille         ●────────── ─           │  ← Slider 12→32sp
│  Interligne     ●──────────────         │  ← Slider 1.2→2.0em
│  Marges         ●──────────             │  ← Slider 8→32dp
└─────────────────────────────────────────┘
```

- Les thèmes en cartes-échantillons montrent le vrai fond/texte (legacy §8.3.2 lu).
- Chaque changement → `ReaderIntent.SetOverrides(...)` existant → `EffectiveReadingSettings.resolve()` déjà câblé.
- Ouvrir via bouton `Aa` dans `UnifiedControlPanel`.

**Commit :** `B.2 — ReaderSettingsPanel — typo/thème/marges depuis le Reader`

---

### B.3 — Panneau TTS in-reader (`ReaderTtsPanel`)

**Raisonnement :** l'utilisateur qui écoute ne doit pas ouvrir Settings pour ajuster la vitesse.

**`ReaderTtsPanel` (ModalBottomSheet distinct) :**
```
┌─────────────────────────────────────────┐
│  Phrase 12 / 87      [◀] [▶]           │
│              [⏮] [⏯] [⏭] [⏹]         │
│  Vitesse ●──────────── 1.0×             │
│  Voix    Jessica ▾                      │
│  Veille  [15 min] [30 min] [45 min] [1h]│  ← chips
└─────────────────────────────────────────┘
```

- Slider vitesse → `UpdatePreferencesUseCase(speed = ...)` → effet immédiat TTS.
- Bouton Stop : `Icon(AppIcons.Stop)` — jamais l'emoji `⏹` (règle K12 du projet).
- Le minuteur de sommeil vit ici (cohérent avec le legacy §2.5 + note §2.3).
- Ouvrir via clic sur l'icône Play-bouton (clic long) **ou** via icône dédiée dans `UnifiedControlPanel`.

**Commit :** `B.3 — ReaderTtsPanel — vitesse/voix/minuteur depuis le Reader`

---

### B.4 — Rendu riche EPUB (`ParagraphStyle`)

**Problème :** `ParagraphStyle` (HEADING, BLOCK_QUOTE, POEM_LINE) est défini dans le domain mais jamais appliqué au rendu.

**Solution dans `SentenceText` :**
```kotlin
// Paramètre supplémentaire
@Composable
fun SentenceText(
    sentence: Sentence,
    paragraphStyle: ParagraphStyle = ParagraphStyle.NORMAL,
    // ... reste inchangé
) {
    val textStyle = when (paragraphStyle) {
        ParagraphStyle.HEADING    -> MaterialTheme.typography.headlineSmall
        ParagraphStyle.BLOCK_QUOTE -> ReadingTypography.copy(
            fontStyle = FontStyle.Italic,
            color = textColor.copy(alpha = 0.75f)
        )
        ParagraphStyle.POEM_LINE  -> ReadingTypography.copy(
            letterSpacing = 0.05.em
        )
        ParagraphStyle.NORMAL     -> ReadingTypography
    }
    // Séparateur visuel pour BLOCK_QUOTE : barre verticale à gauche (Canvas/drawBehind)
}
```

**Rendu d'images EPUB (`EpubImage`) :**
- `StructuralBlock.EpubImage` est déjà parsé, jamais rendu.
- Dans `ReaderScreen`, entre les `Paragraph`, insérer un `AsyncImage(href)` pour chaque `EpubImage` dont `anchorAfterParagraphIndex` correspond au paragraphe courant.
- `contentDescription = altText` (accessibilité).

**Commit :** `B.4 — Rendu enrichi : headings, blockquotes, poems, images EPUB`

---

### B.5 — Piste de lecture (« reading trail »)

**Du legacy (§2.4.e) :** phrases déjà lues → opacité 40%, à venir → 88%. Simple et très efficace visuellement.

```kotlin
// Dans SentenceText
val textAlpha = when {
    index < currentSentenceIndex -> 0.40f  // lue
    index > currentSentenceIndex -> 0.88f  // à venir
    else -> 1.0f                           // en cours
}
Text(text = ..., color = textColor.copy(alpha = textAlpha))
```

**Commit :** `B.5 — Piste de lecture : opacité différenciée lue/en cours/à venir`

---

### B.6 — ETA et micro-indicateur (HUD masqué)

**Du legacy (§2.2) :** quand le HUD est masqué, un micro-overlay affiche `~12 min` (temps restant dans le chapitre, calculé depuis le WPM estimé ou un 250WPM par défaut si pas encore de données).

```kotlin
// Dans ReaderScreen, quand !isHudVisible
if (!isHudVisible) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = state.etaText,  // ex. "~12 min" calculé dans ReaderUiState
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
```

**`etaText` dans `ReaderUiState` :**
```kotlin
val etaText: String get() {
    val remainingSentences = currentChapter?.paragraphs
        ?.flatMap { it.sentences }?.drop(currentSentenceIndex)?.size ?: return ""
    val avgWordsPerSentence = 15  // estimation conservative
    val wpm = 250  // default; remplacer par ReadingSession.wpm si disponible
    val remainingWords = remainingSentences * avgWordsPerSentence
    val minutes = (remainingWords.toFloat() / wpm).roundToInt().coerceAtLeast(1)
    return "~$minutes min"
}
```

**Commit :** `B.6 — Micro-indicateur ETA quand HUD masqué`

---

### B.7 — Captions TTS (overlay sous-titres)

**Du legacy (§2.2) :** overlay semi-transparent en bas de l'écran affichant la phrase en cours de lecture, masqué pendant une sélection.

```kotlin
// Dans ReaderScreen, superposé au contenu (Box parent)
if (state.isPlaying && selectedRange == null) {
    val currentText = sentences.getOrNull(state.currentSentenceIndex)?.text
    if (currentText != null) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = currentText,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

**Accessibilité :** `semantics { liveRegion = LiveRegionMode.Polite }` pour TalkBack.

**Commit :** `B.7 — Captions TTS overlay (sous-titres phrase active)`

---

### B.8 — ChapterOverrideMenu → remplacé par `ReaderSettingsPanel`

**Problème :** `ChapterOverrideMenu` est un bouton binaire "DARK/reset" — un vestige de développement qui ne doit pas exister en production.

**Solution :** le supprimer entièrement. `ReaderSettingsPanel` (B.2) prend le relais avec une vraie UX. L'accès aux overrides par livre se fait via le toggle "Réglages de ce livre" dans `ReaderSettingsPanel`.

**Commit :** `B.8 — Supprime ChapterOverrideMenu, consolide dans ReaderSettingsPanel`

---

## BLOC C — Bibliothèque & Navigation (~5 tâches)

### C.1 — Drawer header avec dégradé (legacy §1.2)

**Du legacy :** bloc 140dp, dégradé `primaryContainer→primary`, "InkTone" en headline. La réécriture actuelle a juste un `Text("InkTone")` sans fond.

```kotlin
// En tête du LibraryDrawerContent
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(140.dp)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primary,
                )
            )
        )
        .padding(start = 24.dp, bottom = 24.dp),
    contentAlignment = Alignment.BottomStart,
) {
    Text(
        "InkTone",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
    )
}
```

**Commit :** `C.1 — Drawer header avec dégradé brand`

---

### C.2 — Drawer footer câblé (À propos, Thème)

**Problème :** les deux boutons du footer drawer ont un `onClick = {}` vide — ils ne font rien.

**Solution :**
- "À propos" → `onNavigateToAbout()` (nouveau callback ou via `onOpenSettings` puis route interne).
- "Thème" → ouvrir un `ThemePickerDialog` (SYSTEM/LIGHT/DARK) via `AppThemeViewModel` existant.
- Ajouter "Debug" conditionné par `BuildConfig.DEBUG` (cohérent avec le legacy et la Phase 0 du projet).

**Commit :** `C.2 — Drawer footer : About, ThemePicker, Debug conditionnel`

---

### C.3 — Actions sheet manquantes (Régénérer/Réinitialiser couvertures)

**Du legacy (§1.4) :** la sheet 3-points a 4 actions : Import, Actualiser, Régénérer les couvertures (avec progression live X/Y), Réinitialiser les couvertures (avec dialogue de confirmation).

**Réécriture actuelle :** seulement Import et Actualiser.

**Solution :**
```kotlin
// LibraryViewModel — ajouter
fun regenerateCovers() { /* appel à repository.regenerateAllCovers() avec progress */ }
fun resetCovers() { /* appel à repository.resetCoversToDefault() */ }

// LibraryIntent
object RegenerateCovers : LibraryIntent
object ResetCovers : LibraryIntent

// ActionSheet — ajouter 2 items + dialog de confirmation pour Reset
```

**Commit :** `C.3 — Actions sheet : régénérer/réinitialiser couvertures avec confirmation`

---

### C.4 — `LibraryNavigationPopup` — navigation enrichie

**Du legacy (§8.1) :** popup deux colonnes (catégories gauche, sous-éléments avec compteur droite). `BY_AUTHOR` est une vraie catégorie, pas un no-op.

**Solution minimale :** remplacer le titre de la TopBar (actuellement vide) par un `Text` cliquable qui ouvre ce popup. La logique de filtrage par auteur est déjà dans `LibraryUiState.availableAuthors` — il suffit de l'exposer.

```kotlin
// Dans LibraryTopBar
IconButton(onClick = { showNavPopup = true }) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(state.activeFilter.label(), style = MaterialTheme.typography.titleMedium)
        Icon(AppIcons.DropDown, contentDescription = null)
    }
}
// LibraryNavigationPopup — Dialog ou ModalBottomSheet avec 2 colonnes
```

**Commit :** `C.4 — Titre TopBar cliquable, LibraryNavigationPopup deux colonnes`

---

### C.5 — SharedTransition couverture → Reader

**Standard 2026 :** `SharedTransitionLayout` + `Modifier.sharedElement()` sur la couverture dans `BookCover` et dans le futur header du Reader.

**Pré-requis :** passer `SharedTransitionScope` et `AnimatedVisibilityScope` via `CompositionLocal` dans `InkToneNavHost` pour éviter de polluer toutes les signatures.

```kotlin
// InkToneNavHost
val sharedTransitionScope = remember { /* ... */ }
CompositionLocalProvider(LocalSharedTransitionScope provides sharedTransitionScope) {
    NavHost(...) { ... }
}
```

**Note :** ne pas implémenter sans appareil de test disponible (commentaire légitimement présent dans le code actuel). À placer en dernière tâche du bloc ou conditionné par un device.

**Commit :** `C.5 — SharedTransition couverture→Reader (élément partagé)`

---

## BLOC D — Settings & Statistics (~6 tâches)

### D.1 — Settings UX — pickers en place des cycles aveugles

**Problème :** thème, police, moteur TTS sont sélectionnés par "cliquer pour voir la prochaine option" — l'utilisateur ne voit jamais toutes les options à la fois.

**Solution :**
- **Thème** → `RadioGroup` inline dans un `AlertDialog` (SYSTEM / CLAIR / SOMBRE).
- **Police** → idem (`DEFAULT / SERIF / OPEN_DYSLEXIC`), avec un extrait visuel de la police.
- **TTS Engine** → `AlertDialog` avec description de chaque moteur.

```kotlin
// Pattern générique
@Composable
fun <T> PickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) { ... }
```

**Commit :** `D.1 — Settings : pickers dialog pour thème, police, moteur TTS`

---

### D.2 — SectionGroup visuel

**Problème :** `SectionGroup` n'a aucun style — son titre est identique visuellement aux labels de settings.

```kotlin
@Composable
fun SectionGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.em,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}
```

**Commit :** `D.2 — SectionGroup : titre coloré + cartes par section`

---

### D.3 — Settings manquants (legacy §3)

**Absents de la réécriture :**

| Setting | Action |
|---|---|
| Gain audio (jusqu'à 4×) | Slider dans section Voix → `UserPreferences.audioGain`, câblé dans `AudioSegmentPlayer` |
| Couleur dynamique (toggle, pas auto) | Toggle "Activer Material You (Android 12+)" → `UserPreferences.useDynamicColor` |
| Police adaptée au système (`fontScale`) | Toggle → `UserPreferences.useSystemFontScale` — si actif, ignorer `fontSize` interne |
| Dossier modèles TTS personnalisable | `SettingRow("Dossier des modèles", path) { launchDirectoryPicker() }` |

**Commit :** `D.3 — Settings manquants : gain audio, dynamic color toggle, fontScale, dossier modèles`

---

### D.4 — Statistics : WPM + DailyGoal + record de série

**Pré-requis domaine :**
```kotlin
// ReadingSession — ajouter (migration DB)
data class ReadingSession(
    // ... existant
    val wordsRead: Int = 0,     // NOUVEAU — nécessaire pour WPM
)

// UserPreferences — ajouter
val dailyGoalMinutes: Int = 20  // NOUVEAU
```

**Migration :**
```sql
ALTER TABLE reading_sessions ADD COLUMN wordsRead INTEGER NOT NULL DEFAULT 0
ALTER TABLE user_preferences ADD COLUMN dailyGoalMinutes INTEGER NOT NULL DEFAULT 20
```

**`GetStatisticsUseCase` — étendre :**
- WPM moyen = `sum(wordsRead) / sum(durationMs / 60_000)` sur les 30 dernières sessions.
- `dailyGoalProgress` = `todayReadingMinutes / dailyGoalMinutes`.
- `maxStreakDays` (record) en plus de `currentStreakDays`.

**`StatisticsScreen` — étendre :**
```
┌─────────────────────────────────────────┐
│  Objectif du jour       [jauge circulaire]  │
│  18 / 20 min            ████████░░       │
├───────────────────────────────────────────┤
│  Série        🔥 5 j    Record : 12 j   │
├───────────────────────────────────────────┤
│  WPM         243        Temps total 14h │
│  Livres lus  8                          │
└─────────────────────────────────────────┘
```

**Commit :** `D.4 — Statistics : WPM + DailyGoal + record de série + migration DB`

---

### D.5 — About screen — corrections bloquantes

**Problèmes précis :**
- Version hardcodée `"0.1.0"` → `BuildConfig.VERSION_NAME`.
- URL GitHub en `Text` non cliquable → `LocalUriHandler.current.openUri(...)`.
- Mention "Sherpa-ONNX" / "Piper VITS" → corriger pour refléter Kokoro (Apache-2.0) + alignement CTC (ADR-021/022).
- Ajouter un `TopAppBar` ou laisser le caller le fournir de façon cohérente.

**Commit :** `D.5 — About : version dynamique, liens cliquables, contenu technique corrigé`

---

### D.6 — Search — expérience complète

**Problèmes :**
- Pas de highlighting du terme dans le snippet.
- Pas d'état vide quand `query.length >= 2 && results.isEmpty()`.
- Pas de bouton X pour effacer la query.
- Pas de debounce explicitement visible.

**Solutions :**
```kotlin
// Highlighting dans SearchResultItem
fun highlightSnippet(snippet: String, query: String): AnnotatedString = buildAnnotatedString {
    val lower = snippet.lowercase()
    val q = query.lowercase()
    var start = 0
    var idx = lower.indexOf(q, start)
    while (idx >= 0) {
        append(snippet.substring(start, idx))
        withStyle(SpanStyle(background = Color(0x66FFEB3B))) {
            append(snippet.substring(idx, idx + q.length))
        }
        start = idx + q.length
        idx = lower.indexOf(q, start)
    }
    append(snippet.substring(start))
}

// SearchViewModel — ajouter debounce
.debounce(300)
.filter { it.length >= 2 }
```

**Commit :** `D.6 — Search : highlighting snippet, état vide, bouton clear, debounce`

---

## BLOC E — Hardening & Release (~5 tâches)

### E.1 — Supprimer les scaffoldings debug

**À supprimer ou conditionner par `BuildConfig.DEBUG` :**
- `ReaderIntent.BootstrapAndOpenFixture` et son handler dans `ReaderViewModel`.
- `bootstrapAndOpenFixture()` dans `ReaderViewModel` (peut rester en `DEBUG` uniquement).
- Tout `Log.d` non conditionnel restant.

**Commit :** `E.1 — Purge des scaffoldings debug, conditionnement BuildConfig`

---

### E.2 — TalkBack systématique

**Audit par écran :**
- `BookCover` : le composable entier n'a pas de `contentDescription` global (uniquement le badge et l'image). Ajouter `Modifier.semantics { contentDescription = "${publication.title}, $progressPercent% lu" }` sur le `Box` root.
- `UnifiedControlPanel` : Prev/Next chapter buttons ont un `contentDescription` — vérifier qu'il change si le chapitre est indisponible (`hasPreviousChapter`).
- `AnnotationColorPicker` : chips avec nom anglais en uppercase — localiser ou ajouter `contentDescription` français.
- `SearchScreen` : le `TextField` n'a pas de `label` visible.

**Commit :** `E.2 — Audit accessibilité TalkBack, contentDescription manquants`

---

### E.3 — Gestion `coverUri` — content:// vs file://

**Problème :** `BookCover.kt` fait `File(coverUri)` — si l'import produit un content:// URI, le `File` sera invalide et la couverture sera toujours absente.

**Solution :**
```kotlin
val model: Any? = when {
    coverUri == null -> null
    coverUri.startsWith("content://") -> Uri.parse(coverUri)  // Coil gère les content:// URIs nativement
    else -> File(coverUri)
}
```

**Commit :** `E.3 — BookCover : support content:// URI en plus de file://`

---

### E.4 — Nested scroll `SeriesGroupedView`

**Problème :** `SeriesGroupedView` utilise un `LazyColumn` imbriqué dans un `Column` scrollable — comportement non défini sur Android.

**Solution :** ne pas utiliser `LazyColumn` dans `SeriesGroupedView`. Itérer avec `forEach` dans un `Column` simple (la liste de séries est bornée, un `LazyColumn` n'est pas nécessaire). La grille principale reste scrollable.

**Commit :** `E.4 — Corrige nested scroll dans SeriesGroupedView`

---

### E.5 — URL Kokoro CDN + WindowSizeClass (issues reportées Phase 9)

**Kokoro URL :** vérifier la release Hugging Face / GitHub du paquet `kokoro-int8-multi-lang-v1_0`, documenter le SHA-256 dans `SherpaOnnxVoiceModelDownloadService.kt`.

**WindowSizeClass :** `LocalWindowSizeClass` est déjà dans `core/designsystem` — vérifier qu'il est fourni dans `MainActivity` via `CompositionLocalProvider`. Fondation uniquement, pas de layout tablette.

**Commit :** `E.5 — Kokoro CDN URL stable + WindowSizeClass fourni dans MainActivity`

---

## Tableau de synthèse — Priorités et effort

| Tâche | Priorité | Effort | Bloquant | Top-tier gate |
|---|---|---|---|---|
| **A.1** Auto-advance TTS + auto-scroll | 🔴 CRITIQUE | M | ✅ | ✅ |
| **A.2** Cleanup `onCleared` | 🔴 CRITIQUE | XS | ✅ | — |
| **A.3** Error state Reader | 🔴 CRITIQUE | S | ✅ | — |
| **A.4** Progression div/0 | 🔴 CRITIQUE | S | ✅ | — |
| **A.5** Voice picker câblé | 🟠 HAUTE | M | — | ✅ |
| **B.1** Mode paginé `HorizontalPager` | 🟠 HAUTE | L | — | ✅ |
| **B.2** `ReaderSettingsPanel` | 🟠 HAUTE | M | — | ✅ |
| **B.3** `ReaderTtsPanel` | 🟠 HAUTE | M | — | ✅ |
| **B.4** Rendu riche EPUB | 🟠 HAUTE | M | — | ✅ |
| **B.5** Reading trail opacité | 🟡 MOYENNE | XS | — | ✅ |
| **B.6** ETA micro-indicateur | 🟡 MOYENNE | S | — | ✅ |
| **B.7** Captions TTS | 🟡 MOYENNE | S | — | ✅ |
| **B.8** Supprimer ChapterOverrideMenu | 🟡 MOYENNE | XS | — | — |
| **C.1** Drawer header dégradé | 🟡 MOYENNE | XS | — | ✅ |
| **C.2** Drawer footer câblé | 🟡 MOYENNE | S | — | — |
| **C.3** Actions sheet couvertures | 🟡 MOYENNE | M | — | — |
| **C.4** `LibraryNavigationPopup` | 🟡 MOYENNE | M | — | ✅ |
| **C.5** SharedTransition | 🟢 BASSE | L | — | ✅ |
| **D.1** Settings pickers dialogs | 🟡 MOYENNE | M | — | ✅ |
| **D.2** SectionGroup visuel | 🟡 MOYENNE | S | — | ✅ |
| **D.3** Settings manquants | 🟡 MOYENNE | M | — | ✅ |
| **D.4** Statistics WPM + DailyGoal | 🟢 BASSE | L | — | ✅ |
| **D.5** About corrections | 🟠 HAUTE | S | — | — |
| **D.6** Search expérience complète | 🟡 MOYENNE | M | — | ✅ |
| **E.1** Purge scaffoldings debug | 🔴 CRITIQUE | S | ✅ | — |
| **E.2** TalkBack systématique | 🟠 HAUTE | M | — | ✅ |
| **E.3** coverUri content:// | 🟠 HAUTE | XS | — | — |
| **E.4** Nested scroll SeriesGroupedView | 🟠 HAUTE | XS | ✅ | — |
| **E.5** Kokoro CDN + WindowSizeClass | 🟡 MOYENNE | S | — | — |

**Légende effort :** XS = <1h · S = 1-3h · M = 3-6h · L = >6h

---

## Séquence d'exécution recommandée

```
Sprint 1 — Fondation solide (CRITIQUE)
  A.1 → A.2 → A.3 → A.4 → E.1 → E.4

Sprint 2 — Reader top-tier (HAUTE)
  A.5 → B.2 → B.3 → B.4 → B.8

Sprint 3 — Mode paginé + polish Reader
  B.1 → B.5 → B.6 → B.7

Sprint 4 — Bibliothèque + Settings
  C.1 → C.2 → C.3 → C.4 → D.1 → D.2 → D.3 → D.5 → D.6

Sprint 5 — Stats + Hardening + Release
  D.4 → E.2 → E.3 → E.5 → C.5
```

---

## Décisions de domaine à trancher explicitement

Ces trois points vont au-delà de l'UI et impactent des phases déjà closes :

### 1. `RichBlock` vs `DocumentModel` actuel
Le legacy parsait `Heading/BlockQuote/PoemLine/EpubImage/SectionBreak` dans un modèle de contenu riche. La réécriture utilise `ParagraphStyle` + `StructuralBlock` — c'est une bonne abstraction mais elle n'est pas utilisée au rendu (Tâche B.4 corrige le rendu, mais le parseur doit aussi alimenter `ParagraphStyle` correctement).
> **Décision suggérée :** B.4 suffit — pas de réécriture du parseur pour v1.

### 2. `wordsRead` dans `ReadingSession`
Nécessaire pour le WPM (Tâche D.4). Implique une migration DB, un calcul dans le renderer (compter les mots des phrases jouées).
> **Décision suggérée :** ajouter le champ et incrémenter depuis `ReaderViewModel` lors de l'auto-advance (A.1). Migration mineure.

### 3. `FilterMode.BY_AUTHOR` — vraie navigation
Le popup de navigation (Tâche C.4) expose `BY_AUTHOR` comme catégorie à part entière avec compteur. `LibraryUiState.availableAuthors` existe déjà.
> **Décision suggérée :** C.4 câble la navigation — pas de changement de domaine.

---

## Ce qu'on ne fait PAS pour la v1 (décisions actées)

| Fonctionnalité | Raison |
|---|---|
| OPDS | Réintégré en v1.x, borné au Volet 1 — voir `ADR-023` et `docs/execution/LOT_13_CATALOGUES_OPDS.md` |
| Sync cloud | Hors périmètre Blueprint — v1.x |
| PDF natif | ADR-017 — reporté |
| TXT renderer riche | Format trop ambigu structurellement |
| Double page tablette | Fondation WindowSizeClass posée, layout hors périmètre v1 |
| Navigation 3 | Alpha instable — Navigation 2.8+ typée retenu |
| `CustomHighlightToolbar` char-level | API publique Compose insuffisante (vérifié empiriquement) |

---

*Document généré le 2026-07-31 — à partir de l'audit complet du legacy `69d18a8` et de la réécriture actuelle (Phases 0–9bis).*
*Méthode : chaque tâche vient d'une lecture intégrale du fichier source correspondant, pas d'un survol.*
