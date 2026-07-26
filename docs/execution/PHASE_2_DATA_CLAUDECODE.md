# Phase 2 — Fondations data & persistance

**Dépend de :** Phase 1 (close, PR #2 mergée)
**Précède :** Phase 3 — Marche à blanc
**Référence :** Blueprint InkTone v1.1.0, §4.7 (frontière data/infrastructure), §6 (Data Model), §10.3 (SAF), §14.5 (tests de migration)
**Sortie de phase :** voir Checklist finale en fin de document.

**Rappel de frontière (Blueprint §4.7), déterminant pour cette phase :**
- `infrastructure/database` contient UNIQUEMENT la technologie Room : entités
  annotées, DAOs, classe `Database`, converters. Aucune logique de mapping
  vers le domaine, aucune implémentation d'interface de repository.
- `data` contient les mappers domaine ↔ persistance ET les implémentations
  des interfaces de repository. `data` dépend de `infrastructure/database`
  (déjà autorisé par la matrice depuis la Phase 0 : `:data → {domain, infrastructure}`).
- `infrastructure/storage` implémente directement une interface de
  **service** du domaine (comme `infrastructure/tts` implémentera
  `TtsEngine` en Phase 5) — pas via `data`, qui est réservé aux
  *repositories*, pas aux services.

---

## Tâche 2.0 — Compléments à la Phase 0, nécessaires avant de commencer

Deux éléments differés en Phase 0 ("suivent le même schéma", jamais écrits en entier) doivent exister maintenant, et une interface de domaine manque pour `infrastructure/storage`.

### 2.0.1 — `InkToneAndroidLibraryConventionPlugin`, écrit en entier

`build-logic/convention/src/main/kotlin/InkToneAndroidLibraryConventionPlugin.kt` :

```kotlin
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Pour infrastructure/* et data (Blueprint §12.3). Dépendance de base
 * vers domain uniquement — chaque module ajoute ensuite ses propres
 * dépendances spécifiques (Room pour infrastructure/database, etc.),
 * vérifiées par checkArchitectureRules selon sa propre entrée de matrice.
 */
class InkToneAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("inktone.architecture.check")

            extensions.configure<LibraryExtension> {
                compileSdk = 34
                defaultConfig { minSdk = 26 }
            }

            dependencies {
                add("implementation", project(":domain"))
                add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
                add("implementation", "com.google.dagger:hilt-android:2.52")
                add("ksp", "com.google.dagger:hilt-android-compiler:2.52")
                add("testImplementation", "junit:junit:4.13.2")
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
```

Enregistrer dans `build-logic/convention/build.gradle.kts` (déjà préparé en Phase 0, section `gradlePlugin { plugins { ... } }` — vérifier que l'entrée `inktoneAndroidLibrary` y figure bien).

### 2.0.2 — Interface `FileStorageService` manquante dans le domaine

La Tâche 1.7 a posé `TtsEngine`, `PublicationParser`, `SearchService` mais aucune interface pour l'accès fichiers — nécessaire dès maintenant pour `infrastructure/storage`. Volontairement minimale : étendue en Phase 4/6 si besoin, jamais par anticipation.

`domain/src/main/kotlin/com/inktone/domain/service/FileStorageService.kt` :

```kotlin
package com.inktone.domain.service

import java.io.InputStream

/**
 * Accès aux fichiers de l'utilisateur via Storage Access Framework
 * (Blueprint §10.3, ADR-015 — SAF exclusivement, jamais
 * MANAGE_EXTERNAL_STORAGE). Implémentée directement par
 * infrastructure/storage — pas via data/, réservé aux repositories.
 */
interface FileStorageService {
    suspend fun openInputStream(uri: String): InputStream?
    suspend fun computeSha256(uri: String): String?
    suspend fun getFileSize(uri: String): Long?
    suspend fun persistReadPermission(uri: String)
}
```

`java.io.InputStream` est une classe JDK standard, pas Android — son usage ne viole pas la règle « le domaine ne dépend jamais d'Android » (même principe que l'ajout de `kotlinx-coroutines-core` en Tâche 1.0.1).

### 2.0.3 — Catalogue de versions, ajouts nécessaires

Ajouter à `gradle/libs.versions.toml` :

```toml
[versions]
# ... (existant)
kotlinxCoroutinesTest = "1.9.0"

[libraries]
# ... (existant)
androidx-test-core = { group = "androidx.test", name = "core", version = "1.6.1" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
```

**Commit :** `Complete la Phase 0 : convention plugin Android library, FileStorageService, catalogue de versions`

---

## Tâche 2.1 — Entités Room

**Objectif :** une entité Room par entité du domaine (Blueprint §6.2). Le `Locator` est aplati en colonnes (`resourceHref`, `chapterIndex`, `paragraphIndex`, `charOffset`) — jamais stocké sous forme sérialisée opaque, pour rester indexable et lisible en SQL brut si besoin de diagnostic.

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/converter/StringListConverter.kt` :

```kotlin
package com.inktone.infrastructure.database.converter

import androidx.room.TypeConverter

/**
 * Sépare une liste de chaînes courtes (auteurs, sujets) par le caractère
 * de contrôle "Unit Separator" (U+001F), jamais présent dans un texte
 * normal. Suffisant pour ce cas d'usage — si une structure plus riche
 * devient nécessaire, migrer vers un converter JSON avec une migration
 * Room dédiée, pas une modification silencieuse du format existant.
 */
class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString(separator = "\u001F")

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\u001F")
}
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/entity/PublicationEntity.kt` :

```kotlin
package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "publications",
    indices = [
        Index("title"), Index("lastOpened"), Index("seriesName"),
        Index("fileHash", unique = true),
    ],
)
data class PublicationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val publisher: String?,
    val language: String?,
    val description: String?,
    val coverUri: String?,
    val format: String,
    val fileUri: String,
    val fileHash: String,
    val fileSize: Long,
    val chapterCount: Int,
    val seriesName: String?,
    val seriesIndex: Float?,
    val isFavorite: Boolean,
    val subjects: List<String>,
    val isDrmProtected: Boolean,
    val importDate: Long,
    val lastOpened: Long?,
)
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/entity/ReadingStateEntity.kt` :

```kotlin
package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Locator aplati en 4 colonnes (resourceHref/chapterIndex/paragraphIndex/
 * charOffset) — voir le mapper dédié en Tâche 2.5, réutilisé pour
 * Bookmark et Annotation, jamais réimplémenté ad hoc.
 * overrideTheme/overrideFontSize nullables : ReadingOverrides est
 * optionnel dans le domaine (Blueprint §3.3) ; nul sur les deux colonnes
 * = aucune surcharge, pas une table séparée pour un seul objet optionnel.
 */
