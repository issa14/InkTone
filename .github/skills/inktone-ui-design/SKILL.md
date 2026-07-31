---
name: inktone-ui-design
description: 'Standards de design UI/UX InkTone et méthodologie de validation device. Use when: créer/modifier un composant UI, implémenter un écran, valider un spike visuel, choisir des couleurs/thèmes/icônes, définir une interaction de lecture, ou toute question de typographie/espacement/hiérarchie visuelle. Combine les patterns des apps de lecture top-tier (Apple Books, Kindle, Kobo, Play Books) avec les règles Material You du projet et la procédure spike → protocole device → preuves → cleanup.'
argument-hint: 'Composant, écran, ou interaction à concevoir/valider'
---

# Design UI/UX — Standards et Validation InkTone

## 1. Hiérarchie de l'information

Inspiré d'Apple Books, Kindle, Google Play Books et Kobo.

L'écran de lecture est organisé en **4 niveaux de priorité visuelle** :

| Niveau | Rôle | Éléments | Visibilité |
|---|---|---|---|
| **Primaire** | Le texte lu | Contenu du livre (WebView/Compose) | Toujours visible, plein écran |
| **Secondaire** | Progression | Numéro de page, pourcentage, temps restant | Visible, discret en bas d'écran |
| **Tertiaire** | Contrôles | TopAppBar, BottomBar, drawer | Caché par défaut, tap center pour révéler |
| **Transitoire** | Actions ponctuelles | Menu de sélection, popup dictionnaire, dialogue thèmes | Apparaît au geste, disparaît après action |

**Règle** : le chrome (niveau tertiaire) ne doit **jamais** couvrir le texte
de façon permanente. Pattern standard : tap au centre → toggle chrome,
auto-dissimulation après 5s d'inactivité.

## 2. Layout de l'écran de lecture

### Zones tactiles

```
┌─────────────────────────────┐
│  ← Page précédente (20%)    │  Zone de tap gauche
│                             │
│  ↕ Toggle chrome (60%)      │  Zone de tap centrale
│                             │
│  → Page suivante (20%)      │  Zone de tap droite
└─────────────────────────────┘
```

- **Zone centrale** (60% largeur) : tap → toggle chrome (TopAppBar + BottomBar)
- **Zones latérales** (20% gauche, 20% droite) : tap → navigation avant/arrière
- **Appui long** n'importe où dans le texte → sélection (cf. §7)
- **Swipe vertical** : scroll dans le chapitre (mode vertical)
- **Swipe horizontal** : changement de chapitre (mode paginé)
- **Pinch** : zoom / ajustement de la taille de police

### Marges de lecture

```
┌──────────────────────────────┐
│ ← 16dp marge gauche          │  Contenu texte
│                              │  Largeur utile = écran - 32dp
│         Texte...             │
│                              │
│                  marge → 16dp│
└──────────────────────────────┘
```

- **Marges latérales** : 16dp par défaut, ajustables (8dp min, 32dp max)
- **Padding vertical** : 24dp en haut/bas du chapitre (pas entre phrases)
- **Espacement inter-paragraphes** : 1.5× la hauteur de ligne
- **Indentation première ligne** : 0dp (style moderne) ou 1.5em (style roman)
  — au choix de l'utilisateur, jamais les deux simultanément
- **Largeur de ligne optimale** : 55-75 caractères par ligne
  (atteignable en combinant taille de police + marges + orientation)

## 3. Système typographique

### Échelle de tailles (lecture)

| Niveau | Taille (sp) | Usage |
|---|---|---|
| Très petit | 12 | Notes de bas de page |
| Petit | 14 | Légendes, metadata |
| **Défaut** | **17** | **Corps de texte principal** |
| Moyen | 19 | Lecture confort |
| Grand | 22 | Lecture accessible |
| Très grand | 26 | Malvoyance |

- **Plage utilisateur** : 12sp – 28sp (curseur à 9 crans minimum)
- **Pas d'incrément** : 2sp entre chaque cran
- **Ratio modulaire** : 1.25 (Major Third) entre chaque niveau

