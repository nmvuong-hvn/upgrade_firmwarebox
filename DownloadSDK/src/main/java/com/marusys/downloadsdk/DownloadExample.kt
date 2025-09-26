package com.marusys.downloadsdk.example

import android.content.Context
import android.util.Log
import com.marusys.downloadsdk.Constants
import com.marusys.downloadsdk.DownloadListener
import com.marusys.downloadsdk.DownloadManager
import com.marusys.downloadsdk.model.DownloadRequestFileModel
import kotlinx.coroutines.*
import java.io.File

/**
 * Complete usage example demonstrating pause, resume, cancel, and network reconnection
 */
class DownloadExample(private val context: Context) {

    private val downloadManager = DownloadManager.getInstance()

    /**
     * Example: Basic download with all control features
     */
    fun startBasicDownload() {
        val downloadRequest = DownloadRequestFileModel(
            downloadId = System.currentTimeMillis(),
            fileName = "firmware_v1.4.0.bin",
            url = "https://example.com/firmware/firmware_v1.4.0.bin",
            downloadedBytes = 0L
        )

        val outputFile = File(context.getExternalFilesDir(null), downloadRequest.fileName)

        val listener = object : DownloadListener {
            override fun onProgressUpdate(downloadId: Long, downloaded: Long, total: Long, percentage: Int) {
                Log.d("Download", "Progress: $percentage% (${formatBytes(downloaded)}/${formatBytes(total)})")
            }

            override fun onDownloadComplete(downloadId: Long, success: Boolean, filePath: String?) {
                if (success) {
                    Log.d("Download", "✅ Download completed: $filePath")
                } else {
                    Log.d("Download", "❌ Download failed")
                }
            }

            override fun onDownloadError(downloadId: Long, error: String) {
                Log.e("Download", "⚠️ Error: $error")
            }

            override fun onDownloadStateChanged(downloadId: Long, state: Int) {
                val stateString = getStateString(state)
                Log.d("Download", "📱 State changed to: $stateString")
            }

            override fun onDownloadPaused(downloadId: Long) {
                Log.d("Download", "⏸️ Download paused")
            }

            override fun onDownloadResumed(downloadId: Long) {
                Log.d("Download", "▶️ Download resumed")
            }

            override fun onDownloadCancelled(downloadId: Long) {
                Log.d("Download", "❌ Download cancelled")
            }

            override fun onNetworkReconnected(downloadId: Long) {
                Log.d("Download", "🌐 Network reconnected - resuming download")
            }
        }

        // Start download
        val downloadId = downloadManager.startDownload(context, downloadRequest, outputFile, listener)

        // Demonstrate pause/resume/cancel after delays
        demonstrateControlOperations(downloadId)
    }

