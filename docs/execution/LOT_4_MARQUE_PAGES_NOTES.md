# Lot 4 — Marque-pages et notes (vue globale)

**Base :** `main` à `7267b6d`. Branche : `lot-4-marque-pages-notes`. Référence cible : `UX_FLOW_DESIGN.md` § Marque-pages et notes — vue globale.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Pourquoi ce lot en premier

L'écran existe et **ment**. Il s'intitule « Marque-pages et notes » et n'affiche que des marque-pages : `GlobalBookmarksViewModel.kt:38` n'observe que `bookmarkRepository.observeAll()`. Un utilisateur qui a surligné trente passages ne les trouve nulle part. C'est plus grave qu'un écran absent, qui au moins ne promet rien.

## Deux blocages de fond, établis avant rédaction

Ils imposent un palier de données **avant** tout travail d'UI :

1. **`AnnotationRepository` n'expose pas `observeAll()`** — seulement `observeForPublication(publicationId)`. Aucun moyen de charger les annotations tous livres confondus.
2. **Aucun extrait de texte n'est stocké.** `Annotation` porte `startLocator`, `endLocator`, `color` et `content` (la note utilisateur), mais **pas le texte surligné**. `Bookmark` n'a que `title` et `note`. Or la carte cible affiche un extrait. Le reconstruire à l'affichage imposerait de rouvrir chaque EPUB pour résoudre les locators — irréaliste dans une liste défilante, et impossible si le fichier a été déplacé.

## Découpage en deux paliers poussables

| Palier | Contenu | Vérifiable seul |
|---|---|---|
| **A** | Données : `observeAll`, extrait persisté, épinglage | Oui — par tests, sans UI |
| **B** | Écran : filtres, tri, recherche, cartes, suppression | Oui — dépend de A |

**Pousser après le palier A.** Il est entièrement testable sans interface, et c'est là que se cachent les erreurs coûteuses.

---

# PALIER A — Données

## Tâche 4.1 — Observation globale des annotations

Ajouter `observeAll(): Flow<List<Annotation>>` à `AnnotationRepository`, avec la requête DAO correspondante.

**Résoudre les titres par jointure SQL, pas par cache mémoire.** Le `GlobalBookmarksViewModel` maintient aujourd'hui un cache `publicationId → title` (`:31-34,47-48`) alimenté par des requêtes secondaires. Ne pas étendre ce patron à une seconde source : le remplacer.

- Jointure `LEFT JOIN` dans le DAO, résultat encapsulé dans un POJO de relation (`AnnotationWithPublication`, `BookmarkWithPublication`). Une requête unique, atomique et réactive.
- Le `LEFT JOIN` — et non `INNER` — pour qu'un enregistrement orphelin reste visible plutôt que de disparaître silencieusement.
- **Le POJO reste dans `infrastructure/database`.** Le repository le convertit en type de domaine avant de le remonter, sinon Room fuit dans le domaine (Blueprint §12.4).
- Supprimer `titleCache` et `titleFor` du ViewModel : cette logique n'est plus de sa responsabilité.

Bénéfice au-delà du N+1 : la désynchronisation disparaît. Un livre **renommé** met aujourd'hui à jour son titre en base sans que le cache le sache — la vue globale afficherait l'ancien titre jusqu'au redémarrage. (La suppression, elle, est déjà couverte par le `CASCADE` posé au lot 2b.)

**Point à trancher — les deux sources.** L'écran fusionne marque-pages *et* annotations, donc deux requêtes. La jointure supprime le N+1 **dans** chaque source, mais le tri **entre** les deux retombe dans le ViewModel. Deux options :

- **Vue `UNION`** renvoyant une ligne unifiée (type, extrait, note, titre, chapitre, date, épinglage). Le tri et la recherche redeviennent entièrement SQL, et une migration ultérieure vers `PagingSource` se fait sur une source unique — c'est l'option que je recommande.
- **Fusion en mémoire** des deux flux, avec tri après fusion. Plus simple, mais reporte le plafond de volumétrie et complique le `Paging3` ultérieur.

Choisir explicitement et consigner. Ne pas laisser le tri croisé arriver par défaut dans le ViewModel.

`Ajoute l observation globale des annotations par jointure`

---

## Tâche 4.2 — Persister l'extrait de texte

**Le point structurant du lot.** Sans extrait stocké, la carte cible est impossible à rendre.

- Ajouter un champ d'extrait à `Annotation` et à `Bookmark`, renseigné **à la création**, dans le lecteur, où le texte est disponible.
- **Migration Room explicite**, jamais de `fallbackToDestructiveMigration`.
- **Définir le comportement pour l'historique existant** : les annotations et marque-pages déjà en base n'ont pas d'extrait. Champ nullable, et l'UI affiche alors le titre du chapitre seul plutôt qu'un blanc. Ne pas tenter de reconstruire rétroactivement.
- **Borner la longueur** à la création (quelques centaines de caractères), sinon un surlignage de plusieurs pages gonfle la base et la carte.

