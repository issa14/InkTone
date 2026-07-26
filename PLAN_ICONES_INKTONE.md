# Plan d'implémentation — Épuration emojis & modernisation des icônes (InkTone)

Audit réalisé directement sur `github.com/issa14/InkTone` (branche `main`). Chiffres et fichiers ci-dessous sont réels, pas des estimations.

---

## 0. Constat chiffré

- **749 occurrences** d'emoji/glyphes décoratifs sur tout le repo, dont l'immense majorité dans les `.md` (README, PROJECT_STATUS, architecture, Plan_d_action, CHANGELOG, CONTRIBUTING, audits...).
- **14 fichiers Kotlin de `app/src/main`** contiennent des emoji (hors tests) — c'est le vrai périmètre applicatif à traiter, le reste est documentaire.
- `compose-material-icons-extended` est **déclaré dans `libs.versions.toml` mais jamais ajouté à `app/build.gradle.kts`** — la dépendance n'est pas utilisée. L'app tourne donc uniquement sur le set Material Icons de base (~130 icônes), ce qui limite déjà le choix actuel.
- **116 usages `Icons.*`** existants (103 `Default`, 8 `Outlined`, 4 `AutoMirrored`, 1 `Filled`) — mélange de styles `Default`/`Outlined` sans convention visible (ex: `Headphones`, `Timer`, `Close` existent dans les deux variantes selon l'écran).
- Un seul drawable vectoriel custom (`ic_launcher_foreground.xml`) — pas de set d'icônes maison, tout passe par Material Icons.

---

## 1. Cas particuliers à traiter différemment (important, ne pas tout mettre dans le même sac)

| Cas | Fichiers | Traitement |
|---|---|---|
| **Emoji rendus dans l'UI lue par l'utilisateur** (`Text("🔖")`, messages de succès/erreur, labels de section) | `ReaderContent.kt`, `ReaderScreen.kt`, `LibraryViewModel.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `SyncSettingsScreen.kt`, `SyncSettingsViewModel.kt`, `AboutScreen.kt`, `AllBookmarksPanel.kt` | **Remplacer par `Icon()` Material** |
| **Emoji dans les logs uniquement** (`Log.i/d/w`, invisibles utilisateur) | `PerfLogger.kt` (11 occurrences) | **Supprimer purement**, pas de remplacement par icône — un logcat n'affiche pas de composable |
| **Emoji dans un écran de debug routé en prod** | `TtsTestScreen.kt` (18 occurrences, le pire fichier du repo) — **confirmé accessible depuis `InkToneNavGraph.kt` ligne 124** | Décision produit à prendre : sortir cet écran du build release (`debug`-only) ou le supprimer. Le nettoyer d'emoji ne suffit pas si un écran de test brut reste accessible aux utilisateurs. |
| **Hack existant de nettoyage d'emoji** | `LibraryScreen.kt:1230` — `error.trimStart('⚠', '❌', '✅', ' ', '️')` | Symptôme direct du problème : le code compense déjà la présence d'emoji dans les messages d'erreur. Une fois les emoji supprimés à la source (ViewModel), ce `trimStart` devient inutile et doit être retiré. |
| **Emoji dans la documentation** (README, PROJECT_STATUS, architecture.md, CHANGELOG, audits) | ~700 occurrences | Cosmétique, sans impact utilisateur final. Nettoyage simple (suppression, pas de remplacement par icône — un `.md` n'a pas de rendu Compose) si tu veux une doc "pro", mais priorité basse. |

---

## 2. Système d'icônes à adopter

**Recommandation : garder Material Symbols, mais le faire correctement.**

1. Ajouter la dépendance manquante dans `app/build.gradle.kts` :
   ```kotlin
   implementation(libs.compose.material.icons.extended)
   ```
   (l'alias existe déjà dans le catalogue, il n'est juste pas branché)
2. Activer le **R8 minification / shrinking des ressources** en release (vérifier si déjà actif) pour ne pas alourdir l'APK avec l'intégralité du set étendu.
3. Uniformiser le style : choisir **`Outlined` par défaut** partout (rendu plus fin/premium que `Default`/`Filled`), sauf éventuellement pour les actions primaires (Play/Pause) où `Filled` peut rester pour la lisibilité. Éliminer le mélange constaté sur `Headphones`, `Timer`, `Close`.
4. Centraliser dans un objet `AppIcons` (`ui/theme/AppIcons.kt`) — un point d'entrée unique au lieu d'appels `Icons.Outlined.X` dispersés dans 15+ fichiers. Facilite un futur changement de pack et rend l'audit futur possible en une recherche.

Pas besoin d'un pack custom (Phosphor/Lucide) vu le volume — Material Symbols Outlined bien utilisé et cohérent suffit pour un rendu premium, sans le coût d'intégration d'un pack tiers.

---

## 3. Séquence d'exécution pour Claude Code

Chaque étape = un commit séparé, validé par `./gradlew assembleDebug` avant de passer à la suivante (convention déjà suivie dans ce repo d'après `CHANGELOG.md`).

### Étape 1 — Setup
- Brancher `compose-material-icons-extended` dans `app/build.gradle.kts`.
- Créer `ui/theme/AppIcons.kt`.

### Étape 2 — Décision produit (bloquant, à valider par toi avant code)
- Que fait-on de `TtsTestScreen` ? (retrait du `NavGraph` release / suppression complète / conservation en debug-only). Sans réponse, Claude Code ne peut pas traiter ce fichier correctement.

### Étape 3 — Mapping
Produire `ICON_MAPPING.md` : tableau emoji → `AppIcons.xxx`, un fichier à la fois, dans cet ordre (du plus impactant au moins impactant) :
1. `ReaderContent.kt` (badges 🔖/📝 inline dans le texte lu — le plus visible)
2. `ReaderScreen.kt` (menu contextuel : Copier/Surligner/Note/Marque-page + tooltips 💡)
3. `LibraryViewModel.kt` + `LibraryScreen.kt` (snackbars de succès, + retrait du `trimStart` devenu inutile)
4. `SettingsScreen.kt` + `SettingsViewModel.kt` (en-têtes de section ⚡📖📱🎨♿💾🗣️, messages backup)
5. `SyncSettingsScreen.kt` + `SyncSettingsViewModel.kt` (statut connexion ✓)
6. `AboutScreen.kt`, `AllBookmarksPanel.kt`, `ReaderTtsPanel.kt`
7. `TtsTestScreen.kt` selon décision Étape 2
8. `PerfLogger.kt` — suppression simple, pas de mapping

### Étape 4 — Implémentation
Remplacer selon le mapping validé. Chaque remplacement suit le pattern existant du repo (`contentDescription` déjà correctement géré selon `PLAN_ACTION_UXUI_CLAUDECODE.md` — réutiliser la même logique décorative vs interactive).

### Étape 5 — Uniformisation du set existant
Passer les 116 usages `Icons.*` déjà en place par `AppIcons`, en tranchant `Outlined` vs `Filled` selon la règle de l'Étape 2 du §2.

### Étape 6 — Garde-fou
Script/check CI simple (regex Unicode emoji) qui échoue sur tout emoji ajouté dans `app/src/main/**/*.kt`, hors zone de test. Évite la réintroduction (le repo montre un pattern récurrent d'emoji ajoutés au fil des sessions).

### Étape 7 — Documentation (optionnel, priorité basse)
Nettoyage des `.md` — simple suppression, script de remplacement, pas de mapping icône nécessaire.

---

## 4. Point de vigilance

Ce chantier touche des fichiers déjà identifiés comme sensibles ailleurs (`ReaderViewModel`, `ReaderScreen`, `LibraryViewModel` reviennent dans plusieurs audits précédents du repo). Vérifier qu'aucun remplacement d'emoji n'interfère avec les correctifs déjà appliqués (Phase 5b/5c/5d selon `PROJECT_STATUS.md`) — en particulier ne pas re-casser le `ErrorBanner` Material 3 déjà aligné en Phase 5c en touchant à nouveau `LibraryScreen.kt`.