    /**
     * Demonstrate pause, resume, and cancel operations
     */
    private fun demonstrateControlOperations(downloadId: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            // Pause after 5 seconds
            delay(5000)
            if (downloadManager.pauseDownload(downloadId)) {
                Log.d("Example", "⏸️ Download paused successfully")

                // Show current progress while paused
                val progress = downloadManager.getDownloadProgress(downloadId)
                val total = downloadManager.getDownloadTotalSize(downloadId)
                Log.d("Example", "📊 Paused at: ${formatBytes(progress)}/${formatBytes(total)}")
            }

            // Resume after 3 seconds
            delay(3000)
            if (downloadManager.resumeDownload(downloadId)) {
                Log.d("Example", "▶️ Download resumed successfully")
            }

            // Cancel after 10 more seconds (optional)
            delay(10000)
            if (downloadManager.cancelDownload(downloadId)) {
                Log.d("Example", "❌ Download cancelled successfully")
            }
        }
    }

    /**
     * Example: Multiple downloads with different control operations
     */
    fun startMultipleDownloads() {
        val downloads = listOf(
            "firmware_v1.4.0.bin" to "https://example.com/firmware_v1.4.0.bin",
            "firmware_v1.4.1.bin" to "https://example.com/firmware_v1.4.1.bin",
            "firmware_v1.4.2.bin" to "https://example.com/firmware_v1.4.2.bin"
        )

        val downloadIds = mutableListOf<Long>()

        downloads.forEach { (fileName, url) ->
            val request = DownloadRequestFileModel(
                downloadId = System.currentTimeMillis() + downloadIds.size,
                fileName = fileName,
                url = url
            )

            val outputFile = File(context.getExternalFilesDir(null), fileName)
            val listener = createMultiDownloadListener(fileName)

            val downloadId = downloadManager.startDownload(context, request, outputFile, listener)
            downloadIds.add(downloadId)

            Log.d("Example", "🚀 Started download: $fileName (ID: $downloadId)")
        }

        // Monitor and control multiple downloads
        monitorMultipleDownloads(downloadIds)
    }

    private fun createMultiDownloadListener(fileName: String): DownloadListener {
        return object : DownloadListener {
            override fun onProgressUpdate(downloadId: Long, downloaded: Long, total: Long, percentage: Int) {
                // Throttle updates for multiple downloads
                if (percentage % 5 == 0) {
                    Log.d("MultiDownload", "[$fileName] $percentage%")
                }
            }

            override fun onDownloadComplete(downloadId: Long, success: Boolean, filePath: String?) {
                Log.d("MultiDownload", "[$fileName] ✅ Completed: $success")
            }

            override fun onDownloadError(downloadId: Long, error: String) {
                Log.e("MultiDownload", "[$fileName] ❌ Error: $error")
            }

            override fun onDownloadStateChanged(downloadId: Long, state: Int) {
                Log.d("MultiDownload", "[$fileName] State: ${getStateString(state)}")
            }

            override fun onDownloadPaused(downloadId: Long) {
                Log.d("MultiDownload", "[$fileName] ⏸️ Paused")
            }

            override fun onDownloadResumed(downloadId: Long) {
                Log.d("MultiDownload", "[$fileName] ▶️ Resumed")
            }

            override fun onDownloadCancelled(downloadId: Long) {
                Log.d("MultiDownload", "[$fileName] ❌ Cancelled")
            }

            override fun onNetworkReconnected(downloadId: Long) {
                Log.d("MultiDownload", "[$fileName] 🌐 Network restored")
            }
        }
    }

    private fun monitorMultipleDownloads(downloadIds: List<Long>) {
        CoroutineScope(Dispatchers.IO).launch {
            while (downloadIds.any { downloadManager.isDownloadActive(it) }) {
                // Print statistics every 5 seconds
                delay(5000)

                val stats = downloadManager.getDownloadStatistics()
                Log.d("Monitor", """
                    📊 Download Statistics:
                    - Total Active: ${stats.totalActive}
                    - Downloading: ${stats.downloading}
                    - Paused: ${stats.paused}
                    - Waiting for Network: ${stats.waitingForNetwork}
                    - Completed: ${stats.completed}
                    - Failed: ${stats.failed}
                """.trimIndent())

                // Show individual progress
                downloadIds.forEach { downloadId ->
                    val info = downloadManager.getDownloadInfo(downloadId)
                    if (info != null) {
                        Log.d("Monitor", "📁 ${info.fileName}: ${info.progress.toInt()}% (${getStateString(info.state)})")
                    }
                }
            }

            Log.d("Monitor", "🎉 All downloads completed!")
            downloadManager.cleanupCompletedDownloads()
        }
    }

    /**
     * Example: Resume interrupted download (simulate app restart)
     */
    fun resumeInterruptedDownload() {
        // Simulate a download that was interrupted at 50% (2MB downloaded out of 4MB)
        val partialDownloadRequest = DownloadRequestFileModel(
            downloadId = 12345L,
            fileName = "large_firmware.bin",
            url = "https://example.com/large_firmware.bin",
            downloadedBytes = 2 * 1024 * 1024L, // 2MB already downloaded
            totalBytes = 4 * 1024 * 1024L        // 4MB total
        )

        val outputFile = File(context.getExternalFilesDir(null), partialDownloadRequest.fileName)

        val listener = object : DownloadListener {
            override fun onProgressUpdate(downloadId: Long, downloaded: Long, total: Long, percentage: Int) {
                Log.d("Resume", "Resuming from 50%: Current $percentage% (${formatBytes(downloaded)}/${formatBytes(total)})")
            }

            override fun onDownloadComplete(downloadId: Long, success: Boolean, filePath: String?) {
                Log.d("Resume", "✅ Interrupted download completed successfully!")
            }

            override fun onDownloadError(downloadId: Long, error: String) {
                Log.e("Resume", "❌ Resume failed: $error")
            }

            override fun onDownloadStateChanged(downloadId: Long, state: Int) {
                Log.d("Resume", "Resume state: ${getStateString(state)}")
            }

            override fun onDownloadPaused(downloadId: Long) {}
            override fun onDownloadResumed(downloadId: Long) {}
            override fun onDownloadCancelled(downloadId: Long) {}

            override fun onNetworkReconnected(downloadId: Long) {
                Log.d("Resume", "🌐 Network restored during resume")
            }
        }

        // This will automatically resume from 2MB using HTTP Range header
        downloadManager.startDownload(context, partialDownloadRequest, outputFile, listener)
        Log.d("Resume", "🔄 Resuming download from ${formatBytes(partialDownloadRequest.downloadedBytes)}")
    }

    /**
     * Example: Batch operations on multiple downloads
     */
    fun demonstrateBatchOperations() {
        Log.d("Batch", "🔄 Pausing all active downloads...")
        downloadManager.pauseAllDownloads()

        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)

            Log.d("Batch", "▶️ Resuming all paused downloads...")
            downloadManager.resumeAllDownloads()

            delay(10000)

            Log.d("Batch", "❌ Cancelling all downloads...")
            downloadManager.cancelAllDownloads()
        }
    }

    // Utility functions
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }

    private fun getStateString(state: Int): String {
        return when (state) {
            Constants.STATE_IDLE -> "Idle"
            Constants.STATE_DOWNLOADING -> "Downloading"
            Constants.STATE_PAUSED -> "Paused"
            Constants.STATE_COMPLETED -> "Completed"
            Constants.STATE_FAILED -> "Failed"
            Constants.STATE_CANCELLED -> "Cancelled"
            Constants.STATE_WAITING_FOR_NETWORK -> "Waiting for Network"
            else -> "Unknown"
        }
    }
}
