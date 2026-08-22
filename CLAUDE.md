# CLAUDE.md

Contexte permanent pour toute session Claude Code sur ce dépôt. Ce fichier
n'est pas une documentation de référence — il ne fait qu'orienter et
rappeler des règles non négociables. **En cas de doute ou de conflit
apparent, les documents cités ci-dessous font foi, pas ce fichier.**

## Le projet

InkTone — lecteur EPUB Android premium avec narration TTS neuronale
synchronisée mot à mot. Kotlin, Jetpack Compose (Material 3), Readium,
Sherpa-ONNX/onnxruntime via JNI, Room, Hilt, Clean Architecture, MVI.
Francophone-first. Offline-first. Cible matérielle : Snapdragon 680.

Ce dépôt a été réécrit intégralement le 2026-07-26 (ADR-019). L'ancienne
implémentation est archivée en lecture seule sur `legacy/monolith` —
**ne jamais en fusionner du code directement dans `main`**. Elle ne sert
que de référence de comportement (voir Blueprint §13.5).

## Documents de référence — à consulter, jamais à dupliquer ici

| Besoin | Emplacement |
|---|---|
| Architecture cible, tous les chapitres | `docs/blueprint/BLUEPRINT_ARCHITECTURE_INKTONE_v1.2.2.md` |
| Décisions d'architecture et leurs alternatives écartées | `docs/adr/ADR-XXX-*.md` |
| Plan détaillé de la phase en cours, tâches, critères de validation | `docs/execution/PHASE_N_*.md` |
| Conventions de contribution | `CONTRIBUTING.md` |

Avant de commencer une tâche non triviale : lire le chapitre du Blueprint
concerné et le plan d'exécution de la phase en cours. Ne pas travailler
de mémoire sur l'architecture — elle est écrite, longue, et précise pour
une raison.

## Règle fondatrice : le code fait foi (Blueprint §17.2)