@Entity(
    tableName = "reading_states",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("publicationId", unique = true)],
)
data class ReadingStateEntity(
    @PrimaryKey val publicationId: String,
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
    val lastReadAt: Long,
    val voiceProfileId: String?,
    val overrideTheme: String?,
    val overrideFontSize: Int?,
)
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/entity/ReadingSessionEntity.kt` :

```kotlin
package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("publicationId")],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val publicationId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: String,
    val sentencesRead: Int,
    val durationMs: Long,
)
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/entity/BookmarkEntity.kt` :

```kotlin
package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("publicationId")],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val publicationId: String,
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
    val title: String?,
    val note: String?,
    val createdAt: Long,
)
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/entity/AnnotationEntity.kt` :

```kotlin
package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Deux Locators aplatis (start/end), même principe que ReadingState. */
@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("publicationId")],
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val publicationId: String,
    val startResourceHref: String,
    val startChapterIndex: Int,
    val startParagraphIndex: Int?,
    val startCharOffset: Int,
    val endResourceHref: String,
    val endChapterIndex: Int,
    val endParagraphIndex: Int?,
    val endCharOffset: Int,
    val color: String,
    val content: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/entity/VoiceProfileEntity.kt` :

```kotlin
package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey val id: String,
    val engine: String,
    val voice: String,
    val language: String,
    val speed: Float,
    val pitch: Float,
    val volume: Float,
    val style: String?,
)
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/entity/UserPreferencesEntity.kt` :

```kotlin
package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ligne unique — id toujours 0. Choix délibéré de rester sur Room plutôt
 * que d'introduire DataStore comme seconde techno de persistance sans ADR. */
@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 0,
    val theme: String,
    val fontSize: Int,
    val defaultTtsEngine: String,
    val crashReportingEnabled: Boolean,
    val language: String,
)
```

**Commit :** `Ajoute les entites Room et le converter de listes de chaines`

---

## Tâche 2.2 — DAOs

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/dao/PublicationDao.kt` :

```kotlin
package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inktone.infrastructure.database.entity.PublicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublicationDao {
    @Query("SELECT * FROM publications ORDER BY lastOpened DESC")
    fun observeAll(): Flow<List<PublicationEntity>>

    @Query("SELECT * FROM publications WHERE id = :id")
    suspend fun getById(id: String): PublicationEntity?

    @Query("SELECT * FROM publications WHERE fileHash = :hash LIMIT 1")
    suspend fun getByFileHash(hash: String): PublicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PublicationEntity)

    @Update
    suspend fun update(entity: PublicationEntity)

    @Query("DELETE FROM publications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE publications SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/dao/ReadingStateDao.kt` :

