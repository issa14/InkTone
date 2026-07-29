# Phase 8 — Réglages, statistiques, onboarding (avec récupération UX legacy)

**Dépend de :** Phase 2 (`PreferencesRepository`), Phase 5 (moteurs TTS, `VoiceModelDownloader` jamais câblé — Phase 5, checklist finale point 7), Phase 6 (bibliothèque)
**Précède :** Phase 9 — Durcissement transverse
**Référence :** Blueprint InkTone v1.2.2, §3.3 (`UserPreferences`/`EffectiveReadingSettings`), ADR-014 (consentement crash reporting), ADR-018 (téléchargement de voix)

**Origine des Tâches 8.3/8.4/8.5 :** audit systématique du code UI `legacy/monolith` (pas de sa documentation) — trois fonctionnalités confirmées absentes de toutes les phases précédentes après vérification croisée.

---

## Tâche 8.0 — Extensions de contrat, préalables à tout le reste

**Trouvées en planifiant**, pas découvertes en cours d'exécution — les rassembler ici évite de les redécouvrir une par une comme lors des phases précédentes (leçon explicitement retenue après la Phase 7).

`domain/src/main/kotlin/com/inktone/domain/model/UserPreferences.kt`, étendre :

```kotlin
data class UserPreferences(
    val theme: ReadingTheme = ReadingTheme.SYSTEM,
    val fontSize: Int = 18,
    val defaultTtsEngine: TtsEngineId = TtsEngineId.SHERPA_ONNX,
    val crashReportingEnabled: Boolean = false,
    val language: String = "fr",
    val fontFamily: FontFamily = FontFamily.DEFAULT,      // NOUVEAU, Tache 8.0bis
    val reduceMotion: Boolean = false,                     // NOUVEAU, Tache 8.0bis
) {
    init {
        require(fontSize > 0) { "fontSize doit être strictement positif" }
    }
}

enum class FontFamily { DEFAULT, OPEN_DYSLEXIC, SERIF, SANS_SERIF }
```

**Migration 2→3** (la table FTS était la 1→2, Tâche 7.3.1 — cette extension de `UserPreferences` est un changement de colonnes, migration distincte) :

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN fontFamily TEXT NOT NULL DEFAULT 'DEFAULT'")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN reduceMotion INTEGER NOT NULL DEFAULT 0")
    }
}
```

Test de migration obligatoire (même gabarit que Tâche 2.4/7.3.1) — **ne jamais sauter cette étape**, c'est la deuxième vraie migration du projet après la table FTS, l'occasion de confirmer que la discipline tient sur la durée, pas seulement au premier essai.

`domain/src/main/kotlin/com/inktone/domain/repository/BookmarkRepository.kt`, ajouter :
```kotlin
fun observeAll(): Flow<List<Bookmark>>  // necessaire pour BackupManager (Tache 8.5), aucune methode globale n'existait
```

`domain/src/main/kotlin/com/inktone/domain/repository/ReadingStateRepository.kt`, ajouter :
```kotlin
suspend fun getAll(): List<ReadingState>  // idem, necessaire pour BackupManager
```

**Commit :** `Etend UserPreferences (fontFamily, reduceMotion) et ajoute les methodes observeAll manquantes`

---

## Tâche 8.1 — UI réglages (fondation)

`feature/settings/src/main/kotlin/com/inktone/feature/settings/SettingsScreen.kt` :

```kotlin
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        SectionGroup("Lecture") {
            SettingRow("Thème", state.preferences.theme.name) { showThemePicker = true }
            SliderSetting("Taille du texte", state.preferences.fontSize.toFloat(), 12f..32f) {
                viewModel.onIntent(SettingsIntent.SetFontSize(it.toInt()))
            }
            SettingRow("Police", state.preferences.fontFamily.name) { showFontPicker = true }
        }
        SectionGroup("Voix") {
            SettingRow("Moteur par défaut", state.preferences.defaultTtsEngine.name) { showEnginePicker = true }
        }
        SectionGroup("Langue") {
            SettingRow("Langue de l'interface", state.preferences.language) { showLanguagePicker = true }
        }
    }
}
```

`SettingsViewModel` — MVI standard, chaque intent appelle `preferencesRepository.update(current.copy(...))` (Tâche 1.6/2.6, déjà fonctionnel). **Rien de nouveau côté domaine ici** — uniquement le branchement UI.

**Critère de validation :** modifier un réglage, tuer l'app, la rouvrir — le réglage persiste (`PreferencesRepository` Room-backed depuis la Phase 2, déjà testé en isolation, cette tâche prouve le chemin UI complet).

**Commit :** `Ajoute SettingsScreen (theme, taille, police, moteur TTS, langue)`

---

## Tâche 8.2 — Vérification de la cascade de précédence en conditions réelles

**Objectif :** `EffectiveReadingSettings.resolve()` (Tâche 1.3) est testé en isolation depuis la Phase 1 — jamais vérifié avec un vrai réglage global (8.1) **et** une vraie surcharge par publication en interaction.

**Point ouvert, à vérifier avant d'écrire le test** : aucune UI n'a jamais été construite pour **définir** une surcharge par publication (`ReadingOverrides`) — la Tâche 4.7 applique `EffectiveReadingSettings` au rendu, mais rien ne permet à l'utilisateur de créer la surcharge elle-même. C'est un trou, pas un oubli de cette tâche précise — à combler ici :

```kotlin
// Dans ReaderScreen (Tache 4.7) ou un menu contextuel dedie :
@Composable
fun ChapterOverrideMenu(currentOverrides: ReadingOverrides?, onSetOverride: (ReadingOverrides) -> Unit) {
    // Bascule "utiliser les reglages de ce livre" -> ecrit ReadingState.overrides
    // via UpdateReadingStateUseCase (Tache 1.8, deja complet)
}
```

**Test de bout en bout :**
```kotlin
@Test
fun surcharge_publication_prime_visiblement_sur_reglage_global() = runTest {
    preferencesRepository.update(UserPreferences(theme = ReadingTheme.LIGHT))
    updateReadingState(readingState.copy(overrides = ReadingOverrides(theme = ReadingTheme.DARK)))

    val effective = readerViewModel.state.value.effectiveSettings
    assertEquals(ReadingTheme.DARK, effective.theme) // la surcharge gagne, pas le global
}
```

**Commit :** `Ajoute l'UI de surcharge par publication et verifie la cascade de bout en bout`