**Attention à l'espace de coordonnées** — c'est le piège déjà rencontré au lot 3a. L'extrait doit être capturé depuis le texte réellement rendu, en cohérence avec les offsets locaux du `ChapterTextMeasurer`, pas reconstruit depuis `Sentence.startOffset` qui pointe dans la ressource EPUB d'origine.

`Persiste l extrait de texte des annotations et marque-pages`

---

## Tâche 4.3 — Épinglage

La cible affiche une étoile sur les éléments épinglés. Rien de tel n'existe dans les deux modèles.

Ajouter un champ d'épinglage à `Annotation` et `Bookmark`, avec sa migration, et la remontée des éléments épinglés en tête de liste. Même patron que l'épinglage des publications livré au lot 2b — s'en inspirer plutôt que d'inventer une seconde convention.

`Ajoute l epinglage des marque-pages et annotations`

---

## Tâche 4.4 — Recherche et tri au niveau de la requête

Un `Flow<List<Annotation>>` global charge toute la base en mémoire. Fluide à quelques dizaines d'éléments, source de saccades au filtrage à la frappe si l'utilisateur accumule des milliers de surlignages.

**Ne pas introduire `Paging3` dans ce lot** — la complexité serait disproportionnée pour la volumétrie réelle d'aujourd'hui. Mais **isoler la recherche et le tri au niveau de la requête** (DAO ou repository), jamais dans le composable ni dans un `filter` sur la liste d'état.

L'interface reste réactive immédiatement, et la bascule ultérieure vers un `PagingSource` devient un remplacement d'implémentation derrière une signature inchangée, pas une refonte de l'écran. C'est le même principe que le contrat `VirtualPagination` du lot 3a : la frontière est posée avant d'en avoir besoin.

La recherche porte sur **l'extrait, la note et le titre d'ouvrage** — donc sur des colonnes de la jointure de 4.1, ce qui suppose l'option `UNION` si le tri doit rester entièrement SQL.

`Isole la recherche et le tri au niveau de la requete`

---

## Tâche 4.5 — Tests du palier A

1. `observeAll` renvoie les annotations de **plusieurs** publications, triées de façon déterministe.
2. Aucun N+1 : **une seule** requête quel que soit le nombre d'éléments — la jointure le garantit, le test le verrouille.
2 bis. **Désynchronisation** : renommer une publication met à jour le titre affiché sans redémarrage. C'est le cas que le cache mémoire ne pouvait pas couvrir.
2 ter. Recherche et tri s'exécutent au niveau requête : filtrer ne reconstruit pas la liste complète en mémoire.
3. Migration : une base peuplée à l'ancienne version s'ouvre sans perte ; les enregistrements existants ont un extrait nul et restent lisibles.
4. Extrait borné : un surlignage très long est tronqué à la création, pas à l'affichage.
5. Épinglage : persistant, et les éléments épinglés remontent en tête.

`Ajoute les tests du modele de marque-pages et annotations`

### Vérifications device — palier A

Peu d'observable à ce stade, et c'est normal. Deux points seulement :

| # | Attendu |
|---|---|
| A1 | Installer par-dessus une version antérieure avec des données : aucun crash, aucune perte de marque-pages ni d'annotations |
| A2 | Créer un surlignage et une note dans le lecteur, puis vérifier en base que l'extrait est bien renseigné |

---

# PALIER B — Écran

## Tâche 4.6 — Reconstruire l'écran

`GlobalBookmarksScreen.kt`. Chaque ligne ci-dessous est un écart constaté, pas une supposition.

| Cible | État actuel |
|---|---|
| Titre « Marque-pages et notes » | « Signets » (`InkToneNavHost.kt`) |
| Recherche **repliable** dans la barre | `TextField` toujours déployé dans le corps |
| Recherche par titre **ou contenu** | Titre d'ouvrage uniquement |
| Icône de tri (chronologique / alphabétique) | Absente, aucun champ de tri dans l'état |
| Puces de filtre Tous / Signets / Surlignages / Notes | Absentes |
| Cartes : extrait + note en italique + `Titre · Chapitre X · date` + étoile épinglée | Rangée plate : icône + titre + « Chapitre N » |
| Swipe-to-dismiss avec **confirmation** | Bouton poubelle, suppression **immédiate sans confirmation** |
| Clic → ouvre le lecteur au passage, avec **flash temporaire** | Navigation OK, pas de flash |

**Trois précisions :**