### Hauteur de ligne

| Taille police | Hauteur de ligne | Ratio |
|---|---|---|
| 14sp | 21sp | 1.5 |
| 17sp (défaut) | 26sp | 1.53 |
| 22sp | 33sp | 1.5 |

- **Règle** : line-height = 1.5× à 1.6× la taille de police
- **Jamais** en dessous de 1.4 (illisible) ni au-dessus de 1.8 (trop aéré)

### Graisse et style

- **Corps** : Regular (400) — jamais Light (300), trop peu contrasté
- **Titres de chapitre** : SemiBold (600), taille +4sp par rapport au corps
- **Italique** : réservé à l'emphase sémantique (citation, pensée, mot étranger)
- **Small caps** : réservé aux titres courants ou aux sigles (optionnel)

### Polices

- **Serif (défaut)** : Literata, Bookerly, Georgia — pour lecture longue
- **Sans-serif** : Roboto Flex, Atkinson Hyperlegible — pour accessibilité
- **Dyslexie** : OpenDyslexic, Luciole — option accessible
- **Monospace** : réservé au code ou aux métadonnées techniques
- **Police système** : jamais pour le texte de lecture (manque de personnalité)

## 4. Système de couleurs

### Thèmes de lecture (NON Dynamic Color)

Ces 3 palettes sont **fixes** — le Dynamic Color Material You ne s'applique
qu'au chrome (TopAppBar, Drawer, Dialogs).

| Propriété | Thème LIGHT | Thème SEPIA | Thème DARK |
|---|---|---|---|
| Fond | `#FFFCF5` | `#FBF0D9` | `#1A1A1A` |
| Texte | `#1A1A1A` | `#5B4636` | `#D4D4D4` |
| Texte secondaire | `#5C5C5C` | `#8B7355` | `#999999` |
| Surbrillance sélection | `#3399FF` (33%) | `#C4956A` (33%) | `#3399FF` (33%) |
| Lien hypertexte | `#1565C0` | `#7B4B2A` | `#64B5F6` |
| Fond carte/dialogue | `#FFFFFF` | `#F5E6D3` | `#2D2D2D` |

**Règles** :
- Le texte n'est **jamais** en `#000000` (noir pur) ni `#FFFFFF` (blanc pur)
  → contraste excessif, fatigue oculaire
- Le fond de lecture n'est **jamais** en blanc pur → léger ton chaud
  (`#FFFCF5` plutôt que `#FFFFFF`)
- Contraste minimum texte/fond : ratio WCAG AA (4.5:1 pour texte normal,
  3:1 pour texte large)

### Thème chrome (Material You / Dynamic Color)

- **Généré par** `MaterialTheme.colorScheme` à partir du Dynamic Color système
- **Appliqué à** : TopAppBar, BottomBar, NavigationDrawer, Dialogs, FAB,
  Snackbar, BottomSheet
- **NE PAS appliquer à** : fond de lecture, texte du livre, surlignage TTS

### Dégradé de progression

Pour les jauges et barres de progression :
```kotlin
val ProgressGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF4CAF50), Color(0xFF81C784))
)
```

## 5. Espacements et grille

### Grille de base

- **Unité de base** : 4dp
- **Pas utilisés** : 4, 8, 12, 16, 20, 24, 32, 48dp

| Usage | Valeur |
|---|---|
| Padding interne composant (icône, chip) | 8dp |
| Padding écran (latéral) | 16dp |
| Espacement entre éléments d'une liste | 12dp |
| Séparateur de section | 24dp |
| Marge inter-composants distincts | 16dp |
| Padding TopAppBar → contenu | 16dp |
| BottomBar → fin d'écran | 8dp + system insets |

### Élévation (ombres)

