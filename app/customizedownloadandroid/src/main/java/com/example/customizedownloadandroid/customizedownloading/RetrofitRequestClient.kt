package com.example.customizedownloadandroid.customizedownloading

import android.os.RecoverySystem
import android.util.Log
import androidx.compose.ui.text.intl.Locale
import com.example.customizedownloadandroid.customizedownloading.HttpClient.Companion.ACCEPT_RANGES
import com.example.customizedownloadandroid.customizedownloading.HttpClient.Companion.CONTENT_DISPOSITION
import com.example.customizedownloadandroid.customizedownloading.HttpClient.Companion.RANGE
import com.example.customizedownloadandroid.customizedownloading.db.DownloadingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import kotlin.let

class RetrofitRequestClient(val entityDownload: DownloadEntity) : DownloadingTask {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentState: DownloadTaskState = DownloadTaskState.None
    private val BUFFER_SIZE = 64 * 1024
    private var startTime: Long = 0
    private val TAG = "MyDownloadTask"
    private var cachedDownloadedByte = entityDownload.downloadedBytes
    private var isResumeSupported = false
    private val headerMap = mutableMapOf<String, String>()


    fun getFileNameFromContentDisposition(header: String): String {
        // Check for RFC 5987 format (filename*)
        val filenameStarRegex = Regex("filename\\*=(?:UTF-8'')?([^;]+)")
        filenameStarRegex.find(header)
            ?.let { return java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") }
        // Check for normal filename=
        val filenameRegex = Regex("filename=\"?([^\";]+)\"?")
        filenameRegex.find(header)?.let { return it.groupValues[1] }
        Log.d(TAG, "getFileNameFromContentDisposition: =====> filenameRegex = $filenameRegex ")
        return ""
    }
    override fun execute() {
        scope.launch {
            try {
                if (cachedDownloadedByte > 0){
                    headerMap[RANGE] = String.format(java.util.Locale.ENGLISH,"bytes=%d-", cachedDownloadedByte)
                    Log.d(TAG, "execute: =====> cachedDownloadedByte = $cachedDownloadedByte")
                }
                var responseServer = RetrofitInstance.instanceService.getUrl(entityDownload.url,headerMap)
                Log.d(TAG, "execute: ======> VAO = $responseServer")
                if (responseServer?.isSuccessful == false || responseServer == null){
                    Log.d(TAG, "execute: =====> error network ====> $responseServer")
                    return@launch
                }
                // isSupported
                val acceptRanges = responseServer.headers()[ACCEPT_RANGES]
                val isAcceptRanges = acceptRanges != null && acceptRanges.isNotEmpty() && acceptRanges.contains("bytes")
                var dataFileName = entityDownload.fileName
                val contentDis = responseServer.headers()[CONTENT_DISPOSITION]
                if (contentDis != null && contentDis.isNotEmpty() && contentDis.contains("filename")){
                    val dataRes = getFileNameFromContentDisposition(contentDis)
                    Log.d(TAG, "execute: ====> fileName = $dataRes")
                    if (dataRes.isNotEmpty()) {
                        dataFileName = dataRes
                    }
                }
                val tempPath = FileStorage.getTempPath(entityDownload.dirPath, dataFileName)
                isResumeSupported = responseServer.code() == HttpURLConnection.HTTP_PARTIAL || isAcceptRanges
                Log.d(TAG, "execute: ====> isResumeSupported = $isResumeSupported")
                if(!isResumeSupported){
                    val deletedFile = File(tempPath)
                    if (deletedFile.delete()){
                        Log.d(TAG, "execute: ====> deleted file successfully")
                    }else {
                        Log.d(TAG, "execute: ====> deleted file failure")
                    }
                    headerMap.clear()
                    responseServer = RetrofitInstance.instanceService.getUrl(entityDownload.url, headerMap)
                    if (responseServer?.isSuccessful == false || responseServer == null){
                        Log.d(TAG, "execute: =====> error network ====> $responseServer")
                        return@launch
                    }
                }
                val totalBytes =( responseServer.body()?.contentLength() ?: 0) + cachedDownloadedByte
                DownloadingDatabase.getInstance().getDownloadingDao().updateTotalBytes(entityDownload.downloadId,totalBytes)
                responseServer.body()?.byteStream()?.use { inputStream ->

                    currentState = DownloadTaskState.Downloading
                    DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(entityDownload.downloadId, DownloadEntity.STATUS_DOWNLOADING)

                    val file = File(tempPath)
                    val outputStream = FileDownloadRandomAccess.create(file = file)
                    val bufferedInputStream = BufferedInputStream(inputStream, BUFFER_SIZE)
                    val buffer = ByteArray(BUFFER_SIZE)

                    do {
                        try {

                            if (currentState == DownloadTaskState.Paused){
                                delay(300L)
                                continue
                            }
                            val byteCount = bufferedInputStream.read(buffer,0,BUFFER_SIZE)
                            if (byteCount == -1 ){
                                if (cachedDownloadedByte == totalBytes){
                                    Log.d(TAG, "execute: =====> Completed 1")
                                    currentState = DownloadTaskState.Completed
                                }
                                break
                            }
                            outputStream.write(buffer, 0, byteCount)
                            cachedDownloadedByte += byteCount
                            val progress = (cachedDownloadedByte * 100.0f / totalBytes)
                            DownloadingDatabase.getInstance().getDownloadingDao().updateDownloadedBytes(entityDownload.downloadId, cachedDownloadedByte)
                            Log.d(TAG, "startDownloading: =====> downloadedBytes = $cachedDownloadedByte - progress = ${progress}")

                            if (cachedDownloadedByte == totalBytes){
                                Log.d(TAG, "execute: =====> Completed")
                                currentState = DownloadTaskState.Completed
                                break
                            }

                        }catch (e : Exception){
                            currentState == DownloadTaskState.Paused
                            DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(entityDownload.downloadId, DownloadEntity.STATUS_PAUSED)
                            if (!isResumeSupported) {
                                Log.d(TAG, "execute: =====> update - isActive = $isActive - id = ${entityDownload.downloadId}")
                                DownloadingDatabase.getInstance().getDownloadingDao().updateDownloadedBytes(entityDownload.downloadId, 0)
                            }
                            break
                        }
                    }while (true)
                    outputStream.flushAndSync()
                    outputStream.close()
                    inputStream.close()
                    if (currentState == DownloadTaskState.Completed) {
                        DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(entityDownload.downloadId, DownloadEntity.STATUS_COMPLETED)
                        var newPath = FileStorage.getPath(entityDownload.dirPath, dataFileName)
                        Log.d(TAG, "execute: ===> temp = $tempPath - newPath = $newPath")
                        FileStorage.renameFileName(tempPath, newPath)
                        DownloadingDatabase.getInstance().getDownloadingDao().updateInformationDownload(
                            entityDownload.downloadId,
                            newPath,
                            "",
                            totalBytes = responseServer.body()!!.contentLength()
                        )
                        newPath = zipFileIfNecessary(newPath, dataFileName)
                        val fileZipCheck = File(newPath)
                        RecoverySystem.verifyPackage(fileZipCheck,object : RecoverySystem.ProgressListener{
                            override fun onProgress(p0: Int) {
                                Log.d(TAG, "onProgress: =====> p0 = $p0")
                            }

                        },null)
                        val sizeInBytes = fileZipCheck.length()
                        val md5String = FileStorage.getFileMD5(fileZipCheck)
                        Log.d(TAG, "execute: ====> md5String = $md5String")
                        Log.d(TAG, "resumeDownloading: =====> File size: $sizeInBytes bytes")

                    }
                }
            }catch (e : Exception){
                Log.d(TAG, "execute: =======> e = ${e.message}")
                scope.launch {
                    Log.d(TAG, "execute: =====> isResumeSupported = $isResumeSupported")
                    if (!isResumeSupported) {
                        DownloadingDatabase.getInstance().getDownloadingDao().updateStatusWithDownloadedBytes(entityDownload.downloadId, 0, DownloadEntity.STATUS_PAUSED)
                    } else {
                        DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(entityDownload.downloadId, DownloadEntity.STATUS_PAUSED)
                    }
                }
            }
        }
    }
    private fun zipFileIfNecessary(newPath: String, dataFileName: String, isZip : Boolean = false): String {
        var newPath1 = newPath
        val fileCheck = File(newPath1)
        if (fileCheck.exists() && isZip) {
            Log.d(TAG, "zipFileIfNeceesary: ====> zip file")
            if (!dataFileName.contains("zip")) {
                FileStorage.createZipFile(
                    listOf(fileCheck),
                    File(entityDownload.dirPath, dataFileName.plus(".zip"))
                )
            }
            newPath1 = FileStorage.getPath(entityDownload.dirPath, dataFileName.plus(".zip"))
        }
        return newPath1
    }

    override fun resume() {
        currentState = DownloadTaskState.Downloading
        scope.launch {
            DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(
                entityDownload.downloadId,
                DownloadEntity.STATUS_DOWNLOADING
            )
        }
    }

    override fun pause() {
        currentState = DownloadTaskState.Paused
        scope.launch {
            DownloadingDatabase.getInstance().getDownloadingDao().updateStatus(
                entityDownload.downloadId,
                DownloadEntity.STATUS_PAUSED
            )
        }
    }

    override fun cancel() {
        currentState = DownloadTaskState.None
        scope.coroutineContext.cancelChildren()
    }

    override fun close() {
        scope.cancel()
    }
}