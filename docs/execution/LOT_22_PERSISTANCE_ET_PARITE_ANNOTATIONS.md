# Lot 22 — Persistance du travail lourd et parité des annotations

**Base :** `main` après merge du `LOT_21_GAINS_RAPIDES_PERF_UX.md` (le Lot 21
unifie le découpage de phrases sur les trois formats : ce Lot **dépend** de
cette unification, sinon il persisterait deux découpages incompatibles).
Source : `docs/incoming/STRATEGIE_PERF_UX_INSPIREE_MOONREADER.md`, **avec
deux corrections vérifiées dans le code** (voir « Corrections apportées au
document source » ci-dessous). ADR concernés : ADR-021 (jamais
d'interpolation de timestamps), ADR-018.

**Objectif :** appliquer le principe directeur retenu de Moon+ — **faire le
travail lourd une seule fois, puis lire vite** — là où InkTone ne l'applique
pas encore (pré-analyse des chapitres, reprise TTS à froid, pages PDF), et
combler les écarts de parité qui exigent une migration de schéma (types
d'annotation, édition depuis le panneau) plus la complétion de la sync.

**Nature du Lot :** chantier lourd. Plusieurs migrations Room, chacune avec
son test `MigrationTestHelper` **dans le même commit** (K4). Base de données
en version **27** au moment de la rédaction
(`infrastructure/database/.../InkToneDatabase.kt:45`) — renuméroter selon
l'état réel de `main` au démarrage du Lot.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

---

## Corrections apportées au document source

Le document source reste la référence des constats et des preuves, sauf sur
ces deux points, vérifiés dans le code au moment de la rédaction de ce Lot.
Ils ne sont pas des nuances de formulation : ils changent la conception du
cache TTS.

1. **La synthèse Sherpa-ONNX n'est PAS déterministe telle qu'elle est
   configurée.** `SherpaOnnxTtsEngine.kt:92-94` passe `noiseScale = 0.667`
   et `noiseScaleW = 0.8` (défauts Piper VITS) ; `tts.generate(text, sid,
   speed)` n'expose aucune graine. VITS échantillonne du bruit, y compris
   dans le prédicteur de durée : deux synthèses de la même phrase produisent
   un audio **et des durées** différents. Le document source affirme
   l'inverse, sans preuve.
   → **Conséquence non négociable : les timestamps ne sont jamais cachés
   sans leur audio.** Un timestamp rejoué sur une synthèse fraîche
   produirait un surlignage faux — exactement ce qu'ADR-021 interdit.
   Audio et timestamps forment une unité indivisible dans le cache, écrite
   et invalidée ensemble.
2. **Le pipeline anticipe déjà la synthèse.**
   `feature/reader/.../PlaybackOrchestrator.kt:614` :
   `Channel<AudioSegment>(LOOKAHEAD)` avec `LOOKAHEAD = 3` (ligne 946),
   producteur et consommateur séparés (acquis du Lot 15, gapless). En
   lecture linéaire, la synthèse des phrases n+1 à n+3 chevauche déjà la
   lecture de la phrase n : **la latence y est déjà masquée**, sauf au tout
   premier segment.
   → **Conséquence : le cache TTS persistant n'est pas « le levier majeur
   de latence » décrit par le document source.** Son gain réel, plus
   étroit mais mesurable, est le démarrage à froid (tap → premier audio),
   la reprise d'un livre et le retour en arrière. Le Lot est cadré sur ce
   gain-là, pas sur un gain imaginaire.

---

## Constat vérifié (base du Lot)

1. **Le découpage est payé deux fois, et jamais persisté.**
   `FrenchSentenceSplitter` est exécuté **à l'import** (index FTS,
   `ImportPublicationUseCase`) **puis re-exécuté à chaque ouverture**
   (`ReaderViewModel.loadChapterContentIfNeeded` → `EpubChapterParser.parseChapter`
   → `JsoupChapterParser.parse`). Le seul cache est un `LruCache` **mémoire**
   de 5 Mo dans `EpubChapterParser.kt`, vidé par `invalidate(publicationId)`
   à la fermeture du lecteur.
2. **Aucune entité de chapitre ni de phrase en base.** Vérifié :
   `infrastructure/database/.../entity/` contient 14 entités, aucune
   `ChapterEntity` ni `SentenceEntity` ; seuls `chapterCount`/`pageCount`
   (compteurs dans `PublicationEntity`) et `SentenceFtsEntity` (texte +
   `charOffset`, pour la recherche) survivent à l'import.
3. **Les timestamps par mot sont recalculés à chaque `synthesize()`.**
   Sherpa-ONNX → `CtcForcedAligner.align(...)` (**~1,6 s par phrase warm**,
   mesuré sur V2206 au Lot 20, pour ~1,1 s de synthèse) ; Android natif →
   `onRangeStart` ; Edge → `mapEdgeWordBoundaries`.
   `AudioSegment.wordTimestamps` ne survit qu'à la phrase courante
   (`PlaybackOrchestrator._currentWordTimestamps`). **Aucune entité Room ne
   stocke d'audio ni de timestamps** ; les usages de `cacheDir` sont des
   fichiers temporaires supprimés.
4. **Format audio du chemin de lecture : PCM16 mono.**
   `AudioSegment` (`domain/service/TtsEngine.kt`) documente l'hypothèse
   « PCM16 signé, mono » ; `GaplessAudioPlayer.kt:94` configure
   `ENCODING_PCM_16BIT`. À 22 050 Hz, cela fait **44 ko/s** : ~180 ko pour
   une phrase de 4 s, ~35 Mo pour un chapitre de 200 phrases.
   `infrastructure/tts/Mp3Decoder.kt` est déjà générique en pratique
   (`createDecoderByType` sur le format extrait) — la voie AAC/Opus est
   donc ouverte si un jour on la veut ; c'est l'encodeur qui manque, pas le
   décodeur.
5. **Pages PDF : cache mémoire de 5 pages, aucun pré-rendu, aucun cache
   disque.** `feature/reader/.../FixedPageContent.kt` :
   `BitmapCache(maxSize = 5)`, zoom par `graphicsLayer`, re-rasterisation
   au relâchement du geste (debounce).
6. **Types d'annotation incomplets.** `domain/model/Annotation.kt` n'a pas
   de champ de type ; `LibraryItemType { BOOKMARK, HIGHLIGHT, NOTE }` ne
   comporte ni souligné ni barré. Les seuls underline/strike du code sont
   des styles **sémantiques EPUB** (`SpanStyles.INSERTED/DELETED`), pas des
   annotations utilisateur.
7. **`BookmarkPanel` en lecture seule.** Onglets NOTES / HIGHLIGHTS /
   BOOKMARKS en affichage pur : ni édition de note, ni suppression.
8. **Palette de couleurs non mémorisée.** 5 valeurs `AnnotationColor`,
   aucun ordre d'usage persisté.


10. **Set d'abréviations de `FrenchSentenceSplitter` trop large.**
    `domain/service/FrenchSentenceSplitter.kt` contient des mots français
    pleins parmi ses abréviations (`mars`, `mai`, `juin`, `août`, `art`,
    `vol`, `p`, `t`, `n`, `ch`, `cit`…) : `"Il partit en mars. Puis il
    revint."` fusionne à tort en une seule phrase. Constaté lors de la
    correction du Lot 21 (le filtre y a été rendu opérant sur les sauts de
    ligne PDF/TXT — voir son commentaire de correctif — ce qui étend
    l'exposition à ces deux formats en plus de l'EPUB, où le défaut
    préexistait). Non corrigé volontairement : nettoyer la liste changerait
    le découpage de toute bibliothèque déjà importée (TTS et index FTS),
    décision à prendre en connaissance de cause, pas en aparté d'un
    correctif. `FrenchSentenceSplitterTest.kt` fige même la sur-fusion de
    `etc.` comme comportement attendu — à retrancher si la liste est
    resserrée.
11. **`FontFamily.valueOf` non défensif.** Trois sites
    (`data/mapper/UserPreferencesMapper.kt`, `data/backup/BackupModels.kt`,
    `data/mapper/CustomThemeMapper.kt`) appellent `FontFamily.valueOf(...)`
    sans repli : une sauvegarde ou un thème personnalisé portant une valeur
    d'enum ajoutée après coup (ex. `SOURCE_SERIF`, Lot 21) fait planter la
    restauration des préférences sur une installation qui ne connaît pas
    encore cette valeur (ancienne version de l'app, ou objet de sync
    produit ailleurs). Le principe « une valeur ajoutée ne se retire
    jamais » (Lot 21, `UserPreferences.kt`) réduit le risque sans
    l'éliminer : la traversée version-descendante reste possible via
    sauvegarde/sync.
12. **`dispatchRawDelta` de l'auto-scroll court-circuite la transition de
    chapitre.** `feature/reader/.../ReaderScreen.kt`, bloc auto-scroll
    (Lot 21, tâche 9) : en bas de chapitre, `!scrollState.canScrollForward`
    arrête simplement l'auto-scroll au lieu de déclencher
    `chapterTransitionConnection` (le nested-scroll qui fait normalement
    avancer au chapitre suivant). Comportement peut-être voulu (un
    « défilement automatique » qui ne franchit jamais de limite de
    chapitre sans intervention), mais non tranché explicitement dans le
    Lot 21 — à décider avant d'y toucher.
13. **Chemin de repli de `FrenchSentenceSplitter.split` hors contrat
    d'offsets.** Quand `BreakIterator` ne produit aucune frontière
    (`rawBoundaries.isEmpty()`), la fonction retourne des offsets dans
    l'espace du texte **trimmé**, pas du texte source — contredit son
    propre contrat de stabilité des offsets (KDoc de tête). Code mort en
    pratique aujourd'hui (`BreakIterator` produit toujours au moins une
    frontière sur un texte non vide), mais un piège latent si ce chemin
    redevient atteignable.