- **TopAppBar** : 4dp (surélève le chrome au-dessus du contenu)
- **FAB** : 8dp (élément d'action principal)
- **Dialogs** : 24dp (superposition modale)
- **BottomSheet** : 16dp
- **Fond de lecture** : 0dp (pas d'ombre, plein écran)

## 6. Icônes et imagerie

- **Aucun emoji** dans le code de production (K12)
- Icônes via `AppIcons` (Material Symbols), pas d'Unicode dans les strings
- Taille standard : 24dp, taille dense : 20dp, taille large : 48dp
- Toujours `contentDescription` pour l'accessibilité
- Script de vérification : `bash scripts/check-no-emoji.sh`

## 7. Sélection de texte

### Pattern Spike (validé V2206, 2026-07-30)

- **Un `SelectionContainer` par phrase** — pas de `SelectionContainer` global
  dans une `LazyColumn` (comportement non défini documenté par Compose)
- `LocalTextToolbar` intercepté via `CompositionLocalProvider` pour
  capturer `showMenu()` → `onSelected`
- `TextToolbar` et `LocalTextToolbar` sont dans `androidx.compose.ui.platform`
  (module `ui-android`), PAS dans `foundation.text.selection` (K13)
- Voir `SelectableSentenceSpike.kt` pour le pattern de référence
- Limitation connue : pas de sélection inter-phrases (sera levée en Partie 3
  via `AnnotationSelectionHandler`)

### Poignées de sélection

- Les poignées (gouttes) sont fournies par le système Android — ne pas
  les redessiner
- Le menu contextuel (copier, rechercher, annoter) utilise le
  `TextToolbar` intercepté, pas le menu système par défaut

## 8. Motion Design

### Durées et courbes

| Type | Durée | Easing | Usage |
|---|---|---|---|
| Micro | 150ms | `FastOutSlowInEasing` | Ripple, focus, toggle |
| Standard | 300ms | `FastOutSlowInEasing` | Transition écran, expand/collapse |
| Entrée | 300ms | `LinearOutSlowInEasing` | Apparition d'élément |
| Sortie | 250ms | `FastOutLinearInEasing` | Disparition d'élément |
| Continue | — | `LinearEasing` | Surlignage TTS, défilement auto |

### Règles spécifiques

- **Jamais** d'animation sur le fond de lecture pendant la narration TTS
- **Surlignage TTS** : seule animation active, synchronisée sur
  `AudioTrack.getPlaybackHeadPosition()`
- **Changement de thème** : fondu croisé 300ms entre LIGHT/DARK/SEPIA
- **Navigation** : défauts Compose Navigation 2.8+ (`fadeIn` +
  `slideInHorizontally`), pas de surcharge
- **Chrome toggle** : fade in/out 300ms, pas de slide (le slide distrait
  de la lecture)
- **Apparition du lecteur audio** (BottomSheet) : slide up 300ms
- **Page curl** : non implémenté (choix délibéré — le curl est
  skeuomorphique et coûteux en performances)

## 9. Architecture MVI

- **UN** état immuable par écran (`XxxUiState`)
- Intents explicites en entrée
- Effets ponctuels par canal dédié (`SharedFlow`/`Channel`)
- `StateFlow` + `collectAsStateWithLifecycle()` dans Compose
- Pas de MVVM libre à états multiples

## 10. Badges et indicateurs

- **Progression bibliothèque** : arrondir ≥ 1% dès qu'une lecture a commencé
- **Jamais** arrondir à 0% si une progression existe
- Exemple : 0.4% → afficher "1%", pas "0%"
- **Badge "Nouveau"** : pastille `MaterialTheme.colorScheme.primary`,
  taille 8dp, disparaît après première ouverture
- **Indicateur de favori** : icône `Star` pleine si favori, `StarBorder` sinon,
  couleur ambre (`#FFC107`) — pas de rouge (le rouge = suppression/alerte)

---

## Méthodologie de validation device

### Quand l'appliquer

Toute modification UI non triviale touchant :
- La hiérarchie Compose (nouveau composant, refonte)
- Les interactions tactiles (sélection, gestes, scroll)
- Le comportement dans une `LazyColumn` (recyclage)
- Les thèmes/couleurs sur device réel (le rendu émulateur diffère)

### Procédure en 7 étapes

#### 1. Spike

Créer un composable de spike **minimal**, isolé de la navigation principale.

#### 2. Écran de test

Créer `SpikeTestScreen.kt` avec **50+ instances** dans une `LazyColumn`,
bandeau de diagnostic visible, TopAppBar affichant l'état courant.

#### 3. Route temporaire

`SpikeTestRoute` → `startDestination` → `composable<SpikeTestRoute> { ... }`

#### 4. Build et déploiement

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.inktone.app/.MainActivity
```

#### 5. Protocole de test manuel

**Important** : Les événements tactiles injectés (`adb input`, `monkey`) ne
déclenchent **pas** la sélection de texte Compose (restriction de sécurité
Android — `FLAG_INJECTED` filtré). Le test DOIT être manuel.

Définir un protocole précis en 5 étapes :
1. Action initiale
2. Manipulation de l'état (scroll, toggle)
3. Retour à l'état initial
4. Vérification visuelle ET fonctionnelle
5. Contre-test (action différente → vérifier absence de corruption)

#### 6. Collecte de preuves

- Capture d'écran : `adb exec-out screencap -p > /tmp/preuve.png`
- Horodatage dans la TopAppBar de test
- Noter les observations **exactes**, pas des résumés

#### 7. Cleanup

Supprimer `SpikeTestScreen.kt`, restaurer NavHost et Routes, nettoyer
les paramètres temporaires du spike, compiler, committer.

---

## Checklist avant merge de tout composant UI

- [ ] Aucun emoji dans le code → `bash scripts/check-no-emoji.sh`
- [ ] Dynamic Color uniquement sur le chrome, pas sur le fond de lecture
- [ ] `contentDescription` sur toutes les icônes
- [ ] Contrastes WCAG AA vérifiés pour les 3 thèmes
- [ ] Testé sur device physique avec le protocole §Méthodologie
- [ ] `SelectionContainer` → pattern spike, pas global dans LazyColumn
- [ ] `LocalTextToolbar` depuis `androidx.compose.ui.platform`
- [ ] `MANAGE_EXTERNAL_STORAGE` absent → `bash scripts/check-no-manage-external-storage.sh`
- [ ] `fallbackToDestructiveMigration` absent
- [ ] Commit en français, impératif

---

## Anti-patrons UI

| Erreur | Correction |
|---|---|
| Dynamic Color sur fond de lecture | Thèmes LIGHT/DARK/SEPIA fixes |
| Emoji dans les strings | `AppIcons` (Material Symbols) |
| `SelectionContainer` global dans `LazyColumn` | Un par phrase (pattern spike) |
| `LocalTextToolbar` depuis `foundation.text.selection` | Depuis `androidx.compose.ui.platform` (K13) |
| `#000000` ou `#FFFFFF` pur | `#1A1A1A` / `#FFFCF5` |
| Line-height < 1.4 | Minimum 1.5× |
| Texte justifié sans césure | Justifié + coupure de mots automatique, ou aligné à gauche |
| Chrome visible en permanence | Auto-dissimulation 5s, toggle au tap |
| Rotation écran non gérée | Paysage = 2 colonnes si largeur > 600dp |
| `fallbackToDestructiveMigration` | Migration explicite + test (K4) |
| `MANAGE_EXTERNAL_STORAGE` | SAF exclusivement (K5) |

## Références

- [Blueprint Architecture](../../../docs/blueprint/BLUEPRINT_ARCHITECTURE_INKTONE_v1.2.2.md)
- [ADR-012 — Pattern MVI](../../../docs/adr/ADR-012-mvi-presentation-pattern.md)
- [ADR-021 — Capacités TTS](../../../docs/adr/ADR-021-capability-aware-tts-abstraction.md)
- [Spike de référence](../../../feature/reader/src/main/kotlin/com/inktone/feature/reader/SelectableSentenceSpike.kt)
- [Instructions Copilot](../../../.github/copilot-instructions.md)
