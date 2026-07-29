# Politique de confidentialité — InkTone

**Dernière mise à jour :** 29 juillet 2026

Ce document décrit précisément ce qu'InkTone collecte, stocke et transmet
— vérifié contre le code source à la date ci-dessus (Tâche 9.5.2), pas
un gabarit générique. Toute évolution de ce comportement doit mettre à
jour ce document dans le même commit (même discipline que le reste du
projet, voir `CONTRIBUTING.md`).

## Ce qu'InkTone ne fait jamais

- **Aucun compte utilisateur.** Aucune identité, aucun email, aucun
  identifiant personnel n'est demandé ni créé.
- **Le contenu de vos livres ne quitte jamais votre appareil.** Aucun
  texte, aucune annotation, aucun signet n'est transmis à un serveur.
  L'application fonctionne hors ligne par conception.
- **Aucune vente ni partage de données** avec des tiers, à quelque fin
  que ce soit.

## Ce qu'InkTone stocke localement, sur votre appareil uniquement

- Votre bibliothèque (métadonnées des livres importés), votre
  progression de lecture, vos signets, vos annotations, vos statistiques
  de lecture, vos règles de prononciation personnalisées et vos réglages.
  Rien de tout cela ne quitte l'appareil, sauf action explicite de votre
  part (voir « Sauvegarde locale » ci-dessous).

## Les deux seuls accès réseau de l'application

1. **Téléchargement du modèle de voix neuronale (optionnel).** Pour
   activer la narration de meilleure qualité (Palier 2, voix Kokoro),
   l'application télécharge un fichier de modèle vocal depuis
   `github.com/k2-fsa/sherpa-onnx` (release publique du projet
   open-source qui fournit ce moteur), vérifié par empreinte
   cryptographique avant utilisation. Ce téléchargement est optionnel :
   la lecture visuelle et la narration de base (moteur natif Android)
   fonctionnent sans lui.
2. **Rapports de crash (opt-in, désactivé par défaut).** Si vous
   l'activez explicitement dans les réglages ou à l'installation, un
   rapport de crash technique (trace d'erreur, version de l'application,
   modèle d'appareil) peut être transmis en cas de plantage. **Ce rapport
   ne contient jamais le contenu de vos livres ni vos annotations.**
   Réversible à tout moment dans Réglages → Confidentialité.

Aucun autre accès réseau n'existe dans l'application.

## Sauvegarde locale (export/import)

Vous pouvez exporter vos signets, règles de prononciation, progression
et statistiques dans un fichier JSON, via l'emplacement de stockage de
votre choix (SAF Android — jamais un accès disque non contrôlé). **Ce
fichier n'est pas chiffré.** Si vous le copiez vers un service cloud
personnel, sachez qu'il reste lisible par quiconque y a accès. Ce fichier
ne contient jamais le texte de vos livres, uniquement des métadonnées de
progression et vos données personnalisées (signets, règles).

## Permissions Android utilisées

| Permission | Usage |
|---|---|
| `INTERNET` | Téléchargement du modèle de voix (voir ci-dessus) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Lecture audio continue quand l'écran est éteint |

Aucune permission de stockage étendu, de localisation, de contacts, de
caméra ou de micro n'est demandée.

## Contact

Projet développé de façon indépendante. [À compléter : adresse de
contact avant publication sur le Play Store.]