---

## Décisions arrêtées

Tranchées avant exécution (2026-08-26). Chacune ferme une alternative
réelle : ne pas la rouvrir en cours de lot sans fait nouveau.

1. **Pré-analyse persistée : fichier sérialisé par publication, pas Room.**
   Versionné (numéro de version du format + du parseur), clé = le
   `fileHash` déjà calculé à l'import (`computeSha256`). Les blocs de
   chapitre sont des blobs lus séquentiellement, jamais interrogés : Room
   n'apporte rien et ferait payer une migration à chaque évolution du
   parseur, là qu'un fichier se régénère en incrémentant un numéro. La
   recherche reste couverte par `SentenceFtsEntity`.
   **Prix à payer, assumé** : on perd le `ON DELETE CASCADE` de Room — la
   purge à la suppression d'une publication devient du code, donc un test
   explicite. C'est un critère de sortie, pas un détail.
2. **Cache audio : PCM16 brut, avec un champ de format versionné.** Le
   chemin de lecture est PCM16 mono : le brut ne coûte aucun décodage. La
   compression (AAC/Opus) ne serait nécessaire que pour viser le livre
   entier, ce que la décision 4 écarte. Le champ de format est là pour que
   basculer plus tard n'invalide pas la conception.
3. **Plafond ~200 Mo global, LRU par publication, purge à la suppression du
   livre.** Ordre de grandeur assumé : 200 Mo ≈ quelques chapitres, pas un
   livre — suffisant compte tenu de la décision 4.
