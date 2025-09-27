package com.example.customizedownloadandroid.customizedownloading

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest


object FileStorage {
    private const val TAG = "FileStorage"
    private const val BUFFER_SIZE = 64 * 1024
    fun getPath(dirPath: String?, fileName: String?): String {
        return dirPath + File.separator + fileName
    }
    fun getRootDirPath(context: Context): String {
        return if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            Log.d(TAG, "getRootDirPath: =====> VAO DAY 1")

            val file: File = ContextCompat.getExternalFilesDirs(
                context.applicationContext,
                null
            )[0]!!
            file.absolutePath
        } else {
            Log.d(TAG, "getRootDirPath: =====> VAO DAY")
            context.applicationContext.filesDir.absolutePath
        }
    }
    fun getTempPath(dirPath: String?, fileName: String?): String {
        return getPath(dirPath, fileName) + ".temp"
    }
    @Throws(IOException::class)
    fun renameFileName(oldPath: String, newPath: String) {
        val oldFile = File(oldPath)
        Log.d(TAG, "renameFileName: ====> length = ${oldFile.length()}")
        try {
            val newFile = File(newPath)
            Log.d(TAG, "renameFileName: ====> new length = ${newFile.length()}")

            if (newFile.exists()) {
                if (!newFile.delete()) {
                    throw IOException("Deletion Failed")
                }
            }
            if (!oldFile.renameTo(newFile)) {
                throw IOException("Rename Failed")
            }
        } finally {
            if (oldFile.exists()) {
                oldFile.delete()
            }
        }
    }
    fun getFileMD5(file: File): String? {
        if (!file.exists()) return null

        val buffer = ByteArray(BUFFER_SIZE) //
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        val digest = md.digest()
        return digest.joinToString("") { "%02x".format(it) } // convert to hex string
    }
}