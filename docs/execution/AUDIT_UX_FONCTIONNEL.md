# Audit UX fonctionnel — chaque fonction remplit-elle son rôle ?

**Date :** 2026-11-11 — **Base :** branche `fix/progression-lecture`, `03b6a336`,
arbre de travail non committé inclus (45 fichiers modifiés ou nouveaux).
**Méthode :** lecture statique du code, en trois passes (écrans, fonctions, puis
chaînes de données de la source à l'affichage). Aucune mesure sur appareil,
aucun build lancé.

**Règle appliquée (Blueprint §17.2) :** le code fait foi. Chaque constat cite le
fichier et la ligne qui le prouve. Un constat que la lecture ne suffit pas à
trancher est marqué **[à confirmer sur appareil]** et regroupé en §7.

**Ce que cet audit ne recouvre pas :** la fluidité (`AUDIT_REACTIVITE_UX.md`),
la robustesse (`AUDIT_CONSOLIDATION_V1.md`), le rendu visuel (contrastes,
espacements, animations).

---

## 1. Synthèse

L'application est globalement saine : la navigation est cohérente, les états
vides et d'erreur existent presque partout, la position de lecture a une source
unique, les suppressions de livres sont confirmées. Les défauts trouvés ne sont
pas des écrans cassés mais des **fonctions qui ne font pas ce que leur libellé
promet**.

Passes 1 et 2 (écrans et fonctions) :

| Gravité | Nombre | Définition |
|---|---|---|
| **P1** | 19 | La fonction ne remplit pas son rôle, trompe l'utilisateur, ou détruit une donnée sans filet |
| **P2** | 18 | Friction ou incohérence réelle, la fonction marche mais mal |
| **P3** | 14 | Finition |

Passe 3 (chaînes de données, §9) : **76 constats supplémentaires**, dont 23 P1
(répartition en §9.16). Elle change l'ordre des priorités : plusieurs fonctions
dont l'écran est correct reposent sur des données fausses ou sur une identité
de livre qui ne tient pas.

**À traiter avant tout le reste (passe 3) :**

- **D57, D58, D59** — synchronisation et sauvegarde ne peuvent pas fonctionner : les livres sont identifiés par un numéro aléatoire propre à chaque installation. Entre deux appareils rien ne s'apparie ; une restauration est soit ignorée, soit fatale à l'application.
- **D39** — après toute pause (notification, appel, son d'une autre application), la position affichée et enregistrée devance le son.
- **D35, D36** — ouvrir le Lecteur pendant une narration l'interrompt, ou mélange deux livres.
- **D3, D6, D7, D10** — la recherche plein texte ouvre le mauvais chapitre, cherche dans les chemins de fichiers, charge des résultats sans limite et garde les livres supprimés.
- **D25, D26, D28** — les statistiques comptent du temps d'écran, datent mal les sessions et affichent un « temps restant » inventé.
- **D18, D21** — position incomplète pour les PDF, non suivie pour les TXT en défilement.
- **D41, D42, D40** — le moteur de voix choisi n'est pas respecté ; un échec bascule définitivement ; une panne se traduit par une lecture muette qui avance.
- **D71** — un modèle de voix corrompu est irrécupérable.

Les six à traiter en premier parmi les passes 1 et 2 :

0. **A14** — ouvrir un signet, une note ou un résultat situé dans un autre chapitre affiche une page vide au début du chapitre **[à reproduire sur appareil]**.
1. **A4** — le livre est rouvert de zéro à chaque retour vers le Lecteur, ce qui peut renvoyer le lecteur à une ancienne position.
2. **A5** — le rappel de repos oculaire coupe la narration 60 s toutes les heures, par défaut, même écran éteint.
3. **A3** — la recherche lancée depuis un livre cherche dans tous les livres et n'indique pas d'où vient chaque résultat.
4. **A7** — le téléchargement de la voix neuronale (183 Mo) est annulé dès qu'on quitte les Réglages.
5. **A1, A2, A15, A16** — quatre réglages affichés n'ont aucun effet (langue, police système, gain audio, intonation hors voix système).

---

## 2. Périmètre

| Zone | Niveau de lecture |
|---|---|
| Navigation (`InkToneNavHost`, `Routes`, `MainActivity`) | Intégrale |
| Bibliothèque, Récents, Détail séries/tags, Marque-pages et notes | Intégrale |
| Lecteur : `ReaderScreen`, `ReaderUiState`, panneaux (HUD, typographie, TTS, minuteur, sommaire, barre du haut, ligne de statut) | Intégrale |
| `ReaderViewModel` | Ouverture, position, TTS, repos oculaire, réglages, `onCleared` ; le reste survolé |
| Réglages (`SettingsScreen`, `SettingsViewModel`), Recherche, Onboarding, Mini-lecteur | Intégrale |
| Statistiques, OPDS, Synchronisation, Thèmes, À propos, `BookmarkPanel`, popup de sélection | Libellés, actions et ViewModels ; mise en page non relue |
| `PagedChapterContent`, `BookBlockItem`, `FixedPageContent`, `PlaybackOrchestrator` | Sondages ciblés uniquement |
| `infrastructure/*`, `data/*` | Hors périmètre, sauf vérifications ponctuelles |

**Deuxième passe (même jour)** — fonctions suivies de bout en bout, du geste à
l'effet : progression de lecture (A19), luminosité (B16), réglette, gain et
intonation, auto-défilement, vidage du cache, réinitialisation, rapports de
crash, navigation par `Locator`.

**Toujours non tracé, donc sans verdict :** calcul de la série et de l'objectif
quotidien, export des statistiques, synchro automatique et option Wi-Fi,
chaîne de téléchargement OPDS, précision du surlignage mot à mot, commandes de
la notification média, justesse de la pagination.

---

## 3. P1 — fonctions qui ne remplissent pas leur rôle

### A1 — « Langue de l'interface » ne change pas la langue

- **Preuve :** `SettingsScreen.kt:350-354` bascule `fr`/`en` ; `SettingsViewModel.kt:143` se contente d'écrire la préférence. Aucun autre lecteur de `preferences.language` pour l'interface, aucun `values-en` dans `app/src/main/res`, tous les libellés sont des chaînes françaises en dur dans les composables.
- **Effet de bord :** `SettingsViewModel.kt:285` et `:349` réutilisent cette valeur comme langue du profil vocal créé par défaut. Passer à « English » ne traduit rien, mais un profil vocal créé ensuite (premier réglage de voix ou de vitesse) sera enregistré en anglais.
- **Correctif :** retirer la ligne tant que l'internationalisation n'existe pas (K « aucune destination affichée sans écran derrière »).
- **Validation :** la carte « Appareil » ne propose plus de langue ; aucun test ne référence `SetLanguage` depuis l'UI.

### A2 — « Police adaptée au système » n'a aucun effet

- **Preuve :** `SettingsScreen.kt:339-342` écrit `useSystemFontScale`. Recherche sur tout le dépôt : la valeur n'est lue que par l'écran Réglages lui-même, le mapper, l'entité et la sauvegarde. Aucun thème ni composable ne la consomme.
- **Correctif :** brancher la valeur dans `InkToneTheme` (densité `fontScale`), ou retirer l'interrupteur.
- **Validation :** basculer l'interrupteur change visiblement la taille des textes d'interface avec une échelle système ≠ 1.

### A3 — La recherche du Lecteur cherche dans toute la bibliothèque, sans dire où

- **Preuve :**
  - L'icône « Recherche » du HUD (`UnifiedControlPanel.kt:191`) ouvre `SearchRoute` sans identifiant de livre (`InkToneNavHost.kt:298`).
  - `SearchViewModel.kt:38` appelle `searchPublication(query)` sans `publicationId`, alors que le use case l'accepte déjà (`SearchPublicationUseCase.kt:13`).
  - `SearchResult` ne porte que `publicationId`, `locator`, `snippet` (`SearchService.kt:33-37`) et `SearchResultItem` n'affiche que l'extrait (`SearchScreen.kt:111`). Ni titre de livre, ni chapitre.
  - Un tap sur un résultat empile un **second** Lecteur par-dessus Recherche et le premier Lecteur (`InkToneNavHost.kt:315-323`).
- **Impact :** depuis un livre, les résultats mélangent tous les livres sans qu'on puisse les distinguer ; chaque recherche ajoute deux niveaux à la pile de retour.
- **Correctif :** passer `publicationId` à `SearchRoute` depuis le Lecteur ; pour un résultat du livre ouvert, dépiler et envoyer `NavigateToLocator` au Lecteur existant au lieu d'en empiler un autre. Si une recherche globale est voulue, lui donner une entrée dans la Bibliothèque et afficher titre + chapitre par résultat.
- **Validation :** depuis le livre A, une requête ne renvoie que des extraits de A ; après un tap sur un résultat, un seul retour ramène à la Bibliothèque.

### A4 — Le livre est rouvert intégralement à chaque retour vers le Lecteur

- **Preuve :** `InkToneNavHost.kt:284-295` envoie `OpenPublication` dans un `LaunchedEffect(route)`. Ce bloc rejoue chaque fois que la destination revient en composition, donc à chaque retour depuis Réglages (`:301`), Règles de prononciation (`:300`) ou Recherche (`:298`). `ReaderViewModel.openPublication` (`:604`) n'a aucune garde « déjà ouvert » : il relit le fichier et remplace tout l'état (`:683`).
- **Impacts :**
  - Un Lecteur ouvert depuis un résultat de recherche ou un signet garde sa cible dans la route. Au retour d'un sous-écran, `navigateToLocator(effectiveLocator)` (`:773-776`) **ramène à cette cible**, même si l'utilisateur a lu plus loin depuis. Avec `flashOnArrival`, le flash rejoue.
  - `observeAnnotations` (`:821`) et `observeBookmarks` (`:924`) relancent une collecte sans annuler la précédente : les collecteurs s'accumulent.
  - Rechargement visible du chapitre et réarmement du rappel de repos oculaire (`:762`).
- **Correctif :** garde en tête de `openPublication` (même `publicationId` déjà ouvert, sans erreur → ne rien faire), et consommer la cible de navigation une seule fois (clé `rememberSaveable` côté NavHost ou drapeau dans le ViewModel). Stocker les jobs d'observation et les annuler avant relance.
- **Validation :** ouvrir un livre depuis un signet, avancer de deux chapitres, ouvrir puis fermer les Réglages : la position ne bouge pas, aucun flash, aucun rechargement.

### A5 — Le rappel de repos oculaire interrompt la narration, par défaut

- **Preuve :** activé par défaut à 60 min (`UserPreferences.kt:51-52`). Armé à l'ouverture du livre (`ReaderViewModel.kt:762`) par un simple `delay` sur l'horloge (`:1626-1632`), sans tenir compte de l'écran ni du mode. À l'échéance, `triggerEyeRestReminder` met la narration en pause (`:1641-1642`) puis ne la relance qu'après un compte à rebours de 60 s (`:1649-1658`).
- **Impact :** quelqu'un qui écoute un livre téléphone verrouillé subit une minute de silence toutes les heures, sans rien voir à l'écran. Un rappel pour les yeux n'a pas de raison de couper une écoute.
- **Note :** le commentaire `:1634-1638` renvoie à une décision consignée (3d.7). Elle est à re-arbitrer : le cas « écoute seule » n'y est pas distingué.
- **Correctif :** ne compter que le temps de lecture visuelle au premier plan (le `sessionTracker` distingue déjà VISUAL/AUDIO) ; ne jamais mettre la narration en pause ; ne pas armer pendant le TTS.
- **Validation :** narration lancée, écran verrouillé, intervalle réglé à 15 min : aucune coupure audio à l'échéance.

### A6 — Vitesse et voix modifiées pendant la narration ne s'appliquent pas

- **Preuve :** `setTtsSpeed` et `setActiveVoiceProfile` (`ReaderViewModel.kt:1523-1542`) enregistrent le profil et mettent à jour l'affichage, sans toucher à la lecture en cours. L'ordonnanceur garde le profil reçu au lancement dans son contexte de session et le réutilise pour la suite (`PlaybackOrchestrator.kt:358`, `:626`, `:635`, `:913`). La vitesse est appliquée à la synthèse, pas à la lecture audio.
- **Impact :** le curseur « Vitesse » du panneau « Contrôle vocal » bouge, l'affichage change, le son ne change pas avant une pause/reprise ou un saut de phrase.
- **Correctif :** si `isPlaying`, relancer la narration à la phrase courante avec le nouveau profil (à la fin du geste, pas à chaque cran).
- **Validation :** narration en cours, passer de 1,0× à 2,0× : la phrase suivante est audiblement plus rapide.

### A7 — Le téléchargement de la voix neuronale meurt avec l'écran Réglages

- **Preuve :** le téléchargement tourne dans `viewModelScope` (`SettingsViewModel.kt:207-214`) ; le commentaire `:99-102` confirme que l'état est « perdu à la navigation ». Or la proposition du Lecteur envoie l'utilisateur dans les Réglages (`ReaderScreen.kt:1676-1679`), en haut de page, sans pointer la carte concernée.
- **Impact :** 183 Mo à télécharger en restant sur l'écran Réglages. Revenir lire annule tout.
- **Correctif :** confier le téléchargement à WorkManager (le module `infrastructure/worker` existe), exposer la progression par un flux observable depuis n'importe quel écran, faire défiler jusqu'à la carte à l'arrivée depuis le Lecteur.
- **Validation :** lancer le téléchargement, revenir au Lecteur, rouvrir les Réglages : la progression a continué.

### A8 — Taille du texte et thème : surcharges par livre invisibles et irréversibles

- **Preuve :**
  - Le curseur « Taille du texte » du Lecteur écrit une surcharge par publication (`ReaderScreen.kt:1453-1456`), alors que tous les autres réglages du même panneau sont globaux (interligne, marges, police : `:1457`, `:1471-1474`).
  - Aucun écran n'émet `SettingsIntent.SetFontSize` : la taille globale n'est modifiable que par le préréglage Accessibilité (`SettingsViewModel.kt:138`, aucun appelant UI).
  - L'icône « Thème » du HUD écrit aussi une surcharge par livre (`ReaderScreen.kt:1323-1328`). Un livre ainsi marqué ignore ensuite le thème choisi dans la Galerie (`ThemeGalleryViewModel.kt:43-45` n'écrit que la préférence globale).
  - Aucun écran n'émet `SetOverrides(null)` : une surcharge ne peut plus être retirée.
- **Impact :** chaque nouveau livre s'ouvre en 18 sp, à régler de nouveau. Un thème marqué « ACTIF » dans la Galerie peut ne pas être celui du livre ouvert, sans explication.
- **Correctif :** décider d'un modèle et l'afficher. Recommandation : taille et thème globaux par défaut, avec un interrupteur explicite « Pour ce livre uniquement » et une action « Revenir aux réglages généraux ».
- **Validation :** régler 22 sp dans un livre, en ouvrir un autre jamais ouvert : il s'affiche en 22 sp.

### A9 — Recherche de la Bibliothèque : le filtre survit, la barre disparaît

- **Preuve :** le mode recherche est un `remember` local (`LibraryScreen.kt:440`, `LibraryDetailScreen.kt:67`) ; la requête vit dans le ViewModel, qui survit à la navigation. L'activité n'a pas de `configChanges` (`AndroidManifest.xml:49-52`).
- **Impact :** chercher « dune », ouvrir le livre, revenir (ou pivoter l'écran) : la barre du haut redevient « Bibliothèque », mais la grille reste filtrée sur « dune » sans rien qui l'indique. Par ailleurs aucun `BackHandler` : le retour système pendant une recherche quitte l'application au lieu de fermer la recherche.
- **Correctif :** dériver `isSearchActive` de l'état du ViewModel (comme `LibraryItemsUiState.isSearchExpanded`, qui le fait correctement), ajouter un `BackHandler(enabled = isSearchActive)`.
- **Validation :** après retour du Lecteur, soit la barre de recherche est encore ouverte avec sa requête, soit la grille est complète.

### A10 — Notes, surlignages et signets supprimés en un tap, sans filet

- **Preuve :** dans le panneau du Lecteur, l'icône corbeille émet directement la suppression (`BookmarkPanel.kt:261`, `:310`, `:379` → `ReaderScreen.kt:1588-1590`). Idem pour « Supprimer » du menu d'annotation (`AnnotationTapPopup.kt:59` → `ReaderScreen.kt:1608-1611`).
- **Incohérence :** la vue globale « Marque-pages et notes » demande confirmation pour la même opération (`LibraryItemsScreen.kt:414-432`, « Cette action est irréversible »).
- **Impact :** une note rédigée est perdue sur un tap mal placé, la corbeille étant voisine du crayon.
- **Correctif :** Snackbar « Annuler » après suppression (préférable à un dialogue pour un geste fréquent), ou la même confirmation que la vue globale.
- **Validation :** supprimer une note depuis le Lecteur offre une récupération pendant quelques secondes.

### A11 — Désactiver un préréglage efface les choix antérieurs

- **Preuve :** `applyDarkModePreset` (`SettingsViewModel.kt:392`) et `applyAccessibilityPresetToggle` (`:411`) remettent les **valeurs par défaut** à la désactivation, pas les valeurs d'avant.
- **Impact :** un lecteur en thème Sépia et police Serif qui essaie « Accessibilité » puis le coupe se retrouve en Papier Clair, police système, 18 sp, grille détaillée. « Mode sombre » coupé repasse l'interface en « Système » même si elle était en « Clair ».
- **Aggravant :** la réglette de lecture n'a aucun interrupteur propre (`SetReadingRulerEnabled` n'est émis par aucun écran) : elle ne s'obtient qu'avec le préréglage complet.
- **Correctif :** mémoriser l'état précédent à l'activation et le restaurer ; exposer la réglette comme réglage indépendant dans le panneau typographie du Lecteur.
- **Validation :** activer puis désactiver chaque préréglage laisse les réglages identiques à l'état de départ.

### A12 — Supprimer un livre en cours de narration n'arrête pas la narration

- **Preuve :** `DeletePublicationUseCase` supprime la ligne, la pré-analyse et les caches, sans toucher à `PlaybackSession`. Aucun des trois ViewModels appelants ne l'arrête (`LibraryViewModel.kt:145-147`, `RecentsViewModel.kt:79-81`, `LibraryDetailViewModel.kt:62-64`).
- **Impact :** le mini-lecteur continue d'afficher et de lire un livre retiré. L'ordonnanceur tente ensuite d'écrire une position pour une publication absente (`PlaybackOrchestrator.kt:954-955`) **[issue exacte à confirmer sur appareil]**.
- **Correctif :** dans le use case, arrêter la session si `metadata.publicationId` est le livre supprimé, avant la suppression.
- **Validation :** narrer un livre, le retirer depuis la Bibliothèque : le son s'arrête et le mini-lecteur disparaît.

### A13 — Deux listes peuvent planter sur clé dupliquée

- **Preuve :**
  - `ImportResultComponents.kt:120` : clé `"${resultType}_${fileName}"`. Deux fichiers de même nom et même issue dans un lot (deux `livre.epub` de dossiers différents, tous deux en doublon) donnent la même clé.
  - `BookStatisticsScreen.kt:135` : clé `"${dateFormatted}_${timeRange}"`. Deux sessions du même livre dans la même minute donnent la même clé.
- **Impact :** `LazyColumn` lève `IllegalArgumentException` sur clé dupliquée : fermeture de l'application à l'ouverture du détail.
- **Correctif :** clé = index, ou identifiant stable ajouté au modèle.
- **Validation :** test Compose avec deux entrées identiques dans chacune des deux listes.

### A14 — Aller à un signet d'un autre chapitre : page vide, début de chapitre

- **Preuve :**
  - Les chapitres EPUB non encore lus sont des coquilles sans phrases ni blocs (`ReadiumPublicationParser.kt:195`, `:215`).
  - `navigateToLocator` (`ReaderViewModel.kt:1049-1076`) change de chapitre **sans jamais appeler** `loadChapterContentIfNeeded`. Ses seuls appelants sont l'ouverture (`:758`, pour le chapitre de reprise), `navigateToChapter` (`:1143`), la narration PDF (`:1324`) et le suivi de narration (`:1476`).
  - Le préchargement qui suit vise N+1, N−1, N+2, jamais N (`:1211`), et ne met pas l'état à jour.
  - Sur une coquille, `indexOfFirst` ne trouve rien : la phrase retombe à 0 (`:1053`), et `persistPosition` abandonne faute de phrase (`:1288`).
- **Cas touchés :** tap sur un signet, une note ou un surlignage du panneau du Lecteur ; ouverture depuis « Marque-pages et notes » ou depuis un résultat de recherche quand la cible n'est pas dans le chapitre de reprise (`:773-776`). Variante : un chapitre déjà visité puis évincé de la fenêtre (`:1243`) garde ses phrases mais plus ses blocs — bonne phrase, page vide.
- **Réserve :** aucun test ne couvre ce chemin avec des chapitres paresseux (les faux fournissent des chapitres complets). Un défaut aussi visible aurait dû remonter : **à reproduire sur appareil avant de corriger**.
- **Correctif :** dans `navigateToLocator`, charger le chapitre cible et attendre la fin du chargement avant de résoudre la phrase, comme le fait déjà l'ouverture pour le chapitre de reprise.
- **Validation :** livre ouvert au chapitre 2, signet posé au chapitre 15 jamais relu depuis le lancement : un tap affiche le passage marqué, et la position enregistrée est la sienne.

### A15 — « Gain audio » n'agit sur rien

- **Preuve :** le curseur (`SettingsScreen.kt:255-263`) écrit `audioGain` (`SettingsViewModel.kt:153-154`). Recherche sur tout le dépôt : la valeur n'est lue que par cet écran, le mapper, l'entité et la sauvegarde. Aucun moteur, aucun lecteur audio ne la consomme.
- **Correctif :** l'appliquer dans le lecteur audio (gain borné, avec limiteur pour éviter l'écrêtage au-delà de 1,0×), ou retirer le curseur.
- **Validation :** « Écouter un extrait » à 1,0× puis à 3,0× donne un volume audiblement différent.

