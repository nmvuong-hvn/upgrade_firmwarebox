package com.marusys.downloadsdk

/**
 * Interface for download operations and state management
 */
interface DownloadController {
    fun pauseDownload()
    fun resumeDownload()
    fun cancelDownload()
    fun getDownloadState(): Int
    fun getCurrentProgress(): Long
    fun getTotalSize(): Long
}

/**
 * Listener interface for download events
 */
interface DownloadListener {
    fun onProgressUpdate(downloadId: Long, downloaded: Long, total: Long, percentage: Int)
    fun onDownloadComplete(downloadId: Long, success: Boolean, filePath: String?)
    fun onDownloadError(downloadId: Long, error: String)
    fun onDownloadStateChanged(downloadId: Long, state: Int)
    fun onDownloadPaused(downloadId: Long)
    fun onDownloadResumed(downloadId: Long)
    fun onDownloadCancelled(downloadId: Long)
    fun onNetworkReconnected(downloadId: Long)
}
