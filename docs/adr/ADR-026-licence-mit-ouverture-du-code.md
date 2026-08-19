# ADR-026 : Publication du code sous licence MIT

**Status :** Accepted
**Date :** 2026-08-20

## Context

Le dépôt n'a jamais porté de fichier `LICENSE`. En droit d'auteur, une
œuvre publiée sans licence est **tous droits réservés** : jusqu'ici, le
code d'InkTone était donc lisible sur GitHub mais juridiquement
inutilisable par un tiers — un état par défaut, jamais décidé.

Cet état par défaut n'était pas neutre : il a servi de prémisse à des
décisions techniques. L'ADR-021 et l'ADR-022 écartent Piper au motif que
la GPL-3.0 est « incompatible avec une application commerciale à code
source fermé ». Le choix du moteur de synthèse vocale — la fonctionnalité
signature du projet — repose donc explicitement sur l'hypothèse d'un code
fermé. Publier une licence permissive sans traiter cette contradiction
laisserait deux ADR acceptés appuyés sur une prémisse fausse.

Trois faits contraignent le choix :

1. **Le dépôt contient du code tiers.**
   `infrastructure/tts/.../com/k2fsa/sherpa/onnx/Tts.kt` (Copyright 2023
   Xiaomi Corporation) provient de sherpa-onnx, sous Apache-2.0. Aucune
   licence choisie ici ne peut relicencier ce fichier.
2. **Toutes les dépendances liées sont permissives** : Readium
   (BSD-3-Clause), ONNX Runtime (MIT), Pdfium (BSD-3-Clause), AndroidX /
   Compose / Room / Hilt / Media3 (Apache-2.0), OkHttp, AppAuth. Aucune
   dépendance copyleft n'est présente dans le graphe.
