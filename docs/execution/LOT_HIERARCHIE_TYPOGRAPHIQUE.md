# Lot Hiérarchie typographique — rôles du texte de l'interface

**Base :** `audit-p2` à `d7b7a159` (poussée, non intégrée ; `main` n'a ni le
lot P1 lourd ni P2). Branche : `audit-typographie`.
**Origine :** constat d'Issa à la vérification de P2 (2026-11-18), absent de
`UI_AUDIT_REPORT.md` : l'audit a traité les **valeurs** de l'échelle
(DS-7 à DS-11, P1-9) et le titre de section commun (P1-18), jamais la
**correspondance élément → rôle** ni la hiérarchie qui en résulte.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Un commit par tâche, dès sa vérification ciblée verte ; validation sur
appareil groupée en fin de lot (protocole des lots P1 et P2). Claude Code
ne déclare pas le lot terminé.

## Le verrou : l'échelle de corps est écrasée

`core/designsystem/.../Type.kt`, typographie du chrome :

| Rôle | InkTone | M3 |
|---|---|---|
| `bodyLarge` | 16 / 24 | 16 / 24 |
| `bodyMedium` | **16 / 24** | 14 / 20 |
| `bodySmall` | **14 / 20** | 12 / 16 |
| `titleSmall` | 14 / 20 Medium | 14 / 20 |
| `titleLarge` | **20 / 28** | 22 / 28 |
| `headlineSmall` | **20 / 28** | 24 / 32 |

Cause : l'échelle de corps du chrome a été recopiée de l'échelle de lecture
(serif 18/16/14, Phase 9bis, `89fc7ef4`) ; P1-9 a ramené `bodyLarge` à 16 sp
sans toucher aux deux rangs suivants. Effets constatés dans le code :

- un titre de ligne en `titleSmall` (14 sp) est **plus petit** que le texte
  courant `bodyMedium` (16 sp), corps des dialogues M3 compris ;
- ligne de la Bibliothèque : titre `titleSmall` et auteur `bodySmall` font
  tous deux 14 sp, seul le poids les distingue ;
- Réglages (`ListItem`) : libellé `bodyLarge` et valeur `bodyMedium` à 16 sp ;
- `titleLarge` = `headlineSmall`.

**Aucune règle de rôle ne vaudra tant que l'échelle reste plate** : la tâche
T.1 passe avant toute autre.

**Hors périmètre, à ne pas toucher :** le texte des livres (échelle de
lecture, `ReadingTypography`, tailles explicites de la pagination). Son
`TextStyle` porte ses propres tailles : la pagination n'est pas concernée.
Le vérifier quand même (T.5, point 4).

---

## Tâche T.1 — Rétablir l'échelle M3 du chrome

`Type.kt` : `bodyMedium` 14/20 (0.25 sp), `bodySmall` 12/16 (0.4 sp),
`titleLarge` 22/28, `headlineSmall` 24/32. Rien d'autre.

Ajouter à `TypographyBrandTest` (ou un `TypographyScaleTest`) l'invariant
qui aurait évité le défaut : tailles **strictement décroissantes** à
l'intérieur de chaque famille (display, headline, title, body, label), et
`headlineSmall` > `titleLarge`.

Effet : tout le texte secondaire de l'app perd 2 sp. Changement le plus
visible du lot ; vérifier écran par écran (T.5).

`Rétablit l'échelle M3 du corps de texte et des grands titres`

---

## Tâche T.2 — Écrire la règle des rôles

Document court `docs/design/TYPOGRAPHIE_ROLES.md` (ou section de
`UX_FLOW_DESIGN.md`, au choix d'Issa), une ligne par élément :

| Élément | Rôle | Couleur |
|---|---|---|
| Titre d'écran (barre d'application) | `titleLarge` (slot `TopAppBar`) | défaut |
| Titre de feuille, panneau, dialogue plein | `titleLarge` | défaut |
| Titre de section | `InkToneSectionHeader` | `primary` |
| Titre de carte | `titleMedium` | défaut |
| Titre d'une ligne de liste (livre, réglage) | `titleSmall` ou `bodyLarge` (`ListItem`) | défaut |
| Texte courant, corps de dialogue | `bodyMedium` | défaut |
| Texte secondaire, métadonnée (auteur, date, position) | `bodySmall` | `onSurfaceVariant` |
| Libellé au-dessus d'un contrôle | `labelLarge` | défaut |
| Badge, pastille, axe, chiffre compact | `labelSmall` / `labelMedium` | selon le badge |
| Chiffre clé (KPI) | `headlineMedium` | défaut |

