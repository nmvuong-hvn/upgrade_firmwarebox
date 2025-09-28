package com.example.customizedownloadandroid.customizedownloading

import android.os.Build
import android.os.RecoverySystem
import android.util.Log
import com.example.customizedownloadandroid.customizedownloading.HttpClient.Companion.CONTENT_DISPOSITION
import com.example.customizedownloadandroid.customizedownloading.db.DownloadingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File

class MyDownloadTask(val downloadEntity: DownloadEntity) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = HttpClient(downloadEntity)
    private var currentState: DownloadTaskState = DownloadTaskState.None
    private val BUFFER_SIZE = 64 * 1024
    private var startTime: Long = 0
    private val TAG = "MyDownloadTask"
    private var cachedDownloadedByte = downloadEntity.downloadedBytes
    private var isResumeSupported = false
    fun getFileNameFromContentDisposition(header: String): String {
        val filenameStarRegex = Regex("filename\\*=(?:UTF-8'')?([^;]+)")
        filenameStarRegex.find(header)?.let {
            return java.net.URLDecoder.decode(it.groupValues[1], "UTF-8")
        }
        val filenameRegex = Regex("filename=\"?([^\";]+)\"?")
        filenameRegex.find(header)?.let {
            return it.groupValues[1]
        }
        Log.d(TAG, "getFileNameFromContentDisposition: =====> filenameRegex = $filenameRegex ")
        return ""
    }

    fun execute() {
        scope.launch {
            try {
                httpClient.connect()
                if (!httpClient.isSuccessful()) {
                    Log.d(TAG, "execute: ====> error network 1")
                    httpClient.disconnect()
                    return@launch
                }
                val dataFileName = getDataFileNameIfSupported()
                val temp = FileStorage.getTempPath(downloadEntity.dirPath, dataFileName + "_${downloadEntity.downloadId}")
                isResumeSupported = httpClient.isSupportResume()
                if (!isResumeSupported) {
                    val deletedTempFile = File(temp)
                    if (deletedTempFile.delete()) {
                        Log.d(TAG, "execute: ====> deleted file success")
                    } else {
                        Log.d(TAG, "execute ====> deleted file failure")
                    }
                    httpClient.connectNotResume()
                    if (!httpClient.isSuccessful()) {
                        Log.d(TAG, "execute: ====> error network 2")
                        httpClient.disconnect()
                        return@launch
                    }
                }

                currentState = DownloadTaskState.Downloading
                val contentLength = (httpClient.getContentLength() ?: 0) + cachedDownloadedByte
                val inputStream = httpClient.getInputStream()
                // handle file
                val tempPath = File(temp)
                Log.d(TAG, "execute: ====> tempPath = " + tempPath.length())
                val outputStream = FileDownloadRandomAccess.create(tempPath)

                if (cachedDownloadedByte > 0) {
                    outputStream.seek(cachedDownloadedByte)
                }
                Log.d(TAG, "execute: ====> tempfile length = ${tempPath.length()} - cachedDownloadedByte = $cachedDownloadedByte - contentLength = $contentLength")
                DownloadingDatabase.getInstance().getDownloadingDao().updateTotalBytes(downloadEntity.downloadId, contentLength)
                val bufferedInputStream = BufferedInputStream(inputStream, BUFFER_SIZE)
                val buffer = ByteArray(BUFFER_SIZE)
                startTime = System.currentTimeMillis()
                do {
                    try {
                        if (currentState == DownloadTaskState.Paused) {
                            delay(300L)
                            continue
                        }
                        val byteCount = bufferedInputStream.read(buffer, 0, BUFFER_SIZE)
                        if (byteCount == -1) {
                            break
                        }
                        outputStream.write(buffer, 0, byteCount)
                        cachedDownloadedByte += byteCount
                        val progress = (cachedDownloadedByte * 100.0f / contentLength).toDouble()
                        DownloadingDatabase.getInstance().getDownloadingDao().updateDownloadedBytes(downloadEntity.downloadId, cachedDownloadedByte)
                        Log.d(TAG, "startDownloading: =====> downloadedBytes = $cachedDownloadedByte - progress = ${progress}")
                        val endTime = System.currentTimeMillis()
                        val totalTimeSeconds = (endTime - startTime) / 1000.0
                        val averageSpeed = (cachedDownloadedByte / totalTimeSeconds / (1024 * 1024))
//                        Log.d(TAG, "Download completed in ${"%.1f".format(totalTimeSeconds)} seconds")
//                        Log.d(TAG, "Average download speed: ${"%.2f".format(averageSpeed)} MB/s")

                        if (cachedDownloadedByte == contentLength) {
                            currentState = DownloadTaskState.Completed
                            break
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "execute: ====> e = ${e.message} isResumeSupported = $isResumeSupported")
                        outputStream.flushAndSync()
                        currentState = DownloadTaskState.Paused
                        DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(downloadEntity.downloadId, DownloadEntity.STATUS_PAUSED)
                        if (!isResumeSupported) {
                            Log.d(TAG, "execute: =====> update - isActive = $isActive - id = ${downloadEntity.downloadId}")
                            DownloadingDatabase.getInstance().getDownloadingDao().updateDownloadedBytes(downloadEntity.downloadId, 0)
                        }
                        break
                    }
                } while (true)
                outputStream.close()
                Log.d(TAG, "execute: ====> temp file length = ${tempPath.length()}")
                bufferedInputStream.close()
                httpClient.disconnect()
                if (currentState == DownloadTaskState.Completed) {
                    DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(downloadEntity.downloadId, DownloadEntity.STATUS_COMPLETED)
                    var newPath = FileStorage.getPath(downloadEntity.dirPath, dataFileName)
                    Log.d(TAG, "execute: ===> temp = $temp - newPath = $newPath")
                    FileStorage.renameFileName(temp, newPath)
                    DownloadingDatabase.getInstance().getDownloadingDao().updateInformationDownload(
                        downloadEntity.downloadId,
                        newPath,
                        "",
                        totalBytes = contentLength
                    )
                    newPath = zipFileIfNecessary(newPath, dataFileName)
                    val fileZipCheck = File(newPath)
//                    RecoverySystem.verifyPackage(fileZipCheck,object : RecoverySystem.ProgressListener{
//                        override fun onProgress(p0: Int) {
//                            Log.d(TAG, "onProgress: =====> p0 = $p0")
//                        }
//
//                    },null)
                    val sizeInBytes = fileZipCheck.length()
                    val md5String = FileStorage.getFileMD5(fileZipCheck)
                    Log.d(TAG, "execute: ====> md5String = $md5String")
                    Log.d(TAG, "resumeDownloading: =====> File size: $sizeInBytes bytes")

                }
            } catch (e: Exception) {
                Log.d(TAG, "execute: ====> error1 = ${e.message}")
                scope.launch {
                    Log.d(TAG, "execute: =====> isResumeSupported = $isResumeSupported")
                    if (!isResumeSupported) {
                        DownloadingDatabase.getInstance().getDownloadingDao().updateStatusWithDownloadedBytes(downloadEntity.downloadId, 0, if (currentState == DownloadTaskState.Completed) DownloadEntity.STATUS_COMPLETED else  DownloadEntity.STATUS_PAUSED)
                    } else {
                        DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(downloadEntity.downloadId, if (currentState == DownloadTaskState.Completed) DownloadEntity.STATUS_COMPLETED else  DownloadEntity.STATUS_PAUSED)
                    }
                }
            }
        }
    }

    private suspend fun getDataFileNameIfSupported(): String {
        val data = httpClient.getHeaders(CONTENT_DISPOSITION)
        Log.d(TAG, "execute: =====> CONTENT_DISPOSITION = $data")
        var dataFileName = downloadEntity.fileName
        if (data != null && data.isNotEmpty() && data.contains("filename")) {
            Log.d(TAG, "execute: ======> VAO getNameFile")
            val dataRes = getFileNameFromContentDisposition(data)
            Log.d(TAG, "execute: ====> fileName = $dataRes")
            if (dataRes.isNotEmpty()) {
                dataFileName = dataRes
                DownloadingDatabase.getInstance().getDownloadingDao()
                    .updateFileName(downloadEntity.downloadId, dataFileName)
            }
        }
        return dataFileName
    }
    // Necessary

    private fun zipFileIfNecessary(newPath: String, dataFileName: String, isZip : Boolean = false): String {
        var newPath1 = newPath
        val fileCheck = File(newPath1)
        if (fileCheck.exists() && isZip) {
            Log.d(TAG, "zipFileIfNeceesary: ====> zip file")
            if (!dataFileName.contains("zip")) {
                FileStorage.createZipFile(
                    listOf(fileCheck),
                    File(downloadEntity.dirPath, dataFileName.plus(".zip"))
                )
            }
            newPath1 = FileStorage.getPath(downloadEntity.dirPath, dataFileName.plus(".zip"))
        }
        val fileNewCheck = File(newPath1)
        Log.d(TAG, "zipFileIfNecessary: ====> fileNewCheck = ${fileNewCheck.length()}")
        return newPath1
    }

    fun pause() {
        currentState = DownloadTaskState.Paused
        scope.launch {
            DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(
                downloadEntity.downloadId,
                DownloadEntity.STATUS_PAUSED
            )
        }
    }

    fun resume() {
        currentState = DownloadTaskState.Downloading
        scope.launch {
            DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(
                downloadEntity.downloadId,
                DownloadEntity.STATUS_DOWNLOADING
            )
        }
    }

    fun cancel() {
        currentState = DownloadTaskState.None
        httpClient.disconnect()
    }

    fun isResumeSupported(): Boolean {
        return httpClient.isSupportResume()
    }

    fun close() {
        currentState = DownloadTaskState.None
        scope.cancel()
    }
}

sealed class DownloadTaskState {
    data object None : DownloadTaskState()
    data object Pending : DownloadTaskState()
    data object Downloading : DownloadTaskState()
    data object Paused : DownloadTaskState()
    data object Completed : DownloadTaskState()
}