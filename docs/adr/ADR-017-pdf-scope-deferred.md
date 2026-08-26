# ADR-017 : Périmètre PDF différé et borné

**Status :** Accepted — affichage livré (Lot 12, 2026-08-12) ; TTS à la phrase livré (2026-08-26), surlignage mot-à-mot toujours conditionné
**Date :** 2026-07-26

## Context

La version 1.0.0 du Blueprint plaçait le support PDF en v1, aux côtés
d'EPUB. Or un support PDF top-tier (rendu paginé sans reflow, extraction
fiable de l'ordre de lecture pour alimenter le TTS) est un chantier à
part entière. Le mener en v1 aurait dilué la qualité du support EPUB,
contraire au standard « production-grade, no half-measures » du produit.

## Decision

La v1 se limite à EPUB et TXT, complets. Le PDF est repoussé en v1.x,
d'abord en **affichage seul** ; le support TTS sur PDF est conditionné à
la validation préalable d'une extraction fiable de l'ordre de lecture.

**Volet 1 — Affichage seul ✅ (Lot 12, 2026-08-12) :** import PDF,
rendu paginé via PDFium (`io.legere:pdfiumandroid:1.0.20`, BSD-3-Clause),
couverture, navigation page à page, signets, reprise de lecture,
thèmes sombre/sépia sur pages vectorielles. Le pipeline de parsing/rendu
est unifié dans le `DocumentModel` existant (page = chapitre), sans
second système d'adressage. Fonctionnalités non applicables (TTS,
minuteur de sommeil, bascule SCROLL/PAGED, sélection libre) explicitement
désactivées pour le format PDF.

**Volet 2a — TTS à la phrase ✅ (2026-08-26) :** narration audio complète
sur PDF, à la granularité de la phrase. Aucune extraction nouvelle n'a été
nécessaire : `PdfPublicationParser.extractPageContent` produisait déjà les
`Sentence` avec leurs offsets depuis l'API texte de PDFium — la matière
était en base depuis le Lot 12, seules les gardes de format la rendaient
inatteignable. Une page sans texte (planche scannée) est sautée plutôt que
d'interrompre la narration ; un PDF dont AUCUNE page ne porte de texte
masque les commandes TTS (`ReaderUiState.supportsTts`) au lieu d'afficher
un bouton sans effet. Le minuteur de sommeil suit la même règle.

Déclenché par le retour des premiers bêta-testeurs : le périmètre
« affichage seul » était lu comme « le PDF ne marche pas », l'attente
réelle sur ce format étant l'écoute, pas la lecture visuelle.

**Volet 2b — surlignage mot-à-mot (toujours conditionné) :** extraction de
`BoundingBox` par mot via l'API texte de PDFium et surlignage synchronisé
sur le bitmap de page. Reste conditionné à la validation d'un alignement
fiable des rectangles : un PDF s'écoute aujourd'hui, il ne se surligne pas.
`TtsCapabilities.wordTimestamps` reste la seule autorité sur le mot-à-mot —
jamais d'interpolation simulée (K12/Blueprint §8).

## Rationale

Mieux vaut exceller sur un périmètre maîtrisé que de moyenner la qualité
sur deux formats simultanément.

## Consequences

Le PDF est désormais atteignable de bout en bout : import, bibliothèque,
lecture visuelle et narration audio. Ne reste hors périmètre que le
surlignage mot-à-mot, et la bascule SCROLL/PAGED — sans objet pour un
format nativement paginé.

La communication produit doit porter sur ce seul écart restant, et non
plus sur « PDF absent » : c'est la formulation précédente, héritée du
périmètre v1, qui a produit la déception des premiers bêta-testeurs.

## Alternatives Considered

- **Support PDF complet dès la v1** : rejeté, risque de dégrader la
  qualité des deux formats simultanément.
- **Abandon complet du PDF** : rejeté, il existe une demande réelle et
  l'architecture (Document Model unifié, ADR-005) le permet sans coût
  structurel supplémentaire.