4. **Cache à la demande, aucune pré-synthèse de fond.** Le `LOOKAHEAD = 3`
   fait déjà ce travail pendant la lecture ; une pré-synthèse de fond
   brûlerait CPU, batterie et disque pour supprimer une latence déjà
   supprimée. **Une exception, et c'est le cœur du gain : le point de
   reprise de chaque livre reste épinglé en cache**, pour que le tap sur
   « reprendre » démarre sans synthèse.
5. **Conflits de position : arbitrage automatique seulement si un seul
   appareil a bougé** depuis la dernière synchro réussie ; sinon on demande
   via `SyncConflictBottomSheet`. La règle « position la plus avancée » est
   écartée : relire un chapitre en arrière est un usage normal, et cette
   règle effacerait l'intention. La synchro de fond continue de ne jamais
   trancher elle-même (KDoc de `PositionConflict`) : l'arbitrage a lieu à
   l'ouverture de l'app.
6. **Sessions distantes orphelines : ignorées.** `ReadingSessionEntity`
   porte une clé étrangère `CASCADE` vers `publications`, or les livres ne
   sont pas synchronisés : une session venue d'un appareil pour un livre
   absent en local **ferait échouer l'insertion**. Elles sont ignorées, pas
   mises en file — les statistiques d'un livre qu'on ne possède pas ne sont
   pas exploitables, et une file d'attente serait de l'état à maintenir
   indéfiniment. Comportement à journaliser, jamais silencieux.

---

## Tâches

### Palier A — Pré-analyse persistée (ouverture instantanée)

1. **Matérialiser chapitres et phrases à l'import**, en fichier sérialisé
   par publication (décision 1) : chapitres (`index`, `href`, `title`,
   blocs) et phrases (offsets par chapitre), avec en-tête portant la
   version du format, la version du parseur et le `fileHash` de la source.
   Le cache se construit **à partir du `Chapter` déjà parsé** —
   interdiction d'ouvrir un second accès ZIP (K2 :
   `ReadiumPublicationRegistry.getOrOpen` reste le point d'entrée unique).
   Commit : `Persiste les chapitres et les phrases a l'import`.
