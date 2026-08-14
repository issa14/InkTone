# Lot 13 — Catalogues OPDS (Volet 1, ADR-023)

**Base :** `main`. Références : `docs/incoming/OPDS.md` (proposition UX/UI/technique
d'origine), `ADR-023` (périmètre OPDS réintégré et borné), `ADR-012` (MVI),
`LOT_11_SYNCHRONISATION.md` et `LOT_12_SUPPORT_PDF.md` (précédents directement
comparables : intégration réseau chiffrée pour le premier, réintégration d'un
périmètre différé pour le second).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare aucun palier clos : il livre, signale ce qu'il n'a pas pu
vérifier, la clôture se fait sur appareil.

## Écarts délibérés par rapport à `docs/incoming/OPDS.md`

`OPDS.md` est une proposition externe, écrite sans connaissance du code réel.
Trois points y sont corrigés pour rester cohérents avec l'existant — à ne pas
« corriger en sens inverse » en cours d'exécution :

1. **Pas de Retrofit ni TikXML.** Le projet n'a jamais introduit Retrofit
   (`infrastructure/sync` construit son client à la main sur `OkHttpClient` brut,
   commentaire explicite : « la bibliothèque officielle n'est volontairement
   pas embarquée »). OPDS est du XML/Atom : `android.util.Xml.newPullParser()`
   (SDK Android, zéro dépendance nouvelle) suffit et suit le même principe de
   sobriété. Un flux `application/opds+json` sans variante Atom est rejeté
   (ADR-023) plutôt que d'ajouter un second parseur JSON pour un besoin non
   confirmé.
2. **Pas de `BasicAuthInterceptor` global.** Le seul précédent réseau du
   projet (`GoogleDriveSyncProvider`, Lot 11) injecte explicitement le token
   au point d'appel (`TokenProvider.getValidToken()`), pas via un
   `OkHttp Interceptor` global — décision déjà prise pour garder le Domain et
   l'UI ignorants du détail, mais aussi pour éviter qu'un intercepteur
   global attache par erreur des identifiants au mauvais hôte. OPDS multiplie
   les hôtes (plusieurs catalogues actifs simultanément, contrairement à Sync
   qui n'a qu'un fournisseur actif à la fois) : le risque de fuite de
   Basic Auth vers le mauvais serveur est réel avec un intercepteur non borné
   par hôte. Le repository OPDS connaît toujours le `catalogId` en cours
   (c'est le point d'entrée de toute requête) : il résout les identifiants et
   pose l'en-tête `Authorization` lui-même, requête par requête — même
   pattern que Sync, pas d'intercepteur.
3. **Pas de « deux états » `catalogListState` / `currentFeedState` au sens de
   deux `StateFlow` distincts.** ADR-012 (MVI) impose un état unique
   immuable par écran. `OpdsUiState` est un type scellé unique
   (`Dashboard(catalogs: ...)` / `Feed(breadcrumb: ..., items: ..., ...)`),
   porté par un seul `StateFlow<OpdsUiState>` — le ViewModel garde une seule
   source de vérité, jamais deux flux qui peuvent diverger.

## Décisions actées

1. **Deux nouveaux modules : `infrastructure:opds` et `feature:opds`.**
   Ajoutés à `settings.gradle.kts` et à la table Blueprint §5.2 **dans le
   même commit** que leur premier fichier (règle Blueprint, ligne 468).
   `infrastructure:opds` applique `inktone.android.library` (comme `sync`,
   `parser`) ; `feature:opds` applique `inktone.android.feature` (Compose +
   Hilt). Matrice `checkArchitectureRules` inchangée : `infrastructure:opds`
   → `:domain` seul, `feature:opds` → `:domain` + `:core:*` seul.
2. **Domaine.** `domain/model/OpdsCatalog.kt` (id, name, rootUrl, hasCredentials,
   searchTemplateUrl nullable), `domain/model/OpdsItem.kt` scellé
   (`Navigation(title, href)` / `Book(title, authors, coverUrl, acquisitionHref,
   mimeType)`), `domain/model/OpdsFeed.kt` (title, items, nextPageUrl nullable,
   searchTemplateUrl nullable). `domain/service/OpdsFailureReason.kt` scellé
   fermé (`UNAUTHORIZED, NOT_FOUND, MALFORMED_FEED, UNSUPPORTED_FORMAT,
   NON_DOWNLOADABLE_ACQUISITION, NETWORK, UNKNOWN`) — même discipline que
   `SyncFailureReason` (Lot 11), pas de `else` fourre-tout. Use cases :
   `GetCatalogsUseCase`, `AddCatalogUseCase`, `RemoveCatalogUseCase`,
   `BrowseOpdsFeedUseCase(url, catalogId)`, `SearchOpdsFeedUseCase(query,
   template)`, `DownloadOpdsBookUseCase` (renvoie un identifiant de travail
   `WorkManager`, jamais le fichier — cohérent avec l'UX non bloquante).
3. **La navigation OPDS ne touche jamais `Locator`.** Un `OpdsItem.Navigation`
   n'est pas une position de lecture ; la pile de navigation OPDS
   (`ArrayDeque<String>` d'URLs, dans `OpdsViewModel`) est un état de
   présentation pur, jamais persisté comme reprise de lecture. Seul le
   livre une fois téléchargé et importé obtient un `Locator` normal, via le
   pipeline d'import existant.
4. **Téléchargement = réutilisation du pipeline d'import EPUB existant, pas
   un second chemin.** `OpdsDownloadWorker` (`infrastructure:worker`, même
   famille que `ImportWorker`) télécharge le flux d'octets vers le
   répertoire d'import SAF-compatible, puis délègue à l'use case d'import
   EPUB déjà en place (celui qui détecte le DRM — K7 — et normalise les
   hrefs percent-encodés — K6). Aucune détection DRM ni normalisation de
   href n'est dupliquée dans `infrastructure:opds`.
5. **Persistance.** `CatalogEntity` (Room, `infrastructure:database`) :
   id, name, rootUrl, searchTemplateUrl, createdAt — jamais les identifiants
   en clair. Migration Room dédiée + test `MigrationTestHelper` dans le même
   commit (règle non négociable, CLAUDE.md). Identifiants Basic Auth
   (username/password) dans `SecureOpdsCredentialsStore`
   (`androidx.security.crypto`, EncryptedSharedPreferences keyée par
   `catalogId`) — même famille que `SecureAuthStateStore` (Lot 11), fichier
   distinct (un catalogue OPDS n'est pas un compte de sync).
6. **`WAL`, pas de `fallbackToDestructiveMigration`** (K1, K4) — la nouvelle
   table `catalogs` suit la même politique de migration que le reste du
   schéma.
7. **Aucun `Retrofit`/`TikXml`** — voir écarts délibérés ci-dessus.
8. **Drawer.** Réactivation de l'item « Catalogues OPDS » (b4,
   `LibraryScreen.kt`) selon la règle actée au Lot 1 : jamais affiché sans
   écran fonctionnel derrière — donc seulement au Palier 1 de ce lot, une
   fois `CatalogDashboardScreen` réellement navigable (mock ou réel).
9. **Aucun emoji** dans le code de production (K12) ; icônes via `AppIcons`
   — prévoir `AppIcons.Catalog`/`AppIcons.Download` si absents, pas d'ajout
   ad hoc hors de ce registre.
10. **HTTP en clair — autorisé, borné au strict nécessaire.** Aucun
    `network_security_config.xml` n'existe aujourd'hui : le cleartext est
    bloqué globalement (comportement par défaut Android, targetSdk ≥ 28).
    Décision (arbitrage Issa) : autoriser le cleartext pour les catalogues
    personnels, pas pour les catalogues par défaut. **Nuance technique
    importante, à ne pas survoler en exécution :** l'API Android
    `NetworkSecurityConfig` est **statique** — pas d'API publique pour
    enregistrer un domaine cleartext à l'exécution. Une vraie liste
    blanche par domaine ajouté dynamiquement par l'utilisateur n'est donc
    **pas implémentable telle quelle**. Le garde-fou réel devient :
    `cleartextTrafficPermitted="true"` en `base-config` (cleartext permis
    app-large, comme c'était déjà implicitement le cas pour toute requête
    HTTP explicite avant Android 9), combiné à deux garde-fous
    applicatifs, pas réseau : (a) les catalogues par défaut (Gutenberg,
    Feedbooks) sont codés en dur en HTTPS et non éditables vers `http://`
    depuis l'UI ; (b) `AddCatalogBottomSheet` affiche un avertissement
    explicite, non désactivable, si l'URL saisie est `http://` (« Ce
    catalogue n'est pas chiffré — à réserver à un serveur de confiance sur
    votre réseau local »). Aucune tentative de contournement TLS (trust-all,
    certificat auto-signé accepté silencieusement) : un catalogue en
    `https://` avec certificat invalide échoue normalement, ce n'est pas la
    même chose que `http://` assumé.
11. **Import d'un fichier téléchargé — pas une réutilisation brute de
    `ImportPublicationUseCase`.** Vérifié dans le code réel : ce use case
    attend une URI SAF `content://` issue d'un picker utilisateur
    (`ACTION_OPEN_DOCUMENT`) et appelle
    `fileStorageService.persistReadPermission` (`ContentResolver
    .takePersistableUriPermission`) dessus — API valide seulement pour une
    URI de picker, pas pour un fichier que l'app vient d'écrire
    elle-même. « Réutiliser le pipeline d'import existant » (décision
    actée 4 ci-dessus) veut dire concrètement : le fichier téléchargé est
    écrit dans le stockage privé de l'app (`getExternalFilesDir`, pas
    `MANAGE_EXTERNAL_STORAGE`, K5 respecté — un fichier privé à l'app n'est
    pas un accès élargi au stockage), puis exposé en `content://` via un
    `FileProvider` déjà déclarable dans `app/AndroidManifest.xml`.
    `ImportPublicationUseCase` doit accepter ce cas sans détourner
    `persistReadPermission` : soit l'appel devient un no-op gracieux quand
    l'URI est déjà possédée par l'app (le provider est celui de l'app,
    pas un document externe), soit une variante d'entrée dédiée est
    ajoutée. Tâche de Palier 3, pas un détail d'implémentation à découvrir
    en cours de route.
