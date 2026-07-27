# Plan d'action — Phases 2 à 10 (vue d'ensemble, sans code)

**Changelog :** 2026-07-27 : Phase 5 revisee post-ADR-021 (Piper
retire, alignement CTC ajoute comme mecanisme du Palier 2).

**Statut :** vue d'ensemble de planification, pas un document d'exécution.
Chaque phase sera détaillée au niveau Claude-Code-ready (code Kotlin complet,
tests, critères avant/après — comme les Phases 0 et 1) juste avant son
exécution. Ce document sert à voir les dépendances inter-phases et à ne rien
oublier, pas à coder directement dessus.

**Référence :** Blueprint InkTone v1.1.0. **Mise à jour :** au fur et à
mesure que les phases réelles révèlent des ajustements — comme la Phase 1 a
révélé deux trous de la Phase 0, ce document sera corrigé, pas figé.

---

## Phase 2 — Fondations data & persistance

**Modules :** `data`, `infrastructure/database`, `infrastructure/storage`
**Dépend de :** Phase 1 (interfaces de repository)

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 2.1 | Entités Room + DAOs (une par entité domain) | 1.6 | Compile, DAOs exposent `Flow` |
| 2.2 | Mappers domain ↔ Room, dont le mapper `Locator` → colonnes à plat (`resourceHref`, `chapterIndex`, `paragraphIndex`, `charOffset`) | 2.1 | Test de mapper : round-trip sans perte |
| 2.3 | Configuration WAL explicite (K1) | 2.1 | Fichiers `-wal`/`-shm` présents à l'exécution ; commentaire justificatif dans le code |
| 2.4 | Harnais de migration (`MigrationTestHelper`), schéma v1, template de test pour v2 | 2.1 | Un test de migration passe, même minimal, prouvant le harnais fonctionnel |
| 2.5 | Implémentations des 7 repositories (Room-backed) | 2.1, 2.2 | Chaque interface du §1.6 a une implémentation réelle |
| 2.6 | Cascade de suppression (Publication → ReadingState/Sessions/Bookmarks/Annotations) | 2.5 | Test : supprimer une Publication vide les tables liées |
| 2.7 | `infrastructure/storage` : wrapper SAF (`DocumentFile`, permissions persistables) | — | Test : ouvrir/lire un fichier via URI SAF simulée |
| 2.8 | DI Hilt : modules liant interfaces ↔ implémentations, fourniture de la base Room | 2.5 | L'app (squelette) s'injecte sans erreur Hilt |
| 2.9 | Suite de tests CRUD complète, une par entité, en base Room in-memory | 2.5 | Tous verts |

**Sortie de phase (Blueprint) :** round-trip CRUD testé par entité ; WAL actif ; harnais de migration prêt.
**Point d'attention :** c'est ici que `PublicationRepositoryImpl.getByFileHash()` doit vraiment être indexé (§6.6) — à vérifier explicitement, pas supposé.

---

## Phase 3 — Marche à blanc (walking skeleton)

**Modules :** `infrastructure/parser`, `infrastructure/tts`, `feature/reader` (squelette)
**Dépend de :** Phase 2 (persistance de `ReadingState`)
**Nature de la phase :** de risquage, pas de couverture — un seul livre, un seul chapitre, une seule phrase. Voir Blueprint §16.3 : c'est le go/no-go du pari architectural central (le `Locator` traverse proprement Reader ↔ TTS ↔ persistance).

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 3.1 | Intégration Readium (dépendance Gradle, wrapper minimal) | — | Un EPUB de test se parse sans erreur |
| 3.2 | Mapping Locator Readium ↔ Locator domaine (ADR-011) | 3.1 | Test : aller-retour sans perte sur un point de test réel |
| 3.3 | Extraction Document Model minimal (un chapitre suffit) | 3.1 | `Chapter`/`Sentence` peuplés avec offsets réels |
| 3.4 | Intégration Sherpa-ONNX via JNI, synthèse d'une phrase | — | Audio produit, timestamps mot présents |
| 3.5 | **Vérification empirique du contrat `WordTimestamp`** contre la sortie réelle de Sherpa-ONNX | 3.4 | Concordance confirmée, ou écart documenté + ADR de correction avant de continuer |
| 3.6 | `feature/reader` squelette MVI : affichage texte + surlignage mot en cours | 3.3, 3.5 | Le mot surligné suit l'audio à l'œil |
| 3.7 | Persistance `ReadingState` (K3) après lecture, relance de l'app | 2.5 | Reprise au mot exact après kill + relance |
| 3.8 | Test de bout en bout (peut être manuel/instrumenté à ce stade) | 3.6, 3.7 | Documenté, reproductible |

