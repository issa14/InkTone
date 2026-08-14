# Conception UX — Flux applicatif et écrans validés

**But de ce document :** capitaliser chaque décision de flux/écran au fur et à mesure qu'elle est validée à l'oral, pour ne rien perdre si la conversation qui les a produites est un jour compactée ou reprise dans une nouvelle session. Même principe que le Blueprint et les ADR pour l'architecture — appliqué ici à la conception UX.

**Méthode retenue (accord explicite) :** l'application pensée comme une entité, son flux élaboré d'abord sans regarder le code existant, puis un palier logique à la fois, jamais deux écrans indépendants traités simultanément. Chaque écran est décrit à l'oral, maquetté, corrigé, validé — puis consigné ici avant de passer au suivant.

**État des lieux du code existant :** volontairement laissé de côté pendant cette phase de conception. La réconciliation avec ce qui est déjà construit (Phase 9bis notamment) se fera après, écran par écran, une fois le flux cible posé — pas avant, pour ne pas biaiser la conception par ce qui existe déjà.

---

## Niveau 1 — Flux général de l'application

```
Premier lancement → Onboarding → Bibliothèque (centre de navigation)
                                        ↕ (boucle principale)
                                     Lecture
                        ↗                        ↖
                   Import                    Drawer (menu latéral)
                                              ├── Récents
                                              ├── Marque-pages et notes (global)
                                              ├── Catalogues OPDS (Lot 13, ADR-023)
                                              ├── Synchronisation
                                              ├── Statistiques de lecture
                                              └── Pied : Réglages / Thèmes / À propos
```

**Note :** ce schéma simplifié date du tout début de la session, quand seuls Onboarding/Bibliothèque/Import/Lecture/Réglages étaient identifiés comme hubs. Le drawer et ses six destinations ont émergé et ont été entièrement conçus au fil de la session (voir Point d'étape en fin de document pour l'état final complet).

**Décisions tranchées :**
- **La Bibliothèque reste le point d'entrée unique** après le premier lancement (confirmé — pas de reprise directe du dernier livre).
- **Pas de mini-lecteur persistant pour cette v1.** L'audio continue en arrière-plan (`AudioPlaybackService`/Phase 5) quand l'utilisateur quitte le Lecteur, mais sans contrôle visible depuis un autre écran — décision assumée, pas un report silencieux.

---

## Écran : Onboarding

### Décisions de fond

