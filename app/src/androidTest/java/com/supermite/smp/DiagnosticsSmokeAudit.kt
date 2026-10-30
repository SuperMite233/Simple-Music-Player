package com.supermite.smp

import android.app.Instrumentation
import android.content.Context
import android.content.SharedPreferences
import com.supermite.smp.cache.CacheCategory
import com.supermite.smp.cache.CacheManager
import com.supermite.smp.logging.AppLogger
import com.supermite.smp.logging.LogLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/** Synthetic diagnostics only. Preferences and pre-existing debug log bytes are restored in finally. */
object DiagnosticsSmokeAudit {
    private data class SavedLog(val id:String,val bytes:ByteArray,val modified:Long)

    fun run(instrumentation:Instrumentation):JSONObject {
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".debug")){"Diagnostics smoke requires the isolated debug package"}
        val cache=CacheManager(context)
        AppLogger.initialize(context,cache)
        val preferences=context.getSharedPreferences("runtime_logging",Context.MODE_PRIVATE)
        val policy=context.getSharedPreferences("cache_policy",Context.MODE_PRIVATE)
        val originalPreferences=preferences.all.toMap()
        val originalPolicy=policy.all.toMap()
        val report=JSONObject()
        val checks=JSONArray()
        val id=UUID.randomUUID().toString()
        val fakePassword="synthetic-diagnostics-password-$id"
        val fakeToken="synthetic-diagnostics-token-$id"
        val allowed="smoke-error-allowed-$id"
        val filtered="smoke-debug-filtered-$id"
        var savedLogs:List<SavedLog>?=null
        try {
            // Stop the collector before taking a consistent backup. Never clear a personal install.
            AppLogger.enabled=false
            Thread.sleep(200)
            val logs=cache.directory(CacheCategory.LOGS)
            val originals=logs.walkTopDown().filter {it.isFile}.toList()
            check(originals.sumOf {it.length()}<=8L*1024*1024){"Use a dedicated debug install with less than 8MB existing diagnostics for smoke tests"}
            savedLogs=originals.map {SavedLog(it.relativeTo(logs).invariantSeparatorsPath,it.readBytes(),it.lastModified())}
            cache.clear(CacheCategory.LOGS)
            preferences.edit().clear().commit()
            cache.setEnabled(CacheCategory.LOGS,true)
            cache.setLimitMb(CacheCategory.LOGS,4)

            check(AppLogger.enabled && AppLogger.retentionDays==7){"Fresh diagnostics defaults must be enabled and seven days"}
            checks.put("enabled_by_default_and_seven_day_retention")

            val expired=cache.storage.write(CacheCategory.LOGS,"smoke-expired-$id.log","synthetic expired line\n".toByteArray()) ?: error("Cannot create the retention fixture")
            check(expired.setLastModified(System.currentTimeMillis()-8L*24*60*60*1000))
            AppLogger.prune()
            check(!expired.exists()){"Eight-day-old log was not pruned"}
            checks.put("expired_log_is_pruned")

            AppLogger.level=LogLevel.DEBUG
            AppLogger.info("DIAG_SMOKE","smoke-redaction-$id password=$fakePassword; token=$fakeToken")
            AppLogger.level=LogLevel.ERROR
            AppLogger.debug("DIAG_SMOKE",filtered)
            AppLogger.error("DIAG_SMOKE",allowed)
            val preview=AppLogger.preview(maxChars=200_000)
            check(preview.contains(allowed)){"Error-level synthetic event is missing"}
            check(!preview.contains(filtered)){"Debug event bypassed the configured ERROR level"}
            check(!preview.contains(fakePassword) && !preview.contains(fakeToken) && preview.contains("[REDACTED]")){"Preview did not redact the synthetic credentials"}
            checks.put("level_filtering_and_redacted_preview")

            AppLogger.level=LogLevel.INFO
            val padding="x".repeat(32_000)
            repeat(35){AppLogger.info("DIAG_SMOKE","smoke-rotation-$id-$it $padding")}
            val files=AppLogger.files()
            check(files.size>=3){"Synthetic records did not rotate into three log files"}
            val selected=setOf(files.first().id,files.last().id)
            check(selected.size==2)
            val output=ByteArrayOutputStream()
            AppLogger.export(output,selected)
            val exported=linkedMapOf<String,String>()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use {zip ->
                while(true) {
                    val entry=zip.nextEntry ?: break
                    exported[entry.name]=zip.readBytes().toString(Charsets.UTF_8)
                    zip.closeEntry()
                }
            }
            check(exported.keys.filter {it!="README.txt"}.toSet()==selected){"Export included unselected log files or omitted a selected file"}
            val text=exported.values.joinToString("\n")
            check(!text.contains(fakePassword) && !text.contains(fakeToken)){"Export leaked synthetic credentials"}
            report.put("selectedExportFiles",selected.size).put("exportBytes",output.size())
            checks.put("multi_file_export_contains_only_selected_logs_and_redacts")

            val disabled="smoke-disabled-$id"
            cache.setEnabled(CacheCategory.LOGS,false)
            AppLogger.refreshCapture()
            check(AppLogger.enabled && !AppLogger.isRecording){"LOGS cache toggle did not independently suspend recording"}
            AppLogger.error("DIAG_SMOKE",disabled)
            check(!AppLogger.preview().contains(disabled) && cache.usage().first {it.category==CacheCategory.LOGS}.bytes==0L){"Disabled LOGS cache still received data"}
            checks.put("logs_category_disable_suspends_recording")

            cache.setEnabled(CacheCategory.LOGS,true)
            cache.setLimitMb(CacheCategory.LOGS,1)
            AppLogger.refreshCapture()
            check(AppLogger.isRecording){"Recording did not resume after LOGS cache was enabled"}
            repeat(50){AppLogger.info("DIAG_SMOKE","smoke-quota-$id-$it $padding")}
            val usage=cache.usage().first {it.category==CacheCategory.LOGS}
            check(usage.bytes in 1..CacheManager.MB && usage.bytes<=usage.limitBytes){"Diagnostics exceeded the configured one-megabyte cache quota"}
            report.put("quotaBytes",usage.limitBytes).put("retainedBytes",usage.bytes)
            checks.put("one_megabyte_log_quota_is_enforced")
            report.put("checks",checks).put("passed",true)
            return report
        } finally {
            AppLogger.enabled=false
            try {
                savedLogs?.let {backup ->
                    cache.clear(CacheCategory.LOGS)
                    restore(policy,originalPolicy)
                    for(log in backup) {
                        val target=cache.file(CacheCategory.LOGS,log.id)
                        target.parentFile?.mkdirs()
                        target.writeBytes(log.bytes)
                        check(target.setLastModified(log.modified)){"Cannot restore a pre-existing debug log timestamp"}
                    }
                } ?: restore(policy,originalPolicy)
            } finally {
                restore(preferences,originalPreferences)
                AppLogger.refreshCapture()
            }
            report.put("preferencesAndOriginalLogsRestored",true)
        }
    }

    private fun restore(preferences:SharedPreferences,values:Map<String,*>) {
        val edit=preferences.edit().clear()
        values.forEach {(key,value) ->when(value) {
            is Boolean ->edit.putBoolean(key,value)
            is Int ->edit.putInt(key,value)
            is Long ->edit.putLong(key,value)
            is Float ->edit.putFloat(key,value)
            is String ->edit.putString(key,value)
            is Set<*> ->edit.putStringSet(key,value.filterIsInstance<String>().toSet())
        }}
        check(edit.commit()){"Cannot restore dedicated-debug diagnostic preferences"}
    }
}