### A16 — « Intonation » n'agit que sur la voix système

- **Preuve :** `pitchControl = false` pour Sherpa-ONNX (`SherpaOnnxTtsEngine.kt:71`) et Edge (`EdgeTtsEngine.kt:49`, hauteur figée à `+0Hz` dans `EdgeTtsClient.kt:141`) ; vrai seulement pour la voix système (`AndroidNativeTtsEngine.kt:65`). Le curseur (`SettingsScreen.kt:267-275`) s'affiche quel que soit le moteur, sans consulter les capacités.
- **Règle enfreinte :** « un moteur ne fait jamais semblant ».
- **Correctif :** n'afficher le curseur que si `TtsCapabilities.pitchControl` est vrai pour le moteur choisi.
- **Validation :** moteur Sherpa sélectionné, le curseur n'apparaît pas.

### A17 — La réglette de lecture n'est pas à la bonne hauteur

- **Preuve :** la réglette se place à `offset(y = currentLineY)` dans la zone de lecture (`ReadingRuler.kt:33`). En mode défilement, la valeur reçue est la hauteur de la ligne **à l'intérieur de son paragraphe** (`BookBlockItem.kt:402`, `:519`), pas dans l'écran — le commentaire de `ReaderScreen.kt:387-391` le dit lui-même. Elle n'est juste que si le paragraphe lu est tout en haut.
- **Seconde limite :** la position ne vient que du mot prononcé par le TTS (`BookBlockItem.kt:397`). En lecture silencieuse, la réglette reste figée en haut de page.
- **Correctif :** ajouter la position du paragraphe dans la zone (coordonnées de mise en page) ; en lecture silencieuse, la masquer ou la laisser déplacer au doigt.
- **Validation :** narration en mode défilement, paragraphe actif au milieu de l'écran : la bande couvre la ligne prononcée.

