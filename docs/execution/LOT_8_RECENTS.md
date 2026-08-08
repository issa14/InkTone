# Lot 8 — Écran Récents

**Base :** `main` à `51eb0fb5`. Branche : `lot-8-recents`. Référence cible : `UX_FLOW_DESIGN.md` § Récents.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Contexte

Écran **entièrement absent** : aucune `RecentsRoute` dans `Routes.kt`, aucun item dans le drawer. C'est une des quatre destinations masquées au lot 1 en application de la décision « aucune destination affichée sans écran derrière ». Ce lot en réactive une.

Lot court et sans blocage : toutes les briques existent.

## Briques réutilisables — à réutiliser, pas à recréer

- **`PublicationListRow`** (`LibraryScreen.kt:578`) est déjà `internal` et partagé entre `LibraryScreen` et `LibraryDetailScreen`. Il porte le cœur, le menu 3-points et la barre de progression du lot 2b. **L'utiliser tel quel** : c'est ce qui garantit que Récents bénéficie automatiquement des corrections futures.
- `LibrarySortOrder.RECENTLY_OPENED` et `progressMap` existent depuis l'audit initial.
- `LibraryDetailScreen` est le modèle d'écran secondaire (flèche de retour à la place du hamburger), mais **ne pas l'étendre** : voir 8.2.

---

## Tâche 8.1 — Route et item de drawer

- Ajouter `RecentsRoute` à `Routes.kt` et sa destination au `NavHost`.
- Ajouter l'item **« Récents »** au drawer, en **première position** des destinations de navigation, avant Bibliothèque — c'est sa place dans la cible.
- Lui donner une icône correcte. Attention : l'item historique utilisait `AppIcons.Loading` (un sablier), corrigé au lot 1 par suppression de l'item. Ne pas réintroduire ce défaut.

**Vérifier l'item actif :** le drawer marque la destination courante. Récents devient une destination à part entière, pas un filtre de la Bibliothèque — c'est précisément l'erreur de l'item mort supprimé au lot 1, dont le `onClick` posait un filtre au lieu de naviguer.

`Ajoute la route et l entree de drawer de l ecran Recents`

---

## Tâche 8.2 — Écran

**Ne pas étendre `LibraryDetailScreen`.** Il porte une topbar à deux niveaux (`SÉRIES` + nom) et les icônes recherche et filtre. La cible de Récents demande l'inverse : topbar simplifiée, **flèche de retour et titre « Récents » seuls**, sans recherche ni filtre. Ajouter une troisième catégorie à `LibraryDetailCategory` obligerait à conditionner la topbar sur la catégorie — le composant deviendrait un aiguillage plutôt qu'un écran.

Écran dédié, réutilisant `PublicationListRow`.

**Règles de contenu, toutes issues de la cible :**

| Règle | Détail |
|---|---|
| Progression minimale | Seuls les livres à **≥ 1 %** de progression. Un livre ouvert puis refermé aussitôt n'est pas « récent » |
| Tri | Par récence d'ouverture (`lastOpened`), le plus récent en premier |
| Limite | **30 éléments** maximum |
| Rendu | **Vue Liste forcée**, quel que soit le réglage de disposition de la Bibliothèque |

**Point à trancher — les livres terminés.** La cible ne le dit pas. Un livre à 100 % satisfait le seuil de 1 % et remonterait donc en tête après lecture de la dernière page. Deux lectures possibles : « en cours de lecture » (exclure les terminés) ou « récemment consultés » (les garder). Je penche pour **les garder** — l'utilisateur peut vouloir y revenir, et les exclure ferait disparaître un livre de la liste au moment précis où il vient de le finir, ce qui surprend. À confirmer sur appareil (point 5) et à consigner.

**État vide — sans bouton d'action.** C'est explicite dans la cible, et c'est délibéré : un utilisateur sans lecture récente a déjà la Bibliothèque pour importer. Un CTA ici dupliquerait celui de l'état vide de la Bibliothèque. Texte seul.

`Ajoute l ecran Recents`

---

## Tâche 8.3 — Tests

1. Un livre à 0 % **n'apparaît pas** ; à 1 % il apparaît.
2. L'ordre suit `lastOpened` décroissant, pas la date d'import ni le titre.
3. Avec 35 livres éligibles, **30** sont affichés — les 30 plus récents.
4. Le rendu est en Liste même quand la Bibliothèque est réglée en mosaïque.
5. L'état vide n'expose **aucun** bouton d'action.
6. L'item de drawer **navigue** ; il ne pose pas un filtre sur la Bibliothèque. Test de non-régression du défaut supprimé au lot 1.
7. Ouvrir un livre depuis Récents met à jour `lastOpened` et le remonte en tête au retour.

`Ajoute les tests de l ecran Recents`

---

## Tâche 8.4 — Consigner

Dans `UX_FLOW_DESIGN.md` : mettre à jour la note d'état du drawer posée au lot 1 — Récents n'est plus masqué. Consigner le sort des livres terminés (8.2).

`Consigne l activation de l ecran Recents dans la cible`

---

## Vérifications sur appareil

| # | Avant (`51eb0fb5`) | Après attendu |
|---|---|---|
| 1 | Aucun item Récents dans le drawer | Présent, en tête des destinations, avec une icône correcte — pas un sablier |
| 2 | — | Le tap **ouvre un écran** ; il ne filtre pas la Bibliothèque |
| 3 | — | Topbar : flèche de retour et « Récents » seuls, sans recherche ni filtre |
| 4 | — | Les livres jamais ouverts sont absents ; l'ordre suit la dernière ouverture |
| 5 | — | Un livre terminé à 100 % : présent ou absent conformément à ce qui a été décidé |
| 6 | Disposition mosaïque en Bibliothèque | Récents reste en Liste |
| 7 | — | Sans lecture récente : message seul, aucun bouton |
| 8 | — | Lire un livre, revenir : il est passé en tête de liste |

Le point 8 est le seul qui teste la chaîne complète plutôt que l'affichage.

---

## Hors périmètre explicite

Galerie de thèmes et Studio, Onboarding, audit Crashlytics, lot 3f.

**Synchronisation** — la dernière destination masquée du drawer, en attente d'une décision de périmètre V1 avant tout plan.
