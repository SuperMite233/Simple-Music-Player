package com.supermite.smp.cache

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ArtworkCacheTest {
    @Test fun changingSubsonicCredentialsDoNotChangeImageIdentity() {
        val a="https://example.invalid/rest/getCoverArt.view?u=demo&id=disc&size=400&t=one&s=salt-a"
        val b="https://example.invalid/rest/getCoverArt.view?s=salt-b&t=two&size=400&id=disc&u=demo"
        assertEquals(ArtworkCache.canonicalKey(a),ArtworkCache.canonicalKey(b))
        assertNotEquals(ArtworkCache.canonicalKey(a),ArtworkCache.canonicalKey(a.replace("id=disc","id=another")))
        assertNotEquals(ArtworkCache.canonicalKey(a),ArtworkCache.canonicalKey(a.replace("u=demo","u=another")))
        assertNotEquals(ArtworkCache.canonicalKey(a),ArtworkCache.canonicalKey(a.replace("size=400","size=800")))
    }

    @Test fun cacheSurvivesObjectRecreationAndDeduplicatesIdenticalContent() {
        val dir=Files.createTempDirectory("smp-art-test").toFile()
        val s=CacheStorage(dir,{true},{1024*1024L},{1024*1024L})
        try {
            val cache=ArtworkCache(s)
            val a=cache.getOrFetch("https://example.invalid/a.jpg") {byteArrayOf(1,2,3)}
            val b=cache.getOrFetch("https://example.invalid/b.jpg") {byteArrayOf(1,2,3)}
            assertEquals(a,b)
            assertEquals(a,ArtworkCache(s).cachedPath("https://example.invalid/a.jpg"))
            assertEquals(1,dir.walkTopDown().count {it.isFile && it.extension=="img"})
            assertFalse(dir.walkTopDown().any {it.name.contains("example.invalid")})
        } finally {dir.deleteRecursively()}
    }

    @Test fun concurrentRequestsFetchOnceAndNeverUseJavaHashCodeAsFilename() {
        val dir=Files.createTempDirectory("smp-art-flight").toFile()
        val s=CacheStorage(dir,{true},{1024*1024L},{1024*1024L})
        val pool=Executors.newFixedThreadPool(6)
        try {
            val cache=ArtworkCache(s);val requests=AtomicInteger();val latch=CountDownLatch(1)
            val futures=(1..6).map {pool.submit<String> {latch.await();cache.getOrFetch("https://example.invalid/cover") {requests.incrementAndGet();Thread.sleep(20);byteArrayOf(7,8,9)}}}
            latch.countDown();val paths=futures.map {it.get(3,TimeUnit.SECONDS)}
            assertEquals(1,requests.get());assertEquals(1,paths.distinct().size);assertTrue(paths.first().isNotBlank())
            assertNotEquals(s.keyedFile(CacheCategory.ARTWORK,"Aa"),s.keyedFile(CacheCategory.ARTWORK,"BB"))
        } finally {pool.shutdownNow();dir.deleteRecursively()}
    }
}
