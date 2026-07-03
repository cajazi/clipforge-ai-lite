package com.clipforge.ai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.text.TextAlignment
import com.clipforge.ai.core.text.TextRenderSpec
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.repository.TextOverlayRepositoryImpl
import com.clipforge.ai.domain.model.TextOverlay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TextOverlayPersistenceMigrationTest {

    private lateinit var context: Context
    private val dbName = "text_overlay_persistence_test.db"

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
    fun migration_from_v4_to_v5_survives_project_data_and_creates_text_overlay_table() {
        createV4Database(context.getDatabasePath(dbName))

        val db = Room.databaseBuilder(context, ClipForgeDatabase::class.java, dbName)
            .addMigrations(ClipForgeDatabase.MIGRATION_4_5)
            .build()

        try {
            db.openHelper.writableDatabase.query("SELECT title FROM projects WHERE id = 'project-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Project 1", cursor.getString(0))
            }
            assertTableExists(db, "text_overlays")
            assertIndexExists(db, "index_text_overlays_projectId")
        } finally {
            db.close()
        }
    }

    @Test
    fun fresh_install_v5_repository_crud_works() = runBlocking {
        val db = freshDatabase()
        try {
            insertProject(db, "project-1")
            val repository = TextOverlayRepositoryImpl(db.textOverlayDao())
            repository.upsertTextOverlay(textOverlay("text-1", "project-1", zIndex = 2))
            repository.upsertTextOverlay(textOverlay("text-2", "project-1", zIndex = 1, highlightRange = 0..3))

            val loaded = repository.getTextOverlaysForProject("project-1")
            assertEquals(listOf("text-2", "text-1"), loaded.map { it.id })
            assertEquals(0..3, loaded.first().renderSpec.highlightRange)

            repository.deleteTextOverlay("text-2")
            assertEquals(listOf("text-1"), repository.getTextOverlaysForProject("project-1").map { it.id })

            repository.deleteTextOverlaysForProject("project-1")
            assertTrue(repository.getTextOverlaysForProject("project-1").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun fk_cascade_works() = runBlocking {
        val db = freshDatabase()
        try {
            insertProject(db, "project-1")
            TextOverlayRepositoryImpl(db.textOverlayDao()).upsertTextOverlay(textOverlay("text-1", "project-1"))

            db.projectDao().deleteProjectById("project-1")

            assertTrue(db.textOverlayDao().getForProject("project-1").isEmpty())
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

    private fun textOverlay(
        id: String,
        projectId: String,
        zIndex: Int = 0,
        highlightRange: IntRange? = null
    ): TextOverlay = TextOverlay(
        id = id,
        projectId = projectId,
        windowStartMs = 100L,
        windowEndMs = 1_100L,
        layer = OverlayLayer.USER,
        zIndex = zIndex,
        transform = OverlayTransform(
            xNorm = 0.5f,
            yNorm = 0.5f,
            scale = 1f,
            rotationDeg = 0f,
            alpha = 1f
        ),
        renderSpec = TextRenderSpec(
            text = "Caption",
            fontId = "default",
            fontSizeNorm = 0.08f,
            colorArgb = 0xFFFFFFFF.toInt(),
            bgColorArgb = 0x66000000,
            bold = true,
            italic = false,
            alignment = TextAlignment.CENTER,
            highlightRange = highlightRange
        )
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

    private fun createV4Database(path: File) {
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
                CREATE TABLE IF NOT EXISTS `effect_items` (
                    `id` TEXT NOT NULL,
                    `projectId` TEXT NOT NULL,
                    `effectId` TEXT NOT NULL,
                    `scope` TEXT NOT NULL,
                    `startMs` INTEGER NOT NULL,
                    `endMs` INTEGER NOT NULL,
                    `zOrder` INTEGER NOT NULL,
                    `paramsJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_effect_items_projectId` ON `effect_items` (`projectId`)")
            db.execSQL(
                """
                INSERT INTO `projects` (
                    `id`, `title`, `aspectRatio`, `exportQuality`, `planType`, `thumbnailUri`,
                    `createdAt`, `updatedAt`, `projectType`, `autoFinalDuration`,
                    `autoSecondsPerClip`, `autoTransitionType`, `autoMusicEnabled`, `autoMusicAssetId`
                ) VALUES ('project-1', 'Project 1', 'RATIO_9_16', 'QUALITY_720P', 'FREE', NULL, 1, 1, 'MANUAL', NULL, NULL, NULL, 0, NULL)
                """.trimIndent()
            )
            db.version = 4
        } finally {
            db.close()
        }
    }
}
