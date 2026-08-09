# Lot 9 — Galerie de thèmes et Studio

**Base :** `main` à `376d9131`. Branche : `lot-9-galerie-themes`. Référence cible : `UX_FLOW_DESIGN.md` § Galerie de thèmes, § Studio de thème personnalisé.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Le verrou : le modèle de thème est fermé

`ReadingTheme` est un **enum de quatre valeurs** — `LIGHT, DARK, SEPIA, SYSTEM` (`ReadingState.kt:7`). La cible demande :

- **4 ambiances nommées** : Papier Clair, Obsidienne, Sépia Vintage, Sauge & Olive
- **2 entrées Confort & Accessibilité** : OpenDyslexic & Espacement, Noir Absolu AMOLED
- **N thèmes personnalisés**, à quatre couleurs libres et nom saisi par l'utilisateur

**Un enum ne peut pas porter de thème créé par l'utilisateur.** Aucune quantité de travail d'UI ne contournera ce point. D'où un palier de modèle avant tout écran — même schéma qu'aux lots 4 et 5.

Second constat : le vocabulaire de la cible (`OBSIDIAN`, `NIGHT`, `DAY`, cités dans les présets du § Réglages) n'existe nulle part dans le code. Les présets du lot 6 s'appuient donc sur des valeurs qui n'ont jamais existé — à réconcilier dans ce lot.

## Découpage en deux paliers poussables

| Palier | Contenu | Risque |
|---|---|---|
| **A** | Modèle ouvert, migration, recâblage des consommateurs | Élevé — touche cinq lots déjà livrés |
| **B** | Écran Galerie + Studio | Modéré — surtout de l'UI |

**Pousser après le palier A**, et le vérifier sur appareil avant d'écrire une ligne d'UI : il modifie le thème de lecture, donc le lecteur entier.

---

# PALIER A — Modèle

## Tâche 9.1 — Ouvrir le modèle de thème

Remplacer l'enum par un type portant ses valeurs :

- identifiant, nom affichable, indicateur intégré/personnalisé ;
- **quatre couleurs** : fond de page, texte principal, accent & progression, surlignage d'annotation ;
- **famille de police**, la cible associant serif aux ambiances claires et sans-serif aux sombres.

Les thèmes intégrés sont des constantes ; les personnalisés vivent en base, avec migration Room explicite.

**Migration des préférences existantes.** `UserPreferences.theme` stocke aujourd'hui un nom d'enum. Faire correspondre les valeurs existantes aux nouveaux identifiants intégrés — `LIGHT` → Papier Clair, `DARK` → Obsidienne, `SEPIA` → Sépia Vintage — et **ne perdre aucun réglage utilisateur**. `SYSTEM` est un cas distinct : depuis le lot 6, le thème système est porté par `AppThemeMode`, séparé du thème de lecture. Vérifier qu'aucune préférence ne dépend encore de `ReadingTheme.SYSTEM` avant de le retirer.

`Ouvre le modele de theme de lecture aux themes personnalises`

---

## Tâche 9.2 — Recâbler les consommateurs existants

Le thème de lecture est consommé par cinq lots déjà livrés. Les recenser **par le compilateur**, pas de mémoire, et traiter au minimum :

- **La bascule cyclique du lecteur** (lot 3b) : elle cycle Clair → Sombre → Sépia. Avec quatre ambiances plus les thèmes personnalisés, cycler devient impraticable — l'utilisateur passerait dix fois pour revenir. **À trancher** : cycler sur trois ambiances de référence uniquement, ou remplacer la bascule par une ouverture de la Galerie. Je penche pour la première : le geste rapide garde sa valeur, et la Galerie reste le chemin complet.
- **Les présets du lot 6** : « Mode sombre » applique `OBSIDIAN` + `NIGHT` selon la cible, deux valeurs inexistantes. Les faire pointer sur les identifiants réels.
- **Le sélecteur de thème des Réglages** et **le panneau TT** du lecteur.

## Piège à traiter explicitement — la pagination

