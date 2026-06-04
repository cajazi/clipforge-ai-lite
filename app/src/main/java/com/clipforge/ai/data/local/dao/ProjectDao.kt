package com.clipforge.ai.data.local.dao
import androidx.room.*
import com.clipforge.ai.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") fun getAllProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1") suspend fun getProjectById(id: String): ProjectEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProject(project: ProjectEntity)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun deleteProjectById(id: String)
}
