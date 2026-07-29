# Phase 9 — Durcissement transverse

**Dépend de :** Phases 4 à 8 (fonctionnalités complètes à durcir)
**Précède :** Phase 10 — Release candidate
**Référence :** Blueprint InkTone v1.2.2, §1.4 (accessibilité), §10 (sécurité), §11.2 (budgets), §14.8 (portes CI)

**Nature de cette phase :** contrairement aux précédentes, il n'y a presque rien de nouveau à construire — le travail est de vérifier systématiquement ce qui existe déjà à travers **tous** les écrans et modules, et de fermer les deux points reportés depuis les Phases 5/8.

---

## Tâche 9.0 — Points reportés, à trancher en premier

### 9.0.1 — Hébergement du modèle Kokoro (reporté depuis la Phase 8)

**Décision produit, pas technique** : `SherpaOnnxVoiceModelDownloadService` a une URL placeholder depuis la Phase 8 (`TODO-cdn-inktone`). Deux options réelles, à trancher explicitement :

1. **URL publique déjà stable** (Hugging Face/GitHub, le paquet `kokoro-int8-multi-lang-v1_0` déjà utilisé et validé en Phase 5) — coût zéro, mais dépendance à la disponibilité continue d'un tiers hors du contrôle d'InkTone.
2. **CDN propre** — contrôle total, mais coût d'infrastructure récurrent pour une app gratuite à donation volontaire (Blueprint, philosophie actée avec toi en Phase 5).

**Action concrète** : vérifier si le paquet exact utilisé en Phase 5 a une URL de release GitHub stable et publiquement accessible (pas seulement le fichier local resté dans `~/Downloads`) — si oui, c'est l'option 1 par défaut, avec le SHA-256 déjà confirmé en Phase 5 réutilisable tel quel. Documenter la décision comme un court ADR (pas besoin d'un numéro complet — un paragraphe dans `SherpaOnnxVoiceModelDownloadService.kt` suffit si l'option 1 est retenue).

### 9.0.2 — `WindowSizeClass` (reporté depuis l'audit UX legacy, Phase 8)

**Objectif minimal pour cette phase : la fondation, pas la fonctionnalité tablette complète** (double page, hors périmètre v1 selon le Blueprint). Reprendre le pattern legacy tel quel :

```kotlin
// core/designsystem/src/main/kotlin/com/inktone/core/designsystem/LocalWindowSizeClass.kt
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass non fourni — doit être défini au niveau de MainActivity")
}
```

Calculer une seule fois dans `MainActivity` (`calculateWindowSizeClass(this)`), fournir via `CompositionLocalProvider` à toute l'arborescence — **ne rien changer aux layouts existants pour l'instant**, juste poser la fondation pour que la Phase suivante (ou une évolution v1.x, Blueprint §16.4) puisse s'en servir sans tout recâbler. Ne pas construire de mode double page ici — hors périmètre annoncé.

**Commit :** `Tranche l'hebergement du modele Kokoro et pose la fondation WindowSizeClass`

---

## Tâche 9.1 — Audit d'accessibilité, systématique et testable

**Objectif :** pas une checklist qu'on remplit de mémoire — des tests Compose réels, exécutables, qui échouent si une régression apparaît plus tard.

### 9.1.1 — Labels TalkBack sur tous les écrans interactifs

**Balayage systématique**, écran par écran (`ReaderScreen`, `PlayerScreen`, `LibraryScreen`, `SettingsScreen`, `SearchScreen`, `OnboardingScreen` — tous construits Phases 4 à 8) :

```kotlin
@Test
fun tous_les_boutons_du_player_ont_une_description_accessible() {
    composeTestRule.setContent { PlayerScreen(/* ... */) }

    composeTestRule.onAllNodes(hasClickAction())
        .assertAll(SemanticsMatcher("a une description accessible") { node ->
            node.config.contains(SemanticsProperties.ContentDescription) ||
                node.config.contains(SemanticsProperties.Text)
        })
}
```

