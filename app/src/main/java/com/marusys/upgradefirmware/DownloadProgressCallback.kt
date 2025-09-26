package com.marusys.upgradefirmware

/**
 * Interface để MainActivity nhận updates từ DownloadingManager
 * Đặc biệt quan trọng cho network reconnection auto-resume
 */
interface DownloadProgressCallback {
    fun onProgressUpdate(downloadId: String, downloaded: Long, total: Long, speed: String)
    fun onDownloadStateChanged(downloadId: String, state: Int)
    fun onNetworkReconnected(downloadId: String)
    fun onNetworkLost(downloadId: String)
    fun onDownloadCompleted(downloadId: String, success: Boolean, filePath: String?)
    fun onDownloadError(downloadId: String, error: String)
}
