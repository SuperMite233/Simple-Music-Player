package com.supermite.smp.conversion

import org.junit.Assert.*
import org.junit.Test

class NcmFileRulesTest {
    @Test fun shortProviderReadsPreserveCipherPosition() {
        val payload=ByteArray(100_003){(it*31).toByte()};val key=ByteArray(256){(it*17+9).toByte()}
        val expected=payload.copyOf();NcmCipher.apply(expected,expected.size,key,0)
        val output=java.io.ByteArrayOutputStream();var start=0
        for(size in listOf(7,301,65536,11,34148)) {
            val length=minOf(size,payload.size-start);if(length<=0)break
            val chunk=payload.copyOfRange(start,start+length);NcmCipher.apply(chunk,length,key,start and 255)
            output.write(chunk);start+=length
        }
        if(start<payload.size){val chunk=payload.copyOfRange(start,payload.size);NcmCipher.apply(chunk,chunk.size,key,start and 255);output.write(chunk)}
        assertArrayEquals(expected,output.toByteArray())
    }

    @Test fun untrustedMetadataCannotEscapeDirectoryOrExceedFilenameByteLimit() {
        val value=NcmFileNames.sanitize("../坏名字/"+"音".repeat(200)+"\u0000")
        assertFalse(value.contains('/'));assertFalse(value.contains('\\'));assertFalse(value.contains('\u0000'))
        assertTrue(value.toByteArray(Charsets.UTF_8).size<=180)
        assertEquals("converted_music",NcmFileNames.sanitize("..."))
    }
}