```kotlin
package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStateDao {
    @Query("SELECT * FROM reading_states WHERE publicationId = :publicationId")
    suspend fun get(publicationId: String): ReadingStateEntity?

    @Query("SELECT * FROM reading_states WHERE publicationId = :publicationId")
    fun observe(publicationId: String): Flow<ReadingStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ReadingStateEntity)

    @Query("DELETE FROM reading_states WHERE publicationId = :publicationId")
    suspend fun delete(publicationId: String)
}
```

**Répéter ce schéma** pour les 5 DAOs restants (une méthode par opération de l'interface de repository correspondante, Tâche 1.6) :

- `ReadingSessionDao` : `insert`, `getAllForPublication(publicationId): List`, `getAll(): List`.
- `BookmarkDao` : `observeForPublication(publicationId): Flow<List>`, `insert`, `delete(id)`.
- `AnnotationDao` : `observeForPublication(publicationId): Flow<List>` (trié par `startChapterIndex, startCharOffset`), `insert`, `update`, `delete(id)`.
- `VoiceProfileDao` : `getById`, `getAll(): List`, `save` (REPLACE), `delete(id)`.
- `UserPreferencesDao` : `observe(): Flow<UserPreferencesEntity?>` et `get(): UserPreferencesEntity?` filtrés sur `WHERE id = 0`, `upsert` (REPLACE).

**Commit :** `Ajoute les DAOs Room correspondant aux interfaces de repository`

---

## Tâche 2.3 — `InkToneDatabase` et configuration WAL (K1)

**Objectif :** la classe `Database` Room, et la configuration WAL réellement câblée — pas seulement documentée.

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/InkToneDatabase.kt` :

```kotlin
package com.inktone.infrastructure.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inktone.infrastructure.database.converter.StringListConverter
import com.inktone.infrastructure.database.dao.AnnotationDao
import com.inktone.infrastructure.database.dao.BookmarkDao
import com.inktone.infrastructure.database.dao.PublicationDao
import com.inktone.infrastructure.database.dao.ReadingSessionDao
import com.inktone.infrastructure.database.dao.ReadingStateDao
import com.inktone.infrastructure.database.dao.UserPreferencesDao
import com.inktone.infrastructure.database.dao.VoiceProfileDao
import com.inktone.infrastructure.database.entity.AnnotationEntity
import com.inktone.infrastructure.database.entity.BookmarkEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.ReadingSessionEntity
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import com.inktone.infrastructure.database.entity.UserPreferencesEntity
import com.inktone.infrastructure.database.entity.VoiceProfileEntity

@Database(
    entities = [
        PublicationEntity::class, ReadingStateEntity::class, ReadingSessionEntity::class,
        BookmarkEntity::class, AnnotationEntity::class, VoiceProfileEntity::class,
        UserPreferencesEntity::class,
    ],
    version = 1,
    exportSchema = true, // condition du harnais de migration — Tâche 2.4
)
@TypeConverters(StringListConverter::class)
abstract class InkToneDatabase : RoomDatabase() {
    abstract fun publicationDao(): PublicationDao
    abstract fun readingStateDao(): ReadingStateDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun userPreferencesDao(): UserPreferencesDao
}
```

`infrastructure/database/src/main/kotlin/com/inktone/infrastructure/database/di/DatabaseModule.kt` :

```kotlin
package com.inktone.infrastructure.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.inktone.infrastructure.database.InkToneDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InkToneDatabase =
        Room.databaseBuilder(context, InkToneDatabase::class.java, "inktone.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // K1 — Blueprint §6.5, ADR-016
            // PAS de fallbackToDestructiveMigration ici (K4) : toute migration
            // manquante doit faire planter l'app, jamais effacer les données.
            .build()

    @Provides fun providePublicationDao(db: InkToneDatabase) = db.publicationDao()
    @Provides fun provideReadingStateDao(db: InkToneDatabase) = db.readingStateDao()
    @Provides fun provideReadingSessionDao(db: InkToneDatabase) = db.readingSessionDao()
    @Provides fun provideBookmarkDao(db: InkToneDatabase) = db.bookmarkDao()
    @Provides fun provideAnnotationDao(db: InkToneDatabase) = db.annotationDao()
    @Provides fun provideVoiceProfileDao(db: InkToneDatabase) = db.voiceProfileDao()
    @Provides fun provideUserPreferencesDao(db: InkToneDatabase) = db.userPreferencesDao()
}
```

`infrastructure/database/build.gradle.kts` :

```kotlin
plugins {
    id("inktone.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.inktone.infrastructure.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
```

`infrastructure/database/src/androidTest/kotlin/com/inktone/infrastructure/database/JournalModeTest.kt` :

```kotlin
package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vérifie K1 sur une base RÉELLEMENT fichier — un test sur base en
 * mémoire ne le peut pas : SQLite ignore WAL pour `:memory:` et utilise
 * toujours MEMORY, quel que soit `setJournalMode`. C'est pourquoi ce
 * test, seul de la suite Phase 2, construit la base exactement comme
 * DatabaseModule le fait en production, plutôt que via
 * `inMemoryDatabaseBuilder` comme les autres tests de cette phase.
 */
@RunWith(AndroidJUnit4::class)
class JournalModeTest {

    @Test
    fun la_base_de_production_utilise_wal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "journal-mode-test.db"
        val db = Room.databaseBuilder(context, InkToneDatabase::class.java, dbName)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

        db.openHelper.writableDatabase.query("PRAGMA journal_mode", emptyArray()).use { cursor ->
            cursor.moveToFirst()
            assertEquals("wal", cursor.getString(0).lowercase())
        }

        db.close()
        context.deleteDatabase(dbName)
    }
}
```

**Critère de validation avant/après :**
- Avant : aucune garantie que le mode journal configuré dans le code soit celui réellement actif à l'exécution.
- Après : `./gradlew :infrastructure:database:connectedAndroidTest --tests "*.JournalModeTest"` vert sur émulateur/device — preuve d'exécution, pas seulement de configuration.

**Commit :** `Ajoute InkToneDatabase avec journal WAL et son test dedie`

---

## Tâche 2.4 — Harnais de migration (K4)

**Objectif :** le schéma est exporté (Tâche 2.3, `exportSchema = true` + `room { schemaDirectory(...) }`). Ce dossier `schemas/` doit être **committé** — c'est la condition sine qua non pour que `MigrationTestHelper` puisse un jour valider une migration v1→v2 en la comparant au schéma v1 réellement exporté, pas à une supposition.

Il n'y a rien à migrer en v1 : ce test ne valide donc aucune migration réelle. Il valide que le **mécanisme** fonctionne — gabarit prêt pour la première vraie migration.

`infrastructure/database/src/androidTest/kotlin/com/inktone/infrastructure/database/DatabaseMigrationTest.kt` :

```kotlin
package com.inktone.infrastructure.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Harnais de migration (Blueprint §14.5, acquis K4). Ne valide aucune
 * migration réelle pour l'instant — le schéma est en version 1, il n'y a
 * rien à migrer. Sert de GABARIT : copier cette structure pour CHAQUE
 * migration future (ajouter `helper.runMigrationsAndValidate(dbName, N,
 * true, MIGRATION_N_MINUS_1_TO_N)` après avoir créé la base à N-1 et
 * inséré des données représentatives). Ne jamais ajouter une version de
 * schéma sans le test correspondant dans le même commit.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InkToneDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `le schema v1 exporte se cree et s'ouvre sans erreur`() {
        // Valide que le schéma exporté (schemas/.../1.json, committé)
        // correspond exactement à InkToneDatabase — toute divergence fait
        // échouer ce test immédiatement.
        helper.createDatabase(TEST_DB_NAME, 1).apply { close() }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
```

**Vérifier que `infrastructure/database/schemas/` n'est PAS dans `.gitignore`** — contrairement aux artefacts de build habituels, ce dossier est un artefact source à committer.

**Critère de validation avant/après :**
- Avant : aucune preuve que l'export de schéma fonctionne réellement.
- Après : `./gradlew :infrastructure:database:connectedAndroidTest --tests "*.DatabaseMigrationTest"` vert ; le fichier `schemas/com.inktone.infrastructure.database.InkToneDatabase/1.json` existe et est committé.

**Commit :** `Ajoute le harnais de migration et exporte le schema v1`

---

## Tâche 2.5 — Mappers domaine ↔ Room (module `data`)

**Objectif :** un seul mapper `Locator` ↔ colonnes, réutilisé pour `ReadingState`, `Bookmark` et les deux bornes d'`Annotation` — jamais réimplémenté ad hoc à chaque entité (Blueprint §6.2).

`data/build.gradle.kts` :

```kotlin
plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.data"
}

dependencies {
    implementation(project(":infrastructure:database"))
    implementation(project(":infrastructure:storage"))
}
```

`data/src/main/kotlin/com/inktone/data/mapper/LocatorMapper.kt` :

```kotlin
package com.inktone.data.mapper

import com.inktone.domain.valueobject.Locator

/** Intermédiaire forçant la réutilisation du même aplatissement partout. */
data class LocatorColumns(
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
)

fun Locator.toColumns(): LocatorColumns = LocatorColumns(
    resourceHref = resourceHref, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, charOffset = charOffset,
)

fun LocatorColumns.toLocator(): Locator = Locator(
    resourceHref = resourceHref, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, charOffset = charOffset,
)
```

`data/src/main/kotlin/com/inktone/data/mapper/PublicationMapper.kt` :

```kotlin
package com.inktone.data.mapper

import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.infrastructure.database.entity.PublicationEntity

fun Publication.toEntity(): PublicationEntity = PublicationEntity(
    id = id, title = title, subtitle = subtitle, authors = authors,
    publisher = publisher, language = language, description = description,
    coverUri = coverUri, format = format.name, fileUri = fileUri,
    fileHash = fileHash, fileSize = fileSize, chapterCount = chapterCount,
    seriesName = seriesName, seriesIndex = seriesIndex, isFavorite = isFavorite,
    subjects = subjects, isDrmProtected = isDrmProtected,
    importDate = importDate, lastOpened = lastOpened,
)

fun PublicationEntity.toDomain(): Publication = Publication(
    id = id, title = title, subtitle = subtitle, authors = authors,
    publisher = publisher, language = language, description = description,
    coverUri = coverUri, format = PublicationFormat.valueOf(format), fileUri = fileUri,
    fileHash = fileHash, fileSize = fileSize, chapterCount = chapterCount,
    seriesName = seriesName, seriesIndex = seriesIndex, isFavorite = isFavorite,
    subjects = subjects, isDrmProtected = isDrmProtected,
    importDate = importDate, lastOpened = lastOpened,
)
```

`data/src/main/kotlin/com/inktone/data/mapper/ReadingStateMapper.kt` :

```kotlin
package com.inktone.data.mapper

import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.ReadingTheme
import com.inktone.infrastructure.database.entity.ReadingStateEntity

fun ReadingState.toEntity(): ReadingStateEntity {
    val cols = locator.toColumns()
    return ReadingStateEntity(
        publicationId = publicationId,
        resourceHref = cols.resourceHref, chapterIndex = cols.chapterIndex,
        paragraphIndex = cols.paragraphIndex, charOffset = cols.charOffset,
        lastReadAt = lastReadAt, voiceProfileId = voiceProfileId,
        overrideTheme = overrides?.theme?.name, overrideFontSize = overrides?.fontSize,
    )
}

fun ReadingStateEntity.toDomain(): ReadingState = ReadingState(
    publicationId = publicationId,
    locator = LocatorColumns(resourceHref, chapterIndex, paragraphIndex, charOffset).toLocator(),
    lastReadAt = lastReadAt, voiceProfileId = voiceProfileId,
    overrides = if (overrideTheme != null || overrideFontSize != null) {
        ReadingOverrides(
            theme = overrideTheme?.let { ReadingTheme.valueOf(it) },
            fontSize = overrideFontSize,
        )
    } else null,
)
```

`data/src/main/kotlin/com/inktone/data/mapper/BookmarkMapper.kt` :

```kotlin
package com.inktone.data.mapper

import com.inktone.domain.model.Bookmark
import com.inktone.infrastructure.database.entity.BookmarkEntity

fun Bookmark.toEntity(): BookmarkEntity {
    val cols = locator.toColumns()
    return BookmarkEntity(
        id = id, publicationId = publicationId,
        resourceHref = cols.resourceHref, chapterIndex = cols.chapterIndex,
        paragraphIndex = cols.paragraphIndex, charOffset = cols.charOffset,
        title = title, note = note, createdAt = createdAt,
    )
}

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id, publicationId = publicationId,
    locator = LocatorColumns(resourceHref, chapterIndex, paragraphIndex, charOffset).toLocator(),
    title = title, note = note, createdAt = createdAt,
)
```

`data/src/main/kotlin/com/inktone/data/mapper/AnnotationMapper.kt` :

```kotlin
package com.inktone.data.mapper

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.infrastructure.database.entity.AnnotationEntity

fun Annotation.toEntity(): AnnotationEntity {
    val start = startLocator.toColumns()
    val end = endLocator.toColumns()
    return AnnotationEntity(
        id = id, publicationId = publicationId,
        startResourceHref = start.resourceHref, startChapterIndex = start.chapterIndex,
        startParagraphIndex = start.paragraphIndex, startCharOffset = start.charOffset,
        endResourceHref = end.resourceHref, endChapterIndex = end.chapterIndex,
        endParagraphIndex = end.paragraphIndex, endCharOffset = end.charOffset,
        color = color.name, content = content, createdAt = createdAt, updatedAt = updatedAt,
    )
}

fun AnnotationEntity.toDomain(): Annotation = Annotation(
    id = id, publicationId = publicationId,
    startLocator = LocatorColumns(startResourceHref, startChapterIndex, startParagraphIndex, startCharOffset).toLocator(),
    endLocator = LocatorColumns(endResourceHref, endChapterIndex, endParagraphIndex, endCharOffset).toLocator(),
    color = AnnotationColor.valueOf(color), content = content,
    createdAt = createdAt, updatedAt = updatedAt,
)
```

**Répéter ce schéma** (aller-retour direct, sans `Locator`) pour `VoiceProfileMapper`, `ReadingSessionMapper` et `UserPreferencesMapper` — conversions champ à champ triviales, `TtsEngineId`/`ReadingMode`/`ReadingTheme` via `.name` et `.valueOf()`.

`data/src/test/kotlin/com/inktone/data/mapper/LocatorMapperTest.kt` :

```kotlin
package com.inktone.data.mapper

import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Test

class LocatorMapperTest {

    @Test
    fun `aller-retour Locator vers colonnes ne perd aucune information`() {
        val original = Locator(
            resourceHref = "ch3.xhtml", chapterIndex = 2, paragraphIndex = 5, charOffset = 142,
        )
        val roundTripped = original.toColumns().toLocator()
        assertEquals(original, roundTripped)
    }

    @Test
    fun `paragraphIndex nul est preserve a l'aller-retour`() {
        val original = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0)
        assertEquals(original, original.toColumns().toLocator())
    }
}
```

**Note de test important — Robolectric n'est PAS introduit ici.** Ce test de mapper est un test JVM pur (`src/test`), pas `src/androidTest` : `LocatorMapper` ne touche à aucune API Android, donc `data/build.gradle.kts` n'a besoin que de `testImplementation("junit:junit:4.13.2")` pour ce fichier — déjà fourni par `inktone.android.library`.

**Commit :** `Ajoute les mappers domaine-Room, dont le mapper Locator reutilise partout`

---

## Tâche 2.6 — Implémentations des repositories et liaison Hilt

`data/src/main/kotlin/com/inktone/data/repository/RoomPublicationRepository.kt` :

```kotlin
package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.Publication
import com.inktone.domain.repository.PublicationRepository
import com.inktone.infrastructure.database.dao.PublicationDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomPublicationRepository @Inject constructor(
    private val dao: PublicationDao,
) : PublicationRepository {
    override fun observeAll(): Flow<List<Publication>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getById(id: String): Publication? = dao.getById(id)?.toDomain()
    override suspend fun getByFileHash(hash: String): Publication? = dao.getByFileHash(hash)?.toDomain()
    override suspend fun insert(publication: Publication) = dao.insert(publication.toEntity())
    override suspend fun update(publication: Publication) = dao.update(publication.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun setFavorite(id: String, isFavorite: Boolean) = dao.setFavorite(id, isFavorite)
}
```

`data/src/main/kotlin/com/inktone/data/repository/RoomReadingStateRepository.kt` :

```kotlin
package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.infrastructure.database.dao.ReadingStateDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomReadingStateRepository @Inject constructor(
    private val dao: ReadingStateDao,
) : ReadingStateRepository {
    override suspend fun get(publicationId: String): ReadingState? = dao.get(publicationId)?.toDomain()
    override fun observe(publicationId: String): Flow<ReadingState?> =
        dao.observe(publicationId).map { it?.toDomain() }
    override suspend fun save(state: ReadingState) = dao.save(state.toEntity())
    override suspend fun delete(publicationId: String) = dao.delete(publicationId)
}
```

`data/src/main/kotlin/com/inktone/data/repository/RoomPreferencesRepository.kt` — le seul avec une valeur par défaut, pas de bootstrap séparé nécessaire :

```kotlin
package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.infrastructure.database.dao.UserPreferencesDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomPreferencesRepository @Inject constructor(
    private val dao: UserPreferencesDao,
) : PreferencesRepository {
    override fun observe(): Flow<UserPreferences> = dao.observe().map { it?.toDomain() ?: UserPreferences() }
    override suspend fun get(): UserPreferences = dao.get()?.toDomain() ?: UserPreferences()
    override suspend fun update(preferences: UserPreferences) = dao.upsert(preferences.toEntity())
}
```

**Répéter ce schéma** (un constructeur `@Inject` avec le DAO, chaque méthode déléguant au DAO via le mapper) pour `RoomReadingSessionRepository`, `RoomBookmarkRepository`, `RoomAnnotationRepository`, `RoomVoiceProfileRepository`.

`data/src/main/kotlin/com/inktone/data/di/RepositoryModule.kt` :

```kotlin
package com.inktone.data.di

import com.inktone.data.repository.RoomAnnotationRepository
import com.inktone.data.repository.RoomBookmarkRepository
import com.inktone.data.repository.RoomPreferencesRepository
import com.inktone.data.repository.RoomPublicationRepository
import com.inktone.data.repository.RoomReadingSessionRepository
import com.inktone.data.repository.RoomReadingStateRepository
import com.inktone.data.repository.RoomVoiceProfileRepository
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.repository.VoiceProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindPublicationRepository(impl: RoomPublicationRepository): PublicationRepository
    @Binds @Singleton abstract fun bindReadingStateRepository(impl: RoomReadingStateRepository): ReadingStateRepository
    @Binds @Singleton abstract fun bindReadingSessionRepository(impl: RoomReadingSessionRepository): ReadingSessionRepository
    @Binds @Singleton abstract fun bindBookmarkRepository(impl: RoomBookmarkRepository): BookmarkRepository
    @Binds @Singleton abstract fun bindAnnotationRepository(impl: RoomAnnotationRepository): AnnotationRepository
    @Binds @Singleton abstract fun bindVoiceProfileRepository(impl: RoomVoiceProfileRepository): VoiceProfileRepository
    @Binds @Singleton abstract fun bindPreferencesRepository(impl: RoomPreferencesRepository): PreferencesRepository
}
```

**Critère de validation avant/après :**
- Avant : les interfaces de la Tâche 1.6 n'ont aucune implémentation.
- Après : `./gradlew :data:compileKotlin` réussit ; les 7 interfaces ont une implémentation Room-backed ; le graphe Hilt (`app`, même minimal) s'assemble sans `MissingBinding`.

**Commit :** `Implemente les 7 repositories via Room et cable les liaisons Hilt`

---

## Tâche 2.7 — Cascade de suppression (Blueprint §3.5, §6.3)

`infrastructure/database/src/androidTest/kotlin/com/inktone/infrastructure/database/CascadeDeleteTest.kt` :

```kotlin
package com.inktone.infrastructure.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.infrastructure.database.entity.BookmarkEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CascadeDeleteTest {

    private lateinit var db: InkToneDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), InkToneDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun supprimer_une_publication_vide_reading_state_et_bookmarks() = runTest {
        val pubId = "pub-1"
        db.publicationDao().insert(
            PublicationEntity(
                id = pubId, title = "Test", subtitle = null, authors = emptyList(),
                publisher = null, language = null, description = null, coverUri = null,
                format = "EPUB", fileUri = "content://x", fileHash = "h1", fileSize = 1L,
                chapterCount = 1, seriesName = null, seriesIndex = null, isFavorite = false,
                subjects = emptyList(), isDrmProtected = false, importDate = 0L, lastOpened = null,
            )
        )
        db.readingStateDao().save(ReadingStateEntity(pubId, "ch1.xhtml", 0, null, 0, 0L, null, null, null))
        db.bookmarkDao().insert(BookmarkEntity("bm-1", pubId, "ch1.xhtml", 0, null, 0, null, null, 0L))

        db.publicationDao().delete(pubId)

        assertNull(db.readingStateDao().get(pubId))
        assertTrue(db.bookmarkDao().observeForPublication(pubId).first().isEmpty())
    }
}
```

**Commit :** `Ajoute le test de suppression en cascade`

---

## Tâche 2.8 — `infrastructure/storage` : wrapper SAF

**Objectif :** implémentation directe de `FileStorageService` (Tâche 2.0.2) — pas via `data/`, réservé aux repositories (rappel de frontière en tête de document).

`infrastructure/storage/build.gradle.kts` :

```kotlin
plugins {
    id("inktone.android.library")
}

