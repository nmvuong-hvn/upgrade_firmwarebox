package com.marusys.upgradefirmware

import android.util.Log
import kotlinx.coroutines.delay

/**
 * Manages download state and control flow
 */
class DownloadStateManager(private val listener: DownloadListener?) {
    val TAG = "DownloadStateManager"
    @Volatile
    private var currentState: DownloadState = DownloadState.IDLE

    fun setState(newState: DownloadState) {
        if (currentState != newState) {
            currentState = newState
            listener?.onDownloadStateChanged(newState)
        }
    }

    fun getState(): DownloadState = currentState

    fun isPaused(): Boolean = currentState == DownloadState.PAUSED

    fun isCancelled(): Boolean = currentState == DownloadState.CANCELLED

    fun isActive(): Boolean = currentState in listOf(
        DownloadState.CONNECTING,
        DownloadState.DOWNLOADING
    )

    fun pause() {
        Log.d(TAG, "pause: ====> isActive = ${isActive()} - currentState = $currentState")
        if (isActive()) {
            setState(DownloadState.PAUSED)
        }
    }

    fun resume() {
        if (isPaused()) {
            setState(DownloadState.DOWNLOADING)
        }
    }

    fun cancel() {
        setState(DownloadState.CANCELLED)
    }

    suspend fun waitWhilePaused() {
        Log.d(TAG, "waitWhilePaused: ====> isPaused = ${isPaused()} - isCancelled = ${isCancelled()}")
        while (isPaused() && !isCancelled()) {
            delay(DownloadConstants.PAUSE_CHECK_DELAY_MILLIS)
        }
    }

    fun checkCancellation() {
        if (isCancelled()) {
            throw DownloadCancelledException("Download was cancelled")
        }
    }
}

/**
 * Custom exception for cancelled downloads
 */
class DownloadCancelledException(message: String) : Exception(message)
