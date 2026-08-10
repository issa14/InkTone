# Identité produit — InkTone

## 0. Métadonnées

| | |
|---|---|
| Version | 0.1 |
| Date | 10/08/2026 |
| Statut global | Complet — toutes les sections (§1 à §3.4) sont validées |
| Document compagnon | `UX_FLOW_DESIGN.md` (flux/écrans), ce document (marque/identité) |

Convention de statut utilisée dans tout le document : **validé** / **proposé — à valider** / **à définir**.

---

## 1. Positionnement — validé (10/08/2026)

**Positionnement**

InkTone est le nouveau venu du foyer : celui qui a beaucoup voyagé et revient avec des histoires inépuisables. Celui qu'on a envie d'écouter quelle que soit notre humeur — pas parce qu'il s'impose, mais parce qu'il sait toujours quoi raconter. Dans ses histoires, on se reconnaît, on apprend, on grandit un peu.

**Pitch condensé**

InkTone est le compagnon de lecture qu'on a toujours envie d'écouter, quelle que soit notre humeur — celui dans les histoires duquel on se reconnaît et on grandit.

*Note : le mot "écouter" est volontairement conservé plutôt que "lire" — il correspond littéralement à la fonctionnalité centrale de l'app (narration TTS intégrée) et ne doit pas être lissé en formulation générique.*

**Repères techniques du positionnement**

- Lecteur EPUB premium Android avec narration TTS neuronale intégrée, niveau visé comparable à Kindle / Apple Books / Kobo.
- Francophone en priorité, offline-first, pensé pour du matériel milieu de gamme (Snapdragon 680 / Android 14).

**Ce qu'InkTone n'est pas**

- Pas un prof qui fait la leçon — on apprend en douceur à travers l'histoire, jamais en cours magistral.
- Pas envahissant — présent quelle que soit l'humeur, jamais insistant.
- Pas un inconnu à qui on ne fait pas confiance — la relation est celle d'un proche, pas d'un outil distant.

---

## 2. Personnalité de marque / ton

**Adjectifs de marque — validé (10/08/2026)**, dérivés directement du positionnement (§1) :

- **Voyageur** — a du vécu, n'est pas un inconnu aseptisé.
- **Généreux** — les histoires ne s'épuisent jamais.
- **Disponible sans s'imposer** — présent quelle que soit l'humeur, jamais insistant.
- **Proche** — "nouveau venu du foyer", pas un outil distant.

**Traduction en microcopy française — validé (10/08/2026)**

Règles générales :

