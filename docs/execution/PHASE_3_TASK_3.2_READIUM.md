# Phase 3 — Tâche 3.2 : intégration Readium (parser EPUB)

**Dépend de :** Tâche 3.1 (close, Plan A confirmé)
**Portée volontairement limitée :** ouvrir un EPUB de test et lire ses métadonnées — pas encore l'extraction de contenu en `Sentence` (Tâche 3.4, qui nécessite de vérifier séparément le guide Readium « Extracting the content of a publication », non fait ici).

## Décision de version — verrouillée avant tout code

La dernière version documentée (3.2.0/3.3.0) exige `compileSdk 36`, Kotlin 2.3.20, Gradle 9.1.0 — incompatible avec notre configuration actuelle sans cascade de montées de version. **On fixe `readium_version = 3.0.0`** (`minSdk 21`, `compileSdk 34`, Kotlin 1.9.24 minimum), compatible tel quel avec `inktone.android.library`/`inktone.feature`. Migration vers une version plus récente : sujet d'un ADR dédié, pas une décision incidente de cette tâche.

## ⚠️ À vérifier par Claude Code avant de compiler, pas à copier aveuglément

Le nom exact des packages (`org.readium.r2.shared` vs `org.readium.shared`, etc.) a pu changer entre les versions majeures de Readium — l'exemple ci-dessous reproduit fidèlement le code du guide officiel (`docs/guides/getting-started.md`, branche `develop` du dépôt `readium/kotlin-toolkit`), qui ne montre pas les imports. **Vérifier les imports réels contre les sources du artifact 3.0.0 une fois téléchargé (Android Studio : "Go to declaration" sur chaque classe), ne pas supposer qu'ils sont identiques à la branche `develop`.**

## 3.2.1 — Dépendances Gradle

`infrastructure/parser/build.gradle.kts` :

```kotlin
plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.parser"
}

dependencies {
    val readiumVersion = "3.0.0" // verrouillé — voir décision de version ci-dessus

    implementation("org.readium.kotlin-toolkit:readium-shared:$readiumVersion")
    implementation("org.readium.kotlin-toolkit:readium-streamer:$readiumVersion")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
```

`app/build.gradle.kts` (ou tout module consommateur final) doit activer le desugaring — exigence Readium, pas optionnelle :

```kotlin
android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}
```

## 3.2.2 — Wrapper implémentant `PublicationParser` (domaine, Tâche 1.7)

Reproduit fidèlement le flux du guide officiel (`AssetRetriever` → `PublicationOpener` → `Publication`), adapté à notre contrat `ParseResult` :

