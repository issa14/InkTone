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

## Sauvegarde locale (`BackupManager`, Tâche 8.5) : fichier en clair

Le fichier JSON exporté (signets, règles de prononciation, progression
de lecture, sessions) n'est **pas chiffré** — décision tranchée en Tâche
9.2.4, jamais spécifiée en Phase 8. Le risque reste faible tant que
l'export SAF reste sur un stockage local choisi par l'utilisateur (aucune
transmission automatique), mais **un utilisateur qui copie ce fichier
vers un cloud personnel (Drive, etc.) doit savoir qu'il n'est pas
protégé** — le contenu (quels livres, quel passage, quand) est lisible
par quiconque a accès au fichier. Si un besoin de confidentialité plus
fort émerge, le chiffrement du fichier exporté (ex. passphrase utilisateur
+ AES-GCM) devra faire l'objet d'un ADR dédié, pas d'un ajout silencieux.