---

## Tâche 8.3 — Règles de prononciation personnalisées (récupéré de l'audit UX legacy)

**Objectif :** l'utilisateur corrige comment le TTS prononce un mot ou motif précis — fonctionnalité spécifique au TTS, absente de toutes les phases précédentes malgré sa pertinence directe pour le pipeline déjà construit (Phase 5).

`domain/src/main/kotlin/com/inktone/domain/model/PronunciationRule.kt` :

```kotlin
data class PronunciationRule(
    val id: String,
    val originalText: String,
    val replacementText: String,
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true,
)
```

`domain/src/main/kotlin/com/inktone/domain/repository/PronunciationRuleRepository.kt` :
```kotlin
interface PronunciationRuleRepository {
    fun observeAll(): Flow<List<PronunciationRule>>
    suspend fun save(rule: PronunciationRule)
    suspend fun delete(id: String)
}
```

Implémentation Room + migration 3→4 (nouvelle table `pronunciation_rules`) — même gabarit que les migrations précédentes, test obligatoire.

### Application au pipeline TTS existant

**Point d'intégration précis** : avant la synthèse, pas à l'extraction — pour rester réversible si l'utilisateur modifie une règle sans réimporter le livre.

```kotlin
class PronunciationRuleApplier @Inject constructor(
    private val ruleRepository: PronunciationRuleRepository,
) {
    suspend fun apply(text: String): String {
        val rules = ruleRepository.observeAll().first().filter { it.isEnabled }
        return rules.fold(text) { acc, rule ->
            if (rule.isRegex) {
                runCatching { acc.replace(Regex(rule.originalText), rule.replacementText) }
                    .getOrElse { acc } // regex invalide utilisateur -> ignoree, jamais un crash
            } else {
                acc.replace(rule.originalText, rule.replacementText)
            }
        }
    }
}
```

**Brancher dans `AndroidNativeTtsEngine.synthesize()` et `SherpaOnnxTtsEngine.synthesize()`** (Phases 3/5) — modification des deux adaptateurs existants, pas une réécriture.

