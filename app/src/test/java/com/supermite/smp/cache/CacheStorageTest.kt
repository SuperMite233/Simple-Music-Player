package com.supermite.smp.cache

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class CacheStorageTest {
    private fun test(block: (CacheStorage, MutableMap<CacheCategory, Boolean>, MutableMap<CacheCategory, Long>, (Long) -> Unit) -> Unit) {
        val temp=Files.createTempDirectory("smp-cache-test").toFile()
        var time=1L
        val enabled=CacheCategory.entries.associateWith {true}.toMutableMap()
        val limits=CacheCategory.entries.associateWith {8L}.toMutableMap()
        val storage=CacheStorage(temp, {enabled.getValue(it)}, {limits.getValue(it)}, {100L}, {time})
        try {block(storage, enabled, limits) {time=it}} finally {temp.deleteRecursively()}
    }

    @Test fun lruEvictsOnlyTheOverBudgetCategory() = test {s,_,_,time ->
        s.write(CacheCategory.ARTWORK,"a",ByteArray(4));time(2)
        s.write(CacheCategory.ARTWORK,"b",ByteArray(4));time(3)
        s.write(CacheCategory.INDEX,"directory",ByteArray(4))
        s.find(CacheCategory.ARTWORK,"a");time(4)
        s.write(CacheCategory.ARTWORK,"c",ByteArray(4))
        assertNotNull(s.find(CacheCategory.ARTWORK,"a"));assertNull(s.find(CacheCategory.ARTWORK,"b"))
        assertNotNull(s.find(CacheCategory.ARTWORK,"c"));assertNotNull(s.find(CacheCategory.INDEX,"directory"))
        assertEquals(8L,s.usage().first {it.category==CacheCategory.ARTWORK}.bytes)
    }

    @Test fun disabledCategoryCannotReadOrWriteAndClearingDoesNotDeleteOtherData() = test {s,e,_,_ ->
        val audio=s.write(CacheCategory.AUDIO,"track",ByteArray(4))!!
        s.write(CacheCategory.METADATA,"song",ByteArray(4))
        s.pin(audio);e[CacheCategory.AUDIO]=false;s.enforce()
        assertNull(s.find(CacheCategory.AUDIO,"track"));assertNull(s.write(CacheCategory.AUDIO,"other",ByteArray(4)))
        assertTrue(audio.isFile);s.unpin(audio);s.enforce();assertFalse(audio.exists())
        s.clear(CacheCategory.ARTWORK);assertNotNull(s.find(CacheCategory.METADATA,"song"))
    }

    @Test fun pinReferenceCountProtectsConcurrentConsumers() = test {s,_,_,_ ->
        val file=s.write(CacheCategory.AUDIO,"track",ByteArray(4))!!
        s.pin(file);s.pin(file);s.unpin(file);s.clear(CacheCategory.AUDIO);assertTrue(file.isFile)
        s.unpin(file);s.clear(CacheCategory.AUDIO);assertFalse(file.exists())
    }

    @Test fun oversizedEntryIsRejectedWithoutDeletingExistingData() = test {s,_,_,_ ->
        s.write(CacheCategory.ARTWORK,"valid",ByteArray(4))
        assertNull(s.write(CacheCategory.ARTWORK,"oversized",ByteArray(9)))
        assertNotNull(s.find(CacheCategory.ARTWORK,"valid"))
    }

    @Test fun categoryPathCannotEscapeItsOwnedDirectory() = test {s,_,_,_ ->
        for (bad in listOf("../secret", "nested/../../secret", "/absolute", "..\\secret", "\\server\\share", "C:relative")) {
            assertThrows(IllegalArgumentException::class.java) {s.file(CacheCategory.ARTWORK,bad)}
        }
    }

    @Test fun scopedWorkFilesRespectBudgetAndAreRemovedOnFailure() = test {s,e,_,_ ->
        val work=s.createWorkFile("convert/source.ncm")!!
        work.use {
            assertThrows(IOException::class.java) {it.outputStream().use {stream ->stream.write(ByteArray(9));stream.flush()}}
            assertTrue(it.file.length()<=8L)
        }
        assertFalse(work.file.exists())
        e[CacheCategory.TEMP]=false;assertNull(s.createWorkFile("convert/disabled.ncm"))
    }

    @Test fun globalBudgetProtectsPinnedFilesAndRejectsNewImpossibleWrites() {
        val temp=Files.createTempDirectory("smp-total-test").toFile()
        val storage=CacheStorage(temp,{true},{100L},{8L})
        try {
            val active=storage.writePinned(CacheCategory.AUDIO,"active",ByteArray(8))!!
            assertNull(storage.write(CacheCategory.ARTWORK,"other",ByteArray(4)))
            assertTrue(active.isFile)
            assertEquals(8L,storage.usage().sumOf {it.bytes})
        } finally {temp.deleteRecursively()}
    }

    @Test fun metadataLibraryReplacementFilesAreCountedAndFailureCleansTheSession() = test {s,_,_,_ ->
        val work=s.createWorkFile("source.mp3")!!
        work.outputStream().use {it.write(ByteArray(4))}
        // Simulate an external tag writer's same-directory replacement, outside our stream API.
        val sibling=java.io.File(work.file.parentFile,"replacement.tmp").apply {writeBytes(ByteArray(5))}
        assertThrows(IOException::class.java) {work.validate()}
        assertFalse(work.file.exists());assertFalse(sibling.exists())
    }
}
