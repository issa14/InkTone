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
- **(c/d) à faire** — `AudioFocusRequest` + `BecomingNoisy` (test instrumenté) ;
  `onCleared()` qui ne coupe plus quand la session détient la lecture ; signal
  dédié `chapterCompleted` (voir 6.4).

### 6.4 — P1, décision d'architecture actée (rappel)

- **Sémantique pause ≠ stop.** Le lecteur pause par `stop()` (Idle) ; la
  notification pausera par `pause()` (réel). Le collecteur traite `Paused` en
  `isPlaying=false` (désormais branché).
- **Limite connue (à lever en c)** : `togglePlayPause` sur `Buffering` passe par
  `stop()` → le collecteur interprète « Idle alors que isPlaying » comme une fin
  de chapitre — un signal dédié `chapterCompleted` (plutôt que l'heuristique
  Idle) lèvera l'ambiguïté sans auto-avance parasite. Documenté dans le code
  (`PlaybackOrchestrator.togglePlayPause`).
