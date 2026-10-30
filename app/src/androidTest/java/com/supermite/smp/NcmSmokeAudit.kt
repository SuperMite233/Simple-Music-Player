package com.supermite.smp

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.supermite.smp.cache.CacheCategory
import com.supermite.smp.cache.CacheManager
import com.supermite.smp.conversion.ConvertedMediaPublisher
import com.supermite.smp.conversion.NcmConverter
import com.supermite.smp.data.LibraryStore
import com.supermite.smp.data.Track
import com.supermite.smp.data.TrackEdit
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** One-second synthetic FLAC only; no personal music, credentials or remote API access. */
object NcmSmokeAudit {
    fun run(instrumentation: Instrumentation): JSONObject {
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".debug")){"NCM smoke requires the isolated debug package"}
        val cache=CacheManager(context)
        val report=JSONObject()
        val fixture=instrumentation.context.assets.open("smp-silent.flac").use {it.readBytes()}
        check(fixture.size in 4..256*1024 && fixture.copyOfRange(0,4).contentEquals("fLaC".toByteArray()))
        val input=cache.createWorkFile("ncm") ?: error("Enable TEMP cache for the NCM smoke test")
        var published: Uri?=null
        try {
            val payload=buildNcm(fixture)
            input.outputStream().use {it.write(payload)}
            val inputUri=Uri.fromFile(input.file)
            check(NcmInput.validate(context,inputUri).endsWith(".ncm"))
            NcmConverter(context).convertTemporary(inputUri).use {result ->
                check(result.isTemporary && result.format=="flac" && result.fileName.endsWith(".flac"))
                val uniqueTitle="SMP NCM smoke " + UUID.randomUUID().toString()
                val media=ConvertedMediaPublisher.publish(context,result.outputFile,uniqueTitle)
                published=media
                check(media.scheme=="content" && media.authority==MediaStore.AUTHORITY){"NCM output was not published to MediaStore"}
                context.contentResolver.query(media,arrayOf(MediaStore.Audio.Media.DISPLAY_NAME,MediaStore.Audio.Media.MIME_TYPE,MediaStore.Audio.Media.SIZE),null,null,null)?.use {cursor ->
                    check(cursor.moveToFirst()){ "Published music is missing from the system library" }
                    check(cursor.getString(0).endsWith(".flac"));check(cursor.getString(1).startsWith("audio/"));check(cursor.getLong(2)>0)
                    report.put("systemMime",cursor.getString(1)).put("publishedBytes",cursor.getLong(2))
                } ?: error("Cannot query the published music")
                val retriever=MediaMetadataRetriever()
                val duration=try {
                    retriever.setDataSource(context,media)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {retriever.release()}
                check(duration in 900..1100){"System decoder did not recognize the one-second FLAC: $duration"}
                val scan=ConvertedMediaPublisher.requestScan(context,media,"audio/flac")
                check(scan.indexed){"MediaStore output indexing was not confirmed"}
                report.put("ncmInputBytes",payload.size).put("durationMs",duration).put("mediaIndexed",true)
            }
            report.put("records",recordPersistence(context,cache))
            return report
        } finally {
            input.close()
            published?.let {media ->
                check(context.contentResolver.delete(media,null,null)>0){"Could not remove the smoke-test MediaStore entry"}
                report.put("testMediaDeleted",true)
            }
        }
    }

    private fun recordPersistence(context: Context,cache: CacheManager): JSONObject {
        val workspace=cache.createWorkFile("record-work") ?: error("No TEMP workspace for record smoke")
        workspace.use {
            val folder=workspace.file.parentFile ?: error("Missing owned workspace")
            val isolated=object:ContextWrapper(context){override fun getFilesDir():File=folder}
            val local=Track("smoke:local","file:///synthetic.mp3","Record smoke song","SMP fixture","Smoke",1000)
            val remote=local.copy(id="stream:dizzylab:smoke:track",uri="https://streaming.example.invalid/short-lived?token=fixture-only",sourcePath="https://www.dizzylab.net/d/smoke/")
            val store=LibraryStore(isolated)
            store.saveTracks(listOf(local));store.rememberTracks(listOf(remote))
            store.addHistory(local.id);store.addHistory(remote.id)
            store.saveEdit(remote.id,TrackEdit(tags=listOf("smoke"),rating=4));store.setFavorite(remote.id,true)
            cache.clear(CacheCategory.METADATA);store.pruneUnreferencedMetadata()
            val restored=LibraryStore(isolated)
            check(restored.history.size==1 && restored.playCount(remote.id)==2 && restored.playCount(local.id)==2)
            check(restored.isFavorite(remote.id) && restored.edits[local.id]?.rating==4)
            check(restored.trackForRecord(remote.id)?.uri?.startsWith("https://www.dizzylab.net/stream-record/")==true)
            check(!File(folder,"library_state.json").readText().contains("fixture-only"))
            workspace.validate()
            return JSONObject().put("logicalHistoryRows",1).put("combinedPlays",2).put("survivesMetadataClear",true)
        }
    }

    private fun buildNcm(flac:ByteArray):ByteArray {
        fun aes(key:ByteArray,bytes:ByteArray):ByteArray=Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"));doFinal(bytes)
        }
        fun hex(value:String)=value.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
        val sourceKey="fixture-only-payload-key".toByteArray()
        val encryptedKey=aes(hex("687A4852416D736F356B496E62617857"),"neteasecloudmusic".toByteArray()+sourceKey).map {(it.toInt() xor 0x64).toByte()}.toByteArray()
        val metadata=JSONObject().put("musicName","SMP NCM smoke fixture").put("artist",JSONArray().put(JSONArray().put("SMP fixture").put(0)))
            .put("album","Synthetic one-second silence").put("format","flac")
        val prefix="163 key(Don't modify):".toByteArray();check(prefix.size==22)
        val encryptedMetadata=(prefix+Base64.getEncoder().encode(aes(hex("2331346C6A6B5F215C5D2630553C2728"),("music:"+metadata).toByteArray())))
            .map {(it.toInt() xor 0x63).toByte()}.toByteArray()
        val box=IntArray(256){it};var j=0
        for(i in 0..255){j=(j+box[i]+(sourceKey[i%sourceKey.size].toInt() and 255)) and 255;val value=box[i];box[i]=box[j];box[j]=value}
        val stream=ByteArray(256){index -> val k=(index+1) and 255;val a=box[k];val b=box[(a+k) and 255];box[(a+b) and 255].toByte()}
        val audio=flac.mapIndexed {index,byte ->(byte.toInt() xor stream[index and 255].toInt()).toByte()}.toByteArray()
        fun number(value:Int)=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        return ByteArrayOutputStream().apply {
            write("CTENFDAM".toByteArray());write(byteArrayOf(2,0));write(number(encryptedKey.size));write(encryptedKey)
            write(number(encryptedMetadata.size));write(encryptedMetadata);write(ByteArray(5));write(number(0));write(number(0));write(audio)
        }.toByteArray()
    }
}
