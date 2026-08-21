# Plan de polissage UX/UI — principe de Pareto (post-v1.0.0)

> **Point de vue adopté :** celui d'un utilisateur qui veut de la fluidité,
> une interface premium, un confort de lecture visuelle réel, et surtout un
> **contrôle du TTS depuis la barre de notification**.
>
> **Règle de ce plan (CLAUDE.md §« le code fait foi ») :** chaque constat
> ci-dessous cite le fichier et la ligne qui le prouvent. Aucun item n'est
> déduit d'un document de statut. Date de l'audit : 2026-08-21, sur `main`
> à `0dbe2e92`.

---

## 1. Verdict de l'audit — où sont réellement les 20 %

Contre-intuitivement, **le polissage visuel n'est plus le gisement principal**.
Vérifié dans le code :

| Ce qu'on aurait pu croire à polir | État réel constaté |
|---|---|
| Surlignage mot-à-mot qui saccade | Déjà dessiné en phase `drawWithContent`, jamais en mesure/placement — `PagedChapterContent.kt:708-713` |
| Grille bibliothèque qui « pop » | Shimmer de chargement (`LibraryScreen.kt:226`) + Coil `crossfade` (`BookCover.kt:120-123`) |
| Transitions d'écran sèches | SharedTransition couverture→lecteur (`ReaderScreen.kt:309-317`), ressorts sur le tirage de chapitre (`PagedChapterContent.kt:314-316`) |
| Accessibilité mouvement | `ReducedMotion.kt` existe et est respecté |

Le gisement réel se concentre sur **quatre trous structurels**, dont un seul
pèse à lui seul la moitié de l'expérience perçue.

---

## 2. Les lots, classés par ratio impact / effort

### P1 — Session média réelle : notification, écran verrouillé, survie en arrière-plan
**Impact : ~45 % du ressenti. Effort : le plus gros du plan, et il le vaut.**

C'est le constat central de l'audit. Aujourd'hui, **le TTS ne survit pas
correctement à l'arrière-plan et n'offre aucun contrôle hors de l'écran de
lecture** :

- `AudioPlaybackService` (`infrastructure/media/.../AudioPlaybackService.kt:24-56`)
  construit bien une `MediaSession` sur un `ExoPlayer`… mais **rien ne s'y
  connecte jamais**. La lecture réelle passe par `GaplessAudioPlayer`
  (`AudioTrack MODE_STREAM`, ADR-025), qui ignore totalement ce service.
  Le service est déclaré au manifeste (`AndroidManifest.xml:58-66`,
  `foregroundServiceType="mediaPlayback"`) mais **aucun `startForeground`
  n'existe dans le dépôt** — grep sur tout l'arbre : zéro occurrence.
  Conséquence : le process est tuable par le système en pleine écoute.
- **Aucune permission `POST_NOTIFICATIONS`** au manifeste → aucune
  notification possible, donc aucune commande depuis la barre d'état ni
  depuis l'écran verrouillé.
- **Aucune gestion de focus audio** : `GaplessAudioPlayer.kt:97-112`
  construit l'`AudioTrack` sans `AudioFocusRequest`. Un appel entrant, une
  notification sonore ou une autre app ne font ni baisser ni pauser InkTone —
  et le débranchement du casque (`ACTION_AUDIO_BECOMING_NOISY`) n'est pas
  écouté non plus : la lecture continue à voix haute dans le haut-parleur.
- **Quitter l'écran de lecture coupe la voix** :
  `ReaderViewModel.onCleared()` appelle `playbackOrchestrator.stop()`
  (`ReaderViewModel.kt:1166-1173`). Impossible d'écouter en consultant sa
  bibliothèque.
- **Effet de bord statistique** : `onAppBackground()`
  (`ReaderViewModel.kt:1198-1201`) met le `sessionTracker` en pause
  inconditionnellement, y compris pendant une écoute TTS active. Le temps
  d'écoute écran éteint n'est donc **pas comptabilisé** dans les statistiques.

**Travail :**
1. Faire de la session média la façade unique du TTS : `MediaSession` +
   `MediaSessionService` en foreground service, pilotant `PlaybackOrchestrator`
   plutôt qu'un `ExoPlayer` fantôme. `AudioPlaybackService` est réécrit ou
   supprimé — il ne doit pas rester un troisième chemin de lecture (K3 :
   source de vérité unique).