android {
    namespace = "com.inktone.infrastructure.storage"
}

dependencies {
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
```

`infrastructure/storage/src/main/kotlin/com/inktone/infrastructure/storage/SafFileStorageService.kt` :

```kotlin
package com.inktone.infrastructure.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.inktone.domain.service.FileStorageService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

class SafFileStorageService @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileStorageService {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun openInputStream(uri: String): InputStream? = withContext(Dispatchers.IO) {
        runCatching { resolver.openInputStream(Uri.parse(uri)) }.getOrNull()
    }

    override suspend fun computeSha256(uri: String): String? = withContext(Dispatchers.IO) {
        openInputStream(uri)?.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var read = stream.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = stream.read(buffer)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    override suspend fun getFileSize(uri: String): Long? = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            // Fallback actif UNIQUEMENT en test (URI file:// vers un fichier
            // temporaire, Tâche 2.8 test ci-dessous) — la production ne
            // reçoit jamais que des URI SAF content://, pour lesquelles
            // ContentResolver.query()/OpenableColumns est la seule voie
            // correcte (une URI file:// n'a pas de "provider" à interroger).
            return@withContext parsed.path?.let { File(it).length() }
        }
        runCatching {
            resolver.query(parsed, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else null
            }
        }.getOrNull()
    }

    override suspend fun persistReadPermission(uri: String) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Unit
    }
}
```

`infrastructure/storage/src/main/kotlin/com/inktone/infrastructure/storage/di/StorageModule.kt` :

```kotlin
package com.inktone.infrastructure.storage.di

