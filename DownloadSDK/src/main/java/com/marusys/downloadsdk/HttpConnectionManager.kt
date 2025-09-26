package com.marusys.downloadsdk

import android.util.Log
import com.marusys.downloadsdk.Constants.CONNECT_TIMEOUT
import com.marusys.downloadsdk.model.DownloadRequestFileModel
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

class HttpConnectionManager(private val request: DownloadRequestFileModel) {
    private val TAG = "HttpConnectionManager"
    private var httpURLConnection : HttpURLConnection? = null

    suspend fun buildConnectionToDownload() {
        disconnect()
        httpURLConnection = createConnection(request.url).also { conn ->
            configureConnection(conn)
            conn.connect()
        }

        if (!isValidResponse()) {
            throw IOException("Invalid HTTP response: ${getResponseCode()}")
        }
    }

    private fun createConnection(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }

    private fun configureConnection(conn: HttpURLConnection) {
        conn.connectTimeout = CONNECT_TIMEOUT.toInt()
        conn.readTimeout = CONNECT_TIMEOUT.toInt()

        if (request.downloadedBytes > 0) {
            val rangeHeader = String.format(
                Locale.ENGLISH,
                "bytes=%d-",
                request.downloadedBytes
            )
            Log.d(TAG, "configureConnection: =====> rangeHeader: $rangeHeader")
            conn.setRequestProperty(Constants.RANGE_HEADER, rangeHeader)
        }
    }

    fun getInputStream(): InputStream? = httpURLConnection?.inputStream

    fun getResponseCode(): Int = httpURLConnection?.responseCode ?: 0

    fun getContentLength(): Long = httpURLConnection?.contentLengthLong ?: -1L

    fun getETag(): String? = httpURLConnection?.getHeaderField(Constants.ETAG_HEADER)

    fun isValidResponse(): Boolean {
        val responseCode = getResponseCode()
        return responseCode == Constants.HTTP_OK || responseCode == Constants.HTTP_PARTIAL_CONTENT
    }

    fun disconnect() {
        httpURLConnection?.disconnect()
        httpURLConnection = null
    }
}