Depuis le lot 3a, `PaginationStyleKey` **exclut délibérément le thème** : les couleurs ne déplacent pas le texte. C'est un invariant testé (lot 3d, test 8).

**Il cesse d'être vrai dans ce lot.** Un thème porte désormais une famille de police, et changer de police change la mise en page. Si le thème continue d'être exclu de la clé, changer d'ambiance laissera une pagination périmée — exactement le défaut latent corrigé au lot 3b.2, réintroduit par l'autre bout.

**À faire :** faire entrer la **police du thème** dans la clé d'invalidation, sans y faire entrer les couleurs. La règle devient « les couleurs n'invalident pas, la police oui » — et le test du lot 3d est à mettre à jour en conséquence, pas à supprimer.

`Recable les consommateurs du theme de lecture`

---

## Tâche 9.3 — Tests du palier A

1. Migration : une base avec `LIGHT`, `DARK` et `SEPIA` s'ouvre et restitue les ambiances correspondantes, sans perte.
2. Un thème personnalisé survit à un redémarrage.
3. **Changer les couleurs d'un thème n'invalide pas la pagination ; changer sa police l'invalide.** Mise à jour du test 8 du lot 3d, pas suppression.
4. La bascule cyclique du lecteur reste bornée et revient à son point de départ en un nombre fixe de taps.
5. Les présets du lot 6 appliquent des identifiants existants — aucun nom mort.

`Ajoute les tests du modele de theme ouvert`

### Vérifications device — palier A

| # | Attendu |
|---|---|
| A1 | Installer par-dessus une version antérieure : le thème de lecture choisi est conservé |
| A2 | La bascule de thème du lecteur fonctionne toujours et reste rapide |
| A3 | Le préset « Mode sombre » des Réglages applique bien un thème sombre |
| A4 | Changer d'ambiance dans le lecteur : si la police change, le nombre de pages se recalcule ; sinon il ne bouge pas |

---

# PALIER B — Écrans

## Tâche 9.4 — Galerie de thèmes

Route et destination, atteignable depuis le **pied de drawer** — le bouton « Thèmes » retiré au lot 1 faute de destination. C'est la troisième des quatre destinations masquées à réactiver.

**Topbar :** flèche de retour, titre « Galerie de thèmes », sous-titre « Personnalisation du rendu du livre ». **Pas de bouton `+`** : la création passe uniquement par la carte dédiée en bas de galerie.

**Section 1 — Ambiances de lecture.** Grille 2 colonnes, **cartes-aperçu vivant** : une mini-page réelle avec l'extrait fixe de la cible, rendue avec la **vraie police et les vraies couleurs** du thème, un numéro de page fictif en pied, et un point de couleur d'accent. Le thème actif porte un **badge « ACTIF »** en haut à droite — pas un simple contour, choix explicite de la cible pour éviter la confusion avec un état de survol. En-tête de section : « Appui long pour tester ».

L'**appui long prévisualise en contexte réel sans valider**. C'est un vrai comportement, pas une mention décorative : si la prévisualisation n'est pas réalisable, retirer la mention plutôt que l'afficher sans effet.

**Section 2 — Confort & Accessibilité.** Format **liste**, délibérément différent des deux autres sections. Chaque ligne : icône-échantillon carrée, titre, sous-titre, chevron. Deux entrées : OpenDyslexic & Espacement, Noir Absolu AMOLED.

**Section 3 — Mes Thèmes Personnalisés.** Grille 2 colonnes, format aperçu vivant. Première case : carte en pointillés « Créer un thème », icône `+` circulaire, sous-texte « Studio de création ». Les thèmes créés portent une **icône crayon** au lieu du chevron, pour signaler qu'ils sont modifiables — les thèmes officiels sont figés.

`Ajoute la galerie de themes`

---

## Tâche 9.5 — Studio de thème

Écran plein écran, ouvert depuis la carte pointillée.

**Topbar :** retour, « Studio de Thème », bouton **Sauvegarder** en haut à droite.

