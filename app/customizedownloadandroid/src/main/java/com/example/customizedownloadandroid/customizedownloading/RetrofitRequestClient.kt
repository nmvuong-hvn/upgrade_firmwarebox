package com.example.customizedownloadandroid.customizedownloading

import android.util.Log
import androidx.compose.ui.text.intl.Locale
import com.example.customizedownloadandroid.customizedownloading.HttpClient.Companion.ACCEPT_RANGES
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
            val tempPath = FileStorage.getTempPath(entityDownload.dirPath, entityDownload.fileName)
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
                val newPath = FileStorage.getPath(entityDownload.dirPath, entityDownload.fileName)
                
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