2. **Lire le cache à l'ouverture.** `ReaderViewModel.loadChapterContentIfNeeded`
   / `EpubChapterParser` consomment la version persistée quand elle existe
   et ne retombent sur le parsing Jsoup qu'en son absence.
   Commit : `Ouvre un chapitre depuis la pre-analyse persistee`.
3. **Invalidation, cohérence et purge.** Le cache est invalidé si le
   `fileHash` de la source change, si la version du parseur ou du format
   change, ou au re-import ; jamais servi pour une source différente. La
   **purge à la suppression d'une publication est écrite et testée** (elle
   n'est plus offerte par Room — décision 1).
   Commit : `Invalide et purge la pre-analyse persistee`.

### Palier B — Cache de reprise TTS (audio + timestamps, indivisibles)

4. **Stockage du cache de synthèse.** Clé = hash de
   `(publicationId, chapterIndex, sentenceOffset, voiceProfileId, hash des
   règles de prononciation, version du moteur)`. Valeur = **audio PCM16 et
   `wordTimestamps` réels, écrits ensemble** (correction 1 : la synthèse
   VITS n'est pas reproductible, un timestamp sans son audio est un
   surlignage faux). **ADR-021 : on cache du calcul réel
   (`CtcForcedAligner.align`), jamais de l'interpolation** ; le surlignage
   mot-à-mot ne s'active toujours que si `TtsCapabilities.wordTimestamps`
   est vrai.
   Commit : `Ajoute un cache persistant des segments TTS et de leurs timestamps`.
5. **Brancher le cache sur `PlaybackOrchestrator`** : consultation avant
   synthèse dans le producteur, écriture après. Ne pas toucher au
   `LOOKAHEAD = 3` ni à la séparation producteur/consommateur — acquis du
   Lot 15, gapless vérifié sur device.
   Commit : `Sert les segments TTS depuis le cache avant toute synthese`.
6. **Épingler le point de reprise** (décision 4) : le segment de la
   position de reprise de chaque livre survit à la purge LRU, pour que le
   tap sur « reprendre » démarre sans synthèse. C'est le gain principal du
   palier, donc ce qui doit être mesuré.
   Commit : `Epingle le segment de reprise de chaque livre dans le cache`.
7. **Plafond et invalidation.** LRU par publication, plafond ~200 Mo
   (décision 3), purge à la suppression du livre ; invalidation sur
   changement de voix, de règles de prononciation ou de texte source.
   Tests d'invalidation obligatoires — un cache TTS périmé produit un
   surlignage faux, c'est-à-dire précisément ce qu'ADR-021 interdit.
   Commit : `Plafonne et invalide le cache TTS`.

### Palier C — Pages PDF (fluidité)

8. **Pré-rendu des pages adjacentes** pendant l'idle et `BitmapCache`
   agrandi, pour supprimer le blanc au swipe.
   Commit : `Pre-rend les pages PDF adjacentes`.
9. **Cache disque des pages rendues** (clé `publicationId` + `pageIndex` +
   résolution), purgé avec la publication.
   Commit : `Ajoute un cache disque des pages PDF rendues`.
   *(Rappel du document source : le double pipeline CPU/OpenGL de Moon+ est
   surdimensionné pour InkTone et reste hors périmètre — PDFium CPU +
   transform GPU Compose est le design retenu.)*

### Palier D — Parité des annotations

