package com.marusys.tv.mams.db.download_firmware

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.downloadmanagerandroid.DownloadEntity
@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes WHERE downloadId = :downloadId")
    suspend fun updateProgress(downloadId: Long, downloadedBytes: Long)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes AND status =:status WHERE downloadId = :downloadId")
    suspend fun updateProgress(downloadId: Long, downloadedBytes: Long, status: Int)

    @Query("UPDATE downloads SET status = :status  WHERE downloadId = :downloadId")
    suspend fun updateState(downloadId: Long, status : Int)

    @Query("UPDATE downloads SET totalBytes = :totalBytes WHERE downloadId = :downloadId")
    suspend fun updateTotalBytes(downloadId: Long, totalBytes: Long)

    @Query("SELECT * FROM downloads WHERE status != 3")
    suspend fun getDownloadsList(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE downloadId = :downloadId ")
    suspend fun deletedById(downloadId : Long)
}