2. Notification `MediaStyle` : couverture, titre, chapitre, actions
   **Pause/Lecture, phrase précédente/suivante, −30 s / +30 s, Stop**, et une
   action « minuteur de sommeil » (le `SleepTimerPanel` existe déjà côté UI).
   Demander `POST_NOTIFICATIONS` au premier démarrage d'une lecture, pas au
   lancement de l'app.
3. `AudioFocusRequest` + `BecomingNoisy` : duck sur focus transitoire, pause
   sur perte durable, reprise sur regain. Test instrumenté obligatoire.
4. Corriger `onAppBackground()` : ne pauser le tracker que si
   `!_state.value.isPlaying`, sinon basculer explicitement en
   `ReadingMode.AUDIO`.
5. Ne plus couper la voix sur `onCleared()` quand la session média détient
   la lecture.

**Critères de sortie (vérifiés sur device, pas en unitaire) :** écran
verrouillé 20 min, lecture ininterrompue ; commandes de la notification et
de l'écran verrouillé fonctionnelles ; appel entrant → pause puis reprise ;
casque débranché → pause immédiate ; le temps d'écoute apparaît dans les
statistiques.

---

### P2 — Continuité de l'écoute dans l'app : mini-lecteur persistant
**Impact : ~15 %. Effort : moyen. Dépend de P1.**

Une fois P1 en place, l'écoute survit hors du lecteur — il faut alors la
rendre visible et pilotable partout : barre persistante en bas de la
bibliothèque, des recherches, des réglages, avec couverture, titre, position
et pause/reprise, et retour au lecteur exactement à la position lue.

Note : `feature:player` (`PlayerScreen.kt`, `PlayerViewModel.kt`) est du
**code mort** — aucune route ne le référence dans `InkToneNavHost.kt`. Soit
il devient la base de ce mini-lecteur, soit il est supprimé dans le même lot.
Un module fantôme qui parle d'un service fantôme entretient exactement le
biais que CLAUDE.md interdit.

---

### P3 — Fluidité perçue au démarrage : profil de référence et build de release honnête
**Impact : ~20 %. Effort : faible — meilleur ratio du plan.**

- **Aucun Baseline Profile** dans le dépôt : pas de `profileinstaller` ni de
  plugin `baselineprofile` dans `gradle/libs.versions.toml`, et `benchmark/`
  ne contient qu'`EpubOpenBenchmark.kt`. Sur un Snapdragon 680 (cible
  matérielle du Blueprint), c'est typiquement **20 à 30 % de temps de
  démarrage et de première frame** laissés sur la table, sans écrire une
  ligne d'UI.
- Le module `benchmark` mesure encore sur un build `debug` avec
  `suppressErrors = DEBUGGABLE` (`benchmark/build.gradle.kts:13-21`) : les
  chiffres actuels ne veulent rien dire.
- R8 est volontairement désactivé (`InkToneApplicationConventionPlugin.kt:74-86`,
  décision actée dans `AUDIT_CONSOLIDATION_V1.md` §4.2). **Ce plan ne
  rouvre pas cette décision** — mais un build type `benchmark` non
  débogable est le préalable qui permettra de la trancher sur mesure.

**Travail :** build type `benchmark` (non debuggable, `matchingFallbacks`
release) → générateur de Baseline Profile couvrant démarrage → bibliothèque →
ouverture d'un EPUB → première page paginée → démarrage TTS →
`profileinstaller` en dépendance de `app` → macrobenchmark de démarrage à
froid avant/après, chiffres consignés.

---

### P4 — Confort de lecture visuelle : les réglages typographiques manquants
**Impact : ~15 %. Effort : faible à moyen.**

`ReaderSettingsPanel.kt` n'expose aujourd'hui que **deux** leviers : taille
de police et interligne (`ReaderSettingsPanel.kt:98-103`). Pour un lecteur
qui se veut premium et francophone-first, il manque les réglages que tout
lecteur sérieux offre :

1. **Marges latérales** (3 crans) — le plus demandé, le plus visible.
2. **Justification + césure** — en français, justifier sans césure produit
   des « rivières » blanches ; les deux vont ensemble, jamais l'un sans
   l'autre.
