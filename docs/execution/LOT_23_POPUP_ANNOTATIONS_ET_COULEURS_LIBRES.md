# Lot 23 — Popup d'annotation repensé et couleurs libres

**Base :** `main` après merge du `LOT_22_PERSISTANCE_ET_PARITE_ANNOTATIONS.md`.
Base de données en version **30** au moment de la rédaction
(`infrastructure/database/.../InkToneDatabase.kt:45`) — renuméroter selon
l'état réel de `main` au démarrage du Lot.

Source : dissection de Moon+ Reader Pro (`docs` de travail
`/home/majeur/Desktop/moonreader/RAPPORT_POPUP_SELECTION_MOONREADER_v2.md`,
hors dépôt, décompilation `aapt2` avec résolution d'ids — pas une inférence
sur noms de classes seuls) + vérification device par Issa du Lot 22, tâche
10 : **aucune action n'existe pour créer un surlignage souligné ou barré**
malgré `AnnotationKind` et son rendu déjà livrés.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

---

## Constat vérifié (base du Lot)

1. **`AnnotationKind` n'est jamais choisi par l'utilisateur.**
   `ReaderIntent.ConfirmAnnotation(color, content)` n'a pas de paramètre
   `kind` ; `ReaderViewModel.confirmAnnotation` construit toujours
   l'`Annotation` avec la valeur par défaut `HIGHLIGHT`. Le rendu
   (`annotationSpanStyle`) et la migration Room (`MIGRATION_28_29`) sont
   corrects et inutilisés au-delà de `HIGHLIGHT`. C'est un trou de la
   tâche 10 du Lot 22, pas une décision actée — à combler proprement, pas
   à la marge d'un autre correctif (directive Issa).
2. **Aucune interaction avec une annotation déjà posée dans le texte.**
   `onAnnotationClick` n'existe que pour la liste de `BookmarkPanel`
   (Lot 22, tâche 11). Taper un passage déjà surligné dans le lecteur ne
   fait rien de spécifique (même comportement que taper n'importe où :
   bascule du HUD). `DeleteAnnotationUseCase`/`UpdateAnnotationUseCase`
   et `EditNoteDialog` existent déjà (Lot 22) : un menu contextuel in-situ
   n'a besoin d'écrire ni cas d'usage ni dialogue neufs, seulement du
   test-hit et un point d'ancrage visuel — **quick win confirmé**
   (directive Issa, tâche 4 du cadrage).