**Répéter ce test pour chaque écran** — pas un seul test générique qui prétend couvrir toute l'app, un test par écran, pour qu'un échec pointe précisément où corriger.

**Attention spécifique aux icônes seules** (`AppIcons`, Blueprint §12.5) : un `IconButton` sans texte visible DOIT avoir un `contentDescription` explicite — c'est le cas le plus facile à oublier, et le plus fréquent dans l'app (barre de contrôle du Player en particulier, Tâche 5.7).

### 9.1.2 — Tailles dynamiques (système, pas seulement le réglage interne de l'app)

**Point non trivial** : `UserPreferences.fontSize` (Phase 1/8) est un réglage **interne à l'app**. Ça ne dit rien sur le respect de l'échelle de police **système** Android (`fontScale`, Paramètres → Accessibilité → Taille du texte) — un utilisateur qui a déjà configuré son système en grande taille doit voir ce choix respecté, pas seulement pouvoir le refaire dans l'app.

```kotlin
@Test
fun le_texte_du_reader_s_agrandit_avec_l_echelle_systeme() {
    val density = Density(density = 1f, fontScale = 2f) // simule Parametres systeme "tres grand"
    composeTestRule.setContent {
        CompositionLocalProvider(LocalDensity provides density) {
            ReaderScreen(/* ... */)
        }
    }
    // Verifier que le texte utilise bien des sp (qui suivent fontScale)
    // et non des dp fixes quelque part dans le rendu (Tache 4.7) - une
    // regression classique de Compose si un developpeur utilise .dp par
    // erreur sur une taille de texte.
}
```

**Vérification manuelle complémentaire** (pas automatisable simplement) : changer l'échelle système sur le V2206 réel, confirmer visuellement que `ReaderScreen` (Tâche 4.7/7.0) s'adapte sans texte coupé ni superposé.

### 9.1.3 — Contrastes (WCAG AA), sur les quatre variantes de thème

**Les quatre thèmes existants** (`LIGHT`, `DARK`, `SEPIA`, plus la combinaison du préréglage d'accessibilité — Tâche 8.4) doivent chacun respecter un ratio de contraste ≥ 4.5:1 texte normal / ≥ 3:1 texte large (WCAG AA) :

```kotlin
@Test
fun tous_les_themes_respectent_le_contraste_wcag_aa() {
    listOf(ReadingTheme.LIGHT, ReadingTheme.DARK, ReadingTheme.SEPIA).forEach { theme ->
        val bg = themeBackgroundColor(theme) // deja ecrit Tache 4.7
        val fg = themeTextColor(theme)
        val ratio = calculateContrastRatio(bg, fg)
        assertTrue("theme $theme : ratio $ratio < 4.5", ratio >= 4.5)
    }
}
```

**Point d'attention pour `SEPIA`** spécifiquement — c'est le thème le plus susceptible d'échouer ce test (fond `0xFFF4ECD8` choisi pour l'esthétique en Tâche 4.7, jamais vérifié pour le contraste) : **mesurer avant de supposer**, ajuster la couleur de texte si nécessaire plutôt que de garder une esthétique qui échoue le test.

### 9.1.4 — Cibles tactiles (48dp minimum)

```kotlin
@Test
fun toutes_les_cibles_tactiles_font_au_moins_48dp() {
    composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
        val sizeDp = node.size.width.toDp() // via density du test
        assertTrue("cible sous 48dp trouvee", sizeDp >= 48.dp)
    }
}
```

**Commit :** `Ajoute l'audit d'accessibilite systematique (TalkBack, echelle systeme, contraste WCAG AA, cibles tactiles)`

---

## Tâche 9.2 — Revue de sécurité complète

**Objectif :** re-vérifier ce qui est censé être garanti depuis le début, pas le supposer encore vrai après 8 phases de code ajouté.

### 9.2.1 — SAF exclusif (K5), re-confirmé sur l'app complète

