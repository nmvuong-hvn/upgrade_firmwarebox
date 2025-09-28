package com.example.customizedownloadandroid.customizedownloading.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.example.customizedownloadandroid.customizedownloading.DownloadEntity

@Dao
interface DownloadingDao {
    @Insert(onConflict = REPLACE)
    suspend fun insert(entity: DownloadEntity)

    @Query("UPDATE downloadentity SET filePath= :filePath AND tempPath= :tempPath AND totalBytes= :totalBytes WHERE downloadId= :downloadId ")
    suspend fun updateInformationDownload(downloadId : Long , filePath : String , tempPath : String, totalBytes: Long)

    @Query("UPDATE downloadentity SET totalBytes= :totalBytes WHERE downloadId= :downloadId ")
    suspend fun updateTotalBytes(downloadId : Long , totalBytes: Long)

    @Query("UPDATE downloadentity SET downloadedBytes = :downloadedBytes WHERE downloadId= :downloadId ")
    suspend fun updateDownloadedBytes(downloadId: Long, downloadedBytes: Long)

    @Query("UPDATE downloadentity SET status = :status WHERE downloadId= :downloadId ")
    suspend fun updateStatus(downloadId: Long, status: Int)

    @Query("UPDATE downloadentity SET fileName = :fileName WHERE downloadId= :downloadId ")
    suspend fun updateFileName(downloadId: Long, fileName : String)

    @Query("UPDATE downloadentity SET status = :status AND downloadedBytes= :downloadedBytes WHERE downloadId= :downloadId ")
    suspend fun updateStatusWithDownloadedBytes(downloadId: Long, downloadedBytes: Long, status: Int)
    @Query("SELECT * FROM downloadentity WHERE status IN (0,1,2)")
    suspend fun getAllDownloadingList(): List<DownloadEntity>

}