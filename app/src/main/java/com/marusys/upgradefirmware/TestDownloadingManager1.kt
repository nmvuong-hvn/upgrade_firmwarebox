package com.marusys.upgradefirmware

import com.marusys.upgradefirmware.download_firmware.DownloadDatabase
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TestDownloadingManager1(context: Context) : NetworkConnectionManager.NetworkStateListener{
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val connectionNetworkConnectionManager = NetworkConnectionManager(context, this)
    private val TAG = "DownloadingManager"
    private val scope = CoroutineScope(Dispatchers.IO  + SupervisorJob())
    private val downloadingMap = ConcurrentHashMap<String, DownloadEntity>()
    private var callback : DownloadingCallback ?= null

    interface DownloadingCallback {
        fun onProgress(data : String, downloadedBytes: Long, totalBytes: Long, downloadId: Long)
        fun onCurrentState(data: String, downloadId: Long)
    }

    fun setCallback(data: DownloadingCallback){
        callback = data
    }
    companion object {
        @SuppressLint("StaticFieldLeak")
        var downloadingManager : TestDownloadingManager1? = null
        fun create(context: Context) {
            downloadingManager = TestDownloadingManager1(context)
        }
        fun getInstance() : TestDownloadingManager1 {
            if (downloadingManager == null) {
                throw IllegalStateException("DownloadingManager is not initialized, call create(context) first")
            }
            return downloadingManager!!
        }
    }
    fun init() {
        connectionNetworkConnectionManager.init()
    }
    fun downloadFile(url: String, fileName: String): Long {
        val uri = url.toUri()
        val request = DownloadManager.Request(uri)
        request.setTitle(fileName)
        request.setDescription("Downloading $fileName")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setAllowedOverMetered(true)
        request.setAllowedOverRoaming(true)
        val downloadId = downloadManager.enqueue(request)
        scope.launch {
            val downloadEntity = DownloadEntity(
                downloadId = downloadId,
                url = url,
                fileName = fileName,
                filePath = "",
                downloadedBytes = 0L,
                status = DownloadEntity.STATUS_PENDING
            )
            downloadingMap[url] = downloadEntity
            Log.d(TAG, "downloadFile: ====> insertDownloaded")
            DownloadDatabase.getInstance().downloadDao().insertDownload(downloadEntity)
        }
        return downloadId
    }

    fun cancelDownloading(downloadId: Long){
        downloadManager.remove(downloadId)
    }

    override fun onNetworkAvailable() {
        scope.launch {
            val downloadingList = DownloadDatabase.getInstance().downloadDao().getDownloadsList()
            Log.d(TAG, "onNetworkAvailable: ====> getDownloadingList ${downloadingList.size}")
            downloadingList.forEach {
                Log.d(TAG, "onNetworkAvailable: ====> restarting download ${it.downloadId}, ${it.url}, ${it.fileName}")
                val entityDownloading = downloadingMap[it.url]
                if (entityDownloading == null) {
                    val newDownloadId = downloadFile(it.url, it.fileName)
                    Log.d(TAG, "onNetworkAvailable: ====> newDownloadId = $newDownloadId")
                    val updatedEntity = it.copy(
                        downloadId = newDownloadId,
                        status = DownloadEntity.STATUS_PENDING
                    )
                    DownloadDatabase.getInstance().downloadDao().updateDownload(updatedEntity)
                }
            }
        }
    }

    override fun onNetworkLost() {
        scope.launch {
            val downloadingList = DownloadDatabase.getInstance().downloadDao().getDownloadsList()
            Log.d(TAG, "onNetworkLost: ====> getDownloadingList ${downloadingList.size}")
            downloadingList.forEach {
                Log.d(TAG, "onNetworkLost: ====> cancelling download ${it.downloadId}, ${it.url}, ${it.fileName}")
                cancelDownloading(it.downloadId)
                val updatedEntity = it.copy(
                    status = DownloadEntity.STATUS_FAILED
                )
                downloadingMap.remove(it.url)
                DownloadDatabase.getInstance().downloadDao().updateDownload(updatedEntity)
            }
        }
    }

    @SuppressLint("Range", "DefaultLocale")
    fun monitorDownloading(){
        scope.launch {
            while(isActive){
                val downloadingList = DownloadDatabase.getInstance().downloadDao().getDownloadsList()
                Log.d(TAG, "monitorDownloading: ====> getDownloadingList ${downloadingList.size}")
                downloadingList.forEach {
                    val query = DownloadManager.Query().setFilterById(it.downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        val downloadedBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val tmpProcess = (1.0f * downloadedBytes / totalBytes).toDouble()
                        val progress = tmpProcess * 100.0f
                        Log.d(TAG, "monitorDownloading: ====> downloadId: ${it.downloadId}, status: $status, percentage = $progress downloadedBytes: $downloadedBytes, totalBytes: $totalBytes")
                        callback?.onProgress(String.format("%.2f", progress),downloadedBytes,totalBytes,it.downloadId)
                        val updatedEntity = it.copy(
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            status = when (status) {
                                DownloadManager.STATUS_PENDING -> DownloadEntity.STATUS_PENDING
                                DownloadManager.STATUS_RUNNING -> DownloadEntity.STATUS_DOWNLOADING
                                DownloadManager.STATUS_PAUSED -> DownloadEntity.STATUS_PAUSED
                                DownloadManager.STATUS_SUCCESSFUL -> DownloadEntity.STATUS_COMPLETED
                                DownloadManager.STATUS_FAILED -> DownloadEntity.STATUS_FAILED
                                else -> it.status
                            }
                        )
                        DownloadDatabase.getInstance().downloadDao().updateDownload(updatedEntity)
                    }
                    cursor?.close()
                }
                delay(TimeUnit.SECONDS.toMillis(2))
            }
        }
    }
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val downloadId: Long,
    val url: String,
    val fileName: String,
    val filePath: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: Int = STATUS_PENDING, // 0: pending, 1: downloading, 2: paused, 3: completed, 4: failed, 5: cancelled
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_FAILED = 4
    }
}