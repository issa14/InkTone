# Directives de Développement — InkTone UI Rebuild

## Stack & Architecture
- **Langage** : Kotlin (Jetpack Compose, Kotlin First).
- **Architecture** : Clean Architecture + MVVM / Unidirectional Data Flow (UDF).
- **Design System** : Material 3 (`androidx.compose.material3`).
- **Image Loading** : Coil 3.x (`coil-compose`).
- **Domaine** : Readium, Text-to-Speech, CTC Alignment.

## Règles Strictes (Garde-fous)
1. **Pas d'Emojis** : N'utilise AUCUN emoji dans le code Kotlin ou les ressources UI (ex: pas de `"⏹ Arrêter"`). Utilise exclusivement des icônes Vectorielles (`AppIcons` ou `Icons.Default`).
2. **Pas d'Arrondi à 0%** : Les badges de progression ne doivent jamais afficher `0%` si une lecture a commencé ; arrondir intelligemment pour refléter au moins `1%` dès que la lecture est initiée.
3. **Séparation Strict Chrome UI / Thème de Lecture** : Le Material You / Dynamic Color ne s'applique QU'AU chrome de l'application (TopBar, Drawer, Dialogs), JAMAIS aux thèmes de lecture (`LIGHT`, `DARK`, `SEPIA`) du Reader.
4. **Modèle de Domaine Sacré** : Ne modifie pas `Chapter`, `Paragraph`, ou `Sentence` d'une manière qui casserait le moteur TTS ou le système de timestamp CTC. `ParagraphStyle` et `StructuralBlock` doivent être des extensions additives.
5. **Composabilité Fine** : Préfère la composition par briques (`SelectableSentence`) plutôt que d'envelopper de grands conteneurs complexes dans `SelectionContainer`.

## Style de Code
- Utilise StateFlow et `collectAsStateWithLifecycle()` dans Compose.
- Sépare clairement la logique UI (Composables) des Modèles de Vue (ViewModels).
- Utilise les tokens du système de design centralisé (`core/designsystem`).