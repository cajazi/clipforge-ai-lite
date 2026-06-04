package com.clipforge.ai.data.local.database
import android.content.Context
import androidx.room.*
import com.clipforge.ai.core.utils.Constants
import com.clipforge.ai.data.local.dao.*
import com.clipforge.ai.data.local.entity.*
@Database(entities = [ProjectEntity::class, MediaAssetEntity::class, TimelineItemEntity::class],
    version = Constants.DB_VERSION, exportSchema = true)
abstract class ClipForgeDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun timelineDao(): TimelineDao
    companion object {
        @Volatile private var INSTANCE: ClipForgeDatabase? = null
        fun getInstance(context: Context): ClipForgeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext,
                    ClipForgeDatabase::class.java, Constants.DB_NAME)
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