**Aperçu dynamique, moitié supérieure :** page de lecture réelle — titre de chapitre, extrait sur plusieurs lignes, **un mot surligné inline** pour montrer la couleur d'annotation en contexte, barre de progression, pourcentage et pagination en pied. Mis à jour **en direct** à chaque changement de couleur.

**Badge WCAG calculé en direct** dans l'en-tête de l'aperçu. `calculateContrastRatio(background, foreground)` existe déjà (`ContrastRatio.kt:14`) et est testé — l'utiliser, ne pas en écrire un second.

**Comportement du badge — décision actée, à respecter à la lettre :** informatif, **jamais bloquant**. Sous le seuil, le badge passe à l'orange puis au rouge avec un avertissement sous l'aperçu, mais **le bouton Sauvegarder reste actif**. Un thème à faible contraste peut être un choix délibéré. Ne pas désactiver le bouton, ne pas ajouter de confirmation supplémentaire.

**Panneau de réglages, moitié inférieure :**
- Nom du thème, en champ texte, **avant** les sélecteurs de couleur.
- **Quatre sélecteurs** : fond de page, texte principal, accent & progression, surlignage d'annotation. Chaque ligne : libellé, valeur hexadécimale, pastille cliquable ouvrant un sélecteur de couleur.
- **Palette de départ** : quatre présets rapides — Sombre, Clair, Chaud, Néon — pour ne pas partir d'une page blanche.

**Modification et suppression** des thèmes personnalisés : l'icône crayon de la Galerie doit mener quelque part. Une suppression est destructive — confirmation obligatoire, et comportement défini si le thème supprimé est le thème actif.

`Ajoute le studio de theme personnalise`

---

## Tâche 9.6 — Tests du palier B

1. Une carte d'ambiance rend l'extrait avec la police **et** les couleurs du thème, pas une pastille.
2. Le badge « ACTIF » suit le thème réellement appliqué.
3. L'appui long prévisualise et **ne valide pas** ; relâcher revient au thème courant.
4. Le badge WCAG reflète le ratio calculé, et **Sauvegarder reste actif** sous le seuil.
5. Les quatre sélecteurs modifient bien quatre propriétés distinctes de l'aperçu.
6. Supprimer le thème actif laisse l'application dans un état valide, sur un thème de repli.
7. Un thème sans nom ne peut pas être sauvegardé, ou reçoit un nom par défaut — pas d'entrée anonyme en galerie.

`Ajoute les tests de la galerie et du studio`

---

## Tâche 9.7 — Consigner

Dans `UX_FLOW_DESIGN.md` : mettre à jour la note du drawer (le pied passe à trois boutons, « Thèmes » n'est plus masqué), consigner l'arbitrage sur la bascule cyclique du lecteur (9.2), et **corriger la règle d'invalidation de pagination** au § Lecture — la formulation « le thème n'invalide jamais » devient « les couleurs n'invalident pas, la police oui ».

`Consigne l activation de la galerie de themes dans la cible`

### Vérifications device — palier B

| # | Avant (`376d9131`) | Après attendu |
|---|---|---|
| B1 | Pied de drawer : 2 boutons | 3 boutons ; « Thèmes » ouvre la Galerie |
| B2 | — | Les cartes d'ambiance montrent une vraie mini-page, pas une pastille |
| B3 | — | Appui long : le lecteur adopte le thème temporairement, sans le valider |
| B4 | — | Créer un thème, le sauvegarder, l'appliquer : le lecteur l'utilise réellement |
| B5 | — | Contraste très faible : avertissement affiché, sauvegarde **possible** |
| B6 | — | Le mot surligné de l'aperçu change bien avec la 4ᵉ couleur |
| B7 | — | Supprimer le thème actif : pas de crash, repli sur un thème valide |
| B8 | — | Un thème personnalisé survit à un redémarrage complet |

Le point B4 est le seul qui prouve la chaîne entière — un aperçu correct dans le Studio ne garantit pas que le lecteur applique le thème.

---

## Hors périmètre explicite

Onboarding, audit Crashlytics, lot 3f.

**Synchronisation** — dernière destination masquée, en attente d'une décision de périmètre V1.
