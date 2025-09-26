//package com.marusys.upgradefirmware
//
//import android.content.Context
//import android.os.Environment
//import android.util.Log
//import android.webkit.URLUtil
//import androidx.core.content.ContextCompat
//import com.marusys.upgradefirmware.database.DownloadDatabase
//import com.marusys.upgradefirmware.database.DownloadEntity
//import com.marusys.upgradefirmware.network.NetworkStateReceiver
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.async
//import kotlinx.coroutines.awaitAll
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.io.BufferedInputStream
//import java.io.File
//import java.io.FileInputStream
//import java.math.BigInteger
//import java.net.HttpURLConnection
//import java.net.URL
//import java.security.MessageDigest
//import java.util.UUID
//
//class DownloadingManager(val context: Context) : NetworkStateReceiver.NetworkStateListener {
//
//    val TAG = "DownloadingManager"
//    private var connection: HttpURLConnection? = null
//    private var url: String = ""
//    private val scope = CoroutineScope(Dispatchers.IO)
//    private var downloadedBytes: Long = 0L
//    private var currentDownloadId: String? = null
//    private var isDownloading = false
//
//    // Database
//    private val database = DownloadDatabase.getDatabase(context)
//    private val downloadDao = database.downloadDao()
//
//    // Callback để thông báo cho UI
//    private var progressCallback: DownloadProgressCallback? = null
//
//    companion object {
//        private var downloadingManager: DownloadingManager? = null
//
//        // Constants for download optimization
//        private const val BUFFER_SIZE = 64 * 1024 // 64KB buffer for faster download
//        private const val CONNECT_TIMEOUT = 15000 // 15 seconds
//        private const val READ_TIMEOUT = 30000 // 30 seconds
//        private const val MAX_PARALLEL_CONNECTIONS = 4 // For parallel downloading
//        private const val CHUNK_SIZE = 1024 * 1024 // 1MB chunks for parallel download
//
//        fun create(context: Context) {
//            downloadingManager = DownloadingManager(context)
//        }
//
//        fun getInstance(): DownloadingManager {
//            return downloadingManager!!
//        }
//
//        fun getPath(dirPath: String?, fileName: String?): String {
//            return dirPath + File.separator + fileName
//        }
//
//        fun getTempPath(dirPath: String?, fileName: String?): String {
//            return getPath(dirPath, fileName) + ".temp"
//        }
//    }
//
//    init {
//        // Đăng ký network listener
//        NetworkStateReceiver.addListener(this)
//
//        // Resume downloads khi khởi tạo
//        resumeIncompleteDownloads()
//    }
//
//    fun getRootDirPath(context: Context): String {
//        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
//            val file = ContextCompat.getExternalFilesDirs(
//                context.getApplicationContext(),
//                null
//            )[0]
//            return file.getAbsolutePath()
//        } else {
//            return context.getApplicationContext().getFilesDir().getAbsolutePath()
//        }
//    }
//    /**
//     * Start download với database persistence
//     */
//    fun startDownloading(url: String, expectedMD5: String? = null) {
//        scope.launch {
//            try {
//                val downloadId = UUID.randomUUID().toString()
//                currentDownloadId = downloadId
//
//                val dirPath = getRootDirPath(context)
//                val fileName = URLUtil.guessFileName(url, null, null)
//                val tempPath = getTempPath(dirPath, fileName)
//                val finalPath = getPath(dirPath, fileName)
//
//                // Lưu vào database
//                val downloadEntity = DownloadEntity(
//                    downloadId = downloadId,
//                    url = url,
//                    fileName = fileName,
//                    filePath = finalPath,
//                    tempPath = tempPath,
//                    downloadedBytes = 0L,
//                    totalBytes = 0L,
//                    status = DownloadEntity.STATUS_PENDING,
//                    expectedMD5 = expectedMD5
//                )
//
//                downloadDao.insertDownload(downloadEntity)
//                Log.d(TAG, "Saved download to database: $fileName")
//
//                // Bắt đầu download
//                performDownload(downloadEntity)
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error starting download: ${e.message}")
//                currentDownloadId?.let { id ->
//                    updateDownloadStatus(id, DownloadEntity.STATUS_FAILED, e.message)
//                }
//            }
//        }
//    }
//
//    /**
//     * Resume downloads chưa hoàn thành khi app khởi động
//     */
//    private fun resumeIncompleteDownloads() {
//        scope.launch {
//            try {
//                val incompleteDownloads = downloadDao.getIncompleteDownloads()
//                Log.d(TAG, "Found ${incompleteDownloads.size} incomplete downloads to resume")
//
//                incompleteDownloads.forEach { download ->
//                    // Kiểm tra file tồn tại và validate
//                    val tempFile = File(download.tempPath)
//                    if (tempFile.exists()) {
//                        val actualSize = tempFile.length()
//                        if (actualSize != download.downloadedBytes) {
//                            // Cập nhật downloaded bytes theo kích thước file thật
//                            downloadDao.updateProgress(download.downloadId, actualSize)
//                            Log.d(TAG, "Updated progress for ${download.fileName}: $actualSize bytes")
//                        }
//                    }
//
//                    // Set status về paused để user có thể resume
//                    downloadDao.updateStatus(download.downloadId, DownloadEntity.STATUS_PAUSED)
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error resuming incomplete downloads: ${e.message}")
//            }
//        }
//    }
//
//    /**
//     * Resume download cụ thể
//     */
//    fun resumeDownload(downloadId: String) {
//        scope.launch {
//            try {
//                val download = downloadDao.getDownloadById(downloadId)
//                if (download != null && !isDownloading) {
//                    Log.d(TAG, "Resuming download: ${download.fileName}")
//                    currentDownloadId = downloadId
//                    performDownload(download)
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error resuming download: ${e.message}")
//            }
//        }
//    }
//
//    /**
//     * Thực hiện download chính với tối ưu hóa tốc độ
//     */
//    private suspend fun performDownload(downloadEntity: DownloadEntity) = withContext(Dispatchers.IO) {
//        try {
//            isDownloading = true
//            downloadDao.updateStatus(downloadEntity.downloadId, DownloadEntity.STATUS_DOWNLOADING)
//
//            // Thông báo UI về state change
//            progressCallback?.onDownloadStateChanged(downloadEntity.downloadId, DownloadEntity.STATUS_DOWNLOADING)
//
//            val tempFile = File(downloadEntity.tempPath)
//            downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
//
//            // Cập nhật downloaded bytes trong database
//            if (downloadedBytes > 0) {
//                downloadDao.updateProgress(downloadEntity.downloadId, downloadedBytes)
//            }
//
//            Log.d(TAG, "Starting optimized download from byte: $downloadedBytes for ${downloadEntity.fileName}")
//
//            connection = createOptimizedConnection(downloadEntity.url, downloadedBytes)
//
//            Log.d(TAG, "performDownload: ====> connecting with optimizations")
//            connection?.connect()
//
//            // Kiểm tra response code
//            val responseCode = connection?.responseCode ?: 0
//            Log.d(TAG, "HTTP Response Code: $responseCode")
//
//            if (responseCode !in 200..299 && responseCode != 206) {
//                throw Exception("HTTP Error: $responseCode - ${connection?.responseMessage}")
//            }
//
//            val contentLength = connection?.contentLengthLong ?: 0L
//            val totalBytes = contentLength + downloadedBytes
//            downloadDao.updateTotalBytes(downloadEntity.downloadId, totalBytes)
//
//            val inputStream = connection?.inputStream
//
//            // Sử dụng BufferedInputStream cho tốc độ cao hơn
//            val bufferedInputStream = BufferedInputStream(inputStream, BUFFER_SIZE)
//
//            // Tạo file và folder nếu chưa có
//            if (!tempFile.exists()) {
//                tempFile.parentFile?.mkdirs()
//                tempFile.createNewFile()
//            }
//
//            val outputStream = FileDownloadRandomAccessFile.create(tempFile)
//            if (downloadedBytes != 0L) {
//                outputStream.seek(downloadedBytes)
//            }
//
//            // Sử dụng buffer lớn hơn cho tốc độ cao
//            val buff = ByteArray(BUFFER_SIZE)
//            var lastUpdateTime = System.currentTimeMillis()
//            var bytesReadInSecond = 0L
//            var lastSpeedUpdate = System.currentTimeMillis()
//
//            do {
//                try {
//                    val byteCount: Int = bufferedInputStream.read(buff, 0, BUFFER_SIZE)
//                    if (byteCount == -1) {
//                        break
//                    }
//
//                    outputStream.write(buff, 0, byteCount)
//                    downloadedBytes += byteCount
//                    bytesReadInSecond += byteCount
//
//                    // Cập nhật database và UI mỗi giây
//                    val currentTime = System.currentTimeMillis()
//                    if (currentTime - lastUpdateTime > 1000) {
//                        downloadDao.updateProgress(downloadEntity.downloadId, downloadedBytes)
//
//                        // Tính tốc độ download
//                        val speed = bytesReadInSecond / ((currentTime - lastSpeedUpdate) / 1000.0)
//                        val speedString = formatSpeed(speed)
//
//                        // Thông báo UI về progress update
//                        progressCallback?.onProgressUpdate(
//                            downloadEntity.downloadId,
//                            downloadedBytes,
//                            totalBytes,
//                            speedString
//                        )
//
//                        Log.d(TAG, "Progress: ${downloadEntity.fileName} - $downloadedBytes/$totalBytes bytes - Speed: $speedString")
//
//                        lastUpdateTime = currentTime
//                        lastSpeedUpdate = currentTime
//                        bytesReadInSecond = 0L
//                    }
//
//                } catch (e: Exception) {
//                    Log.e(TAG, "Download error: ${e.message}")
//                    downloadDao.updateError(downloadEntity.downloadId, e.message)
//                    progressCallback?.onDownloadError(downloadEntity.downloadId, e.message ?: "Unknown error")
//                    throw e
//                }
//
//            } while (isDownloading && currentDownloadId == downloadEntity.downloadId)
//
//            outputStream.close()
//            bufferedInputStream.close()
//            connection?.disconnect()
//
//            if (isDownloading && currentDownloadId == downloadEntity.downloadId) {
//                // Download hoàn thành
//                val finalPath = downloadEntity.filePath
//                Utils.renameFileName(downloadEntity.tempPath, finalPath)
//
//                downloadDao.updateStatus(downloadEntity.downloadId, DownloadEntity.STATUS_COMPLETED)
//
//                // Thông báo UI về completion
//                progressCallback?.onDownloadStateChanged(downloadEntity.downloadId, DownloadEntity.STATUS_COMPLETED)
//                progressCallback?.onDownloadCompleted(downloadEntity.downloadId, true, finalPath)
//
//                Log.d(TAG, "Download completed: ${downloadEntity.fileName}")
//
//                // Verify MD5 nếu có
//                downloadEntity.expectedMD5?.let { expectedMD5 ->
//                    verifyDownloadIntegrity(finalPath, expectedMD5) { isValid ->
//                        scope.launch {
//                            if (isValid) {
//                                Log.d(TAG, "✅ ${downloadEntity.fileName} - MD5 verification successful")
//                            } else {
//                                Log.e(TAG, "❌ ${downloadEntity.fileName} - MD5 verification failed")
//                                downloadDao.updateError(downloadEntity.downloadId, "MD5 verification failed")
//                                progressCallback?.onDownloadError(downloadEntity.downloadId, "MD5 verification failed")
//                            }
//                        }
//                    }
//                }
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Download failed: ${e.message}")
//            downloadDao.updateStatus(downloadEntity.downloadId, DownloadEntity.STATUS_FAILED)
//            downloadDao.updateError(downloadEntity.downloadId, e.message)
//
//            // Thông báo UI về failure
//            progressCallback?.onDownloadStateChanged(downloadEntity.downloadId, DownloadEntity.STATUS_FAILED)
//            progressCallback?.onDownloadError(downloadEntity.downloadId, e.message ?: "Download failed")
//        } finally {
//            isDownloading = false
//            currentDownloadId = null
//        }
//    }
//
//    /**
//     * Tạo connection được tối ưu hóa cho tốc độ download cao
//     */
//    private fun createOptimizedConnection(url: String, startByte: Long): HttpURLConnection {
//        val connection = URL(url).openConnection() as HttpURLConnection
//
//        // Timeout tối ưu
//        connection.connectTimeout = CONNECT_TIMEOUT
//        connection.readTimeout = READ_TIMEOUT
//
//        // Headers để tối ưu tốc độ
//        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) DownloadManager/2.0")
//        connection.setRequestProperty("Accept", "*/*")
//        connection.setRequestProperty("Accept-Encoding", "identity") // Không nén để tăng tốc
//        connection.setRequestProperty("Connection", "keep-alive")
//        connection.setRequestProperty("Cache-Control", "no-cache")
//
//        // Thêm Range header nếu resume
//        if (startByte > 0) {
//            connection.setRequestProperty("Range", "bytes=$startByte-")
//            Log.d(TAG, "Resume download from byte: $startByte")
//        }
//
//        // Tối ưu HTTP settings
//        connection.instanceFollowRedirects = true
//        connection.useCaches = false
//        connection.doInput = true
//
//        // Thêm headers để server biết client hỗ trợ range requests
//        connection.setRequestProperty("Accept-Ranges", "bytes")
//
//        Log.d(TAG, "Created optimized connection with ${BUFFER_SIZE/1024}KB buffer")
//        return connection
//    }
//
//    /**
//     * Format tốc độ download để hiển thị
//     */
//    private fun formatSpeed(bytesPerSecond: Double): String {
//        return when {
//            bytesPerSecond >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024 * 1024))
//            bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024)
//            else -> "%.0f B/s".format(bytesPerSecond)
//        }
//    }
//
//    /**
//     * Pause download hiện tại
//     */
//    fun pauseDownload() {
//        isDownloading = false
//        currentDownloadId?.let { id ->
//            scope.launch {
//                updateDownloadStatus(id, DownloadEntity.STATUS_PAUSED)
//            }
//        }
//        Log.d(TAG, "Download paused")
//    }
//
//    /**
//     * Cancel download hiện tại
//     */
//    fun cancelDownload() {
//        isDownloading = false
//        currentDownloadId?.let { id ->
//            scope.launch {
//                updateDownloadStatus(id, DownloadEntity.STATUS_CANCELLED)
//                // Xóa temp file
//                val download = downloadDao.getDownloadById(id)
//                download?.let {
//                    File(it.tempPath).delete()
//                }
//            }
//        }
//        Log.d(TAG, "Download cancelled")
//    }
//
//    // NetworkStateReceiver.NetworkStateListener implementation
//    override fun onNetworkAvailable() {
//        Log.d(TAG, "Network available - resuming paused downloads")
//
//        // Thông báo cho UI về network reconnection
//        currentDownloadId?.let { downloadId ->
//            progressCallback?.onNetworkReconnected(downloadId)
//        }
//
//        scope.launch {
//            val pausedDownloads = downloadDao.getIncompleteDownloads()
//                .filter { it.status == DownloadEntity.STATUS_PAUSED }
//
//            if (pausedDownloads.isNotEmpty() && !isDownloading) {
//                // Resume download đầu tiên
//                val download = pausedDownloads.first()
//                currentDownloadId = download.downloadId
//
//                // Thông báo UI về việc auto-resume
//                progressCallback?.onDownloadStateChanged(download.downloadId, DownloadEntity.STATUS_DOWNLOADING)
//
//                resumeDownload(download.downloadId)
//            }
//        }
//    }
//
//    override fun onNetworkLost() {
//        Log.d(TAG, "Network lost - pausing current download")
//
//        // Thông báo cho UI về network loss
//        currentDownloadId?.let { downloadId ->
//            progressCallback?.onNetworkLost(downloadId)
//            progressCallback?.onDownloadStateChanged(downloadId, DownloadEntity.STATUS_PAUSED)
//        }
//
//        if (isDownloading) {
//            pauseDownload()
//        }
//    }
//
//    private suspend fun updateDownloadStatus(downloadId: String, status: Int, error: String? = null) {
//        downloadDao.updateStatus(downloadId, status)
//        error?.let { downloadDao.updateError(downloadId, it) }
//    }
//
//    fun cleanup() {
//        NetworkStateReceiver.removeListener(this)
//        isDownloading = false
//        connection?.disconnect()
//    }
//
//    /**
//     * Tính MD5 checksum của file
//     * @param file File cần tính checksum
//     * @return String MD5 hash hoặc null nếu có lỗi
//     */
//    fun calculateMD5(file: File): String? {
//        return try {
//            val md5 = MessageDigest.getInstance("MD5")
//            FileInputStream(file).use { fis ->
//                val buffer = ByteArray(8192)
//                var bytesRead: Int
//                while (fis.read(buffer).also { bytesRead = it } != -1) {
//                    md5.update(buffer, 0, bytesRead)
//                }
//            }
//            val digest = md5.digest()
//            val bigInteger = BigInteger(1, digest)
//            bigInteger.toString(16).padStart(32, '0')
//        } catch (e: Exception) {
//            Log.e(TAG, "Error calculating MD5: ${e.message}")
//            null
//        }
//    }
//
//    /**
//     * Tính MD5 checksum của file theo đường dẫn
//     * @param filePath Đường dẫn đến file
//     * @return String MD5 hash hoặc null nếu có lỗi
//     */
//    fun calculateMD5(filePath: String): String? {
//        val file = File(filePath)
//        return if (file.exists() && file.isFile) {
//            calculateMD5(file)
//        } else {
//            Log.e(TAG, "File not found: $filePath")
//            null
//        }
//    }
//
//    /**
//     * So sánh MD5 checksum của file v��i hash mong đợi
//     * @param file File cần kiểm tra
//     * @param expectedHash MD5 hash mong đợi
//     * @return true nếu khớp, false nếu không khớp
//     */
//    fun verifyMD5(file: File, expectedHash: String): Boolean {
//        val actualHash = calculateMD5(file)
//        return if (actualHash != null) {
//            val isValid = actualHash.equals(expectedHash, ignoreCase = true)
//            Log.d(TAG, "MD5 Verification - Expected: $expectedHash, Actual: $actualHash, Valid: $isValid")
//            isValid
//        } else {
//            Log.e(TAG, "Cannot calculate MD5 for verification")
//            false
//        }
//    }
//
//    /**
//     * So sánh MD5 checksum của file theo đường dẫn với hash mong đợi
//     * @param filePath Đường dẫn đến file
//     * @param expectedHash MD5 hash mong đợi
//     * @return true nếu khớp, false nếu không khớp
//     */
//    fun verifyMD5(filePath: String, expectedHash: String): Boolean {
//        val file = File(filePath)
//        return if (file.exists() && file.isFile) {
//            verifyMD5(file, expectedHash)
//        } else {
//            Log.e(TAG, "File not found for MD5 verification: $filePath")
//            false
//        }
//    }
//
//    /**
//     * Kiểm tra tính toàn vẹn file sau khi download hoàn thành
//     * @param filePath Đường dẫn file đã download
//     * @param expectedMD5 MD5 hash mong đợi
//     * @param onResult Callback trả về kết quả (true/false)
//     */
//    fun verifyDownloadIntegrity(filePath: String, expectedMD5: String, onResult: (Boolean) -> Unit) {
//        scope.launch {
//            val isValid = verifyMD5(filePath, expectedMD5)
//            withContext(Dispatchers.Main) {
//                onResult(isValid)
//            }
//        }
//    }
//
//    /**
//     * Start parallel download để tăng tốc độ
//     */
//    fun startParallelDownloading(url: String, expectedMD5: String? = null, numConnections: Int = MAX_PARALLEL_CONNECTIONS) {
//        scope.launch {
//            try {
//                val downloadId = UUID.randomUUID().toString()
//                currentDownloadId = downloadId
//
//                val dirPath = getRootDirPath(context)
//                val fileName = URLUtil.guessFileName(url, null, null)
//                val tempPath = getTempPath(dirPath, fileName)
//                val finalPath = getPath(dirPath, fileName)
//
//                // Kiểm tra xem server có hỗ tr��� range requests không
//                if (supportsRangeRequests(url)) {
//                    Log.d(TAG, "Server supports range requests - using parallel download with $numConnections connections")
//
//                    val downloadEntity = DownloadEntity(
//                        downloadId = downloadId,
//                        url = url,
//                        fileName = fileName,
//                        filePath = finalPath,
//                        tempPath = tempPath,
//                        downloadedBytes = 0L,
//                        totalBytes = 0L,
//                        status = DownloadEntity.STATUS_PENDING,
//                        expectedMD5 = expectedMD5
//                    )
//
//                    downloadDao.insertDownload(downloadEntity)
//                    performParallelDownload(downloadEntity, numConnections)
//                } else {
//                    Log.d(TAG, "Server doesn't support range requests - using single connection")
//                    startDownloading(url, expectedMD5)
//                }
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error starting parallel download: ${e.message}")
//                currentDownloadId?.let { id ->
//                    updateDownloadStatus(id, DownloadEntity.STATUS_FAILED, e.message)
//                }
//            }
//        }
//    }
//
//    /**
//     * Kiểm tra server có hỗ trợ range requests không
//     */
//    private suspend fun supportsRangeRequests(url: String): Boolean = withContext(Dispatchers.IO) {
//        return@withContext try {
//            val connection = URL(url).openConnection() as HttpURLConnection
//            connection.requestMethod = "HEAD"
//            connection.connect()
//
//            val acceptRanges = connection.getHeaderField("Accept-Ranges")
//            val supportsRanges = acceptRanges == "bytes" || connection.responseCode == 206
//
//            connection.disconnect()
//            Log.d(TAG, "Range support check - Accept-Ranges: $acceptRanges, Supports: $supportsRanges")
//            supportsRanges
//        } catch (e: Exception) {
//            Log.w(TAG, "Cannot check range support: ${e.message}")
//            false
//        }
//    }
//
//    /**
//     * Thực hiện parallel download
//     */
//    private suspend fun performParallelDownload(downloadEntity: DownloadEntity, numConnections: Int) = withContext(Dispatchers.IO) {
//        try {
//            isDownloading = true
//            downloadDao.updateStatus(downloadEntity.downloadId, DownloadEntity.STATUS_DOWNLOADING)
//
//            // Lấy file size trước
//            val totalFileSize = getFileSize(downloadEntity.url)
//            if (totalFileSize <= 0) {
//                throw Exception("Cannot determine file size for parallel download")
//            }
//
//            downloadDao.updateTotalBytes(downloadEntity.downloadId, totalFileSize)
//            Log.d(TAG, "Starting parallel download - File size: ${formatBytes(totalFileSize)}, Connections: $numConnections")
//
//            val tempFile = File(downloadEntity.tempPath)
//            if (!tempFile.exists()) {
//                tempFile.parentFile?.mkdirs()
//                tempFile.createNewFile()
//            }
//
//            // Tạo file với size đầy đủ
//            val randomAccessFile = FileDownloadRandomAccessFile.create(tempFile)
//            randomAccessFile.setLength(totalFileSize)
//
//            // Chia file thành các chunk
//            val chunkSize = totalFileSize / numConnections
//            val downloadJobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()
//
//            repeat(numConnections) { i ->
//                val startByte = i * chunkSize
//                val endByte = if (i == numConnections - 1) totalFileSize - 1 else (i + 1) * chunkSize - 1
//
//                val job = async {
//                    downloadChunk(downloadEntity.url, startByte, endByte, tempFile, i)
//                }
//                downloadJobs.add(job)
//
//                Log.d(TAG, "Chunk $i: ${formatBytes(startByte)} - ${formatBytes(endByte)}")
//            }
//
//            // Theo dõi tiến độ
//            val progressJob = async {
//                monitorParallelProgress(downloadEntity, totalFileSize, tempFile)
//            }
//
//            // Đợi tất cả chunks hoàn thành
//            downloadJobs.awaitAll()
//            progressJob.cancel()
//
//            randomAccessFile.close()
//
//            if (isDownloading && currentDownloadId == downloadEntity.downloadId) {
//                // Download hoàn thành
//                Utils.renameFileName(downloadEntity.tempPath, downloadEntity.filePath)
//                downloadDao.updateStatus(downloadEntity.downloadId, DownloadEntity.STATUS_COMPLETED)
//                Log.d(TAG, "Parallel download completed: ${downloadEntity.fileName}")
//
//                // Verify MD5
//                downloadEntity.expectedMD5?.let { expectedMD5 ->
//                    verifyDownloadIntegrity(downloadEntity.filePath, expectedMD5) { isValid ->
//                        scope.launch {
//                            if (isValid) {
//                                Log.d(TAG, "✅ ${downloadEntity.fileName} - MD5 verification successful")
//                            } else {
//                                Log.e(TAG, "❌ ${downloadEntity.fileName} - MD5 verification failed")
//                                downloadDao.updateError(downloadEntity.downloadId, "MD5 verification failed")
//                            }
//                        }
//                    }
//                }
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Parallel download failed: ${e.message}")
//            downloadDao.updateStatus(downloadEntity.downloadId, DownloadEntity.STATUS_FAILED)
//            downloadDao.updateError(downloadEntity.downloadId, e.message)
//        } finally {
//            isDownloading = false
//            currentDownloadId = null
//        }
//    }
//
//    /**
//     * Download một chunk của file
//     */
//    private suspend fun downloadChunk(url: String, startByte: Long, endByte: Long, outputFile: File, chunkIndex: Int) = withContext(Dispatchers.IO) {
//        var retryCount = 0
//        val maxRetries = 3
//
//        while (retryCount < maxRetries && isDownloading) {
//            try {
//                val connection = URL(url).openConnection() as HttpURLConnection
//
//                // Cấu hình connection cho chunk
//                connection.connectTimeout = CONNECT_TIMEOUT
//                connection.readTimeout = READ_TIMEOUT
//                connection.setRequestProperty("Range", "bytes=$startByte-$endByte")
//                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ParallelDownloader/1.0")
//                connection.setRequestProperty("Accept", "*/*")
//
//                connection.connect()
//
//                if (connection.responseCode == 206) {
//                    val inputStream = java.io.BufferedInputStream(connection.inputStream, BUFFER_SIZE / 4)
//                    val randomAccessFile = FileDownloadRandomAccessFile.create(outputFile)
//
//                    randomAccessFile.seek(startByte)
//                    val buffer = ByteArray(BUFFER_SIZE / 4) // Smaller buffer for parallel chunks
//                    var totalRead = 0L
//                    var bytesRead: Int
//
//                    while (inputStream.read(buffer).also { bytesRead = it } != -1 && isDownloading) {
//                        randomAccessFile.write(buffer, 0, bytesRead)
//                        totalRead += bytesRead
//
//                        // Break if we've read all bytes for this chunk
//                        if (startByte + totalRead > endByte) {
//                            break
//                        }
//                    }
//
//                    randomAccessFile.close()
//                    inputStream.close()
//                    connection.disconnect()
//
//                    Log.d(TAG, "Chunk $chunkIndex completed: ${formatBytes(totalRead)} bytes")
//                    return@withContext
//
//                } else {
//                    throw Exception("Chunk $chunkIndex failed with response code: ${connection.responseCode}")
//                }
//
//            } catch (e: Exception) {
//                retryCount++
//                Log.w(TAG, "Chunk $chunkIndex retry $retryCount/$maxRetries: ${e.message}")
//
//                if (retryCount >= maxRetries) {
//                    throw e
//                }
//
//               delay(1000L * retryCount) // Exponential backoff
//            }
//        }
//    }
//
//    /**
//     * Theo dõi tiến độ của parallel download
//     */
//    private suspend fun monitorParallelProgress(downloadEntity: DownloadEntity, totalSize: Long, tempFile: File) = withContext(Dispatchers.IO) {
//        var lastSize = 0L
//        var lastTime = System.currentTimeMillis()
//
//        while (isDownloading && currentDownloadId == downloadEntity.downloadId) {
//            kotlinx.coroutines.delay(1000) // Update every second
//
//            if (tempFile.exists()) {
//                val currentSize = tempFile.length()
//                val currentTime = System.currentTimeMillis()
//
//                // Tính tốc độ
//                val speed = (currentSize - lastSize) / ((currentTime - lastTime) / 1000.0)
//                val progress = ((currentSize * 100) / totalSize).toInt()
//
//                // Cập nhật database
//                downloadDao.updateProgress(downloadEntity.downloadId, currentSize)
//
//                Log.d(TAG, "Parallel progress: $progress% - ${formatBytes(currentSize)}/${formatBytes(totalSize)} - Speed: ${formatSpeed(speed)}")
//
//                lastSize = currentSize
//                lastTime = currentTime
//            }
//        }
//    }
//
//    /**
//     * Lấy file size từ server
//     */
//    private suspend fun getFileSize(url: String): Long = withContext(Dispatchers.IO) {
//        return@withContext try {
//            val connection = URL(url).openConnection() as HttpURLConnection
//            connection.requestMethod = "HEAD"
//            connection.connect()
//
//            val size = connection.contentLengthLong
//            connection.disconnect()
//
//            Log.d(TAG, "File size: ${formatBytes(size)}")
//            size
//        } catch (e: Exception) {
//            Log.e(TAG, "Cannot get file size: ${e.message}")
//            -1L
//        }
//    }
//
//    /**
//     * Format bytes thành string dễ đọc
//     */
//    private fun formatBytes(bytes: Long): String {
//        return when {
//            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
//            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
//            bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
//            else -> "$bytes B"
//        }
//    }
//
//    /**
//     * Set callback để UI nhận updates khi network reconnect và auto-resume
//     */
//    fun setProgressCallback(callback: DownloadProgressCallback?) {
//        progressCallback = callback
//    }
//
//    /**
//     * Gọi callback để thông báo tiến độ download cho UI
//     */
//    private fun notifyProgress(downloadId: String, downloadedBytes: Long, totalBytes: Long) {
////        progressCallback?.onProgress(downloadId, downloadedBytes, totalBytes)
//    }
//
//    /**
//     * Gọi callback khi download hoàn thành
//     */
//    private fun notifyCompletion(downloadId: String, success: Boolean, errorMessage: String? = null) {
////        progressCallback?.onCompletion(downloadId, success, errorMessage)
//    }
//
//    /**
//     * Gọi callback khi có lỗi xảy ra
//     */
//    private fun notifyError(downloadId: String, errorMessage: String) {
////        progressCallback?.onError(downloadId, errorMessage)
//    }
//}
