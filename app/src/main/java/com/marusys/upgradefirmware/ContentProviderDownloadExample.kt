//package com.marusys.upgradefirmware
//
//import android.content.Context
//import kotlinx.coroutines.*
//
///**
// * Usage examples for ContentProvider-based download system
// * Compatible with Android 14+ scoped storage requirements
// */
//class ContentProviderDownloadExample {
//
//    fun demonstrateBasicDownloadWithContentProvider(context: Context) {
//        val firmwareManager = FirmwareManager.getInstance()
//
//        // Create download request
//        val downloadRequest = DownloadRequest.Builder()
//            .url("https://example.com/firmware.bin")
//            .downloadId(System.currentTimeMillis())
//            .connectTimeOut(30_000)
//            .build()
//
//        val fileName = "Firmware01_1.4.0.bin"
//
//        // Create listener for handling download events
//        val downloadListener = object : DownloadListener {
//            override fun onProgressUpdate(downloaded: Long, total: Long, percentage: Int) {
//                println("Download progress: $percentage% (${formatBytes(downloaded)}/${formatBytes(total)})")
//            }
//
//            override fun onDownloadComplete(success: Boolean, filePath: String?) {
//                if (success) {
//                    println("✅ Download completed successfully!")
//                    println("File URI: $filePath")
//                    println("File saved to Downloads folder via MediaStore")
//                } else {
//                    println("❌ Download failed")
//                }
//            }
//
//            override fun onDownloadError(error: String) {
//                println("⚠️ Download error: $error")
//            }
//
//            override fun onDownloadStateChanged(state: DownloadState) {
//                println("📱 Download state: $state")
//            }
//        }
//
//        // Start download with ContentProvider support
//        try {
//            val downloadId = firmwareManager.startDownload(
//                context = context,
//                request = downloadRequest,
//                fileName = fileName,
//                listener = downloadListener
//            )
//            println("🚀 Download started with ID: $downloadId")
//            println("💾 File will be saved to Downloads folder using MediaStore API")
//        } catch (e: IllegalStateException) {
//            println("❌ Failed to start download: ${e.message}")
//        }
//    }
//
//    fun demonstrateDownloadControlWithUri(context: Context) {
//        val firmwareManager = FirmwareManager.getInstance()
//        val downloadId = System.currentTimeMillis()
//
//        CoroutineScope(Dispatchers.Main).launch {
//            // Start a download
//            val request = DownloadRequest.Builder()
//                .url("https://example.com/large-firmware.bin")
//                .downloadId(downloadId)
//                .build()
//
//            firmwareManager.startDownload(
//                context = context,
//                request = request,
//                fileName = "large-firmware.bin"
//            )
//
//            // Monitor and control the download
//            delay(5_000)
//
//            // Pause download
//            if (firmwareManager.pauseDownload(downloadId)) {
//                println("⏸️ Download paused successfully")
//
//                // Get file URI while paused
//                val fileUri = firmwareManager.getDownloadFileUri(downloadId)
//                println("📁 Current file URI: $fileUri")
//            }
//
//            // Resume after 3 seconds
//            delay(3_000)
//            if (firmwareManager.resumeDownload(downloadId)) {
//                println("▶️ Download resumed successfully")
//            }
//
//            // Cancel after 10 more seconds
//            delay(10_000)
//            if (firmwareManager.cancelDownload(downloadId)) {
//                println("❌ Download cancelled - file automatically deleted")
//            }
//        }
//    }
//
//    fun demonstrateMultipleDownloadsWithContentProvider(context: Context) {
//        val firmwareManager = FirmwareManager.getInstance()
//        val downloadIds = mutableListOf<Long>()
//
//        // Start multiple firmware downloads
//        val firmwareFiles = listOf(
//            "Firmware_v1.4.0.bin",
//            "Firmware_v1.4.1.bin",
//            "Firmware_v1.4.2.bin"
//        )
//
//        firmwareFiles.forEachIndexed { index, fileName ->
//            val request = DownloadRequest.Builder()
//                .url("https://example.com/firmware/$fileName")
//                .downloadId(System.currentTimeMillis() + index)
//                .build()
//
//            val downloadId = firmwareManager.startDownload(
//                context = context,
//                request = request,
//                fileName = fileName,
//                listener = createDownloadListener(fileName)
//            )
//
//            downloadIds.add(downloadId)
//            println("📥 Started download: $fileName (ID: $downloadId)")
//        }
//
//        // Monitor all downloads
//        monitorMultipleDownloads(firmwareManager, downloadIds)
//    }
//
//    private fun createDownloadListener(fileName: String): DownloadListener {
//        return object : DownloadListener {
//            override fun onProgressUpdate(downloaded: Long, total: Long, percentage: Int) {
//                // Throttle progress updates for multiple downloads
//                if (percentage % 10 == 0) {
//                    println("[$fileName] Progress: $percentage%")
//                }
//            }
//
//            override fun onDownloadComplete(success: Boolean, filePath: String?) {
//                if (success) {
//                    println("✅ [$fileName] Download completed: $filePath")
//                } else {
//                    println("❌ [$fileName] Download failed")
//                }
//            }
//
//            override fun onDownloadError(error: String) {
//                println("⚠️ [$fileName] Error: $error")
//            }
//
//            override fun onDownloadStateChanged(state: DownloadState) {
//                println("📱 [$fileName] State: $state")
//            }
//        }
//    }
//
//    private fun monitorMultipleDownloads(
//        firmwareManager: FirmwareManager,
//        downloadIds: List<Long>
//    ) {
//        CoroutineScope(Dispatchers.IO).launch {
//            while (downloadIds.any { firmwareManager.isDownloadActive(it) }) {
//                println("\n📊 === Download Status ===")
//
//                downloadIds.forEach { downloadId ->
//                    val state = firmwareManager.getDownloadState(downloadId)
//                    val progress = firmwareManager.getDownloadProgress(downloadId)
//                    val isActive = firmwareManager.isDownloadActive(downloadId)
//                    val fileUri = firmwareManager.getDownloadFileUri(downloadId)
//
//                    println("ID: $downloadId | State: $state | Progress: ${formatBytes(progress)} | Active: $isActive")
//                    if (fileUri != null) {
//                        println("   URI: $fileUri")
//                    }
//                }
//
//                println("Active downloads: ${firmwareManager.getActiveDownloadCount()}")
//                println("========================\n")
//
//                delay(3_000) // Check every 3 seconds
//            }
//
//            println("🎉 All downloads completed!")
//        }
//    }
//
//    fun demonstrateResumeDownloadFromUri(context: Context) {
//        // This example shows how to resume a download that was previously started
//        val firmwareManager = FirmwareManager.getInstance()
//
//        // Simulate a download that was interrupted
//        val existingDownloadId = 12345L
//        val partiallyDownloadedBytes = 1024 * 512 // 512KB already downloaded
//
//        val resumeRequest = DownloadRequest.Builder()
//            .url("https://example.com/firmware.bin")
//            .downloadId(existingDownloadId)
//            .downloadedBytes(partiallyDownloadedBytes.toLong()) // Resume from this position
//            .build()
//
//        val listener = object : DownloadListener {
//            override fun onProgressUpdate(downloaded: Long, total: Long, percentage: Int) {
//                println("📈 Resuming download: $percentage% (${formatBytes(downloaded)}/${formatBytes(total)})")
//            }
//
//            override fun onDownloadComplete(success: Boolean, filePath: String?) {
//                println("✅ Resume download completed: $filePath")
//            }
//
//            override fun onDownloadError(error: String) {
//                println("❌ Resume download error: $error")
//            }
//
//            override fun onDownloadStateChanged(state: DownloadState) {
//                println("🔄 Resume download state: $state")
//            }
//        }
//
//        try {
//            firmwareManager.startDownload(
//                context = context,
//                request = resumeRequest,
//                fileName = "firmware_resumed.bin",
//                listener = listener
//            )
//            println("🔄 Resume download started from ${formatBytes(partiallyDownloadedBytes.toLong())}")
//        } catch (e: Exception) {
//            println("❌ Failed to resume download: ${e.message}")
//        }
//    }
//
//    private fun formatBytes(bytes: Long): String {
//        return when {
//            bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
//            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
//            bytes >= 1024 -> "${bytes / 1024} KB"
//            else -> "$bytes B"
//        }
//    }
//}
