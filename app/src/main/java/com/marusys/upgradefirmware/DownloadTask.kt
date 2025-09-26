package com.marusys.upgradefirmware

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.*

/**
 * Clean implementation of download task with ContentProvider support for Android 14+
 * Uses MediaStore API for proper scoped storage access
 */
class DownloadTask(
    private val context: Context,
    private val request: DownloadRequest,
    private val fileName: String,
    private val listener: DownloadListener? = null
) : DownloadController {

    private val connectionManager = HttpConnectionManager(request)
    private val stateManager = DownloadStateManager(listener)
    private val fileStorageManager = FileStorageManager(context)

    private var downloadJob: Job? = null
    private var progressTracker: DownloadProgressTracker? = null
    private var outputFileUri: Uri? = null

    override suspend fun startDownload() = withContext(Dispatchers.IO) {
        if (stateManager.isActive()) return@withContext

        downloadJob = launch(Dispatchers.IO) {
            try {
                executeDownload()
            } catch (e: DownloadCancelledException) {
                handleDownloadCancellation()
            } catch (e: Exception) {
                handleDownloadError(e)
            } finally {
                cleanup()
            }
        }
    }

    private suspend fun executeDownload() {
        stateManager.setState(DownloadState.CONNECTING)

        // Create or get existing file Uri
        outputFileUri = createOrGetExistingFile()

        establishConnection()

        stateManager.setState(DownloadState.DOWNLOADING)
        performFileDownload()

        outputFileUri?.let { uri ->
            fileStorageManager.markFileAsCompleted(uri)
        }

        stateManager.setState(DownloadState.COMPLETED)
        notifyDownloadComplete()
    }

    private fun createOrGetExistingFile(): Uri {
        return fileStorageManager.createDownloadFile(fileName, "application/octet-stream")
            ?: throw IOException("Unable to create download file")
    }

    private fun establishConnection() {
        connectionManager.connect()

        if (!connectionManager.isValidResponse()) {
            throw IOException("Invalid HTTP response: ${connectionManager.getResponseCode()}")
        }

        val totalSize = connectionManager.getContentLength()
        val existingSize = outputFileUri?.let { fileStorageManager.getFileSize(it) } ?: 0L

        progressTracker = DownloadProgressTracker(
            totalSize = totalSize,
            downloadedBytes = existingSize,
            listener = listener
        )
    }

    private suspend fun performFileDownload() {
        val uri = outputFileUri ?: throw IOException("Output file URI is null")

        connectionManager.getInputStream()?.use { inputStream ->
            openOutputStreamForUri(uri).use { outputStream ->
                downloadDataStream(inputStream, outputStream)
            }
        } ?: throw IOException("Unable to get input stream")
    }

    private fun openOutputStreamForUri(uri: Uri): OutputStream {
        val shouldAppend = request.downloadedBytes > 0
        return if (shouldAppend) {
            fileStorageManager.openOutputStreamForAppend(uri)
        } else {
            fileStorageManager.openOutputStream(uri)
        } ?: throw IOException("Unable to open output stream for URI: $uri")
    }

    private suspend fun downloadDataStream(
        inputStream: java.io.InputStream,
        outputStream: OutputStream
    ) {
        val buffer = ByteArray(DownloadConstants.DEFAULT_BUFFER_SIZE)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            stateManager.checkCancellation()
            stateManager.waitWhilePaused()

            writeBufferToOutputStream(outputStream, buffer, bytesRead)
            progressTracker?.updateProgress(bytesRead)

            yield() // Allow other coroutines to run
        }
    }

    private fun writeBufferToOutputStream(
        outputStream: OutputStream,
        buffer: ByteArray,
        bytesRead: Int
    ) {
        outputStream.write(buffer, 0, bytesRead)
        outputStream.flush() // Ensure data is written immediately
    }

    private fun handleDownloadCancellation() {
        stateManager.setState(DownloadState.CANCELLED)
        // Delete the incomplete file
        outputFileUri?.let { uri ->
            fileStorageManager.deleteFile(uri)
        }
        listener?.onDownloadError("Download was cancelled")
    }

    private fun handleDownloadError(exception: Exception) {
        stateManager.setState(DownloadState.FAILED)
        // Delete the incomplete file
        outputFileUri?.let { uri ->
            fileStorageManager.deleteFile(uri)
        }
        val errorMessage = "Download failed: ${exception.message}"
        listener?.onDownloadError(errorMessage)
    }

    private fun notifyDownloadComplete() {
        val filePath = outputFileUri?.toString() ?: "Unknown location"
        listener?.onDownloadComplete(true, filePath)
    }

    override fun pauseDownload() {
        stateManager.pause()
    }

    override fun resumeDownload() {
        stateManager.resume()
    }

    override fun cancelDownload() {
        stateManager.cancel()
        downloadJob?.cancel()
    }

    override fun getDownloadState(): DownloadState = stateManager.getState()

    fun getCurrentProgress(): Long = progressTracker?.getCurrentProgress() ?: 0L

    fun isActive(): Boolean = stateManager.isActive()

    fun getOutputFileUri(): Uri? = outputFileUri

    private fun cleanup() {
        connectionManager.disconnect()
        downloadJob = null
    }
}