```kotlin
package com.inktone.infrastructure.parser

import android.content.Context
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente PublicationParser (Tâche 1.7) via Readium 3.0.0 — encapsulé
 * ici, jamais exposé au-delà de ce module (ADR-011). Portée de la Tâche
 * 3.2 : ouverture + métadonnées uniquement. L'extraction en DocumentModel
 * (Chapter/Sentence avec offsets) est la Tâche 3.4, pas celle-ci — ne pas
 * anticiper dessus tant que le guide d'extraction Readium n'est pas
 * vérifié séparément.
 */
@Singleton
class ReadiumPublicationParser @Inject constructor(
    @ApplicationContext private val context: Context,
) : PublicationParser {

    override val supportedFormats = listOf(PublicationFormat.EPUB)

    // Instanciation paresseuse — coûteuse, un seul jeu de composants
    // Readium réutilisé pour tous les parses de la durée de vie du singleton.
    // NOTE : les noms de classes ci-dessous (AssetRetriever, DefaultHttpClient,
    // PublicationOpener, DefaultPublicationParser) sont ceux du guide
    // officiel — VÉRIFIER leurs packages réels avant compilation (voir
    // avertissement en tête de document).
    private val httpClient by lazy { DefaultHttpClient() }
    private val assetRetriever by lazy {
        AssetRetriever(contentResolver = context.contentResolver, httpClient = httpClient)
    }
    private val publicationOpener by lazy {
        PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context, httpClient = httpClient, assetRetriever = assetRetriever,
                // Pas de pdfFactory : PDF hors périmètre v1 (ADR-017).
            ),
        )
    }

    override suspend fun parse(fileUri: String): ParseResult = withContext(Dispatchers.IO) {
        // Tâche 3.2 : URI de fichier local de test uniquement. Le SAF réel
        // (content://) sera branché en reliant infrastructure/storage à
        // ce parser en Phase 4/6 — pas cette tâche.
        val url = File(fileUri).toUrl()

        val asset = assetRetriever.retrieve(url).getOrElse {
            return@withContext ParseResult.Corrupted("Echec de lecture de l'asset: $it")
        }

        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            return@withContext ParseResult.Corrupted("Echec d'ouverture de la publication: $it")
        }

        // DRM : Readium expose publication.protectionError ou un mécanisme
        // équivalent selon la version exacte — À VERIFIER (K7 dépend de
        // cette detection ; ne pas la deviner, la confirmer contre l'API
        // reelle du protocole de protection avant de fermer cette tache).

        ParseResult.Success(
            documentModel = DocumentModel(chapters = emptyList(), tableOfContents = emptyList(), resources = emptyList()),
            isDrmProtected = false, // placeholder explicite — Tâche 3.4/4.4 le remplacera
        )
    }
}
```

**Ce placeholder `DocumentModel(emptyList(), emptyList(), emptyList())` est délibéré, pas un oubli** : le critère de validation de cette tâche (3.2.3) ne porte que sur l'ouverture et les métadonnées, pas encore sur l'extraction de chapitres — cohérent avec la portée annoncée en tête de document.

## 3.2.3 — Test instrumenté : un EPUB de test s'ouvre sans erreur

```kotlin
package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Nécessite un fixture EPUB minimal dans
 * infrastructure/parser/src/androidTest/assets/fixture-minimal.epub
 * (livre de test à 1 chapitre, sans DRM — à ajouter au dépôt en même
 * temps que ce test, pas généré au vol).
 */
@RunWith(AndroidJUnit4::class)
class ReadiumPublicationParserTest {

    @Test
    fun ouvre_un_epub_de_test_sans_erreur() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = copyAssetToCache(context, "fixture-minimal.epub")
        val parser = ReadiumPublicationParser(context)

        val result = parser.parse(fixtureFile.absolutePath)

        assertTrue("le parsing doit reussir sur un EPUB valide", result is ParseResult.Success)
    }

    private fun copyAssetToCache(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }
}
```

**Critère de validation avant/après :**
- Avant : aucune dépendance Readium, aucune preuve qu'un EPUB réel s'ouvre dans ce projet.
- Après : `./gradlew :infrastructure:parser:connectedAndroidTest` vert ; `ReadiumPublicationParserTest` confirme l'ouverture sans erreur du fixture.

**Commit :** `Integre Readium 3.0.0 et ouvre un EPUB de test (metadonnees uniquement)`

---

## Ce qui reste ouvert, explicitement, pour les tâches suivantes

- **Détection DRM réelle** (K7) : le mécanisme exact de Readium pour ça n'a pas été vérifié dans cette tâche — placeholder `isDrmProtected = false` à remplacer en 3.4/4.4 après avoir confirmé l'API contre les sources.
- **Extraction en `DocumentModel`** (Chapter/Paragraph/Sentence avec offsets réels) : Tâche 3.4, nécessite de vérifier séparément le guide « Extracting the content of a publication » — pas fait ici, volontairement.
- **Accès SAF réel** (`content://` au lieu de fichier local) : relié en Phase 4/6, quand `infrastructure/storage` (Tâche 2.8) et ce parser se rejoignent dans `ImportPublicationUseCase`.
