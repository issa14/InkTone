# Lot 7 — Statistiques : affinage

**Base :** branche `lot-statistiques-palier1` à `ec9a0ad8` (quatre paliers déjà livrés, ~4 500 lignes). Référence cible : `UX_FLOW_DESIGN.md` § Statistiques de lecture.

**Nature du lot :** affinage, pas construction. Le verrou d'origine est levé — `ReadingSessionTracker` et les migrations 17/18 fournissent enfin la série temporelle qui bloquait la section 2 depuis l'audit initial. Les quatre sections existent. Ce lot corrige des écarts à la cible et deux duplications.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

---

## Tâche 7.1 — Objectif quotidien : brancher la préférence

**Prioritaire, et ce n'est pas un écart de valeur par défaut.**

`StatisticsViewModel.kt:74` écrit `dailyGoalMinutes = 20` en dur, et le ViewModel n'injecte **aucun** `PreferencesRepository` — il ne peut donc lire aucune préférence, quelle qu'en soit la valeur.

Or le lot 6 a livré, dans la carte « Performance & bien-être », un curseur d'objectif quotidien de 10 à 120 min qui écrit dans `UserPreferences.dailyGoalMinutes`. **L'utilisateur règle 45 min et la jauge reste calibrée sur 20.** C'est un réglage sans effet — sixième occurrence de l'antipattern depuis l'audit initial, et la première introduite dans un lot déjà déclaré clos.

**À faire :** injecter `PreferencesRepository` et intégrer `dailyGoalMinutes` au `combine` existant, pour que la jauge se recalibre sans redémarrage quand l'utilisateur change son objectif.

**Ne pas confondre avec la valeur par défaut.** La cible mentionne 30 min, `UserPreferences` en déclare 20. Une fois la préférence branchée, cet écart devient un simple choix de défaut : le trancher et l'aligner, mais c'est secondaire — le bug, c'est l'absence de lecture.

`Branche l objectif quotidien sur les preferences utilisateur`

---

## Tâche 7.2 — Réorganiser la section 1 en trois blocs

Corrige **deux duplications** en plus de l'alignement.

**Bloc 1 — Objectif du jour :** jauge circulaire, **Série et Record en regard**, plus le **libellé de régularité** (absent du code — aucune occurrence).

`currentStreakDays` est aujourd'hui affiché deux fois : à côté de la jauge (`StatisticsScreen.kt:156`) et dans la carte « Série » (`:188`). Le regroupement supprime le doublon.

**Bloc 2 — Ventilation :** Lecture visuelle · Écoute TTS. Déjà conforme, ne pas y toucher.

**Bloc 3 — Volumes, trois cartes :** Livres finis · **Pages lues** · **Mots parcourus**.

- Les deux dernières métriques n'existent pas ; les calculer depuis les sessions.
- **Formatage abrégé** K/M pour les grands nombres, comme le demande la cible. Un compteur de mots à sept chiffres est illisible brut.

**WPM sort du tableau de bord.** Il est déjà présent en section 4 (Détail par ouvrage), donc affiché à deux endroits — seconde duplication. La cible le place au niveau de l'ouvrage, ce qui a plus de sens : un WPM global moyenné sur des livres de densités très différentes n'est pas actionnable, alors qu'un WPM par ouvrage alimente directement le temps restant.

`Reorganise les KPI en trois blocs et supprime les doublons`

---

## Tâche 7.3 — Ventilation des sessions mixtes

**Ferme le point resté volontairement ouvert depuis le début de la conception UX.** Le code l'avait tranché implicitement (deux icônes réduites à 16 dp) ; la décision est désormais explicite.

**Ligne d'historique, `BookStatisticsScreen.kt:211-232` :**

- **À gauche** — icône de mode à taille pleine (24 dp). Session mixte : les deux icônes, sans réduction. **Pas de notion de « mode dominant »** : une session 26/24 afficherait un œil seul et mentirait. À gauche on lit *quoi*, à droite *combien*.
- **À droite** — durée totale au-dessus, ventilation par mode en dessous : `30 min` + icône · `15 min` + icône.

**Trois contraintes :**

1. **Icônes en composables `Icon()`, jamais en emoji.** La CI a une étape dédiée — `.github/workflows/ci.yml:30` exécute `scripts/check-no-emoji.sh`. Passer par `AppIcons`, conformément au système unifié Material Symbols. La réduction à 14–16 dp est ici légitime : ce sont des unités accolées à un nombre, pas des marqueurs de type de session.
2. **La somme doit être cohérente.** `30 min` + `15 min` doit toujours donner le `45 min` affiché. Arrondir le total puis répartir — jamais arrondir les trois indépendamment, sinon des lignes afficheront 30 + 15 = 46.
3. **Annonce TalkBack.** Les `contentDescription` sont à `null` (`:218,220,223,225`). Une session mixte doit s'annoncer « 45 minutes, dont 30 en lecture et 15 en écoute », pas une suite de nombres nus.