**Point d'attention non résolu, à trancher en écrivant le test** : les `WordTimestamp` retournés doivent rester alignés sur le texte **original** de la `Sentence` (le surlignage pointe vers le texte affiché à l'écran), pas sur le texte substitué envoyé au moteur. Si une règle change la longueur du texte (« Dr. » → « Docteur »), le mapping timestamp-vers-texte-affiché doit rester cohérent — à vérifier par un test dédié, pas supposé transparent :

```kotlin
@Test
fun une_regle_qui_allonge_le_texte_ne_desaligne_pas_le_surlignage() = runTest {
    pronunciationRuleRepository.save(PronunciationRule(id = "r1", originalText = "Dr.", replacementText = "Docteur"))
    val sentence = Sentence(0, "Dr. Martin est arrive.", startOffset = 0, endOffset = 22)

    val segment = sherpaOnnxTtsEngine.synthesize(sentence, voiceProfile)

    // Le premier WordTimestamp doit correspondre a "Dr." (3 caracteres,
    // texte AFFICHE), pas a "Docteur" (7 caracteres, texte ENVOYE au moteur).
    assertEquals(0, segment.wordTimestamps.first().charOffset)
    assertEquals(3, segment.wordTimestamps.first().charOffset + "Dr.".length)
}
```

`feature/settings/src/main/kotlin/com/inktone/feature/settings/PronunciationRulesScreen.kt` — liste + formulaire ajout/édition, switch regex, pattern MVI standard.

**Commit :** `Ajoute les regles de prononciation personnalisees et verifie l'alignement du surlignage`

---

## Tâche 8.4 — Préréglage d'accessibilité en un geste (récupéré de l'audit UX legacy)

**Objectif :** un bouton, plusieurs réglages appliqués ensemble — pas un parcours de cinq écrans séparés pour configurer une lecture confortable.

### `reducedMotionDuration` — à utiliser partout, pas juste ici

`core/designsystem/src/main/kotlin/com/inktone/core/designsystem/ReducedMotion.kt` :

```kotlin
/**
 * Repris de l'audit UX legacy — respecte le reglage SYSTEME Android
 * (echelle d'animation), pas seulement une preference applicative.
 */
@Composable
fun reducedMotionDuration(defaultMs: Int): Int {
    val context = LocalContext.current
    val isReduced = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f,
            ) == 0.0f
        } catch (_: SecurityException) { false }
    }
    return if (isReduced) 0 else defaultMs
}
```

**Action de suivi non optionnelle** : auditer les écrans déjà construits (Phases 4 à 7) pour toute animation qui n'utilise pas encore ce helper — corriger rétroactivement plutôt que de laisser une incohérence entre l'ancien et le nouveau code. Lister explicitement dans le rapport de tâche chaque animation trouvée et corrigée, pas une déclaration générale « fait ».

### Préréglage combiné

```kotlin
class ApplyAccessibilityPresetUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke() {
        val current = preferencesRepository.get()
        preferencesRepository.update(
            current.copy(
                fontSize = 24,
                theme = ReadingTheme.LIGHT,
                fontFamily = FontFamily.OPEN_DYSLEXIC,
                reduceMotion = true,
            ),
        )
    }
}
```