| Point | Décision |
|---|---|
| Contenu fonctionnel (consentement crash reporting ADR-014, téléchargement de voix ADR-018) | **Différé au point de besoin réel**, pas dans l'onboarding — consentement demandé juste avant d'activer la fonctionnalité concernée, téléchargement de voix au premier lancement effectif du TTS. L'onboarding reste une pure présentation, sans rien de fonctionnel. |
| Élément visuel par carte | Illustration complète (pas une simple icône, pas du texte seul) |
| Navigation entre cartes | Balayage horizontal **et** bouton « Suivant » visible (choix motivé par l'accessibilité — ne pas dépendre uniquement de la découverte du geste) |
| Sortie de l'onboarding | Bouton d'action explicite sur la dernière carte (« Commencer »), pas juste continuer le balayage |
| Bouton « Passer » | Présent sur les cartes 1 et 2 (coin supérieur droit, discret) — **retiré sur la carte 3**, car redondant avec « Commencer » à ce stade (proposé par Claude, **confirmé explicitement par Issa** — voir Points ouverts ci-dessous) |
| Indicateurs de position | 3 points en bas, le point actif rempli |

### Carte 1 — Bienvenue

- **Illustration :** un livre ouvert stylisé (deux pages en éventail avec lignes de texte suggérées) d'où partent deux arcs représentant des ondes sonores — symbolise « lecture + audio » en une seule image plutôt que deux symboles séparés.
- **Titre :** « Bienvenue sur InkTone »
- **Corps de texte (validé, lot 10) :** « Lisez avec les yeux, continuez avec les oreilles. Une expérience de lecture unifiée qui s'adapte à votre rythme. »
- **Bouton :** « Passer » (coin supérieur droit)

### Carte 2 — Conçu pour votre confort

- **Titre (validé, lot 10) :** « Conçu pour votre confort »
- **Disposition :** deux blocs côte à côte (pas une illustration unique comme la carte 1 — jugé plus lisible pour deux capacités distinctes) :
  - Bloc gauche — icône livre simple : **« Lecture sur mesure »** / « Thèmes, typographie et mise en page entièrement personnalisables pour un confort visuel absolu. »
  - Bloc droit — icône égaliseur audio simple : **« Narration naturelle »** / « Des voix ultra-réalistes et fluides. Ajustez la vitesse et laissez-vous porter par l'histoire. »
- **Bouton :** « Passer » (coin supérieur droit)

### Carte 3 — Clôture

- **Illustration :** variante différenciée de celle de la carte 1 (lot 10, tâche 10.1, point 5) — le livre est retiré, seules les ondes sonores concentriques convergent vers un point d'accent central, pour éviter que les cartes 1 et 3 se ressemblent trop dans un pager de trois cartes.
- **Titre (validé, lot 10) :** « Votre prochaine histoire vous attend »
- **Sous-texte (validé, lot 10) :** « Importez vos livres et commencez l'expérience InkTone. »
- **Bouton :** « Commencer » (mène à la Bibliothèque)

### Points ouverts — résolus au lot 10

- **Textes validés** (repris à la lettre, `LOT_10_ONBOARDING.md`) — le titre carte 3 diffère du brouillon initial de conception (« Profitez d'une expérience... ») : le texte validé prime.
- **Source des illustrations : vectoriel composé (Compose Canvas), pas génération IA** — décision inversée par rapport au brouillon de conception. Six corrections avant intégration (couleurs paramétrées depuis `MaterialTheme`, tout en `dp.toPx()`, `quadraticTo`, taille laissée à l'appelant, cartes 1/3 différenciées, `clearAndSetSemantics` pour l'accessibilité) — voir `OnboardingIllustrations.kt`.
- **Bouton « Suivant » retiré** : le plan d'exécution (`LOT_10_ONBOARDING.md`, tâche 10.2) simplifie à balayage horizontal + « Passer » (cartes 1/2) + « Commencer » (carte 3), sans bouton « Suivant » séparé — écart assumé par rapport à la décision de fond initiale (accessibilité, ne pas dépendre uniquement du geste) : le balayage reste secondé par un bouton explicite sur chaque carte (Passer ou Commencer), donc jamais uniquement gestuel.
- **Retrait du bouton « Passer » sur la carte 3 : confirmé, implémenté.**
- Palette bordeaux `#7A1F3D` : consommée via `MaterialTheme.colorScheme.primary` (paramètre par défaut), pas un littéral dans les illustrations.

### État d'implémentation (lot 10)

Onboarding câblé au premier lancement (`OnboardingRoute`, `startDestination` de `InkToneNavHost` arbitré sur `UserPreferences.hasSeenOnboarding`, migration 19→20). `CrashConsent`/`VoiceDownload` retirés (pure présentation) : le consentement crash vit dans la carte Confidentialité des Réglages (formulation honnête ajoutée ce lot, gap trouvé à l'audit — voir `LOT_ONBOARDING_PERIMETRE.md`), le téléchargement de voix dans la carte Lecture des Réglages (`SettingsIntent.StartVoiceDownload`, nouveau point de besoin réel, aucun autre point d'accès n'existait avant ce lot).

---

## Écran : Bibliothèque — état vide

### Structure validée

**Barre supérieure (de gauche à droite) :**
1. Icône menu hamburger — ouvre le drawer (b). Ajout de Claude à l'origine, non explicitement demandé par Issa — **confirmé depuis** par Issa.
2. Titre « Bibliothèque » — adaptatif selon l'élément actif du menu latéral (comportement fonctionnel, pas encore visuellement différent dans le mockup de l'état vide).
3. Icône recherche — repliée par défaut, se déploie au tap (l'animation de déploiement elle-même n'a jamais été détaillée visuellement — seul point resté ouvert de cette barre).
4. Icône filtre — ouvre un popup de filtrage (contenu détaillé et **maquetté** plus loin, § Popup de filtrage).
5. Icône menu 3-points — ouvre un bottom sheet (contenu détaillé et **maquetté** plus loin, § Bottom sheet du menu 3-points).

**Corps, état vide :**
- Illustration : étagère avec emplacements de livres en pointillés (suggère l'absence de contenu plutôt qu'un vide complet). **Produite au lot 10** (`EmptyLibraryShelfIllustration`, vectoriel Compose Canvas, même procédé que l'onboarding — couleur paramétrée, proportionnelle à la taille reçue) — ferme la dette signalée au lot 2a.6, `AppIcons.Reading` n'est plus un repli.
- Titre : « Votre bibliothèque est vide » — **textes validés** (lot 2a).
- Corps : « Importez votre premier livre pour commencer à lire et écouter avec InkTone. » — **texte validé** (lot 2a).
- Bouton central, icône + libellé : « Importer votre premier livre ».
- **Ajout à la cible (lot 2a, non prévu à l'origine) :** variante affichée quand un import est déjà en cours (« Import en cours… », pas de bouton) — état réel et utile du code, conservé plutôt que perdu ; à arbitrer si une conception dédiée est souhaitée.

### Éléments décrits par Issa, contenu actée à l'oral — **depuis entièrement maquettés** (§ Popup de filtrage, § Bottom sheet du menu 3-points)

**Popup de filtrage (icône a3), deux colonnes :**
- Colonne 1 — Trier par : Date d'import (récemment importés d'abord) / Titre / Auteur / Récents — **décision actée (lot 2a) : « Récents » et « Récemment lus » fusionnés en une seule entrée**, le domaine n'ayant qu'un seul champ `lastOpened` (pas de distinction possible entre les deux). 4 entrées, pas 5.
- Colonne 2 — Filtrer par : Non lu / En cours de lecture / Lu-Terminé
- Sous les deux colonnes, ligne 1 — Mise en page : deux icônes (mode liste / mode mosaïque)
- Ligne 2 — Type de fichier : cases à cocher, **Tous / EPUB / TXT** (PDF mentionné à l'origine à titre indicatif seulement, pas retenu — cohérent avec ADR-017, qui différe le PDF à une v1.x ultérieure)

**Bottom sheet du menu 3-points (icône a4) :**
- Importer des livres
- Couverture par défaut
- Reconstruire les couvertures
- Ouvrir un livre au hasard
- Synchroniser avec le cloud — **action ponctuelle, fonctionne uniquement si un service de sync est déjà configuré** — **tranché plus loin** (§ Bottom sheet du menu 3-points) : si non configuré, le tap déclenche directement l'écran de configuration, pas un bouton désactivé.

**Menu déroulant à côté du libellé « Bibliothèque » (pas dans le popup de filtrage a3, précision importante d'Issa) :**
- Favoris / Séries / Tags — accessibles depuis ce menu déroulant, pas depuis ailleurs.

**Drawer (menu latéral, b) :**
- b1 — Liste des récents
- b2 — Bibliothèque (surligné par défaut, élément actif)
- b3 — Marque-pages et Notes (ajouté après coup — oublié dans la description initiale, confirmé par Issa) — placé ici, juste après Bibliothèque, par cohérence avec les autres destinations de contenu réel. Positionnement proposé par Claude, **confirmé depuis** par Issa.
- b4 — Catalogues OPDS — réintégré en v1.x, borné au Volet 1 (`ADR-023`), planifié par `docs/execution/LOT_13_CATALOGUES_OPDS.md`
- b5 — Synchronisation — **écran de réglages**, distinct de l'action ponctuelle du bottom sheet : ouvre la configuration du service de sync **et** regroupe l'import/export local (rejoint `BackupManager`, déjà construit en Phase 8 — pas à reconstruire, juste à brancher visuellement ici)
- b6 — Statistiques de lecture
- b7 — Pied de drawer : Options/Paramètres, À propos, Thèmes

**Résolu :** les signets globaux (et les notes) ont bien leur place dans le drawer (b3) — ce n'était qu'un oubli, pas une omission volontaire.

**Fonction précise de chaque item du drawer** — délibérément non détaillée par Issa à ce stade (« on reviendra sur ce que fera chaque élément, c'est pour ça que je n'ai pas détaillé, c'est juste l'apparence d'abord »). À traiter dans un palier dédié, après que l'apparence de tous les écrans principaux soit posée.

---

## Écran : Bibliothèque — état peuplé

### Disposition mosaïque (par défaut), 3 colonnes

Couverture seule par livre — **aucun titre ni auteur visible** en mosaïque, contrairement à beaucoup d'apps de référence. Trois éléments superposés sur chaque couverture :
- **Haut-droite :** cœur (toggle favori) — contour si non favori, plein/coloré si favori.
- **Bas-gauche :** trois points empilés verticalement, sur un fond sombre semi-transparent — ouvre un popup d'actions propre au livre ciblé, **3 actions** (« Télécharger la couverture » retirée de la cible, décision actée lot 2b) :
  - Épingler
  - Détails du livre
  - Retirer de la bibliothèque (**nécessite une confirmation** — action destructive, avertissement précisant que l'action est **irréversible** et supprime également les **marque-pages et notes associés** à ce livre)
- **Bas-droite :** badge circulaire de progression, fond sombre semi-transparent, pourcentage arrondi en texte clair.

### Disposition liste

Rangée : couverture en miniature à gauche, puis titre (plus grand, plus marqué) et auteur en dessous (plus petit, plus discret) — hiérarchie de taille claire entre les deux. **Cœur et 3-points côte à côte** (pas empilés), alignés à l'extrême droite de la rangée — décision affinée après un premier essai où les deux étaient superposés verticalement, jugé moins attrayant. Sous chaque rangée, une **barre de progression pleine largeur** avec le pourcentage affiché à droite — pas un badge circulaire comme en mosaïque, une barre linéaire.

### Historique de clarification, pour mémoire

Le positionnement exact des 3-points en mode liste a demandé un aller-retour : la première description orale (« devant le titre ») était ambiguë, clarifiée par une image de référence (bibliothèque tierce, fournie à titre d'inspiration de disposition uniquement — pas de contenu à reprendre), puis affinée une seconde fois (côte à côte plutôt qu'empilés).

### Décision finale sur la disposition grille

Après comparaison visuelle des deux variantes (couverture+titre vs couverture seule), **la grille couvertures seules est retenue telle que maquettée initialement** — pas de titre ajouté sous la couverture. Le besoin d'accessibilité soulevé (reconnaissance d'un livre par le texte plutôt que par la seule image, pertinent pour la basse vision) reste couvert **par l'existence du mode Liste** (titre + auteur toujours visibles), pas par une modification de la grille elle-même.

**Tranché :** le préréglage d'accessibilité (Tâche 8.4, un geste qui applique plusieurs réglages) bascule aussi automatiquement vers le mode Liste plutôt que de laisser la Grille par défaut — cohérent avec l'esprit du préréglage, confirmé.

### Point ouvert

Le comportement d'interaction du cœur en mode liste (vrai toggle animé) n'a été vu que comme deux états statiques (plein/vide) dans le mockup — l'animation/transition elle-même reste à définir, pas bloquant pour la suite.

---

## Écran : Menu déroulant du titre (Favoris/Séries/Tags)

### Décision de pattern

**Flyout à deux colonnes**, pas des filter chips horizontaux — choix motivé par la nature des données (Séries et Tags peuvent contenir des dizaines d'entrées chez un utilisateur avec une grande bibliothèque, et la structure est intrinsèquement à deux niveaux catégorie → élément), pas par une mode visuelle. Reprend et modernise le pattern déjà présent dans le legacy (`LibraryNavigationPopup`).

### Déclencheur

Juste à côté du libellé « Bibliothèque » dans la barre du haut, avec un chevron. **Le titre affiché est adaptatif** — reflète la sélection active (« Bibliothèque », « Favoris », « Séries », « Tags »), cohérent avec la description initiale de la barre du haut (a1).

### Structure du flyout

- **Colonne gauche — catégories :** Tous / Favoris / Séries / Tags (pas « Auteur » — déjà décidé que ce filtre reste simple, sans navigation dédiée — ni « Dossiers », concept legacy jamais demandé pour la réécriture)
- **Colonne droite — sous-éléments**, uniquement pour Séries et Tags, chacun avec un compteur (ex. « Trilogie du Vide (3) »)

### Comportement par catégorie

| Catégorie | Sous-niveau ? | Comportement au clic |
|---|---|---|
| Tous | Non | Identique à la Bibliothèque de base — c'est le défaut |
| Favoris | Non | Filtre appliqué directement sur la grille/liste principale |
| Séries | Oui | Sélectionner une série précise **navigue** vers un écran de détail dédié |
| Tags | Oui | **Même comportement que Séries** — navigue vers un écran de détail dédié, réutilisant le même patron d'écran |

### Écran de détail (partagé Séries/Tags)

Un seul écran réutilisable pour les deux cas — seul le contenu de l'étiquette et de la liste change.

- **Barre du haut :** flèche retour (remplace le hamburger — on n'est plus au niveau racine), étiquette de catégorie en petites majuscules espacées (« SÉRIES » ou « TAGS »), nom de l'élément sélectionné en dessous en plus grand — tronqué avec points de suspension si trop long. Icônes conservées : **recherche et filtre uniquement**, pas de 3-points ni de hamburger à ce niveau.
- **Corps :** les livres correspondants (tomes d'une série, ou livres portant ce tag), affichés en **vue Liste** — même disposition que la Bibliothèque peuplée (couverture, titre/auteur, cœur + 3-points côte à côte, barre de progression pleine largeur).

### Points de correction pendant la conception

- L'icône « filtre » utilisée dans un des mockups intermédiaires (☰) était en réalité un symbole de menu générique, pas un vrai symbole de filtre — à corriger dans l'implémentation, pas reproduire l'erreur.
- La hiérarchie visuelle (étiquette petite en haut, nom en plus gros en dessous) a été vérifiée explicitement avant validation — pas l'inverse.

---

## Écran : Popup de filtrage (icône a3 de la barre du haut)

**Forme :** dialogue centré (pas un bottom sheet) — choix délibéré pour se distinguer visuellement du menu 3-points, qui lui en est un.

- **Trier par** (sélection unique, ronds pleins) : Date d'import (par défaut, plus récent d'abord) / Titre / Auteur / Récents — 4 entrées (« Récents »/« Récemment lus » fusionnés, lot 2a, voir § Bibliothèque état vide)
- **Filtrer par** (sélection unique) : Tous (par défaut) / Non lu / En cours / Terminé
- **Mise en page** : deux icônes (liste / mosaïque), état actif visuellement distinct
- **Type de fichier** (cases à cocher, **sélection multiple** — contrairement aux deux colonnes du dessus) : Tous / EPUB / TXT

---

## Écran : Bottom sheet du menu 3-points (icône a4 de la barre du haut)

Cinq actions, dans l'ordre :
1. Importer des livres
2. Couverture par défaut
3. Reconstruire les couvertures
4. Ouvrir un livre au hasard
5. Synchroniser avec le cloud — **action ponctuelle**, fonctionne uniquement si un service de sync est déjà configuré. **Si non configuré : le tap déclenche directement l'écran de configuration de la sync** (tranché) — pas un bouton désactivé.

---

## Écran : Drawer (menu latéral)

- **En-tête :** nom de l'application (« InkTone »), fond dégradé léger.
- **Navigation :**
  - Récents
  - Bibliothèque (surligné par défaut, élément actif)
  - Marque-pages et Notes
  - Catalogues OPDS — réintégré en v1.x (`ADR-023`, Lot 13), pas d'étiquette de placeholder une fois l'écran conçu — badge/style transitoire à reconsidérer seulement si l'écran reste masqué au moment de son propre lot (règle du Lot 1 : jamais de destination affichée sans écran derrière)
  - Synchronisation (écran de réglages, distinct de l'action ponctuelle du bottom sheet — regroupe aussi l'import/export local, `BackupManager` déjà construit en Phase 8)
  - Statistiques de lecture
- **Pied de page**, rangée compacte à 3 boutons (pas empilés verticalement) : Paramètres / À propos / Thèmes.

**Fonction précise de chaque item — statut de conception (mis à jour en fin de session) :**
- Récents (b1) → ✅ écran conçu (§ Écran Récents).
- Bibliothèque (b2) → ✅ élément actif par défaut, écran déjà entièrement conçu.
- Marque-pages et Notes (b3) → ✅ écran conçu (§ Marque-pages et notes — vue globale). Distinct du panneau par livre (§ Marque-pages — panneau latéral).
- Catalogues OPDS (b4) → non conçu dans cette session ; réintégré en v1.x et planifié séparément (`ADR-023`, `docs/execution/LOT_13_CATALOGUES_OPDS.md`).
- Synchronisation (b5) → ✅ écran conçu (§ Écran Synchronisation — Configuration + Opérationnel).
- Statistiques de lecture (b6) → ✅ écran conçu (§ Écran Statistiques de lecture, 4 sections).
- Pied de page (b7) : **Paramètres** → ✅ pointe vers l'écran **Réglages** entièrement conçu (6 cartes). **Thèmes** → ✅ pointe vers la **Galerie de thèmes** entièrement conçue (Studio de création inclus). **À propos** → ✅ écran conçu (§ Écran À propos).

**Tous les items du drawer sont désormais conçus, à l'exception d'OPDS — hors périmètre de cette session de conception UX, planifié à part (`ADR-023`, `docs/execution/LOT_13_CATALOGUES_OPDS.md`).**

**État d'implémentation (lot 1) :** le drawer portait 3 destinations (Bibliothèque, Marque-pages et Notes, Statistiques de lecture) et 2 boutons de pied (Paramètres, À propos). Récents, Catalogues OPDS, Synchronisation et Thèmes étaient **volontairement masqués** tant que leur écran n'existait pas — décision actée : aucune destination affichée sans écran derrière. Séries / Auteurs / Tags restent transitoirement dans le drawer jusqu'au lot 2, qui les déplace vers le flyout du titre.

**État d'implémentation (lot 8) :** Récents n'est plus masqué — item réactivé en première position (avant Bibliothèque), icône `AppIcons.Recents` (horloge d'historique, pas le sablier `AppIcons.Loading` de l'ancien item défectueux corrigé au lot 1). Écran dédié `RecentsScreen` (`feature/library`), topbar flèche de retour + titre seul, réutilise `PublicationListRow` en vue Liste forcée. Contenu : livres à progression ≥1%, triés par `lastOpened` décroissant, limités aux 30 plus récents. **Point tranché (Tâche 8.2) :** les livres terminés à 100% restent affichés — lecture « récemment consultés », pas « en cours de lecture » seul, pour ne pas faire disparaître un livre au moment précis où il vient d'être fini. Catalogues OPDS, Synchronisation et Thèmes restaient masqués à l'issue de ce lot (hors périmètre).

**État d'implémentation (lot 9) :** Thèmes n'est plus masqué — pied de drawer passé à 3 boutons (Paramètres / Thèmes / À propos, icône `AppIcons.Appearance`). `ReadingTheme` ouvert d'un enum fermé (LIGHT/DARK/SEPIA/SYSTEM) vers un modèle à thèmes personnalisés (6 thèmes intégrés + thèmes créés en base, migration Room 18→19). Galerie (`ThemeGalleryScreen`, `feature/settings`) et Studio (`ThemeStudioScreen`) entièrement fonctionnels — cartes-aperçu vivant, badge ACTIF, appui long, 4 sélecteurs de couleur, badge WCAG, suppression sécurisée. **Point tranché (Tâche 9.2) :** la bascule cyclique du lecteur (icône Thème du panneau, lot 3b) reste bornée à 3 ambiances de référence (Papier Clair → Obsidienne → Sépia Vintage → Papier Clair) plutôt que de cycler sur l'ensemble ouvert des thèmes personnalisés — un cycle sur un catalogue de taille arbitraire serait impraticable au geste rapide ; la Galerie reste le chemin complet pour choisir un thème personnalisé. **Écart déclaré (Tâche 9.4) :** l'appui long prévisualise via un recouvrement plein écran DANS la Galerie (mockup agrandi), pas une poussée en direct dans un Reader déjà ouvert ailleurs — `ReaderViewModel` ne réobserve pas en continu `UserPreferences.theme` (seulement à l'ouverture/`SetOverrides`), et ajouter cette réobservation aurait débordé le périmètre de ce lot pour un gain marginal. Catalogues OPDS et Synchronisation restent masqués (hors périmètre).

**État d'implémentation (lot 11) :** Synchronisation n'est plus masquée — réactivée en **b5** de la liste principale du drawer (entre Marque-pages et Statistiques), pas au pied de drawer comme une première implémentation l'avait placée à tort (retour Issa, vérification device, corrigé le jour même). **Toutes les destinations conçues sont désormais affichées** — seule Catalogues OPDS (b4) reste masquée, différée volontairement à v1.x (elle n'a jamais été conçue dans cette session, ce n'est pas un oubli).

**Google Drive en V1, WebDAV différé (décision actée, tâche 11.2)** : `drive.appdata` est une portée sensible mais non restreinte — 100 comptes de test disponibles sans attente en mode Test, aucun audit de sécurité tiers requis pour démarrer. L'exclusivité mutuelle actée dans la cible (un seul fournisseur cloud actif) impose de toute façon une frontière propre entre fournisseurs ; WebDAV sera une seconde implémentation derrière la même interface `SyncProvider`, hors périmètre du lot 11. La carte WebDAV de l'écran Configuration reste grisée en permanence (pas seulement quand Drive est actif, contrairement à la cible) tant qu'aucune implémentation n'existe.

**Écran de conflit — absent de la conception initiale (tâche 11.10)** : `UX_FLOW_DESIGN.md` ne décrivait aucun écran de conflit avant ce lot ; c'est un manque de conception identifié à l'implémentation, pas un oubli d'exécution. `SyncConflictBottomSheet` a été conçu directement en code, sans maquette préalable — exception assumée à la méthode habituelle de ce document. Il n'arbitre **que** la position de lecture, jamais les annotations ni les marque-pages.

**Matrice de résolution des conflits (tâche 11.10)** :

| Donnée | Résolution |
|---|---|
| Position de lecture | Arbitrage utilisateur (`SyncConflictBottomSheet`) — valeur unique par livre |
| Marque-pages, annotations | Fusion silencieuse (union par identifiant), jamais de question posée |
| Réglages, thèmes personnalisés | Dernier écrit gagne |
| Suppressions | **Écart déclaré** — aucun marqueur de suppression horodaté n'existe encore : un élément supprimé localement peut réapparaître si un autre appareil le televerse encore. Retrofitter les chemins de suppression existants pour écrire une tombe plutôt qu'un DELETE dur est un chantier à part, volontairement hors de ce lot plutôt que fait en hâte. |

La synchro en arrière-plan ne tranche jamais un conflit de position elle-même : elle le détecte et le met en file (persistée, Room), présentée à la prochaine ouverture de l'app (écran Bibliothèque).

---

## Bibliothèque : écran complet

Tous les éléments décrits initialement sont maintenant conçus et consignés : barre du haut (titre adaptatif, recherche repliable, filtre, 3-points), état vide avec CTA, état peuplé (mosaïque et liste), menu déroulant du titre avec écran de détail Séries/Tags, popup de filtrage, bottom sheet 3-points, drawer. Trois points restent ouverts, à ne pas perdre :
1. Synchronisation par bottom sheet si aucun service n'est configuré : **tranché** — déclenche l'écran de configuration (voir §3-points).
2. Animation de morphing du menu déroulant du titre : **tranché — abandonnée**, une transition standard suffit (moins de risque, plus simple à implémenter).
3. Bascule automatique vers le mode Liste par le préréglage d'accessibilité (Tâche 8.4) : **tranché — oui**, cohérent avec l'esprit du préréglage.

---

## Écran : Import — progression et retours

**Déclencheur :** le sélecteur de fichiers du système (SAF) — rien à concevoir de notre côté, ouvert à la fois depuis le CTA de la bibliothèque vide et depuis « Importer des livres » du bottom sheet 3-points.

**Ce qui reste notre UI, en revanche :**

- **Bannière de progression**, non bloquante, au-dessus de la grille pendant l'import (« Import en cours · 5/12 » + barre fine) — jamais un overlay plein écran qui masquerait la bibliothèque (leçon déjà retenue du legacy, Blueprint/Phase 6).
- **Résumé de fin de lot** : succès complet (« 12 livres importés ») ou résultat mixte avec détail et action « Détails » (« 9 importés · 2 doublons ignorés · 1 fichier corrompu »).
- **Retour pour un import à l'unité**, un message par cas, sur **deux registres visuels distincts** :
  - **Informationnel** (icône ⓘ, ton neutre) : doublon détecté — ce n'est pas un échec, juste « ce livre y est déjà ».
  - **Alerte** (icône ⚠/🔒, ton plus marqué) : fichier corrompu, protégé par DRM, format non pris en charge.

Correspond directement aux cas déjà gérés par le domaine depuis la Phase 6 (`ImportResult.Success/Duplicate/Corrupted/DrmProtected/UnsupportedFormat`) — l'habillage visuel closer ce qui manquait, pas la logique.

### Comportement consigné — Lot 5

- **Lots > 50 fichiers :** `WorkManagerImportScheduler` découpe l'import en `WorkRequest` chaînées de 50 URI max. Tous les lots partagent le même `sessionId` UUID → les résultats sont agrégés sur la chaîne complète dans la table Room `import_results`, jamais partiels.
- **Persistance des résultats :** table Room `import_results` (créée par `MIGRATION_13_14`). Les résultats survivent à la mort du processus et à un redémarrage. Ils sont purgés quand l'utilisateur ferme le résumé (bouton « OK » ou `DismissImportResults`).
- **Résolution du nom de fichier :** via `ContentResolver` + `OpenableColumns.DISPLAY_NAME` au moment de l'import, avec fallback `uri.lastPathSegment ?: "Fichier inconnu"`. Le nom est stocké avec le résultat — l'URI SAF peut ne plus être résoluble après coup.
- **Pas de « Réessayer » sur DRM / format non supporté :** ces erreurs ne sont pas résolubles par une nouvelle tentative. Seul `Duplicate` propose une action (« Ouvrir » vers la publication existante).
- **Catégories vides non affichées :** un lot 100% réussi affiche « 12 importés », pas « 12 importés · 0 doublon · 0 corrompu ».
- **ImportSessionStore :** singleton domaine partagé entre `ImportViewModel` (écrit le `sessionId`) et `LibraryViewModel` (lit pour charger les résultats en fin d'import). Introduit pour le Lot 5 — deux ViewModels distincts ne peuvent pas partager d'état directement.

---

## Flux de niveau 1 : Import entièrement couvert

Bibliothèque et Import sont maintenant complets. **Note historique, dépassée depuis :** au moment de ce checkpoint intermédiaire, il restait Lecture et un « Réglages » qui regroupait provisoirement Réglages/Statistiques/Signets globaux — cette fusion provisoire a depuis été éclatée en écrans séparés (voir Point d'étape final : Réglages, Statistiques de lecture, et Marque-pages et notes — vue globale sont trois écrans distincts, tous entièrement conçus).

---

## Écran : Lecture — vue silencieuse (visuelle, avant le TTS)

**Ordre de conception délibéré, rappelé par Issa en ouvrant cet écran :** la lecture visuelle d'abord, le TTS ensuite — pas les deux construits avec la même priorité.

### Décision majeure révisée : le mode pagé redevient prioritaire

**Renversement de la décision prise pendant la séance de questions à choix** (qui avait différé le mode pagé en v1.x) — motif donné par Issa : « déjà en production ».

**⚠️ Point critique à ne pas perdre, trouvé pendant l'audit du travail fait seul (avant la pause) :** l'implémentation déjà construite du mode pagé (`PagedChapterContent.kt`) a trois problèmes réels, jamais résolus :
1. Pagination par **estimation du nombre de caractères**, pas par mesure réelle du texte rendu (le legacy mesurait hors thread principal — cette version ne le fait pas) → pages probablement incohérentes en remplissage.
2. `sentences.indexOf(sentence)` appelé pour **chaque phrase affichée à chaque recomposition** — recherche linéaire coûteuse, pire sur les longs chapitres.
3. **Aucun test dédié**, alors que chaque brique un peu significative du projet en a eu au moins un depuis la Phase 1.

« Redevenir prioritaire » pour la conception UX ne règle pas ces trois points — ils restent à corriger avant l'implémentation finale, pas juste à accepter parce que quelque chose existe déjà.

**Mode pagé (lot 3a).** Les trois défauts signalés sont corrigés : pagination par mesure réelle du texte, plus par estimation au caractère ; recherche linéaire par phrase supprimée ; composant couvert par des tests. Un quatrième défaut trouvé à l'implémentation est corrigé au passage : le mode pagé forçait `ParagraphStyle.NORMAL`, rendant les titres de l'EPUB comme du texte courant — le respect des titres réels du fichier, acté au § Immersion, est rétabli. **Pages virtuelles disponibles dans les deux modes** ; leur affichage dans la ligne de statut arrive au lot 3b.

### Ligne de statut persistante (bas d'écran, toujours visible — y compris HUD masqué)

Trois éléments sur une seule ligne, discrets mais lisibles :
- **Gauche :** heure locale (ex. `14:32`)
- **Centre :** chapitre + repère de position — format `Chapitre X (page/total)`. **Le compteur de page est une estimation informative** (« pages virtuelles », recalculée selon la taille de police notamment), la lecture elle-même reste en défilement continu à cet endroit précis — **distinct** de la vraie pagination du mode `PAGED` évoquée ci-dessus, qui elle navigue réellement par page. Les deux coexistent : ce repère de position fonctionne quel que soit le mode de lecture actif.
- **Droite :** progression du livre entier, en pourcentage avec une décimale, **séparateur décimal : virgule** (`34,7%`, convention française) — confirmé explicitement.

### Immersion totale — pas de bannière de chapitre injectée par l'app

**Aucun élément de chrome ajouté par InkTone** au-dessus du texte pour annoncer le chapitre (la ligne de statut suffit). **Distinction importante, confirmée** : si le livre lui-même contient un vrai titre de chapitre écrit par l'auteur dans le fichier EPUB, ce texte continue de s'afficher naturellement dans le flux de lecture avec son style de titre — cohérent avec l'extension `ParagraphStyle.HEADING` décidée en Partie 1 (Fondations). Seul l'ajout artificiel d'une bannière par l'application est exclu, pas le contenu réel du livre.

---

## Écran : Lecture — HUD (barre du haut + panneau unifié)

**Déclenchement :** visible à l'ouverture du livre, se masque automatiquement après 4-5 secondes ; réapparaît sur un tap au milieu de l'écran de lecture.

### Barre du haut
Flèche de retour, puis titre du livre (plus grand) et nom de l'auteur en dessous (plus petit) — même hiérarchie typographique que les autres titres à deux niveaux déjà conçus dans ce document (écran de détail Séries/Tags notamment).

### Panneau unifié, 3 rangées

- **Rangée 1 :** barre fine de progression du livre entier (répète l'information de la ligne de statut, mais visible uniquement avec le HUD).
- **Rangée 2** (5 icônes) : Sommaire · Marque-pages (affiche marque-pages et notes) · **Play** — pour lancer le TTS, bouton central, visuellement mis en avant (plus grand, rempli, couleur d'accent, distinct des autres icônes de la rangée) · Thème du lecteur · Taille et espacement du texte (« TT », glyphe provisoire — une vraie icône reste à trouver pour l'implémentation).
- **Rangée 3** (4 icônes) : Minuteur de sommeil · **Haut-parleur** — options TTS (voix, volume, vitesse) · Mode de défilement (continu/paginé — redevient un vrai choix fonctionnel suite à la décision révisée ci-dessus) · Luminosité.

**Chaque icône ouvre son propre écran/panneau** — à concevoir un par un dans un prochain palier, pas tous en même temps.

**État d'implémentation (lot 3b).** Rangée 3 du panneau : **5 icônes** et non 4 — la Recherche dans le livre y est conservée, faute d'autre point d'entrée vers l'écran de recherche (décision actée). Luminosité absente jusqu'au lot 3c, son action n'existant pas encore. Navigation par chapitre retirée du panneau, en attente de la barre de contrôle TTS (lot 3d) ; entre-temps elle passe par le Sommaire. Micro-indicateur ETA retiré : absent de la cible.

**État d'implémentation (lot 3c).** Sommaire et Marque-pages passent en surfaces superposées ; le lecteur n'est plus démonté (`return@Column` retiré des deux). Hiérarchie du sommaire : **peuplée et affichée** — `TableOfContentsEntry.children` est bien renseigné par le parseur réel (prouvé contre un vrai EPUB à hiérarchie NCX imbriquée, `TableOfContentsChildrenTest`, Tâche 4.11) et l'indentation (`TableOfContentsSheet`) s'exerce dessus, vérifié par test de composant sur une fixture à structure Tome/Chapitre. Titre du bottom sheet aligné sur la cible : « Table des matières » (pas « Sommaire »). Marque-pages : panneau latéral à 3 onglets (Notes/Surlignages/Marque-pages) avec toggle « Marquer cette page » en tête, remplace la liste plein écran ; le bouton `+ Signet` transitoire du lot 3b est retiré. Popup de sélection : Copier/Surligner/Note positionné près de la sélection réelle (`PopupPositionProvider` alimenté par les `LayoutCoordinates`, suit un défilement/une rotation), remplace le sélecteur de couleur en bas d'écran ; **Sélection de texte par phrase** (appui long puis extension) et non libre : limitation d'API Compose documentée et assumée depuis la Tâche 7.0 — le popup Copier/Surligner/Note s'applique à la sélection par phrase. Prototype de sélection au mot (Tâche 3c.5) : les trois points du plan sont favorables, y compris le conflit de geste drag/pager en mode pagé — jugé bloquant sur une première mesure trop faible (3 répétitions), levé sur mesure robuste (30/30 répétitions, protocole renforcé, détail dans `docs/execution/NOTE_3C5_PROTOTYPE_SELECTION_MOT.md`). Ça rouvre la décidabilité du lot 3f (choix produit, pas encore tranché) mais ne change rien au périmètre livré ici : **la sélection par phrase reste le comportement d'InkTone v1**, le lot 3f n'est pas déclenché par ce lot. Position de lecture en mode défilement : `currentSentenceIndex` suit désormais un défilement manuel silencieux (dérivé de la phrase la plus haute visible), le compteur de page et le pourcentage de progression dérivent alors de la même source qu'en mode pagé — plus d'estimation par fraction de défilement. Luminosité toujours absente de la rangée 3 (lot 3d).

**État d'implémentation (lot 3d).** Rangée 3 du panneau : **5 icônes** — Luminosité ajoutée avec son action dans le même commit (`ReaderBrightnessBar`, `WindowManager.LayoutParams.screenBrightness` de la fenêtre du lecteur seulement, jamais le réglage système ; position « système » explicite distincte du minimum). Panneau TT (`ReaderSettingsPanel`) reconstruit : les 3 cartes de thème sont retirées (redondantes depuis la bascule cyclique du lot 3b), curseurs de taille et d'interligne continus (`steps = 0`), aperçu du texte réellement en cours de lecture. Interligne (`UserPreferences.lineHeightMultiplier`) : seul ajout de modèle du lot, alimente `PaginationStyleKey.lineHeightSp` via `baseTextStyle.lineHeight` (garde-fou posé en 3b.2, vérifié effectif — changer l'interligne redéclenche la pagination). **Règle corrigée au lot 9** (`ReadingTheme` porte désormais une police) : « le thème n'invalide jamais » devient « les couleurs n'invalident pas, la police oui » — changer les couleurs d'une ambiance ne redéclenche toujours rien, changer d'ambiance vers une police différente (ex. Papier Clair → Obsidienne) recalcule la pagination via `baseTextStyle.fontFamily`. Panneau Voix (`ReaderTtsPanel`) : curseur de vitesse branché sur `VoiceProfile.speed` (déjà consommé par les deux moteurs TTS depuis avant ce lot, seul le branchement UI manquait), sélecteur de voix ajouté (format `voix · moteur · langue`, ex. `ff_siwis · Kokoro · Français`), lien « Ajouter une règle de prononciation » vers `PronunciationRulesRoute`. **Bouton Stop retiré** : `ReaderViewModel.pausePlayback()` coupe déjà entièrement l'`AudioTrack` (`AudioSegmentPlayer` en `MODE_STATIC`, aucune reprise à mi-phrase) — Pause et Stop seraient rigoureusement identiques avec l'architecture actuelle. Un vrai Stop distinct nécessiterait de migrer la lecture du Reader vers `AudioPlaybackService`/Media3 (déjà utilisé par `feature/player`/`PlayerScreen`, qui recoupe d'ailleurs largement ce que ce panneau Voix devait construire) — reporté au lot 3e. Minuteur : le cycle silencieux 15→30→45→60 sur l'icône Veille est retiré, remplacé par `SleepTimerPanel` (puces 15/30/45 + roue personnalisée heures/minutes, même intent `SetSleepTimer` pour les deux). Rappel de repos oculaire ajouté (seconde fonction du même panneau, indépendante du minuteur de sommeil) : activé par défaut, intervalle 1h réglable par pas de 15 min, popup + compte à rebours 60s avec « Reprendre »/« Reporter (+10 min) ». Comportement audio retenu : le TTS actif est coupé AVANT l'affichage du popup (jamais silencieusement, le popup visible est l'avertissement), donc aucune annonce TalkBack superposée à la voix — même principe que le retrait de l'overlay de captions en B.7.

---

## Écran : Lecture — popup de sélection de texte

Apparaît uniquement après sélection d'un passage. Un seul bloc, positionné au-dessus ou en dessous du texte sélectionné selon la place disponible. **Trois options : Copier / Surligner / Note** — Signet retiré (un signet marque une position de lecture, pas une plage de texte ; la sélection n'est pas le bon geste pour en créer un). Un tap en dehors du bloc annule la sélection et masque le popup.

---

## Écran : Lecture — sous-écrans du panneau unifié

**Ordre de conception :** un par un, en commençant par Sommaire (le plus simple), comme convenu.

### Vue d'ensemble des 7 icônes (clarifiée avant tout mockup)

| Icône | Comportement |
|---|---|
| **Sommaire** | Ouvre un bottomsheet, libellé « Table des matières » — voir détail ci-dessous |
| **Marque-pages** | Ouvre un écran latéral (≈85% de la largeur), **s'ouvre depuis la gauche**. Affiche notes, surlignages ET marque-pages (pas seulement deux catégories comme supposé au départ) — filtre en bas de l'écran à trois catégories : Notes / Surlignages / Marque-pages. Sous ce filtre, un bouton pour marquer la page actuelle — **toggle** (retire le signet s'il existe déjà sur cette page, jamais de doublon) |
| **Play** | N'ouvre rien — déclenche directement le TTS |
| **Thème** | Bascule **cyclique** (pas un panneau) : Clair → Sombre → Sépia → Clair... |
| **TT** | Petit bottomsheet, ligne 1 taille du texte, ligne 2 interligne |
| **Minuteur** | **Deux fonctions distinctes sous une seule icône :** (1) minuteur de sommeil TTS — 3 durées fixes (15/30/45 min) + bouton durée personnalisée ; (2) rappel de repos oculaire, **indépendant du TTS**, activé par défaut, déclenché après **1h fixe par défaut** — utilisateur peut désactiver le rappel ou ajuster l'intervalle si activé. Popup : « Cela fait 1h que vous lisez, pensez à reposer vos yeux » + compte à rebours de 60s ignorable |
| **Haut-parleur** | Choix de la voix (parmi celles du moteur déjà sélectionné dans les Réglages généraux), volume, vitesse |
| **Luminosité** | Fine barre, ajuste la luminosité **uniquement pour l'écran de lecture**, pas le système — **signalé par Issa comme un défi technique potentiel**, noté tel quel, pas résolu à ce stade |

### Sommaire — Table des matières

Bottomsheet, chapitre en cours mis en évidence et automatiquement centré dans la liste à l'ouverture, encadré par **2 chapitres avant/après** (tranché, confirmé). **Titre du bottomsheet, y compris pour un livre à hiérarchie (Tome/Livre/Chapitre) : reste « Table des matières »** — confirmé, pas de variante selon la structure du livre.

**Occasion saisie pendant le mockup :** testé le cas d'un livre à hiérarchie réelle (Tome → Chapitre), directement lié au point resté « jamais vérifié » depuis les Fondations (`TableOfContentsEntry.children`, Tâche 1.3.0/audit Phase 9bis) — la disposition avec indentation fonctionne visuellement, reste à confirmer contre de vraies données une fois implémenté.

### Marque-pages — panneau latéral

Écran latéral, **s'ouvre depuis la gauche**, couvre ≈85% de la largeur (le reste de l'écran de lecture reste visible en scrim assombri). En-tête avec **flèche de retour** + titre « Marque-pages et Notes ». Trois onglets en bas de l'écran (Notes / Surlignages / Marque-pages, sélection unique), et sous cette ligne un bouton **toggle** « Marquer cette page » (retire le signet s'il existe déjà, jamais de doublon — confirmé précédemment).

**Contenu par onglet, à affiner :**
- **Notes** : extrait du passage annoté + texte de la note + position (chapitre) et date.
- **Marque-pages** : position (chapitre, page) + extrait du passage, sans note associée.
- **Surlignages** : extrait de texte surligné, en texte normal (pas de fond coloré) + une **barre de couleur verticale** en repère à gauche de l'extrait (même position/marge qu'une pastille, hauteur étirée sur tout le bloc extrait+métadonnées) + chapitre + date. **Tri : le plus récent en haut.** Format de position avec hiérarchie (Tome/Livre → Chapitre) : `Chapitre X (Tome Z)`. Format de date : `25 déc. 2025`.

Le bouton « Marquer cette page » reste visible quel que soit l'onglet actif — action indépendante du filtre affiché.

### Thème — bascule cyclique

Pas un panneau : un tap fait défiler **Clair → Sombre → Sépia → Clair...**. Confirmé : **aucun retour visuel supplémentaire** (pas de toast/texte de confirmation) — le changement d'apparence de l'écran suffit à lui-même.

Couleurs : Sépia reprend `#F4ECD8` (déjà choisi Tâche 4.7, pas réinventé). Sombre utilise un gris très foncé chaud (`#1c1b19`) plutôt qu'un noir pur — plus confortable pour une lecture longue, à valider si un noir pur est préféré.

### TT — taille du texte et interligne

Petit bottomsheet, **aperçu du texte en direct** au-dessus (le vrai texte du livre) pour voir l'effet sans fermer le panneau. Deux réglages en **sliders continus** (pas de paliers fixes) : ligne 1 taille du texte (repères A/A petit-grand), ligne 2 interligne (repères ≡/≡ serré-large).

### Minuteur — deux fonctions distinctes sous une seule icône

**Section 1 — Minuteur de lecture audio (sommeil TTS) :** 3 durées fixes en chips (15/30/45 min) + « Personnalisé » qui ouvre une **roue de temps** (deux colonnes heures/minutes, pas de 15 min).

**Section 2 — Repos oculaire, indépendant du TTS :** activé par défaut (toggle), désactivable. Si activé, intervalle réglable par **stepper (−/+) avançant par pas de 15 min** (supporte les formats type 1h30, pas seulement des heures rondes) — pas deux steppers séparés heures/minutes, un seul compteur.

**Popup de rappel** (déclenché à l'intervalle écoulé) : « Cela fait [durée] que vous lisez, pensez à reposer vos yeux » + compte à rebours de 60s + bouton « Ignorer ».

### Haut-parleur — voix, volume, vitesse

Bottomsheet : sélecteur de voix (parmi celles disponibles pour le moteur déjà choisi dans les Réglages généraux — affiche le nom réel de la voix, ex. « ff_siwis · Kokoro · Français », validée par écoute 8/10 en Phase 5, pas un nom inventé), puis deux sliders horizontaux (Volume, Vitesse en ×, ex. « 1,0× »).

**Recoupement notable avec l'existant :** ce panneau reprend presque exactement ce que `PlayerScreen` (Phase 5) fait déjà (vitesse, sélecteur de voix) — probablement moins de travail d'implémentation que prévu, réutilisation plutôt que construction neuve.

**Ajout signalé après coup par Issa, absent du legacy à cet endroit précis** (le legacy le mettait dans les réglages généraux, jugé trop loin d'accès) : un lien « Ajouter une règle de prononciation » en bas du panneau Haut-parleur. Ouvre un **formulaire rapide uniquement** (mot à corriger + remplacement, sans afficher la liste des règles existantes) — la gestion complète de la liste reste dans les Réglages généraux. Se raccorde directement à la fonctionnalité déjà entièrement construite en Tâche 8.3 (`PronunciationRuleRepository`, `PronunciationRuleApplier`) — nouveau point d'entrée, pas de logique à reconstruire.

### Luminosité — barre flottante, lecteur uniquement

Barre horizontale fine (cohérente avec tous les autres sliders de ce document), en overlay flottant juste au-dessus du panneau unifié — pas un panneau séparé. Icône soleil petite à gauche, grande à droite. **Ajuste uniquement l'affichage de l'écran de lecture, pas la luminosité système** — signalé par Issa comme un défi d'implémentation potentiel, non résolu à ce stade (conception uniquement).

---

## Écran : Lecture — couche TTS

**Déclenchement :** clic sur Play (panneau unifié) → lance le TTS **et remplace le panneau unifié** par un pop-up de contrôle de lecture TTS en bas de l'écran (pas d'affichage superposé aux deux).

**Actions du pop-up, ordre confirmé :** chapitre précédent (cp) — phrase précédente (pp) — resume/play (r) — phrase suivante (ps) — chapitre suivant (cs). Soit `cp - pp - r - ps - cs`.

**Accès aux autres fonctions du panneau pendant la lecture (lot 3e, tranché à la vérification device) :** un appui sur la zone de lecture rappelle le panneau unifié complet (Sommaire, TT, etc.) en overlay par-dessus la barre pilule, sans interrompre la lecture ni le surlignage. Un second appui referme tout, un troisième ramène la barre pilule — même rythme à trois temps que le cycle d'auto-masquage habituel du HUD, appliqué à un état de plus.

**Repli automatique :** la barre complète se replie après **4 secondes** (ajusté depuis 5s) en un **FAB** unique, coin inférieur droit. Toute interaction avec la barre pendant qu'elle est ouverte (tap sur cp/pp/r/ps/cs) **relance** le compte à 4s.

**FAB replié :**
- Affiche un indicateur discret (petite onde sonore animée, distincte de l'icône Lire/Pause de la barre déployée) qui reflète l'audio réellement en train de sortir — pas juste « TTS engagé » : elle s'arrête pendant un blanc de synthèse entre deux phrases.
- Tap simple → rouvre la barre complète pour 4s.
- Swipe vers le bas → **met en pause et referme la barre** (le panneau unifié revient). **Écart tranché au lot 3e** : la cible d'origine prévoyait un « stop immédiat » distinct d'une pause, uniquement sur le FAB replié. `ReaderViewModel.pausePlayback()` reste le seul comportement disponible (pas de reprise ni de libération distincte — retrait du bouton Stop au lot 3d) ; un vrai Stop nécessiterait de migrer la lecture du Reader vers `AudioPlaybackService`/Media3, hors périmètre présentation de ce lot. Le geste est donc une pause honnêtement nommée, disponible aussi bien sur la barre déployée que sur le FAB replié (pas réservé au FAB replié comme prévu initialement).

**Surlignage et captions :** le surlignage mot-à-mot du texte lu reste actif en permanence (barre ouverte ou FAB replié). Les captions sont **désactivées** — jugées trop encombrantes, notamment sur les phrases longues. Piste non tranchée pour une version 1+ : déplacer l'info de lecture dans la barre de notification système.

**Mockup validé :** confirmé sans correction — barre pilule sombre flottante en bas (ordre des 5 actions), et FAB avec surlignage mot en cours visible en fond et petit indicateur d'onde sonore. Écarts par rapport au mockup, tranchés à la vérification device : accès aux fonctions du panneau et sort du swipe décrits ci-dessus.

---

## Écran : Récents (item du drawer)

**Accès à la Bibliothèque avec vue restreinte** : réutilise l'écran Bibliothèque, topbar simplifiée — juste une flèche de retour vers la Bibliothèque + libellé « Récents » (pas de recherche, filtre, ou menu 3-points). Pas de menu hamburger non plus, la flèche de retour suffit à la navigation.

**Contenu :** uniquement les livres avec une **progression ≥ 1%**, triés par récence d'ouverture (le plus récent en premier), **limité aux 30 derniers**.

**Affichage : liste** (pas mosaïque) — jaquette + titre + auteur + barre de progression visible par livre, pour mettre en avant le suivi de lecture plutôt que le catalogue.

**État vide :** icône simple centrée + message « Vous n'avez aucune lecture récente, ouvrez un livre pour commencer. » — pas de bouton d'action (contrairement à l'état vide de la Bibliothèque qui propose d'importer).

---

## Écran : Réglages

Écran dense en 5 cartes : Présets rapides, Lecture, Appareil, Données, Prononciation. Traité carte par carte (méthode habituelle, jamais deux cartes en parallèle).

### Carte 1 — Présets rapides

Deux cartes-boutons **empilées verticalement** (pas côte à côte), chacune avec icône, titre, description courte du preset sous le titre, et un **vrai interrupteur (toggle) visuel** à droite — pas un chevron.

- **Mode sombre** (icône Lune) : applique thème système `OBSIDIAN` + thème de lecture `NIGHT` simultanément.
- **Accessibilité** (icône Accessibilité) : applique police OpenDyslexic + taille 24sp + thème lecteur `DAY` + réduction des animations.

**Comportement du toggle** : ON applique l'ensemble des réglages du preset, OFF les désactive/désapplique — l'interrupteur agit directement sur l'activation groupée des réglages associés à sa section, pas seulement comme confirmation visuelle post-clic.

### Carte 2 — Lecture

Titre de carte en majuscules discrètes en en-tête. Contenu, dans l'ordre de la spec :
- **Moteur de synthèse** et **Voix active** : lignes sélecteurs (libellé à gauche, valeur courante + chevron à droite), ouvrent chacune un dialogue de sélection au clic.
- **Vitesse d'élocution** (0,5x–2,0x), **Gain audio** (1,0x–4,0x), **Intonation/Pitch** (0,5x–1,5x) : trois curseurs, chacun avec libellé + valeur courante affichée au-dessus de la piste, et bornes min/max affichées sous la piste.
- **Écouter un extrait** : bouton plein largeur en bas de carte, icône + libellé.

Mockup validé sans correction.

**Confirmations actées pour la suite de l'écran (cartes non encore maquettées) :**
- **Vider le cache** (carte Données) : action déclenchée après **confirmation** (dialogue de sécurité, comme Réinitialiser les paramètres).
- **Importer une sauvegarde** (carte Données) : dialogue d'**avertissement avant d'écraser** la configuration courante, avant d'exécuter l'import.

### Carte 3 — Appareil

Une carte avec deux sous-sections séparées par un petit libellé interne en majuscules discrètes : **Apparence** et **Accessibilité**.

- **Apparence** : ligne sélecteur **Thème** (valeur courante + chevron, dialogue à 3 options Système/Clair/Sombre) ; ligne toggle **Couleurs dynamiques**, avec description courte en dessous précisant la condition Android 12+.
- **Accessibilité** : ligne toggle **Réduire les animations** ; ligne toggle **Police adaptée au système**, avec description courte précisant que ça ignore la taille interne de l'app.

Les toggles ici sont de vrais interrupteurs d'état (ON/OFF réel du réglage) — à distinguer du toggle « action groupée » de la carte Présets rapides.

Mockup validé sans correction.

### Carte 4 — Données

Cinq lignes dans l'ordre de la spec, pas de chevron sur les actions immédiates (Export/Import) puisqu'elles ne naviguent pas vers un sous-écran :

- **Dossier des modèles** : valeur du chemin (« Non défini » par défaut) + chevron, ouvre le sélecteur système.
- **Exporter les données** : ligne d'action avec icône + sous-texte rappelant le contenu exporté (progression, signets, réglages). Action directe, sans confirmation.
- **Importer une sauvegarde** : ligne d'action avec icône + sous-texte rappelant l'écrasement. Ouvre le sélecteur de fichiers puis le dialogue d'avertissement (acté précédemment) avant d'écraser.
- **Vider le cache** : taille occupée affichée (ex. « 124 Mo occupés ») + bouton dédié « Vider », avec confirmation (actée précédemment).
- **Réinitialiser les paramètres** : ligne en couleur d'alerte (rouge/orange) avec icône triangle d'avertissement, pour signaler visuellement le côté destructif de l'action. Confirmation avant exécution.

Mockup validé sans correction.

### Carte 5 — Prononciation

Deux volets : la carte liste, et le dialogue modal d'ajout/édition.

**Carte — en-tête et liste :**
- En-tête : « Dictionnaire phonétique (n) » + bouton `+` en icône carrée à droite.
- Chaque ligne : motif `→` remplacement (flèche icône, pas texte brut) avec badge `regex` si applicable, toggle d'activation/désactivation, icône poubelle pour suppression.
- Tap sur la ligne (hors toggle/poubelle) ouvre le dialogue d'édition pré-rempli avec les valeurs courantes.

**Dialogue modal (ajout ou édition) :**
- Titre : « Ajouter une règle » (ajout) / « Modifier la règle » (édition) — seul le titre change, le reste est identique.
- Champ **Texte d'origine**, champ **Prononciation de remplacement** (style label + champ bordé, cohérent avec le reste de l'app).
- Case à cocher **Expression régulière (Regex)**.
- Boutons Annuler / Enregistrer en bas à droite.

### Carte 6 — Performance & Bien-être

Ajoutée après coup à l'écran Réglages (initialement conçu avec 5 cartes). Regroupe objectifs de lecture personnels et suivi de la santé visuelle.

- **Objectif quotidien de lecture** : ligne affichant la cible actuelle (**30 min par défaut**), ouvre un **dialogue avec curseur continu** au clic (plage 10 min à 120 min) — cohérent avec le pattern déjà utilisé pour Vitesse/Gain/Pitch (carte Lecture). Alimente directement la jauge de progression et le calcul du streak dans l'écran Statistiques (§ Section 1 — KPIs & Objectifs).
- **Rappel de repos oculaire** : toggle, réutilise **exactement** le mécanisme déjà spécifié dans le sous-écran Minuteur du panneau unifié (§ Minuteur — deux fonctions distinctes) — activé par défaut, déclenché après 1h fixe par défaut.
- **Intervalle de pause** : visible/actif seulement si le rappel est activé, même **stepper (−/+) par pas de 15 min** que dans le sous-écran Minuteur — pas un nouveau mécanisme, le même réglage exposé à un second endroit (cohérent avec le pattern déjà vu pour la règle de prononciation, accessible à la fois en accès rapide et en gestion complète).

Mockup validé sans correction.

**Écran Réglages : 6 cartes au total désormais.**

### État réel — Lot 6, Palier A (livré, non déclaré terminé — voir `docs/execution/LOT_6_REGLAGES.md`)

Les 6 cartes existent dans `SettingsScreen.kt`, branchées sur `SettingsViewModel`
et testées (`SettingsViewModelTest`, `DatabaseMigrationTest`). Écarts assumés
par rapport à cette section, à vérifier sur appareil avant clôture :

- **Langue et Confidentialité** : pas de place dans les 6 cartes cibles —
  rattachées comme sous-sections de la carte **Appareil** plutôt que
  supprimées (le consentement Crashlytics doit rester accessible).
- **Désapplication des présets** : approche « retour aux valeurs par défaut »
  retenue plutôt que la mémoire de l'état antérieur — plus simple et
  prévisible, ne surprend pas un utilisateur qui aurait modifié des réglages
  entre l'activation et la désactivation d'un préset.
- **Nommage du thème sombre** : cette section nomme le préset `OBSIDIAN`/
  `NIGHT` (galerie de thèmes, hors périmètre du Lot 6). Le code actuel n'a que
  `ReadingTheme.LIGHT/DARK/SEPIA/SYSTEM` et `AppTheme.SYSTEM/LIGHT/DARK` — le
  préset Mode sombre applique `AppTheme.DARK` + `ReadingTheme.DARK`, pas les
  valeurs nommées ici. À réconcilier quand la galerie de thèmes sera traitée.
- **Préset Accessibilité → mode Liste** : la disposition de la bibliothèque
  (`LibraryLayoutMode`, `feature/library`) est désormais persistée dans
  `UserPreferences.libraryLayoutMode` (migration 15→16) pour que ce préset
  puisse la piloter, comme tranché lot 2b — ce n'était pas encore le cas
  avant ce lot (`LibraryViewModel` gardait la disposition en mémoire
  seulement).
- **Objectif quotidien — valeur par défaut** : cette section documente 30 min
  par défaut ; `UserPreferences.dailyGoalMinutes` vaut 20 min depuis le Lot 1
  (`MIGRATION_5_6`, couvert par un test qui fige cette valeur). Écart non
  résolu dans ce palier — changer la valeur par défaut affecterait des
  installations existantes sans migration dédiée.
### État réel — Lot 6, Palier B (livré, non déclaré terminé)

Carte Données et carte Prononciation inline ajoutées. Écarts et points à
vérifier sur appareil avant clôture :

- **Frontière `BackupManager`** : vit dans `:data`, invisible depuis
  `feature/settings` (Blueprint §12.4 — feature ne dépend que de
  domain/core). L'export/import (choix SAF, appel à `BackupManager`,
  lecture de `BuildConfig.VERSION_NAME`) est donc piloté par un
  `BackupViewModel` dédié dans le module `app`
  (`app/src/main/kotlin/com/inktone/app/BackupViewModel.kt`), câblé au
  `composable<SettingsRoute>` d'`InkToneNavHost`. Le reste de la carte
  (cache, dossier des modèles, réinitialisation des préférences) reste
  dans `SettingsViewModel`, qui n'a besoin que de `Context` et de
  `domain` pour ça.
- **`BackupManager.exportTo` retourne maintenant un `Boolean`** (succès
  d'écriture) au lieu d'avaler silencieusement l'échec de
  `FileStorageService.writeToUri` — corrigé dans ce palier, testé par
  `exportTo_puis_importFrom_restitue_les_donnees_aller_retour_complet`
  (`BackupManagerTest`), qui fait un vrai aller-retour via `BackupManager`
  plutôt que de ne construire qu'un `BackupPayload` à la main.
- **Dossier des modèles** : chemin fixe (`filesDir/voices`, même
  convention que `SherpaOnnxModelPaths`/`VoiceModelDownloader` dans
  `infrastructure/tts`), toujours en lecture seule — aucune capacité de
  déplacement n'existe dans ce lot, signalé via une icône cadenas plutôt
  que masqué.
- **Vider le cache** : taille réellement calculée (`Context.cacheDir`,
  parcours récursif des fichiers), pas estimée — testé avec un vrai
  fichier temporaire (`SettingsViewModelTest`, Robolectric — un
  `Context.cacheDir` exige un environnement Android, indisponible en JVM
  pur). `IoDispatcher` (qualifieur Hilt dédié,
  `feature/settings/di/IoDispatcher.kt`) rend ce calcul substituable en
  test : un premier essai avec `Dispatchers.IO` en dur produisait un test
  intermittent (`advanceUntilIdle()` ne voit pas une coroutine qui a
  sauté sur un vrai pool de threads).
- **Bug réel trouvé et corrigé sur appareil** : la ligne « Dossier des
  modèles » plaçait le chemin absolu (`/data/user/0/...`) à côté du
  libellé sur la même `Row` — le chemin, non borné, écrasait le libellé
  caractère par caractère sur écran étroit (même classe de bug que le
  Stepper « Intervalle de pause » du palier précédent : un
  `Modifier.weight(1f)` seul ne protège pas contre un voisin qui grandit
  librement). Corrigé en mettant le chemin sur sa propre ligne, ellipsé à
  une seule ligne (`TextOverflow.Ellipsis`), sous le libellé plutôt qu'à
  côté.
- **Prononciation inline** : carte avec en-tête « Dictionnaire phonétique
  (n) » + bouton `+`, liste des règles, dialogue modal d'ajout/édition.
  L'écran séparé (`PronunciationRulesRoute`/`PronunciationRulesScreen`)
  est **conservé tel quel** — plus lié depuis les Réglages, mais toujours
  la cible du lien « Ajouter une règle de prononciation » du panneau Voix
  du lecteur (`ReaderTtsPanel`), vérifié non cassé (chemin de navigation
  inchangé dans `InkToneNavHost.kt`).
- **Édition d'une règle préserve `isEnabled`** : reconstruire un
  `PronunciationRule` par défaut sur une édition aurait silencieusement
  réactivé une règle désactivée par l'utilisateur — l'état existant est
  relu avant reconstruction (`SettingsViewModel.savePronunciationRule`),
  testé.
- **Non vérifié sur appareil** (nécessite un geste humain — sélection SAF,
  confirmation de dialogue) : aller-retour export → réinstallation →
  import complet (B2 dans `LOT_6_REGLAGES.md`), comportement sur fichier
  de sauvegarde invalide (B3), confirmations des trois actions
  destructives jusqu'au bout (B5).

---

## Écran : Marque-pages et notes — vue globale (drawer, b3)

Distinct du panneau par livre (§ Marque-pages — panneau latéral) : cet écran centralise signets, surlignages et annotations de **tous les livres** de la bibliothèque.

**Topbar** : flèche de retour + libellé « Marque-pages et notes », icône recherche repliable (même comportement que sur la Bibliothèque) + icône de tri.

**Puces de filtre** (ligne scrollable horizontale sous la topbar) : Tous / Signets / Surlignages / Notes.

**Recherche** : filtre par titre d'ouvrage ou contenu textuel.

**Tri** (menu déroulant via icône) : chronologique (récent/ancien) ou **alphabétique par titre d'ouvrage** — les entrées mélangeant plusieurs livres, le tri alphabétique porte sur le titre de l'ouvrage, pas sur le contenu de l'entrée.

**Cartes** : fond légèrement surélevé par rapport à l'écran. Contenu par carte :
- Extrait de texte du passage.
- Commentaire/annotation de l'utilisateur, si présent (note) — en italique, sous l'extrait.
- Titre de l'ouvrage + chapitre + date, en pied de carte (format `Titre · Chapitre X · 25 déc. 2025`, pas de pourcentage — jugé surchargé).
- Icône étoile en haut à droite si l'entrée est épinglée en favori — les favoris remontent en haut de liste indépendamment du tri choisi.

**Interactions :**
- **Clic sur une carte** → ouvre le lecteur directement au passage correspondant, avec un flash/surlignage temporaire sur la phrase ciblée.
- **Swipe-to-dismiss** sur une carte → supprime définitivement l'entrée, **avec confirmation** propre à cet élément (signet/surlignage/note) avant suppression.
- **Suppression d'un livre entier** (depuis la Bibliothèque, pas depuis cet écran) : avertissement distinct et déjà consigné dans la section Bibliothèque (§ popup d'actions par livre) — irréversible, précise que les marque-pages/notes associés au livre seront aussi supprimés. La liste ici se met à jour dynamiquement en conséquence.

Mockup validé sans correction après un ajustement (retrait du pourcentage entre parenthèses à côté du chapitre, jugé surchargé).

**État réel (Lot 4, code fait foi) :** écran implémenté (`LibraryItemsScreen`/`LibraryItemsUiState`/`LibraryItemsViewModel`, `feature/library`) — **remplace** `GlobalBookmarksScreen`/`GlobalBookmarksUiState`/`GlobalBookmarksViewModel` (Phase 9bis.6, signets seuls), supprimés dans ce lot plutôt que conservés en parallèle. Un audit qui cherche encore `GlobalBookmarksScreen` ne trouvera rien : c'est attendu, pas un fichier perdu. Adossé à une vue SQL `library_items` (`UNION` marque-pages + annotations, jointure sur `publications` pour le titre, `infrastructure/database`). L'**extrait de texte n'est disponible que pour les éléments créés après ce lot** — `Annotation.excerpt`/`Bookmark.excerpt` sont des colonnes nullables ajoutées par `MIGRATION_12_13` ; l'historique importé avant cette migration n'a pas d'extrait et la carte affiche alors le titre d'ouvrage et le chapitre seuls, sans blanc ni troncature visible (couvert par `LibraryItemDaoTest`/`DatabaseMigrationTest`). Le **flash à l'arrivée** dans le lecteur est différé jusqu'à confirmation de fin de mise en page du chapitre visé (`PendingHighlightTarget`, `ReaderViewModel`/`ReaderScreen`) — jamais émis avant, y compris sur un chapitre long où la mesure asynchrone prend le plus de temps.

---

## Écran : Statistiques de lecture (drawer, b6)

Tableau de bord visuel et détaillé des performances et habitudes de lecture/écoute, calculé en temps réel via `ReaderRepository` et les événements de session. Traité section par section (méthode habituelle) :
1. ✅ KPIs & Objectifs
2. ✅ Graphiques d'Activité (histogramme + heatmap)
3. ✅ Carte résumé Livre en cours + bouton Export
4. ✅ Écran dédié Détail par ouvrage (avec sélecteur)

**Flow acté pour l'articulation dashboard ↔ détail par ouvrage :** le tableau de bord principal affiche les stats globales + une carte résumé du livre en cours (progression %, temps restant). Un clic sur cette carte (ou un bouton « Détails par livre ») ouvre un écran dédié séparé avec un sélecteur de livre en haut pour naviguer d'un ouvrage à l'autre — évite de surcharger le dashboard principal avec l'historique de sessions de chaque livre, y compris ceux déjà terminés.

**Export acté :** un seul bouton « Exporter les statistiques » (icône Download/Share), déclenche un menu contextuel (bottom sheet) proposant le choix CSV ou JSON — pattern Material 3 standard, plutôt que deux boutons distincts par format.

### Section 1 — KPIs & Objectifs

- **Carte objectif du jour** (jauge + streak regroupés) : jauge circulaire de progression (temps du jour / objectif configuré) avec le temps accumulé affiché au centre, à côté un compteur de jours consécutifs (streak) avec icône flamme + libellé de régularité (« Série en cours · régularité élevée »).
- **Temps cumulé ventilé** : deux cartes égales côte à côte, Lecture visuelle et Écoute TTS, chacune avec sa durée totale.
- **Volumes parcourus** : trois petites cartes-stats compactes côte à côte — Livres finis, Pages lues, Mots parcourus (format abrégé pour les grands nombres, ex. « 1,4M »).

Mockup validé sans correction.

### Section 2 — Graphiques d'Activité

Deux cartes distinctes : histogramme et heatmap. Version affinée par Issa à partir d'une première proposition de Claude, avec plusieurs ajouts qui améliorent la lisibilité par rapport à la proposition initiale.

**Carte Histogramme :**
- **En-tête** : titre « Activité » + **total de la période affichée** (ex. « 6h 25m ») + **variation en pourcentage** vs la période équivalente précédente (semaine en cours vs semaine dernière, ou mois en cours vs mois dernier selon le sélecteur).
- Sélecteur **Semaine/Mois** en haut à droite.
- Barres empilées par jour, deux segments (Lecture visuelle en vert, Écoute TTS en violet), avec une **ligne de repère pointillée horizontale** pour faciliter la lecture des hauteurs relatives.
- **Marqueur visuel du jour courant** (libellé du jour en couleur + point, ex. « M • » pour Mercredi) pour se repérer immédiatement dans la semaine.
- Légende Lecture visuelle / Écoute TTS en pied de carte.

**Carte Heatmap (« Habitudes de lecture ») :**
- **En-tête** : titre + **pic horaire affiché directement** (ex. « Pic : 20h-22h ») — évite d'avoir à scanner la grille pour trouver la conclusion.
- Grille jours (L à D) × créneaux horaires (6h/10h/14h/18h/22h, 5 colonnes), intensité de couleur croissante selon l'activité (dégradé sur la couleur d'accent violette).
- **Séparation visuelle légère** entre la semaine (L-V) et le week-end (S-D) — pertinent car les habitudes diffèrent souvent entre les deux.
- **Légende d'intensité** en pied de carte (Inactif → Très actif, dégradé de couleur).

Mockup validé — version d'Issa retenue telle quelle, avec confirmation sur le sens du `+12%` (comparaison à la période équivalente précédente).

### Section 3 — Carte résumé Livre en cours + bouton Export

**Carte Livre en cours** (en pied du dashboard) : étiquette « Livre en cours » + couverture miniature + titre + barre de progression + pourcentage et temps restant estimé sur la même ligne (ex. « 64% · encore ≈ 2h 40min estimées »). Chevron à droite, clic sur la carte → ouvre l'écran dédié Détail par ouvrage (Section 4).

**Bouton Export** : « Exporter les statistiques » (icône Download), pleine largeur, sous la carte Livre en cours. Au clic, ouvre un **bottom sheet** avec deux options — **Format CSV** (récapitulatif des sessions) et **Format JSON** (données brutes d'événements), chacune avec une icône distincte et un sous-texte rappelant ce que contient le format.

### Section 4 — Écran dédié Détail par ouvrage

**Topbar** : flèche de retour + sélecteur de livre (titre courant + chevron vers le bas, ouvre la liste de tous les ouvrages pour naviguer d'un livre à l'autre).

**Deux cartes-stats côte à côte** : **Vitesse (WPM)** — moyenne en mots/minute ; **Temps restant** — estimation ajustée d'après le rythme des dernières sessions.

**Historique des sessions** : liste chronologique, chaque ligne = icône de mode (œil pour lecture visuelle, casque pour écoute TTS) + date + chapitres parcourus, durée affichée à droite.

Mockup validé sans correction. **Point tranché (Lot 7, tâche 7.3)** : une session mixte affiche les deux icônes de mode côte à côte, à taille pleine (24 dp, pas de « mode dominant » — un ratio 26/24 avec une seule icône mentirait), avec la ventilation par mode (minutes + icône réduite 14–16 dp, car accolée à un nombre) sous la durée totale. Somme toujours cohérente (arrondi du total puis répartition, jamais des trois valeurs indépendamment) et annonce TalkBack unique (« 45 minutes, dont 30 en lecture et 15 en écoute »).

### Écarts assumés

Trois écarts délibérés entre cette cible et l'implémentation réelle, consignés ici pour qu'un audit futur les retrouve sans avoir à fouiller les KDoc du code (Lot 7, tâche 7.6) :

- **Chapitres parcourus absents de l'historique par ouvrage** (Section 4), alors que le libellé ci-dessus les mentionne. Trois raisons : l'instabilité structurelle des chapitres dans les EPUB mal formés ou les PDF (un `chapterIndex` peut pointer vers une ressource inexistante ou un découpage arbitraire selon le parseur) ; les micro-sessions TTS qui n'avancent pas l'index de chapitre, donnant l'illusion d'un blocage alors que l'utilisateur progresse ; et la séparation des responsabilités entre `ReadingState` (où reprendre) et `ReadingSession` (quand et combien de temps a-t-on lu). L'historique reste donc purement temporel (dates, durées) — voir `BookStatisticsViewModel.kt`, KDoc de `SessionHistoryItem`.
- **Heatmap à 5 créneaux** (6h/10h/14h/18h/22h) plutôt qu'une grille à 24 créneaux horaires bruts — choix d'implémentation non spécifié par cette cible, retenu pour la lisibilité de la grille 7×5.
- **Session mixte** : traitement tranché ci-dessus (tâche 7.3), referme le point resté ouvert depuis la conception initiale de cet écran.

**Écran Statistiques de lecture entièrement conçu — 4 sections validées.**

---

## Écran : Synchronisation (drawer, b5)

Module piloté par un état scellé (`SyncUiState`), basculant entre deux vues distinctes selon l'état :
- **`Unconfigured`** ou édition en cours → **écran Configuration**.
- **`Configured`** et pas en édition → **écran Opérationnel** (Dashboard).
- **`Authenticating`** → écran de chargement plein écran (« Connexion au service cloud... »), non maquetté en détail — un simple indicateur de chargement centré avec le message.

**Décision structurelle actée :** WebDAV et Google Drive sont **mutuellement exclusifs** — un seul fournisseur cloud actif à la fois (`account: SyncAccount` unique dans l'état `Configured`). La Sauvegarde Locale E2EE (`.rfbackup`) est une **fonction manuelle indépendante**, toujours disponible quel que soit l'état de la sync cloud.

### Écran Configuration

**Top app bar** : flèche de retour + titre « Configuration Sync » + bouton **Enregistrer** (en haut à droite, ferme la config et bascule vers l'écran Opérationnel).

**Carte WebDAV** : badge ACTIF/INACTIF en en-tête. Si actif — bordure et fond teintés couleur succès, champs URL/Identifiant/Mot de passe, bouton Tester + badge « Connecté », bouton Déconnecter aligné à droite.

**Carte Google Drive** : si l'autre fournisseur est actif, la carte est **grisée et désactivée** (opacité réduite, bouton désactivé), avec un message explicite : « Désactivez WebDAV pour connecter Google Drive ». Si Google Drive est le fournisseur actif, elle affiche à l'inverse avatar + email lié + badge Connecté + bouton Déconnecter (état symétrique à WebDAV).

**Carte Fichier local (.rfbackup)** : badge « AUTONOME », champ mot de passe de chiffrement E2EE, boutons Exporter / Importer côte à côte. Toujours active, indépendante du fournisseur cloud choisi.

**Retour des actions (test de connexion, export, import)** : pas de carte de statut permanente dans le layout — une **snackbar temporaire** apparaît après chaque action et disparaît d'elle-même, cohérent avec le reste de l'app.

Mockup validé, après correction d'une confusion initiale entre les deux écrans (une carte de statut de synchronisation cloud s'était glissée par erreur dans l'écran de Configuration).

### Écran Opérationnel (Dashboard)

**Header profil** : avatar (initiale) + identifiant du compte + fournisseur actif (ex. « WebDAV Actif ») + bouton « Gérer » (bascule vers l'écran Configuration).

**Action rapide** : horodatage relatif de la dernière synchro (« Il y a 2 minutes ») + bouton pleine largeur « Synchroniser maintenant ».

**Flotte d'appareils** : badge de comptage en en-tête, liste des appareils liés — icône type d'appareil, nom, badge « (Cet appareil) » pour l'appareil courant, statut texte (« À jour • Aujourd'hui 14:22 » / « Vu hier à 23:15 »), point de couleur (vert = en ligne/à jour, gris = pas récent).

**Toggles** : Synchro automatique en arrière-plan, Wi-Fi uniquement — deux interrupteurs simples.

**Journal d'activité** : liste compacte des derniers événements de sync (ex. « Alignement progression livre (+2 chapitres) »), horodatage à droite.

Mockup validé sans correction.

---

## Écran : Galerie de thèmes (pied de drawer)

Trois piliers retenus par Issa, priorisés parmi plusieurs idées proposées par Claude pour une expérience premium : **aperçu vivant** (mini-page réelle plutôt que pastille de couleur), **séparation nette Ambiances vs Confort & Accessibilité** (l'accessibilité n'est pas traitée comme une esthétique de second rang), et **Studio de thème personnalisé** (au-delà des presets).

**Topbar** : flèche de retour + titre « Galerie de thèmes » + sous-titre descriptif (« Personnalisation du rendu du livre »). Pas de bouton `+` en topbar — l'accès à la création passe uniquement par la carte dédiée en bas de galerie.

**Section 1 — Ambiances de lecture** : grille 2 colonnes, cartes-aperçu vivant. Chaque carte affiche une **mini-page réelle** avec un extrait de texte fixe (« La lumière filtrait doucement à travers les persiennes... »), rendu avec la **vraie police et couleur du thème** (serif pour les thèmes clairs type Papier/Sépia, sans-serif pour les thèmes sombres type Obsidienne/Sauge), un numéro de page fictif en pied de mini-page, et un point de couleur d'accent (couleur de progression/surlignage du thème). Le thème actif porte un **badge « ACTIF »** en haut à droite de la carte (pas un simple contour, pour éviter la confusion avec un état de survol). Mention « Appui long pour tester » en en-tête de section — prévisualisation en contexte réel sans validation immédiate. Thèmes de départ : Papier Clair, Obsidienne, Sépia Vintage, Sauge & Olive.

**Section 2 — Confort & Accessibilité** : format **liste** (pas grille), délibérément différent des deux autres sections — ces réglages sont vécus comme des paramètres à activer plutôt que des ambiances à comparer visuellement. Chaque ligne : icône-échantillon carrée (ex. « Aa » sur fond clair pour OpenDyslexic, « OLED » sur fond noir pour l'AMOLED) + titre + sous-titre descriptif + chevron. Deux entrées de départ : **OpenDyslexic & Espacement** (police adaptée aux troubles de la lecture) et **Noir Absolu AMOLED** (contraste maximal & économie d'énergie).

**Section 3 — Mes Thèmes Personnalisés** : grille 2 colonnes, retour au format aperçu vivant. Première case : **carte en pointillés** « Créer un thème » (icône `+` circulaire + sous-texte « Studio de création ») — ouvre l'écran Studio dédié. Thèmes perso déjà créés : même format aperçu vivant que les Ambiances, mais avec une **icône crayon** (plutôt qu'un chevron) en pied de carte pour signaler qu'ils sont modifiables, contrairement aux thèmes officiels figés.

### Studio de thème personnalisé (`ThemeStudioScreen`)

Écran dédié plein écran, ouvert depuis la carte pointillée « Créer un thème » de la Galerie. Version affinée par Issa à partir d'une première proposition de Claude, avec plusieurs ajouts qui élèvent nettement le niveau par rapport au scope initial.

**Topbar** : flèche de retour + titre « Studio de Thème » + bouton **Sauvegarder** en haut à droite (même pattern que Config Sync).

**Aperçu dynamique en direct** (moitié supérieure de l'écran) : mockup de page de lecture réelle avec titre de chapitre, extrait de texte sur plusieurs lignes, **un mot surligné inline** (`<mark>`) pour démontrer la couleur de surlignage en contexte, puis barre de progression + pourcentage + pagination en pied — mis à jour en direct à chaque changement de couleur. Barre d'en-tête de l'aperçu avec un **badge de contraste WCAG calculé en direct** (ex. « ✓ WCAG AAA (14.2:1) »).

**Comportement du badge WCAG — décision actée :** informatif, **jamais bloquant**. Sous le seuil de lisibilité, le badge passe au orange/rouge avec un message d'avertissement sous l'aperçu (« Ce thème peut être difficile à lire pour certaines personnes — vous pouvez tout de même l'enregistrer. »), mais le bouton Sauvegarder reste actif. Cohérent avec la mission d'accessibilité du projet sans retirer le contrôle créatif à l'utilisateur — un thème à faible contraste peut être un choix délibéré (ambiance volontairement douce).

**Panneau de réglages** (moitié inférieure) :
- **Nom du thème** : champ texte en haut, avant les sélecteurs de couleur.
- **Quatre sélecteurs de couleur** (extension actée du scope initial de 3 à 4) : Fond de page, Texte principal, Accent & Progression, **Surlignage d'annotation** (ajout volontaire d'Issa). Chaque ligne : libellé + valeur hex + pastille de couleur cliquable (ouvre un color picker natif, pas détaillé plus avant dans ce document).
- **Palette de départ** : quatre presets rapides (Sombre / Clair / Chaud / Néon) pour ne pas partir d'une page blanche à chaque création.

Mockup validé sans correction. **Galerie de thèmes entièrement conçue, Studio inclus.**

---

## Écran : À propos (pied de drawer)

Centralise l'identité visuelle d'InkTone, ses engagements de confidentialité (architecture 100% locale, zero-server), sa stack technique, et le support interactif. Version affinée par Issa à partir d'une spec structurée en 6 niveaux, avec une **correction factuelle importante** apportée par Claude avant tout mockup.

**⚠️ Correction actée : Piper VITS → Kokoro.** La spec d'origine mentionnait Piper VITS (licence MIT) comme moteur TTS. Piper a été **éliminé du projet** (relicensing GPL-3.0, octobre 2025) — **Kokoro** est le moteur retenu (licence Apache-2.0). Cet écran affichant des informations réelles de diagnostic (copiées au presse-papier, envoyées par email de support), l'erreur aurait été visible des utilisateurs. Corrigé partout : `ttsEngineInfo` = `"Sherpa-ONNX (Kokoro)"`, et l'entrée stack technique Niveau 5 devient **Kokoro** — modèles vocaux neuronaux HD, Apache-2.0 (au lieu de Piper VITS/MIT).

**Topbar** : flèche de retour + titre « À propos & Confidentialité ».

**Hero header** : icône livre + nom de l'app en grand + **badge de version cliquable** (`v0.1.0 • Build 42 (Release)`) — clic court affiche un snackbar « Version officielle », clic long copie au presse-papier les specs système complètes (modèle appareil, version OS, version build, moteur TTS).

**Carte description** : texte de présentation du produit, justifié.

**Grille Engagements & Confidentialité** : 3 colonnes compactes — 100% Local (inférence CPU), Vie Privée (stockage isolé), Hors-Ligne (zéro serveur) — icône + titre + sous-texte très court par pilier.

**Ressources & Support** (regroupées sous un même en-tête) :
- **Dépôt GitHub officiel** : carte neutre, ouvre l'URL en externe.
- **Signaler un problème** : carte à **bordure accentuée** (couleur d'accent) — mise en avant volontaire par rapport au GitHub, car action plus directement utile en cas de besoin. Déclenche un email pré-rempli avec diagnostic automatique (version app, appareil, OS, moteur TTS).

**Accordéon Architecture Tech & Licences** : dépliable/repliable, liste Sherpa-ONNX / Kokoro / Android Jetpack & Room, chacun avec description courte et **badge de licence coloré selon la licence** (pas selon la bibliothèque — les trois étant Apache-2.0, les trois badges partagent la même couleur, corrigé après une incohérence dans le premier jet).

**Pied de page** : copyright centré, année dynamique + nom du développeur + licence du projet (MIT).

Mockup validé sans correction après les deux corrections apportées (Kokoro, cohérence des couleurs de licence).

**Le flux général de niveau 1 est maintenant entièrement conçu** (hors Catalogues OPDS, hors périmètre de cette session — planifié à part, `ADR-023`).

---

## Panneau unifié du Lecteur : entièrement conçu

Les 7 icônes (Sommaire, Marque-pages, Play, Thème, TT, Minuteur, Haut-parleur) et Luminosité sont maintenant toutes spécifiées et maquettées.

---

## Point d'étape — ce qui reste sur l'ensemble du flux

**Fait — flux de niveau 1 entièrement conçu :** Onboarding (3 cartes) · Bibliothèque (barre du haut, état vide, état peuplé mosaïque/liste, menu déroulant + écran de détail Séries/Tags, popup de filtrage, bottom sheet 3-points, drawer, avertissement de suppression en cascade des marque-pages/notes) · Import (progression, retours) · Lecture (vue silencieuse, HUD, popup de sélection, 7 sous-écrans du panneau unifié + luminosité, couche TTS complète — barre de contrôle et FAB replié, panneau Marque-pages par livre entièrement conçu avec ses trois onglets Notes/Surlignages/Marque-pages) · Récents (états peuplé et vide) · Réglages (écran entièrement conçu, 6 cartes) · Marque-pages et notes — vue globale (drawer b3) · Statistiques de lecture (écran entièrement conçu, 4 sections) · Synchronisation (écran entièrement conçu — Configuration + Opérationnel) · Galerie de thèmes entièrement conçue (Studio de création inclus) · **À propos (écran entièrement conçu, avec correction factuelle Piper→Kokoro).**

**Seul point hors scope de cette session :**
- Catalogues OPDS (b4) — non conçu ici ; réintégré en v1.x et planifié séparément (`ADR-023`, `docs/execution/LOT_13_CATALOGUES_OPDS.md`)

**Toutes les questions en suspens ont été tranchées, y compris les deux derniers points ouverts identifiés lors de l'audit de fin de session :**
- Icône hamburger comme déclencheur du drawer, et positionnement de b3 juste après Bibliothèque — **confirmés explicitement par Issa**.
- Affichage d'une session mixte lecture/TTS dans l'historique par ouvrage (§ Statistiques, Section 4) — **tranché depuis (Lot 7, tâche 7.3)** : deux icônes de mode 24dp côte à côte sans mode dominant, ventilation par mode sous la durée totale, arrondi total-puis-répartition, annonce TalkBack unique. Voir § Statistiques, Section 4 pour le détail et `BookStatisticsScreen.kt`/`BookStatisticsViewModel.kt` pour l'implémentation vérifiée.

---

## Lecture PDF — Matrice des fonctionnalités (Lot 12, tâche 12.14)

**Contexte :** le support PDF en affichage seul (ADR-017, volet 1) est livré au Lot 12 (2026-08-12). Aucune maquette préalable n'existait pour `FixedPageContent` — exception assumée (décision actée 20 du plan), sur le même modèle que `SyncConflictBottomSheet` au Lot 11 : la conception a été faite directement en code, consignée ici a posteriori.

### Fonctionnalités désactivées pour le format PDF

Chaque désactivation est explicite et visible dans le code — jamais un bouton qui reste actif sans effet (décision actée 16 du plan).

| Fonctionnalité | Statut en lecture PDF | Raison |
|---|---|---|
| **TTS / Lecture audio** | ❌ Masqué (`playCurrentSentence` neutralisé, `showTtsControls = false`) | Pas de « phrase courante » en navigation manuelle page à page ; le TTS sur PDF est un lot distinct conditionné (ADR-017, volet 2) |
| **Minuteur de sommeil** | ❌ Masqué (`showSleepTimer = false`) | Sans TTS actif, un minuteur d'arrêt automatique n'a pas de sens |
| **Bascule SCROLL/PAGED** | ❌ Masqué (`showTtsControls = false` masque le bouton Mode ; `ToggleReadingMode` neutralisé dans le ViewModel) | Un PDF est nativement paginé — le défilement vertical n'a pas de sens pour un format à mise en page fixe |
| **Sélection libre au mot / Annotations** | ❌ Non déclenché | Un rendu bitmap sous `Canvas` n'offre pas la sélection native de `BasicTextField` ; l'ajout demanderait un hit-testing dédié sur les `BoundingBox` de mots (hors périmètre) |
| **Repos oculaire** | ✅ Conservé | Indépendant du TTS — simple rappel de pause visuelle, fonctionne déjà en lecture purement visuelle |
| **Signets** | ✅ Adapté | Même `Locator`, mêmes Use Cases — granularité page (`chapterIndex`), pas phrase |
| **Recherche plein texte** | ✅ Compatible | Texte extrait au parsing (PDF vectoriel) indexé en FTS, navigation via `chapterIndex` sans code spécifique |
| **Thèmes sombre/sépia** | ✅ Vectoriel seulement | `ColorMatrix` d'inversion appliqué si la page contient du texte extrait (`paragraphs.isNotEmpty()`). Page scannée (image pure) : rendu original conservé |
| **Reprise de lecture** | ✅ Conservée | `pageOffsetY` stocké dans le `Locator`, restaure la page ET le défilement intra-page |

### Principe de navigation

- **Page = Chapitre** dans le `DocumentModel` (décision actée 4 du plan) : `currentChapterIndex` = page courante, `NextChapter`/`PreviousChapter`/`JumpToChapter` fonctionnent sans code spécifique.
- **`HorizontalPager`** pour la navigation entre pages (Compose Foundation).
- **Pas de reflow** : le bitmap PDFium est affiché tel quel (ADR-017).
- **Zoom** : transformation GPU (`graphicsLayer`) pendant le geste de pincement ; re-rasterisation haute définition au relâchement (debounce 250 ms).
- **Cache** : `LruCache` limité à 5 pages (active, N-1, N+1, N-2, N+2).

### Écarts déclarés

1. **Pas de maquette préalable** — `FixedPageContent` conçu directement en code (même exception que `SyncConflictBottomSheet` au Lot 11).
2. **Rendu en tuiles simplifié** — une seule re-rasterisation à résolution supérieure au lieu d'un vrai découpage en grille de tuiles indépendantes.
3. **Pas de mode paysage / double-page tablette** — à ouvrir seulement sur demande explicite.
4. **Pas de « Forcer l'inversion » sur pages scannées** — l'option de réglage n'est pas encore exposée dans l'interface des préférences.

---

## Prochain palier

**Le flux général de niveau 1 est entièrement conçu.** Prochaines pistes possibles pour une future session, à définir avec Issa :
- Niveau 2 : détail des interactions/comportements internes à chaque item du drawer (fonction précise de chaque destination, au-delà de l'apparence déjà posée).
- Toute reprise de contact avec le code existant (audit, phases d'exécution Claude Code) sort du périmètre de cette session de conception UX.
