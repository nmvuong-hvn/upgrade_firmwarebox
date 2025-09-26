package com.marusys.upgradefirmware

/**
 * Handles progress tracking and calculations for downloads
 */
class DownloadProgressTracker(
    private val totalSize: Long,
    private var downloadedBytes: Long = 0L,
    private val listener: DownloadListener?
) {

    fun updateProgress(bytesRead: Int) {
        downloadedBytes += bytesRead
        notifyProgress()
    }

    private fun notifyProgress() {
        val percentage = calculatePercentage()
        listener?.onProgressUpdate(downloadedBytes, totalSize, percentage)
    }

    private fun calculatePercentage(): Float {
        return if (totalSize > 0) {
            ((downloadedBytes * 100.0) / totalSize).toFloat()
        } else {
            0f
        }
    }

    fun getCurrentProgress(): Long = downloadedBytes

    fun isCompleted(): Boolean = totalSize > 0 && downloadedBytes >= totalSize
}
