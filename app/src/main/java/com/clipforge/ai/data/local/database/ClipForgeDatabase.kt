package com.clipforge.ai.data.local.database
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clipforge.ai.core.utils.Constants
import com.clipforge.ai.data.local.dao.*
import com.clipforge.ai.data.local.entity.*
@Database(entities = [ProjectEntity::class, MediaAssetEntity::class, TimelineItemEntity::class, EffectItemEntity::class],
    version = Constants.DB_VERSION, exportSchema = true)
abstract class ClipForgeDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun timelineDao(): TimelineDao
    abstract fun effectItemDao(): EffectItemDao
    companion object {
        @Volatile private var INSTANCE: ClipForgeDatabase? = null
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }
        fun getInstance(context: Context): ClipForgeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext,
                    ClipForgeDatabase::class.java, Constants.DB_NAME)
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build().also { INSTANCE = it }
            }
    }
}