3. **Espacement des paragraphes** et **famille de police** (2-3 polices
   embarquées, dont une adaptée à la dyslexie).
4. **Garder l'écran allumé** pendant la lecture visuelle — `FLAG_KEEP_SCREEN_ON`
   n'apparaît nulle part dans le dépôt ; l'écran s'éteint en pleine page.

**Point d'attention technique :** chacun de ces réglages invalide la mesure
de pagination. Ils doivent passer par la même invalidation que la taille de
police (`ChapterTextMeasurer` / `VirtualPaginationEngine`), et la position de
lecture doit être préservée à travers la re-pagination via `Locator` — jamais
via un index de page (règle d'adressage, CLAUDE.md).

---

### P5 — Micro-polish premium : le vernis qui se remarque sans se voir
**Impact : ~5 %. Effort : faible. À faire en dernier, jamais en premier.**

- **Haptique cohérente** : seulement 8 occurrences de retour haptique dans
  tout le code de production. Définir une échelle (tick au changement de
  page, confirmation au signet, rejet en fin de chapitre) et l'appliquer
  uniformément — le retour tactile est ce qui distingue le plus nettement
  une app premium d'une app correcte.
- **Tokens de motion** dans `core:designsystem` : les durées et courbes sont
  aujourd'hui écrites en dur dans les écrans (`tween(fadeDuration)`,
  ressorts locaux). Les centraliser à côté de `Spacing.kt` et `Shape.kt`,
  et les faire respecter `ReducedMotion` par construction.
- **Transition de page vers la notification** : la couverture affichée dans
  la notification média doit être la même bitmap que celle du lecteur
  (cache Coil partagé), pas une seconde décompression.

---

## 3. Ce que ce plan ne fait délibérément pas

- **Ne rouvre pas la décision R8/minify** (`AUDIT_CONSOLIDATION_V1.md` §4.2) :
  P3 en pose seulement le préalable mesurable.
- **Ne touche pas au moteur de pagination ni au pipeline gapless** : les deux
  fonctionnent et sont couverts de tests ; P4 les sollicite mais ne les
  réécrit pas.
- **Ne refait pas la charte visuelle** : palette, typographie et iconographie
  sont stabilisées (PLAN_2A/2B/2C) — les retoucher serait de l'agitation, pas
  du Pareto.
- **N'ajoute aucun écran nouveau** hormis le mini-lecteur de P2.

---

## 4. Ordre d'exécution recommandé

`P3` → `P1` → `P2` → `P4` → `P5`

P3 passe en premier bien qu'il pèse moins que P1 : il est peu coûteux, sans
risque de régression, et il installe l'instrumentation qui permettra de
**prouver** que P1 n'a pas dégradé le démarrage. P4 et P5 sont indépendants
et peuvent être intercalés si le temps manque pour P1/P2.

## 5. Indicateurs de succès, mesurés et non déclaratifs

| Indicateur | Avant (mesuré) | Cible |
|---|---|---|
| Démarrage à froid → bibliothèque interactive | à mesurer (P3, build benchmark) | −25 % |
| Écoute écran verrouillé sans interruption | non garantie (process tuable) | 60 min continues |
| Actions TTS accessibles hors du lecteur | 0 | 6 (notification) |
| Réglages typographiques exposés | 2 | 6 |
| Temps d'écoute en arrière-plan compté en statistiques | non | oui |

---

## 6. Journal d'exécution

Ordre retenu : `P3` → `P1` → `P2` → `P4` → `P5` (voir §4). Chaque entrée cite
le commit ou l'état du code qui la prouve (CLAUDE.md §« le code fait foi »).

### 6.1 — P3, fondation « build honnête » (en cours)

- **Build type `benchmark` non debuggable** ajouté à `:app`
  (`InkToneApplicationConventionPlugin.kt`, bloc `buildTypes`) :
  `isDebuggable = false`, `matchingFallbacks += listOf("release")`, signé
  release si `keystore.properties` présent. C'est le préalable qui lève le
  `suppressErrors = DEBUGGABLE` du module benchmark.
- **Module `benchmark`** : `suppressErrors = DEBUGGABLE` retiré, build type
  `benchmark` non debuggable ajouté, aligné sur `:app` via
  `targetProjectPath` (`benchmark/build.gradle.kts`).