### A18 — « Réinitialiser les paramètres » renvoie à l'écran de bienvenue

- **Preuve :** l'action écrit `UserPreferences()` (`SettingsViewModel.kt:192`), dont `hasSeenOnboarding` vaut `false` (`UserPreferences.kt:60`). `MainActivity.kt:85-94` observe ce drapeau et en dérive la destination de départ du graphe : son changement reconstruit la navigation sur l'onboarding.
- **Impact :** l'utilisateur est éjecté des Réglages vers « Bienvenue sur InkTone ». Sont aussi remis à zéro, sans que le dialogue le dise : la proposition de voix déjà vue, la voix active, les couleurs d'annotation récentes.
- **Plus grave que décrit initialement :** la même ligne de préférences porte l'identité de l'appareil (`deviceId`) et le compte de synchronisation (`RoomSyncAccountRepository` y écrit). La réinitialisation déconnectait donc la synchronisation et donnait à l'appareil une nouvelle identité, sans que le dialogue l'annonce.
- **Correctif :** conserver tout ce qui n'est pas un réglage : parcours, identité de l'appareil, compte et options de synchronisation, historique des couleurs d'annotation (`UserPreferences.withSettingsReset`).
- **Validation :** après réinitialisation, l'utilisateur reste sur les Réglages.

### A19 — Progression : calcul juste, affichage non uniforme

**Ce qui est juste.** Le calcul est unique (`Publication.progressionAt`, `ReadingProgress.kt`), fondé sur la position en caractères dans le livre entier, partagé par le Lecteur (`ReaderUiState.kt:223-242`), la Bibliothèque, les Récents (`ProgressMap.kt:25-34`) et les Statistiques (`GetCurrentBookUseCase.kt:40-42`). L'index de chapitre enregistré est bien la position dans l'ordre de lecture, côté Lecteur (`ReaderViewModel.kt:1193`) comme côté narration sans écran (`PlaybackOrchestrator.kt` : index du programme).

**Ce qui ne l'est pas :**

| Écart | Preuve |
|---|---|
| **Trois arrondis pour une même valeur.** 12,6 % réels s'affichent « 12,6% » dans le Lecteur, « 13% » en Bibliothèque (arrondi, plancher à 1), « 12% » aux Statistiques (troncature). | `StatusLineBar.kt:152-157`, `ProgressMap.kt:31`, `StatisticsScreen.kt:669` |
| **Le conflit de synchro utilise encore l'ancienne formule** `chapitre / nombre de chapitres` : le pourcentage qui sert à choisir entre deux appareils n'est pas celui affiché ailleurs. | `PositionConflict.kt:19`, `SyncConflictBottomSheet.kt:101` |
| **« Livres finis », « Terminé », « En cours » restent à l'index de chapitre** (déjà B2). | `GetStatisticsUseCase.kt:66`, `PublicationDao.kt:36-37`, `:103-104` |
| **100 % est inatteignable.** La progression est celle du *début* de la phrase courante ; pour un PDF, c'est `page / nombre de pages` : la dernière page d'un PDF de 10 pages affiche 90 %, d'un PDF de 2 pages 50 %. | `ReaderUiState.kt:237`, `ReadingProgress.kt:63-66` |
| **La carte « Reprendre » affiche « 0% »** et une barre vide quand la progression est inconnue, alors que la règle partout ailleurs est « aucun chiffre plutôt qu'un chiffre faux ». | `LibraryScreen.kt:243`, `:1025-1036` |
| **Le Lecteur écarte les longueurs si la structure a changé, pas la Bibliothèque** : un pourcentage peut s'afficher sur la couverture et pas dans le livre. | `ReaderViewModel.kt:694-695` contre `ReadingProgress.kt:76` |
| Un livre entamé dont la progression est inconnue est annoncé « Non commencé » à TalkBack. | `BookCover.kt:99` |

- **Correctif :** une seule fonction de formatage dans le domaine (même arrondi, même décimale ou non, même espace avant %), utilisée par les quatre écrans ; `PositionConflict` calculé par `progressionAt` ; une position en fin de dernier chapitre ou sur la dernière page vaut 100 % ; `progressPercent` nullable jusqu'à la carte « Reprendre ».
- **Validation :** un même livre à une position donnée affiche le même chiffre dans le Lecteur, sur sa couverture, dans Récents et aux Statistiques ; un PDF lu jusqu'à sa dernière page affiche 100 %.

---

## 4. P2 — frictions et incohérences

| # | Constat | Preuve | Correctif |
|---|---|---|---|
| B1 | **État vide trompeur.** Un filtre sans résultat (Favoris, Non lus, format) affiche « Votre bibliothèque est vide » et « Importer votre premier livre ». Seul le cas recherche a été corrigé. Même message après « Ignorer » sur une erreur de chargement. | `LibraryScreen.kt:231-235`, `:1134`, `:1179-1200` | Passer `activeFilter` et `selectedFormats` à `EmptyState` ; message « Aucun livre ne correspond à ce filtre » + bouton « Effacer les filtres ». |
| B2 | **« Terminé » et « En cours » ne suivent pas le pourcentage affiché.** Le filtre utilise encore l'ancien critère d'index de chapitre, que cette branche vient de retirer partout ailleurs. Un livre à 85 % dans un long dernier chapitre est « Terminé ». | `PublicationDao.kt:36-37` | Filtrer sur la même progression que `computeProgressMap`. |
| B3 | **PDF étiqueté en chapitres.** La ligne de statut, le panneau des signets, la vue globale et le conflit de sync écrivent « Chapitre N » pour une page PDF. | `StatusLineBar.kt:101-103`, `BookmarkPanel.kt:253,305,353`, `LibraryItemsScreen.kt:371`, `SyncConflictBottomSheet.kt:74,80,101` | Passer le format et écrire « Page N / total ». |
| B4 | **Aucun moyen de sauter loin dans un livre.** La barre de progression est un indicateur passif ; le sommaire est masqué hors EPUB. Un PDF de 400 pages ne se parcourt que page à page. | `BookProgressBar.kt:19`, `ReaderScreen.kt:1339` | Curseur de progression interactif, ou « Aller à la page » pour le PDF. |
| B5 | **Sommaire imprécis.** Une entrée ne porte qu'un index de chapitre : plusieurs sections d'un même fichier mènent toutes au début du fichier et sont toutes surlignées comme « courantes ». | `DocumentModel.kt:89-93`, `TableOfContentsSheet.kt:107,115` | Conserver le fragment à l'analyse et naviguer par `Locator`. |
| B6 | **Inversion PDF sans commande.** `ToggleForcePdfInversion` n'est émis par aucun écran : un PDF scanné reste blanc en thème sombre. L'inversion automatique ne reconnaît que le fond `#000000` exact, donc pas un thème sombre personnalisé. | `ReaderUiState.kt:528`, `ReaderScreen.kt:869-877` | Interrupteur dans le panneau de réglages pour les PDF ; test de luminance du fond plutôt qu'égalité de chaîne. |
| B7 | **« Enregistrer » de la Sync n'enregistre rien et éjecte.** Le bouton appelle `onBack`, qui dépile vers la Bibliothèque, alors que la connexion est déjà effective. Depuis « Gérer », on attend un retour au tableau de bord. | `SyncConfigurationScreen.kt:122-126`, `InkToneNavHost.kt:417` | Renommer « Terminé » et se contenter de `manualShowConfig = false`. |
| B8 | **Deux éditeurs de règles de prononciation.** Celui ouvert depuis le Lecteur est resté à l'état d'ébauche : titre affiché deux fois, sans accents, pas de modification, suppression directe par bouton plein. | `PronunciationRulesScreen.kt:31-79`, `InkToneNavHost.kt:447` | Réutiliser la carte et le dialogue des Réglages (`SettingsScreen.kt:942-1068`). |
| B9 | **Bornes incohérentes entre écrans.** Vitesse 0,5–2,0 aux Réglages contre 0,5–3,0 au Lecteur. Intervalle de repos borné à 120 min aux Réglages, sans plafond au Lecteur. Les crans de 0,25 du Lecteur s'affichent arrondis à une décimale (0,75 → « 0,8 »). | `SettingsScreen.kt:249`, `ReaderTtsPanel.kt:154-159`, `SettingsScreen.kt:489`, `SleepTimerPanel.kt:145` | Constantes partagées dans le domaine ; format à deux décimales. |
| B10 | **Export : fichier vide abandonné.** Le fichier est créé avant la saisie du mot de passe ; annuler le dialogue le laisse à 0 octet. | `InkToneNavHost.kt:348-350`, `:378` | Demander le mot de passe avant le sélecteur de fichier, ou supprimer le document à l'annulation. |
| B11 | **Sauvegarde décrite de façon incomplète.** Le libellé et l'avertissement d'import omettent les notes, surlignages et thèmes personnalisés, pourtant inclus et donc remplacés. Rien ne dit que les livres eux-mêmes ne sont pas sauvegardés. | `SettingsScreen.kt:421`, `:584-586`, `BackupModels.kt:35,40` | Aligner les deux textes sur le contenu réel. |
| B12 | **Import : bouton mort et progression masquée.** « Détails » reste affiché et inactif dans la vue détail. Un nouvel import lancé avant d'avoir fermé le résumé précédent n'affiche aucune progression. | `ImportResultComponents.kt:109`, `LibraryScreen.kt:205-213` | Masquer le bouton en vue détail ; vider les anciens résultats au lancement d'un import. |
| B13 | **Épinglage sans témoin ni alternative.** Un livre épinglé remonte en tête sans marque sur sa couverture. Dans « Marque-pages et notes », épingler ne passe que par un appui long et supprimer que par un balayage, sans action accessible à TalkBack. | `BookCover.kt` (aucun usage de `isPinned`), `BookActionsSheet.kt:72`, `LibraryItemsScreen.kt:289-294`, `:331-334` | Badge d'épingle ; `customActions` de sémantique pour épingler et supprimer. |
| B14 | **Aucun champ de recherche ne prend le focus.** Il faut un second tap pour faire apparaître le clavier dans les cinq recherches de l'application. | Seul `SelectionActionPopup.kt` utilise `FocusRequester` | `FocusRequester` + `LaunchedEffect` à l'ouverture. |
| B16 | **Luminosité : fonctionnelle, mais lourde et peu lisible.** Elle s'applique bien à la fenêtre du Lecteur, se mémorise, revient au réglage système à la sortie. En revanche : chaque échantillon du glissement fait une lecture et une écriture en base, et ni le remplissage de la barre ni l'écran ne bougent avant le retour de la base **[latence à mesurer]** ; `readerBrightness` étant un champ structurel, chaque échantillon recompose tout l'écran. En mode « système », la barre s'affiche vide. Le retour au réglage système est un soleil de 32 dp identique à celui de droite, sans libellé. Non réglable avec TalkBack (aucune action de réglage). Disparition après 3 s. | `ReaderViewModel.kt:1614-1619`, `InkToneLevelBar` (remplissage piloté par `value`), `ReaderLiveFields.kt`, `ReaderBrightnessBar.kt:87-106`, `ReaderScreen.kt:284-289` | État local pendant le geste, appliqué à la fenêtre tout de suite, écrit en base au relâchement ; icône « auto » distincte ; `setProgress` de sémantique. |
| B17 | **Les panneaux du Lecteur ne suivent pas le thème de lecture.** Barre de luminosité, réglages, voix, minuteur, sommaire et signets prennent les couleurs de l'interface. Interface claire et thème Obsidienne : un panneau blanc s'ouvre sur une page noire, y compris en réglant la luminosité dans le noir. | Aucun `MaterialTheme` dérivé du thème de lecture dans `feature/reader` ; `ReaderBrightnessBar.kt:77` | Envelopper les surimpressions du Lecteur dans un jeu de couleurs dérivé de `resolvedTheme`. |
| B18 | **Auto-défilement : boucle active en fin de chapitre.** Arrivée en bas, la boucle ne sort pas : la liste a des éléments, l'attente se termine aussitôt et la boucle recommence, indéfiniment, jusqu'à un toucher. Le commentaire annonce une sortie qui n'a pas lieu. | `ReaderScreen.kt:763-777` | Sortir (ou suspendre sur `canScrollForward`) une fois la première mesure faite. |
| B15 | **Synchronisation manuelle muette.** Depuis le menu de la Bibliothèque, rien n'indique qu'une synchro est en cours ; l'échec s'affiche sans motif. Par ailleurs un message de développeur est visible si le `clientId` Google manque. | `LibraryViewModel.kt:199-209`, `LibraryScreen.kt:148-153`, `SyncConfigurationScreen.kt:313` | Snackbar indéfini pendant l'opération ; relayer le motif d'échec ; reformuler le message. |

