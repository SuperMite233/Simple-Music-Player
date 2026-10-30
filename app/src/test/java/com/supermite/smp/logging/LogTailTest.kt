package com.supermite.smp.logging

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LogTailTest {
    @Test fun previewReadsLatestEntriesAndKeepsUnicodeReadable() {
        val file=Files.createTempFile("smp-tail-", ".log").toFile()
        try {
            file.bufferedWriter().use {out ->repeat(2000) {out.appendLine("事件 event-$it：缓存操作完成")}}
            val text=LogTail.read(file,300)
            assertTrue(text.contains("event-1999"));assertFalse(text.contains("event-0："));assertFalse(text.contains('\uFFFD'));assertTrue(text.length<=300)
        } finally {file.delete()}
    }
}
