package com.marusys.upgradefirmware.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val downloadId: Long,
    val url: String,
    val fileName: String,
    val filePath: String,
    val tempPath: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: Int = STATUS_PENDING, // 0: pending, 1: downloading, 2: paused, 3: completed, 4: failed, 5: cancelled
    val expectedMD5: String? = null,
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_FAILED = 4
        const val STATUS_CANCELLED = 5
    }
}