- **Adresse** : vouvoiement non rigide — "vous", mais sans "veuillez" ni formules de politesse empilées ; impératif direct plutôt que périphrase polie.
- **Voix** : impersonnelle partout (pas de "je"), **à une exception** : les accroches de narration TTS (message d'amorce de lecture, ex. *"Je vous lis [Titre], chapitre 4"*) peuvent utiliser le "je", parce que la fonctionnalité met littéralement une voix qui lit à la première personne. Nulle part ailleurs (permissions, erreurs système, réglages) — pour éviter la dérive vers un ton "mignon/gadget" qui desservirait le positionnement premium (§1).
- **Jamais** : point d'exclamation, urgence fabriquée, "Erreur :" en préfixe, justification excessive.

Traduction des adjectifs de marque (§2) en principes concrets :

| Adjectif | Principe microcopy | Exemple |
|---|---|---|
| Disponible sans s'imposer | Propose, n'exige pas ; pas d'impératif forcé, pas d'urgence fabriquée | *"Ce fichier ne s'est pas laissé lire. Réessayez, ou choisissez-en un autre."* |
| Généreux | Informe utilement sans s'étaler | *"Synchronisation impossible cette fois. Nouvel essai au prochain lancement."* |
| Voyageur | Pointe de registre narratif dans les moments non-critiques (onboarding, états vides) — jamais dans une erreur, où la clarté prime | *"Cette bibliothèque n'attend qu'un premier livre pour voyager."* |
| Proche | Adresse directe, jamais institutionnelle | *"Bienvenue. Vos histoires vous attendent, où que vous en soyez."* |

Autres exemples de référence :

- Fin de livre : *"Terminé. Un autre livre pour continuer le voyage ?"*
- Permission stockage (SAF) : *"Accès aux fichiers nécessaire pour retrouver vos livres sur l'appareil."*
- Amorce de narration TTS (exception "je") : *"Je vous lis [Titre], chapitre [N]."*

---

## 3. Identité visuelle

### 3.1 Logo — **validé**

- Silhouette : cacatoès émergeant d'un livre ouvert, noir sur fond clair.
- Usage : zone de protection = safe zone adaptive icon standard (66/108), ne jamais recadrer en dessous.
- Interdits : pas de déformation, pas de recolorisation hors palette validée (§3.2), pas de version "aplatie" sans le contraste blanc des pages/œil.
- Assets sources générés : `ic_launcher_foreground.png`, `ic_launcher_monochrome.png`, `ic_notification.png`, `playstore_icon_512.png` (voir échange du 10/08/2026).

### 3.2 Palette

**Icône de l'application — validé (10/08/2026)**

| Rôle | Valeur | Justification |
|---|---|---|
| Icône (launcher, splash, Play Store) | **Noir `#000000`** | Intemporel, cohérent sur tous les supports, aucune collision sémantique avec les couleurs de statut (erreur/destructif) |

**Accent d'interface — validé (10/08/2026)**

Le bordeaux (`#7A1F3D`, ancienne teinte ReadFlow) a été écarté pour l'usage répété en interface : trop proche de la famille rouge réservée aux états d'erreur/destructifs dans l'écosystème Material/Android, et trop saturé pour une app dont la promesse repose sur le calme de lecture ("silent chrome").

Rampe tonale proposée à partir de Deadly Depths (`#19113B`, teinte 251°/55%/15% en HSL), avec un palier dédié au mode sombre pour rester lisible (contrastes vérifiés WCAG) :

| Token | Hex | Usage | Contraste / fond crème `#FBFAF6` | Contraste / fond sombre `#121212` |
|---|---|---|---|---|
| `Accent900` | `#19113B` | Emphase forte, usage ponctuel (mode clair uniquement) | 16.97:1 | — |
| `Accent700` | `#2C1E67` | Accent principal — mode clair (icônes actives, boutons, liens, TTS) | 13.56:1 | — |
| `Accent500` | `#7661D1` | Accent principal — mode sombre (même rôle que 700, adapté à la luminosité) | 4.58:1 | 3.92:1 |
| `AccentContainer300` | `#A698E1` | Fond teinté clair (badges, container) | — | 7.32:1 |
| `AccentContainer100` | `#E4DFF6` | Fond très légèrement teinté (sélection, highlight discret) | — | 14.46:1 |

`Accent700` et `Accent500` dépassent le seuil WCAG non-text (3:1) et s'approchent ou dépassent le seuil texte (4.5:1) sur leurs fonds respectifs — utilisables aussi bien pour des icônes que pour du texte de lien.

**Couleurs sémantiques (erreur, succès, avertissement)** — *à définir*, mais doivent explicitement **ne pas** chevaucher la famille rouge/bordeaux ni la famille violette de l'accent, pour rester distinguables des couleurs de marque.

### 3.3 Typographie — validé (10/08/2026)

**Distinction de principe** : la police de chrome UI (fixe, choix de marque, ci-dessous) est entièrement séparée de la police de lecture du texte du livre (configurable par l'utilisateur — hors charte de marque, ne jamais confondre les deux).

**Décision** : système à deux polices.

| Rôle | Police | Usage |
|---|---|---|
| Chrome fonctionnel | **Work Sans** | Navigation, boutons, réglages, libellés — tout le texte d'interface récurrent |
| Accent narratif ponctuel | **Literata** | Accroche de bienvenue (onboarding), carte "fin de livre" — moments non-fonctionnels uniquement, jamais dans les boutons/nav |

Classement argumenté des 5 candidats évalués (Work Sans, Literata, Source Sans 3, Inter, Public Sans) :

1. **Work Sans** — meilleur compromis : lettrage humaniste qui porte "proche/généreux", peu saturée dans l'écosystème Android (contrairement à Inter), diacritiques français propres, variable font légère.
2. **Literata** — la plus forte en personnalité de marque ("voyageur"/"histoires"), mais perd en netteté aux tailles UI très petites et brouille la frontière avec la police de lecture par défaut de Google Play Books si utilisée en chrome complet → cantonnée à l'accent narratif.
3. **Source Sans 3** — solide et irréprochable techniquement, mais communique "compétent" plus que "compagnon".
4. **Inter** — techniquement excellente mais devenue le nouveau standard de facto (Discord, Figma, GitHub…) ; la choisir apporte peu de différenciation par rapport à rester en Roboto.
5. **Public Sans** — la plus neutre, conçue pour l'accessibilité gouvernementale ; irréprochable en lisibilité, la moins expressive des cinq.

**Licences — vérifiées à la source (10/08/2026)** :

- Work Sans : SIL Open Font License 1.1, confirmé sur le dépôt GitHub officiel (`weiweihuanghuang/Work-Sans`). Usage commercial libre, variable font, 63 langues.
- Literata : SIL Open Font License 1.1, confirmé sur le dépôt GitHub officiel (`googlefonts/literata`). Usage commercial libre, variable font, 72 langues. À l'origine police de marque de Google Play Books, open-sourcée en 2018.
- Aucune obligation d'attribution visible dans l'app ; seule l'inclusion du fichier de licence avec les assets est requise par l'OFL.

**Police de lecture** — hors charte, reste entièrement configurable par l'utilisateur dans les réglages de lecture.

### 3.4 Iconographie — validé (10/08/2026)

**Décision** : Material Symbols, variante **Rounded**.

- **Mécanisme d'état** : contour (`FILL 0`) par défaut/inactif, plein (`FILL 1`) à l'état actif/sélectionné — écho direct au logo (silhouette pleine) au moment précis de l'interaction, cohérent avec "disponible sans s'imposer" (le chrome reste discret par défaut).
- **Grille** : 24dp, standard Material.
- **Justification pragmatique** : maintenu par Google, couverture quasi exhaustive (aucun risque d'icône manquante forçant un fallback incohérent), licence Apache 2.0, support natif Jetpack Compose.
- **Alternative écartée** : Tabler Icons (trait fin uniforme, MIT) — plus distinctif visuellement, mais sans dualité contour/plein native ; poids visuel identique partout, mécanisme d'état à reconstruire manuellement.

---

## 4. Principes de conception — validé

Consolidation des principes établis au fil du projet :

- **Zéro contrôle décoratif** — tout élément d'UI doit avoir une logique branchée avant livraison. Traité comme un défaut de première classe (6 instances éliminées à ce jour).
- **Mesure réelle plutôt qu'estimation** — ex. pagination `TextMeasurer` réelle plutôt que raccourcis d'estimation de caractères.
- **Root-cause only** — pas de correctifs en aval qui compensent un problème en amont.
- **Auditer le code, pas la doc** — les fichiers de statut peuvent mentir ; toute vérification passe par le code source et le comportement sur device réel.
- **Vérification des licences à la source primaire** — remonter aux corpus d'entraînement originaux, pas seulement aux métadonnées de hub.
- **Scoper les décisions avant de coder** — toute zone ambiguë (ex. accent d'interface ci-dessus) est consignée comme *proposée* jusqu'à validation explicite, jamais codée en dur en attendant.

---

## 5. Registre des décisions

| Décision | Statut | Date | Justification résumée |
|---|---|---|---|
| Icône app = noir | Validé | 10/08/2026 | Intemporel, pas de collision sémantique, cohérence multi-supports |
| Accent interface = rampe Deadly Depths (5 paliers) | Validé | 10/08/2026 | Bordeaux trop saturé/chaud pour usage répété + collision avec rouge d'erreur ; Deadly Depths seul seuil trop sombre en mode sombre, d'où la rampe |
| Positionnement produit (§1) | Validé | 10/08/2026 | "Le nouveau venu du foyer" — voyageur, histoires inépuisables, écouté quelle que soit l'humeur (mots d'Issa) |
| Adjectifs de personnalité de marque (§2) | Validé | 10/08/2026 | Voyageur, généreux, disponible sans s'imposer, proche |
| Traduction en microcopy (§2) | Validé | 10/08/2026 | Vouvoiement non rigide, voix impersonnelle sauf exception amorces TTS ("je") |
| Typographie chrome + accent narratif (§3.3) | Validé | 10/08/2026 | Work Sans (chrome), Literata (accent narratif ponctuel) — licences OFL 1.1 vérifiées à la source |
| Iconographie chrome (§3.4) | Validé | 10/08/2026 | Material Symbols Rounded, contour/plein pour l'état inactif/actif |
| Typographie (§3.3) | À définir | — | — |
| Iconographie chrome (§3.4) | À définir | — | — |

---

## 6. Annexe — tokens techniques (Compose)

À adapter au fichier de thème Compose existant (nommage à aligner sur ta convention actuelle) :

```kotlin
// Icône (hors thème runtime — utilisée dans les assets launcher uniquement)
val IconBrand = Color(0xFF000000)

// Accent d'interface — validé (10/08/2026)
val Accent900 = Color(0xFF19113B)
val Accent700 = Color(0xFF2C1E67)  // accent principal, mode clair
val Accent500 = Color(0xFF7661D1)  // accent principal, mode sombre
val AccentContainer300 = Color(0xFFA698E1)
val AccentContainer100 = Color(0xFFE4DFF6)

// Référence — couleur de fond du logo (non utilisée en UI, pour cohérence visuelle uniquement)
val LogoBackgroundReference = Color(0xFFFBFAF6)

// Typographie — chrome fonctionnel vs accent narratif ponctuel (§3.3)
// Work Sans et Literata : SIL OFL 1.1, à intégrer en variable font (poids 400/500 suffisent pour le chrome)
val ChromeFontFamily = FontFamily(Font(R.font.work_sans_variable))
val NarrativeAccentFontFamily = FontFamily(Font(R.font.literata_variable))
// Police de lecture : non déclarée ici — reste pilotée par les réglages utilisateur, hors thème de marque
```

*Toute entrée de ce tableau doit rester synchronisée avec le fichier de thème réel — en cas de divergence, le code fait foi, pas ce document (cf. principe §4 "Auditer le code, pas la doc").*
