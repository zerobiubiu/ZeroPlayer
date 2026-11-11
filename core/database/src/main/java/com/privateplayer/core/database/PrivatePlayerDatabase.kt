package com.privateplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.privateplayer.core.database.dao.FolderDao
import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.database.dao.MediaDao
import com.privateplayer.core.database.dao.PlaybackHistoryDao
import com.privateplayer.core.database.dao.PlaylistDao
import com.privateplayer.core.database.entity.FolderEntity
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.database.entity.MediaEntity
import com.privateplayer.core.database.entity.PlaybackHistoryEntity
import com.privateplayer.core.database.entity.PlaylistEntity
import com.privateplayer.core.database.entity.PlaylistItemEntity

@Database(
    entities = [
        MediaEntity::class,
        FolderEntity::class,
        PlaybackHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        ImportTaskEntity::class,
    ],
    version = PrivatePlayerDatabase.VERSION,
    exportSchema = true,
)
abstract class PrivatePlayerDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    abstract fun folderDao(): FolderDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    abstract fun importTaskDao(): ImportTaskDao

    companion object {
        const val VERSION = 2

        /** 位于 `databases/` 目录：`databases/media.db`。 */
        const val NAME = "media.db"
    }
}
