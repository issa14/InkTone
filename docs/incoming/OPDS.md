Concevoir en partant de l'utilisateur (UX -> UI -> Technique) est la meilleure approche pour garantir une application robuste et agréable. Dans le cas d'un catalogue OPDS, le risque principal est de frustrer l'utilisateur avec une navigation perdue ou des téléchargements bloquants.

Voici un plan d'implémentation de bout en bout pour InkTone.

---

## Phase 1 : UX - Le Parcours Utilisateur

Avant de coder, définissons ce que l'utilisateur doit ressentir et accomplir.

1. **Le point d'entrée et le tableau de bord :**
* L'utilisateur ouvre le *drawer* et clique sur "Catalogue OPDS".
* Il n'arrive pas directement sur un flux brut, mais sur un **Tableau de bord des catalogues**. L'application doit proposer des catalogues par défaut (ex: Projet Gutenberg, Feedbooks) et offrir la possibilité d'ajouter des catalogues personnels (ex: un serveur Calibre-Web ou Komga).


2. **La navigation hiérarchique (Le syndrome du petit poucet) :**
* En cliquant sur un catalogue, l'utilisateur entre dans une arborescence (Catégories -> Auteurs -> Livres).
* **Règle d'or UX :** Le bouton "Retour" (système et interface) ne doit pas fermer l'écran OPDS, mais remonter au dossier parent. S'il est à la racine du catalogue, le bouton "Retour" le ramène au tableau de bord.


3. **L'acquisition (Téléchargement transparent) :**
* L'utilisateur voit un livre qui l'intéresse et clique sur "Télécharger".
* L'action doit être **asynchrone et non bloquante**. Il peut continuer à naviguer pendant le téléchargement.
* Une fois terminé, le livre est injecté silencieusement, et un retour visuel discret (Snackbar) confirme le succès avec un bouton "Lire maintenant".


4. **La recherche et l'authentification (Friction réduite) :**
* Si le flux supporte OpenSearch, une icône "Loupe" apparaît naturellement.
* Si un catalogue nécessite un mot de passe, l'UX ne doit pas demander les identifiants à chaque requête, mais une seule fois lors de l'ajout du catalogue.



---

## Phase 2 : UI - Traduction en Jetpack Compose

Ces choix UX déterminent les composants visuels nécessaires.

### 1. `CatalogDashboardScreen`

* **Composant principal :** `LazyColumn` ou `LazyVerticalGrid` listant les sources.
* **Action :** Un `FloatingActionButton` (FAB) pour ajouter un catalogue.
* **Dialogue :** `AddCatalogBottomSheet` contenant les champs : Nom, URL racine, Nom d'utilisateur (optionnel), Mot de passe (optionnel).

### 2. `OpdsFeedScreen` (Le cœur de l'exploration)

* **TopAppBar :**
* Titre : Dynamique (affiche le nom du dossier courant, ex: "Science-Fiction").
* Actions : Icône de recherche (visible uniquement si l'état indique `searchTemplateUrl != null`).


* **Contenu :** Un `LazyVerticalGrid` adaptatif pour optimiser l'espace.
* *Type A (Navigation) :* Une `DirectoryCard` simple (icône de dossier + Titre).
* *Type B (Livre) :* Une `BookCard` riche (Couverture asynchrone via Coil, Titre, Auteur, bouton d'icône de téléchargement).


* **États transitoires :**
* *Loading :* `CircularProgressIndicator` centré ou, idéalement, des composants *Skeleton* (Shimmer effect) pour éviter les sauts visuels.
* *Pagination :* Un indicateur de chargement en bas de la grille déclenché par un `LaunchedEffect` sur le dernier élément visible.



---

## Phase 3 : Technique - Impact sur la Clean Architecture

Maintenant que l'UI/UX est définie, voici comment la technique (Clean Architecture) doit supporter cette vision.

### 1. Module Feature (Présentation)

Le comportement du bouton "Retour" UX dicte le comportement du ViewModel. Le routeur global de l'app (Navigation Compose) ne voit que `OpdsScreen`. La navigation *interne* au catalogue est gérée par le `OpdsViewModel`.

* **Le `OpdsViewModel**` maintient deux états :
1. `catalogListState` : Pour le tableau de bord.
2. `currentFeedState` : Pour le flux en cours d'exploration.


* **La gestion du Retour :** Le ViewModel possède une `ArrayDeque<String>` (la pile d'URLs). L'interception du bouton retour système via `BackHandler` dans Compose demande au ViewModel de `goBack()`. S'il n'y a plus d'historique, l'écran se ferme.

### 2. Module Domain (Cas d'usage)

L'UX exigeant des téléchargements non bloquants, les Use Cases doivent être pensés pour des opérations en arrière-plan.

* `GetCatalogsUseCase` : Récupère la liste depuis Room.
* `BrowseOpdsFeedUseCase` : Récupère le XML, le parse, et sépare proprement les entités (`OpdsItem.Navigation` vs `OpdsItem.Book`).
* `DownloadBookUseCase` : Ne renvoie pas le livre immédiatement. Il déclenche un `WorkManager` (ou un service) qui gérera le téléchargement indépendamment du cycle de vie de l'écran.

### 3. Module Data (Infrastructure)

L'UX demandant une authentification transparente implique une gestion réseau spécifique.

* **Stockage local :** Une base Room `CatalogEntity` stockant les URLs et les identifiants (chiffrés si possible via EncryptedSharedPreferences).
* **Réseau (Retrofit) :** Un `BasicAuthInterceptor` (OkHttp). À chaque requête HTTP, l'intercepteur regarde l'URL, interroge Room de manière synchrone, et injecte le header `Authorization` si nécessaire, rendant le processus totalement invisible pour le `Domain` et l'UI.

---

## Séquence de développement recommandée

Pour éviter l'effet tunnel, implémente dans cet ordre :

1. **L'échafaudage UI :** Crée les écrans Compose avec des données mockées (fausses cartes, fausse navigation). Valide le comportement du `BackHandler` et de la pile.
2. **La persistance des catalogues :** Implémente Room et l'écran de tableau de bord. Permets l'ajout, la modification et la suppression d'URLs racines.
3. **Le parseur réseau :** Branche Retrofit/TikXML. Transforme l'URL racine d'un catalogue en un affichage réel de la première page.
4. **Le moteur de rendu OPDS :** Gère la navigation récursive (clic sur un sous-dossier -> nouvelle requête -> mise à jour de l'UI -> empilage dans l'historique).
5. **Le boss final (Le pipeline) :** Développe le `DownloadBookUseCase`, télécharge le fichier `.epub`, et connecte-le à ton système de gestion de bibliothèque InkTone.
