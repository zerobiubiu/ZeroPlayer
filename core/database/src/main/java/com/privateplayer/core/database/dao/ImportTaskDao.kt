package com.privateplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.privateplayer.core.database.entity.ImportTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportTaskDao {

    @Query("SELECT * FROM import_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImportTaskEntity>>

    @Query(
        """
        SELECT * FROM import_tasks
        WHERE state IN ('QUEUED', 'RUNNING', 'COMMITTING')
        ORDER BY createdAt DESC
        """,
    )
    fun observeActive(): Flow<List<ImportTaskEntity>>

    @Query("SELECT * FROM import_tasks WHERE id = :id")
    suspend fun findById(id: String): ImportTaskEntity?

    @Query("SELECT * FROM import_tasks WHERE state IN ('QUEUED', 'RUNNING', 'COMMITTING') ORDER BY createdAt ASC")
    suspend fun findPending(): List<ImportTaskEntity>

    @Query("SELECT COUNT(*) FROM import_tasks WHERE state IN ('QUEUED', 'RUNNING', 'COMMITTING')")
    suspend fun countActive(): Int

    @Insert
    suspend fun insert(task: ImportTaskEntity)

    @Query("UPDATE import_tasks SET state = :state, updatedAt = :now WHERE id = :id")
    suspend fun updateState(id: String, state: String, now: Long)

    @Query("UPDATE import_tasks SET state = 'RUNNING', updatedAt = :now WHERE id = :id AND state IN ('QUEUED', 'RUNNING', 'COMMITTING', 'FAILED')")
    suspend fun claimForImport(id: String, now: Long): Int

    @Query("UPDATE import_tasks SET state = 'COMMITTING', updatedAt = :now WHERE id = :id AND state = 'RUNNING'")
    suspend fun beginCommit(id: String, now: Long): Int

    @Query("UPDATE import_tasks SET state = 'CANCELED', updatedAt = :now WHERE id = :id AND state NOT IN ('COMPLETED', 'CANCELED')")
    suspend fun cancelUnlessFinished(id: String, now: Long): Int

    @Query("UPDATE import_tasks SET state = 'QUEUED', errorMessage = NULL, updatedAt = :now WHERE id = :id AND state = 'FAILED'")
    suspend fun retryFailed(id: String, now: Long): Int

    @Query("UPDATE import_tasks SET state = 'FAILED', bytesCopied = :bytesCopied, errorMessage = :message, updatedAt = :now WHERE id = :id AND state IN ('RUNNING', 'COMMITTING')")
    suspend fun failIfActive(id: String, bytesCopied: Long, message: String, now: Long): Int

    @Query("UPDATE import_tasks SET state = 'FAILED', bytesCopied = :bytesCopied, errorMessage = :message, updatedAt = :now WHERE id = :id AND state = 'CANCELED'")
    suspend fun failIfCanceled(id: String, bytesCopied: Long, message: String, now: Long): Int

    /** 只更新进度，不覆盖状态，避免与用户的取消操作相互覆盖。 */
    @Query("UPDATE import_tasks SET bytesCopied = :bytesCopied, updatedAt = :now WHERE id = :id")
    suspend fun updateBytesCopied(id: String, bytesCopied: Long, now: Long)

    @Query("SELECT state FROM import_tasks WHERE id = :id")
    suspend fun findState(id: String): String?

    @Query(
        """
        UPDATE import_tasks
        SET state = :state, bytesCopied = :bytesCopied, errorMessage = :errorMessage, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: String,
        state: String,
        bytesCopied: Long,
        errorMessage: String?,
        now: Long,
    )

    @Query("UPDATE import_tasks SET state = :state, mediaId = :mediaId, updatedAt = :now WHERE id = :id")
    suspend fun complete(id: String, state: String, mediaId: Long?, now: Long)

    @Query("UPDATE import_tasks SET state = 'COMPLETED', mediaId = :mediaId, errorMessage = :notice, updatedAt = :now WHERE id = :id")
    suspend fun completeWithNotice(id: String, mediaId: Long?, notice: String?, now: Long)

    @Query("DELETE FROM import_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM import_tasks WHERE state IN ('COMPLETED', 'CANCELED') AND updatedAt < :cutoff")
    suspend fun pruneFinished(cutoff: Long)
}
