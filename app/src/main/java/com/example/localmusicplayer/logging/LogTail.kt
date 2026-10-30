package com.supermite.smp.logging

import java.io.File
import java.io.RandomAccessFile

/** Bounded tail reader, discarding a partial first UTF-8 line before decoding. */
object LogTail {
    fun read(file:File,maxChars:Int):String {
        val limit=maxChars.coerceIn(1,200_000)
        return RandomAccessFile(file,"r").use {input ->
            val length=input.length()
            val count=minOf(length,limit*4L+4096).toInt()
            val offset=length-count
            input.seek(offset)
            val bytes=ByteArray(count);input.readFully(bytes)
            val start=if(offset>0L)bytes.indexOf('\n'.code.toByte()).let {if(it>=0)it+1 else bytes.size} else 0
            LogRedactor.redact(String(bytes,start,bytes.size-start,Charsets.UTF_8)).takeLast(limit)
        }
    }
}
