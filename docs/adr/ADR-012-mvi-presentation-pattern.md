# ADR-012 : MVI comme pattern de présentation

**Status :** Accepted
**Date :** 2026-07-26

## Context

La couche Presentation doit s'appuyer sur un pattern d'état normatif ;
sans cela, chaque contributeur — humain ou agent — tranche différemment
la gestion d'état d'un écran, produisant des divergences difficiles à
maintenir. Le legacy a éprouvé MVI avec succès sur plusieurs écrans.

## Decision

MVI (Model-View-Intent) est formalisé au §4.4 comme pattern unique de
présentation : un état unique et immuable par écran, des intents
explicites en entrée, des effets ponctuels acheminés par un canal dédié.

## Rationale

Prévisibilité du flux de données, testabilité des ViewModels en pur JVM
sans dépendance Android, cohérence de structure entre tous les écrans du
produit.

## Consequences

Un peu de cérémonie par écran (définition de l'état, des intents, des
effets). En échange, chaque écran se teste par la formule « intent
entrant → état attendu », sans instrumentation Android.

## Alternatives Considered

- **MVVM libre**, sans contrat d'état formel : rejeté — c'est précisément
  le défaut (états multiples divergents) que MVI corrige.
- **Molecule ou autres frameworks réactifs** : rejetés, dépendance
  supplémentaire sans besoin démontré au-delà de ce que MVI apporte déjà.
