package com.privateplayer.data.mapper

import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.data.importer.webUploadSource
import com.privateplayer.domain.model.ImportTaskState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTaskMappingTest {
    @Test
    fun onlyTheTasksOwnWebUploadSourceMapsToAWebTask() {
        val task = ImportTaskEntity(
            id = "task-a", sourceUri = "content://documents/video", displayName = "video.mp4",
            mimeType = "video/mp4", sizeBytes = 10, targetFileName = "video.mp4", folderId = null,
            tempRelativePath = "", state = ImportTaskState.FAILED.name, bytesCopied = 2,
            errorMessage = null, mediaId = null, createdAt = 0, updatedAt = 0,
        )
        assertFalse(task.toDomain().isWebUpload)
        assertTrue(task.copy(sourceUri = webUploadSource(task.id)).toDomain().isWebUpload)
        assertFalse(task.copy(sourceUri = webUploadSource("task-b")).toDomain().isWebUpload)
    }
}