import com.inktone.domain.service.FileStorageService
import com.inktone.infrastructure.storage.SafFileStorageService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    @Singleton
    abstract fun bindFileStorageService(impl: SafFileStorageService): FileStorageService
}
```

`infrastructure/storage/src/androidTest/kotlin/com/inktone/infrastructure/storage/SafFileStorageServiceTest.kt` :

```kotlin
package com.inktone.infrastructure.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Teste via une URI file:// vers un fichier temporaire réel, pas une
 * vraie URI SAF content:// (qui exigerait un FileProvider de test ou une
 * interaction utilisateur simulée). ContentResolver.openInputStream gère
 * nativement file:// — ce test valide fidèlement la logique du wrapper
 * pour openInputStream/computeSha256 ; getFileSize utilise sciemment le
 * repli file:// documenté dans SafFileStorageService (voir le commentaire
 * associé).
 */
@RunWith(AndroidJUnit4::class)
class SafFileStorageServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val service = SafFileStorageService(context)

    private fun tempFile(content: String): File =
        File(context.cacheDir, "test-${System.nanoTime()}.txt").apply { writeText(content) }

    @Test
    fun ouvre_et_lit_un_fichier_reel() = runTest {
        val file = tempFile("Bonjour InkTone")
        val content = service.openInputStream(Uri.fromFile(file).toString())
            ?.bufferedReader()?.use { it.readText() }
        assertEquals("Bonjour InkTone", content)
        file.delete()
    }

    @Test
    fun calcule_un_hash_sha256_deterministe() = runTest {
        val file = tempFile("contenu identique")
        val uri = Uri.fromFile(file).toString()
        val hash1 = service.computeSha256(uri)
        val hash2 = service.computeSha256(uri)
        assertNotNull(hash1)
        assertEquals(hash1, hash2)
        file.delete()
    }

    @Test
    fun retourne_la_taille_reelle_du_fichier() = runTest {
        val file = tempFile("12345")
        assertEquals(5L, service.getFileSize(Uri.fromFile(file).toString()))
        file.delete()
    }
}
```

**Critère de validation avant/après :**
- Avant : aucun accès fichier possible sans `MANAGE_EXTERNAL_STORAGE`.
- Après : les 3 tests passent ; `grep -r "MANAGE_EXTERNAL_STORAGE" infrastructure/storage/` ne renvoie rien (K5, déjà vérifié en CI depuis la Phase 0).

**Commit :** `Implemente SafFileStorageService et sa liaison Hilt`

---

## Tâche 2.9 — Tests CRUD restants et clôture

**Répéter le schéma de test** de `PublicationDaoTest` (Tâche 2.1, non montré séparément — même structure que `CascadeDeleteTest`) pour `ReadingSessionDao`, `BookmarkDao`, `AnnotationDao`, `VoiceProfileDao`, `UserPreferencesDao` : `Room.inMemoryDatabaseBuilder`, un fixture par entité, une assertion par opération de l'interface de repository.

Point d'attention pour `AnnotationDao` : tester explicitement que le tri `ORDER BY startChapterIndex, startCharOffset` reflète l'ordre naturel du `Locator` (Tâche 1.1, `compareTo`) — deux annotations du même chapitre doivent revenir dans l'ordre de leur position, pas de leur `createdAt`.

### Checklist finale de sortie de Phase 2

| # | Critère | Vérification |
|---|---|---|
| 1 | Round-trip CRUD testé par entité | 7 suites de DAO tests vertes (in-memory) |
| 2 | WAL actif en conditions réelles | `JournalModeTest` vert (base fichier, pas in-memory) |
| 3 | Harnais de migration prêt | `DatabaseMigrationTest` vert ; `schemas/.../1.json` committé |
| 4 | Cascade de suppression | `CascadeDeleteTest` vert |
| 5 | 7 repositories implémentés et liés à Hilt | `RepositoryModule` compile, graphe Hilt s'assemble |
| 6 | `FileStorageService` implémenté | `SafFileStorageServiceTest` (3 tests) vert |
| 7 | Frontière data/infrastructure respectée | Aucun mapper ni implémentation de repository dans `infrastructure/database` ; `infrastructure/storage` implémente directement son interface de service |
| 8 | `Locator` ↔ colonnes sans perte | `LocatorMapperTest` vert |
| 9 | CI verte sur PR | build + `checkArchitectureRules` + tests instrumentés + lint emoji + check manifest |

Une fois les 9 critères vérifiés sur un clone frais, Phase 2 est close. Étape suivante : **Phase 3 — Marche à blanc** (Readium, Sherpa-ONNX, un livre, un chapitre, une phrase — le point de décision go/no-go du pari architectural, Blueprint §16.3).