**Sortie de phase :** le mot surligné suit l'audio en direct ; l'app relancée reprend au mot exact.
**Règle de la phase :** si 3.5 révèle un écart significatif entre le contrat posé en Phase 1 et la réalité de Sherpa-ONNX, corriger le contrat (`domain/service/TtsEngine.kt`) et documenter l'ADR **avant** de passer à la Phase 4 — ne jamais construire en largeur sur un contrat non vérifié.

---

## Phase 4 — Reading Engine complet

**Modules :** `infrastructure/parser` (complet), `feature/reader` (complet)
**Dépend de :** Phase 3 (contrats validés)

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 4.1 | Parser EPUB complet (tous chapitres, toutes ressources, TOC) | 3.1–3.3 | Corpus de fixtures EPUB variées s'ouvre intégralement |
| 4.2 | Parser TXT | — | Découpage chapitres/paragraphes correct |
| 4.3 | Normalisation hrefs percent-encodés (K6) | 4.1 | Test avec fixture EPUB à hrefs mixtes |
| 4.4 | Détection DRM à l'import (K7) | 4.1 | Fixture EPUB DRM détectée, message clair, jamais de crash |
| 4.5 | Navigation complète (chapitre suiv./préc., TOC virtualisée, retour dernière position) | 3.6 | Parcours manuel complet sans accroc |
| 4.6 | Préchargement chapitre suivant | 4.5 | Navigation perçue instantanée sur chapitre préchargé |
| 4.7 | Rendu virtualisé, thèmes, typographie, `EffectiveReadingSettings` appliqué | 3.6 | Changement de thème/police instantané, pas de recomposition intégrale |
| 4.8 | Gestion d'erreurs (fichier corrompu, ressource manquante) | 4.1 | Fixture corrompue → message clair, jamais de crash (Blueprint §7.11) |
| 4.9 | Benchmarks §11.2 (ouverture EPUB, navigation, scroll) | 4.1–4.7 | Budgets tenus sur baseline Snapdragon 680 |
| 4.10 | Garde-fous de régression K3/K6/K7 complets | 4.3–4.5 | Tests dédiés verts (Blueprint §14.6) |

**Sortie de phase :** budgets §11.2 tenus sur le corpus de fixtures ; garde-fous K3/K6/K7 verts.

---

## Phase 5 — TTS Engine complet

