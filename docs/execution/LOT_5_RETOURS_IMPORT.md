# Lot 5 — Retours d'import

**Base :** `main` (après fusion du lot 4). Branche : `lot-5-retours-import`. Référence cible : `UX_FLOW_DESIGN.md` § Import — progression et retours.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Pourquoi ce lot

Second endroit où l'app ment. Elle avale un fichier corrompu, protégé par DRM ou en double **sans un mot** : la bannière compte les fichiers traités, pas ce qui leur est arrivé. L'utilisateur constate qu'un livre manque, sans jamais savoir pourquoi.

## Le vrai problème n'est pas dans l'UI

L'audit initial concluait « l'habillage visuel manque, pas la logique ». Le code dit autre chose : **l'information est détruite avant d'atteindre l'UI.**

- `ImportPublicationUseCase` produit bien quatre cas distincts — `Duplicate`, `Corrupted`, `DrmProtected`, `UnsupportedFormat`.
- Mais `ImportWorker.kt:67-71` les réduit à trois compteurs : `Success`, `Duplicate`, et un `else -> failureCount` qui **fusionne les trois cas d'échec**.
- Aucun **nom de fichier** n'est conservé nulle part : le worker ne manipule que des URI.
- `ImportViewModel` n'expose aucun état de retour — un `enqueue` sans suite.

Un retour par fichier sur deux registres est donc impossible avec les données actuelles, quelle que soit l'UI écrite au-dessus. D'où un palier de données en premier.

## Découpage en deux paliers poussables

| Palier | Contenu | Vérifiable seul |
|---|---|---|
| **A** | Conserver et remonter le résultat par fichier | Oui — par tests, sans UI |
| **B** | Résumé de fin de lot et retour par fichier | Oui — dépend de A |

**Pousser après le palier A.**

---

# PALIER A — Remonter l'information

## Tâche 5.1 — Cesser de fusionner les cas d'échec

`ImportWorker.kt:67-71`. Remplacer les trois compteurs par une **collecte par fichier** conservant, pour chaque URI traitée : le type de résultat (les cinq cas du domaine, sans regroupement) et le **nom affichable** du fichier.

Le nom doit être résolu **au moment de l'import**, depuis l'URI SAF, et stocké avec le résultat. Après coup, l'URI peut ne plus être résoluble — permission révoquée, fichier déplacé.

**Attention :** le lot 2 a déjà corrigé un bug de ce registre (`Corrige l'import TXT silencieusement ignoré (URI SAF confondue avec un chemin de fichier)`). Traiter l'URI comme une URI, pas comme un chemin.

`Conserve le resultat detaille de chaque fichier importe`

---

## Tâche 5.2 — Transport et persistance des résultats

Décision de conception à prendre explicitement, pas par défaut.

**La contrainte :** la cible prévoit un résumé de fin de lot **avec une action « Détails »**. Un détail consultable après coup ne peut pas vivre dans un état transitoire — il doit survivre à la fermeture de la bannière, voire au redémarrage du processus.

**Deux options :**

- **`Data` de sortie du worker** — simple, mais plafonnée à ~10 Ko et perdue dès que WorkManager élague le travail. Suffisant pour des compteurs, insuffisant pour une liste nommée.
- **Petite table Room de résultats d'import**, purgée après consultation ou après un délai. Durable, alimente l'écran de détail, survit à la mort du processus — **recommandée**.

**Difficulté supplémentaire, déjà documentée dans le code :** au-delà de 50 URI, `WorkManagerImportScheduler` découpe l'import en `WorkRequest` chaînées, et `ImportProgress` ne reflète que le lot en cours (limitation écrite dans le KDoc d'`ImportProgressObserver`). Un résumé « de fin de lot » doit donc **agréger sur toute la chaîne**, pas sur le dernier maillon. Une table Room résout ce point sans effort ; l'option `Data` ne le résout pas.

Si l'agrégation sur chaîne s'avère impraticable, **le signaler et proposer** — par exemple un résumé par lot avec mention explicite qu'il en reste. Ne pas afficher un total partiel comme s'il était complet.

`Persiste les resultats d import pour le resume et le detail`

---

## Tâche 5.3 — Contrat domaine

Ajouter une abstraction d'observation des résultats, sur le modèle exact d'`ImportProgressObserver` : interface dans `domain/service`, implémentation dans `infrastructure`. `feature/import` et `feature/library` ne doivent jamais toucher WorkManager ni Room directement (Blueprint §12.4 — discipline déjà respectée par `ImportScheduler` et `ImportProgressObserver`, à ne pas rompre ici).

`ImportViewModel` expose alors un état réel, au lieu du seul `enqueueImport`.

`Ajoute le contrat d observation des resultats d import`

---