Le texte secondaire s'atténue par `onSurfaceVariant`, jamais par un alpha —
sauf sur les couleurs du thème de lecture (chrome du Lecteur), qui n'ont pas
de rôle `onSurfaceVariant` propre.

`Documente la correspondance entre éléments d'interface et rôles typographiques`

---

## Tâche T.3 — Appliquer la règle : titres

Relevé (`grep` du rôle par appel `Text`, 2026-11-18) :

- **Titres de feuille en `titleMedium` → `titleLarge`** : `ReaderTtsPanel.kt:74`
  (Narration), `ReaderSettingsPanel.kt:133` (Réglages du texte),
  `BookmarkPanel.kt:177` (Signets et notes), `SleepTimerPanel.kt:100`
  (Minuteur de sommeil), `TableOfContentsSheet.kt:111` (Table des matières).
  Déjà en `titleLarge` : conflit de synchro, édition de catalogue, fiche livre.
- **En-têtes de feuille en `labelLarge`** : `LibraryScreen.kt:592`
  (« Bibliothèque », feuille Actions), `BookActionsSheet.kt:66` (titre du livre,
  feuille d'actions) — à aligner sur la règle (titre de feuille).
- **Titres de section hors `InkToneSectionHeader`** :
  `ThemeGalleryScreen.kt:95,108,123` (Ambiances de lecture, Confort et
  accessibilité, Mes thèmes personnalisés), `SettingsScreen.kt` en-tête
  dessiné à la main de `PronunciationCard` (« Règles de prononciation (n) »,
  `titleSmall` + `primary` recopiés) — garder son bouton « + » en
  `trailing`, quitte à donner un emplacement d'action à
  `InkToneSectionHeader`.
- **État vide hors composant partagé** : `LibraryDetailScreen.kt:146`
  (« Aucun livre dans cette sélection », `bodyLarge` centré) →
  `InkToneEmptyState`.

`Aligne titres de feuille et de section sur la règle des rôles`

---

## Tâche T.4 — Appliquer la règle : texte secondaire

`labelSmall` (11 sp) porte des phrases et des métadonnées. Passer en
`bodySmall` + `onSurfaceVariant` :

- position et date des signets et annotations : `BookmarkPanel.kt:256,308,356,374`,
  `LibraryItemsScreen.kt:380` ;
- « Connecté », « (Cet appareil) », horodatage du journal :
  `SyncConfigurationScreen.kt:283,372,632,756` ;
- sous-titres : `ThemeGalleryScreen.kt:300` (lignes Confort),
  `AboutScreen.kt:207` (piliers) ;
- phrase d'avertissement de lisibilité : `ThemeStudioScreen.kt:290`.

**Restent en `labelSmall`** (badges, axes, chiffres compacts, conformes) :
badge « Actif », pastille de progression, « regex », axes et légendes des
graphiques, minima/maxima des curseurs, ligne de statut du Lecteur
(`labelMedium` depuis P2-4).

Alphas hors thème de lecture → `onSurfaceVariant` : `ThemeStudioScreen.kt:263,291`
(sauf si ces textes sont dessinés **dans** l'aperçu du thème, auquel cas ils
suivent ses couleurs — le vérifier avant de toucher), `AboutScreen.kt:132`.

`Passe métadonnées et phrases secondaires en bodySmall`

---

## Tâche T.5 — Vérification

Tests :

1. L'invariant d'échelle de T.1 (unitaire, `core:designsystem`).
2. Tests instrumentés qui mesurent du texte ou s'appuient sur un libellé
   déplacé (`SettingsAccessibilityTest`, `StatusLineBarComposeTest`,
   `ReaderSettingsPanelTest`…) : les relancer, les corriger s'ils mesuraient
   l'ancienne échelle — jamais les affaiblir.
3. `check-no-font-weight.sh`, `check-no-spacing-literal.sh` verts.
4. Pagination : changer la taille de police du Lecteur et ouvrir un livre
   long ; le nombre de pages ne doit pas bouger entre avant et après le lot
   à réglages égaux (le chrome seul change).

Sur appareil (liste groupée à préparer en fin de lot) : Bibliothèque
(lignes, grille détaillée, carte Reprendre, feuilles), Lecteur (cinq
panneaux, barre du haut, mini-lecteur), Réglages, Galerie et Studio,
Statistiques, Synchro, Catalogues, Recherche, Onboarding, À propos, et une
passe en police système maximale.

---

## Écarts et décisions à confirmer par Issa avant T.2

- Emplacement de la règle : nouveau document ou section de `UX_FLOW_DESIGN.md`.
- Titre de la feuille d'actions d'un livre : `titleLarge` (règle) ou garder
  un en-tête plus discret, la feuille étant courte.