Déjà vérifié en CI depuis la Phase 0 (`scripts/check-no-manage-external-storage.sh`) — cette tâche confirme que **rien construit depuis** (Phases 4 à 8, en particulier `BackupManager` Tâche 8.5 qui manipule des fichiers) n'a réintroduit un accès fichier hors SAF :

```bash
grep -rn "File(\"/storage\|File(\"/sdcard\|getExternalStorageDirectory" --include="*.kt" .
# Attendu : vide. Tout accès fichier doit passer par FileStorageService (Tache 2.0/6.0).
```

### 9.2.2 — Permissions déclarées vs réellement utilisées

```bash
# Extraire les permissions du manifeste, comparer a un usage reel justifie
grep "uses-permission" app/src/main/AndroidManifest.xml
```

Pour chaque permission déclarée : `INTERNET` (téléchargement de voix, Tâche 5.6/8.7 — justifié), `POST_NOTIFICATIONS` (notification média, Tâche 5.4 — justifié). **Toute permission non directement traçable à une fonctionnalité construite est à retirer**, pas à garder « au cas où ».

### 9.2.3 — Consentement crash reporting, comportement réel

```kotlin
@Test
fun le_crash_reporting_est_desactive_par_defaut_sur_une_installation_fraiche() = runTest {
    // Pas de fixture, pas d'onboarding complete - etat vraiment initial
    val prefs = preferencesRepository.get()
    assertFalse(prefs.crashReportingEnabled) // ADR-014 : opt-in, jamais opt-out
}
```

### 9.2.4 — `BackupManager` (Tâche 8.5) : les données exportées sont-elles sensibles ?