## Tâche 5.4 — Tests du palier A

1. Les cinq cas remontent **distinctement** — un `Corrupted` ne doit pas être indiscernable d'un `DrmProtected`. Test de non-régression du `else` fusionnant.
2. Le nom de fichier est conservé et lisible après la fin du worker.
3. Import de 60 fichiers : le résumé agrège la chaîne complète, ou signale explicitement qu'il est partiel.
4. Les résultats survivent à la mort du processus (si l'option Room est retenue).
5. Un import entièrement réussi ne laisse pas de résidu à consulter.

`Ajoute les tests de remontee des resultats d import`

### Vérifications device — palier A

| # | Attendu |
|---|---|
| A1 | Importer un lot mêlant un EPUB valide, un doublon et un fichier corrompu : vérifier en base que les trois cas sont enregistrés distinctement, avec leurs noms |
| A2 | Importer plus de 50 fichiers : les résultats des différents maillons de la chaîne sont bien tous enregistrés |

---

# PALIER B — Interface

## Tâche 5.5 — Résumé de fin de lot

À la fin de l'import, la bannière de progression cède la place à un résumé du type `9 importés · 2 doublons ignorés · 1 fichier corrompu`, avec une action **« Détails »**.

- **Ne pas afficher les catégories vides.** Un lot sans échec affiche `12 importés`, pas `12 importés · 0 doublon · 0 corrompu`.
- Le résumé reste **non bloquant**, comme la bannière de progression : rendu au-dessus du contenu dans le `Column` du Scaffold, jamais en overlay plein écran. Cette discipline est déjà en place, la conserver.
- Prévoir sa disparition — automatique après un délai, ou au premier geste de l'utilisateur.

`Affiche le resume de fin de lot d import`

---

## Tâche 5.6 — Retour par fichier, deux registres

L'écran de détail liste chaque fichier avec son sort, sur **deux registres visuels distincts** :

| Registre | Cas | Sens |
|---|---|---|
| **Informationnel** | `Duplicate` | Rien d'anormal : le livre est déjà dans la bibliothèque |
| **Alerte** | `Corrupted`, `DrmProtected`, `UnsupportedFormat` | Le fichier n'a pas pu être importé |

C'est la distinction que la cible tient à établir, et c'est celle que le `else` du worker avait supprimée. Un doublon n'est pas une erreur.

- Chaque ligne porte le **nom du fichier** et une raison lisible — pas un nom de classe ni un code.
- Pour un `Duplicate`, permettre d'ouvrir le livre déjà présent : `ImportResult.Duplicate` porte `existingPublicationId`, la donnée est disponible.
- Ne pas proposer de « réessayer » sur un cas qui échouera à l'identique (DRM, format non pris en charge) : ce serait un contrôle décoratif.

`Ajoute le detail par fichier des resultats d import`

---

## Tâche 5.7 — Tests du palier B

1. Un lot sans échec n'affiche aucune catégorie à zéro.
2. Doublon et fichier corrompu sont rendus sur des registres visuellement distincts.
3. « Détails » ouvre la liste ; chaque ligne porte un nom de fichier réel.
4. Depuis un doublon, l'ouverture du livre existant navigue vers la bonne publication.
5. Aucun bouton d'action sur un cas non réessayable.

`Ajoute les tests de l interface de retour d import`

---

## Tâche 5.8 — Consigner

Dans `UX_FLOW_DESIGN.md`, § Import : consigner le comportement retenu pour les lots de plus de 50 fichiers (résumé agrégé ou partiel signalé), et la durée d'existence des résultats consultables.

`Consigne le comportement des retours d import dans la cible`

### Vérifications device — palier B

| # | Avant | Après attendu |
|---|---|---|
| B1 | Un fichier corrompu disparaît sans un mot | Le résumé le signale à la fin du lot |
| B2 | Un doublon est traité comme un échec silencieux | Signalé sur un registre informationnel, distinct d'une alerte |
| B3 | — | « Détails » liste chaque fichier avec son nom réel et sa raison |
| B4 | — | Depuis un doublon, ouvrir le livre déjà présent mène à la bonne publication |
| B5 | — | Import entièrement réussi : résumé simple, sans catégorie à zéro |
| B6 | — | Import de plus de 50 fichiers : le total est correct, ou explicitement annoncé comme partiel |
| B7 | — | Tuer l'app pendant un import, la rouvrir : les résultats déjà obtenus sont consultables ou proprement absents, jamais faux |

Les points B6 et B7 sont les deux seuls à tester des cas que la logique existante ne couvre pas.

---

## Hors périmètre explicite

Récents, Synchronisation, Galerie de thèmes et Studio, Onboarding, cartes manquantes des Réglages, sections manquantes des Statistiques, audit Crashlytics, lot 3f.