3. **`AnnotationColor` est un enum fermé à 5 valeurs**
   (`domain/model/Annotation.kt`), rendu en `FilterChip` texte
   (`AnnotationColorPicker.kt`), jamais en pastille de couleur. Traverse
   `AnnotationEntity.color` (colonne `TEXT`, nom d'enum), `LibraryItemView`
   (vue SQL dérivée, aucun stockage propre), `BackupModels.AnnotationBackup`
   (`.name`/`.valueOf`, aucun repli défensif) et
   `UserPreferences.recentAnnotationColors` (Lot 22, tâche 12, liste
   d'enum).
4. **`SelectionActionPopup` est ancré près de la sélection**
   (`Popup` + `SelectionPopupPositionProvider`), 4 modes
   (`ACTIONS`/`COLOR_PICKER`/`MORE`/`NOTE_INPUT`), jamais de rangée de
   type d'annotation.
5. **Deux icônes manquantes.** `AppIcons.kt` a `Highlight` mais pas
   *Souligné*/*Barré* — nécessaires pour la rangée de type (K12 : icônes
   uniquement via `AppIcons`, jamais d'emoji).

---

## Décisions arrêtées

Tranchées avec Issa avant exécution. Chacune ferme une alternative réelle :
ne pas la rouvrir en cours de lot sans fait nouveau.

1. **Pas de bricolage.** Ce lot ne "comble pas le trou" a minima : il
   reprend la conception du popup dans son ensemble (disposition +
   couleur + interaction sur annotation existante), dans un lot dédié.
2. **Trois `AnnotationKind`, pas quatre.** Le style *Squiggly* (souligné
   ondulé) vu chez Moon+ est explicitement écarté — hors périmètre, comme
   l'était déjà "types d'annotation au-delà des trois retenus" au Lot 22.
3. **Couleur : 5 préréglages + personnalisation RGB libre, pas un
   remplacement total.** `AnnotationColorPicker` garde 5 pastilles rapides
   (mêmes teintes qu'aujourd'hui, en pastilles pleines plutôt qu'en
   `FilterChip` texte) **et** gagne une entrée « Personnaliser » ouvrant un
   éditeur RGB (sliders R/V/B + champ hex) — toute couleur choisie, préréglage
   ou personnalisée, est une valeur libre côté stockage (décision 6). Les
   deux réponses d'Issa ("on reste sur des valeurs fixes" puis "on peut
   envisager la personnalisation" une fois le lot dédié confirmé) se
   concilient ainsi : les préréglages restent le chemin rapide par défaut,
   la personnalisation est une extension, pas un remplacement.
4. **Menu contextuel sur annotation existante : dans le périmètre**, tap
   sur un span annoté dans le texte → petit popup positionné dessus
   (Modifier la note / Supprimer), même patron que `SelectionActionPopup`.
5. **Panneau ancré en bas d'écran**, façon Moon+ : `SelectionActionPopup`
   n'est plus un `Popup` positionné près de la sélection mais un panneau
   qui s'ouvre depuis le bas de l'écran (largeur pleine, `Surface` +
   `AnimatedVisibility` slide-in, même famille de composant que
   `BookmarkPanel`/`ReaderSettingsPanel` plutôt qu'un nouveau pattern).
   Actions persistantes en bas (Copier/Surligner/Note/Plus), rangée de
   type + couleur qui s'ouvre au-dessus quand « Surligner » est activé —
   repris de la structure Moon+ observée, pas de sa mise en œuvre
   (`ActionMode` natif écarté, cf. rapport §7 "à ne pas copier").
6. **Stockage couleur : colonne `TEXT` conservée, contenu devient un hex
   `#AARRGGBB`, pas de nouvelle colonne ni de réécriture de table.**
   Cohérent avec la seule discipline de migration du projet à ce jour
   (additive, jamais de `DROP`/rebuild de table — aucun précédent dans
   `Migrations.kt`). La migration réécrit les 5 valeurs d'enum existantes
   vers leurs hex actuels (`toComposeColor()`) : **aucune annotation
   existante ne change de couleur visuellement**. Domaine : `AnnotationColor`
   devient une value class portant un `Int` ARGB (jamais
   `androidx.compose.ui.graphics.Color` — le domaine ne dépend jamais de
   Compose/Android), avec 5 constantes nommées pour les préréglages.
   `toComposeColor()` reste dans `feature/reader`.
7. **Sauvegarde : repli défensif obligatoire.** `AnnotationBackup.color`
   doit lire aussi bien un ancien nom d'enum (`"YELLOW"`) qu'un nouveau hex
   (`"#FFFFF59D"`) — une sauvegarde faite avant ce Lot doit rester
   restaurable. Ne pas reproduire le défaut du constat 11 du Lot 22
   (`FontFamily.valueOf` non défensif).

---

## Tâches

### Palier A — Couleur libre (domaine + persistance)

1. **`AnnotationColor` devient une value class `Int` ARGB** (décision 6),
   avec 5 constantes correspondant aux teintes actuelles. Migration Room
   (nouvelle version) : réécrit `annotations.color` des 5 noms d'enum vers
   leurs hex, colonne `TEXT` inchangée. Test `MigrationTestHelper`
   (couleur visuelle identique avant/après) dans le même commit.
   Commit : `Remplace AnnotationColor par une couleur ARGB libre`.
2. **Mappers et sauvegarde mis à jour** : `AnnotationMapper`,
   `LibraryItemMapper` (lecture seule, `LibraryItemView` dérivée — aucune
   migration propre), `BackupModels.AnnotationBackup` avec repli
   défensif (décision 7, testé : un backup avec un ancien nom d'enum se
   restaure sans erreur).
   Commit : `Lit les anciennes couleurs d'annotation en sauvegarde`.
3. **`UserPreferences.recentAnnotationColors` migré vers le nouveau type**
   (liste de couleurs ARGB au lieu d'enum), migration additive dédiée
   (nouvelle colonne ou reformat de la colonne existante — trancher au
   palier selon ce que permet `MIGRATION_29_30` déjà livrée), test.
   Commit : `Migre les couleurs recentes vers le format ARGB libre`.

### Palier B — Choix du type d'annotation (le trou comblé)

4. **`ReaderIntent.ConfirmAnnotation` gagne `kind: AnnotationKind`**,
   propagé jusqu'à la construction de l'`Annotation` dans
   `ReaderViewModel.confirmAnnotation` (actuellement toujours
   `HIGHLIGHT` implicite).
   Commit : `Ajoute le choix du type d'annotation a la confirmation`.
5. **Deux icônes manquantes** (constat 5) : `AppIcons.Underline`,
   `AppIcons.Strikethrough` (Material Symbols, même pipeline que les
   icônes existantes — voir `ASSETS_ICONES_2C.md`).
   Commit : `Ajoute les icones souligne et barre`.
6. **Rangée de type dans le nouveau panneau** (3 icônes : Surlignage/
   Souligné/Barré, cf. Palier C pour la disposition d'ensemble) — pas de
   Squiggly (décision 2).
   Commit : `Affiche le choix du type d'annotation dans le panneau`.

### Palier C — Panneau ancré en bas (réorganisation)

7. **`SelectionActionPopup` devient un panneau ancré en bas d'écran**
   (décision 5) : actions persistantes (Copier/Surligner/Note/Plus) en
   bas, rangée type + palette couleur qui s'ouvre au-dessus au tap sur
   « Surligner » — remplace l'actuel `Popup` positionné près de la
   sélection. Vérifier que le mode `NOTE_INPUT` (clavier, focus) et
   `MORE` (Partager) survivent au changement de conteneur — même discipline
   de focus que l'actuelle (`PopupProperties(focusable = ...)`,
   bug device déjà documenté en commentaire).
   Commit : `Ancre le popup de selection en bas de l'ecran`.
8. **`AnnotationColorPicker` en pastilles pleines** (décision 3) au lieu
   de `FilterChip` texte, plus une entrée « Personnaliser » (voir Palier D).
   Commit : `Rend le selecteur de couleur en pastilles`.

### Palier D — Personnalisation de couleur (RGB libre)

9. **Éditeur de couleur personnalisée** : sliders R/V/B (0-255) + champ
   hex, accessible depuis « Personnaliser » du sélecteur. La couleur
   validée devient une entrée comme une autre pour les couleurs récentes
   (`withRecentColor`, déjà écrit Lot 22 — vérifier qu'il reste correct
   pour une couleur libre, pas seulement les 5 préréglages).
   Commit : `Ajoute l'editeur de couleur personnalisee`.
10. **Tests** : parsing/clamp RGB↔hex, couleur personnalisée persistée et
    retrouvée en tête des couleurs récentes après usage.
    Commit : `Teste l'editeur de couleur personnalisee`.

### Palier E — Menu contextuel sur annotation existante

11. **Détection du tap sur un span annoté** dans le texte lu (offset du
    tap → `Annotation` dont la plage le contient, dans le chapitre
    courant). N'ouvre le menu contextuel QUE si le tap tombe dans une
    plage annotée — un tap hors annotation garde le comportement actuel
    (bascule HUD).
    Commit : `Detecte le tap sur une annotation existante`.
12. **Popup contextuel** (Modifier la note / Supprimer), même patron de
    positionnement que `SelectionActionPopup` (ancré près du tap, pas le
    panneau du bas — c'est une action ponctuelle sur un élément déjà
    posé, pas un nouveau surlignage). Réutilise `UpdateAnnotationNote`/
    `DeleteAnnotation`/`EditNoteDialog` (Lot 22, tâche 11) — aucun nouveau
    cas d'usage.
    Commit : `Ouvre un menu contextuel sur une annotation existante`.
13. **Tests.**
    Commit : `Teste le menu contextuel d'annotation existante`.

---

## Ce qu'on ne fait pas dans ce Lot

- **Style *Squiggly*** (décision 2) — `AnnotationKind` reste à 3 valeurs.
- **`ActionMode` natif Android** — le popup Compose actuel (devenu
  panneau bas) reste la seule couche, jamais de greffe sur le système
  (rapport Moon+ §7 : "à ne pas copier").
- **Notes avec titre séparé ou images jointes** (`dlg_note.xml`/
  `note_images.xml` chez Moon+) — `Annotation.content` reste un champ
  texte unique.
- **Personnalisation de l'apparence d'une bulle de note** (couleur
  texte/fond séparée, `popup_note_color.xml` chez Moon+).
- **Surligner toutes les occurrences d'un texte dans le livre**
  (fonctionnalité de masse Moon+, `highlight_pop.xml`) — aucune demande,
  pas de cas d'usage InkTone identifié.
- **Réglage "template couleur au-dessus de la barre" optionnel** — chez
  nous ce comportement devient la disposition par défaut (décision 5),
  pas un réglage utilisateur activable/désactivable.

---

## Écart déclaré (contrat point 5)

**Palier E, tâches 11-12 (menu contextuel sur annotation existante) :
mode SCROLL uniquement, mode PAGED non couvert.** `BookBlockItem.kt`
(SCROLL) et `PagedChapterContent.kt` (PAGED) sont deux pipelines de
rendu/geste indépendants (aucun code partagé pour la détection de tap,
seulement pour le calcul de bornes fenêtre — `rangeBoundsInWindow`).
Étendre au mode PAGED est mécaniquement similaire (même patron :
`onValueChange` → `PageBlock`) mais double la surface de risque sur du
code de geste déjà marqué par plusieurs correctifs device documentés
(`PagedChapterContent.kt`, `BookBlockItem.kt` — commentaires « Bug réel
trouvé sur appareil »). Non traité dans ce Lot faute de cycle de
vérification device pour ce second pipeline ; à couvrir dans un lot
séparé si le mode PAGED est confirmé comme usage réel pour cette
interaction.

---

## Points de vigilance (non négociables)

- **Trois canaux visuels séparés** (annotation / surlignage TTS
  `WordHighlightColor` / sélection `SelectionHighlightColor`) — ce Lot
  ne touche qu'au premier, ne jamais les mélanger (rappel Lot 22, tâche 10).
- **Domaine sans dépendance Android/Compose** : `AnnotationColor`
  (value class ARGB) ne référence jamais `androidx.compose.ui.graphics.Color`
  — la conversion reste dans `feature/reader`.
- **K4** : migration Room testée dans le même commit ; aucune annotation
  existante ne doit changer de couleur visuellement après migration
  (test explicite comparant l'ARGB migré à `toComposeColor()` actuel).
- **K12** : icônes nouvelles via `AppIcons` uniquement, jamais d'emoji.
- **Locator unique** : la détection de tap sur annotation existante
  (Palier E) réutilise les offsets/`Locator` déjà en place, jamais un
  nouveau système d'adressage.
- **Rétrocompatibilité de sauvegarde** : un fichier de sauvegarde produit
  AVANT ce Lot (couleurs en noms d'enum) doit rester restaurable après.

---

## Critères de sortie du Lot

- [ ] Créer un surlignage souligné et un surlignage barré depuis le
      panneau de sélection (le trou signalé par Issa est comblé).
- [ ] Le panneau de sélection s'ouvre ancré en bas de l'écran ; Note/Plus/
      Partager fonctionnent toujours (pas de régression de focus clavier
      en mode Note).
- [ ] Le sélecteur de couleur affiche des pastilles pleines, propose une
      personnalisation RGB, et la couleur personnalisée survit à une
      fermeture/réouverture du lecteur (persistée).
- [ ] Une annotation créée avant ce Lot garde exactement la même couleur
      visuelle après la migration (vérifié, pas supposé).
- [ ] Un ancien fichier de sauvegarde (couleurs en noms d'enum) se
      restaure sans erreur.
- [ ] Taper un passage déjà surligné dans le texte ouvre un menu
      Modifier/Supprimer ; taper ailleurs garde le comportement actuel
      (bascule HUD). **Mode SCROLL seulement — écart déclaré ci-dessus
      pour le mode PAGED.**
- [ ] `./gradlew build` vert.
