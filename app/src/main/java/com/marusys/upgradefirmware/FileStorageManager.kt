package com.marusys.upgradefirmware

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

class FileStorageManager(private val context: Context) {
    fun createDownloadFile(fileName: String, mimeType: String = "application/octet-stream"): Uri? {
        val contentResolver = context.contentResolver

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createFileWithMediaStore(contentResolver, fileName, mimeType)
        } else {
            createFileLegacy(fileName)
        }
    }

    private fun createFileLegacy(fileName: String): Uri? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createFileWithMediaStore(
        contentResolver: ContentResolver,
        fileName: String,
        mimeType: String
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        return contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
    }
    fun openOutputStream(uri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(uri)
    }

    fun openOutputStreamForAppend(uri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(uri, "wa")
    }

    fun markFileAsCompleted(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, contentValues, null, null)
        }
    }
    fun deleteFile(uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }
    fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }


    fun fileExists(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