- **`profileinstaller`** ajouté en dépendance de `:app` + entrées du catalogue
  de versions (`gradle/libs.versions.toml` : `profileInstaller = "1.3.1"`,
  `androidx-profileinstaller`).
- **Restant pour clore P3** : le générateur de Baseline Profile
  (`androidx.baselineprofile` côté module générateur + `…gradle.producer` côté
  `:app` + `BaselineProfileGenerator` couvrant démarrage → bibliothèque →
  ouverture EPUB → première page → démarrage TTS), puis le macrobenchmark de
  démarrage à froid avant/après, chiffres consignés dans §5. La génération
  s'exécute **sur device** (hors portée d'une session sans appareil).

### 6.2 — P1, correctif du tracker de session (fait)

- **`onAppBackground()`** ne pause plus le tracker quand `isPlaying` est vrai
  (`ReaderViewModel.kt`) : le temps d'écoute écran éteint reste imputé au mode
  AUDIO au lieu d'être figé. Garde-fou de non-régression :
  `ReaderViewModelBackgroundTrackerTest.kt` (deux cas : sans écoute → pausé,
  pendant l'écoute → actif). Commit `37fbefd1`.

### 6.3 — P1, session média

Paliers **a** (contrat + ordonnanceur) et **b** (service + notification) livrés ;
**c/d** restants :

- **(a) fait** — contrat domaine `PlaybackSession` (`domain/service/PlaybackSession.kt`)
  + `PlaybackOrchestrator` l'implémente : rétention du contexte de session,
  `skip(delta)`, `togglePlayPause()` (pause **réelle** `pause()`/`resume()`, pas
  `stop()`), `metadata`/`setMetadata`, `isPlaying` et `sessionState` dérivés de
  `state` (flux `stateIn`, jamais un second drapeau). Liaison Hilt
  `PlaybackSessionModule` (`feature/reader/di`). Collecteur du `ReaderViewModel`
  synchronisé : `Paused` → `isPlaying=false`, `Playing` → `isPlaying=true`.
  Tests JVM (`PlaybackOrchestratorTest`). Commit `ebc8d83e`.
- **(b) fait** — `AudioPlaybackService` réécrit en vrai service foreground
  (`android.app.Service` + `MediaSession` système + notification `MediaStyle`),
  pilotant `PlaybackSession` — plus de `MediaSessionService`/`ExoPlayer` fantôme.
  `PlaybackServiceLauncher` (`@Singleton`) observe `sessionState` et démarre/
  arrête le service (découplé du `ReaderViewModel`, activé depuis
  `InkToneApplication`). Manifest : `POST_NOTIFICATIONS` + déclaration de service
  nettoyée. Écart déclaré : la demande de `POST_NOTIFICATIONS` à l'exécution
  (Android 13+) n'est pas encore câblée (concerne l'UI/Activity), et les
  dépendances `media3` d'`infrastructure/media` deviennent inutilisées (nettoyage
  avec P2, suppression de `feature/player`).
- **(c) fait** — focus audio et débranchement de casque. `AudioInterruptionPolicy`
  (pur JVM, modèle `GaplessPlaybackCore`/`GaplessAudioPlayer`) porte les règles :
  jamais d'atténuation d'une voix (`CAN_DUCK` traité comme perte transitoire),
  reprise automatique **seulement** après une perte transitoire, jamais après un
  casque débranché ni une perte définitive, pause différée quand l'interruption
  tombe pendant la synthèse. `AudioFocusController` est la couche I/O
  `AudioManager` (`AudioFocusRequest` GAIN, `USAGE_MEDIA`/`CONTENT_TYPE_SPEECH`,
  `setWillPauseWhenDucked`, receiver `BECOMING_NOISY`), possédée par le service —
  seul demandeur de focus de l'app (K3). `PlaybackSession` gagne `pause()`/
  `resume()` explicites (une interruption externe ne peut pas passer par
  `togglePlayPause`, qui relancerait la narration). Tests JVM :
  `AudioInterruptionPolicyTest` (10 cas). Commit `c4b401aa`.