- **Les puces de filtre distinguent Surlignages et Notes** : un surlignage sans note et un surlignage annoté sont deux `Annotation`, séparées par la présence de `content`. La distinction se fait sur ce champ, pas sur un type.
- **La suppression doit être confirmée.** C'est aujourd'hui la seule action destructive de l'app sans confirmation — le lot 2b en a posé une pour les publications.
- **Le flash à l'arrivée** est un surlignage temporaire du passage dans le lecteur. Réutiliser le mécanisme de surlignage existant plutôt qu'en créer un second. **Son déclenchement fait l'objet de la tâche 4.7** — ce n'est pas un détail d'animation.

`Reconstruit l ecran marque-pages et notes`

---

## Tâche 4.7 — Flash différé à la fin de la mise en page

**Course d'exécution réelle, à traiter avant de coder le flash.**

Émettre le surlignage dès la réception des arguments de navigation le fait cibler des coordonnées qui n'existent pas encore : depuis le lot 3a, la mesure du chapitre est **asynchrone** (`Dispatchers.Default`, première page prioritaire puis reste en arrière-plan). Au moment où le lecteur reçoit la destination, `ChapterTextMeasurer` n'a pas fini de produire les offsets locaux de la page. Le flash tomberait à côté, ou nulle part — de façon intermittente, et d'autant plus souvent que le chapitre est long.

**À faire :** stocker l'intention sous forme d'état différé (`PendingHighlightTarget`), consommé par l'UI **uniquement** après confirmation de fin de mise en page du chapitre visé.

Trois contraintes, toutes issues d'invariants déjà posés :

1. **La cible est un `Locator`, jamais un index de page.** Un index de page ne vaut que pour un couple (style, viewport) donné — c'est l'invariant du lot 3b, et une rotation pendant l'ouverture le rendrait faux.
2. **Consommation unique.** L'état est vidé après déclenchement, sinon le flash rejoue à chaque recomposition ou changement de style.
3. **Sortie de secours.** Si la mise en page n'aboutit pas (chapitre en erreur, livre refermé), la cible ne doit pas rester en attente indéfiniment. Prévoir un abandon explicite plutôt qu'un état orphelin.

`Diffère le flash de navigation à la fin de la mise en page`

---

## Tâche 4.8 — Tests du palier B

1. Les puces filtrent réellement : « Surlignages » masque les marque-pages et les notes.
2. La recherche porte sur le contenu **et** le titre — un mot présent seulement dans un extrait remonte l'élément.
3. Les deux tris réordonnent effectivement.
4. Supprimer demande confirmation ; refuser n'appelle pas le use case, accepter l'appelle une fois.
5. Ouvrir un élément navigue au bon passage et déclenche le flash **après** la fin de mise en page, jamais avant.
5 bis. Le flash ne se déclenche **qu'une fois** : changer la taille de police ensuite ne le rejoue pas.
5 ter. Si la mise en page échoue, la cible en attente est abandonnée et non conservée.
6. Non-régression : un élément sans extrait (donnée d'avant migration) s'affiche sans blanc ni crash.

`Ajoute les tests de l ecran marque-pages et notes`

---

## Tâche 4.9 — Consigner

Dans `UX_FLOW_DESIGN.md`, § Marque-pages et notes : consigner que l'extrait n'est disponible que pour les éléments créés après ce lot, l'historique affichant le chapitre seul.

`Consigne l etat de la vue globale dans la cible`

### Vérifications device — palier B

| # | Avant (`7267b6d`) | Après attendu |
|---|---|---|
| B1 | Écran « Signets », marque-pages seuls | « Marque-pages et notes » ; surlignages et notes présents |
| B2 | — | Les 4 puces filtrent réellement le contenu affiché |
| B3 | Recherche sur le titre d'ouvrage seulement | Un mot d'un extrait surligné remonte l'élément |
| B4 | Aucun tri | Chronologique et alphabétique réordonnent |
| B5 | Poubelle, suppression immédiate | Balayage puis confirmation ; annuler ne supprime rien |
| B6 | Clic ouvre le lecteur, sans repère | Le passage est brièvement mis en évidence à l'arrivée — **sur un chapitre long**, là où la mesure asynchrone prend le plus de temps |
| B6 bis | — | Renommer un livre : le titre change dans la vue globale sans redémarrer l'app |
| B7 | — | Les éléments créés **avant** ce lot s'affichent proprement, sans extrait |
| B8 | — | Épingler un élément le remonte en tête ; l'état survit à un redémarrage |

Le point B7 est celui que je passerais en premier : c'est le seul qui teste la migration sur des données réelles plutôt que sur une base neuve.

---

## Hors périmètre explicite

Retours d'import (résumé de fin de lot et retour par fichier) → lot suivant.

Récents, Synchronisation, Galerie de thèmes et Studio, Onboarding, cartes manquantes des Réglages, sections manquantes des Statistiques, audit Crashlytics, lot 3f.
