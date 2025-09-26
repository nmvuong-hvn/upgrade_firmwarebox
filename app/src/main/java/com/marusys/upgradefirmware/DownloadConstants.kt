package com.marusys.upgradefirmware

/**
 * Constants used throughout the download system
 */
object DownloadConstants {
    const val RANGE_HEADER = "Range"
    const val ETAG_HEADER = "ETag"
    const val USER_AGENT_HEADER = "User-Agent"

    const val DEFAULT_READ_TIMEOUT_MILLIS = 20_000
    const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 20_000
    const val DEFAULT_BUFFER_SIZE = 8_192
    const val PAUSE_CHECK_DELAY_MILLIS = 100L

    const val HTTP_OK = 200
    const val HTTP_PARTIAL_CONTENT = 206
    const val HTTP_RANGE_NOT_SATISFIABLE = 416
    const val HTTP_TEMPORARY_REDIRECT = 307
    const val HTTP_PERMANENT_REDIRECT = 308
}
