# ADR-027 : Contexte textuel dans le Locator pour ré-ancrer les positions

**Status :** Accepted
**Date :** 2026-11-12

## Context

Toute position est un `Locator` (Blueprint §3.2, `domain/valueobject/Locator.kt`) :
`resourceHref`, `chapterIndex`, `paragraphIndex`, `charOffset`, et `pageOffsetY`
pour le PDF. `charOffset` est un décalage dans le **texte produit par le
parseur** (`JsoupChapterParser`), pas dans le fichier source.

Quand le parseur évolue, ce texte change : une suite d'espaces regroupée, un
`<br>` rendu en saut de ligne, une liste qui garde ses puces. Les offsets
calculés avec l'ancienne version ne désignent plus le même passage.
`RefreshStalePreAnalysesUseCase` régénère alors la pré-analyse et les
longueurs de chapitres (`PARSER_VERSION`, `FilePreAnalysisStore.kt`). Il ne
touche ni aux signets, ni aux annotations, ni à la position de reprise, qui
restent dans l'ancien espace d'offsets (audit UX, constat **D17**).

Cela bloque des correctifs déjà identifiés. D12 (`<br>` aplatis), D13
(listes et citations aplaties) et D14 (espaces non normalisés) modifient
tous le texte extrait. Les livrer aujourd'hui déplacerait sans bruit
chaque surlignage existant de quelques caractères, ou sur un autre mot.

Signets et annotations enregistrent déjà un `excerpt`, d'au plus 280
caractères (`Annotation.kt:70`, `Bookmark.kt:11`). Il sert à l'affichage :
il peut être tronqué, il est absent de la position de reprise, et il ne
dit pas où se trouve le point exact dans le passage. Il ne suffit pas à
ré-ancrer.

Il y a deux contraintes :

- CLAUDE.md et le Blueprint §3.2 font de `Locator` le **seul** value
  object de position. Un second système d'adressage est interdit.
- Le Locator du domaine encapsule celui de Readium sans l'exposer
  (ADR-011). Readium résout ce problème avec `Locator.Text`
  (`before` / `highlight` / `after`) : il ré-ancre une position en
  cherchant son texte environnant.

## Decision

1. **`Locator` porte un contexte textuel optionnel**, nouveau value
   object du domaine :

   ```text
   Locator
   ├── resourceHref, chapterIndex, paragraphIndex, charOffset, pageOffsetY   (inchangés)
   └── text: LocatorText?
         ├── before   les 32 caractères qui précèdent charOffset (moins en début de chapitre)
         └── after    les 32 caractères qui suivent charOffset (moins en fin de chapitre)
   ```

   Le contexte décrit un **point**, pas une plage. Une annotation garde
   ses deux `Locator`, début et fin, et chacun porte son propre contexte.
   Il n'y a donc pas de `highlight` : la plage se reconstitue en
   ré-ancrant ses deux bornes séparément.

2. **`charOffset` reste la source de vérité de la position.** Le contexte
   ne sert qu'à recalculer `charOffset` quand le texte du chapitre a
   changé. Il n'entre ni dans `compareTo`, ni dans l'égalité métier de
   deux positions, ni dans le calcul de progression.

3. **Le contexte est capturé à la création** de chaque `Locator` persisté :
   signet, borne d'annotation, position de reprise. Il est lu dans le
   texte du chapitre déjà chargé, sans accès fichier supplémentaire.
   Une page PDF sans texte, ou un chapitre pas encore chargé, donne
   `text = null`.

4. **Le ré-ancrage s'exécute quand le texte d'un livre change.** Quand
   `RefreshStalePreAnalysesUseCase` réécrit la pré-analyse d'un livre, le
   même traitement ré-ancre les `Locator` de ce livre, dans cet ordre :
   - chercher `before + after` dans le nouveau texte du même chapitre,
     espaces normalisés des deux côtés ;
   - une seule occurrence : `charOffset` devient la frontière entre
     `before` et `after` ;
   - plusieurs occurrences : garder la plus proche de l'ancien
     `charOffset` ;
   - aucune occurrence : chercher `before` seul, puis `after` seul, avec
     la même règle de proximité ;
   - toujours rien : laisser `charOffset` inchangé, borné à la longueur
     du chapitre, et compter la position comme non ré-ancrée. Ne jamais
     la supprimer.

   Le contexte est ensuite recapturé dans le nouveau texte. Le ré-ancrage
   d'un livre est **idempotent et repris s'il est interrompu** : chaque
   livre enregistre la version du parseur pour laquelle ses positions ont
   été ancrées, et le traitement ne s'arrête qu'une fois cette version
   égale à `PARSER_VERSION`.

5. **L'index de recherche plein texte n'est pas ré-ancré**, il est
   reconstruit. Ses offsets dérivent du texte et ne portent aucune donnée
   de l'utilisateur.

