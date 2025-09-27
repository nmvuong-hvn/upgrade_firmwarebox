package com.example.customizedownloadandroid.customizedownloading

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.RandomAccessFile

class FileDownloadRandomAccess(file: File) : FileDownloadOutputStream {
    private var out : BufferedOutputStream ?= null
    private var fd : FileDescriptor ?= null
    private var randomAccessFile: RandomAccessFile ?= null

    init {
        randomAccessFile = RandomAccessFile(file,"rw")
        fd = randomAccessFile?.fd
        out = BufferedOutputStream(FileOutputStream(randomAccessFile?.fd))
    }

    override fun write(byte: ByteArray, off: Int, len: Int) {
        out!!.write(byte, off,len)
    }

    override fun flushAndSync() {
        out!!.flush()
        fd!!.sync()
    }

    override fun close() {
        out!!.close()
        randomAccessFile!!.close()
    }

    override fun seek(offset: Long) {
       randomAccessFile!!.seek(offset)
    }

    companion object {
        fun create(file: File) : FileDownloadRandomAccess{
           return FileDownloadRandomAccess(file)
        }
    }
}