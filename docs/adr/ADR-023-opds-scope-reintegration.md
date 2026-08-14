# ADR-023 : Réintégration bornée du périmètre OPDS (Volet 1 — bibliothèque distante)

**Status :** Accepted
**Date :** 2026-08-13

## Context

Le Blueprint et `docs/execution/PLAN_ACTION_INKTONE_TOP_TIER.md` (ligne ~832,
table « Ce qu'on ne fait pas pour la v1 ») ont placé OPDS « hors périmètre
Blueprint — v1.x », sans ADR dédié — une simple ligne de table, contrairement
au PDF (ADR-017) qui avait, lui, un ADR formel dès le report initial.
`docs/execution/UX_FLOW_DESIGN.md:847` affirme même que le placeholder « Catalogues
OPDS » du drawer est « cohérent avec ADR différant OPDS » — cette référence est
fausse à ce jour : aucun ADR OPDS n'existe. Cet ADR corrige l'incohérence et,
suivant le précédent PDF (ADR-017 différé puis réintégré par volets via le
Lot 12), pose la réintégration d'OPDS comme un **Volet 1 borné**, pas un
chantier ouvert.

Périmètre proposé (`docs/incoming/OPDS.md`) : tableau de bord de catalogues,
navigation hiérarchique dans un flux OPDS/Atom, téléchargement transparent
d'EPUB, recherche OpenSearch optionnelle, authentification HTTP Basic
optionnelle par catalogue.

## Decision

La v1.x réintègre OPDS en **un seul volet borné** :

**Volet 1 — Bibliothèque OPDS (ce Lot 13) :** tableau de bord de catalogues
(CRUD Room + credentials chiffrés), navigation récursive dans un flux
OPDS 1.2/Atom (paliers/dossiers, pas de second système d'adressage —
la navigation OPDS ne touche jamais `Locator`, qui reste exclusif à la
position de lecture), téléchargement asynchrone via `WorkManager` et
injection dans la bibliothèque existante en réutilisant le pipeline
d'import EPUB déjà en place (pas de second chemin d'import dupliqué),
recherche OpenSearch si `searchTemplateUrl` est annoncé par le flux,
authentification HTTP Basic stockée chiffrée (`androidx.security.crypto`,
même famille que `SecureAuthStateStore` du Lot 11) — pas d'OAuth (hors
scope OPDS 1.2, aucun catalogue cible connu n'en a besoin).

**Hors Volet 1, explicitement différé :**
- OPDS 2.0 (JSON) — seul Atom/XML (OPDS 1.2) est supporté ; un flux
  annonçant `application/opds+json` sans variante Atom est rejeté avec un
  message clair, pas silencieusement mal interprété.
- Détection et gestion fine des acquisitions partielles/empruntées
  (`opds:indirectAcquisition`, DRM de prêt type Adobe ACS) — un livre dont
  le lien d'acquisition ne pointe pas vers un `.epub` direct (ou dont le
  type MIME annonce un DRM) est signalé « non téléchargeable depuis
  InkTone » plutôt que de tenter un téléchargement voué à l'échec, cohérent
  avec K7 (détection DRM à l'import) déjà acquis pour l'import local.
- Synchronisation entre catalogues OPDS et le fournisseur de sync cloud
  (Lot 11, Google Drive) — les deux mécanismes restent non liés.

## Rationale

Suivre le précédent ADR-017/Lot 12 : plutôt que de lever la totalité du
périmètre OPDS envisageable (OPDS 2.0, emprunt DRM, sync croisée) d'un
coup, un volet borné et testé en profondeur maintient le standard
« production-grade, no half-measures » sans diluer la qualité déjà
acquise sur EPUB/TXT/PDF.

## Consequences

- Nouveaux modules `infrastructure:opds` et `feature:opds` à ajouter à la
  table Blueprint §5.2 **dans le même commit** que leur création
  (Palier 1 du Lot 13).
- `docs/execution/PLAN_ACTION_INKTONE_TOP_TIER.md` (ligne ~832) et
  `docs/execution/UX_FLOW_DESIGN.md` (mentions « placeholder v1.x », b4)
  doivent être corrigés pour refléter la réintégration, avec renvoi vers
  cet ADR plutôt qu'une référence fantôme.
- Le drawer (`LibraryScreen.kt`) réactive l'item « Catalogues OPDS »
  (b4) selon la même règle que Récents/Sync/Thèmes : jamais affiché sans
  écran fonctionnel derrière (règle actée au Lot 1).

## Alternatives Considered

- **Support OPDS 2.0 dès le Volet 1** : rejeté — peu de catalogues grand
  public le servent par défaut (Gutenberg, Feedbooks, Calibre-Web, Komga
  répondent tous en Atom), ajoute un second parseur pour un bénéfice
  marginal immédiat.
- **Réutiliser `SyncProvider` (domaine) pour modéliser un catalogue OPDS** :
  rejeté — `SyncProvider` est un contrat d'upload/download/list/delete
  vers un fournisseur de stockage exclusif (un seul actif à la fois,
  Lot 11) ; un catalogue OPDS est une source de lecture parmi plusieurs
  actives simultanément, sémantique différente. Un contrat `OpdsCatalogSource`
  distinct est plus honnête qu'un détournement du contrat existant.
- **Abandon complet d'OPDS** : rejeté — fonctionnalité demandée, cohérente
  avec le positionnement offline-first + sources ouvertes (Gutenberg,
  Feedbooks) déjà mentionné au Blueprint §1.4 (mission d'accessibilité).
