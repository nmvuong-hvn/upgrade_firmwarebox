package com.example.customizedownloadandroid.customizedownloading

import java.io.IOException
import kotlin.jvm.Throws

interface FileDownloadOutputStream {

    @Throws(IOException::class)
    fun write(byte : ByteArray, off : Int , len : Int)

    @Throws(IOException::class)
    fun flushAndSync()

    fun close()

    @Throws(IOException::class, IllegalAccessException::class)
    fun seek(offset : Long)
}