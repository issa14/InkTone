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

## Sauvegarde locale (`BackupManager`) : chiffrée de bout en bout

Le fichier exporté (signets, règles de prononciation, progression de
lecture, sessions) est chiffré **AES/GCM**, avec une clé dérivée par
PBKDF2 d'un mot de passe saisi par l'utilisateur (`BackupCrypto`,
`BackupPasswordDialog`). Le mot de passe n'est stocké nulle part : le
perdre rend la sauvegarde définitivement illisible, y compris pour son
propriétaire.

`importFrom` accepte encore les exports antérieurs en JSON clair, que
`BackupCrypto.isEncryptedEnvelope` reconnaît à l'absence de son en-tête —
compatibilité ascendante volontaire. **L'export, lui, ne produit plus
jamais de fichier en clair.**

Cette section a longtemps affirmé l'inverse, après que le chiffrement eut
été ajouté sans qu'elle soit mise à jour. Elle a induit en erreur une
rédaction du README, qui a publié l'affirmation périmée. C'est exactement
ce que la règle « le code fait foi » cherche à éviter : **vérifier dans
`BackupManager.exportTo` avant de citer cette section.**
