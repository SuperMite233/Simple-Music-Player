package com.supermite.smp

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import com.supermite.smp.cache.CacheManager
import com.supermite.smp.logging.AppLogger

class SmpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val cache = CacheManager(this)
        AppLogger.initialize(this, cache)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private fun record(activity: Activity, event: String) = AppLogger.debug("LIFECYCLE", "${activity.javaClass.simpleName}: $event")
            override fun onActivityCreated(activity: Activity, saved: Bundle?) = record(activity, "created")
            override fun onActivityStarted(activity: Activity) = record(activity, "started")
            override fun onActivityResumed(activity: Activity) = record(activity, "resumed")
            override fun onActivityPaused(activity: Activity) = record(activity, "paused")
            override fun onActivityStopped(activity: Activity) = record(activity, "stopped")
            override fun onActivitySaveInstanceState(activity: Activity, saved: Bundle) = record(activity, "state saved")
            override fun onActivityDestroyed(activity: Activity) = record(activity, "destroyed")
        })
        Thread({
            runCatching {cache.migrateLegacy()}
                .onSuccess {AppLogger.info("CACHE", "Legacy cache migration completed: $it files")}
                .onFailure {AppLogger.warn("CACHE", "Legacy cache migration will be retried next launch", it)}
        }, "SMP-Cache-Migration").apply {isDaemon=true;start()}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLogger.info("CONFIG", "Configuration changed: ${newConfig.screenWidthDp}x${newConfig.screenHeightDp}dp; fontScale=${newConfig.fontScale}; uiMode=${newConfig.uiMode}")
    }
    override fun onTrimMemory(level: Int) {super.onTrimMemory(level);AppLogger.warn("MEMORY", "Trim memory level=$level")}
    override fun onLowMemory() {super.onLowMemory();AppLogger.warn("MEMORY", "Low memory")}
}