## Rationale

- **Un seul système d'adressage, conformément à la règle.** Le contexte
  est un attribut de `Locator`, pas un adressage parallèle. Toute
  position garde la même forme et les mêmes règles. Le contexte la rend
  seulement réparable.
- **Le mapping avec Readium reste sans perte** (ADR-011).
  `LocatorText(before, after)` correspond à `Locator.Text(before, null, after)`.
  Un `highlight` pourra être ajouté plus tard sans rompre le format, s'il
  devient nécessaire.
- **32 caractères de chaque côté**, soit 64 au total, suffisent à rendre
  une position unique dans un chapitre de roman, sauf refrain ou
  répétition volontaire. Le départage par proximité de l'ancien offset
  couvre ces cas. C'est aussi l'ordre de grandeur de la recommandation
  reçue (environ 30 caractères).
- **Un point plutôt qu'une plage** donne un traitement unique pour les
  signets, la reprise et les annotations, et évite de stocker deux fois
  le texte d'une sélection déjà présent dans `excerpt`.
- **Les espaces sont normalisés à la comparaison**, pas au stockage :
  c'est justement ce que D14 changera dans le texte extrait.

## Consequences

**Schéma et données**

- Nouvelles colonnes nullables pour chaque `Locator` aplati (`LocatorColumns`,
  `LocatorMapper.kt`) : `ReadingState`, `Bookmark`, et les deux bornes
  d'`Annotation`. Une migration Room ajoute ces colonnes, avec son test
  `MigrationTestHelper` dans le même commit (K4).
- Nouvelle colonne de version d'ancrage sur la publication.
- **Rattrapage obligatoire avant toute évolution du parseur.** Les positions
  déjà enregistrées n'ont pas de contexte. Une tâche de fond le capture
  dans le texte actuel, sur le modèle de `BackfillChapterLengthsUseCase`.
  Elle doit être livrée et avoir tourné **avant** le premier changement
  de `PARSER_VERSION` qui suivra (D12 à D14). Sans cela, ces positions
  ne seraient pas réparables.
- La sauvegarde et la synchronisation (`BackupModels.kt`, `SyncNowManager`)
  transportent le contexte. Une position venue d'un autre appareil reste
  ainsi ré-ancrable, ce qui aidera aussi à apparier les livres entre
  appareils (D57).

**Code**

- `Locator.kt` : nouveau champ `text: LocatorText?`, avec ses invariants
  par `require()` (longueurs bornées).
- La capture du contexte se fait là où les `Locator` persistés sont
  construits : `ReaderViewModel` pour la reprise et les signets,
  `AnnotationSelectionHandler` pour les annotations,
  `PlaybackOrchestrator` pour la position pendant la narration.
- `RefreshStalePreAnalysesUseCase` appelle le ré-ancrage. Celui-ci est
  testé en JVM avec des textes avant/après évolution du parseur : espaces
  regroupés, `<br>` devenu saut de ligne, passage dupliqué, passage
  supprimé.

**Documentation**

- Le Blueprint §3.2 est mis à jour **dans le même commit** que
  l'implémentation : `text` est ajouté à l'arbre de `Locator`, avec la
  règle « `charOffset` reste la source de vérité ».

**Limites**

- Si un passage disparaît du texte extrait (contenu masqué que D16
  cesserait d'afficher), sa position ne peut pas être ré-ancrée. Elle
  reste à l'offset borné, et elle est comptée.
- Un passage répété mot pour mot plusieurs fois peut être ré-ancré sur la
  mauvaise occurrence si le texte qui précède a beaucoup changé. Le
  départage par proximité rend ce cas rare.

## Alternatives Considered

1. **EPUB CFI** (Canonical Fragment Identifier). Écarté : ce serait un
   second système d'adressage à côté de `Locator`, ce que la règle
   interdit. Un CFI désigne en plus un nœud du XHTML source, alors que
   tout le Lecteur (pagination, TTS, surlignage mot à mot) travaille en
   offsets dans le texte extrait. Il faudrait convertir dans les deux
   sens à chaque usage, avec le parseur lui-même comme source d'écart :
   précisément le problème à résoudre.
2. **Conserver l'ancienne version du texte** de chaque chapitre et
   calculer un diff pour translater les offsets. Écarté : il faudrait
   garder le texte intégral de chaque livre pour chaque version du
   parseur, et la translation par diff est fragile quand le texte est
   restructuré (listes, paragraphes scindés).
3. **Ré-ancrer à partir de l'`excerpt` existant.** Écarté comme mécanisme
   principal : il est absent de la position de reprise, tronqué à 280
   caractères, et ne situe pas le point exact. Il reste un dernier
   recours possible pour une annotation sans contexte.
4. **Geler le parseur** et renoncer à D12–D14. Écarté : les vers, les
   listes et les dialogues resteraient aplatis.