**Modules :** `infrastructure/tts` (complet), `infrastructure/media`, `feature/player`
**Dépend de :** Phase 3 (adaptateur Sherpa-ONNX validé)

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 5.1 | Adaptateur Sherpa-ONNX (synthèse, modèle Kokoro) — pas de timestamps natifs, voir ADR-021 | 3.4 (DocumentModel) | Audio produit, qualité vocale supérieure au Palier 1 |
| 5.2 | Passage d'alignement forcé CTC sur l'audio Sherpa-ONNX généré (modèle CTC léger + Viterbi contraint par le texte connu) | 5.1 | WordTimestamp réels produits, précision mesurée contre le corpus de référence (±120ms, §11.2) |
| 5.3 | Buffer/préchargement adaptatif (phrase n+1 pendant lecture de n) | 3.4 | Silence inter-phrases ≤ 150 ms (§11.2) |
| 5.4 | MediaSession + service arrière-plan + notification | 5.3 | Lecture continue écran éteint, contrôlable depuis la notification |
| 5.5 | Contrôles complets (play/pause/stop/vitesse/voix à chaud) | 5.4 | Changement de vitesse recalcule les timestamps sans coupure perceptible |
| 5.6 | Téléchargement de voix à la demande (ADR-018), vérification d'empreinte | — | Modèle téléchargé, vérifié, utilisable hors ligne ensuite |
| 5.7 | `feature/player` UI complète | 5.4, 5.5 | Parcours manuel complet |
| 5.8 | Gestion d'erreurs TTS (voix indisponible, modèle corrompu, repli proposé) | 5.6 | Jamais d'interruption brutale |
| 5.9 | Benchmarks TTS (latence premier audio, silence inter-phrases, précision surlignage ±120 ms) | 5.3, 3.5 | Budgets §11.2 tenus |
| 5.10 | Tests par capacité (chaque adaptateur vérifié contre son propre contrat) | 5.1, 5.2 | Aucun adaptateur ne prétend une capacité qu'il n'a pas |

**Point d'attention :** le passage CTC est un second modèle ONNX + une
latence supplémentaire à mesurer — budget à fixer en Phase 5, pas
encore acté dans les chiffres de §11.2.

**Sortie de phase :** budgets TTS tenus ; lecture en arrière-plan survit à la mise en veille.

---

## Phase 6 — Bibliothèque & import

