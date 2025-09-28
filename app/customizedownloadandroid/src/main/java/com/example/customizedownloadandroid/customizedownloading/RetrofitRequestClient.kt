package com.example.customizedownloadandroid.customizedownloading

import android.util.Log
import androidx.compose.ui.text.intl.Locale
import com.example.customizedownloadandroid.customizedownloading.HttpClient.Companion.ACCEPT_RANGES
import com.example.customizedownloadandroid.customizedownloading.HttpClient.Companion.CONTENT_DISPOSITION
import com.example.customizedownloadandroid.customizedownloading.HttpClient.Companion.RANGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.nio.ByteBuffer

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
            
            responseServer.body()?.byteStream()?.let { inputStream -> 
                val file = File(tempPath)
                val outputStream = FileDownloadRandomAccess.create(file = file)
                val bufferedInputStream = BufferedInputStream(inputStream, BUFFER_SIZE)
                val buffer = ByteArray(BUFFER_SIZE)
                
                do {
                    
                    val byteCount = bufferedInputStream.read(buffer,0,BUFFER_SIZE)
                    if (byteCount == -1 ){
                        break
                    }
                    outputStream.write(buffer, 0, byteCount)
                    cachedDownloadedByte += byteCount
                    Log.d(TAG, "execute: =====> cachedDownloadedByte = $cachedDownloadedByte") 
                    
                }while (true)
                outputStream.flushAndSync()
                outputStream.close()
                inputStream.close()
                val newPath = FileStorage.getPath(entityDownload.dirPath, dataFileName)
                
                FileStorage.renameFileName(tempPath, newPath)
                val fileNew = File(newPath)
                if (fileNew.exists()){
                    Log.d(TAG, "execute: ====> length = ${fileNew.length()}")
                }
            }
        }
    }

    override fun resume() {
    }

    override fun pause() {
    }

    override fun cancel() {
    }

    override fun close() {
    }
}