package com.clipforge.ai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import com.clipforge.ai.data.local.entity.EffectItemEntity
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.repository.EffectParamsCodec
import com.clipforge.ai.data.repository.EffectRepositoryImpl
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EffectPersistenceMigrationTest {

    private lateinit var context: Context
    private val dbName = "effect_persistence_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration_from_v3_to_current_survives_and_creates_effect_table_and_index() {
        createV3Database(context.getDatabasePath(dbName))

        val db = Room.databaseBuilder(context, ClipForgeDatabase::class.java, dbName)
            .addMigrations(ClipForgeDatabase.MIGRATION_3_4, ClipForgeDatabase.MIGRATION_4_5)
            .build()

        try {
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM projects WHERE id = 'project-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            assertTableExists(db, "effect_items")
            assertIndexExists(db, "index_effect_items_projectId")
        } finally {
            db.close()
        }
    }

    @Test
    fun fk_cascade_works() = runBlocking {
        val db = freshDatabase()
        try {
            insertProject(db, "project-1")
            db.effectItemDao().upsert(effectEntity("effect-1", "project-1"))
            db.projectDao().deleteProjectById("project-1")
            assertTrue(db.effectItemDao().getForProject("project-1").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun dao_upsert_get_delete_works() = runBlocking {
        val db = freshDatabase()
        try {
            insertProject(db, "project-1")
            db.effectItemDao().upsert(effectEntity("effect-1", "project-1", zOrder = 2))
            db.effectItemDao().upsert(effectEntity("effect-2", "project-1", zOrder = 1))

            val loaded = db.effectItemDao().getForProject("project-1")
            assertEquals(listOf("effect-2", "effect-1"), loaded.map { it.id })

            db.effectItemDao().deleteById("effect-2")
            assertEquals(listOf("effect-1"), db.effectItemDao().getForProject("project-1").map { it.id })

            db.effectItemDao().deleteForProject("project-1")
            assertTrue(db.effectItemDao().getForProject("project-1").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun corrupt_params_row_is_skipped_while_valid_rows_load() = runBlocking {
        val db = freshDatabase()
        try {
            insertProject(db, "project-1")
            db.effectItemDao().upsert(effectEntity("valid", "project-1"))
            db.effectItemDao().upsert(effectEntity("corrupt", "project-1", paramsJson = """{"params":{}}"""))
            db.effectItemDao().upsert(effectEntity("clip", "project-1", scope = "CLIP"))

            val loaded = EffectRepositoryImpl(db.effectItemDao()).getEffectsForProject("project-1")
            assertEquals(listOf("valid"), loaded.map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun fresh_install_v4_crud_works() = runBlocking {
        val db = freshDatabase()
        try {
            insertProject(db, "project-1")
            val repo = EffectRepositoryImpl(db.effectItemDao())
            repo.upsertEffect(
                EffectItem(
                    id = "effect-1",
                    projectId = "project-1",
                    effectId = "vhs",
                    scope = EffectScope.GLOBAL,
                    startMs = 0L,
                    endMs = 1000L,
                    zOrder = 0,
                    params = mapOf("intensity" to EffectParamValue.Constant(0.5f))
                )
            )

            val loaded = repo.getEffectsForProject("project-1")
            assertEquals(1, loaded.size)
            assertEquals("effect-1", loaded.single().id)

            repo.deleteEffect("effect-1")
            assertTrue(repo.getEffectsForProject("project-1").isEmpty())
        } finally {
            db.close()
        }
    }

    private fun freshDatabase(): ClipForgeDatabase =
        Room.databaseBuilder(context, ClipForgeDatabase::class.java, dbName)
            .addMigrations(ClipForgeDatabase.MIGRATION_3_4, ClipForgeDatabase.MIGRATION_4_5)
            .build()

    private suspend fun insertProject(db: ClipForgeDatabase, id: String) {
        db.projectDao().upsertProject(
            ProjectEntity(
                id = id,
                title = "Project $id",
                aspectRatio = "RATIO_9_16",
                exportQuality = "QUALITY_720P",
                planType = "FREE",
                thumbnailUri = null,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }

    private fun effectEntity(
        id: String,
        projectId: String,
        zOrder: Int = 0,
        scope: String = "GLOBAL",
        paramsJson: String = EffectParamsCodec.encode(emptyMap())
    ): EffectItemEntity = EffectItemEntity(
        id = id,
        projectId = projectId,
        effectId = "vhs",
        scope = scope,
        startMs = 0L,
        endMs = 1000L,
        zOrder = zOrder,
        paramsJson = paramsJson
    )

    private fun assertTableExists(db: ClipForgeDatabase, tableName: String) {
        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName)
        ).use { cursor ->
            assertTrue("missing table $tableName", cursor.moveToFirst())
        }
    }

    private fun assertIndexExists(db: ClipForgeDatabase, indexName: String) {
        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(indexName)
        ).use { cursor ->
            assertTrue("missing index $indexName", cursor.moveToFirst())
        }
    }

    private fun createV3Database(path: File) {
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `projects` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `aspectRatio` TEXT NOT NULL,
                    `exportQuality` TEXT NOT NULL,
                    `planType` TEXT NOT NULL,
                    `thumbnailUri` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `projectType` TEXT NOT NULL,
                    `autoFinalDuration` INTEGER,
                    `autoSecondsPerClip` INTEGER,
                    `autoTransitionType` TEXT,
                    `autoMusicEnabled` INTEGER NOT NULL,
                    `autoMusicAssetId` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `media_assets` (
                    `id` TEXT NOT NULL,
                    `projectId` TEXT NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `localUri` TEXT NOT NULL,
                    `remoteUrl` TEXT,
                    `durationMs` INTEGER,
                    `fileSizeBytes` INTEGER NOT NULL,
                    `mimeType` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_assets_projectId` ON `media_assets` (`projectId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `timeline_items` (
                    `id` TEXT NOT NULL,
                    `projectId` TEXT NOT NULL,
                    `mediaAssetId` TEXT NOT NULL,
                    `trackIndex` INTEGER NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    `startMs` INTEGER NOT NULL,
                    `endMs` INTEGER NOT NULL,
                    `trimStartMs` INTEGER NOT NULL,
                    `trimEndMs` INTEGER NOT NULL,
                    `fitMode` TEXT NOT NULL,
                    `transitionType` TEXT,
                    `transitionDurationMs` INTEGER,
                    `volumeMultiplier` REAL NOT NULL,
                    `opacity` REAL NOT NULL,
                    `playbackSpeed` REAL NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_timeline_items_projectId` ON `timeline_items` (`projectId`)")
            db.execSQL(
                """
                INSERT INTO `projects` (
                    `id`, `title`, `aspectRatio`, `exportQuality`, `planType`, `thumbnailUri`,
                    `createdAt`, `updatedAt`, `projectType`, `autoFinalDuration`,
                    `autoSecondsPerClip`, `autoTransitionType`, `autoMusicEnabled`, `autoMusicAssetId`
                ) VALUES ('project-1', 'Project 1', 'RATIO_9_16', 'QUALITY_720P', 'FREE', NULL, 1, 1, 'MANUAL', NULL, NULL, NULL, 0, NULL)
                """.trimIndent()
            )
            db.version = 3
        } finally {
            db.close()
        }
    }
}