**Modules :** `feature/library`, `infrastructure/worker`
**Dépend de :** Phase 2 (WAL actif), Phase 4 (parser complet pour la détection DRM/doublons à l'import réel)

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 6.1 | Import en tâche WorkManager (survit à la mise en arrière-plan) | 4.1 | Import continue après mise en arrière-plan de l'app |
| 6.2 | Parallélisation de l'import (K2, après WAL déjà actif depuis 2.3) | 2.3, 6.1 | Ordre WAL-puis-parallélisation respecté et documenté |
| 6.3 | Détection de doublons par hash (`getByFileHash`) | 2.5 | Import du même fichier deux fois → doublon détecté, pas dupliqué |
| 6.4 | `ImportPublicationUseCase` enfin complété (Phase 1 TODO levé) | 4.1, 4.4, 6.3 | Le `TODO()` disparaît, remplacé par une implémentation testée |
| 6.5 | Filtres réels (favoris, séries, tags/`subjects`, auteur, statut de lecture) | 2.5 | Chaque filtre retourne le bon sous-ensemble sur un jeu de données de test |
| 6.6 | UI bibliothèque : grille/liste, scroll à 1000+ livres | 2.5 | 60 fps maintenu (§11.2) |
| 6.7 | `ExportLibraryUseCase` complété (Phase 1 TODO levé) | 2.7 | Export réel vers une destination SAF |
| 6.8 | Bannière de progression non bloquante, badges d'état | 6.1 | Import visible, UI jamais gelée |
| 6.9 | Benchmark import (500 EPUB) | 6.1–6.3 | ≤ 5 min (§11.2) |

**Sortie de phase :** budget d'import tenu ; scroll bibliothèque à 1000+ livres à 60 fps.

---

## Phase 7 — Annotations, signets, recherche

**Modules :** `feature/reader` (annotations/signets), `feature/search`
**Dépend de :** Phase 4 (Reader complet)

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 7.1 | UI annotation (sélection de texte → `Locator` de début/fin, couleur, note) | 4.7 | Création/édition/suppression d'annotation fonctionnelle |
| 7.2 | UI signets | 4.5 | Création/navigation vers signet fonctionnelle |
| 7.3 | `SearchService` : implémentation FTS4 (SQLite via Room, §6.9) | 2.1 | Recherche sur un corpus de test retourne les bons résultats |
| 7.4 | `SearchPublicationUseCase` complété (Phase 1 TODO levé) | 7.3 | Le `TODO()` disparaît |
| 7.5 | UI recherche (résultats, extraits, navigation vers résultat) | 7.4 | Parcours manuel complet |
| 7.6 | Tests FTS (pertinence, performance sur corpus volumineux) | 7.3 | Recherche rapide même sur bibliothèque large |

**Sortie de phase :** recherche plein texte fonctionnelle ; CRUD annotation sur plages de `Locator`.

---

## Phase 8 — Réglages, statistiques, onboarding

**Modules :** `feature/settings`, `feature/statistics`, `feature/onboarding`
**Dépend de :** Phase 2 (`PreferencesRepository`), Phase 5 (liste des moteurs TTS disponibles)

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 8.1 | UI réglages (thème, police, langue, moteur TTS par défaut) | 2.5 | Modification persistée et reflétée immédiatement |
| 8.2 | Vérification de la cascade de précédence en conditions réelles UI | 8.1 | Surcharge de publication prime visiblement sur le réglage global |
| 8.3 | `feature/statistics` (temps de lecture, livres terminés, séries de jours) basé sur `ReadingSession` | 2.5 | Statistiques cohérentes avec l'historique réel |
| 8.4 | Onboarding : présentation, consentement crash reporting (ADR-014), téléchargement voix initiale (ADR-018) | 5.6 | Premier lancement complet, choix reversible dans les réglages ensuite |
| 8.5 | Tests du flux onboarding (accepté / refusé / reporté) | 8.4 | Les trois chemins testés |

**Sortie de phase :** flux d'onboarding complet ; opt-in testé.

---

## Phase 9 — Durcissement transverse

**Modules :** tous
**Dépend de :** Phases 4 à 8 (fonctionnalités complètes à durcir)

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 9.1 | Audit accessibilité (labels TalkBack, tailles dynamiques, contrastes, cibles tactiles) sur tous les écrans | 4–8 | Checklist accessibilité (Blueprint §1.4, engagement v1) passée |
| 9.2 | Revue de sécurité complète (§10) | 4–8 | Aucune fuite de données, permissions minimales confirmées |
| 9.3 | Suite de benchmarks complète, tous les budgets §11.2, sur device réel classe Snapdragon 680 | 4–8 | Tous les budgets au vert, ou ADR de révision motivé |
| 9.4 | Finalisation des portes CI (couverture, tout §14.8) | — | CI complète et bloquante |
| 9.5 | Audit de conformité Play Store (permissions, politique de confidentialité, fiche store) | 9.2 | Aucun blocker connu de type K5 |

**Sortie de phase :** tous les budgets §11.2 au vert ; checklist accessibilité passée.

---

## Phase 10 — Release candidate

**Dépend de :** Phase 9

| # | Tâche | Dépend de | Sortie vérifiable |
|---|---|---|---|
| 10.1 | Build signé (keystore — rappel CLAUDE.md : ne jamais le manipuler à la légère) | 9.4 | APK/AAB signé généré |
| 10.2 | Dogfooding personnel sur usage réel | 10.1 | Retour d'usage documenté |
| 10.3 | Bug bash ciblé sur les parcours critiques | 10.2 | Liste de bugs triée, bloquants corrigés |
| 10.4 | Fiche store (captures, description, politique de confidentialité) | 9.5 | Prête à soumettre |
| 10.5 | Signature finale de la RC | 10.1–10.4 | Décision explicite d'Issa, pas un critère automatique |

**Sortie de phase :** RC signée.

---

## Notes de méthode pour la suite

- Chaque phase sera détaillée en document Claude-Code-ready (comme Phases 0/1)
  **immédiatement avant son exécution**, jamais plus tôt — pour intégrer les
  ajustements que la phase précédente aura révélés.
- La Phase 3 est un point de décision, pas une simple étape : si elle révèle un
  écart de contrat, ce document sera corrigé avant de détailler la Phase 4.
- Les tâches marquées « TODO Phase 1 levé » (6.4, 6.7, 7.4) sont le rappel
  explicite que ces Use Cases ont été délibérément laissés en signature — leur
  complétion ici n'est pas un oubli à combler mais l'exécution du plan initial.