- **(d) fait** — `ReaderViewModel.onCleared()` ne coupe plus la narration quand
  une session est engagée (`PlaybackOrchestrator.isSessionEngaged()`, lu sur
  l'état interne et non sur le `stateIn` dérivé, périmé d'un tick). Quitter le
  Lecteur laisse la voix continuer, pilotée par la notification. Commit `43d3caa8`.
- **Signal `chapterCompleted` fait** — l'auto-avance ne se déduit plus de « `Idle`
  alors que la lecture était engagée » (qui faisait sauter un chapitre quand la
  notification demandait une pause pendant la synthèse). `PlaybackOrchestrator`
  émet un `SharedFlow` depuis le seul chemin qui constate la fin réelle. La limite
  connue de §6.4 est donc levée. Commit `69e32573`.
- **`POST_NOTIFICATIONS` fait** — écart de (b) refermé :
  `rememberTtsNotificationPermissionRequest` demande la permission en contexte,
  au premier démarrage d'une narration ; un refus ne coûte que le contrôle depuis
  le volet, jamais la lecture. Les trois points lecture/pause de `ReaderScreen`
  passent par un unique `togglePlayback`. Commit `20bda23e`.
- **Action « Arrêter » faite** — `ACTION_STOP` était géré mais inatteignable :
  quatrième action de la vue déployée + `deleteIntent` (balayer la notification
  arrête réellement). Commit `39a83eec`.

**Écarts déclarés à l'issue de P1** (tous relèvent de P2, mini-lecteur
persistant, qui déplacera la propriété de la session hors du `ReaderViewModel`) :

1. **L'auto-avance de chapitre s'arrête** quand l'écran Lecteur est détruit :
   le collecteur `chapterCompleted` vit dans le ViewModel. La narration continue
   donc jusqu'à la fin du chapitre en cours, puis s'arrête.
2. **Le tracker de statistiques cesse de compter** dans le même cas — le
   correctif §6.2 ne couvre que l'app en arrière-plan, pas l'écran détruit.
3. **Pas de `−30 s`/`+30 s`** dans la notification : la narration est adressée
   par phrase, pas par temps. Les actions « phrase précédente/suivante » en sont
   l'équivalent exact ; un saut temporel exigerait un index temps→phrase qui
   n'existe pas. Non simulé, conformément à K12 (jamais de compensation aval).
4. **Pas d'action « minuteur de sommeil »** dans la notification : le minuteur
   vit dans le `ReaderViewModel` (`sleepTimerJob`), pas dans la session. Son
   déplacement appartient à P2.
5. **Vérification device non faite** — focus audio, casque débranché et survie
   écran verrouillé ne se prouvent pas en JVM ; la clôture du palier revient à
   Issa (checklist ci-dessous).

### 6.5 — P2, mini-lecteur persistant

- **(a) fait** — `feature:player` (code mort : aucun module n'en dépendait,
  aucune route ne référençait `PlayerScreen`) est **remplacé** par le
  mini-lecteur. `MiniPlayerViewModel` consomme directement `PlaybackSession`
  via Hilt : plus de `MediaController`, plus de `ComponentName` reconstruit
  depuis un nom de classe en chaîne. `MiniPlayerContent` est sans état donc
  testable. `InkToneNavHost` l'affiche en bande sous le contenu (jamais en
  flottant, qui masquerait les actions ancrées en bas), masqué sur le Lecteur
  et l'onboarding ; l'appui ramène au livre réellement narré
  (`PlaybackMetadata.publicationId`). Tests : `MiniPlayerUiStateTest` (JVM),
  `MiniPlayerContentTest` (Compose, 4 cas). Commit `94325623`.