3. **Les modèles téléchargés à l'exécution ont leurs propres licences**,
   indépendantes de celle du code : Kokoro-82M (Apache-2.0) et NeMo
   FastConformer CTC (CC-BY-4.0, **attribution obligatoire dans
   l'application distribuée**).

## Decision

Le code source écrit pour InkTone est publié sous **licence MIT**.

1. Un fichier `LICENSE` à la racine porte le texte MIT intégral, suivi
   d'une clause de portée : la licence couvre le code écrit pour ce
   projet, et ne relicencie ni le code tiers présent dans le dépôt, ni
   les bibliothèques liées, ni les modèles téléchargés à l'exécution.
2. `THIRD_PARTY_NOTICES.md` recense nommément ce qui échappe à la licence
   MIT : le fichier `Tts.kt` maintenu sous Apache-2.0 avec son en-tête de
   copyright d'origine, les bibliothèques liées, et les modèles avec leur
   licence respective.
3. **L'attribution CC-BY-4.0 du modèle d'alignement est une obligation de
   l'application distribuée, pas seulement du dépôt.** Elle est affichée
   dans l'écran « À propos » (accordéon « Architecture Tech & Licences »)
   et ne peut en être retirée.
4. **L'ADR-021 et l'ADR-022 ne sont pas remplacés.** Leur prémisse
   « application commerciale à code source fermé » cesse d'être vraie,
   mais leurs décisions restent valides pour des raisons indépendantes de
   cette prémisse, explicitées ci-dessous. Le présent ADR est la référence
   pour la question de licence ; ces deux-là restent la référence pour le
   choix du moteur TTS.

## Rationale

**Pourquoi une licence permissive plutôt que le statu quo.** L'absence de
licence n'était pas une protection choisie mais un défaut de décision, et
elle rendait le dépôt public inutilisable — la forme la plus inutile de
publication. Trancher explicitement valait mieux que laisser le défaut
juridique tenir lieu de politique.

**Pourquoi MIT plutôt qu'Apache-2.0.** Les deux sont permissives et
compatibles avec l'intégralité du graphe de dépendances. MIT est plus
courte et sans ambiguïté de lecture ; Apache-2.0 apporte en plus une
concession explicite de brevets. Pour un projet solo sans portefeuille de
brevets ni exposition brevetaire identifiée, cette clause protège surtout
les contributeurs futurs d'un risque théorique, au prix d'un texte que
peu de gens lisent. Le choix retenu privilégie la lisibilité. **Si une
concession de brevets devient souhaitable — contributions externes
régulières, adoption industrielle — le passage à Apache-2.0 fera l'objet
d'un ADR, et devra tenir compte du fait qu'il ne s'applique pas
rétroactivement aux copies déjà distribuées sous MIT.**

**Pourquoi cela ne rouvre pas le dossier Piper.** L'argument de l'ADR-022
survit intact au changement de licence, pour une raison qui n'a rien à
voir avec l'ouverture du code : MIT accorde explicitement le droit de
*vendre* des copies (« to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell »). Une voix dont les données d'entraînement
portent une restriction *non-commerciale* (`upmc-medium`, chaîne de
provenance Blizzard/CSTR) reste donc incompatible — davantage, même, que
sous un modèle fermé où l'usage commercial pouvait au moins être
circonscrit. Quant à `mls-medium`, il avait été écarté sur la qualité
vocale, un critère que la licence ne touche pas. Le choix de Kokoro tient
sur ses propres jambes.

**Ce que la prémisse invalidée changeait réellement.** Rien, sur le fond
technique : Kokoro (Apache-2.0) est compatible avec un projet fermé
comme avec un projet MIT. La contradiction était documentaire, pas
architecturale — mais un ADR accepté qui s'appuie sur un fait devenu faux
est exactement le genre de dette que le §17.2 du Blueprint (« le code
fait foi ») demande de traiter plutôt que de laisser vieillir.

## Consequences

- N'importe qui peut désormais recompiler InkTone, le modifier et le
  redistribuer, y compris commercialement et sous un autre nom, sans
  contrepartie autre que la conservation de la notice de copyright.
  **C'est l'effet recherché d'une licence permissive, pas un effet de
  bord** — sur le Play Store, l'identité du projet ne tient plus qu'au
  nom, à l'icône et au keystore de signature, jamais à la licence.
- Une licence permissive publiée est en pratique irréversible : les
  copies distribuées sous MIT le restent, même si le dépôt change de
  licence plus tard. Un changement futur ne vaudrait que pour l'avenir.
- Toute nouvelle dépendance copyleft (GPL, AGPL) devient incompatible
  avec la distribution du projet sous MIT et doit être refusée à
  l'entrée, au même titre qu'avant — la raison change, la règle non.
- Tout nouveau fichier tiers ajouté au dépôt doit conserver son en-tête
  de licence d'origine et être inscrit dans `THIRD_PARTY_NOTICES.md` dans
  le même commit.
- Toute nouvelle ressource soumise à attribution (modèle, police, jeu de
  données) doit apparaître dans l'écran « À propos », pas seulement dans
  le dépôt.

## Alternatives Considered

- **Apache-2.0.** Écartée pour ce projet à ce stade : la concession de
  brevets qu'elle ajoute ne répond à aucun risque identifié ici, et son
  texte est nettement plus long. Reste le candidat naturel si le projet
  accueille des contributions externes régulières.
- **GPL-3.0 / AGPL-3.0.** Écartées : le copyleft imposerait sa licence à
  tout dérivé, ce qui est un choix militant défendable mais sans rapport
  avec l'objectif d'accessibilité du projet — et incohérent avec le fait
  que l'ADR-022 rejette précisément une dépendance GPL.
- **Rester sans licence (tous droits réservés).** Écartée : c'est l'état
  qu'on corrige. Un dépôt public sans licence donne l'illusion de
  l'ouverture sans en accorder aucun droit.
- **Double licence (MIT pour le code, commerciale pour une édition
  Play Store).** Écartée : elle suppose une infrastructure de licence et
  de contribution (CLA, suivi des ayants droit) sans commune mesure avec
  un projet solo, et pour un bénéfice nul tant qu'aucun tiers ne
  contribue.
