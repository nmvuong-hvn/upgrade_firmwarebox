package com.marusys.downloadsdk.db

import android.content.Context
import android.util.Log
import com.marusys.downloadsdk.Constants
import com.marusys.downloadsdk.model.DownloadRequestFileModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages persistence of download requests to Room database
 * Enables resume functionality when app is reopened
 */
class DownloadPersistenceManager private constructor() {
    
    private val TAG = "DownloadPersistence"
    private lateinit var dao: DataRequestDao
    private val persistenceScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        @Volatile
        private var INSTANCE: DownloadPersistenceManager? = null
        
        fun getInstance(): DownloadPersistenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadPersistenceManager().also { INSTANCE = it }
            }
        }
    }
    
    fun initialize(context: Context) {
        DatabaseCache.create(context)
        dao = DatabaseCache.getInstance().getDataRequestDao()
        Log.d(TAG, "DownloadPersistenceManager initialized")
    }
    
    // Save download request to database
    suspend fun saveDownloadRequest(request: DownloadRequestFileModel, filePath: String) {
        withContext(Dispatchers.IO) {
            val dataModel = request.toDataRequestEntity().copy(
                filePath = filePath,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertDownloadRequest(dataModel)
            Log.d(TAG, "Saved download request: ${request.fileName} (ID: ${request.downloadId})")
        }
    }
    
    // Update download progress in database
    fun updateProgress(downloadId: Long, downloadedBytes: Long, totalBytes: Long) {
        persistenceScope.launch {
            val progress = if (totalBytes > 0) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()) * 100
            } else 0f
            
            dao.updateDownloadProgress(downloadId, downloadedBytes, progress)
            
            if (totalBytes > 0) {
                dao.updateTotalBytes(downloadId, totalBytes)
            }
        }
    }
    
    // Update download state in database
    fun updateState(downloadId: Long, state: Int, errorMessage: String? = null) {
        persistenceScope.launch {
            dao.updateDownloadState(downloadId, state)
            errorMessage?.let { 
                dao.updateErrorMessage(downloadId, it)
            }
            Log.d(TAG, "Updated state for download $downloadId to ${getStateString(state)}")
        }
    }
    
    // Update retry count
    fun updateRetryCount(downloadId: Long, retryCount: Int) {
        persistenceScope.launch {
            dao.updateRetryCount(downloadId, retryCount)
        }
    }
    
    // Get incomplete downloads for resume when app opens
    suspend fun getIncompleteDownloads(): List<DownloadRequestFileModel> {
        return withContext(Dispatchers.IO) {
            val incompleteDownloads = dao.getIncompleteDownloads()
            Log.d(TAG, "Found ${incompleteDownloads.size} incomplete downloads to resume")
            incompleteDownloads.map { it.toDownloadRequestFileModel() }
        }
    }
    
    // Get failed downloads for potential retry
    suspend fun getFailedDownloads(): List<DownloadRequestFileModel> {
        return withContext(Dispatchers.IO) {
            val failedDownloads = dao.getFailedDownloads()
            Log.d(TAG, "Found ${failedDownloads.size} failed downloads")
            failedDownloads.map { it.toDownloadRequestFileModel() }
        }
    }
    
    // Get specific download request
    suspend fun getDownloadRequest(downloadId: Long): DownloadRequestFileModel? {
        return withContext(Dispatchers.IO) {
            dao.getDownloadRequest(downloadId)?.toDownloadRequestFileModel()
        }
    }
    
    // Get all downloads for UI display
    suspend fun getAllDownloads(): List<DownloadRequestFileModel> {
        return withContext(Dispatchers.IO) {
            dao.getAllDownloads().map { it.toDownloadRequestFileModel() }
        }
    }
    
    // Flow for real-time UI updates
    fun getAllDownloadsFlow(): Flow<List<DownloadRequestFileModel>> {
        return dao.getAllDownloadsFlow().map { list ->
            list.map { it.toDownloadRequestFileModel() }
        }
    }
    
    // Flow for specific download
    fun getDownloadFlow(downloadId: Long): Flow<DownloadRequestFileModel?> {
        return dao.getDownloadRequestFlow(downloadId).map { 
            it?.toDownloadRequestFileModel() 
        }
    }
    
    // Check if file exists and validate download integrity
    suspend fun validateDownloadFile(downloadId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val request = dao.getDownloadRequest(downloadId) ?: return@withContext false
            val file = File(request.filePath)
            
            if (!file.exists()) {
                Log.w(TAG, "Download file not found: ${request.filePath}")
                return@withContext false
            }
            
            val actualFileSize = file.length()
            val expectedSize = request.downloadedBytes
            
            if (actualFileSize != expectedSize) {
                Log.w(TAG, "File size mismatch. Expected: $expectedSize, Actual: $actualFileSize")
                // Update database with actual file size
                dao.updateDownloadProgress(downloadId, actualFileSize,
                    if (request.totalBytes > 0) (actualFileSize.toFloat() / request.totalBytes) * 100 else 0f)
                return@withContext false
            }

            Log.d(TAG, "Download file validated: ${request.fileName}")
            true
        }
    }

    // Delete download from database
    suspend fun deleteDownload(downloadId: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteDownloadRequest(downloadId)
            Log.d(TAG, "Deleted download request: $downloadId")
        }
    }

    // Cleanup old completed downloads (older than 30 days)
    suspend fun cleanupOldDownloads(daysOld: Int = 30) {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
            dao.deleteOldCompletedDownloads(cutoffTime)
            Log.d(TAG, "Cleaned up downloads older than $daysOld days")
        }
    }

    // Get download statistics from database
    suspend fun getDownloadStatistics(): Map<Int, Int> {
        return withContext(Dispatchers.IO) {
            dao.getDownloadStatistics()
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

// Extension functions for conversion between models
fun DataRequestModel.toDownloadRequestFileModel(): DownloadRequestFileModel {
    return DownloadRequestFileModel(
        id = downloadId,
        downloadId = downloadId,
        fileName = fileName,
        url = url,
        progress = progress,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        state = state
    )
}

fun DownloadRequestFileModel.toDataRequestEntity(): DataRequestModel {
    return DataRequestModel(
        downloadId = downloadId,
        fileName = fileName,
        url = url,
        filePath = "", // Will be set when saving
        progress = progress,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        state = state,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        retryCount = 0
    )
}