**Vérification avant intégration** : licence de la police OpenDyslexic (généralement libre, mais à confirmer à la source avant d'embarquer le fichier de police — même discipline que pour les voix TTS, ne pas supposer).

**Commit :** `Ajoute le preregalage d'accessibilite, porte reducedMotionDuration et corrige les animations existantes`

---

## Tâche 8.5 — Sauvegarde et restauration locale (récupéré de l'audit UX legacy)

**Objectif :** export/import JSON des métadonnées utilisateur — jamais les livres eux-mêmes.

```kotlin
data class BackupPayload(
    val appVersion: String,
    val createdAt: Long,
    val bookmarks: List<Bookmark>,
    val pronunciationRules: List<PronunciationRule>,
    val readingStates: List<ReadingState>,
    val readingSessions: List<ReadingSession>,
)

class BackupManager @Inject constructor(
    private val fileStorageService: FileStorageService, // ecriture deja ajoutee Tache 6.0
    private val bookmarkRepository: BookmarkRepository,   // observeAll() ajoute Tache 8.0
    private val pronunciationRuleRepository: PronunciationRuleRepository,
    private val readingStateRepository: ReadingStateRepository, // getAll() ajoute Tache 8.0
    private val readingSessionRepository: ReadingSessionRepository,
) {
    suspend fun exportTo(destinationUri: String) {
        val payload = BackupPayload(
            appVersion = BuildConfig.VERSION_NAME,
            createdAt = System.currentTimeMillis(),
            bookmarks = bookmarkRepository.observeAll().first(),
            pronunciationRules = pronunciationRuleRepository.observeAll().first(),
            readingStates = readingStateRepository.getAll(),
            readingSessions = readingSessionRepository.getAll(),
        )
        val json = Json.encodeToString(payload) // kotlinx.serialization — VERIFIER
        // que c'est bien la bibliotheque deja utilisee ailleurs dans le projet
        // avant de l'ajouter comme nouvelle dependance (le legacy utilisait
        // Gson, ne pas reproduire ce choix sans verifier la coherence avec
        // le reste du projet actuel).
        val tempFile = File.createTempFile("inktone-backup", ".json")
        tempFile.writeText(json)
        fileStorageService.writeToUri(destinationUri, tempFile)
        tempFile.delete()
    }

    suspend fun importFrom(sourceUri: String): ImportBackupResult {
        val json = fileStorageService.openInputStream(sourceUri)?.bufferedReader()?.readText()
            ?: return ImportBackupResult.Failed("Impossible de lire le fichier")

        val payload = runCatching { Json.decodeFromString<BackupPayload>(json) }
            .getOrElse { return ImportBackupResult.Failed("Fichier de sauvegarde invalide ou corrompu") }

        var restored = 0
        var skippedOrphans = 0

        // CORRECTIF par rapport au legacy : le legacy inserait sans verifier
        // que la Publication referencee existe encore (bookmarkDao.insert
        // direct) - une contrainte FK aurait fait planter tout l'import sur
        // un seul signet orphelin. Ici : verification explicite, comptage
        // separe, jamais un crash sur une donnee partiellement obsolete.
        payload.bookmarks.forEach { bookmark ->
            if (publicationRepository.getById(bookmark.publicationId) != null) {
                bookmarkRepository.insert(bookmark); restored++
            } else skippedOrphans++
        }
        payload.pronunciationRules.forEach { pronunciationRuleRepository.save(it); restored++ }
        payload.readingSessions.forEach { session ->
            if (publicationRepository.getById(session.publicationId) != null) {
                readingSessionRepository.insert(session); restored++
            } else skippedOrphans++
        }

        return ImportBackupResult.Success(restored = restored, skippedOrphans = skippedOrphans)
    }
}

sealed interface ImportBackupResult {
    data class Success(val restored: Int, val skippedOrphans: Int) : ImportBackupResult
    data class Failed(val message: String) : ImportBackupResult
}
```

**Commit :** `Ajoute la sauvegarde/restauration locale, corrige la gestion des orphelins par rapport au legacy`

---

## Tâche 8.6 — `feature/statistics`

**Objectif :** temps de lecture, livres terminés, séries de jours — basé sur `ReadingSession` (Phase 1/2, jamais exploité en UI jusqu'ici).

```kotlin
data class StatisticsUiState(
    val totalReadingTimeMs: Long = 0,
    val booksFinished: Int = 0,
    val currentStreakDays: Int = 0,
)

class GetStatisticsUseCase @Inject constructor(
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(): StatisticsUiState {
        val sessions = readingSessionRepository.getAll()
        val totalMs = sessions.sumOf { it.durationMs }

        // Reutilise la definition de FilterMode.READ (Tache 6.5.2, "dernier
        // chapitre atteint") - MEME heuristique, pas une deuxieme
        // definition de "termine" qui divergerait silencieusement.
        val finishedCount = publicationRepository.observeAll().first()
            .count { isConsideredFinished(it) }

        val streak = computeStreak(sessions.map { it.startedAt })

        return StatisticsUiState(totalMs, finishedCount, streak)
    }
}
```

**Point de cohérence explicite** : « livre terminé » doit utiliser **exactement** la même définition que `FilterMode.READ` (Tâche 6.5.2) — pas une deuxième heuristique inventée ici qui donnerait un chiffre différent de celui affiché dans les filtres de bibliothèque. Si `FilterMode.READ` change un jour (décision produit toujours ouverte depuis la Phase 6), les statistiques doivent changer avec, automatiquement — factoriser dans une seule fonction partagée (`domain/service/ReadingStatusEvaluator.kt` ou équivalent), pas dupliquer la logique.

**Commit :** `Ajoute feature-statistics, reutilise la definition de "termine" de la Phase 6`

---

## Tâche 8.7 — Onboarding

**Objectif :** ferme deux points laissés explicitement ouverts depuis les phases précédentes — le consentement crash reporting (ADR-014, jamais eu d'UI) et le téléchargement de voix (Tâche 5.6, mécanisme fait et testé mais jamais câblé — signalé dans la checklist de clôture de Phase 5).

```kotlin
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    when (state.step) {
        OnboardingStep.Welcome -> WelcomeStep(onNext = { viewModel.onIntent(OnboardingIntent.Next) })
        OnboardingStep.CrashConsent -> CrashConsentStep(
            // Texte honnete sur le contenu d'un rapport de crash (Blueprint
            // §10.7, ADR-014) - ce qui y figure (trace, version, modele
            // d'appareil), ce qui n'y figure JAMAIS (contenu des livres,
            // annotations). Defaut : DESACTIVE (opt-in, pas opt-out).
            onAccept = { viewModel.onIntent(OnboardingIntent.SetCrashReporting(true)) },
            onDecline = { viewModel.onIntent(OnboardingIntent.SetCrashReporting(false)) },
        )
        OnboardingStep.VoiceDownload -> VoiceDownloadStep(
            downloadProgress = state.downloadProgress, // VoiceModelDownloader, Tache 5.6, enfin utilise
            onStart = { viewModel.onIntent(OnboardingIntent.StartVoiceDownload) },
            onSkip = { viewModel.onIntent(OnboardingIntent.Next) }, // Palier 1 reste utilisable sans telecharger
        )
        OnboardingStep.Done -> { /* navigation vers LibraryScreen */ }
    }
}
```

**Rappel explicite (Blueprint §10.7, ADR-014)** : le choix de consentement doit rester modifiable à tout moment dans les réglages (Tâche 8.1) — vérifier que `SettingsScreen` expose bien un toggle crash reporting séparé, pas seulement disponible à l'onboarding une fois pour toutes.

**Commit :** `Ajoute l'onboarding avec consentement crash reporting et telechargement de voix initial`

---

## Tâche 8.8 — Tests du flux onboarding

```kotlin
@Test fun onboarding_accepte_le_crash_reporting() { /* ... */ }
@Test fun onboarding_refuse_le_crash_reporting() { /* ... */ }
@Test fun onboarding_reporte_le_telechargement_de_voix_sans_bloquer() { /* le Palier 1 doit rester utilisable */ }
```

**Commit :** `Teste les trois chemins de l'onboarding`

---

## Checklist finale de sortie de Phase 8

| # | Critère | Vérification |
|---|---|---|
| 1 | Extensions de contrat faites en une fois, migration 2→3 testée | Tâche 8.0 |
| 2 | Réglages persistés, vérifiés de bout en bout | Tâche 8.1 |
| 3 | Cascade de précédence vérifiée avec une vraie UI de surcharge (créée ici, pas supposée exister) | Tâche 8.2 |
| 4 | Règles de prononciation appliquées aux deux paliers TTS, alignement surlignage testé explicitement | Tâche 8.3 |
| 5 | Préréglage d'accessibilité + audit rétroactif des animations Phases 4-7 documenté | Tâche 8.4 |
| 6 | Backup/restore, gestion des orphelins corrigée par rapport au legacy | Tâche 8.5 |
| 7 | Statistiques cohérentes avec la définition de "terminé" de la Phase 6 (pas une deuxième heuristique) | Tâche 8.6 |
| 8 | Onboarding ferme ADR-014 et la Tâche 5.6 (téléchargement de voix enfin câblé) | Tâche 8.7 |
| 9 | Les 3 chemins d'onboarding testés | Tâche 8.8 |

Une fois les 9 critères vérifiés, Phase 8 est close. Étape suivante : **Phase 9 — Durcissement transverse** (accessibilité, sécurité, benchmarks complets — y compris la note `WindowSizeClass` laissée en suspens par l'audit UX legacy).
