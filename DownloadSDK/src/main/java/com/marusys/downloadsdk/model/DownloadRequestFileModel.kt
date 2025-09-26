package com.marusys.downloadsdk.model

import com.marusys.downloadsdk.db.DataRequestModel


data class DownloadRequestFileModel (
    val id : Long = System.currentTimeMillis(),
    val downloadId : Long = 0,
    val fileName : String = "",
    val url : String = "",
    val progress : Float = 0.0f,
    val downloadedBytes : Long = 0L,
    val totalBytes : Long = 0L,
    val state : Int = 0 // 0: idle, 1: downloading, 2: completed, 3: failed
) {
    fun toDataRequestEntity() : DataRequestModel {
        return DataRequestModel(
            id = id,
            downloadId = downloadId,
            fileName = fileName,
            url = url,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            state = state
        )
    }
}