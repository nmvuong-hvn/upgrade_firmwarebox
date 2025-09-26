package com.marusys.upgradefirmware

/**
 * Interface for download progress and state callbacks
 */
interface DownloadListener {
    fun onProgressUpdate(downloaded: Long, total: Long, percentage: Float)
    fun onDownloadComplete(success: Boolean, filePath: String?)
    fun onDownloadError(error: String)
    fun onDownloadStateChanged(state: DownloadState)
}

/**
 * Interface for download operations
 */
interface DownloadController {
    suspend fun startDownload()
    fun pauseDownload()
    fun resumeDownload()
    fun cancelDownload()
    fun getDownloadState(): DownloadState
}

/**
 * Enum representing download states
 */
enum class DownloadState {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}