**Vérifier la mise en page sur appareil.** Le `trailingContent` d'un `ListItem` Material 3 est prévu compact. Deux lignes à droite plus `headlineContent` et `supportingContent` à gauche peuvent serrer sur écran étroit. Si ça déborde sur le V2206, déplacer la ventilation dans `supportingContent`, à côté de la plage horaire — et le signaler.

`Ventile les sessions mixtes par mode dans l historique`

---

## Tâche 7.4 — Sélecteur Semaine/Mois et total de période

Non implémenté. Le tableau de bord affiche systématiquement les 30 derniers jours.

- Sélecteur en haut à droite de la carte histogramme.
- **Total de la période** dans l'en-tête, à côté de la variation en pourcentage — absent aujourd'hui, seule la variation est affichée.
- La variation doit se recalculer sur la période sélectionnée, pas rester figée sur 30 jours. C'est le piège de cette tâche : changer la vue sans changer le calcul produirait un pourcentage faux.

`Ajoute le selecteur de periode et le total de la carte activite`

---

## Tâche 7.5 — Couverture du livre en cours

La carte « Livre en cours » affiche une icône `MenuBook` générique alors que `coverUri` est disponible dans `CurrentBookState` et transite déjà jusqu'à l'UI. Afficher la vraie miniature, avec repli sur l'icône générique si la couverture est absente.

`Affiche la couverture miniature du livre en cours`

---

## Tâche 7.6 — Consigner les écarts assumés

Trois écarts assumés vivent aujourd'hui dans des KDoc, donc invisibles depuis la cible. Le prochain audit les ressortira comme des manques — c'est exactement ce qui s'est produit sur cet écran.

À écrire dans `UX_FLOW_DESIGN.md`, § Statistiques :

- **Chapitres parcourus absents** de l'historique par ouvrage, alors que la cible les demande. Trois raisons, toutes solides : instabilité structurelle des chapitres dans les EPUB mal formés, micro-sessions TTS qui n'avancent pas l'index, et séparation `ReadingState` (position de reprise) / `ReadingSession` (activité temporelle). Écart validé, à acter dans la cible plutôt qu'à laisser en KDoc.
- **Heatmap à 5 créneaux** (6 h → 22 h), choix d'implémentation non spécifié par la cible.
- **Session mixte** : décision retenue en 7.3, qui referme le point resté ouvert depuis le début de la conception.

`Consigne les ecarts assumes des statistiques dans la cible`

---

## Tâche 7.7 — Tests

1. **Objectif quotidien** — changer la préférence recalibre la jauge sans redémarrage. Test de non-régression du réglage sans effet.
2. **Pas de doublon** — `currentStreakDays` n'apparaît qu'à un seul endroit ; le WPM n'apparaît plus dans le tableau de bord.
3. **Formatage abrégé** — 1 250 000 mots s'affiche en forme abrégée, pas brut.
4. **Cohérence des durées** — pour un jeu de sessions mixtes, la somme des durées par mode égale toujours le total affiché, arrondis compris.
5. **Période** — basculer Semaine/Mois change le total **et** la variation, pas seulement les barres.
6. **Régularité** — le libellé reflète l'assiduité réelle, il n'est pas constant.

`Ajoute les tests d affinage des statistiques`

---

## Vérifications sur appareil

| # | Avant (`ec9a0ad8`) | Après attendu |
|---|---|---|
| 1 | Régler 45 min dans les Réglages : la jauge reste sur 20 | La jauge se cale sur 45, sans redémarrage |
| 2 | Série affichée deux fois ; WPM affiché sur deux écrans | Chaque valeur à un seul endroit |
| 3 | Trois rangées de deux cartes | Trois blocs : objectif+séries, ventilation, trois volumes |
| 4 | Pages lues et mots parcourus absents | Présents, en forme abrégée |
| 5 | Session mixte : deux icônes réduites, durée totale seule | Icônes pleines à gauche, ventilation à droite ; lisible sur écran étroit |
| 6 | — | TalkBack sur une session mixte annonce le total **et** la répartition |
| 7 | 30 derniers jours imposés | Semaine/Mois change les barres, le total et la variation |
| 8 | Icône générique sur le livre en cours | Vraie couverture, repli propre si absente |

Le point 1 est le plus important : il ferme un réglage livré au lot 6 qui ne produit aucun effet.

---

## Hors périmètre explicite

Récents, Synchronisation, Galerie de thèmes et Studio, Onboarding, audit Crashlytics, lot 3f.
