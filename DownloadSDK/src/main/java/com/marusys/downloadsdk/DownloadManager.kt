package com.marusys.downloadsdk

import android.content.Context
import android.util.Log
import com.marusys.downloadsdk.db.DownloadPersistenceManager
import com.marusys.downloadsdk.model.DownloadRequestFileModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced Download Manager with Room database persistence
 * Automatically resumes downloads when app is reopened
 */
class DownloadManager private constructor() {
    
    private val TAG = "DownloadManager"
    private val activeDownloads = ConcurrentHashMap<Long, DownloadTaskManager>()
    private val persistenceManager = DownloadPersistenceManager.getInstance()
    private val resumeScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null
        
        fun getInstance(): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize the download manager and resume incomplete downloads
     */
    fun initialize(context: Context, autoResumeIncompleteDownloads: Boolean = true) {
        persistenceManager.initialize(context)
        
        if (autoResumeIncompleteDownloads) {
            resumeIncompleteDownloads(context)
        }
        
        Log.d(TAG, "DownloadManager initialized")
    }
    
    /**
     * Resume all incomplete downloads from database when app opens
     */
    fun resumeIncompleteDownloads(context: Context) {
        resumeScope.launch {
            try {
                val incompleteDownloads = persistenceManager.getIncompleteDownloads()
                Log.d(TAG, "Resuming ${incompleteDownloads.size} incomplete downloads")
                
                incompleteDownloads.forEach { request ->
                    // Validate that the file still exists and has correct size
                    if (persistenceManager.validateDownloadFile(request.downloadId)) {
                        val outputFile = File(context.getExternalFilesDir(null), request.fileName)
                        resumeDownloadFromDatabase(context, request, outputFile)
                    } else {
                        Log.w(TAG, "Cannot resume download ${request.fileName} - file validation failed")
                        // Reset download progress in database
                        persistenceManager.updateProgress(request.downloadId, 0L, request.totalBytes)
                        persistenceManager.updateState(request.downloadId, Constants.STATE_IDLE)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming incomplete downloads", e)
            }
        }
    }
    
    private fun resumeDownloadFromDatabase(
        context: Context,
        request: DownloadRequestFileModel,
        outputFile: File
    ) {
        Log.d(TAG, "Resuming download: ${request.fileName} from ${formatBytes(request.downloadedBytes)}")
        
        val listener = DatabaseIntegratedListener(request.downloadId)
        val downloadTask = DownloadTaskManager(context, request, outputFile, listener)
        activeDownloads[request.downloadId] = downloadTask
        
        // Set initial state to paused so user can choose to resume
        persistenceManager.updateState(request.downloadId, Constants.STATE_PAUSED)
        
        Log.d(TAG, "Download ${request.fileName} ready to resume (currently paused)")
    }
    
    /**
     * Start a new download with database persistence
     */
    fun startDownload(
        context: Context,
        downloadRequest: DownloadRequestFileModel,
        outputFile: File,
        listener: DownloadListener? = null
    ): Long {
        val downloadId = downloadRequest.downloadId
        
        // Check if download is already active
        if (isDownloadActive(downloadId)) {
            Log.w(TAG, "Download with ID $downloadId is already active")
            return downloadId
        }
        
        // Save to database
        resumeScope.launch {
            persistenceManager.saveDownloadRequest(downloadRequest, outputFile.absolutePath)
        }
        
        // Create composite listener that includes database updates
        val compositeListener = CompositeDownloadListener(downloadId, listener)
        
        // Create and start download task
        val downloadTask = DownloadTaskManager(context, downloadRequest, outputFile, compositeListener)
        activeDownloads[downloadId] = downloadTask
        downloadTask.startDownload()
        
        Log.d(TAG, "Started download: ${downloadRequest.fileName} (ID: $downloadId)")
        return downloadId
    }
    
    /**
     * Pause a download
     */
    fun pauseDownload(downloadId: Long): Boolean {
        return activeDownloads[downloadId]?.let { task ->
            task.pauseDownload()
            persistenceManager.updateState(downloadId, Constants.STATE_PAUSED)
            Log.d(TAG, "Paused download ID: $downloadId")
            true
        } ?: false
    }
    
    /**
     * Resume a paused download
     */
    fun resumeDownload(downloadId: Long): Boolean {
        return activeDownloads[downloadId]?.let { task ->
            task.resumeDownload()
            persistenceManager.updateState(downloadId, Constants.STATE_DOWNLOADING)
            Log.d(TAG, "Resumed download ID: $downloadId")
            true
        } ?: false
    }
    
    /**
     * Cancel a download
     */
    fun cancelDownload(downloadId: Long): Boolean {
        return activeDownloads[downloadId]?.let { task ->
            task.cancelDownload()
            persistenceManager.updateState(downloadId, Constants.STATE_CANCELLED)
            activeDownloads.remove(downloadId)
            Log.d(TAG, "Cancelled download ID: $downloadId")
            true
        } ?: false
    }
    
    /**
     * Cancel all active downloads
     */
    fun cancelAllDownloads() {
        val downloadIds = activeDownloads.keys.toList()
        downloadIds.forEach { downloadId ->
            cancelDownload(downloadId)
        }
        Log.d(TAG, "Cancelled all downloads (${downloadIds.size} downloads)")
    }
    
    /**
     * Get download state
     */
    fun getDownloadState(downloadId: Long): Int? {
        return activeDownloads[downloadId]?.getDownloadState()
    }
    
    /**
     * Get current progress in bytes
     */
    fun getDownloadProgress(downloadId: Long): Long {
        return activeDownloads[downloadId]?.getCurrentProgress() ?: 0L
    }
    
    /**
     * Get total file size in bytes
     */
    fun getDownloadTotalSize(downloadId: Long): Long {
        return activeDownloads[downloadId]?.getTotalSize() ?: 0L
    }
    
    /**
     * Get complete download information
     */
    fun getDownloadInfo(downloadId: Long): DownloadRequestFileModel? {
        return activeDownloads[downloadId]?.getDownloadInfo()
    }
    
    /**
     * Check if download is active
     */
    fun isDownloadActive(downloadId: Long): Boolean {
        return activeDownloads[downloadId]?.isActive() == true
    }
    
    /**
     * Get all active download IDs
     */
    fun getActiveDownloadIds(): Set<Long> {
        return activeDownloads.keys.toSet()
    }
    
    /**
     * Get number of active downloads
     */
    fun getActiveDownloadCount(): Int = activeDownloads.size
    
    /**
     * Pause all active downloads
     */
    fun pauseAllDownloads() {
        activeDownloads.values.forEach { task ->
            task.pauseDownload()
        }
        Log.d(TAG, "Paused all downloads (${activeDownloads.size} downloads)")
    }
    
    /**
     * Resume all paused downloads
     */
    fun resumeAllDownloads() {
        activeDownloads.values.forEach { task ->
            if (task.getDownloadState() == Constants.STATE_PAUSED) {
                task.resumeDownload()
            }
        }
        Log.d(TAG, "Resumed all paused downloads")
    }
    
    /**
     * Clean up completed or failed downloads from active list
     */
    fun cleanupCompletedDownloads() {
        val completedIds = mutableListOf<Long>()
        
        activeDownloads.forEach { (id, task) ->
            val state = task.getDownloadState()
            if (state in listOf(Constants.STATE_COMPLETED, Constants.STATE_FAILED, Constants.STATE_CANCELLED)) {
                task.cleanup()
                completedIds.add(id)
            }
        }
        
        completedIds.forEach { id ->
            activeDownloads.remove(id)
        }
        
        if (completedIds.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${completedIds.size} completed downloads")
        }
    }
    
    /**
     * Get download statistics
     */
    fun getDownloadStatistics(): DownloadStatistics {
        var totalDownloading = 0
        var totalPaused = 0
        var totalWaitingForNetwork = 0
        var totalCompleted = 0
        var totalFailed = 0
        
        activeDownloads.values.forEach { task ->
            when (task.getDownloadState()) {
                Constants.STATE_DOWNLOADING -> totalDownloading++
                Constants.STATE_PAUSED -> totalPaused++
                Constants.STATE_WAITING_FOR_NETWORK -> totalWaitingForNetwork++
                Constants.STATE_COMPLETED -> totalCompleted++
                Constants.STATE_FAILED -> totalFailed++
            }
        }
        
        return DownloadStatistics(
            totalActive = activeDownloads.size,
            downloading = totalDownloading,
            paused = totalPaused,
            waitingForNetwork = totalWaitingForNetwork,
            completed = totalCompleted,
            failed = totalFailed
        )
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
    
    /**
     * Composite listener that handles both user callbacks and database persistence
     */
    private inner class CompositeDownloadListener(
        private val downloadId: Long,
        private val userListener: DownloadListener?
    ) : DownloadListener {
        
        override fun onProgressUpdate(downloadId: Long, downloaded: Long, total: Long, percentage: Int) {
            // Update database
            persistenceManager.updateProgress(downloadId, downloaded, total)
            // Notify user listener
            userListener?.onProgressUpdate(downloadId, downloaded, total, percentage)
        }
        
        override fun onDownloadComplete(downloadId: Long, success: Boolean, filePath: String?) {
            // Update database
            val state = if (success) Constants.STATE_COMPLETED else Constants.STATE_FAILED
            persistenceManager.updateState(downloadId, state)
            // Remove from active downloads
            activeDownloads.remove(downloadId)
            // Notify user listener
            userListener?.onDownloadComplete(downloadId, success, filePath)
        }
        
        override fun onDownloadError(downloadId: Long, error: String) {
            // Update database
            persistenceManager.updateState(downloadId, Constants.STATE_FAILED, error)
            // Notify user listener
            userListener?.onDownloadError(downloadId, error)
        }
        
        override fun onDownloadStateChanged(downloadId: Long, state: Int) {
            // Update database
            persistenceManager.updateState(downloadId, state)
            // Notify user listener
            userListener?.onDownloadStateChanged(downloadId, state)
        }
        
        override fun onDownloadPaused(downloadId: Long) {
            persistenceManager.updateState(downloadId, Constants.STATE_PAUSED)
            userListener?.onDownloadPaused(downloadId)
        }
        
        override fun onDownloadResumed(downloadId: Long) {
            persistenceManager.updateState(downloadId, Constants.STATE_DOWNLOADING)
            userListener?.onDownloadResumed(downloadId)
        }
        
        override fun onDownloadCancelled(downloadId: Long) {
            persistenceManager.updateState(downloadId, Constants.STATE_CANCELLED)
            userListener?.onDownloadCancelled(downloadId)
        }
        
        override fun onNetworkReconnected(downloadId: Long) {
            userListener?.onNetworkReconnected(downloadId)
        }
    }
    
    /**
     * Database-only listener for resumed downloads
     */
    private inner class DatabaseIntegratedListener(
        private val downloadId: Long
    ) : DownloadListener {
        
        override fun onProgressUpdate(downloadId: Long, downloaded: Long, total: Long, percentage: Int) {
            persistenceManager.updateProgress(downloadId, downloaded, total)
        }
        
        override fun onDownloadComplete(downloadId: Long, success: Boolean, filePath: String?) {
            val state = if (success) Constants.STATE_COMPLETED else Constants.STATE_FAILED
            persistenceManager.updateState(downloadId, state)
            activeDownloads.remove(downloadId)
        }
        
        override fun onDownloadError(downloadId: Long, error: String) {
            persistenceManager.updateState(downloadId, Constants.STATE_FAILED, error)
        }
        
        override fun onDownloadStateChanged(downloadId: Long, state: Int) {
            persistenceManager.updateState(downloadId, state)
        }
        
        override fun onDownloadPaused(downloadId: Long) {
            persistenceManager.updateState(downloadId, Constants.STATE_PAUSED)
        }
        
        override fun onDownloadResumed(downloadId: Long) {
            persistenceManager.updateState(downloadId, Constants.STATE_DOWNLOADING)
        }
        
        override fun onDownloadCancelled(downloadId: Long) {
            persistenceManager.updateState(downloadId, Constants.STATE_CANCELLED)
        }
        
        override fun onNetworkReconnected(downloadId: Long) {
            // Handle network reconnection
        }
    }
}

data class DownloadStatistics(
    val totalActive: Int,
    val downloading: Int,
    val paused: Int,
    val waitingForNetwork: Int,
    val completed: Int,
    val failed: Int
)
