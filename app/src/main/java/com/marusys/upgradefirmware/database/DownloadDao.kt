package com.marusys.upgradefirmware.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)
//
//    @Update
//    suspend fun updateDownload(download: DownloadEntity)
//
//    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes WHERE downloadId = :downloadId")
//    suspend fun updateProgress(downloadId: Long, downloadedBytes: Long, updatedAt: Long = System.currentTimeMillis())
//
//    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes AND status =:status WHERE downloadId = :downloadId")
//    suspend fun updateProgress(downloadId: Long, downloadedBytes: Long, status: Int, updatedAt: Long = System.currentTimeMillis())
//
//    @Query("UPDATE downloads SET status = :status  WHERE downloadId = :downloadId")
//    suspend fun updateState(downloadId: Long, status : Int, updatedAt: Long = System.currentTimeMillis())
//
//    @Query("UPDATE downloads SET totalBytes = :totalBytes WHERE downloadId = :downloadId")
//    suspend fun updateTotalBytes(downloadId: Long, totalBytes: Long)
//
//    @Query("UPDATE downloads SET totalBytes = :totalBytes WHERE downloadId = :downloadId")
//    suspend fun updateSomeInformation(downloadId: Long ,)
//
//    @Query("SELECT * FROM downloads WHERE downloadId = :downloadId")
//    suspend fun getDownloadById(downloadId: String): DownloadEntity?
//
//    @Query("SELECT * FROM downloads WHERE status IN (0, 1, 2)")
//    suspend fun getIncompleteDownloads(): List<DownloadEntity>
//
//    @Query("SELECT * FROM downloads WHERE downloadId = :downloadId")
//    fun getDownloadFlow(downloadId: String): Flow<DownloadEntity?>
//
//    @Delete
//    suspend fun deleteDownload(download: DownloadEntity)
//
//    @Query("DELETE FROM downloads WHERE downloadId = :downloadId")
//    suspend fun deleteDownloadById(downloadId: String)
//
//    @Query("DELETE FROM downloads WHERE status = 3")
//    suspend fun deleteOldCompletedDownloads(beforeTime: Long)
}