10. **`AnnotationKind { HIGHLIGHT, UNDERLINE, STRIKETHROUGH }`** — champ de
    type sur `Annotation`, migration Room (valeur par défaut `HIGHLIGHT`
    pour l'existant, aucune perte), rendu via `TextDecoration` **sans
    mélanger les canaux visuels** : annotation, surlignage TTS
    (`WordHighlightColor`) et sélection (`SelectionHighlightColor`) restent
    trois canaux séparés — c'est précisément ce qu'InkTone fait mieux que
    Moon+, ne pas régresser. Le `Locator` n'est pas touché.
    Commit : `Ajoute les annotations soulignees et barrees`.
11. **Édition depuis `BookmarkPanel`** — modification de note et
    suppression depuis les trois onglets.
    Commit : `Permet d'editer et de supprimer depuis le panneau des notes`.
12. **Couleurs récemment utilisées** persistées et proposées en tête du
    sélecteur (migration additive + test).
    Commit : `Memorise les couleurs de surlignage recemment utilisees`.

### Palier E — Complétion de la synchronisation

13. **Fusion des `readingSessions`** dans `SyncNowManager.mergeRemoteSnapshots` :
    **union par clé primaire** (`ReadingSessionEntity.id`, `String` stable),
    donc idempotente par construction — pas d'agrégation à écrire. Les
    sessions dont la publication est absente en local sont **ignorées et
    journalisées** (décision 6), jamais insérées au risque de violer la clé
    étrangère. Ne jamais conflater `ReadingState` (position, un par
    publication) et `ReadingSession` (historique, plusieurs) — règle
    d'architecture non négociable.
    Commit : `Fusionne les sessions de lecture distantes par identifiant`.
14. **Arbitrage des conflits de position** (décision 5) : automatique quand
    un seul appareil a bougé depuis la dernière synchro réussie ; sinon
    choix explicite dans `SyncConflictBottomSheet`. La file
    `PendingConflictEntity` doit pouvoir se vider dans les deux cas.
    Commit : `Tranche les conflits de position au lieu de les empiler`.

---

## Ce qu'on ne fait pas dans ce Lot

- **Pré-synthèse de fond du livre entier** (décision 4) — redondante avec
  le `LOOKAHEAD = 3` existant.
- **Compression du cache audio** (décision 2) — le décodeur générique
  existe déjà si l'on change d'avis ; l'encodeur reste à écrire, hors
  périmètre.
- Moteur de césure à motifs TeX (conditionné à une mesure, voir Lot 21).
- Double pipeline de rendu CPU/OpenGL pour le PDF (anti-pattern assumé).
- Types d'annotation au-delà des trois retenus.
- Toute forme d'interpolation de timestamps pour combler un cache manquant
  (ADR-021).

---

## Points de vigilance (non négociables)

- **Audio et timestamps sont indivisibles** dans le cache (correction 1).
  Aucun chemin ne doit pouvoir servir des timestamps issus d'une autre
  synthèse.
- **K2** : aucun nouveau chemin de ce Lot n'ouvre un second accès ZIP.
- **K4** : chaque migration a son test `MigrationTestHelper` dans le même
  commit ; aucun `fallbackToDestructiveMigration`.
- **Espace disque** : audio et pages rendues sont volumineux ; plafonds et
  purge à la suppression du livre font partie de la définition de
  « terminé », pas d'un suivi ultérieur — d'autant que la pré-analyse en
  fichiers ne bénéficie plus du `CASCADE` de Room (décision 1).
- **Locator unique** : les caches sont indexés par `Locator`/offsets
  existants, jamais par un nouveau système d'adressage.

---

## Critères de sortie du Lot

- [ ] Ouverture d'un livre déjà importé : aucun re-parsing Jsoup ni
      re-`BreakIterator` (prouvé par trace ou test, pas par ressenti).
- [ ] Ouverture d'un gros EPUB mesurée avant/après sur device, chiffres
      consignés dans ce document.
- [ ] Suppression d'une publication : sa pré-analyse **et** son cache TTS
      disparaissent du disque (test explicite — le `CASCADE` de Room ne
      couvre plus la pré-analyse).
- [ ] Reprise d'un livre : le tap sur « reprendre » démarre **sans
      synthèse** (segment épinglé) ; latence tap → premier audio mesurée
      avant/après sur device et confrontée au budget Blueprint §11.2
      (1 500 ms).
- [ ] Une phrase déjà synthétisée est rejouée depuis le cache avec **ses**
      timestamps, jamais avec ceux d'une autre synthèse.
- [ ] Changement de voix ou de règle de prononciation : cache invalidé,
      surlignage toujours exact (test automatisé).
- [ ] Plafond de ~200 Mo respecté sous lecture longue (vérifié, pas supposé).
- [ ] Lecture linéaire toujours gapless après branchement du cache (le
      `LOOKAHEAD = 3` du Lot 15 n'a pas régressé).
- [ ] Swipe PDF sans page blanche sur device ; reprise instantanée après
      réouverture.
- [ ] Souligné et barré créables, rendus, persistés, synchronisés ;
      annotations existantes intactes après migration (test de migration).
- [ ] Note éditable et annotation supprimable depuis `BookmarkPanel`.
- [ ] Couleurs récentes proposées en tête du sélecteur.
- [ ] `readingSessions` fusionnées sans doublon après deux synchronisations
      consécutives sur deux appareils ; session orpheline ignorée et
      journalisée, aucune violation de clé étrangère.
- [ ] La file `PendingConflictEntity` se vide après arbitrage.
- [ ] `./gradlew build` vert.