---

## 5. P3 — finition

| # | Constat | Preuve |
|---|---|---|
| C1 | Accents manquants dans des libellés visibles : « Regles de prononciation », « A propos », « desactives par defaut », « Local par defaut », « Non determine », « mot a mot ». | `InkToneNavHost.kt:447,486`, `AboutScreen.kt:71,117,194,339`, `PronunciationRulesScreen.kt:31` |
| C2 | Fiche « Détails du livre » : deux `Spacer` en largeur dans une colonne (aucun espace vertical produit), format et langue affichés bruts (`EPUB`, `fr`), titre et auteurs non nettoyés. | `BookActionsSheet.kt:148-156`, `:173` |
| C3 | Cibles tactiles sous 48 dp : menu 3-points des couvertures (32 dp), bouton lecture de la carte « Reprendre » (36 dp), actions du HUD et de la barre TTS (44 dp). | `BookCover.kt:166`, `LibraryScreen.kt:1048`, `UnifiedControlPanel.kt:212-217`, `TtsPillBar.kt:271` |
| C4 | L'icône de recherche reste une loupe quand la recherche est ouverte ; seul son libellé d'accessibilité change. | `LibraryItemsScreen.kt:117-119` |
| C5 | Dialogue de choix de voix du Lecteur : seul le bouton radio est cliquable, pas la ligne. | `ReaderTtsPanel.kt:201-213` |
| C6 | Une regex invalide est acceptée à l'enregistrement puis ignorée en silence à la synthèse. | `SettingsViewModel.kt:320-330`, `PronunciationRuleApplier.kt:25-26` |
| C7 | Suppression d'une règle de prononciation en un tap, sans confirmation, à côté de l'interrupteur. | `SettingsScreen.kt:1010-1012` |
| C8 | Détail d'un tag : la progression ne se rafraîchit pas au retour du Lecteur (flux limité à la table des publications, états lus une seule fois). | `RoomPublicationRepository.kt:26`, `LibraryDetailViewModel.kt:85-90` |
| C9 | Course d'écriture d'état : la copie d'état est calculée hors du fil principal puis réassignée, ce qui peut écraser une frappe de recherche survenue entre-temps. | `LibraryViewModel.kt:308-315` |
| C10 | OPDS : une carte de livre n'ouvre rien (pas de fiche, pas de description) et le bouton de téléchargement ne reflète ni l'état en cours ni terminé. | `OpdsFeedScreen.kt:194-231` |
| C11 | À propos : toucher la version affiche « Version officielle », sans utilité ; échec d'ouverture du client mail silencieux. | `AboutScreen.kt:100-101`, `:137` |
| C12 | Onboarding : aucun bouton « Suivant » (balayage seul) ; promet « des voix ultra-réalistes » alors que la voix par défaut est celle du système. | `OnboardingScreen.kt:97-121`, `:192` |
| C13 | Statistiques : pas d'état vide pour un compte neuf, uniquement des zéros (« 0h 0m »). | `StatisticsViewModel.kt:133-137` |
| C14 | Documentation et code morts : le KDoc du Lecteur annonce l'absence d'auto-défilement TTS alors qu'il existe ; paramètres et champs jamais utilisés (`floatingActionButton`, `floatingAudioButton`, `LibraryEffect.NavigateToStats`, `availableAuthors`, `autoStartTts`). | `ReaderScreen.kt:183-186` contre `:506-520` ; `LibraryScreen.kt:106-107`, `LibraryUiState.kt:62,199`, `Routes.kt:27` |

---

## 6. Ce qui a été vérifié et tient

Lu dans le code, sans défaut relevé :

- **Navigation :** tiroir unique partagé, pile plate entre destinations principales, onboarding retiré de la pile, mini-lecteur masqué sur le Lecteur et l'onboarding.
- **Bibliothèque :** grille et badges alimentés par deux flux combinés, sans rechargement au retour ; confirmation explicite avant retrait d'un livre ; résumé d'import par catégorie (doublons, DRM, corrompus, format).
- **Position de lecture :** écriture différée pendant le défilement, vidée dans `onCleared` ; défilement manuel ignoré pendant la narration (K3) ; ancrage du mode défilement protégé contre l'écrasement à l'ouverture.
- **Lecteur :** état d'erreur avec « Réessayer » et « Retour » réels ; HUD en surimpression sans redimensionner la page ; commandes TTS masquées pour un PDF sans texte ; minuteur de sommeil porté par la session, donc survivant à la fermeture de l'écran.
- **Mini-lecteur :** état dérivé de la session, masqué quand il doublonne la carte « Reprendre ».
- **Marque-pages et notes :** filtre, tri et recherche en requête, pagination, confirmation de suppression, état vide distinct selon filtre.
- **OPDS :** retour système géré niveau par niveau, pagination, avertissement sur URL non chiffrée.
- **Sauvegarde :** mot de passe obligatoire à l'export avec avertissement de perte ; avertissement avant import.
- **Studio de thème :** indicateur de contraste WCAG, validation du code couleur, confirmation de suppression.

---

## 7. À confirmer sur appareil

