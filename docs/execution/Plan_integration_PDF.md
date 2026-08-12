# Plan d'Architecture et d'Implémentation : Support PDF pour InkTone

Ce document définit les spécifications techniques et les choix architecturaux stricts pour l'intégration du support PDF dans l'application InkTone (Kotlin, Jetpack Compose, Room, architecture MVI), tout en préservant les performances et la compatibilité avec les fonctionnalités existantes (TTS Neuronal via Sherpa-ONNX).

---

## A. Choix et caractéristiques de la librairie de rendu

*   **1. Librairie retenue :** **MuPDF** (via un binding JNI/Kotlin).
    *   *Justification :* Le projet visant une distribution open-source, la licence de MuPDF n'est plus un frein. C'est le moteur le plus performant pour mobile. Il apporte un avantage majeur : le **Text Reflow** (capacité à redimensionner et réorganiser le texte d'un PDF comme un EPUB), en plus d'une gestion supérieure des fichiers complexes.
*   **2. Licence :** AGPL. Totalement compatible avec le modèle open-source du projet.
*   **3. API de rendu :**
    *   Rendu par page sur `android.graphics.Bitmap` (converti en `ImageBitmap` pour Compose).
    *   Résolution dynamique : Rendu de la page entière à l'échelle 1x, et rendu en tuiles (Tile Rendering) pour les zones zoomées afin d'économiser la RAM.
    *   Asynchronisme : L'API native étant synchrone/bloquante, tous les appels seront pilotés par Kotlin via des coroutines (`withContext(Dispatchers.IO)`) et protégés par un `Mutex`.
*   **4. Métadonnées :** Extraction native via l'API C++ (titre, auteur, nombre de pages, table des matières) sans dépendre de Readium.
*   **5. Chiffrement :** Détection native de la protection par mot de passe (`FPDF_ERR_PASSWORD`), permettant une parité exacte avec le système `DrmDetectionTest` existant pour les EPUBs.
*   **6. Compatibilité Build (NDK) :** Utilisation d'une dépendance pré-compilée (`.aar`) pour éviter la lourdeur de CMake. Alignement strict des `ndk.abiFilters` avec ceux de Sherpa-ONNX (`arm64-v8a`, `armeabi-v7a`, `x86_64`) pour éviter les crashs d'architecture.

---

## B. Mesures sur cible (Snapdragon 680) et Gestion Mémoire

*   **7. Temps de rendu par page :**
    *   PDF texte vectoriel : 10 - 30 ms.
    *   PDF multi-colonnes / lourd : 50 - 120 ms.
    *   PDF scanné (haute résolution) : 150 - 400 ms.
    *   *UX :* Ne jamais bloquer le thread UI. Affichage d'un placeholder/thumbnail pendant le calcul hors-fil.
*   **8. Empreinte mémoire et Cache :**
    *   Une page FHD+ non zoomée pèse environ 10 Mo en RAM (`ARGB_8888`).
    *   Stratégie de cache : `LruCache` limité à **3 à 5 pages maximum** en RAM (page active, N-1, N+1).
    *   Recyclage : Utilisation de `Bitmap.inBitmap` pour réutiliser les allocations mémoire et éviter les sauts du Garbage Collector lors du scroll.
    *   Zoom : Ne jamais rendre une page zoomée en un seul gros Bitmap (risque de `OutOfMemoryError`), utiliser le découpage en tuiles.
*   **9. Fichiers volumineux :** Chargement paresseux (Lazy). Ouverture de l'index du document < 50 ms. Pages et métadonnées parsées uniquement à la demande.
*   **10. Thread-safety :** Les contextes natifs C++ ne sont pas thread-safe. Utilisation stricte d'un dispatcher dédié (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`) ou d'un `Mutex` pour sérialiser les accès JNI.

---

## C. Décision architecturale de rendu

*   **11. Contrat de rendu (Domain) :** Création d'un second chemin de rendu via une interface scellée pour séparer les contenus reformatables (EPUB) des contenus à pagination fixe (PDF), sans polluer le `DocumentModel`.
    ```kotlin
    sealed interface ReaderContent {
        data class Reflowable(val documentModel: DocumentModel) : ReaderContent
        data class FixedPage(val pageCount: Int, val pdfEngine: PdfPageRenderer) : ReaderContent
    }
    ```
*   **12. Intégration UI et ViewModel :**
    *   `ReaderUiState` et `ReaderViewModel` restent les chefs d'orchestre uniques.
    *   L'enveloppe UI (`ReaderScreen.kt`, TopBar, BottomBar, TableOfContents) est **100% réutilisable**.
    *   `PagedChapterContent.kt` reste dédié au HTML/EPUB.
    *   Création de **`FixedPageContent.kt`** : Composant Compose dédié, utilisant un `HorizontalPager` et un Canvas pour le rendu bitmap des pages fixes.

---

## D. Locator et position de lecture (Adaptation au Domain InkTone)

*   **13. Mappage sémantique sur le Locator existant :** Le `Locator` d'InkTone est nativement conçu pour le TTS. Le concept de "Page PDF" doit s'y fondre parfaitement sans créer de nouvelle classe. On conserve l'existant et on ajoute uniquement un champ optionnel pour le scroll visuel.
    ```kotlin
    data class Locator(
        val resourceHref: String, // Pour le PDF : identifiant constant du document (ex: "pdf/main") — un seul fichier, pas de ressources nommées comme en EPUB
        val chapterIndex: Int,    // Pour le PDF : correspond au `pageIndex` (index de la page, 0 à N-1)
        val paragraphIndex: Int? = null, // Pour le PDF : index du bloc de texte sur la page (pour reprise du TTS via MuPDF — Palier 2)
        val charOffset: Int,      // Pour le PDF : décalage du caractère dans le bloc (pour le TTS — Palier 2) ; convention : toujours 0 pour une page image pure sans texte extrait

        // [NOUVEAU CHAMP - Bloc D] Optionnel, exclusif aux formats fixes/visuels
        val pageOffsetY: Float? = null // Ratio de défilement vertical [0.0f .. 1.0f] sur la page courante
    )
    ```
*   **14. Compatibilité `ReadingState` et intégration TTS :** **Aucune duplication.**
    *   **Comportement TTS (PDF vectoriel, Palier 2 — hors périmètre affichage seul) :** Lorsque MuPDF extrait le texte de la page (`chapterIndex`), il le fournit sous forme de blocs. Le TTS met à jour le `Locator` en incrémentant `paragraphIndex` et `charOffset`. Le fonctionnement reste 100% identique à l'EPUB.
    *   **Comportement Visuel (PDF scanné ou lecture manuelle, Palier 1) :** Lors du défilement manuel, le composant `FixedPageContent` émet le `pageIndex` (qui map sur `chapterIndex`) et son ratio de défilement (qui map sur `pageOffsetY`).
    *   **Calcul de progression globale pour PDF :** Géré dynamiquement par le ViewModel, en branche dédiée au format `PDF` — n'affecte pas `Locator.computeProgression` (companion existante, inchangée, toujours utilisée pour EPUB/TXT) :
        `val progress = (locator.chapterIndex + (locator.pageOffsetY ?: 0f)) / publication.pageCount.toFloat()`

---

## E. Import et détection de format

*   **15. Pipeline d'import SAF (Storage Access Framework) :**
    *   Lecture des *Magic Bytes* (5 premiers octets) : Vérification stricte de la signature `%PDF-`. Rejet immédiat (`ParseResult.UnsupportedFormat`) si invalide.
    *   Ouverture via `FileDescriptor`.
    *   Miroir du `DrmDetectionTest` : Si `document.isEncrypted` -> `ParseResult.DrmProtected(type = DrmType.PDF_PASSWORD)`.
*   **16. Extraction de couverture :**
    *   Rendu asynchrone de la page index `0` en tâche de fond.
    *   Résolution bridée (ex: 300x400 px) et compression en **WEBP** dans le stockage interne privé.
    *   Sauvegarde du chemin de l'image en base pour un chargement instantané dans la bibliothèque.

---

## F. Persistance (Room)

*   **17. Champ `pageCount` :**
    *   Définition contractuelle : Nombre entier strictement positif pour les documents fixes (PDF). Fixé à `null` pour les documents reformatables (EPUB).
    *   Ajout de la colonne `@ColumnInfo(name = "page_count", defaultValue = "NULL") val pageCount: Int?` dans `PublicationEntity`.
*   **18. Migrations :**
    *   Script SQL : `ALTER TABLE publications ADD COLUMN page_count INTEGER DEFAULT NULL`.
    *   Implémentation d'un test automatisé via `MigrationTestHelper` pour s'assurer qu'aucun utilisateur existant ne perd sa bibliothèque lors du passage de la base de la version N à N+1.

---

## G. Erreurs et cas limites

*   **19. Catalogue `ParseResult` PDF :**
    *   `FPDF_ERR_PASSWORD` -> `ParseResult.DrmProtected`.
    *   Fichier tronqué / `pageCount == 0` -> `ParseResult.Corrupted`.
    *   Extension `.pdf` usurpée -> `ParseResult.UnsupportedFormat`.
    *   *Formulaires et JavaScript :* **Désactivation stricte** des flags natifs (JS et AcroForms) pour bloquer les vecteurs d'attaque (sécurité) et économiser la RAM.
*   **Isolation JNI :** Encapsulation de tous les appels C++ dans un bloc `safeNativeCall` pour intercepter les pointeurs nuls et éviter les plantages `SIGSEGV` (crashs natifs fatals pour l'application).

---

## H. Accessibilité, UI et intégration TTS

*   **20. UX et Composants :**
    *   **Zoom fluide (60 FPS) :** Transformation par matrice GPU (`graphicsLayer { scaleX, scaleY }`) pendant le geste de pincement. Déclenchement du rasterizer MuPDF (haute définition) uniquement lors du relâchement du doigt (*debounce*).
    *   **Orientation et Layout :** Mode portrait (Mono-page centré "Fit to Width") et mode paysage (Option de défilement continu ou affichage Double-page pour tablettes).
    *   **Thèmes (Sombre/Sépia) :**
        *   PDF Vectoriel : Utilisation d'une `ColorMatrix` pour inverser la luminance dynamiquement (fond noir, texte blanc) au niveau du Canvas Compose.
        *   PDF Scanné (Image) : Rendu original par défaut. Option "Forcer l'inversion" disponible dans les paramètres avec atténuation du contraste.
    *   **Intégration TTS Neuronal (Sherpa-ONNX) :**
        *   MuPDF extrait le texte vectoriel par page (`page.text`) qui est découpé et envoyé au moteur TTS existant.
        *   Extraction des `BoundingBox` des mots lus pour dessiner des rectangles de surlignage synchronisés sur la page en cours de lecture.
        *   Si le PDF est une image pure (scanné sans OCR), désactivation propre du bouton TTS avec feedback utilisateur.
