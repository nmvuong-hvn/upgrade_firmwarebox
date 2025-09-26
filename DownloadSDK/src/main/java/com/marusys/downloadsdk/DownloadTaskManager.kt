package com.marusys.downloadsdk

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.marusys.downloadsdk.model.DownloadRequestFileModel
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class DownloadTaskManager(
    private val context: Context,
    private var downloadRequestFileModel: DownloadRequestFileModel,
    private val outputFile: File,
    private val listener: DownloadListener? = null
) : DownloadController, NetworkManager.NetworkConnectionState {

    private val TAG = "DownloadTaskManager"

    // Core components
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpConnectionManager = HttpConnectionManager(downloadRequestFileModel)
    private val networkManager = NetworkManager(context, this)

    // State management
    private val currentState = AtomicInteger(Constants.STATE_IDLE)
    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val isWaitingForNetwork = AtomicBoolean(false)

    // Download progress tracking
    private val downloadedBytes = AtomicLong(downloadRequestFileModel.downloadedBytes)
    private val totalBytes = AtomicLong(downloadRequestFileModel.totalBytes)

    // Jobs and retry management
    private var downloadJob: Job? = null
    private var retryCount = 0

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun startDownload() {
        if (currentState.get() == Constants.STATE_DOWNLOADING) {
            Log.w(TAG, "Download already in progress")
            return
        }

        networkManager.registerNetworkCallback()

        if (!networkManager.isConnected()) {
            setState(Constants.STATE_WAITING_FOR_NETWORK)
            Log.d(TAG, "No network connection, waiting for network...")
            return
        }

        executeDownload()
    }

    private fun executeDownload() {
        if (isCancelled.get()) return

        setState(Constants.STATE_DOWNLOADING)
        isPaused.set(false)

        downloadJob = scope.launch {
            try {
                performDownload()
            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled")
                handleDownloadCancellation()
            } catch (e: IOException) {
                Log.e(TAG, "Download IO error", e)
                handleNetworkError(e)
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                handleDownloadError(e)
            }
        }
    }

    private suspend fun performDownload() {
        // Get current downloaded bytes (important for resume)
        val currentDownloadedBytes = downloadedBytes.get()

        // Update request model with current progress for HTTP Range header
        downloadRequestFileModel = downloadRequestFileModel.copy(
            downloadedBytes = currentDownloadedBytes
        )

        Log.d(TAG, "Starting download from byte: $currentDownloadedBytes")

        // Establish connection with Range header for resume
        httpConnectionManager.buildConnectionToDownload()

        val contentLength = httpConnectionManager.getContentLength()

        // Set total bytes correctly for resume scenarios
        if (totalBytes.get() <= 0) {
            // For new downloads, total = content length + already downloaded
            val totalSize = if (currentDownloadedBytes > 0) {
                // Resume: add current downloaded bytes to remaining content length
                currentDownloadedBytes + contentLength
            } else {
                // New download: just the content length
                contentLength
            }
            totalBytes.set(totalSize)
            Log.d(TAG, "Total size set to: $totalSize bytes")
        }

        // Validate that we can resume
        if (currentDownloadedBytes > 0 && outputFile.exists()) {
            val actualFileSize = outputFile.length()
            if (actualFileSize != currentDownloadedBytes) {
                Log.w(TAG, "File size mismatch. Expected: $currentDownloadedBytes, Actual: $actualFileSize")
                // Reset to actual file size
                downloadedBytes.set(actualFileSize)
                downloadRequestFileModel = downloadRequestFileModel.copy(
                    downloadedBytes = actualFileSize
                )
                // Reconnect with correct range
                httpConnectionManager.disconnect()
                httpConnectionManager.buildConnectionToDownload()
            }
        }

        // Start streaming download from current position
        httpConnectionManager.getInputStream()?.use { inputStream ->
            // IMPORTANT: Use append mode (true) when resuming
            FileOutputStream(outputFile, currentDownloadedBytes > 0).use { outputStream ->
                Log.d(TAG, "Resume mode: ${currentDownloadedBytes > 0}, File exists: ${outputFile.exists()}")
                downloadDataStream(inputStream, outputStream)
            }
        } ?: throw IOException("Unable to get input stream")

        // Download completed successfully
        setState(Constants.STATE_COMPLETED)
        listener?.onDownloadComplete(
            downloadRequestFileModel.downloadId,
            true,
            outputFile.absolutePath
        )

        Log.d(TAG, "Download completed. Final size: ${outputFile.length()} bytes")
    }

    private suspend fun downloadDataStream(
        inputStream: java.io.InputStream,
        outputStream: FileOutputStream
    ) {
        val buffer = ByteArray(Constants.BUFFER_SIZE)
        var bytesRead: Int
        var lastProgressUpdate = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            // Check for cancellation
            if (isCancelled.get()) {
                throw CancellationException("Download cancelled")
            }

            // Handle pause state
            while (isPaused.get() && !isCancelled.get()) {
                delay(100)
            }

            // Write data to file
            outputStream.write(buffer, 0, bytesRead)
            outputStream.flush()

            // Update progress
            val currentDownloaded = downloadedBytes.addAndGet(bytesRead.toLong())

            // Throttle progress updates (every 100KB or 1%)
            val shouldUpdateProgress = currentDownloaded - lastProgressUpdate >= 100_000 ||
                    (totalBytes.get() > 0 && (currentDownloaded * 100 / totalBytes.get()) > (lastProgressUpdate * 100 / totalBytes.get()))

            if (shouldUpdateProgress) {
                notifyProgress(currentDownloaded)
                lastProgressUpdate = currentDownloaded
            }

            // Yield to allow other coroutines to run and check for state changes
            yield()
        }
    }

    private fun notifyProgress(downloaded: Long) {
        val total = totalBytes.get()
        val percentage = if (total > 0) {
            ((downloaded * 100) / total).toInt()
        } else 0

        listener?.onProgressUpdate(
            downloadRequestFileModel.downloadId,
            downloaded,
            total,
            percentage
        )
    }

    private fun handleNetworkError(exception: IOException) {
        if (networkManager.isConnected()) {
            // Network is available but connection failed - retry
            handleRetry(exception)
        } else {
            // No network - wait for reconnection
            setState(Constants.STATE_WAITING_FOR_NETWORK)
            isWaitingForNetwork.set(true)
            Log.d(TAG, "Network lost during download, waiting for reconnection...")
        }
    }

    private fun handleRetry(exception: Exception) {
        if (retryCount < Constants.MAX_RETRY_ATTEMPTS && !isCancelled.get()) {
            retryCount++
            Log.d(TAG, "Retrying download... Attempt $retryCount/${Constants.MAX_RETRY_ATTEMPTS}")

            scope.launch {
                delay(Constants.RETRY_DELAY_MS)
                if (!isCancelled.get()) {
                    executeDownload()
                }
            }
        } else {
            setState(Constants.STATE_FAILED)
            listener?.onDownloadError(
                downloadRequestFileModel.downloadId,
                "Download failed after $retryCount attempts: ${exception.message}"
            )
        }
    }

    private fun handleDownloadError(exception: Exception) {
        setState(Constants.STATE_FAILED)
        listener?.onDownloadError(
            downloadRequestFileModel.downloadId,
            "Download error: ${exception.message}"
        )
    }

    private fun handleDownloadCancellation() {
        setState(Constants.STATE_CANCELLED)
        // Delete partial file if download was cancelled
        if (outputFile.exists() && downloadedBytes.get() < totalBytes.get()) {
            outputFile.delete()
            Log.d(TAG, "Deleted partial download file")
        }
        listener?.onDownloadCancelled(downloadRequestFileModel.downloadId)
    }

    // DownloadController implementation
    override fun pauseDownload() {
        if (currentState.get() == Constants.STATE_DOWNLOADING) {
            isPaused.set(true)
            setState(Constants.STATE_PAUSED)
            listener?.onDownloadPaused(downloadRequestFileModel.downloadId)
            Log.d(TAG, "Download paused")
        }
    }

    override fun resumeDownload() {
        when (currentState.get()) {
            Constants.STATE_PAUSED -> {
                if (networkManager.isConnected()) {
                    isPaused.set(false)
                    setState(Constants.STATE_DOWNLOADING)
                    listener?.onDownloadResumed(downloadRequestFileModel.downloadId)
                    Log.d(TAG, "Download resumed")
                } else {
                    setState(Constants.STATE_WAITING_FOR_NETWORK)
                    isWaitingForNetwork.set(true)
                    Log.d(TAG, "Cannot resume - no network connection")
                }
            }
            Constants.STATE_FAILED -> {
                // Allow resume after failure
                retryCount = 0
                executeDownload()
            }
        }
    }

    override fun cancelDownload() {
        isCancelled.set(true)
        isPaused.set(false)
        downloadJob?.cancel()
        cleanup()
        Log.d(TAG, "Download cancelled")
    }

    override fun getDownloadState(): Int = currentState.get()

    override fun getCurrentProgress(): Long = downloadedBytes.get()

    override fun getTotalSize(): Long = totalBytes.get()

    // NetworkManager.NetworkConnectionState implementation
    override fun onConnected() {
        Log.d(TAG, "Network reconnected")

        if (isWaitingForNetwork.get() || currentState.get() == Constants.STATE_WAITING_FOR_NETWORK) {
            isWaitingForNetwork.set(false)
            retryCount = 0 // Reset retry count on network reconnection

            listener?.onNetworkReconnected(downloadRequestFileModel.downloadId)

            // Resume download automatically when network is restored
            executeDownload()
        }
    }

    override fun onDisconnected() {
        Log.d(TAG, "Network disconnected")

        if (currentState.get() == Constants.STATE_DOWNLOADING) {
            setState(Constants.STATE_WAITING_FOR_NETWORK)
            isWaitingForNetwork.set(true)
        }
    }

    private fun setState(newState: Int) {
        val oldState = currentState.getAndSet(newState)
        if (oldState != newState) {
            listener?.onDownloadStateChanged(downloadRequestFileModel.downloadId, newState)
        }
    }

    fun cleanup() {
        downloadJob?.cancel()
        httpConnectionManager.disconnect()
        networkManager.cleanup()
        scope.cancel()
    }

    // Utility methods
    fun getDownloadInfo(): DownloadRequestFileModel {
        return downloadRequestFileModel.copy(
            downloadedBytes = downloadedBytes.get(),
            totalBytes = totalBytes.get(),
            state = currentState.get(),
            progress = if (totalBytes.get() > 0) {
                (downloadedBytes.get().toFloat() / totalBytes.get().toFloat()) * 100
            } else 0f
        )
    }

    fun isActive(): Boolean {
        return currentState.get() in listOf(
            Constants.STATE_DOWNLOADING,
            Constants.STATE_WAITING_FOR_NETWORK
        )
    }
}