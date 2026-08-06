# Lot 1 — Coquille de navigation

**Base :** `main` à `97ed564`. Référence cible : `UX_FLOW_DESIGN.md` § Drawer, § Bottom sheet 3-points, § À propos.

## Règle de clôture du lot

Claude Code **ne déclare pas ce lot terminé**. Il livre, il liste explicitement ce qu'il n'a pas pu vérifier lui-même, et la clôture se fait après vérification manuelle sur appareil (§ Vérifications device en fin de document).

## Contrat applicable à chaque tâche

1. **Atteignable** — tout écran touché a un chemin réel depuis le lancement de l'app.
2. **Zéro décoration** — aucun contrôle affiché sans logique branchée. Pas de `TODO`, pas de paramètre `= {}` non fourni par l'appelant. Un contrôle dont la destination n'existe pas **n'est pas affiché**.
3. **Testé** — test Compose sur les interactions qui portent une décision.
4. **Vérifié** — ce qui n'a pas pu être vérifié est signalé, jamais présenté comme validé.
5. **Écart déclaré** — tout écart au flux cible est écrit dans `UX_FLOW_DESIGN.md` au moment du lot.

## Décision de périmètre (actée)

Les destinations dont l'écran n'existe pas sont **masquées**, pas affichées en impasse : **Récents, Catalogues OPDS, Synchronisation, Thèmes** n'apparaissent pas dans le drawer à ce lot. Aucune route ni écran vide n'est créé pour elles. Elles apparaîtront à leur lot dédié.

Le drawer de ce lot porte donc **3 destinations + 2 boutons de pied**, pas 6 + 3. C'est un écart temporaire assumé au flux cible, à consigner (tâche 7).

---

## Tâche 1 — Rendre l'écran Réglages atteignable

**Problème :** `SettingsRoute` est déclaré (`Routes.kt:32-33`) et sa destination existe (`InkToneNavHost.kt:117-126`), mais aucun `navigate(SettingsRoute)` n'existe dans `app/src/main`. Par cascade, `AboutRoute` et `PronunciationRulesRoute` sont inatteignables — leur seule entrée est `SettingsScreen`.

**À faire :**
- Ajouter les paramètres `onOpenSettings` et `onOpenAbout` à l'appel de `LibraryScreen` dans `InkToneNavHost.kt:71-81`, pointant vers `navController.navigate(SettingsRoute)` et `navController.navigate(AboutRoute)`.
- `LibraryScreen` déclare déjà `onOpenAbout` (`LibraryScreen.kt:115`) — ajouter `onOpenSettings` à sa signature et le propager à `LibraryDrawerContent`.

**Ne pas faire :** créer un `BackScaffold` pour Réglages. `SettingsScreen` porte son propre `Scaffold`/`LargeTopAppBar` (`SettingsScreen.kt:79-91`), c'est délibéré.

`Corrige l'accès à l'écran Réglages depuis le drawer`

---

## Tâche 2 — Reconstruire le contenu du drawer

Dépend de la tâche 1.

**Cible pour ce lot** (`LibraryScreen.kt:249-356`) :

Navigation :
- **Bibliothèque** — élément actif par défaut, destination à part entière (pas un des `SelectableFilters`)
- **Marque-pages et Notes** — libellé exact, pas « Signets » → `BookmarksRoute`
- **Statistiques de lecture** — libellé exact, pas « Statistiques » → `StatisticsRoute`

Pied de page, rangée compacte à **2 boutons** (le 3e, Thèmes, arrive à son lot) :
- **Paramètres** → `SettingsRoute`
- **À propos** → `AboutRoute`

**À retirer :**
- l'item « Récents » (`LibraryScreen.kt:289-294`) — son `onClick` est `onSelectFilter(FilterMode.ALL, null)`, il ne change ni le tri ni l'écran ; son `selected` compare à un tri que le clic ne définit jamais. Contrôle mort.
- le bouton « Debug » (`LibraryScreen.kt:350-352`) — `onClick` vide, commenté `/* no-op pour l'instant */`.
- le bouton « Thème » du pied (`LibraryScreen.kt:348`) — appelle `onOpenThemePicker`, qui n'a aucune destination. Retirer aussi le paramètre `onOpenThemePicker` de la signature de `LibraryScreen` (`116`) et de `LibraryDrawerContent` (`255`) — pas de paramètre orphelin laissé en place.