12. **Résolution des hrefs OPDS relatifs — mécanisme dédié.**
    `JsoupChapterParser.resolveHref()` (K6) est spécifique à la résolution
    de chemins internes à un ZIP EPUB (pas d'URL absolue). `OpdsFeedParser`
    a besoin de sa propre résolution via `java.net.URI.resolve(base, href)`
    contre l'URL du flux Atom couramment consulté — aucun code existant à
    réutiliser ici, ce n'est pas une extension de K6.
13. **Couvertures réseau via Coil — chargement à construire, pas déjà
    câblé.** Le seul `Fetcher` Coil existant (`EpubImageFetcher`,
    `feature/reader`) lit des ressources internes à un EPUB, sans réseau.
    Charger une couverture depuis un catalogue protégé par Basic Auth
    demande un composant dédié (`feature:opds`, ex. un `Interceptor` Coil
    scoped ou un header `Authorization` construit par entrée de liste,
    résolu via `SecureOpdsCredentialsStore` comme `OpdsHttpClient` — même
    logique d'injection par hôte, pas un intercepteur global unique).
14. **Garde-fou anti-boucle de pagination.** `BrowseOpdsFeedUseCase`
    retient les URLs déjà visitées dans la session de navigation en cours ;
    un `nextPageUrl` qui reboucle sur une URL déjà vue interrompt la
    pagination automatique plutôt que de boucler indéfiniment.

## Paliers

### Palier 0 — Gouvernance (préalable, bloquant)

0.1. Faire accepter `ADR-023` (Status Proposed → Accepted) — c'est un choix de
     produit (réintégrer un périmètre explicitement différé), pas une décision
     que Claude Code tranche seul.
0.2. Corriger `docs/execution/PLAN_ACTION_INKTONE_TOP_TIER.md` (ligne ~832) :
     retirer OPDS de la table « Ce qu'on ne fait pas pour la v1 », renvoyer
     vers ADR-023.
0.3. Corriger `docs/execution/UX_FLOW_DESIGN.md` : les mentions « placeholder
     v1.x » / « différé volontairement » pour Catalogues OPDS (b4) et la
     référence erronée à un ADR inexistant (ligne ~847) — renvoyer vers
     ADR-023, marquer b4 comme conçu par ce Lot.
0.4. Ajouter `infrastructure:opds` et `feature:opds` à `settings.gradle.kts`
     et à la table Blueprint §5.2, dans le commit qui crée le premier
     fichier de chacun (pas avant, pas de module vide committé seul).

### Palier 1 — Échafaudage UI + persistance des catalogues

Correspond à « L'échafaudage UI » + « La persistance des catalogues » de
`OPDS.md`, fusionnés : un mock sans persistance serait jeté immédiatement.

1.1. `CatalogEntity` + `CatalogDao` (`infrastructure:database`), migration
     Room + test `MigrationTestHelper`.
1.2. `SecureOpdsCredentialsStore` (`infrastructure:opds`), chiffré, keyé par
     `catalogId`.
1.3. Contrats domaine explicites (`domain/repository/OpdsCatalogRepository.kt`,
     `domain/service/OpdsCredentialsStore.kt`) — fichiers à créer avant
     leurs implémentations, pas déduits implicitement des use cases.
     `RoomOpdsCatalogRepository` (`data/`) implémente le premier.
1.4. `CatalogDashboardScreen` (`feature:opds`) : `LazyVerticalGrid`, FAB,
     `AddCatalogBottomSheet` (nom, URL racine, identifiants optionnels).
     Deux catalogues par défaut proposés (Gutenberg, Feedbooks) en
     pré-remplissage du formulaire d'ajout, pas en lignes non supprimables
     imposées. Avertissement visible et non désactivable si l'URL saisie
     est `http://` (décision actée 10).
1.5. `OpdsViewModel` : `OpdsUiState` scellé (`Dashboard` / `Feed`), un seul
     `StateFlow`. `BackHandler` Compose relié à `goBack()`.
1.6. Wiring navigation réel : `:feature:opds` ajouté comme dépendance du
     graphe de navigation (`:app`), route `OpdsRoute` déclarée — pas
     seulement « le drawer pointe vers l'écran ». Réactivation du drawer
     (b4) → `CatalogDashboardScreen`.
1.7. Accessibilité : `contentDescription` sur `DirectoryCard`/`BookCard`/FAB,
     vérifiée TalkBack sur device au Palier 4 (convention déjà établie du
     projet pour tout nouvel écran Compose).
1.8. Tests : `CatalogDaoTest` (migration + CRUD), `OpdsViewModelTest`
     (transition Dashboard↔Feed, pile de retour, fermeture d'écran quand la
     pile est vide) sur données mockées — pas de réseau à ce palier.

### Palier 2 — Client réseau + parseur OPDS/Atom

2.1. `network_security_config.xml` (`app/`) : `base-config
     cleartextTrafficPermitted="true"`, référencé depuis
     `AndroidManifest.xml` — décision actée 10, avec le commentaire du
     pourquoi (pas de liste blanche par domaine possible à l'exécution)
     directement dans le fichier XML pour qu'un futur lecteur ne le
     reprenne pas pour un oubli.
2.2. `OpdsFeedParser` (`infrastructure:opds`, `XmlPullParser`) : entrées Atom
     → `OpdsFeed`. Distinction lien de navigation
     (`rel="subsection"`/`collection`) vs acquisition
     (`rel` commençant par `http://opds-spec.org/acquisition`). Résolution
     des hrefs relatifs via `URI.resolve` contre l'URL du flux consulté
     (décision actée 12 — pas de réutilisation de `JsoupChapterParser`).
     Feed malformé → `OpdsFailureReason.MALFORMED_FEED`, jamais un feed
     vide silencieux.
2.3. `OpdsHttpClient` (`infrastructure:opds`, `OkHttpClient` injecté via
     Hilt, même `SyncNetworkModule`-like singleton) : résout les identifiants
     via `SecureOpdsCredentialsStore` par `catalogId`, pose l'en-tête
     `Authorization: Basic` requête par requête (pas d'intercepteur, voir
     écarts délibérés §2).
2.4. `BrowseOpdsFeedUseCase` branché sur le vrai réseau ; `OpdsViewModel`
     bascule du mock au flux réel. Garde-fou anti-boucle de pagination
     (décision actée 14) : URLs déjà visitées retenues pour la session de
     navigation en cours.
2.5. Chargeur de couvertures réseau (`feature:opds`, décision actée 13) :
     composant Coil dédié résolvant l'auth par catalogue — pas de requête
     de couverture sans en-tête sur un catalogue protégé.
2.6. Pagination : bouton/`LaunchedEffect` sur dernier élément visible,
     `nextPageUrl` du feed.
2.7. Recherche OpenSearch : icône loupe visible seulement si
     `searchTemplateUrl != null` (état, pas une supposition statique).
     Substitution `{searchTerms}` avec encodage URL de la requête
     utilisateur (`URLEncoder`), pas une concaténation brute.
2.8. Tests : `OpdsFeedParserTest` (fixtures XML réelles — Gutenberg,
     Feedbooks, Calibre-Web si échantillon disponible ; cas malformé,
     cas sans lien d'acquisition direct, hrefs relatifs), `OpdsHttpClientTest`
     (injection Basic Auth conditionnelle, requête sans catalogId sans
     credentials stockés → pas d'en-tête), test de non-régression sur la
     boucle de pagination (fixture qui rebouclerait sans le garde-fou).

### Palier 3 — Téléchargement et injection bibliothèque

3.1. `DownloadOpdsBookUseCase` : vérifie `mimeType`/`acquisitionHref` avant
     de lancer quoi que ce soit — lien indirect ou MIME non-EPUB →
     `OpdsFailureReason.NON_DOWNLOADABLE_ACQUISITION`, remonté à l'UI
     (pas de tentative de téléchargement vouée à l'échec).
3.2. `OpdsDownloadWorker` (`infrastructure:worker`) : téléchargement en
     arrière-plan vers le stockage privé de l'app
     (`getExternalFilesDir`), exposé en `content://` via un `FileProvider`
     app-scopé déclaré dans `app/AndroidManifest.xml`. Adaptation de
     `ImportPublicationUseCase` pour accepter cette URI sans détourner
     `persistReadPermission` (no-op gracieux si l'autorité du provider est
     celle de l'app — décision actée 11, à vérifier par un test dédié
     plutôt que supposée). Fichier téléchargé purgé du stockage privé une
     fois l'import terminé (succès ou échec), pas laissé orphelin.
3.3. Annulation : le téléchargement peut être annulé depuis l'UI
     (`WorkManager.cancelWorkById`), fichier partiel nettoyé — pas de
     tentative d'import sur un téléchargement interrompu.
3.4. Snackbar « Lire maintenant » sur succès (canal d'effet dédié MVI, pas
     un `LaunchedEffect` ad hoc sur l'état).
3.5. Tests : `OpdsDownloadWorkerTest` (succès, échec réseau, annulation,
     acquisition non-EPUB rejetée avant tout appel réseau), test dédié de
     l'adaptation `persistReadPermission` sur une URI `FileProvider`
     app-scopée, test d'intégration bout en bout jusqu'à l'apparition du
     livre dans `PublicationDao` (pas de mock du pipeline d'import —
     cohérent avec la préférence déjà actée du projet pour les tests
     d'intégration réels plutôt que mockés). Cas déjà en bibliothèque
     (même hash SHA-256, détection existante de `ImportPublicationUseCase`) :
     le téléchargement aboutit normalement (bande passante dépensée, pas
     évitable sans requête réseau préalable), mais aucune entrée dupliquée
     n'apparaît en bibliothèque — vérifié, pas supposé.

### Palier 4 — Durcissement et cas limites

4.1. Flux `application/opds+json` sans variante Atom → message clair,
     pas de crash de parsing.
4.2. Catalogue nécessitant une auth mais identifiants absents/expirés →
     `UNAUTHORIZED` remonté distinctement de `NETWORK` (pas un message
     générique).
4.3. Suppression d'un catalogue : purge `CatalogEntity` **et**
     `SecureOpdsCredentialsStore` dans la même opération (jamais
     d'identifiants orphelins en clair-chiffré sans propriétaire).
4.4. Vérification sur device réel (contrat point 4) : Gutenberg et
     Feedbooks (pas de compte requis), plus un catalogue Basic Auth si un
     serveur de test (Calibre-Web local, par ex.) est disponible — sinon
     écart déclaré explicitement, pas simulé.
4.5. `./gradlew build` vert, `checkArchitectureRules` sur `infrastructure:opds`
     et `feature:opds`, `scripts/check-no-emoji.sh` inclus.

## Critères de sortie du Lot

- [x] ADR-023 Accepted (2026-08-13).
- [ ] `infrastructure:opds`, `feature:opds` dans `settings.gradle.kts` et
      Blueprint §5.2.
- [ ] Migration Room `catalogs` + test `MigrationTestHelper`.
- [ ] Drawer b4 navigable, plus de placeholder masqué.
- [ ] Navigation hiérarchique + retour (système et UI) conforme à la règle
      d'or UX (`OPDS.md` §1.2), testée par `OpdsViewModelTest`.
- [ ] Téléchargement non bloquant, livre injecté et lisible sans redémarrage
      de l'app, vérifié sur device.
- [ ] Aucune duplication de la détection DRM (K7) ni de la normalisation de
      href (K6) hors du pipeline d'import existant.
- [ ] `PLAN_ACTION_INKTONE_TOP_TIER.md` et `UX_FLOW_DESIGN.md` mis à jour,
      plus de référence à un ADR fantôme.
- [ ] Catalogue `http://` accepté avec avertissement UX explicite ;
      catalogues par défaut restent HTTPS non éditables ; aucun bypass TLS.
- [ ] Fichier téléchargé importé sans détournement de
      `persistReadPermission` (test dédié vert), stockage privé purgé après
      import.
- [ ] Couvertures réseau chargées avec succès sur un catalogue protégé par
      Basic Auth (vérifié sur device, pas supposé).
- [ ] Accessibilité TalkBack vérifiée sur `CatalogDashboardScreen` et
      `OpdsFeedScreen`.
- [ ] `./gradlew build` vert.
