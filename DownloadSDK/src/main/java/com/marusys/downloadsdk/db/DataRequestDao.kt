package com.marusys.downloadsdk.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DataRequestDao {

    @Insert(onConflict = REPLACE)
    suspend fun insertDownloadRequest(dataRequest: DataRequestModel)

    @Update
    suspend fun updateDownloadRequest(dataRequest: DataRequestModel)

    @Query("UPDATE download_requests SET state = :state, updatedAt = :updatedAt WHERE downloadId = :downloadId")
    suspend fun updateDownloadState(downloadId: Long, state: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE download_requests SET downloadedBytes = :downloadedBytes, progress = :progress, updatedAt = :updatedAt WHERE downloadId = :downloadId")
    suspend fun updateDownloadProgress(downloadId: Long, downloadedBytes: Long, progress: Float, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE download_requests SET totalBytes = :totalBytes, updatedAt = :updatedAt WHERE downloadId = :downloadId")
    suspend fun updateTotalBytes(downloadId: Long, totalBytes: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE download_requests SET retryCount = :retryCount, updatedAt = :updatedAt WHERE downloadId = :downloadId")
    suspend fun updateRetryCount(downloadId: Long, retryCount: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE download_requests SET errorMessage = :errorMessage, updatedAt = :updatedAt WHERE downloadId = :downloadId")
    suspend fun updateErrorMessage(downloadId: Long, errorMessage: String?, updatedAt: Long = System.currentTimeMillis())

    // Query methods for resume functionality
    @Query("SELECT * FROM download_requests WHERE downloadId = :downloadId")
    suspend fun getDownloadRequest(downloadId: Long): DataRequestModel?

    @Query("SELECT * FROM download_requests WHERE state IN (:states)")
    suspend fun getDownloadsByState(states: List<Int>): List<DataRequestModel>

    @Query("SELECT * FROM download_requests WHERE state = :state")
    suspend fun getDownloadsByState(state: Int): List<DataRequestModel>

    // Get incomplete downloads for resume (downloading, paused, waiting for network)
    @Query("SELECT * FROM download_requests WHERE state IN (1, 2, 6) ORDER BY updatedAt DESC")
    suspend fun getIncompleteDownloads(): List<DataRequestModel>

    // Get failed downloads for retry
    @Query("SELECT * FROM download_requests WHERE state = 4 ORDER BY updatedAt DESC")
    suspend fun getFailedDownloads(): List<DataRequestModel>

    // Get all downloads ordered by most recent
    @Query("SELECT * FROM download_requests ORDER BY updatedAt DESC")
    suspend fun getAllDownloads(): List<DataRequestModel>

    // Live data for UI updates
    @Query("SELECT * FROM download_requests ORDER BY updatedAt DESC")
    fun getAllDownloadsFlow(): Flow<List<DataRequestModel>>

    @Query("SELECT * FROM download_requests WHERE downloadId = :downloadId")
    fun getDownloadRequestFlow(downloadId: Long): Flow<DataRequestModel?>

    // Cleanup methods
    @Query("DELETE FROM download_requests WHERE downloadId = :downloadId")
    suspend fun deleteDownloadRequest(downloadId: Long)

    @Query("DELETE FROM download_requests WHERE state = :state")
    suspend fun deleteDownloadsByState(state: Int)

    // Delete old completed downloads (older than specified timestamp)
    @Query("DELETE FROM download_requests WHERE state = 3 AND updatedAt < :beforeTimestamp")
    suspend fun deleteOldCompletedDownloads(beforeTimestamp: Long)

    // Get download statistics
    @Query("SELECT COUNT(*) FROM download_requests WHERE state = :state")
    suspend fun getDownloadCountByState(state: Int): Int

    @Query("SELECT state, COUNT(*) as count FROM download_requests GROUP BY state")
    suspend fun getDownloadStatistics(): Map<Int, Int>
}