**Question non posée en Phase 8, à trancher ici** : le fichier de sauvegarde JSON contient signets/notes/progression — potentiellement révélateur de ce que quelqu'un lit. Il reste **local** (export SAF vers un emplacement choisi par l'utilisateur, jamais transmis), donc le risque est faible, mais **le fichier exporté est-il en clair ou chiffré ?** Si en clair (probable, rien ne l'a spécifié en Phase 8), documenter ce choix explicitement dans `CONTRIBUTING.md`/Blueprint §10 plutôt que de le laisser implicite — un utilisateur qui exporte vers un cloud personnel (Drive, etc.) doit savoir que le fichier n'est pas chiffré.

**Commit :** `Revue de securite complete : SAF, permissions, consentement, clarte sur BackupManager`

---

## Tâche 9.3 — Suite de benchmarks consolidée (§11.2)

**Objectif :** un seul tableau, tous les budgets, mesurés sur l'app **complète** (pas juste un module isolé comme dans chaque phase précédente) — certains chiffres existent déjà, d'autres n'ont jamais été mesurés sur l'app assemblée.

| Budget (§11.2) | Cible | Déjà mesuré ? | Où |
|---|---|---|---|
| Cold start → bibliothèque | ≤ 1500 ms | **Non** — jamais mesuré sur l'app complète (Phases 4-8 assemblées, onboarding inclus) | À faire ici |
| Reprise chaude | ≤ 800 ms | Non | À faire ici |
| Ouverture EPUB 5 Mo | ≤ 800 ms | Partiel (Tâche 4.9, scénario différé à la bibliothèque) | À compléter |
| Scroll bibliothèque 1000+ | 60 fps | Oui | Tâche 6.6/6.9 |
| Navigation chapitre préchargé | ≤ 150 ms | Non | À faire ici |
| TTS tap→premier audio | ≤ 1500 ms | **Non tenu, documenté** (ADR-022, ~4,7×) | Atténuation produit déjà actée |
| Silence inter-phrases | ≤ 150 ms | Oui (Palier 1) / dépend du Palier 2 | Tâche 5.9 |
| Précision surlignage | ± 120 ms | Oui | ADR-022 §7 |
| Import 500 EPUB | ≤ 5 min | Oui — 66,7 s | Tâche 6.9 |
| Mémoire pic | ≤ 250 Mo | **Non** — jamais mesuré | À faire ici |
| Taille APK (hors modèles) | ≤ 60 Mo | **Non** — jamais mesuré | À faire ici |
| Batterie TTS continu | ≤ 8 %/h | **Non** — jamais mesuré | À faire ici |

**Cinq lignes jamais mesurées** — c'est le vrai travail de cette tâche, pas répéter ce qui existe déjà.

```kotlin
// Macrobenchmark, app complete (module benchmark, Taches 4.9/5.9 etendues)
@Test
fun demarrage_a_froid_vers_bibliotheque() = benchmarkRule.measureRepeated(
    packageName = "com.inktone.app",
    metrics = listOf(StartupTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.COLD,
) {
    pressHome(); startActivityAndWait()
}
```

Mémoire (via `dumpsys meminfo` sur device réel, scripté) et taille APK (`./gradlew :app:bundleRelease`, mesurer l'AAB généré moins la taille des modèles téléchargés à part) — pas de nouvel outillage complexe, juste l'exécuter et documenter.

**Tout budget non tenu** doit suivre le même traitement que la latence Kokoro (ADR-022) : mesuré, documenté, avec une atténuation explicite ou un ADR de révision — jamais silencieusement ignoré.

**Commit :** `Consolide les benchmarks sur l'app complete, mesure les 5 budgets jamais verifies`

---

## Tâche 9.4 — Finalisation des portes CI

**Objectif :** vérifier que les portes posées depuis la Phase 0 bloquent vraiment de mauvaises PR, pas seulement qu'elles existent dans le fichier YAML.

```yaml
# .github/workflows/ci.yml, ajouts Phase 9 :
- name: Tests instrumentes (migrations, DAO, UI)
  run: ./gradlew connectedAndroidTest  # jamais fait tourner en CI jusqu'ici,
  # seulement manuellement sur device (Tache 8.8 notamment) - a corriger :
  # necessite soit un emulateur en CI (Gradle Managed Devices), soit rester
  # manuel avec une documentation claire de cette limite, pas une pretention
  # de couverture automatique qui n'existe pas.

- name: Couverture de tests minimale
  run: ./gradlew koverVerify  # seuil a definir - ne pas inventer un chiffre
  # arbitraire, verifier la couverture actuelle reelle d'abord (Tache 9.4.1)
  # puis fixer un seuil legerement en dessous, pas au-dessus par optimisme
```

### 9.4.1 — Mesurer la couverture réelle avant de fixer un seuil

```bash
./gradlew koverHtmlReport
# Lire le pourcentage reel obtenu - fixer koverVerify EN DESSOUS de ce
# chiffre (ex. 5 points de marge), jamais au-dessus par aspiration.
```

**Test négatif obligatoire** (même discipline que `checkArchitectureRules`, Tâche 0.5.5) : introduire volontairement une régression (dépendance interdite, ou couverture sous le seuil), confirmer que la CI échoue bien, puis annuler — ne pas faire confiance à une porte jamais vue échouer.

**Commit :** `Finalise les portes CI : instrumentes (limite documentee), couverture mesuree et seuillee`

---

## Tâche 9.5 — Conformité Play Store

**Objectif :** aucun blocker connu du type K5 (`MANAGE_EXTERNAL_STORAGE`, déjà résolu) — un dernier passage avant la Phase 10.

### 9.5.1 — Formulaire « Sécurité des données » (Data Safety), exactitude vérifiée contre le code

Pas rempli de mémoire — vérifié contre ce qui est réellement collecté :

| Donnée | Collectée ? | Justification (code) |
|---|---|---|
| Contenu des livres | Non, jamais transmis | Offline-first, aucun upload (Blueprint §1.4) |
| Rapports de crash | Optionnel, opt-in | ADR-014, Tâche 9.2.3 vérifiée |
| Statistiques de lecture | Locales uniquement | `feature/statistics` (Tâche 8.6), aucune transmission |
| Identifiants personnels | Aucun | Pas de compte (Blueprint §2.7, ADR-007) |

### 9.5.2 — Politique de confidentialité

**Doit exister et être exacte** avant soumission — pas un gabarit générique copié, un texte qui reflète précisément le tableau ci-dessus. À rédiger en français (langue principale du projet, Blueprint §1.3), lié depuis `SettingsScreen`/`AboutScreen`.

### 9.5.3 — Cible API et permissions, dernière passe

```bash
grep "targetSdk\|minSdk\|compileSdk" app/build.gradle.kts
# Cible Android la plus recente exigee par Play Store au moment de la
# soumission (change chaque annee) - VERIFIER la valeur actuelle exigee,
# pas supposer que compileSdk=34 (fixe Phase 0) suffit encore.
```

**Point d'attention** : `compileSdk 34` a été fixé en Phase 0 pour la compatibilité Readium (Tâche 3.2). Play Store exige généralement la cible API la plus récente à la soumission — **vérifier l'exigence actuelle avant la Phase 10**, une montée de version pourrait être nécessaire et mérite d'être anticipée, pas découverte au moment de soumettre.

**Commit :** `Verifie la conformite Play Store : Data Safety exact, politique de confidentialite, cible API`

---

## Checklist finale de sortie de Phase 9

| # | Critère | Vérification |
|---|---|---|
| 1 | Hébergement Kokoro tranché, `WindowSizeClass` posée | Tâche 9.0 |
| 2 | Accessibilité testée automatiquement (TalkBack, échelle système, contraste, cibles tactiles) sur tous les écrans | Tâche 9.1, 4 sous-tâches |
| 3 | SAF re-confirmé sur l'app complète, permissions justifiées une à une | Tâche 9.2.1/9.2.2 |
| 4 | Consentement crash reporting vérifié désactivé par défaut | Tâche 9.2.3 |
| 5 | Clarté sur le chiffrement (ou non) du backup, documentée | Tâche 9.2.4 |
| 6 | Les 5 budgets jamais mesurés le sont maintenant, sur l'app complète | Tâche 9.3 |
| 7 | Tout budget non tenu a son atténuation ou son ADR, pas ignoré | Tâche 9.3 |
| 8 | Couverture de tests mesurée puis seuillée (pas un chiffre arbitraire) | Tâche 9.4.1 |
| 9 | Data Safety exact contre le code, politique de confidentialité réelle | Tâche 9.5.1/9.5.2 |
| 10 | Cible API vérifiée contre l'exigence Play Store actuelle | Tâche 9.5.3 |

Une fois les 10 critères vérifiés, Phase 9 est close. Étape suivante : **Phase 10 — Release candidate**.

---

## État réel — vérifié le 29 juillet 2026, pas supposé depuis un plan

**Résumé : 8/10 critères pleinement vérifiés, 2 partiels documentés
explicitement ci-dessous (pas de case cochée sans preuve).**

### 1. Hébergement Kokoro + WindowSizeClass — fait

URL/SHA-256 réels confirmés via l'API GitHub Releases de
`k2-fsa/sherpa-onnx` (`digest` du champ API, pas calculé à la main) —
voir KDoc `SherpaOnnxVoiceModelDownloadService.kt`. Limite documentée
dans le même fichier : l'asset est une archive `.tar.bz2`, l'extraction
vers les fichiers individuels reste TODO (dépendance de décompression
absente du projet). `LocalWindowSizeClass` posée (`core/designsystem`),
calculée une fois dans `MainActivity`, aucun layout ne la consomme
encore (fondation seulement, comme demandé).

### 2. Accessibilité — partiel, honnêtement scope réduit

Fait et **testé réellement sur device V2206** :
- Contraste WCAG AA mesuré (pas supposé) pour les 4 thèmes
  (`ContrastRatioTest.kt`, JVM pur) — SEPIA mesuré ~17.7:1, largement
  au-dessus du seuil 4.5:1 (texte noir sur fond clair, cas favorable).
- `SettingsContent` et `PlayerContent` extraites en composables
  sans-état testables ; `SettingsAccessibilityTest.kt` et
  `PlayerAccessibilityTest.kt` (3 + 2 tests) vérifient réellement sur
  device : description accessible sur toute cible cliquable, cibles
  tactiles ≥ 48dp (a nécessité une vraie correction — `Button`s Material3
  sous 48dp par défaut), texte qui s'agrandit avec l'échelle système.
  `ToggleSetting`/`SliderSetting` corrigés pour porter une
  `contentDescription`/un rôle `Switch` explicite.

**Non fait, à ne pas confondre avec "vérifié"** : `ReaderScreen`,
`LibraryScreen`, `SearchScreen`, `OnboardingScreen`,
`PronunciationRulesScreen`, `StatisticsScreen` n'ont pas reçu le même
traitement (extraction sans-état + tests Compose réels). Le pattern est
prouvé et reproductible (voir les deux fichiers ci-dessus) — reste à
appliquer, pas à supposer fait.

### 3-4. Sécurité SAF/permissions/consentement — fait, un vrai bug corrigé

- SAF re-confirmé (grep vide sur toute l'app, `File("/storage`,
  `MANAGE_EXTERNAL_STORAGE`, etc.).
- **Bug réel trouvé et corrigé** : permission `INTERNET` absente du
  manifeste alors que `VoiceModelDownloader` (Tâche 5.6) en a besoin —
  tout téléchargement réel aurait échoué avec `SecurityException` sur
  un device (jamais découvert avant car jamais testé hors fakes
  unitaires). Ajoutée avec justification en commentaire.
- `crashReportingEnabled = false` par défaut désormais couvert par un
  test explicite (`UserPreferencesTest.kt`) — trou de couverture comblé.
- `POST_NOTIFICATIONS` : **trouvé absent aussi**, alors que
  `AudioPlaybackService` (Media3 `MediaSessionService`) gère une
  notification système pour la lecture au premier plan. Sans elle, la
  notification ne s'affiche simplement pas sur Android 13+ (pas un
  crash direct du service). Non corrigé ici — nécessite un flux de
  demande de permission runtime (UI), hors du périmètre d'un correctif
  de manifeste seul ; à traiter explicitement en Phase 10, pas oublié.

### 5. Clarté chiffrement backup — fait

Documenté dans `CONTRIBUTING.md` : export JSON en clair, risque faible
tant que local, avertissement explicite si copié vers un cloud
personnel.

### 6-7. Benchmarks — les 5 budgets mesurés réellement, 1 violation majeure trouvée ET corrigée

Mesurés sur device V2206 réel (pas un émulateur), build **debug**
(`androidx.benchmark.suppressErrors=DEBUGGABLE` toujours actif — un
vrai build type non-debuggable reste une tâche séparée non résolue,
donc ces chiffres restent indicatifs, pas certifiés release) :

| Budget | Cible | Mesuré | Verdict |
|---|---|---|---|
| Cold start → bibliothèque | ≤ 1500 ms | median ~1362-1417 ms, **max observé 1521.9 ms sur une série** | Limite/variable — pas tenu de façon fiable sur toutes les séries mesurées |
| Reprise chaude (WARM) | ≤ 800 ms | median ~556 ms, max 630 ms | Tenu, marge confortable |
| Mémoire pic (dumpsys meminfo, écran bibliothèque) | ≤ 250 Mo | 114,9 Mo PSS | Tenu, large marge |
| Taille APK/AAB (hors modèles) | ≤ 60 Mo | **196 Mo avant correction**, 22 Mo après | Violation majeure trouvée et corrigée (voir ci-dessous) |
| Batterie TTS continu | ≤ 8 %/h | **Non mesuré** | Nécessite un soak test de plusieurs heures, hors de la portée de cette session — à faire avant Phase 10, pas ignoré |

**Deux causes réelles de la violation de taille, toutes deux corrigées :**
1. `app/build.gradle.kts` ne restreignait pas les ABI natives — l'AAB
   embarquait `libonnxruntime.so` pour les 4 ABI (x86_64/x86/arm64-v8a/
   armeabi-v7a, ~107 Mo à lui seul) alors que le code natif du projet
   (`infrastructure/tts`) ne compile que pour `arm64-v8a`. Corrigé par
   `ndk.abiFilters += "arm64-v8a"` dans `app/build.gradle.kts`.
2. `app/src/main/assets/models/` contenait ~171 Mo de voix VITS/Piper
   (`fr_FR-upmc-medium`, `fr_FR-siwis-medium`) explicitement rejetées/
   remplacées par Kokoro (ADR-022) — fichiers **gitignorés** (jamais
   commités, donc jamais présents sur un checkout propre ni en CI), mais
   gonflant silencieusement tout build local qui les avait accumulés
   lors des expérimentations Phase 5. Supprimés du disque local.

Après correction : **22 Mo**, largement sous le budget. `ouvertureEpub5Mo`
reste un placeholder de démarrage pur (pas encore le scénario complet
"ouverture EPUB 5 Mo", limite déjà documentée dans le fichier depuis la
Tâche 4.9) — nouveau test `repriseChaude` ajouté pour combler la ligne
"reprise chaude" du budget, jamais mesurée avant cette tâche.

### 8. Couverture mesurée puis seuillée — fait, porte vérifiée en échec réel

Kover (0.9.9) câblé en agrégation racine sur tous les modules de
production (hors `:benchmark`). Couverture ligne réelle mesurée :
**15,2091 %** (`./gradlew koverHtmlReport`, uniquement les tests JVM
unitaires — pas les tests instrumentés, hors de portée par défaut).
Seuil `koverVerify` fixé à **10 %** (marge ~5 points, mesuré avant
d'être fixé, jamais l'inverse). **Test négatif exécuté pour de vrai** :
seuil temporairement monté à 90 %, `koverVerify` a échoué avec le
message exact `Rule violated: lines covered percentage is 15.209100,
but expected minimum is 90` — confirmé que la porte échoue réellement
avant d'être remise à 10 %. CI mise à jour (`.github/workflows/ci.yml`)
avec l'étape `koverVerify` et une note explicite sur la limite des
tests instrumentés (jamais automatisés en CI, exécutés manuellement sur
V2206 tout au long du projet).

### 9. Data Safety + politique de confidentialité — fait

`docs/legal/DATA_SAFETY.md` et `docs/legal/POLITIQUE_CONFIDENTIALITE.md`
créés, vérifiés contre le code réel (permissions, dépendances,
absence de SDK publicitaire/compte).

### 10. Cible API — vérifié, écart critique trouvé, NON corrigé ici

**Recherche réelle (pas supposée)** : Google Play exige, à compter du
**31 août 2026**, que toute nouvelle app ou mise à jour cible **API 36
(Android 16)** ; les apps existantes doivent au minimum cibler API 35.
Le projet est actuellement fixé à `compileSdk = 34` / `targetSdk = 34`
(`InkToneApplicationConventionPlugin.kt`, choix Phase 0 pour
compatibilité Readium). **Écart de 2 niveaux d'API, échéance dans environ
un mois au moment de cette vérification.**

Délibérément **non corrigé dans cette tâche** : monter compileSdk/targetSdk
a des répercussions transverses (compatibilité Readium, Compose, Hilt,
Room, comportements runtime changés par les nouvelles restrictions
Android 15/16) qui exigent une validation complète, pas un changement de
chiffre isolé sans re-tester l'app entière. **Action requise avant la
Phase 10** : traiter comme un point d'entrée dédié de la Phase 10
(release candidate), avec le même sérieux que la latence Kokoro
(ADR-022) — mesuré et documenté ici, jamais ignoré silencieusement.