- **(b) fait** — la propriété de la session quitte le `ReaderViewModel`. Deux
  commits, un par écart refermé :
  - **Auto-avance (écart 1)** — `PlaybackOrchestrator` porte un
    `NarrationProgram` (les hrefs des chapitres, jamais leurs phrases) et
    obtient lui-même le chapitre suivant de `ChapterParser`. L'écran **suit**
    désormais `currentChapterIndex` au lieu de le piloter
    (`syncDisplayToNarratedChapter` : ne coupe pas la lecture, ne la relance
    pas, ne persiste pas la position — l'ordonnanceur en reste seul écrivain
    pendant le TTS, K3). Passage en `Buffering` avant le parsing, pour ne pas
    faire clignoter la notification. Corrige au passage une libération qui
    aurait vidé le palier de son sens : `onCleared()` fermait le résolveur EPUB
    et invalidait le cache du parseur, donc l'auto-avance aurait échoué au
    premier chapitre suivant ; ces libérations sont différées à la fin réelle
    de session (`releaseOnSessionEnd`) et annulées si un Lecteur rouvre le
    livre. Tests : `PlaybackOrchestratorChapterAdvanceTest` (4 cas). Commit
    `24016a0a`.
  - **Statistiques (écart 2)** — `NarrationSessionContinuation` (@Singleton)
    prend le relais du `ReadingSessionTracker`, qui n'est jamais partagé mais
    **transmis** : l'écran le cède en mourant, un écran rouvert sur le même
    livre le reprend (`takeOver`), deux trackers concurrents produiraient des
    fragments chevauchants. Mode forcé à AUDIO (sans écran, aucune lecture
    visuelle à imputer) ; `lastFragmentSavedMs` transmis dans les deux sens.
    Tests : `NarrationSessionContinuationTest` (4 cas, horloge injectée).
    Commit `8f397215`.