**À conserver tel quel, transitoirement :** les sections Séries / Auteurs / Tags du drawer (`LibraryScreen.kt:307-338`) et la liste des `SelectableFilters` (`282-288`). Ces contrôles **fonctionnent** — ils appliquent un filtre réel. Le flux cible les déplace vers le menu déroulant du titre et son écran de détail, mais ce déplacement est le lot 2 : les retirer maintenant rendrait Séries et Tags inatteignables entre les deux lots. Laisser un commentaire `// Transitoire, lot 1 : déplacé vers le flyout du titre au lot 2 (UX §Menu déroulant du titre)`.

`Reconstruit le drawer avec ses destinations réelles`

---

## Tâche 3 — Nettoyer le bottom sheet 3-points

Le flux cible (§ Bottom sheet du menu 3-points) définit 5 actions. Deux entrées actuelles n'y figurent pas et sont mortes.

**À retirer** de `LibraryScreen.kt:483-509` :
- « À propos » (`505`) et « Thème » (`506`) — `onOpenAbout`/`onOpenThemePicker` n'étaient jamais fournis par `InkToneNavHost`, les deux ne faisaient rien. Ces deux entrées appartiennent au pied de drawer (tâche 2), pas ici.

**À conserver :** Importer, Régénérer les couvertures, Réinitialiser les couvertures, Actualiser.

**Hors périmètre de ce lot :** « Ouvrir un livre au hasard » et « Synchroniser avec le cloud » (actions 4 et 5 de la cible), ainsi que l'alignement des libellés sur « Couverture par défaut » / « Reconstruire les couvertures ». Ils relèvent du lot Bibliothèque.

Retirer en conséquence les paramètres `onOpenAbout`/`onOpenThemePicker` de `LibraryTopBar` (`LibraryScreen.kt:383-384`).

`Retire les deux actions mortes du menu 3-points`

---

## Tâche 4 — Corriger les icônes fausses

`AppIcons.Loading` vaut `Icons.Outlined.HourglassEmpty` (`AppIcons.kt:68`). Il est utilisé à deux endroits restants après les tâches 2-3 :

- **`LibraryScreen.kt:437`** — comme chevron du menu déroulant du titre, avec le commentaire `// flèche vers le bas via rotation` alors qu'aucune rotation n'est appliquée. C'est un sablier affiché à côté de « Bibliothèque ». Remplacer par `Icons.Filled.KeyboardArrowDown` (ou un `AppIcons.ChevronDown` ajouté à `AppIcons.kt`), et supprimer le commentaire devenu faux.
- **`LibraryScreen.kt:494`** — icône de l'action « Actualiser ». Remplacer par une icône de rafraîchissement réelle.

**Second point :** `AppIcons.CoverOnly` et `AppIcons.ReadingModePaged` pointent tous deux sur `Icons.Outlined.ViewDay` (`AppIcons.kt:74,80`). Le bouton de bascule de disposition (`LibraryScreen.kt:466-468`, `545-549`) affiche donc le même glyphe pour deux modes différents. Donner à `CoverOnly` un glyphe distinct.