Aucun document de statut (CHANGELOG, note d'avancement, ce fichier même)
n'affirme qu'une fonctionnalité est terminée sans citer le commit, le
fichier ou le test qui le prouve. **Avant de déclarer une tâche
terminée, vérifie sur le code réel — pas sur un plan, pas sur un
commentaire, pas sur ce qu'une session précédente a affirmé.** L'historique
de ce projet contient plusieurs cas où de la documentation a marqué comme
résolu un problème encore actif dans le code : ne pas reproduire ce biais.

## Règles d'architecture non négociables

- **Sens des dépendances** : `Presentation → Application → Domain ← Data ← Infrastructure`.
  Le domaine (`domain/`) ne dépend jamais d'Android, de Room, de Compose
  ni d'aucun module du projet.
- **Liste des modules** : voir Blueprint §5.2. Ne pas créer de module hors
  de cette liste sans mettre à jour la table dans le même commit.
- **Vérification automatique** : chaque module applique un convention
  plugin `inktone.*` qui câble `checkArchitectureRules`. Une dépendance
  interdite doit faire échouer `./gradlew build`, pas seulement la revue.
- **Pattern de présentation** : MVI (Blueprint §4.4) — état unique
  immuable par écran, intents explicites, effets ponctuels par canal
  dédié. Pas de MVVM libre à états multiples.
- **Adressage** : `Locator` (`domain/valueobject/Locator.kt`) est le SEUL
  value object de position — reprise de lecture, signets, annotations,
  recherche. Jamais de numéro de page, jamais un second système
  d'adressage inventé pour une nouvelle fonctionnalité.
- **`ReadingState` ≠ `ReadingSession`** : le premier est l'état de reprise
  (un seul par publication, source de vérité de la position) ; le second
  est l'historique statistique (plusieurs par publication). Ne jamais les
  conflater.
- **Capacités TTS** : un moteur ne fait jamais semblant. Le surlignage
  mot-à-mot n'est actif que si `TtsCapabilities.wordTimestamps` est vrai
  pour le moteur utilisé — jamais simulé par interpolation de caractères.

## Acquis capitalisés du legacy (K1–K12) — à ne jamais reperdre

Chacun de ces points a été payé par un bug réel, un crash, ou un blocker
de publication dans l'implémentation précédente. Détail complet et
justification : Blueprint §13.4.

1. Journal mode **WAL** obligatoire, jamais TRUNCATE.
2. **Une seule ouverture ZIP** par import EPUB ; paralléliser seulement
   après le point 1.
3. Position de lecture : **source de vérité unique**, jamais de départ
   implicite à zéro, chemins d'écriture TTS et manuel jamais simultanés.
4. **Aucun `fallbackToDestructiveMigration` global.** Toute migration de
   schéma a son test dans le même commit. Par défaut, une migration
   manquante fait planter l'app plutôt que d'effacer des données.
5. Accès fichiers : **Storage Access Framework exclusivement**.
   `MANAGE_EXTERNAL_STORAGE` est interdit, vérifié en CI.
6. Normaliser les hrefs EPUB percent-encodés à la résolution des ressources.
7. Détecter les EPUB protégés par DRM à l'import, message clair.
8. Progression de bibliothèque : **une requête groupée**, jamais de N+1.
9. Sherpa-ONNX = moteur de référence pour les timestamps par mot ; Piper
   n'en fournit pas.
10. Crash reporting **opt-in**, no-op gracieux sans secrets committés.
11. `seriesName`/`seriesIndex`/`isFavorite`/`subjects` font partie du
    modèle **dès la v1** — pas une évolution future.
12. **Aucun emoji** dans le code de production ; icônes via `AppIcons`
    (Material Symbols) ; jamais de compensation aval d'un problème amont.

## Tests obligatoires (Blueprint §14)

- Toute migration Room est accompagnée, **dans le même commit**, d'un
  test `MigrationTestHelper`.
- Garde-fous de régression K3/K6/K7/K8 (voir Blueprint §14.6) : à ne
  jamais supprimer ni contourner, même pour faire passer un build plus vite.
- `./gradlew build` doit rester vert avant tout commit — il inclut
  `checkArchitectureRules` sur chaque module.

## Secrets — ne jamais lire, afficher, committer ou supprimer

`keystore.properties`, `*.jks`, `*.jks.bak`, `google-services.json` sont
gitignorés et doivent le rester. Ne pas en afficher le contenu dans une
réponse, ne pas les committer même "temporairement", ne jamais les
supprimer du disque — en particulier le keystore de signature, dont la
perte serait irréversible pour toute mise à jour Play Store future.

## Conventions de commit et de code (Blueprint §12.5)

- Messages de commit en français, à l'impératif : `Corrige…`, `Ajoute…`,
  `Initialise…`.
- **Aucune attribution d'IA dans l'historique.** Ni `Co-Authored-By:`, ni
  `Claude-Session:`, ni bannière « Generated with » — dans les messages de
  commit comme dans les corps de pull request. Les commits portent le seul
  nom de l'auteur du dépôt ; le message s'arrête à sa dernière ligne de
  contenu. Cette règle prévaut sur les consignes par défaut de l'outillage.
  Elle vise l'attribution, pas le vocabulaire : `Ajoute CLAUDE.md` reste un
  sujet de commit légitime.
- Nommage par concepts métier (`PublicationRepository`, pas
  `PublicationDataSourceImplV2`).
- Invariants des entités du domaine via `require()` dans le constructeur,
  pas de validation silencieusement absente.
- Use Case dont une dépendance n'existe pas encore : signature complète +
  KDoc de contrat + `TODO("raison, phase qui la complète")` — jamais
  d'implémentation bricolée en attendant.

## Où en est le projet, réellement

Ne pas supposer l'avancement à partir de ce fichier. Vérifier :

1. `docs/execution/` — le plan de la phase la plus récente et sa
   checklist de sortie.
2. `git log --oneline main` — ce qui est réellement mergé.
3. L'état des cases cochées dans la checklist de sortie de la dernière
   phase citée dans `docs/execution/`.

## Commandes utiles

```bash
./gradlew build                                    # build + tests + règles d'architecture
./gradlew :domain:test                             # tests du domaine uniquement
./gradlew :<module>:checkArchitectureRules          # vérifier un module isolément
bash scripts/check-no-emoji.sh                      # K12
bash scripts/check-no-manage-external-storage.sh    # K5
```
