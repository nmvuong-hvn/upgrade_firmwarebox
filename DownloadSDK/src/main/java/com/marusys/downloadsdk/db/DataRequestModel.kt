package com.marusys.downloadsdk.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_requests")
data class DataRequestModel(
    @PrimaryKey
    val downloadId: Long,
    val fileName: String = "",
    val url: String = "",
    val filePath: String = "", // Added: Full file path for resume
    val progress: Float = 0.0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val state: Int = 0, // Using Constants.STATE_* values
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val etag: String? = null, // Added: For server validation
    val retryCount: Int = 0, // Added: Track retry attempts
    val errorMessage: String? = null // Added: Last error message
)