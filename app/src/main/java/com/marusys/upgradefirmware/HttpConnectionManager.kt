package com.marusys.upgradefirmware

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Manages HTTP connections for downloads with proper resource handling
 */
class HttpConnectionManager(private val request: DownloadRequest) {
    private var connection: HttpURLConnection? = null

    @Throws(IOException::class)
    fun connect() {
        disconnect() // Ensure clean state

        connection = createConnection().also { conn ->
            configureConnection(conn)
            conn.connect()
        }
    }

    private fun createConnection(): HttpURLConnection {
        return URL(request.url).openConnection() as HttpURLConnection
    }

    private fun configureConnection(conn: HttpURLConnection) {
        conn.connectTimeout = request.connectTimeOut.toInt()

        if (request.downloadedBytes > 0) {
            val rangeHeader = String.format(
                Locale.ENGLISH,
                "bytes=%d-",
                request.downloadedBytes
            )
            conn.setRequestProperty(DownloadConstants.RANGE_HEADER, rangeHeader)
        }
    }

    fun getInputStream(): InputStream? = connection?.inputStream

    fun getResponseCode(): Int = connection?.responseCode ?: 0

    fun getContentLength(): Long = connection?.contentLengthLong ?: -1L

    fun isValidResponse(): Boolean {
        val responseCode = getResponseCode()
        return responseCode == DownloadConstants.HTTP_OK ||
               responseCode == DownloadConstants.HTTP_PARTIAL_CONTENT
    }

    fun disconnect() {
        connection?.disconnect()
        connection = null
    }
}
