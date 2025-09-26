package com.marusys.upgradefirmware

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Clean implementation of firmware download manager with ContentProvider support
 * Manages multiple concurrent downloads with proper Android 14+ storage handling
 */
class FirmwareManager private constructor() {

    val TAG = "FirmwareManager"
    private val activeDownloads = ConcurrentHashMap<Long, DownloadTask>()
    private val downloadScope = CoroutineScope(Dispatchers.IO)

    companion object {
        @Volatile
        private var INSTANCE: FirmwareManager? = null

        fun getInstance(): FirmwareManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirmwareManager().also { INSTANCE = it }
            }
        }
    }

    fun startDownload(
        context: Context,
        request: DownloadRequest,
        fileName: String,
        listener: DownloadListener? = null
    ): Long {
        validateDownloadRequest(request, fileName)

        if (isDownloadActive(request.downloadId)) {
            throw IllegalStateException("Download with ID ${request.downloadId} is already active")
        }

        val downloadTask = createDownloadTask(context, request, fileName, listener)
        activeDownloads[request.downloadId] = downloadTask

        downloadScope.launch {
            try {
                downloadTask.startDownload()
            } finally {
                removeCompletedDownload(request.downloadId)
            }
        }

        return request.downloadId
    }

    private fun validateDownloadRequest(request: DownloadRequest, fileName: String) {
        require(request.url.isNotBlank()) { "Download URL cannot be empty" }
        require(request.downloadId != -1L) { "Invalid download ID" }
        require(fileName.isNotBlank()) { "File name cannot be empty" }
    }

    private fun createDownloadTask(
        context: Context,
        request: DownloadRequest,
        fileName: String,
        listener: DownloadListener?
    ): DownloadTask {
        return DownloadTask(context, request, fileName, listener)
    }

    fun pauseDownload(downloadId: Long): Boolean {
        val taskRes = getDownloadTask(downloadId)
        Log.d(TAG, "pauseDownload: ====> taskRes = $taskRes")
        return taskRes?.let { task ->
            task.pauseDownload()
            true
        } ?: false
    }

    fun resumeDownload(downloadId: Long): Boolean {
        return getDownloadTask(downloadId)?.let { task ->
            task.resumeDownload()
            true
        } ?: false
    }

    fun cancelDownload(downloadId: Long): Boolean {
        return getDownloadTask(downloadId)?.let { task ->
            task.cancelDownload()
            activeDownloads.remove(downloadId)
            true
        } ?: false
    }

    fun cancelAllDownloads() {
        activeDownloads.values.forEach { task ->
            task.cancelDownload()
        }
        activeDownloads.clear()
    }

    fun getDownloadState(downloadId: Long): DownloadState? {
        return getDownloadTask(downloadId)?.getDownloadState()
    }

    fun getDownloadProgress(downloadId: Long): Long {
        return getDownloadTask(downloadId)?.getCurrentProgress() ?: 0L
    }

    fun getDownloadFileUri(downloadId: Long): Uri? {
        return getDownloadTask(downloadId)?.getOutputFileUri()
    }

    fun isDownloadActive(downloadId: Long): Boolean {
        return getDownloadTask(downloadId)?.isActive() == true
    }

    fun getActiveDownloadIds(): Set<Long> {
        return activeDownloads.keys.toSet()
    }

    fun getActiveDownloadCount(): Int = activeDownloads.size

    private fun getDownloadTask(downloadId: Long): DownloadTask? {
        return activeDownloads[downloadId]
    }

    private fun removeCompletedDownload(downloadId: Long) {
        activeDownloads.remove(downloadId)
    }

}

/**
 * Clean data class for download requests with validation and builder pattern
 */
data class DownloadRequest(
    val url: String,
    val downloadId: Long,
    val connectTimeOut: Long = TimeUnit.SECONDS.toMillis(30),
    val downloadedBytes: Long = 0L,
    val fileName: String = "",
) {
    class Builder {
        private var url: String = ""
        private var downloadId: Long = -1L
        private var connectTimeOut: Long = TimeUnit.SECONDS.toMillis(30)
        private var downloadedBytes: Long = 0L
        private var fileName: String = ""
//        private var requestState: RequestState = RequestState.None

        fun url(url: String) = apply {
            this.url = url
            this.fileName = URLUtil.guessFileName(url, null, null)
        }
        fun downloadId(id: Long) = apply { this.downloadId = id }
        fun connectTimeOut(timeout: Long) = apply { this.connectTimeOut = timeout }
        fun build() = DownloadRequest(
            url = url,
            downloadId = downloadId,
            connectTimeOut = connectTimeOut,
            downloadedBytes = downloadedBytes,
            fileName = fileName,
        )
    }
}
