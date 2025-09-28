package com.example.customizedownloadandroid.customizedownloading

interface DownloadingTask {
    fun execute()
    fun resume()
    fun pause()
    fun cancel()
    fun close()
}