package com.marusys.upgradefirmware

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.util.Log
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marusys.upgradefirmware.database.DownloadDao
import com.marusys.upgradefirmware.database.DownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Time
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TestDownloadingManager(val context: Context, val downloadDao: DownloadDao): NetworkMonitorManager.NetworkStateListener {
    private val TAG = "DownloadingManager"
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    var currentState = 0

    val networkMonitorManager = NetworkMonitorManager(context, this)
    private val activesDownloadingMap = ConcurrentHashMap<String, DownloadEntity>()
    var cachedUrl = ""
    val scope = CoroutineScope(Dispatchers.IO)
    companion object {
        const val BUFFER_SIZE = 64 * 1024
        @SuppressLint("StaticFieldLeak")
        var downloadingManager : TestDownloadingManager? = null
        fun create(context: Context , downloadDao: DownloadDao) {
            downloadingManager = TestDownloadingManager(context,downloadDao)
        }
        fun getInstance() : TestDownloadingManager {
            if (downloadingManager == null) {
                throw IllegalStateException("DownloadingManager is not initialized, call create(context) first")
            }
            return downloadingManager!!
        }
    }

    fun syncDataFromDb(){
        networkMonitorManager.init()
        runBlocking {
            downloadDao.getIncompleteDownloads().forEach {
                activesDownloadingMap[it.url] = it
            }
        }
    }

    var connection : HttpURLConnection ?= null
    var cachedDownloadedBytes = 0L
    var isFinished : Boolean = false
    fun getPath(dirPath: String?, fileName: String?): String {
        return dirPath + File.separator + fileName
    }
    fun getRootDirPath(context: Context): String {
        return if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            val file: File = ContextCompat.getExternalFilesDirs(
                context.applicationContext,
                null
            )[0]!!
            file.absolutePath
        } else {
            context.applicationContext.filesDir.absolutePath
        }
    }
    fun getTempPath(dirPath: String?, fileName: String?): String {
        return getPath(dirPath, fileName) + ".temp"
    }

    @Throws(IOException::class)
    fun renameFileName(oldPath: String, newPath: String) {
        val oldFile = File(oldPath)
        try {
            val newFile = File(newPath)
            if (newFile.exists()) {
                if (!newFile.delete()) {
                    throw IOException("Deletion Failed")
                }
            }
            if (!oldFile.renameTo(newFile)) {
                throw IOException("Rename Failed")
            }
        } finally {
            if (oldFile.exists()) {
                oldFile.delete()
            }
        }
    }


    fun startDownloading(url: String){
        scope.launch {
            val downloadEntity = activesDownloadingMap[url]
            if (downloadEntity == null){
                val newDownloadEntity = DownloadEntity(
                    downloadId = System.currentTimeMillis(),
                    url = url,
                    fileName = URLUtil.guessFileName(url, null, null),
                    filePath = "",
                    tempPath = "",
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    status = 1,
                    expectedMD5 = null
                )
                downloadDao.insertDownload(newDownloadEntity)
                activesDownloadingMap[url] = newDownloadEntity
            }else {
                if (downloadEntity.status == 1) {
                    Log.d(TAG, "startDownloading: ====> id = ${downloadEntity.downloadId} is downloading....")
                    return@launch
                }else if (downloadEntity.status == 2 || downloadEntity.status == 4) {
                    resumeDownloading(downloadEntity)
                    return@launch
                }
            }

            Log.d(TAG, "startDownloading: --------> first")
            val downloadEntityData = activesDownloadingMap[url]
            if (downloadEntityData == null){
                return@launch
            }
            cachedDownloadedBytes = downloadEntityData.downloadedBytes
            connection = URL(url).openConnection() as HttpURLConnection?
            connection!!.connectTimeout = TimeUnit.SECONDS.toMillis(60).toInt()
            connection!!.connect()

            val contentLength = connection?.contentLengthLong ?: 0L
            val inputStream = connection?.inputStream
            // Sử dụng BufferedInputStream cho tốc độ cao hơn
            val bufferedInputStream = BufferedInputStream(inputStream, BUFFER_SIZE)
            val buff = ByteArray(BUFFER_SIZE)

            val dirPath = getRootDirPath(context)
            val fileName = URLUtil.guessFileName(url, null, null)
            val tempPath = getTempPath(dirPath , fileName)
            val file = File(tempPath)
//            downloadDao.updateTotalBytes(downloadEntityData.downloadId, contentLength)
            val outputStream = FileDownloadRandomAccessFile.create(file)
            do {
                if(currentState == 3) {
                    Log.d(TAG, "startDownloading: =====> resume()")
                    delay(100L)
                }else {
                    try {
                        val byteCount: Int = bufferedInputStream.read(buff, 0, BUFFER_SIZE)
                        if (byteCount == -1) {
                            break
                        }

                        outputStream.write(buff, 0, byteCount)
                        cachedDownloadedBytes += byteCount
                        val progress = (cachedDownloadedBytes * 100.0f / contentLength).toDouble()
                        Log.d(TAG, "startDownloading: =====> downloadedBytes = $cachedDownloadedBytes - progress = ${progress.roundTo(2)}")
                        if (cachedDownloadedBytes == contentLength) {
                            Log.d(TAG, "startDownloading: =====> finished")
                            isFinished = true
                            break;
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Xử lý lỗi (ví dụ: kết nối mạng bị gián đoạn)
                        outputStream.flushAndSync()
                        break
                    }
                }
            } while (true)

            outputStream.close()
            bufferedInputStream.close()
            connection?.disconnect()
            Log.d(TAG, "startDownloading: ====> isFinished = $isFinished")
            if (isFinished) {
                val path: String = getPath(dirPath, fileName)
                renameFileName(tempPath, path)
                val fileNew = File(path)
                if (fileNew.exists()) {
                    val sizeInBytes = fileNew.length()
                    Log.d(TAG, "resumeDownloading: =====> File size: $sizeInBytes bytes")
                }
            }
        }

    }

    fun pause(){
        currentState = 3
    }
    fun resume(){
        currentState = 1
    }
    fun resumeDownloading(downloadEntity: DownloadEntity){
        scope.launch {
            connection = URL(downloadEntity.url).openConnection() as HttpURLConnection?
            connection!!.connectTimeout = TimeUnit.SECONDS.toMillis(60).toInt()

            val range = java.lang.String.format(
                Locale.ENGLISH,
                "bytes=%d-", downloadEntity.downloadedBytes
            )
            Log.d(TAG, "resumeDownloading: ====> range = $range")
            connection!!.addRequestProperty("Range", range)
            connection!!.connect()
            val dirPath = getRootDirPath(context)
            val fileName = URLUtil.guessFileName(downloadEntity.url, null, null)
            val contentLength = connection!!.contentLength + downloadEntity.downloadedBytes
            Log.d(TAG, "resumeDownloading: ====> contentLength = $contentLength")
            val tempPath = getTempPath(dirPath, fileName)
            val file = File(tempPath)
            val outputStream = FileDownloadRandomAccessFile.create(file)
            val inputStream = BufferedInputStream(connection!!.inputStream, BUFFER_SIZE)
            val buff = ByteArray(BUFFER_SIZE)
            if (downloadEntity.downloadedBytes > 0) {
                outputStream.seek(downloadEntity.downloadedBytes)
            }
            var cachedDownloadedBytes = downloadEntity.downloadedBytes
            Log.d(TAG, "resumeDownloading: -----> cachedDownloadedBytes = ${downloadEntity.downloadedBytes}")
            do {
                val byteCount: Int = inputStream.read(buff, 0, BUFFER_SIZE)
                Log.d(TAG, "resumeDownloading: =====> byteCount = $byteCount")
                if (byteCount == -1) {
                    break
                }
                outputStream.write(buff, 0, byteCount)
                cachedDownloadedBytes += byteCount
                val progress = (cachedDownloadedBytes * 100.0f / contentLength).toDouble()
                Log.d(TAG, "resumeDownloading: =====> downloadedBytes = $cachedDownloadedBytes - progress = ${progress.roundTo(2)}")
//                downloadDao.updateProgress(downloadEntity.downloadId, cachedDownloadedBytes, DownloadEntity.STATUS_DOWNLOADING,System.currentTimeMillis())
                if (cachedDownloadedBytes == contentLength) {
                    isFinished = true
                    break;
                }

            } while (true)
            outputStream.close()
            inputStream.close()
            connection?.disconnect()
            Log.d(TAG, "resumeDownloading: =====> isFinished = $isFinished")
            if (isFinished) {
                val currentTime = System.currentTimeMillis()
                Log.d(TAG, "resumeDownloading: =====> finished = $currentTime")
//                downloadDao.updateState(downloadEntity.downloadId, DownloadEntity.STATUS_COMPLETED, currentTime)
                val path: String = getPath(dirPath, fileName)
                renameFileName(tempPath, path)
                val file = File(path)
                if (file.exists()) {
                    val sizeInBytes = file.length()
                    Log.d(TAG, "resumeDownloading: =====> File size: $sizeInBytes bytes")
                }
            }
        }
    }

    override fun onNetworkAvailable() {
        if (cachedUrl.isNotEmpty()) {
            scope.launch {
                delay(TimeUnit.SECONDS.toMillis(6))
            }
        }
    }

    override fun onNetworkLost() {
        pause()
    }

}
fun Double.roundTo(n: Int): String {
    return "%.${n}f".format(this)
}
@Entity("firmware_table")
data class FirmwareModel(
    @PrimaryKey(autoGenerate = true)
    val id : Long = -1L,
    val downloadId : Long = -1L,
    val fileSize : Long = 0L,
    val tittle : String = "",
    val progress : Int = 0,
    val status : Int = -1 ,
    val isPaused : Boolean =false,
    val filePath : String = ""
)