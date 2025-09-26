//package com.marusys.downloadsdk.example
//
//import android.content.Context
//import android.util.Log
//import androidx.lifecycle.lifecycleScope
//import com.marusys.downloadsdk.Constants
//import com.marusys.downloadsdk.DownloadListener
//import com.marusys.downloadsdk.DownloadManager
//import com.marusys.downloadsdk.model.DownloadRequestFileModel
//import kotlinx.coroutines.*
//import java.io.File
//
///**
// * Complete example demonstrating Room database integration for resuming downloads
// * when the app is reopened
// */
//class DatabaseResumeExample(private val context: Context) {
//
//    private val downloadManager = DownloadManager.getInstance()
//
//    /**
//     * Initialize download manager in your Application class or MainActivity
//     */
//    fun initializeDownloadManager() {
//        // Initialize with automatic resume of incomplete downloads
//        downloadManager.initialize(
//            context = context,
//            autoResumeIncompleteDownloads = true // This will resume downloads when app opens
//        )
//
//        Log.d("DatabaseResume", "📱 DownloadManager initialized with database persistence")
//        Log.d("DatabaseResume", "🔄 Incomplete downloads will be automatically loaded and ready to resume")
//    }
//
//    /**
//     * Start a new download that will be persisted to database
//     */
//    fun startPersistentDownload() {
//        val downloadRequest = DownloadRequestFileModel(
//            downloadId = System.currentTimeMillis(),
//            fileName = "firmware_persistent.bin",
//            url = "https://example.com/firmware_persistent.bin",
//            downloadedBytes = 0L
//        )
//
//        val outputFile = File(context.getExternalFilesDir(null), downloadRequest.fileName)
//
//        val listener = object : DownloadListener {
//            override fun onProgressUpdate(downloadId: Long, downloaded: Long, total: Long, percentage: Int) {
//                Log.d("PersistentDownload", "Progress: $percentage% - ${formatBytes(downloaded)}/${formatBytes(total)}")
//                // Progress is automatically saved to database by CompositeDownloadListener
//            }
//
//            override fun onDownloadComplete(downloadId: Long, success: Boolean, filePath: String?) {
//                if (success) {
//                    Log.d("PersistentDownload", "✅ Download completed and saved to database: $filePath")
//                } else {
//                    Log.d("PersistentDownload", "❌ Download failed - state saved to database")
//                }
//            }
//
//            override fun onDownloadError(downloadId: Long, error: String) {
//                Log.e("PersistentDownload", "⚠️ Error (saved to DB): $error")
//            }
//
//            override fun onDownloadStateChanged(downloadId: Long, state: Int) {
//                Log.d("PersistentDownload", "📊 State changed to: ${getStateString(state)} (saved to DB)")
//            }
//
//            override fun onDownloadPaused(downloadId: Long) {
//                Log.d("PersistentDownload", "⏸️ Download paused - state persisted for resume")
//            }
//
//            override fun onDownloadResumed(downloadId: Long) {
//                Log.d("PersistentDownload", "▶️ Download resumed from database state")
//            }
//
//            override fun onDownloadCancelled(downloadId: Long) {
//                Log.d("PersistentDownload", "❌ Download cancelled - marked in database")
//            }
//
//            override fun onNetworkReconnected(downloadId: Long) {
//                Log.d("PersistentDownload", "🌐 Network restored - download will auto-resume")
//            }
//        }
//
//        // Start download - it will be automatically saved to database
//        val downloadId = downloadManager.startDownload(context, downloadRequest, outputFile, listener)
//
//        Log.d("DatabaseResume", "🚀 Started persistent download: ${downloadRequest.fileName} (ID: $downloadId)")
//        Log.d("DatabaseResume", "💾 Download progress will be saved to Room database")
//    }
//
//    /**
//     * Demonstrate manual resume of incomplete downloads
//     */
//    fun resumeIncompleteDownloadsManually() {
//        CoroutineScope(Dispatchers.Main).launch {
//            try {
//                // Get all downloads from database
//                val allDownloads = downloadManager.getAllDownloadsFromDatabase()
//
//                Log.d("DatabaseResume", "📊 Found ${allDownloads.size} downloads in database:")
//
//                allDownloads.forEach { download ->
//                    val stateString = getStateString(download.state)
//                    val progress = if (download.totalBytes > 0) {
//                        "${(download.downloadedBytes * 100 / download.totalBytes)}%"
//                    } else "0%"
//
//                    Log.d("DatabaseResume", "  📁 ${download.fileName}: $stateString ($progress)")
//
//                    // Resume paused downloads
//                    if (download.state == Constants.STATE_PAUSED && downloadManager.isDownloadActive(download.downloadId)) {
//                        Log.d("DatabaseResume", "🔄 Resuming paused download: ${download.fileName}")
//                        downloadManager.resumeDownload(download.downloadId)
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("DatabaseResume", "Error loading downloads from database", e)
//            }
//        }
//    }
//
//    /**
//     * Demonstrate observing downloads from database in real-time
//     */
//    fun observeDownloadsFromDatabase() {
//        // Observe all downloads from database for UI updates
//        CoroutineScope(Dispatchers.Main).launch {
//            downloadManager.getAllDownloadsFlow().collect { downloads ->
//                Log.d("DatabaseObserver", "📊 Database updated - ${downloads.size} total downloads:")
//
//                downloads.forEach { download ->
//                    val stateString = getStateString(download.state)
//                    val progressPercent = if (download.totalBytes > 0) {
//                        (download.downloadedBytes * 100 / download.totalBytes).toInt()
//                    } else 0
//
//                    Log.d("DatabaseObserver", "  📱 ${download.fileName}: $stateString ($progressPercent%)")
//                }
//            }
//        }
//    }
//
//    /**
//     * Simulate app restart scenario
//     */
//    fun simulateAppRestart() {
//        Log.d("AppRestart", "🔄 Simulating app restart...")
//
//        // This would normally happen when the app starts
//        downloadManager.initialize(context, autoResumeIncompleteDownloads = true)
//
//        // After initialization, incomplete downloads are automatically loaded
//        CoroutineScope(Dispatchers.IO).launch {
//            delay(2000) // Wait for initialization
//
//            val activeIds = downloadManager.getActiveDownloadIds()
//            Log.d("AppRestart", "📱 After restart - ${activeIds.size} downloads loaded from database")
//
//            activeIds.forEach { downloadId ->
//                val info = downloadManager.getDownloadInfo(downloadId)
//                if (info != null) {
//                    Log.d("AppRestart", "  🔄 Loaded: ${info.fileName} (${info.progress.toInt()}% complete)")
//                    Log.d("AppRestart", "     State: ${getStateString(info.state)}")
//                    Log.d("AppRestart", "     Progress: ${formatBytes(info.downloadedBytes)}/${formatBytes(info.totalBytes)}")
//                }
//            }
//        }
//    }
//
//    /**
//     * Demonstrate cleanup operations
//     */
//    fun demonstrateCleanupOperations() {
//        CoroutineScope(Dispatchers.IO).launch {
//            // Clean up old completed downloads (older than 7 days)
//            downloadManager.cleanupOldDownloads(daysOld = 7)
//            Log.d("Cleanup", "🧹 Cleaned up old completed downloads from database")
//
//            // Get download statistics from database
//            val stats = downloadManager.getDownloadStatistics()
//            Log.d("Cleanup", """
//                📊 Download Statistics:
//                - Active: ${stats.totalActive}
//                - Downloading: ${stats.downloading}
//                - Paused: ${stats.paused}
//                - Waiting for Network: ${stats.waitingForNetwork}
//                - Completed: ${stats.completed}
//                - Failed: ${stats.failed}
//            """.trimIndent())
//        }
//    }
//
//    /**
//     * Demonstrate deleting downloads with file cleanup
//     */
//    fun deleteDownloadExample(downloadId: Long) {
//        CoroutineScope(Dispatchers.IO).launch {
//            // Delete download from database and remove file
//            downloadManager.deleteDownload(downloadId, deleteFile = true)
//            Log.d("DeleteDownload", "🗑️ Deleted download $downloadId from database and removed file")
//        }
//    }
//
//    /**
//     * Create a download that will be paused and later resumed via database
//     */
//    fun createResumableDownload() {
//        val downloadRequest = DownloadRequestFileModel(
//            downloadId = System.currentTimeMillis(),
//            fileName = "large_resumable_file.bin",
//            url = "https://example.com/large_resumable_file.bin"
//        )
//
//        val outputFile = File(context.getExternalFilesDir(null), downloadRequest.fileName)
//
//        val listener = object : DownloadListener {
//            override fun onProgressUpdate(downloadId: Long, downloaded: Long, total: Long, percentage: Int) {
//                // Automatically saved to database
//                if (percentage > 0 && percentage % 25 == 0) {
//                    Log.d("ResumableDownload", "📊 Progress milestone: $percentage% - saved to database")
//                }
//            }
//
//            override fun onDownloadComplete(downloadId: Long, success: Boolean, filePath: String?) {
//                Log.d("ResumableDownload", "✅ Download completed - final state saved to database")
//            }
//
//            override fun onDownloadError(downloadId: Long, error: String) {
//                Log.e("ResumableDownload", "❌ Error saved to database: $error")
//            }
//
//            override fun onDownloadStateChanged(downloadId: Long, state: Int) {
//                Log.d("ResumableDownload", "State changed: ${getStateString(state)} - persisted to DB")
//            }
//
//            override fun onDownloadPaused(downloadId: Long) {
//                Log.d("ResumableDownload", "⏸️ Paused - ready for resume after app restart")
//            }
//
//            override fun onDownloadResumed(downloadId: Long) {
//                Log.d("ResumableDownload", "▶️ Resumed from database state")
//            }
//
//            override fun onDownloadCancelled(downloadId: Long) {
//                Log.d("ResumableDownload", "❌ Cancelled")
//            }
//
//            override fun onNetworkReconnected(downloadId: Long) {
//                Log.d("ResumableDownload", "🌐 Network restored - auto-resuming")
//            }
//        }
//
//        // Start download
//        val downloadId = downloadManager.startDownload(context, downloadRequest, outputFile, listener)
//
//        // Simulate user pausing the download after 3 seconds
//        CoroutineScope(Dispatchers.Main).launch {
//            delay(3000)
//            downloadManager.pauseDownload(downloadId)
//            Log.d("ResumableDownload", "⏸️ Download paused - state saved to database")
//            Log.d("ResumableDownload", "🔄 This download can now be resumed even after app restart")
//        }
//    }
//
//    // Utility functions
//    private fun formatBytes(bytes: Long): String {
//        return when {
//            bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
//            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
//            bytes >= 1024 -> "${bytes / 1024} KB"
//            else -> "$bytes B"
//        }
//    }
//
//    private fun getStateString(state: Int): String {
//        return when (state) {
//            Constants.STATE_IDLE -> "Idle"
//            Constants.STATE_DOWNLOADING -> "Downloading"
//            Constants.STATE_PAUSED -> "Paused"
//            Constants.STATE_COMPLETED -> "Completed"
//            Constants.STATE_FAILED -> "Failed"
//            Constants.STATE_CANCELLED -> "Cancelled"
//            Constants.STATE_WAITING_FOR_NETWORK -> "Waiting for Network"
//            else -> "Unknown"
//        }
//    }
//}
//
///**
// * Example Application class showing proper initialization
// */
///*
//class MyApplication : Application() {
//    override fun onCreate() {
//        super.onCreate()
//
//        // Initialize download manager with database persistence
//        val downloadManager = DownloadManager.getInstance()
//        downloadManager.initialize(
//            context = this,
//            autoResumeIncompleteDownloads = true
//        )
//    }
//}
//*/
//
///**
// * Example Activity showing usage
// */
///*
//class MainActivity : AppCompatActivity() {
//    private lateinit var databaseExample: DatabaseResumeExample
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        databaseExample = DatabaseResumeExample(this)
//
//        // Initialize (if not done in Application class)
//        databaseExample.initializeDownloadManager()
//
//        // Start observing downloads from database
//        databaseExample.observeDownloadsFromDatabase()
//
//        // Start a persistent download
//        databaseExample.startPersistentDownload()
//
//        // Create a resumable download
//        databaseExample.createResumableDownload()
//    }
//
//    override fun onResume() {
//        super.onResume()
//        // Resume any paused downloads
//        databaseExample.resumeIncompleteDownloadsManually()
//    }
//}
//*/
