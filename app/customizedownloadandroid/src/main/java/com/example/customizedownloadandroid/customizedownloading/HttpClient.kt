package com.example.customizedownloadandroid.customizedownloading

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

class HttpClient(val downloadEntity: DownloadEntity) {

    private val TAG = "HttpClient"
    companion object {
        const val RANGE = "Range"
        const val ACCEPT_RANGES = "Accept-Ranges"
        const val CONTENT_DISPOSITION = "Content-Disposition"
    }

    private var connection: HttpURLConnection? = null
    suspend fun connect() {
        connection = URL(downloadEntity.url).openConnection() as HttpURLConnection
        connection?.connectTimeout = TimeUnit.SECONDS.toMillis(120).toInt()
        if (downloadEntity.downloadedBytes > 0) {
            val rangeHead = String.format(Locale.ENGLISH, "bytes=%d-", downloadEntity.downloadedBytes)
            connection?.addRequestProperty(RANGE, rangeHead)
        }
        connection?.connect()
    }

    suspend fun connectNotResume(){
        disconnect()
        connection = URL(downloadEntity.url).openConnection() as HttpURLConnection
        connection?.connectTimeout = TimeUnit.SECONDS.toMillis(120).toInt()
        connection?.connect()
    }

    suspend fun reconnect() {
        connection = URL(downloadEntity.url).openConnection() as HttpURLConnection
        if (downloadEntity.downloadedBytes > 0) {
            val rangeHead =
                String.format(Locale.ENGLISH, "bytes=%d-", downloadEntity.downloadedBytes)
            connection?.addRequestProperty(RANGE, rangeHead)
        }
        connection?.connect()
    }

    fun isSuccessful(): Boolean {
        Log.d(TAG, "isSuccessful: ====> code = ${connection?.responseCode}")
        return (connection?.responseCode ?: 0) >= HttpURLConnection.HTTP_OK
                && (connection?.responseCode ?: 0) < HttpURLConnection.HTTP_MULT_CHOICE
    }
     fun disconnect(){
        connection?.disconnect()
        connection = null
    }

    fun isSupportResume(): Boolean {
        val acceptRange = connection?.getHeaderField(ACCEPT_RANGES)
        val isAcceptByteRange = acceptRange != null && acceptRange.isNotEmpty() && acceptRange.contains("bytes")
       return connection?.responseCode == HttpURLConnection.HTTP_PARTIAL || isAcceptByteRange
    }
    fun getInputStream() = connection?.inputStream
    fun getContentLength() = connection?.contentLength
}