| Point | Pourquoi la lecture ne suffit pas |
|---|---|
| A12 — issue exacte après suppression d'un livre narré | Dépend de la contrainte de clé étrangère et du `catch` englobant de l'ordonnanceur. |
| Ligne de statut sur une page PDF sans texte | `isMeasurementComplete` peut valoir vrai avec zéro offset mesurable (`ChapterPaginationState.kt:134-135`), ce qui afficherait « Chapitre N (1/0) ». |
| A14 — page vide après un saut vers un autre chapitre | Chemin sans ambiguïté dans le code, mais défaut trop visible pour ne pas avoir été vu : à reproduire avant tout correctif. |
| B16 — latence du réglage de luminosité | Dépend du temps d'aller-retour Room sur le Snapdragon 680. |
| D35 — narration interrompue à l'ouverture du Lecteur | L'issue (arrêt ou relance) dépend de l'ordre d'arrivée de deux événements. |
| D39 — avance de la position après une pause | Logique certaine ; l'ampleur perçue dépend de la durée des pauses et de l'avance de synthèse. |
| D53 — import d'un seul fichier sans retour | Dépend de la livraison de la dernière mise à jour de progression par WorkManager. |
| D45 — surlignage avec la voix système | Dépend du moteur TTS installé. |
| D71 — modèle de voix tronqué | Repli silencieux ou plantage natif selon la bibliothèque. |
| D1, D14, D48 — couverture en double, espaces multiples, page blanche | Effets visuels à constater sur des EPUB réels. |
| A5 — coupure audio écran verrouillé | Le `delay` survit tant que le processus vit ; le service de premier plan le maintient, à vérifier en conditions Doze. |
| C9 — perte d'une frappe | Fenêtre de quelques millisecondes ; visible surtout pendant un import groupé. |
| Contrastes des icônes du HUD (`accentColor` à 70 % d'opacité) | Non mesurable sans rendu, à contrôler sur les six thèmes intégrés. |

---

## 8. Plan d'action

Ordre proposé, par lots cohérents. Chaque lot est indépendant des autres.

| Lot | Contenu | Risque |
|---|---|---|
| **0 — Reproduire puis corriger** | A14 | À faire en premier : si confirmé, c'est le défaut le plus visible. |
| **1 — Retirer ce qui ment** | A1, A2, A15, A16, A18, B12 (bouton mort), C14 (KDoc périmé) | Faible : suppressions ou branchement simple. |
| **1 bis — Progression uniforme** | A19, B2 | Faible à moyen : formatage unique, puis filtres SQL. S'inscrit dans la branche en cours. |
| **2 — Cycle de vie du Lecteur** | A4, puis A3 qui en dépend | Moyen : touche l'ouverture et K3. Tests `ReaderViewModel` à étendre (réouverture idempotente, cible consommée une fois). |
| **3 — Audio** | A5, A6, A12 | Moyen : `PlaybackOrchestrator`. |
| **4 — Voix neuronale** | A7 | Moyen : déplacement vers WorkManager. |
| **5 — Modèle des réglages** | A8, A11, B9 | Décision produit à prendre d'abord (global ou par livre). |
| **6 — Filets de sécurité** | A10, A13, C7 | Faible. |
| **7 — Bibliothèque** | A9, B1, B2, B13, B14 | Faible. |
| **8 — PDF et navigation longue** | B3, B4, B5, B6, A17, B16, B17, B18 | Moyen : B5 touche l'analyse EPUB. |
| **9 — Finition** | B7, B8, B10, B11, B15, C1 à C13 | Faible. |

**Lots issus de la passe 3**, à insérer avant le lot 1 pour les trois premiers :

| Lot | Contenu | Risque |
|---|---|---|
| **A — Identité des livres** | D57, D58, D59, D60, D61 : apparier par `fileHash`, rendre la restauration idempotente, corriger le texte | Élevé : format de sauvegarde et de synchro, migration à prévoir. |
| **B — Session de narration** | D35, D36, D39, D40, D46, puis A4, A5, A6, A12 | Élevé : cœur de `PlaybackOrchestrator` et ouverture du Lecteur. |
| **C — Index et adressage** | D2, D3, D4, D19, A14, puis D6 à D11 (reconstruction de l'index) | Moyen à élevé : réindexation des bibliothèques existantes. |
| **D — Moteurs et voix** | D41, D42, D43, D44, D71 à D74, D76, A7, A15, A16 | Moyen. |
| **E — Statistiques** | D25 à D34, A19, B2 | Moyen : les sessions déjà enregistrées gardent leurs dates fausses. |
| **F — Formats et rendu** | D18, D21, D22, D48, D12 à D16 | Élevé pour D12 à D14 : tout changement du texte extrait déplace signets et surlignages existants (D17) — à décider avant d'y toucher. |
| **G — Import et fichiers** | D53 à D56, D63, D68 | Moyen. |

**Décisions de l'auteur (2026-11-12).** Toutes sont arrêtées. La dernière
colonne signale où la décision s'écarte de la recommandation reçue, pour
respecter une règle du projet ou une limite technique ; ces écarts ont été
validés par l'auteur.

| # | Décision | Écart avec la recommandation reçue |
|---|---|---|
| A8 | Taille du texte et thème **globaux par défaut**, surcharge par livre **explicite** (« Pour ce livre uniquement ») et réversible (« Revenir aux réglages généraux »), stockée en base. | Aucun. |
| A3 | **Recherche plein texte globale**, avec une entrée dans la Bibliothèque ; la recherche lancée depuis le Lecteur reste limitée au livre ouvert. Index construit à l'import (déjà le cas). | L'index reste en **FTS4** natif Room, pour garder les migrations gérées par Room. Room ne gère que `@Fts3`/`@Fts4` (`SentenceFtsEntity.kt:13`) ; FTS5 imposerait une table créée à la main hors du harnais de migration, pour un gain sans objet à ce volume. |
| A5 | Rappel de repos oculaire **désactivé par défaut** (opt-in). Il ne met jamais la narration en pause. | La bannière « après plusieurs heures de lecture » proposée est hors périmètre de l'audit : à planifier à part si voulue. Les installations existantes gardent leur valeur enregistrée. |
| D55 | Un livre importé est **copié dans le stockage privé de l'application**. | Aucun sur le principe. Conséquences à planifier (lot dédié, ADR) : double occupation disque, rattrapage des livres déjà importés tant que leur permission SAF tient, purge du fichier à la suppression (rejoint D63, D68). K5 reste respecté : l'accès au fichier d'origine passe toujours par SAF. |
| D17 | Signets et surlignages ré-ancrés par leur **texte** quand le parseur évolue. | Pas d'EPUB CFI : CLAUDE.md impose `Locator` comme **seul** value object de position, et un CFI serait un second système d'adressage. `Locator` reçoit un contexte textuel optionnel (32 caractères avant et après la position, sur le modèle de `Locator.Text` de Readium), et le ré-ancrage cherche ce contexte quand le parseur évolue. Voir [ADR-027](../adr/ADR-027-locator-contexte-textuel-reancrage.md). |
| D1 | **Pas de couverture ajoutée** en tête quand le livre a déjà une page de couverture ; la couverture reste dans la Bibliothèque et la fiche du livre. | Aucun. Attention : retirer la page ajoutée décale l'index de chapitre de tous les livres concernés (D2, D4) — positions, signets et longueurs sont à migrer dans le même lot. |
| D74 | **Moteur et voix** choisis dans les Réglages ; **vitesse** réglable directement dans la barre de lecture du Lecteur, appliquée sans relancer à la main (rejoint A6). | Pas de curseur d'intonation dans la barre : aucun moteur n'applique la hauteur (A16, D43), et l'afficher enfreindrait « un moteur ne fait jamais semblant ». Il n'apparaîtra que pour un moteur qui l'applique réellement. |
| Recherche Bibliothèque | Étendre la recherche rapide aux **tags, séries, éditeur et description**, sans le plein texte (réservé à A3). | Aucun. La description est à chercher sur son texte, une fois le HTML retiré (`3540f679`). |

---

## 9. Troisième passe — chaînes de données

Règle de cette passe : une fonction n'est déclarée juste que si la donnée
qu'elle consomme a été suivie jusqu'à sa source. Un commentaire qui explique ou
assume un comportement ne retire rien à la liste.

### 9.1 Structure d'un EPUB et index de chapitre

| # | Constat | Preuve |
|---|---|---|
| D1 | **Une « couverture » est ajoutée en tête de presque tout EPUB.** Le repli conçu pour les couvertures hors ordre de lecture s'appuie sur `linkWithRel("cover")`, qui désigne l'**image** de couverture du manifeste, pas une page. Une image n'est jamais dans l'ordre de lecture : la condition d'ajout est donc vraie dès qu'un EPUB déclare une image de couverture. Si le livre a déjà une page de couverture XHTML, la couverture s'affiche deux fois de suite. Le rôle `cover` porté par l'image est vérifié dans la bibliothèque Readium elle-même (`ResourceAdapter.computePropertiesAndRels`) ; seul l'effet visuel reste à constater sur appareil. | `ReadiumPublicationParser.kt:203-214`, `:258-261` |
| D2 | **Deux numérotations de chapitre coexistent.** La liste du Lecteur est décalée de +1 par la couverture ajoutée ; le parseur de chapitre, lui, numérote par `readingOrder.indexOf(link)`, sans décalage. Le Lecteur corrige à la volée pour l'affichage, mais **l'index de recherche est écrit à l'import avec l'index non décalé**. | `EpubChapterParser.kt:138`, `ImportPublicationUseCase.kt` (`indexSentences(publication.id, chapter.index, …)`), correction locale `ReaderViewModel.kt:1193` |
| D3 | **Conséquence : un résultat de recherche ouvre le chapitre précédent.** Le résultat porte l'index non décalé ; `navigateToLocator` ne lit que cet index et ignore le `resourceHref`, pourtant exact. | `RoomSearchService.kt` (`toSearchResult`), `ReaderViewModel.kt:1049-1053` |
| D4 | La structure est recalculée à chaque ouverture. Si la détection de couverture échoue ce jour-là (repli `<guide>` qui relit le fichier), tous les index glissent d'un cran pour la session : position, signets, surlignages et longueurs. Rien ne compare la structure lue à `chapterCount` enregistré, sauf pour masquer la progression. | `ReadiumPublicationParser.kt:136-146`, `:258-261`, `ReaderViewModel.kt:694-695` |
| D5 | Une entrée de sommaire dont la cible n'est pas dans l'ordre de lecture retombe en silence sur le premier chapitre. | `ReadiumPublicationParser.kt` (`toTocEntry` : `coerceAtLeast(0)`) |

### 9.2 Index de recherche

| # | Constat | Preuve |
|---|---|---|
| D6 | **Toutes les colonnes sont indexées et interrogées.** `MATCH` porte sur la table entière : identifiant, index, chemin du fichier et offset sont cherchés comme du texte. Chercher « text », « html », « chapter » ou un nombre remonte toutes les phrases des livres dont le chemin interne contient ce mot. | `SentenceFtsEntity.kt:13-21`, `SentenceFtsDao.kt` (`WHERE sentence_fts MATCH :query`) |
| D7 | **Aucune limite, aucun tri.** Un mot courant (« de », « le ») charge en mémoire toutes les phrases correspondantes de toute la bibliothèque, dès la première lettre tapée. | `SentenceFtsDao.kt`, `SearchViewModel.kt:36-40` |
| D8 | **Accents et majuscules accentuées exacts obligatoires.** Tokeniseur par défaut : « eleve » ne trouve pas « élève », « école » ne trouve pas « École ». | `SentenceFtsEntity.kt:13` (`@Fts4` sans tokeniseur) |
| D9 | **Pas de recherche par début de mot** : la requête est une phrase exacte entre guillemets ; rien ne s'affiche tant que le mot n'est pas complet. | `RoomSearchService.kt` (`sanitizeFtsQuery`) |
| D10 | **L'index n'est jamais purgé.** Aucune suppression à la suppression d'un livre : ses phrases restent trouvables et mènent à « Publication introuvable ». | `SentenceFtsDao.kt` (aucune requête de suppression), `DeletePublicationUseCase.kt` |
| D11 | **Index incomplet sans signal.** Si un chapitre échoue à l'import, ou si l'application est tuée après l'insertion, l'indexation s'arrête ; elle n'est jamais reprise et rien ne l'indique. | `ImportPublicationUseCase.kt` (bloc `try` unique, `catch` vide) |

### 9.3 Fidélité du texte extrait

| # | Constat | Preuve |
|---|---|---|
| D12 | **Les retours à la ligne `<br>` deviennent des espaces** : vers, adresses, dialogues en ligne sont fondus en un paragraphe. | `JsoupChapterParser.kt` (`"br" -> textBuilder.append(' ')`) |
| D13 | **Listes, tableaux, citations à plusieurs paragraphes sont aplatis** en un seul paragraphe, sans puces ni numéros. Seuls `div`, `section`, `article`, `main` sont parcourus en profondeur. | `JsoupChapterParser.kt:263-360` |
| D14 | **Les espaces ne sont pas normalisés.** Un saut de ligne ou une tabulation du source devient une espace, un pour un : un XHTML indenté produit des suites d'espaces visibles dans le texte. **[à confirmer visuellement]** | `JsoupChapterParser.kt` (`softWrapsToSpaces`, aucun regroupement) |
| D15 | **Les liens ont l'apparence de liens et ne font rien.** Notes de bas de page et renvois internes sont colorés, sans action au tap. | `BookBlockStyleMapper.kt:52` ; aucun gestionnaire de lien dans `rendering/` |
| D16 | Le style porté par CSS est ignoré (italique par classe, centrage, contenu masqué affiché). | `JsoupChapterParser.kt` (aucune lecture de `class`/`style`) |

### 9.4 Offsets et évolution du parseur

| # | Constat | Preuve |
|---|---|---|
| D17 | **Signets, surlignages, position et index de recherche sont adressés par offset de caractère dans le texte produit par le parseur.** Quand le parseur change, la pré-analyse et les longueurs de chapitres sont régénérées, mais pas ces quatre-là : ils restent dans l'ancien espace d'offsets. Tout correctif de D12 à D14 déplacera donc les surlignages existants. | `RefreshStalePreAnalysesUseCase.kt` (ne touche que pré-analyse et longueurs), `FilePreAnalysisStore.kt:117` |

### 9.5 Position de lecture : écriture et relecture

| # | Constat | Preuve |
|---|---|---|
| D18 | **Le décalage vertical dans une page PDF n'est jamais enregistré.** `Locator.pageOffsetY` existe, est calculé, déclenche une écriture différée à chaque panoramique, mais la table n'a pas de colonne et le mapper l'abandonne. À la réouverture il vaut toujours 0. Conséquence sur la progression : le Lecteur compte `page + décalage`, la Bibliothèque `page` seule. | `LocatorMapper.kt:6-21`, `ReadingStateMapper.kt`, `ReaderViewModel.kt:518-524`, `:707`, `ReadingProgress.kt:77` |
| D19 | **Un index de chapitre enregistré hors bornes laisse un livre vide sans message.** À l'ouverture, `currentChapterIndex` reprend la valeur enregistrée sans la borner ; si la structure lue a moins de chapitres (D4, synchro depuis un autre appareil), aucun chapitre n'est courant : zone vide, ligne de statut absente, aucune erreur. | `ReaderViewModel.kt:694-699` (`currentChapterIndex = restored?.locator?.chapterIndex ?: 0`), `:1051` (retour silencieux) |
| D20 | **Deux écrivains modifient la même ligne en lecture-puis-écriture non atomique** : la narration (position, à chaque phrase) et le Lecteur (surcharges de thème/taille, position manuelle). Un changement de thème pendant la narration peut être écrasé par l'écriture de position qui avait lu l'ancienne valeur, et inversement. | `PlaybackOrchestrator.kt:954-955`, `ReaderViewModel.kt:568-574`, `:1300-1310` |

### 9.6 Fichiers TXT et PDF

| # | Constat | Preuve |
|---|---|---|
| D21 | **Un TXT entier est un seul paragraphe.** En mode défilement, le livre est un unique élément de liste : la position ne peut plus être déduite du défilement (le « premier bloc visible » est toujours le bloc 0), donc **ni la progression ni la reprise ne suivent la lecture d'un TXT en défilement**, et le suivi TTS ne peut pas amener la phrase lue à l'écran. Coût de mise en page proportionnel au fichier entier. | `TxtPublicationParser.kt` (un seul `ParagraphBlock`, `blockIndex = 0`), `ReaderScreen.kt:536-555`, `:506-520` |
| D22 | **Un TXT qui n'est pas en UTF-8 s'affiche avec des caractères de remplacement.** Le décodage UTF-8 ne lève jamais d'erreur : le message « encodage ? » prévu est inatteignable. Les textes français anciens sont souvent en Windows-1252. | `TxtPublicationParser.kt` (`toString(Charsets.UTF_8)` sous `runCatching`) |
| D23 | Le format est déduit de l'extension du nom de fichier. Un fichier texte accepté par le sélecteur (`text/plain`) mais sans extension `.txt` est traité comme un EPUB et rejeté comme corrompu. | `ImportPublicationUseCase.kt` (`formatOf`), `CompositePublicationParser.kt:53-59`, `InkToneNavHost.kt:264` |
| D24 | Un PDF est chargé entièrement en mémoire à l'import et à chaque ouverture. Trop gros, l'échec d'allocation est rattrapé et présenté comme « Fichier introuvable ou illisible ». Le plan (signets) du PDF est ignoré : pas de sommaire. | `PdfPublicationParser.kt:60-62` |

### 9.7 Statistiques : d'où viennent les chiffres

| # | Constat | Preuve |
|---|---|---|
| D25 | **Le temps de lecture visuelle est du temps d'écran, pas du temps de lecture.** Le compteur tourne tant que le Lecteur est affiché, sans aucune détection d'inactivité. Avec « Garder l'écran allumé », un livre laissé ouvert trois heures compte trois heures : objectif du jour, série, totaux, vitesse et temps restant en héritent. | `ReadingSessionTracker.kt` (`flush` : simple différence d'horloge), `ReaderViewModel.kt:1798-1817` |
| D26 | **Un fragment est daté de la fin du fragment précédent, pas de son propre début.** `startedAt` reprend `lastFragmentSavedMs`, qui n'est pas remis à l'heure à la reprise. Après une pause (application en arrière-plan la nuit, Lecteur resté ouvert), les premières minutes du lendemain sont datées de la veille au soir. Or jour, série, objectif du jour et créneau horaire sont tous calculés sur `startedAt`. Un fragment à cheval sur minuit est compté entièrement sur le jour de départ. | `ReaderViewModel.kt` (`persistFragment` : `fragmentStart = lastFragmentSavedMs`), `onAppForeground` (ne le met pas à jour), `ReadingSessionDao.kt` (toutes les agrégations sur `startedAt`) |
| D27 | **Supprimer un livre efface son temps de lecture.** Les sessions partent en cascade : totaux, historique quotidien et série reculent rétroactivement. Le dialogue de confirmation ne mentionne que les marque-pages et les notes. | `ReadingSessionEntity.kt:19-22` (`ON DELETE CASCADE`), `BookActionsSheet.kt` (`DeleteConfirmationDialog`) |
| D28 | **« Temps restant » du détail d'un livre est un nombre inventé** : `nombre de chapitres × 3000 mots ÷ vitesse`. Il ne dépend pas de la position, ne diminue jamais en lisant, et compte la couverture ajoutée (D1) ou chaque page d'un PDF pour 3000 mots. L'écran principal des statistiques calcule, lui, une autre estimation pour le même livre. | `BookStatisticsViewModel.kt` (`estimatedWordsRemaining = publication!!.chapterCount * 3000L`), `StatisticsViewModel.kt:177-183` |
| D29 | **La vitesse de lecture repose sur des mots mal comptés.** (a) Faire défiler vite un chapitre crédite tous les mots franchis comme lus, sans plafond de vraisemblance. (b) Après un saut par signet dans le même chapitre, le prochain petit défilement crédite tout l'intervalle sauté. (c) Dans un fragment mixte, les mots narrés par le TTS s'ajoutent aux mots lus mais ne sont divisés que par le temps visuel. (d) La lecture visuelle d'un PDF, ou d'un TXT en défilement (D21), ne crédite aucun mot. (e) L'inactivité (D25) tire la valeur vers le bas. | `ReaderViewModel.kt:489-510`, `:1049-1071`, `ReadingSessionDao.kt` (`getRecentSessionsWithWords`), `GetStatisticsUseCase.kt` (`averageWpm`) |
| D30 | **La carte d'habitudes mesure des interruptions, pas du temps, et sur une échelle par ligne.** L'intensité est un nombre de fragments (un toutes les 5 min, plus un à chaque mise en arrière-plan), normalisé séparément pour chaque créneau horaire : un seul fragment à 6 h s'affiche aussi « très actif » que cinquante à 22 h, alors que la légende va d'« Inactif » à « Très actif » sans le dire. | `ReadingSessionDao.kt` (`COUNT(*) as interactionCount`), `GetStatisticsUseCase.kt` (`computeHeatmapSlots` : maximum par créneau) |
| D31 | **Un livre à un seul chapitre est « Terminé » dès qu'il est ouvert.** Le critère `chapitre atteint ≥ dernier chapitre` est vrai d'emblée pour tout TXT : il n'est jamais « En cours » et compte tout de suite dans « Livres finis ». Atteindre le dernier chapitre par le sommaire suffit aussi. | `PublicationDao.kt:36-37`, `:103-104`, `TxtPublicationParser.kt` (chapitre unique) |
| D32 | **Une série se gagne en cinq secondes.** Tout jour portant un fragment (seuil : 5 s d'écran) compte, sans lien avec l'objectif quotidien affiché juste au-dessus. | `ReadingSessionTracker.kt` (`shouldPersist`), `ReadingSessionDao.kt` (`getDistinctReadingDays`) |
| D33 | **Deux définitions du « livre en cours ».** Statistiques : livre de la dernière session enregistrée. Bibliothèque : dernier livre ouvert. Un livre ouvert moins de 5 s est « à reprendre » ici et absent là. Le titre n'y est pas nettoyé comme ailleurs, et la progression vaut 0 (pas « inconnue ») sans état de lecture. | `GetCurrentBookUseCase.kt`, `ObserveResumePublicationUseCase.kt:42` |
| D34 | Les statistiques sont calculées une fois à l'affichage de l'écran : une narration en cours ne les fait pas évoluer. | `GetStatisticsUseCase.kt` (`flow { emit(result) }`) |

### 9.8 Narration : session, écran et autres livres

| # | Constat | Preuve |
|---|---|---|
| D35 | **Ouvrir le Lecteur sur le livre en cours de narration interrompt la narration.** L'ouverture remplace l'état par un état neuf (`isPlaying = false`), puis rejoint la position enregistrée par `navigateToLocator`, qui appelle toujours `pausePlayback()` — c'est-à-dire `stop()` sur l'ordonnanceur. Selon qu'un changement d'état audio survient pendant le chargement du chapitre, la narration est soit arrêtée sans reprise, soit relancée au début de la phrase. C'est le chemin du tap sur le mini-lecteur et sur la carte « Reprendre ». **[à confirmer sur appareil]** | `ReaderViewModel.kt:683-713` (état neuf), `:773-776`, `:1064-1065`, `:1486-1490` ; `InkToneNavHost.kt:525-527` |
| D36 | **Le Lecteur ne vérifie jamais quel livre est narré.** La position narrée ne porte pas d'identifiant de publication, et l'ouverture d'un livre écrase les métadonnées de la session. Ouvrir un livre B jamais ouvert pendant que A est narré : la notification et le mini-lecteur affichent B alors que A est lu ; au prochain changement d'état audio, le Lecteur de B se déclare « en lecture » puis recale son affichage sur le chapitre et la phrase de A ; les mots narrés de A sont crédités à B. | `ReaderViewModel.kt:717-722` (`setMetadata`), `:160-200` (collecteurs sans filtre), `PlaybackOrchestrator.kt:186-194`, `:1153` |
| D37 | **Le minuteur de sommeil coupe net et compte par pas.** À l'échéance il appelle `stop()` au milieu d'une phrase ; le fondu annoncé par le modèle (`fadeOutEnabled = true`) n'existe pas. Le décompte soustrait un pas fixe à chaque `delay`, sans lire l'horloge : tout retard de réveil allonge la durée réelle. | `PlaybackOrchestrator.kt:447-470`, `SleepTimerState.kt:20` |
| D38 | L'export des statistiques ne contient ni titres ni mots lus, seulement des identifiants internes de livre ; les deux formats proposés (« récapitulatif » et « données brutes ») portent exactement les mêmes champs. Le partage est lancé sans sélecteur ni garde : sans application cible, l'application plante. | `ExportStatisticsUseCase.kt`, `StatisticsScreen.kt:107-112` |

### 9.9 Narration : cadencement, moteurs et réglages de voix

| # | Constat | Preuve |
|---|---|---|
| D39 | **Après une vraie pause, la phrase affichée et la position enregistrée passent devant le son.** L'avancée des phrases suit une horloge murale partie au début de la lecture ; `pause()` gèle l'attente en cours mais pas cette horloge. Au retour, les phrases suivantes sont « avancées » sans attendre, d'une durée égale à la pause, et l'écart ne se résorbe pas avant la fin du chapitre. Le surlignage mot à mot disparaît (il suit la phrase avancée, pas celle qu'on entend), et rouvrir le livre reprend après du texte jamais entendu. Toute pause est concernée sauf celle du Lecteur (qui arrête et relance) : mini-lecteur, notification, écran verrouillé, casque, appel téléphonique, et chaque son d'une autre application (la perte de focus « avec atténuation » est traitée en pause). | `PlaybackOrchestrator.kt` (`consume` : `elapsedMs = nanoTime − playStartNanos`, jamais corrigé ; `pace` ; `resume`), `AudioInterruptionPolicy.kt` (`FOCUS_LOST_TRANSIENT_CAN_DUCK → pauseFor`) |
| D40 | **Une synthèse qui échoue est remplacée par 50 ms de silence, sans limite.** Si le moteur échoue en continu, la narration « lit » le livre en silence : la position avance, les mots sont crédités aux statistiques, les chapitres s'enchaînent, sans son ni message. | `PlaybackOrchestrator.kt` (`segmentFor` : `catch → silence(TIMEOUT_SILENCE_MS)`, aucun compteur d'échecs) |
| D41 | **Le choix « Voix système » n'est pas respecté si la voix neuronale est installée.** Le routage ne distingue que Edge du reste ; « le reste » essaie toujours Sherpa d'abord, quel que soit le moteur du profil. | `SelectiveTtsEngine.kt:54-58`, `FallbackTtsEngine.kt` (`primary.synthesize` sans lire `voiceProfile.engine`) |
| D42 | **Un seul échec bascule sur la voix système jusqu'au redémarrage de l'application.** Le repli Sherpa → système est définitif pour le processus, y compris quand l'échec venait du modèle pas encore téléchargé : après le téléchargement (proposé par le Lecteur), la voix reste celle du système, alors que les Réglages affichent « Sherpa-ONNX (UPMC) ». Même verrou pour Edge après une seule erreur réseau. | `FallbackTtsEngine.kt` (`fallenBack`, jamais remis à faux), `SelectiveTtsEngine.kt` (`edgeFailed`), `SherpaOnnxTtsEngine.kt:76-77` |
| D43 | **« Intonation » n'est lue par aucun moteur** — pas même la voix système, qui déclare pourtant la capacité : `setPitch` n'est jamais appelé. (Renforce A16.) La valeur entre en revanche dans la clé du cache audio : la modifier fait tout resynthétiser à l'identique. | `AndroidNativeTtsEngine.kt:160-163`, `TtsSegmentCache.kt:102` |
| D44 | **La langue du livre est ignorée.** La voix système est forcée en français, le modèle neuronal est français : un livre en anglais est lu avec une phonétique française, sans avertissement. Le nom de voix du profil n'est pas appliqué à la voix système. | `AndroidNativeTtsEngine.kt` (`engine.language = Locale.FRENCH`), `Publication.language` jamais transmis |
| D45 | Le surlignage mot à mot de la voix système dépend d'un rappel (`onRangeStart`) que le moteur installé n'émet pas forcément lors d'une synthèse vers fichier. S'il n'arrive pas, aucun mot n'est surligné alors que la capacité et les Réglages l'annoncent. **[à confirmer sur les moteurs Google et Samsung]** | `AndroidNativeTtsEngine.kt` (`boundaries` alimenté par `onRangeStart` seul), `SettingsScreen.kt:1120` |
| D46 | En attente de synthèse (« tampon »), le bouton pause arrête la session au lieu de la suspendre, et le mini-lecteur disparaît. Un saut de phrase en pause déplace la position sans déplacer le son : la reprise continue l'ancienne phrase. | `PlaybackOrchestrator.kt` (`togglePlayPause` : `Buffering -> stop()` ; `skip` : branche `else`) |
| D47 | La pause plus longue prévue entre paragraphes n'est jamais appliquée : le test sur le saut de ligne final vient après un `trimEnd()`. | `PlaybackOrchestrator.kt` (`silenceDurationFor`) |

### 9.10 Mode paginé, sélection, annotations

| # | Constat | Preuve |
|---|---|---|
| D48 | **Le mode paginé n'affiche aucune image.** La mesure et le rendu des pages ne prennent que les paragraphes et les titres ; illustrations, cartes et séparateurs disparaissent. Un chapitre fait d'une seule image est une page blanche — or la couverture ajoutée en tête (D1) en est un : en mode paginé, presque tout EPUB s'ouvre la première fois sur une page vide. Les titres y sont rendus comme du texte courant. | `ChapterTextMeasurer.kt:142`, `ChapterPaginationState.kt:157`, `PagedChapterContent.kt` (aucun `ImageBlock`), `VirtualPaginationEngine.kt` (`pageCount ≥ 1`) |
| D49 | **Copier, partager et l'extrait enregistré collent les phrases entre elles.** Le texte sélectionné est reconstruit phrase par phrase sans rien remettre entre deux : « Fin.Début » au lieu de « Fin. Début », et aucun saut de paragraphe. | `AnnotationSelectionHandler.kt` (`sliceChapterText`), `ReaderScreen.kt:622-626`, `ReaderViewModel.kt` (`excerpt = sliceChapterText(…)`) |
| D50 | **Une sélection ne peut pas franchir un paragraphe (défilement) ni une page (paginé).** Impossible de surligner un passage à cheval. | `ReaderScreen.kt:162-173` (limite structurelle décrite), `PagedChapterContent.kt` (`PageBlock`) |
| D51 | **« Marquer cette page » marque une phrase.** Le signet n'est reconnu que si la phrase courante est exactement celle marquée : après un défilement d'un paragraphe ou un changement de taille de texte, le bouton repropose de marquer alors que le signet est à l'écran, et en crée un second à quelques lignes. | `ReaderUiState.kt:261-275`, `ReaderViewModel.kt` (`toggleBookmarkAtCurrentPosition`) |
| D52 | Aucun moyen de choisir la couleur d'une note à la création : le sélecteur de couleur crée un surlignage et ne permet pas de revenir à « Note ». Le sélecteur ne se ferme qu'en touchant ailleurs. | `SelectionActionPopup.kt` (modes `COLOR_PICKER` sans retour, `dismissOnClickOutside = false`) |

### 9.11 Import et fichiers source

| # | Constat | Preuve |
|---|---|---|
| D53 | **L'import d'un seul fichier peut ne donner aucun retour.** La progression n'est publiée qu'après chaque fichier terminé : pendant tout le traitement du premier, la bannière est masquée (total à 0, rien en file). Et le résumé de fin ne se charge que si un total > 0 a été observé juste avant la fin ; le second terme de la condition (`hasQueuedChunks`) relit la valeur qu'on vient d'écrire et vaut toujours faux à ce moment-là. Si la dernière mise à jour de progression est absorbée par la fin du travail, un doublon, un fichier protégé ou corrompu importé seul ne produit ni livre ni message. **[à confirmer sur appareil]** | `ImportWorker.kt` (`setProgressAsync` après chaque fichier), `WorkManagerImportProgressObserver.kt`, `LibraryViewModel.kt:96-110`, `LibraryScreen.kt:1087-1088` |
| D54 | **Une exception sur un fichier fait échouer tout le lot et annule les suivants.** Le worker n'isole pas chaque fichier ; les lots sont chaînés, donc l'échec d'un lot supprime ceux d'après. Les fichiers non traités n'apparaissent nulle part dans le résumé. | `ImportWorker.kt` (`async { importPublication(uri) }` sans `try`, `awaitAll`), `WorkManagerImportScheduler.kt` (`.then(...)`) |
| D55 | **Un livre importé n'est qu'un lien vers le fichier d'origine.** Rien n'est copié. Si le fichier est déplacé, renommé, supprimé, ou si la permission durable n'a pas pu être prise (l'échec est avalé), le livre reste en bibliothèque avec sa couverture et sa progression, mais ne s'ouvre plus : « corrompu », sans moyen de le relocaliser. Rien ne signale l'état en bibliothèque. | `SafFileStorageService.kt` (`persistReadPermission` sous `runCatching`), `ImportPublicationUseCase.kt` (`fileUri = fileUri`), `ReaderViewModel.kt:801-810` |
| D56 | Un chapitre illisible à l'import interrompt l'indexation, la pré-analyse et le calcul des longueurs pour tout le livre, alors que le Lecteur, lui, tolère ce chapitre. Le livre n'aura ni recherche complète ni pourcentage, sans explication. | `ImportPublicationUseCase.kt` (un seul `try` autour de la boucle des chapitres) |

### 9.12 Synchronisation et sauvegarde : identité des livres

| # | Constat | Preuve |
|---|---|---|
| D57 | **Deux appareils ne reconnaissent jamais le même livre.** L'identifiant d'un livre est un UUID tiré au hasard à l'import, sur chaque appareil. Or la synchronisation n'apparie positions, signets, annotations et sessions que par cet identifiant ; l'empreinte du fichier (`fileHash`), seule donnée commune aux deux appareils, n'est ni envoyée ni consultée. Tout ce qui vient d'un autre appareil et concerne un livre est donc écarté comme « livre absent ». Entre deux appareils, seuls les règles de prononciation et les thèmes personnalisés se synchronisent ; le dialogue de conflit de position ne peut pas se déclencher. | `ImportPublicationUseCase.kt` (`id = UUID.randomUUID()`), `SyncNowManager.kt` (`publicationRepository.getById(backup.publicationId)`), `BackupModels.kt` (aucun `fileHash`) |
| D58 | **Une sauvegarde ne se restaure ni après réinstallation ni sur un nouveau téléphone.** Même appariement par identifiant local : une fois les livres réimportés, leurs identifiants ont changé et tout est compté « ignoré (livre absent) ». | `BackupManager.kt:117-165` |
| D59 | **Restaurer une sauvegarde sur l'installation d'origine fait planter l'application.** C'est le seul cas où les identifiants concordent, et là les insertions sont refusées par la base (clé déjà présente) : l'exception n'est rattrapée nulle part. | `BackupManager.kt:125-161` (`insert`), `BookmarkDao.kt:17`, `AnnotationDao.kt:18`, `ReadingSessionDao.kt:112` (`@Insert` sans stratégie), `BackupViewModel.kt:71-80` (aucun `try`) |
| D60 | **L'avertissement d'import décrit le contraire de ce qui est fait.** Il annonce que l'import « remplace » les données actuelles de façon irréversible ; le code n'efface rien et ajoute. | `SettingsScreen.kt:584-586`, `BackupManager.kt:117-165` |
| D61 | **Une suppression ne se propage pas, une modification non plus.** La fusion ajoute ce qui manque localement : un signet supprimé revient depuis l'instantané d'un autre appareil ; une note modifiée ailleurs est ignorée parce que son identifiant est déjà connu. | `SyncNowManager.kt` (`if (backup.id !in local…) insert`) |
| D62 | La synchronisation est déclenchée toutes les 15 minutes et envoie à chaque fois l'instantané complet (toutes les sessions de lecture, toutes les annotations), puis télécharge et relit celui de chaque autre appareil. | `WorkManagerSyncScheduler.kt`, `SyncNowManager.kt` (`buildPayloadForSync`) |

### 9.13 Catalogues OPDS

| # | Constat | Preuve |
|---|---|---|
| D63 | **Le fichier d'un livre téléchargé n'est jamais supprimé avec le livre.** Il reste dans le stockage de l'application, sans moyen de le retirer. | `OpdsDownloadWorker.kt` (écriture dans `opds_downloads/`), `DeletePublicationUseCase.kt` (aucune suppression de fichier) |
| D64 | L'échec est toujours « Échec du téléchargement », que la cause soit le réseau, un fichier protégé ou un fichier invalide. Les PDF sont refusés (« format non supporté ») alors que l'application les lit. Aucun signal si l'on a quitté l'écran avant la fin. | `OpdsDownloadWorker.kt`, `DownloadOpdsBookUseCase.kt`, `OpdsViewModel.kt:76-88` |

### 9.14 Autres constats de cette passe

| # | Constat | Preuve |
|---|---|---|
| D65 | **Sans horodatage par mot, rien n'indique où en est la narration.** Seul le mot prononcé est surligné ; il n'existe aucun repli au niveau de la phrase, dont les bornes sont pourtant connues. Sont concernés : tout PDF, la voix neuronale tant que le modèle d'alignement est absent, la voix système si elle n'émet pas ses repères (D45), toute phrase en échec (D40). | `BookBlockItem.kt` (`highlightedRange` seul), `PagedChapterContent.kt`, `SherpaOnnxTtsEngine.kt:128-135` |
| D66 | **Tri et recherche ignorent le français.** Tri par titre ou auteur en ordre binaire sur la chaîne en minuscules : « Émile » et « À rebours » se rangent après « Zola ». Recherche de bibliothèque et de notes sensible aux accents (« eleve » ne trouve pas « élève ») ; dans les notes, `%` et `_` saisis sont interprétés comme jokers et la casse des lettres accentuées compte. | `LibraryUiState.kt:110-117`, `:95-98`, `LibraryItemDao.kt:29-36` |
| D67 | Chaque réglage est écrit par « lire toutes les préférences, puis réécrire toute la ligne », dans des coroutines indépendantes. Deux écritures rapprochées (rafale du réglage de luminosité, couleur d'annotation récente, cran de marge…) peuvent s'écraser : la seconde réécrit l'ancienne valeur du champ que la première venait de changer. | `ReaderViewModel.kt:1545-1619` (patron `get()` puis `update(copy)`), `LibraryViewModel.kt:164-167` |
| D68 | Les fichiers de couverture ne sont pas supprimés avec le livre ni par « Couverture par défaut ». Le nom du fichier dérive d'un hachage 32 bits de l'URI : deux livres peuvent, rarement, partager la même image. | `CoverStorage.kt:40`, `DeletePublicationUseCase.kt`, `PublicationDao.kt:90-91` |
| D69 | **Corrigé après vérification** — la première version de ce constat était fausse. Une nouvelle installation part de `UserPreferences()`, où les couleurs dynamiques sont **désactivées** ; seule la migration 9→10 les a activées (`DEFAULT 1`) pour les installations existant à ce moment-là. Restent : sur Android 8 à 11, l'interrupteur est affiché et sans effet ; au démarrage, l'état initial du flux vaut `true` avant la première lecture des préférences. | `UserPreferences.kt:25`, `RoomPreferencesRepository.kt:15-16`, `Migrations.kt:53`, `AppThemeViewModel.kt` (`stateIn(…, true)`), `InkToneTheme` (`SDK_INT >= S`) |
| D70 | Le découpage en phrases fusionne toute phrase finissant par un mot de sa liste d'abréviations, qui contient des mots courants : « mai. », « mars. », « juin. », « août. », « art. », « vol. », « t. », « p. ». Effet : unités de narration et de saut de phrase plus longues que la phrase réelle. | `FrenchSentenceSplitter.kt` (`abbreviations`, `endsWithAbbreviation`) |

### 9.15 Voix : profils et modèle téléchargé

| # | Constat | Preuve |
|---|---|---|
| D71 | **Un modèle de voix partiellement extrait est considéré comme installé, sans retour possible.** L'extraction écrit directement les fichiers définitifs ; « prêt » ne teste que leur existence. Si l'application est tuée pendant l'extraction, le modèle tronqué est tenu pour bon : la carte affiche « Voix neuronale installée », le bouton de téléchargement disparaît, et le moteur charge un fichier incomplet à chaque narration (repli silencieux sur la voix système, ou plantage natif **[à confirmer]**). Aucune commande ne permet de supprimer ou de retélécharger la voix, ni de libérer ses 183 Mo. Aggravant : le préchauffage (`warmUp`, lancé à chaque ouverture de livre) initialise le modèle sans protection ; une initialisation qui lève une exception remonte dans `viewModelScope.launch(Dispatchers.Default)` du Lecteur, sans gestionnaire. **[à confirmer]** | `SherpaOnnxModelPaths.kt` (`isReady` : `exists()`), `CtcModelPaths.kt`, `TarBz2Extractor.kt:71-76`, `SettingsScreen.kt:812-824` |
| D72 | **Le téléchargement ne reprend jamais et peut rester figé.** Aucune reprise partielle : toute interruption (dont le simple fait de quitter les Réglages, A7) repart de zéro, après avoir recalculé l'empreinte du fichier partiel. Aucun délai d'attente sur la connexion : un réseau qui se fige laisse la progression immobile sans erreur. Ni l'espace libre ni le type de réseau ne sont vérifiés avant 183 Mo. La progression est émise tous les 8 Ko (plus de vingt mille mises à jour d'état). | `VoiceModelDownloader.kt` (`openConnection()` sans `Range` ni délais, `emit` par tampon) |
| D73 | **Le « profil vocal actif » a trois définitions par défaut, et deux sources pour le moteur.** Sans profil enregistré, le Lecteur et la narration retombent sur `vp-native-fr` (voix système), les Réglages affichent « Voix par défaut » et créent `vp-settings-default` avec le moteur des préférences. Le moteur affiché aux Réglages vient des préférences, celui utilisé vient du profil. Régler la vitesse depuis le Lecteur ou depuis les Réglages peut ainsi créer deux profils distincts aux vitesses différentes. | `ReaderViewModel.kt:592-595`, `:1968-1971`, `SettingsViewModel.kt:339-358`, `SettingsScreen.kt:208-216` |
| D74 | **Le bouton « Voix » du Lecteur ne propose pas des voix mais des enregistrements de profil.** Il liste les profils existants en base — souvent un seul, parfois deux doublons de D73 — jamais Jessica, Pierre ou les voix Edge. En choisir un change aussi la vitesse, sans le dire. | `ReaderViewModel.kt:681` (`availableVoiceProfiles = getVoiceProfiles()`), `ReaderTtsPanel.kt:186-216`, `TtsVoiceCatalog.kt:17-28` |
| D75 | **Tourner une page ne se fait que par balayage.** Aucun tap en bord d'écran, aucune touche de volume : toucher la page ne fait qu'afficher ou masquer les commandes. | `PagedChapterContent.kt:746`, `:798-800` ; aucun traitement de touche dans `feature/reader` ni `MainActivity` |
| D76 | **Le cache audio ne sait pas quel moteur a réellement parlé.** La clé porte le moteur du profil, pas celui qui a synthétisé (version de synthèse constante pour tous). Une phrase produite par la voix de repli est rangée sous l'identité de la voix demandée et rejouée telle quelle plus tard : des phrases en voix système au milieu d'un chapitre en voix neuronale ou Edge. | `TtsEngine.kt:26` (`synthesisVersion = 1`, jamais redéfini), `TtsSegmentCache.kt:102`, `PlaybackOrchestrator.kt` (`segmentFor`) |

### 9.16 Gravité des constats de la troisième passe

| Gravité | Constats |
|---|---|
| **P1 — fonction fausse, trompeuse ou destructrice** | D3, D6, D7, D10, D18, D21, D25, D26, D27, D28, D35, D36, D39, D40, D41, D42, D48, D53, D55, D57, D58, D59, D71 |
| **P2 — friction ou incohérence réelle** | D1, D2, D4, D8, D9, D11, D12, D13, D14, D15, D17, D19, D22, D29, D30, D31, D33, D43, D44, D45, D49, D50, D51, D54, D56, D60, D61, D63, D65, D66, D72, D73, D74, D75, D76 |
| **P3 — finition ou risque rare** | D5, D16, D20, D23, D24, D32, D34, D37, D38, D46, D47, D52, D62, D64, D67, D68, D69, D70 |

### 9.17 Corrections apportées aux passes précédentes

- **§6, « Sauvegarde »** : l'interface (mot de passe, avertissements) est correcte, mais la fonction ne remplit pas son rôle — voir D58 à D60. La ligne ne doit plus être lue comme un satisfecit.
- **§6, « Mini-lecteur »** : l'affichage dérive bien de la session, mais la session elle-même peut désigner le mauvais livre (D36) et sa position peut devancer le son (D39).
- **A19, « ce qui est juste »** : vrai pour l'EPUB. Pour le PDF, la donnée d'entrée est tronquée (D18) : Lecteur et Bibliothèque ne calculent pas sur la même position.
- **A16** : aucun moteur n'applique l'intonation, pas même la voix système (D43).
- **A14** : le saut par résultat de recherche cumule ce défaut avec l'index décalé (D3).
- **B2** : étendu par D31 (livre à un seul chapitre « terminé » à l'ouverture).
- **B11** : sans objet tant que D57 à D59 ne sont pas corrigés.

### 9.18 Ce qui reste hors de cette passe

Lu partiellement ou pas du tout, donc **sans verdict** : rendu PDF (zoom, panoramique, cache de pages), client Edge TTS et son jeton, aligneur CTC, estimateur de position du lecteur audio, authentification Google et client WebDAV, relais de statistiques pendant la narration sans écran (`NarrationSessionContinuation`), Studio de thème (enregistrement), migrations Room. Aucun constat de ce rapport n'a été vérifié sur appareil.

---

## 10. Suivi des corrections

État au 2026-11-12, branche `fix/progression-lecture` (non poussée). « Appareil »
signifie vérifié par Issa sur le V2206 ; « Instrumenté » un test exécuté sur le
V2206 ; « Test JVM » un test automatisé qui échoue sans la correction ;
« Lecture » et « Compilation » : aucun test, vérification par relecture du code.
`./gradlew build` vert sur `7fce12a5`, compilé seul (sans l'arbre de travail).

| Constat | Correction | Commit | Vérification |
|---|---|---|---|
| A19 (en partie) | Progression calculée sur la longueur de tous les chapitres, même calcul dans le Lecteur, la Bibliothèque, Récents et les Statistiques ; position narrée publiée d'un bloc ; plus de recul en défilement | `86308c4d`, `ab44685b`, `504cc7c3` | Appareil + tests JVM + migration testée |
| B10 | Mot de passe d'export demandé avant la création du fichier | `a411acf1` | Appareil |
| B7 | Synchronisation : « Terminé » ramène au tableau de bord | `85d177e2` | Appareil |
| A9, B14, C4 | Recherche portée par l'état de l'écran, retour système qui la ferme, focus clavier, icône de fermeture | `5f86203d` | Appareil + test JVM |
| B1 | « Aucun livre dans ce filtre » et « Afficher tous les livres » | `cfb363e9` | Appareil |
| B13 | Témoin d'épinglage ; épingler et supprimer accessibles à TalkBack | `f141d1ea` | Appareil (témoin) ; TalkBack non vérifié |
| C2 | Fiche détails : espacements, langue lisible, titre et auteurs nettoyés | `87ada8bd` | Appareil |
| C2 (suite) | Description : HTML interprété à l'affichage | `3540f679` | Appareil + instrumenté |
| D59 | Restauration : ce qui existe déjà est conservé, jamais réinséré | `b8ccfafc` | Test JVM |
| D60, B11 | Textes de la carte « Données » conformes à ce que fait l'import | `4738ca4a` | Lecture |
| A18 | Réinitialisation : parcours, identité, synchronisation et couleurs récentes conservés | `534972c0` | Test JVM |
| C6 | Règles de prononciation inapplicables refusées à la saisie | `3983f8d5` | Tests JVM |
| D49 | Copier, partager, extrait : espaces et paragraphes restitués | `9daeef53` | Tests JVM |
| D42 | Repli vers la voix système levé à chaque ouverture de livre | `aa97bf66` | Test JVM |
| D71, D72 | Installation atomique de la voix ; délais d'attente, progression tous les 256 Ko, message en français | `8b9adc7b` | Tests JVM (installation) ; téléchargement : lecture |
| D66 (bibliothèque) | Tri alphabétique français, recherche insensible aux accents | `d25f0f12` | Tests JVM |
| A13 | Listes sans clé dérivée du contenu | `d5cbd3ed` | Compilation |
| D54 | Une exception sur un fichier n'interrompt plus le lot d'import | `9a4f96d1` | Compilation |
| D6, D7, D9, D10 | Recherche plein texte : texte seul, livres retirés exclus, 200 résultats en ordre de lecture, début de mot | `69c41b47` | Instrumenté |
| D30 | Carte d'habitudes : temps de lecture, une seule échelle | `bad480da` | Test JVM + instrumenté |
| A10 | Confirmation avant suppression dans le panneau des marque-pages et le menu d'annotation | `34640fc8` | Instrumenté |
| C8 | Détail séries/tags : progression mise à jour au retour du Lecteur | `ead8495f` | Test JVM |
| B12 | Bouton « Détails » inerte retiré ; progression d'import toujours visible | `8481a7aa` | Instrumenté |
| D69 | Couleurs dynamiques masquées avant Android 12 ; valeur initiale alignée | `69eb77a3` | Lecture |
| C1 | Accents rétablis | `7fce12a5` | Lecture |
| C5 | Choix de voix : toute la ligne cliquable | non committé | Invérifiable tant que la liste ne montre qu'une voix (D74) |

`BookmarkPanelTest` : les deux tests de balayage d'onglet
(`un_swipe_horizontal_change_l_onglet_affiche`,
`tap_sur_un_onglet_apres_un_swipe_reste_synchronise`) échouent aussi sur
`03b6a336`, avant toute correction : défaut préexistant, à traiter à part.

**Limites déclarées :**
- **D10** : les phrases d'un livre supprimé restent dans l'index (seule la requête les écarte). Les purger demande de modifier `DeletePublicationUseCase`.
- **D71** : une installation déjà tronquée avant ce correctif reste tenue pour bonne (seule l'existence des fichiers est vérifiée) ; seules les installations futures sont protégées.
- **B10** : si Android détruit l'application pendant le choix de l'emplacement, le mot de passe (jamais persisté, à dessein) est perdu et le fichier choisi reste vide.
- **D66** : la recherche de « Marque-pages et notes » reste sensible aux accents (requête SQL `LIKE` ; la rendre insensible demande une colonne normalisée, donc une migration).
