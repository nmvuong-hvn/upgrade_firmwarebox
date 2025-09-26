package com.marusys.downloadsdk

import java.util.concurrent.TimeUnit

object Constants {
    const val RANGE_HEADER = "Range"
    const val ETAG_HEADER = "ETag"
    val CONNECT_TIMEOUT = TimeUnit.SECONDS.toSeconds(60)
    const val HTTP_OK = 200
    const val HTTP_PARTIAL_CONTENT = 206

    // Download States
    const val STATE_IDLE = 0
    const val STATE_DOWNLOADING = 1
    const val STATE_PAUSED = 2
    const val STATE_COMPLETED = 3
    const val STATE_FAILED = 4
    const val STATE_CANCELLED = 5
    const val STATE_WAITING_FOR_NETWORK = 6

    // Buffer and retry settings
    const val BUFFER_SIZE = 8192
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 2000L
    const val NETWORK_CHECK_INTERVAL_MS = 1000L
}