- **Écarts 3 et 4 de P1 : état inchangé.** Le saut ±30 s reste refusé (pas
  d'index temps→phrase, K12). L'action « minuteur de sommeil » dans la
  notification reste à faire : le minuteur vit toujours dans le
  `ReaderViewModel` (`sleepTimerJob`) et meurt donc avec l'écran — désormais
  le seul élément de session encore attaché à l'écran.
- **Vérification device restant à faire pour (b)** : quitter le Lecteur en
  pleine narration, laisser passer une fin de chapitre, vérifier que la voix
  enchaîne le chapitre suivant ; puis que le temps écoulé apparaît bien en
  écoute dans les statistiques du livre.
- **Défaut de test corrigé au passage** :
  `PlaybackOrchestratorTest.togglePlayPause_bascule_entre_pause_et_reprise`
  affirmait `isPlaying` (un `stateIn`) immédiatement après avoir attendu
  `state` — vert ou rouge selon l'ordonnancement. Observé rouge sur une
  exécution complète, corrigé en attendant la valeur dérivée.

### 6.4 — P1, décision d'architecture actée (rappel)

- **Sémantique pause ≠ stop.** Le lecteur pause par `stop()` (Idle) ; la
  notification pausera par `pause()` (réel). Le collecteur traite `Paused` en
  `isPlaying=false` (désormais branché).
- **Limite connue (à lever en c)** : `togglePlayPause` sur `Buffering` passe par
  `stop()` → le collecteur interprète « Idle alors que isPlaying » comme une fin
  de chapitre — un signal dédié `chapterCompleted` (plutôt que l'heuristique
  Idle) lèvera l'ambiguïté sans auto-avance parasite. Documenté dans le code
  (`PlaybackOrchestrator.togglePlayPause`).

### 6.6 — P4, confort de lecture visuelle

Le panneau n'exposait que deux leviers (taille, interligne). Trois des quatre
réglages prévus sont livrés ; un quatrième était déjà fait, un cinquième est
refusé en l'état.

- **Marges latérales (fait)** — trois crans (`readerMarginFor`), le cran par
  défaut valant exactement l'ancienne valeur en dur (16 dp) : aucune
  bibliothèque existante ne change d'apparence. Une SEULE valeur alimente la
  mesure de pagination et le rendu de `PagedChapterContent`, dont le padding
  était écrit en dur — deux sources auraient mesuré une page plus large que
  celle dessinée, donc fait déborder le texte.
- **Justification + césure (fait)** — posée sur `pagination.baseTextStyle`,
  d'où le mode paginé tire déjà son style de rendu : mesure et affichage ne
  peuvent pas diverger. Le mode SCROLL applique les mêmes règles. La césure
  déplaçant les coupures de ligne, `PaginationStyleKey` gagne `justified` —
  sans quoi la pagination resterait calculée sur l'ancien style sans signal,
  exactement le piège déjà documenté pour `lineHeight`/`fontFamily`.
- **Garder l'écran allumé (fait)** — `KeepScreenOnEffect`, en
  `DisposableEffect` : le maintien meurt avec l'écran de lecture, jamais un
  `addFlags` sans retrait qui viderait la batterie sur un autre écran.
- **Famille de police — déjà faite avant ce plan.** Vérifié dans le code :
  `UserPreferences.fontFamily` → `EffectiveReadingSettings` → `TextStyle` →
  clé de pagination, de bout en bout. Retirée du périmètre plutôt que
  réimplémentée.
- **Espacement des paragraphes — REFUSÉ en l'état (écart déclaré).** La
  colonne `paragraphSpacingStep` est persistée mais n'est pas exposée. Les
  paragraphes sont séparés par un `"\n"` dont l'espace d'offsets doit rester
  aligné au caractère près avec `JsoupChapterParser` : c'est l'invariant dont
  dépend le surlignage TTS. L'espacer demande des `ParagraphStyle` par bloc
  dans `buildBatchAnnotatedString`, pas un séparateur supplémentaire. Exposer
  un curseur sans ce travail aurait donné soit un réglage sans effet, soit un
  surlignage désaligné.

Migration `MIGRATION_26_27` avec son test `MigrationTestHelper` dans le même
commit (K4), **vérifié sur device** : 29 tests de migration, 0 échec. Tests
JVM : `ReaderComfortTest` (4 cas), `PaginationJustificationKeyTest` (2 cas).
Commits `1bcc5d70`, `3f8f9b64`.

**Vérifié sur device** (V2206, Android 14) : les trois réglages sont exposés au
panneau TT et prennent effet — justification et césure visibles (mots coupés en
fin de ligne), marges larges effectives.

**Défaut trouvé par cette vérification, corrigé** : le cran de marge n'avait
aucun effet en mode SCROLL, dont le `contentPadding` était resté écrit en dur —
seul le mode paginé avait été câblé. La lecture du code ne l'avait pas relevé ;
l'écran, immédiatement.

**Restant à vérifier par Issa** : qu'un changement de marge repagine sans perdre
la position de lecture sur un chapitre long, et le maintien de l'écran allumé
sur une session réelle.

### 6.7 — P5, micro-polish premium

- **Échelle haptique (fait)** — `AppHaptics` (core:designsystem) : `tick`,
  `confirm`, `reject`, `longPress`. Passe par les constantes plateforme et non
  par `LocalHapticFeedback` : sur Compose 1.7, `HapticFeedbackType` n'offre que
  `LongPress` et `TextHandleMove`, sans distinction confirmation/refus et bien
  trop appuyés pour un changement de page. Premiers usages : cran de page
  tournée (dans la branche du swipe MANUEL — `onPageChanged` suit aussi la
  narration et ferait vibrer en continu pendant une écoute), confirmation au
  signet.
- **Tokens de mouvement (posés)** — `Motion` : trois durées, deux courbes,
  dont les fabriques passent par `reducedMotionDuration` **par construction**.
  `gestureSpring` renvoie une durée nulle plutôt qu'un ressort rapide quand le
  mouvement est réduit.
- **Restant** — substituer ces tokens aux `tween` en dur existants. Non fait
  dans le même commit volontairement : plusieurs de ces animations portent des
  correctifs de clignotement documentés
  (`NOTE_REGRESSION_CLIGNOTEMENT_PAGE_HUD.md`), leur migration demande une
  vérification device dédiée.
- **Restant** — partage de la bitmap de couverture entre le lecteur et la
  notification média (cache Coil), pour éviter une seconde décompression.

Commit `f42103bb`.

### 6.8 — État du plan

| Lot | État |
|---|---|
| P1 — session média | Livré ; écarts 3 (±30 s, refusé) et 4 (minuteur en notification) ouverts |
| P2 — mini-lecteur et propriété de session | Livré (paliers a et b) |
| P3 — build honnête | Partiel : build type et `profileinstaller` posés ; générateur de Baseline Profile restant |
| P4 — confort visuel | Livré, sauf espacement de paragraphe (refusé, motivé) |
| P5 — micro-polish | Fondations posées ; migration des animations et couverture partagée restantes |

Le plus gros reste de valeur mesurable est **P3** : le Baseline Profile est le
seul élément du plan dont le gain (démarrage à froid) se chiffre, et il n'est
toujours pas mesuré.
