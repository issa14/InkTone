# Contribuer à InkTone

## Le code fait foi

Aucun document de statut (CHANGELOG, notes d'avancement) ne doit affirmer
qu'une fonctionnalité est terminée sans citer le commit, le fichier ou le
test qui le prouve. Tout audit d'avancement se fait sur le code source,
jamais sur la documentation déclarative. Voir Blueprint §17.2.

## Toute décision d'architecture passe par un ADR

Nouveau fichier dans `docs/adr/`, format du Blueprint §15.2 — jamais de
suppression, un ADR remplacé passe en statut `Superseded`.

## Règles de dépendance entre modules

Encodées dans `build-logic/` (voir Blueprint §12.4). Une dépendance
interdite fait échouer le build, pas la revue de code.

## Conventions de code

Voir Blueprint §12.5 : nommage par concepts métier, Use Cases en verbe à
l'infinitif, aucun emoji dans le code de production, messages de commit
en français à l'impératif ("Corrige…", "Ajoute…", "Initialise…").
