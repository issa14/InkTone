# Plan — Dette streak / jour calendaire

Vérifié contre `1235c422`. Deux bugs distincts, un seul point de contact.

## Diagnostic confirmé (par exécution, pas par lecture)

### Bug 1 — production réelle : reset faux de la série

`GetStatisticsUseCase.kt:238` (`parseDateToEpochDay`) parse une date `"yyyy-MM-dd"` avec
`SimpleDateFormat` (fuseau **local** implicite) puis divise en jours avec
`TimeUnit.MILLISECONDS.toDays` (grille **UTC**). Pour tout fuseau de décalage
**positif** (Cotonou, UTC+1, y compris à l'année sans DST) — décalage entier ou
fractionnaire, peu importe — le résultat est systématiquement le jour réel **moins 1**.

`GetStatisticsUseCase.kt:118` (`computeStreak`, variable `today`) calcule le jour courant
par une **méthode différente** : `TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())`,
sans passer par `parseDateToEpochDay`. Ce `today` n'est donc *pas* décalé.

Conséquence vérifiée par simulation (Africa/Porto-Novo, UTC+1, aucune session encore
aujourd'hui, série réelle de 3 jours se terminant hier) :

```
distinctDays (DB, localtime, correct)  = ["2026-08-09", "2026-08-08", "2026-08-07"]
parseDateToEpochDay (décalé -1)        = [20673, 20672, 20671]
today (non décalé)                     = 20675
today - 1                              = 20674
20673 != 20675 ET 20673 != 20674  →  reset à 0
```

**Résultat : `currentStreakDays` retombe à 0 à chaque fois que l'app est consultée avant
la première session du jour, sur tout appareil à décalage UTC positif.** C'est visible,
pas cosmétique — c'est la consultation la plus fréquente (le matin, avant de lire).

À l'inverse, si l'utilisateur a déjà lu aujourd'hui au moment du calcul, le décalage
constant de -1 s'annule dans les différences relatives et la tolérance `!= today-1`
l'absorbe : streak correct par coïncidence de structure, pas par conception. C'est ce
qui rend le bug invisible en usage superficiel.

**Non affecté**, contrairement à l'hypothèse de départ :
- `todayReadingMinutes` (objectif du jour) — comparaison de `String` (`todayKey` vs
  `dailyStats[].date`), aucun passage par `parseDateToEpochDay`.
- Les 4 requêtes SQL du DAO (`ReadingSessionDao.kt:38,52-53,64,114`) — toutes cohérentes
  via `date(startedAt/1000, 'unixepoch', 'localtime')`.

### Bug 2 — fragilité des tests : deux horloges non fixées, pas une

- `GetStatisticsUseCaseTest.kt:60` et `StatisticsViewModelTest.kt:235,257,286,317,354` :
  `now = System.currentTimeMillis()` en dur, sans horloge injectée.
- Les 5 occurrences dans `StatisticsViewModelTest` exercent en réalité
  `StatisticsViewModel.fillMissingDays` / `computeVariation`
  (`StatisticsViewModel.kt:128`, `LocalDate.now()` non fixé) — **pas**
  `parseDateToEpochDay`. C'est un deuxième site non pincé, indépendant du bug 1.

Deux risques de flake distincts, cumulables :
1. **Course à la frontière de minuit** : le test capture `now` à l'instant T1 ; le code de
   prod recalcule "maintenant" à un instant T2 postérieur (après un saut de dispatcher) ;
   si T1/T2 encadrent minuit local, les deux "aujourd'hui" divergent.
2. **Dépendance au fuseau de la machine hôte** : le bug 1 est masqué ou visible selon que
   le CI/poste tourne en UTC ou dans un fuseau à décalage positif — un test peut passer
   sur un runner CI et échouer sur ta machine (Cotonou, UTC+1), ou l'inverse.

Cohérent avec le profil "45/46 depuis le lot 3a" : pas un flake aléatoire, un flake
structurel dépendant de l'heure et du fuseau d'exécution.

## Correctif production

Direction proposée, meilleure que la "conversion explicite par ZoneId" envisagée :
**ne jamais repasser par les millisecondes/UTC pour une valeur qui est déjà une date
calendaire.** `SimpleDateFormat` + `TimeUnit.toDays` fait un aller-retour date → instant
→ jours qui force une décision de fuseau qui n'a pas lieu d'être. `java.time.LocalDate`
représente un jour calendaire directement, sans ambiguïté de fuseau à la lecture :

```kotlin
// GetStatisticsUseCase.kt — remplace parseDateToEpochDay
private fun parseDateToEpochDay(date: String): Long? =
    runCatching { LocalDate.parse(date).toEpochDay() }.getOrNull()

// computeStreak (ligne 118) — today doit utiliser la même représentation
private fun computeStreak(epochDays: List<Long>, today: Long): Int { ... }
// appelant (ligne 76-77) :
val today = LocalDate.now(clock).toEpochDay()
val streak = computeStreak(streakDays, today)
val maxStreak = computeMaxStreak(streakDays)
```

`LocalDate.parse("2026-08-10")` interprète la chaîne comme un jour calendaire pur —
aucune notion de fuseau à ce stade, donc aucune conversion à faire foirer. `today` doit
utiliser la même primitive (`LocalDate.now(clock)`) pour comparer deux valeurs de même
nature, au lieu de comparer un "jour local mal projeté en UTC" à un "jour UTC brut".

**Horloge injectée**, pas seulement pour les tests — `System.currentTimeMillis()`
apparaît aussi lignes 60, 63, 87-88 (`thirtyDaysAgo`, `sixtyDaysAgo`, `todayKey`).
Remplacer par un `java.time.Clock` en paramètre de constructeur :

```kotlin
class GetStatisticsUseCase(
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
)
```

`domain/` reste sans annotation de framework de DI (`UseCaseModule.kt:1-2` documente
cette contrainte) — `java.time.Clock` est une classe JDK pure, pas un artefact Hilt,
donc ce paramètre ne la viole pas. Câblage :

```kotlin
// data/di/UseCaseModule.kt
@Provides
@Singleton
fun provideClock(): Clock = Clock.systemDefaultZone()

@Provides
fun provideGetStatisticsUseCase(
    readingSessionRepository: ReadingSessionRepository,
    publicationRepository: PublicationRepository,
    clock: Clock,
): GetStatisticsUseCase = GetStatisticsUseCase(readingSessionRepository, publicationRepository, clock)
```

**Extension nécessaire à `StatisticsViewModel`** — `fillMissingDays` et
`computeVariation` (`StatisticsViewModel.kt:128,169`) ont le même point faible
(`LocalDate.now()` non fixé), et ce sont eux, pas `GetStatisticsUseCase`, que 4 des 5
tests fragiles de `StatisticsViewModelTest` exercent réellement. Corriger uniquement
`parseDateToEpochDay` laisserait ces 4 tests flaky. Proposition : injecter le même
`Clock` (déjà fourni par Hilt ci-dessus) dans `StatisticsViewModel` :

```kotlin
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    ...
    private val clock: Clock,
) : ViewModel() {
    private fun fillMissingDays(...) {
        val today = LocalDate.now(clock)
        ...
    }
}
```

Avant/après :
- Avant : reset faux de `currentStreakDays` à chaque consultation avant la première
  lecture du jour, sur tout appareil à décalage UTC positif (Cotonou compris).
- Après : `currentStreakDays`/`maxStreakDays` corrects indépendamment du moment de
  consultation et du fuseau ; `fillMissingDays`/`computeVariation` déterministes et
  testables sans dépendre de l'horloge système.

## Correctif tests

- Remplacer `now = System.currentTimeMillis()` par `Clock.fixed(instant, zone)` injecté
  dans `GetStatisticsUseCase(...)` / `StatisticsViewModel(...)`. Construire les sessions
  à partir de `LocalDate` + `atStartOfDay(zone)`, pas d'arithmétique brute en
  millisecondes sur un "now" non pincé.
- **Fixer explicitement un fuseau à décalage positif** dans au moins un test (ex.
  `ZoneId.of("Africa/Porto-Novo")` ou `ZoneOffset.ofHours(1)`) — aucun des 6 tests actuels
  ne le fait ; c'est précisément la condition qui déclenche le bug 1, et le passage à
  `LocalDate.toEpochDay()` doit être vérifié sous cette condition, pas seulement sous UTC.
- **Nouveau test de non-régression** pour le cas non couvert par les 6 tests existants —
  tous insèrent une session incluant "aujourd'hui" ; aucun ne teste "série de N jours
  consécutifs se terminant hier, rien lu aujourd'hui". C'est exactement le scénario qui
  casse actuellement.

## Hors périmètre de cette dette

`System.currentTimeMillis()` apparaît aussi dans 11 autres fichiers de production
(`BackupManager`, `ExportStatisticsUseCase`, `GoogleSyncLinker`, `SyncNowManager`,
`ReadingSessionTracker`, `ImportPublicationUseCase`, `ResolvePositionConflictUseCase`,
`ReaderViewModel`, `StatusLineBar`, `SyncConfigurationScreen`,
`SyncConflictBottomSheet`). Même antipattern, mais aucun n'a le double appel
"jour calendaire vs jour UTC" qui cause le bug 1 — pas de raison de les toucher dans
cette dette. À consigner comme item séparé si tu veux un audit horloge plus large.