**Ne pas toucher** au nombre de modes de disposition (3 aujourd'hui, 2 dans la cible) — c'est le lot Bibliothèque.

`Corrige les icônes fausses de la bibliothèque`

---

## Tâche 5 — Corriger les données codées en dur de l'écran À propos

Cet écran affiche des informations de diagnostic destinées au support ; une valeur fausse y est visible de l'utilisateur.

- **Version** : `AboutScreen(versionName: String = "0.1.0", ...)` (`core/ui/.../AboutScreen.kt:38`) est appelé **sans argument** (`InkToneNavHost.kt:155`). La valeur affichée est donc une constante littérale, pas `BuildConfig.VERSION_NAME`. `core:ui` n'a pas `buildConfig` activé (`core/ui/build.gradle.kts`) — passer la valeur depuis `app`, qui l'a (`InkToneApplicationConventionPlugin.kt:43`), via le paramètre existant. Ne pas activer `buildConfig` sur `core:ui`.
- **Année** : `"© 2026 InkTone."` (`AboutScreen.kt:90`) — rendre l'année dynamique.
- **Paramètre mort** : `onOpenUrl` (`AboutScreen.kt:38`) n'est jamais utilisé, l'écran passe par `LocalUriHandler` en interne (`39,85`). Le retirer.

**Hors périmètre :** badge de version cliquable, « Signaler un problème », accordéon des licences, grille à 3 piliers. Lot À propos dédié.

`Corrige la version et l'année codées en dur dans A propos`

---

## Tâche 6 — Retirer les captions TTS

Décision actée (UX § Lecture — couche TTS) : *« Les captions sont désactivées — jugées trop encombrantes, notamment sur les phrases longues. »*

Elles sont implémentées et actives : overlay noir à 65 % d'opacité, pleine largeur, en bas d'écran pendant toute la lecture TTS (`ReaderScreen.kt:311-331`).

Retirer le bloc. **Attention** au `liveRegion = LiveRegionMode.Polite` (`321`) qu'il porte : c'est l'annonce TalkBack de la phrase en cours. Ne pas la perdre silencieusement — soit la reporter sur un nœud sémantique sans rendu visuel, soit la déclarer comme perte assumée dans le rapport de livraison. Ce point est à signaler explicitement, pas à trancher seul.

`Retire les captions TTS du lecteur`

---

## Tâche 7 — Consigner l'écart de périmètre

Ajouter dans `UX_FLOW_DESIGN.md`, section Drawer, une note d'état :

> **État d'implémentation (lot 1) :** le drawer porte 3 destinations (Bibliothèque, Marque-pages et Notes, Statistiques de lecture) et 2 boutons de pied (Paramètres, À propos). Récents, Catalogues OPDS, Synchronisation et Thèmes sont **volontairement masqués** tant que leur écran n'existe pas — décision actée : aucune destination affichée sans écran derrière. Séries / Auteurs / Tags restent transitoirement dans le drawer jusqu'au lot 2, qui les déplace vers le flyout du titre.

`Consigne l'ecart de perimetre du drawer au lot 1`

---

## Tâche 8 — Tests Compose

`feature/library` n'a pas de dossier `androidTest`. Le plugin `inktone.feature` fournit déjà `ui-test-junit4` et `ui-test-manifest` (`InkToneFeatureConventionPlugin.kt:57-58`) — **aucune modification Gradle nécessaire**. Suivre le pattern de `feature/settings/src/androidTest/.../SettingsAccessibilityTest.kt` (`createAndroidComposeRule<ComponentActivity>`, composable sans état testé directement).

Extraire `LibraryDrawerContent` en composable sans état testable si nécessaire, comme `SettingsContent` l'a été.

Tests à écrire :
1. Chaque item de navigation du drawer déclenche bien son callback au clic (3 destinations).
2. Chaque bouton du pied déclenche bien son callback (2 boutons).
3. Le drawer **n'affiche pas** d'item « Récents », « Debug » ni « Thème » — test de non-régression sur le retrait, sinon rien n'empêche leur réapparition.
4. « Bibliothèque » est l'item marqué actif à l'état initial.

Le test 1+2 est le garde-fou du critère 2 : un contrôle qui n'appelle rien fait échouer le test.

`Ajoute les tests Compose du drawer de bibliothèque`

---

## Vérifications sur appareil (à faire manuellement, hors Claude Code)

Formulées en avant/après observables, pas en cases à cocher.

| # | Avant (état actuel `97ed564`) | Après attendu |
|---|---|---|
| 1 | Aucun chemin vers Réglages depuis l'app | Drawer → Paramètres ouvre Réglages |
| 2 | À propos inatteignable | Drawer → À propos ouvre l'écran ; Réglages → À propos fonctionne aussi |
| 3 | Règles de prononciation inatteignables | Réglages → « Règles de prononciation · Gérer » ouvre l'écran |
| 4 | Pied de drawer : « À propos » et « Thème » ne font rien au tap | Pied : 2 boutons, les deux ouvrent un écran |
| 5 | Drawer → « Récents » ne change rien à l'écran | L'item n'existe plus |
| 6 | Bottom sheet 3-points : « À propos » et « Thème » ne font rien | Les deux entrées ont disparu |
| 7 | Un sablier est affiché à côté du titre « Bibliothèque » | Un chevron vers le bas |
| 8 | Le bouton de disposition affiche le même glyphe pour Grille et Couvertures seules | Trois glyphes distincts pour les trois modes |
| 9 | À propos affiche « Version 0.1.0 » — inchangé même si `versionName` change | La version correspond au build réel (modifier `versionName` dans le plugin de convention et reconstruire pour le prouver) |
| 10 | Lancer le TTS : overlay noir de sous-titres en bas d'écran | Plus d'overlay ; le surlignage mot-à-mot reste actif |
| 11 | — | TalkBack : vérifier ce que devient l'annonce de la phrase en cours après retrait des captions (point signalé tâche 6) |

Le point 9 est le seul qui demande une manipulation volontaire pour être probant : afficher « 0.1.0 » ne prouve rien tant que la constante littérale vaut la même chose.

---

## Hors périmètre explicite de ce lot

À ne pas commencer, même si le code est sous la main : popup de filtrage, flyout du titre et écran de détail Séries/Tags, popup d'actions par livre, cœur au lieu de l'étoile, barre de progression en mode liste, réduction à 2 modes de disposition, barre du haut du Reader, ligne de statut, restructuration du panneau unifié, onboarding.
