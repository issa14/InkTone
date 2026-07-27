# Tâche 4.11 — Validation contre un EPUB réel (amendement à la clôture de Phase 4)

**Pourquoi maintenant :** tout ce qui a été testé jusqu'ici (Phases 2 à 4) tourne contre des fixtures qu'on a nous-mêmes fabriquées à la main — `fixture-minimal.epub`, `fixture-multi-chapitre.epub` (3 chapitres plats). Une fixture qu'on construit soi-même a tendance à confirmer nos propres hypothèses sur la structure d'un EPUB, jamais à les remettre en question. Un vrai livre est le seul test qui peut révéler une surprise qu'on n'a pas anticipée — exactement le principe qui a fait gagner tout ce fil (Sherpa-ONNX, `onRangeStart`, `Href`/`Url`).

## Source retenue

**Les Misérables, Victor Hugo — Project Gutenberg #135**, œuvre complète, français, domaine public, EPUB3 disponible. Choix délibéré : c'est déjà le nom utilisé dans nos données de test depuis la Phase 1 (`PublicationTest`, `ToggleFavoriteUseCaseTest`) — cohérence amusante mais surtout, c'est une œuvre substantielle (5 tomes, dizaines de chapitres) avec une vraie structure éditoriale, pas un texte plat.

```bash
# URL a verifier au moment de l'execution (le format exact des URLs de
# telechargement Gutenberg a pu changer) :
curl -L -o les-miserables.epub "https://www.gutenberg.org/ebooks/135.epub3.images"
```

**Ne PAS committer ce fichier dans le dépôt** — contrairement aux fixtures synthétiques (quelques Ko, contenu qu'on a écrit), c'est un fichier tiers de plusieurs Mo. Il reste un artefact de test local/manuel, pas un fixture CI automatisé pour l'instant.

## Prédiction à vérifier en premier — la table des matières est probablement cassée

En relisant le code de la Tâche 4.1 (`DocumentModelExtractor.extract`) :

```kotlin
val toc = publication.tableOfContents.mapIndexed { index, link ->
    TableOfContentsEntry(title = link.title ?: "", chapterIndex = index)
}
```

C'est un mapping **plat**. Or `TableOfContentsEntry` a un champ `children` (Blueprint §7.5, domaine Phase 1) — jamais rempli, jamais testé, parce que `fixture-multi-chapitre.epub` a une TOC plate à 3 entrées. Les Misérables a presque certainement une structure **Tome → Livre → Chapitre**, sur 3 niveaux. Si `publication.tableOfContents` de Readium renvoie une hiérarchie (via les `children` de son propre modèle de `Link`) et qu'on l'aplatit sans récursion, soit on perd des niveaux, soit on obtient une liste plate de titres sans relation hiérarchique — visible immédiatement dans l'UI de TOC (Tâche 4.5) qui listera tout au même niveau.

**Ne pas corriger ça en aveugle avant de l'avoir observé.** Le but de cette tâche est de confirmer ou d'infirmer cette prédiction, pas de la traiter comme acquise.

## Autres points à surveiller, spécifiques à un vrai texte français

- **Dialogue au tiret cadratin** (« — Bonjour, dit-il. » plutôt que des guillemets), omniprésent dans Hugo — vérifier que le découpage en phrases (`ContentTokenizer` Readium ou notre `TxtPublicationParser`) ne casse pas au milieu d'une réplique.
- **Volume réel de texte** — premier test de mémoire/temps d'ouverture non trivial, même sans benchmark formalisé (différé à la Phase 6 selon la Tâche 4.9).
- **Accents et ponctuation française** (é, è, à, ç, œ, « », …) — stress-test basique de l'encodage UTF-8 de bout en bout.
- **Métadonnées réelles** (auteur, image de couverture, description) — jamais exercées par nos fixtures minimales.

## Procédure

```bash
adb push les-miserables.epub /sdcard/Download/les-miserables.epub
```

1. Importer le fichier via le sélecteur SAF de l'app (pas de copie manuelle en base — passer par le vrai chemin `infrastructure/storage`).
2. Ouvrir le livre — noter le temps d'ouverture perçu, tout crash ou ANR.
3. Ouvrir la table des matières — **comparer sa structure à celle du livre réel** (vérifier sur `gutenberg.org/ebooks/135` la structure Tome/Livre/Chapitre attendue).
4. Naviguer sur au moins 5 chapitres non consécutifs, y compris le premier et le dernier.
5. Lancer le Palier 1 (TTS natif) sur au moins 3 phrases contenant du dialogue à tiret cadratin.
6. Fermer et rouvrir l'app — vérifier la reprise à la position exacte (K3).

## Rapport à produire

`docs/execution/VALIDATION_EPUB_REEL_LES_MISERABLES.md` :

```markdown
# Validation contre un EPUB réel — Les Misérables (Gutenberg #135)

**Date :** [date d'exécution]

## Résultat de la prédiction TOC
[Confirmée / Infirmée] — [détail de ce qui a été observé]

## Bugs trouvés
[Liste, chacun avec : symptôme observé, cause identifiée si connue,
corrigé dans quel commit — ou "non corrigé, à traiter en Phase X" si
le correctif dépasse le périmètre de cette validation]

## Ce qui a bien fonctionné sans surprise
[Court — ne pas gonfler artificiellement cette section]
```

**Règle explicite :** ce rapport documente ce qui a été observé, pas ce qu'on espérait observer. Si tout fonctionne du premier coup, le dire simplement — ne pas chercher des problèmes qui n'existent pas pour justifier la tâche.

## Critère de clôture

- Le rapport existe, avec la prédiction TOC explicitement tranchée (pas laissée en suspens).
- Tout bug trouvé et corrigé a son propre commit, avec le comportement réel documenté dans le code concerné (même discipline que K6 en Tâche 4.3) — pas juste mentionné dans le rapport puis oublié.
- Tout bug trouvé mais dont le correctif dépasse le périmètre de cette tâche est explicitement reporté vers la phase appropriée, pas laissé sans destination.

**Commit du rapport :** `Documente la validation contre un EPUB reel (Les Miserables, Gutenberg #135)`
