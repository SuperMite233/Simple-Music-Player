package com.supermite.smp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.supermite.smp.data.*
import com.supermite.smp.playback.MusicPlayer
import com.supermite.smp.stream.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Isolated visual fixtures: no real accounts, remote writes or music downloads. */
class VisualAuditInstrumentation : Instrumentation() {
    private lateinit var activity: MainActivity
    private lateinit var output: File
    private lateinit var arguments: Bundle
    private lateinit var track: Track
    private lateinit var album: StreamAlbum
    private lateinit var playlist: Playlist
    private val results = JSONArray()
    private var errors = 0
    private val assertions = JSONArray()
    private val smokeResults = JSONObject()

    override fun onCreate(arguments: Bundle?) { this.arguments = arguments ?: Bundle(); start() }
    private fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun get(name: String): Any? = field(name).get(activity)
    private fun set(name: String, value: Any?) { field(name).set(activity, value) }
    private fun enumValue(name: String, value: String): Any = MainActivity::class.java.declaredClasses.first { it.simpleName == name }.enumConstants!!.first { (it as Enum<*>).name == value }
    private fun call(name: String, vararg args: Any?): Any? = MainActivity::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(activity, *args)
    private fun ui(block: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync {try {block()} catch (e: Throwable) {failure=e}}
        failure?.let {throw it}
    }
    private fun render(page: String) { call("render", enumValue("Page", page)) }
    private fun dismiss() { sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); Thread.sleep(150) }
    private fun click(text: String) {
        val root = uiAutomation.rootInActiveWindow ?: error("No accessibility window")
        val node = root.findAccessibilityNodeInfosByText(text).firstOrNull {
            it.text?.toString() == text || it.contentDescription?.toString() == text || it.contentDescription?.toString()?.startsWith("$text，") == true
        } ?: error("Missing control: $text")
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        check(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Cannot click $text" }
        Thread.sleep(350)
    }
    private fun capture(name: String, block: () -> Unit) {
        val result = JSONObject().put("name", name)
        try {
            ui(block); Thread.sleep(450); ui { }
            val screenshot = uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            File(output, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }; screenshot.recycle()
            val small = JSONArray()
            val clipped = JSONArray()
            val root = uiAutomation.rootInActiveWindow
            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null || !node.isVisibleToUser) return
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                if (node.isClickable && node.isEnabled && node.className?.toString()?.substringAfterLast('.') in listOf("Button", "ImageButton", "CheckBox", "EditText", "Switch", "Spinner", "SeekBar")) {
                    val density = activity.resources.displayMetrics.density
                    if (bounds.width() / density < 47.5f || bounds.height() / density < 47.5f)
                        {
                            var ancestor = node.parent
                            var edge = false
                            while(ancestor != null) {
                                if(ancestor.isScrollable) {
                                    val viewport=Rect();ancestor.getBoundsInScreen(viewport)
                                    if(kotlin.math.abs(bounds.top-viewport.top)<3 || kotlin.math.abs(bounds.bottom-viewport.bottom)<3 || kotlin.math.abs(bounds.left-viewport.left)<3 || kotlin.math.abs(bounds.right-viewport.right)<3) edge=true
                                }
                                ancestor=ancestor.parent
                            }
                            (if(edge) clipped else small).put(JSONObject().put("label", node.text ?: node.contentDescription ?: "").put("widthDp", bounds.width()/density).put("heightDp",bounds.height()/density))
                        }
                }
                for (i in 0 until node.childCount) walk(node.getChild(i))
            }
            walk(root)
            result.put("screenshot", "$name.png").put("smallTargets", small).put("edgeClippedTargets",clipped).put("ok", true)
        } catch (e: Throwable) {
            errors++; result.put("ok", false).put("error", e.cause?.toString() ?: e.toString())
        }
        results.put(result)
        sendStatus(0, Bundle().apply { putString("stream", "VISUAL $name: ${result.optBoolean("ok")}\n") })
    }

    /** Assertions execute outside the UI thread so real pointer events can be injected. */
    private fun scenario(name: String, block: () -> Unit) {
        try {
            block()
            assertions.put(JSONObject().put("name", name).put("ok", true))
            capture(name) { }
        } catch (e: Throwable) {
            errors++
            val failure = JSONObject().put("name", name).put("ok", false).put("error", e.cause?.toString() ?: e.toString())
            assertions.put(failure); results.put(JSONObject(failure.toString()))
            sendStatus(0, Bundle().apply { putString("stream", "ASSERT $name: false\n") })
        }
    }

    private fun drag(view: android.view.View, from: Float, to: Float) {
        val bounds = Rect()
        ui { check(view.getGlobalVisibleRect(bounds) && bounds.width() > 80) { "Gesture target is not visible" } }
        val start = android.os.SystemClock.uptimeMillis()
        for (step in 0..10) {
            val action = when (step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 10 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
            val x = bounds.left + bounds.width() * (from + (to - from) * step / 10f)
            val event = android.view.MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(), action, x, bounds.exactCenterY(), 0)
            sendPointerSync(event); event.recycle(); Thread.sleep(22)
        }
        Thread.sleep(350)
    }

    private fun tap(view: android.view.View) {
        val bounds = Rect()
        ui {
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        }
        Thread.sleep(200)
        ui { check(view.getGlobalVisibleRect(bounds)) { "Tap target is not visible" } }
        val start = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            sendPointerSync(event); event.recycle()
        }
        Thread.sleep(400)
    }

    private fun swipeUp(view: android.view.View) {
        val bounds = Rect()
        ui { view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true) }
        Thread.sleep(250)
        ui { check(view.getGlobalVisibleRect(bounds)) { "Swipe target is not visible" } }
        val density = activity.resources.displayMetrics.density
        val fromY = bounds.exactCenterY()
        val toY = maxOf(16f * density, fromY - 100f * density)
        check(fromY - toY >= 72f * density) { "Insufficient room for the original upward title gesture" }
        val start = android.os.SystemClock.uptimeMillis()
        for (step in 0..10) {
            val action = when (step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 10 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
            val y = fromY + (toY - fromY) * step / 10f
            val event = android.view.MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(), action, bounds.exactCenterX(), y, 0)
            sendPointerSync(event); event.recycle(); Thread.sleep(22)
        }
        Thread.sleep(400)
    }

    private fun smokeAudit() {
        val checks = listOf<Pair<String, () -> JSONObject>>(
            "ncm_system_media_and_records" to { NcmSmokeAudit.run(this) },
            "runtime_diagnostics" to { DiagnosticsSmokeAudit.run(this) }
        )
        for ((name, run) in checks) {
            try {
                val detail = run()
                smokeResults.put(name, detail)
                assertions.put(JSONObject().put("name", name).put("ok", true))
                sendStatus(0, Bundle().apply { putString("stream", "SMOKE $name: true\n") })
            } catch (error: Throwable) {
                errors++
                val failure = JSONObject().put("name", name).put("ok", false).put("error", error.cause?.toString() ?: error.toString())
                assertions.put(failure); smokeResults.put(name, failure)
                sendStatus(0, Bundle().apply { putString("stream", "SMOKE $name: false: ${failure.optString("error")}\n") })
            }
        }
    }
    private fun fixtures() {
        val audio = File(activity.filesDir, "audit-tone.wav")
        val pcm = ByteArray(44100 * 2 * 8)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(44100); putInt(88200); putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size)
        }.array()
        audio.outputStream().use { it.write(header); it.write(pcm) }
        val art = File(activity.filesDir, "audit-cover.png")
        val bitmap = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(42, 76, 70)); val paint = Paint(3)
            paint.color=Color.rgb(170,215,194); drawCircle(256f,256f,170f,paint)
            paint.color=Color.rgb(42,76,70); drawCircle(256f,256f,105f,paint)
            paint.color=Color.rgb(238,219,158); drawCircle(256f,256f,28f,paint)
        }
        art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        track=Track("audit-1", android.net.Uri.fromFile(audio).toString(), "晨光序曲 · Morning Light", "SMP 演示艺术家", "流动的声音", 8000, 1, 2026, mimeType="audio/wav", sourcePath=audio.absolutePath, artworkPath=art.absolutePath)
        val tracks=listOf(track,track.copy(id="audit-2", title="远方来信 · A Letter from Afar",trackNumber=2), track.copy(id="audit-3", title="雨后的城市与一段特别长的歌曲标题，用于验证大字体排版",trackNumber=3))
        val store=get("store") as LibraryStore
        store.playlists.removeAll { !it.isLocked }
        store.subsonicServers.clear();store.webDavServers.clear()
        store.saveTracks(tracks); set("allTracks",tracks); set("currentQueue",tracks); set("currentIndex",0)
        playlist=store.createPlaylist("通勤时光"); store.addToPlaylist(playlist.id, tracks.map { it.id })
        store.saveEdit(track.id, TrackEdit(tags=listOf("器乐","轻音乐"),rating=4))
        (get("player") as MusicPlayer).play(track, false)
        album=StreamAlbum("audit-disc","流动的声音","https://www.dizzylab.net/d/audit/",price=0.0,purchased=true,circle="SMP 演示社团",onSale=true)
        set("dizzylabAlbums", listOf(album,album.copy(id="audit-2",title="城市漫游",price=30.0,purchased=false)))
        set("dizzyMusicLoaded",true);set("dizzyMusicLoading",false);set("dizzyHasMore",false)
        val circle=DizzylabCircle("audit-circle","SMP 演示社团",description="独立音乐与日常灵感")
        set("dizzyCircles",listOf(circle));set("dizzyFollowing",listOf(circle))
        set("dizzyFeed",listOf(DizzylabFeedEntry(circle,"2026-10-29",listOf(album))))
        set("dizzyUser",DizzylabUser("0","演示账户","demo@example.invalid","","普通用户"))
        set("dizzyAccountLoading",true);set("dizzylabToken","visual-audit-fixture-only");set("dizzylabUserId","0")
        val sub=SubsonicServer("audit-server","我的音乐服务器","https://example.invalid")
        store.subsonicServers.add(sub);store.selectedSubsonicServerId=sub.id
        val song=SubsonicSong("audit-song",track.title,track.artist,album=track.album,albumId="audit-album",durationSeconds=8)
        val subAlbum=SubsonicAlbum("audit-album",track.album,track.artist,songCount=3,year=2026)
        val subPlaylist=SubsonicPlaylist("audit-list","通勤时光",songCount=3,comment="今日精选")
        set("subsonicRandomRequested",true);set("subsonicRandomSongs",listOf(song));set("subsonicSongs",listOf(song));set("subsonicAlbums",listOf(subAlbum))
        set("subsonicArtists",listOf(SubsonicArtist("audit-artist",track.artist,1)));set("subsonicPlaylists",listOf(subPlaylist));set("subsonicCurrentAlbum",subAlbum);set("subsonicCurrentPlaylist",subPlaylist)
        val dav=WebDavServer("audit-dav","音乐文件夹",url="https://example.invalid")
        store.webDavServers.add(dav);set("webDavCurrentServer",dav);set("webDavCurrentItems",listOf(WebDavItem("流动的声音","/album/",true),WebDavItem("晨光序曲.wav","/morning.wav",false,pcm.size.toLong())))
    }
    private fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
        if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun scrollBottom() {
        // Post until the new content is measured, just as a user scrolls after rendering.
        descendants(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().forEach { scroll ->
            scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }
    private fun visibleControl(label: String) {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if ((node.text?.toString() == label || node.contentDescription?.toString() == label) && node.isClickable && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        var node = find(uiAutomation.rootInActiveWindow ?: error("No active window")) ?: error("Unreachable control: $label")
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        Thread.sleep(250)
        node = find(uiAutomation.rootInActiveWindow ?: error("No active window")) ?: error("Unreachable control after scrolling: $label")
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        val density = activity.resources.displayMetrics.density
        check(bounds.width()/density >= 47.5f && bounds.height()/density >= 47.5f) { "Clipped control: $label $bounds" }
    }
    private fun supplementalScrollAudit() {
        capture("playing_initial") { render("NOW_PLAYING") }
        ui {scrollBottom()};Thread.sleep(300)
        capture("playing_scrolled") {}
        listOf("上一首", "播放", "下一首", "播放选项").forEach { visibleControl(it) }
        capture("webdav_initial") { set("streamSource",enumValue("StreamSource","WEBDAV"));render("STREAMING") }
        ui {scrollBottom()};Thread.sleep(300);capture("webdav_scrolled"){}
        capture("subsonic_search_initial") {set("onlineMusicSource",OnlineMusicSource.SUBSONIC);set("subsonicScreen",enumValue("SubsonicScreen","SEARCH"));render("ONLINE")}
        ui {scrollBottom()};Thread.sleep(300);capture("subsonic_search_scrolled"){}
        capture("selection_initial") {
            (get("selectedTrackIds") as MutableSet<String>).add(track.id);set("selectionMode",true);set("selectionFromLibrary",true);render("LIBRARY")
        }
        ui {
            descendants(activity.window.decorView).filterIsInstance<android.widget.HorizontalScrollView>().forEach { scroll -> scroll.post { scroll.fullScroll(android.view.View.FOCUS_RIGHT) } }
        }
        Thread.sleep(300);capture("selection_scrolled"){}
        ui {set("selectionMode",false);(get("selectedTrackIds") as MutableSet<String>).clear();render("SETTINGS")}
        for ((name,arg) in listOf("showPlaybackSettingsDialog" to null,"showEqualizerDialog" to null,"showAudioMetadataEditDialog" to track,"showSubsonicEditDialog" to null,"showWebDavEditDialog" to null)) {
            capture(name+"_scrolled") {if(name in listOf("showPlaybackSettingsDialog","showEqualizerDialog"))call(name) else call(name,arg)}
            // Dialogs have their own window; access the custom scroll through accessibility.
            fun scroll(node: AccessibilityNodeInfo?) {
                if(node==null)return
                if(node.isScrollable) {repeat(12){node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)};return}
                for(i in 0 until node.childCount)scroll(node.getChild(i))
            }
            scroll(uiAutomation.rootInActiveWindow);Thread.sleep(500)
            visibleControl("取消")
            capture(name+"_bottom"){}
            dismiss()
        }
        capture("rating_five") {call("showEditDialog",track)}
        fun ratingNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.className?.toString()?.endsWith("RatingBar")==true)return node
            for(i in 0 until node.childCount)ratingNode(node.getChild(i))?.let{return it}
            return null
        }
        var ratingControl=ratingNode(uiAutomation.rootInActiveWindow) ?: error("Missing rating control")
        ratingControl.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);Thread.sleep(300)
        ratingControl=ratingNode(uiAutomation.rootInActiveWindow) ?: error("Missing rating after scrolling")
        val ratingBounds=Rect();ratingControl.getBoundsInScreen(ratingBounds)
        val touchX=ratingBounds.left+ratingBounds.width()*0.9f
        val touchY=ratingBounds.exactCenterY()
        val touchTime=android.os.SystemClock.uptimeMillis()
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(touchTime,android.os.SystemClock.uptimeMillis(),action,touchX,touchY,0)
            sendPointerSync(event);event.recycle()
        }
        Thread.sleep(300)
        capture("rating_five_selected"){}
        click("保存")
        check((get("store") as LibraryStore).edits[track.id]?.rating==5){"Five-star rating was not saved"}
        capture("custom_avatar") {
            set("profileAvatarUri",android.net.Uri.fromFile(File(activity.filesDir,"audit-cover.png")).toString());call("saveSettings");render("SETTINGS")
        }
        check(targetContext.getSharedPreferences("settings",0).getString("profileAvatarUri","")!!.endsWith("audit-cover.png")){"Custom avatar did not persist"}
        dizzylabScrollRefreshAudit()
    }
    private fun assertPlayerStyle() {
        val mini = get("miniPlayerPanel") as android.view.View
        check(mini.background is android.graphics.drawable.ColorDrawable && (mini.background as android.graphics.drawable.ColorDrawable).color == Color.TRANSPARENT) { "Mini player regained a background card" }
        val button = get("miniPlayButton") as com.google.android.material.button.MaterialButton
        check(button.tag == "miniPlayerControl" && button.backgroundTintList?.defaultColor == Color.TRANSPARENT) { "Mini player button style was reset" }
        check(button.iconTint?.defaultColor == com.supermite.smp.ui.Palette.TEXT) { "Mini player icon did not follow theme" }
        val seek = get("miniSeek") as android.widget.SeekBar
        check(seek.tag == "miniPlayerProgress" && seek.progressDrawable.javaClass.simpleName == "PlayerProgressDrawable") { "Mini player lost the thin progress track" }
        check(seek.thumbTintList?.defaultColor == com.supermite.smp.ui.Palette.ACCENT) { "Progress thumb did not follow theme" }
    }

    private fun cacheDialogBottomAudit() {
        fun target(category: String, action: String): AccessibilityNodeInfo {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null) return
                nodes.add(node)
                for (index in 0 until node.childCount) walk(node.getChild(index))
            }
            walk(uiAutomation.rootInActiveWindow)
            fun matches(node: AccessibilityNodeInfo, label: String): Boolean = node.text?.toString() == label ||
                node.contentDescription?.toString() == label || node.contentDescription?.toString()?.startsWith("$label，") == true
            val clearIndex = nodes.indexOfFirst { matches(it, "清理$category") }
            check(clearIndex >= 0) { "Missing cache category action: $category" }
            var result = when (action) {
                "budget" -> nodes.take(clearIndex).lastOrNull { matches(it, "分类上限") }
                "keep" -> nodes.firstOrNull { matches(it, "保留$category") }
                else -> nodes[clearIndex]
            } ?: error("Missing $action control for $category")
            while (!result.isClickable) result = result.parent ?: error("Cache control is not actionable: $category/$action")
            return result
        }
        fun reveal(category: String, action: String) {
            target(category, action).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            Thread.sleep(300)
            val control = target(category, action)
            val bounds = Rect(); control.getBoundsInScreen(bounds)
            val density = activity.resources.displayMetrics.density
            check(control.isVisibleToUser && control.isEnabled && bounds.width()/density >= 47.5f && bounds.height()/density >= 47.5f) {
                "Cache control is clipped or unreachable: $category/$action $bounds"
            }
        }
        for ((category, key) in listOf("运行日志" to "logs", "转换与导出临时文件" to "temporary")) {
            scenario("cache_${key}_switch_reachable") { reveal(category, "keep") }
            scenario("cache_${key}_actions_reachable") { reveal(category, "budget"); reveal(category, "clear") }
        }
        scenario("cache_settings_bottom") {
            fun scroll(node: AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.isScrollable) { repeat(24) { node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }; return }
                for (index in 0 until node.childCount) scroll(node.getChild(index))
            }
            scroll(uiAutomation.rootInActiveWindow)
            Thread.sleep(350)
            visibleControl("关闭")
        }
    }

    /** MDC 1.14 stacks large-font actions inside a separate native buttonPanel ScrollView. */
    private fun logSelectionActionsAudit() {
        fun findAction(label: String): AccessibilityNodeInfo {
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                // Include offscreen descendants: the native action panel can initially show only one button.
                if (node.isClickable && node.text?.toString() == label) return node
                for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
                return null
            }
            return find(uiAutomation.rootInActiveWindow) ?: error("Missing log selection action: $label")
        }
        fun isReachable(label: String): Boolean {
            val node = findAction(label)
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            val density = activity.resources.displayMetrics.density
            return node.isVisibleToUser && node.isEnabled && bounds.width()/density >= 47.5f && bounds.height()/density >= 47.5f
        }
        fun reveal(label: String) {
            findAction(label).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            Thread.sleep(300)
            if (!isReachable(label)) {
                // Scroll only the action's own nearest scrolling ancestor, never the log-file list.
                for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                    repeat(8) {
                        if (!isReachable(label)) {
                            var panel = findAction(label).parent
                            while (panel != null && !panel.isScrollable) panel = panel.parent
                            check(panel != null) { "No scrollable action panel for $label" }
                            panel.performAction(direction)
                            Thread.sleep(150)
                        }
                    }
                    if (isReachable(label)) break
                }
            }
            check(isReachable(label)) {
                val bounds = Rect(); findAction(label).getBoundsInScreen(bounds)
                "Log selection action is clipped or unreachable after scrolling: $label $bounds"
            }
        }
        for ((label, key) in listOf("导出 ZIP" to "export", "取消" to "cancel", "预览" to "preview")) {
            scenario("log_selection_${key}_reachable") { reveal(label) }
        }
    }

    private fun refreshAudit() {
        val originalMode = get("darkMode")
        scenario("mini_unframed") {
            ui { render("LIBRARY") }; Thread.sleep(450)
            ui { assertPlayerStyle() }
        }
        if ((get("miniSeek") as android.view.View).isShown) scenario("mini_seek_does_not_skip") {
            var before = ""
            ui {
                set("currentIndex", 0)
                (get("player") as MusicPlayer).play(track, false)
                set("playbackDurationMs", 8000L)
                call("updateSeek", get("miniSeek"), get("miniTime"))
                before = (get("player") as MusicPlayer).currentTrack!!.id
            }
            Thread.sleep(350)
            drag(get("miniSeek") as android.view.View, 0.25f, 0.8f)
            ui { check((get("player") as MusicPlayer).currentTrack?.id == before && get("currentIndex") == 0) { "Dragging progress skipped a track" } }
        } else scenario("mini_compact_density") {
            ui {
                val config = activity.resources.configuration
                val compact = config.screenHeightDp < 500 || (config.fontScale > 1.3f && get("libraryFiltersExpanded") == true)
                check(compact && (get("miniSeek") as android.view.View).visibility == android.view.View.GONE) { "Progress unexpectedly hidden outside compact layout" }
            }
        }
        scenario("mini_swipe_changes_track") {
            val row = (get("miniThumb") as android.view.View).parent as android.view.View
            drag(row, 0.8f, 0.15f)
            ui { check((get("player") as MusicPlayer).currentTrack?.id == "audit-2") { "Mini row swipe no longer changes track" }; (get("player") as MusicPlayer).pause() }
        }
        scenario("long_title_single_line") {
            ui {
                val longTrack = (get("allTracks") as List<Track>)[2]
                set("currentIndex", 2); (get("player") as MusicPlayer).play(longTrack, false)
                set("nowPlayingArtworkHidden", false); render("NOW_PLAYING")
            }
            Thread.sleep(450)
            ui {
                val title = descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>().first { it.text.toString().startsWith("雨后的城市") }
                check(title.maxLines == 1 && title.ellipsize == android.text.TextUtils.TruncateAt.MARQUEE) { "Long title is not a single-line marquee" }
                if (android.animation.ValueAnimator.areAnimatorsEnabled() && !(activity.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager).isTouchExplorationEnabled) check(title.isSelected) { "Marquee is not selected" }
                val play = get("nowPagePlayButton") as com.google.android.material.button.MaterialButton
                check(play.tag == "playerPrimary" && play.minimumHeight >= (72 * activity.resources.displayMetrics.density).toInt()) { "Primary play target is undersized" }
            }
        }
        scenario("long_title_original_tap_gesture") {
            val title = descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>().first { it.text.toString().startsWith("雨后的城市") }
            val before = get("nowPlayingArtworkHidden") as Boolean
            tap(title)
            ui { check(get("nowPlayingArtworkHidden") != before) { "Marquee title blocked the original artwork/lyrics gesture" } }
        }
        scenario("long_title_original_up_gesture") {
            val title = descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>().first { it.text.toString().startsWith("雨后的城市") }
            swipeUp(title)
            val queue = uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("正在播放列表").orEmpty()
            check(queue.any { it.isVisibleToUser && it.text?.toString() == "正在播放列表" }) { "Marquee title upward gesture did not open the queue" }
        }
        dismiss()
        scenario("source_dropdown_affordance") {
            ui { set("onlineMusicSource", OnlineMusicSource.DIZZYLAB); set("openedDizzylabAlbum", null); render("ONLINE") }
            Thread.sleep(450)
            ui {
                val source = get("statusText") as android.widget.TextView
                val arrow = source.compoundDrawablesRelative[2] ?: error("Missing source dropdown arrow")
                check(arrow.bounds.width() == (28 * activity.resources.displayMetrics.density).toInt()) { "Source arrow is not 28dp" }
                check(source.background is android.graphics.drawable.RippleDrawable && source.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt()) { "Source dropdown lost its container or target" }
            }
        }
        for (mode in listOf("DARK", "LIGHT")) scenario("player_theme_" + mode.lowercase()) {
            ui { set("darkMode", enumValue("DarkMode", mode)); call("applyAppPalette"); call("refreshShellColors"); render("LIBRARY") }
            Thread.sleep(450)
            ui { assertPlayerStyle(); render("NOW_PLAYING") }
            Thread.sleep(450)
            ui {
                val play = get("nowPagePlayButton") as com.google.android.material.button.MaterialButton
                check(play.tag == "playerPrimary" && play.backgroundTintList?.defaultColor == com.supermite.smp.ui.Palette.ACCENT && play.iconTint?.defaultColor == com.supermite.smp.ui.Palette.ON_ACCENT) { "Primary play style did not follow theme" }
                val previous = descendants(activity.window.decorView).filterIsInstance<com.google.android.material.button.MaterialButton>().first { it.contentDescription == "上一首" && it.tag == "playerControl" }
                check(previous.backgroundTintList?.defaultColor == Color.TRANSPARENT && previous.iconTint?.defaultColor == com.supermite.smp.ui.Palette.TEXT) { "Secondary player controls regained filled cards" }
            }
        }
        ui { set("darkMode", originalMode); call("applyAppPalette"); call("refreshShellColors") }
        dizzylabScrollRefreshAudit()
        for (name in listOf("showCacheSettingsDialog", "showDiagnosticsDialog", "showLogRetentionDialog", "showLogSelectionDialog")) {
            capture(name) { render("SETTINGS"); call(name) }
            if (name == "showCacheSettingsDialog") cacheDialogBottomAudit()
            if (name == "showLogSelectionDialog") logSelectionActionsAudit()
            dismiss()
        }
        capture("showLogPreviewDialog") { call("showLogPreviewDialog", emptySet<String>()) }; dismiss()
        capture("refresh_settings") { set("darkMode", originalMode); call("applyAppPalette"); render("SETTINGS") }
    }

    private fun dizzylabScrollRefreshAudit() {
        val albums = (1..48).map { album.copy(id = "audit-page-$it", title = "演示专辑 $it · Synthetic collection") }
        var before = 0
        var key = ""
        scenario("dizzylab_long_list") {
            ui {
                set("onlineMusicSource", OnlineMusicSource.DIZZYLAB); set("openedDizzylabAlbum", null)
                set("dizzySection", DizzylabSection.DISCOVER); set("dizzyMusicLoaded", true); set("dizzyMusicLoading", false)
                set("dizzylabAlbums", albums); set("dizzyHasMore", true); set("dizzyOffset", albums.size); render("ONLINE")
            }
            Thread.sleep(450)
            ui {
                val scroll = get("dizzyListScrollView") as android.widget.ScrollView
                key = call("dizzylabListKey") as String
                scroll.scrollTo(0, (1800 * activity.resources.displayMetrics.density).toInt())
            }
            Thread.sleep(250)
            ui { before = (get("dizzyListScrollView") as android.widget.ScrollView).scrollY; check(before > 0) { "Long list did not scroll" } }
        }
        scenario("dizzylab_append_keeps_position") {
            // Simulate a successful load-more response; never issue an HTTP request.
            ui { set("dizzylabAlbums", albums + (49..64).map { album.copy(id="audit-page-$it", title="演示专辑 $it") }); set("dizzyOffset",64); render("ONLINE") }
            Thread.sleep(500)
            ui {
                val restored = (get("dizzyListScrollView") as android.widget.ScrollView).scrollY
                check(kotlin.math.abs(restored - before) <= 4) { "Appending reset list position: $before -> $restored" }
                check((get("dizzyListPositions") as Map<*, *>)[key] == before) { "List position was not saved under its context key" }
            }
        }
        scenario("dizzylab_load_more_affordance") {
            ui { (get("dizzyListScrollView") as android.widget.ScrollView).fullScroll(android.view.View.FOCUS_DOWN) }
            Thread.sleep(300)
            ui {
                val more = descendants(activity.window.decorView).filterIsInstance<android.widget.Button>().first { it.text.toString() == "加载更多" }
                val bounds = Rect()
                check(more.isClickable && more.isEnabled && more.getGlobalVisibleRect(bounds) && bounds.height() > 0) { "Load-more action is not reachable" }
            }
        }
        ui { (get("dizzyListScrollView") as android.widget.ScrollView).scrollTo(0, before) }
        Thread.sleep(250)
        scenario("dizzylab_detail_back_keeps_position") {
            ui { set("openedDizzylabAlbum", StreamAlbumDetails(albums[5], listOf(track), circle=track.artist, purchased=true)); render("ONLINE") }
            Thread.sleep(450)
            ui { activity.onBackPressedDispatcher.onBackPressed() }
            Thread.sleep(500)
            ui {
                check(get("openedDizzylabAlbum") == null) { "Back did not close detail" }
                val restored = (get("dizzyListScrollView") as android.widget.ScrollView).scrollY
                check(kotlin.math.abs(restored - before) <= 4) { "Detail back reset list position: $before -> $restored" }
            }
        }
    }

    private fun finishReport(theme: String) {
        File(output,"report.json").writeText(JSONObject().put("version",BuildConfig.VERSION_NAME).put("suite","ui-refresh").put("scope",arguments.getString("scope","full")).put("run",arguments.getString("run",theme)).put("theme",theme).put("fontScale",activity.resources.configuration.fontScale).put("widthDp",activity.resources.configuration.screenWidthDp).put("heightDp",activity.resources.configuration.screenHeightDp).put("errors",errors).put("assertions",assertions).put("smoke",smokeResults).put("screens",results).toString(2))
        ui{(get("player") as MusicPlayer).pause();activity.finish()}
        finish(if(errors==0) Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply{putString("stream","Captured ${results.length()} screens, $errors errors; ${output.absolutePath}\n")})
    }
    override fun onStart() {
        val theme=arguments.getString("theme","light")
        val mode=if(theme=="dark")1 else if(theme=="system")2 else 0
        val seed=if(theme=="amber")0xFFF2B84B.toInt() else 0xFF35C2A1.toInt()
        try {
            check(targetContext.packageName.endsWith(".debug")) { "Visual audit requires the isolated debug package" }
            targetContext.getSharedPreferences("settings",0).edit().putBoolean("firstLaunchPromptDone",true).putBoolean("autoScanOnceDone",true).putBoolean("autoCheckUpdates",false).putBoolean("restorePlaybackOnLaunch",false).putInt("darkMode",mode).putInt("themeColor",seed).putString("language","zh").putString("backgroundImageUri", "").putFloat("backgroundAlpha",0.35f).commit()
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            Thread.sleep(700)
            output=File(activity.getExternalFilesDir(null),"visual-audit/"+arguments.getString("run",theme)).apply{mkdirs()}
            if(arguments.getString("scope")=="smoke") {smokeAudit();finishReport(theme);return}
            ui { fixtures() }
            if(arguments.getString("scope")=="refresh") {refreshAudit();finishReport(theme);return}
            if(arguments.getString("scope")=="scroll") {supplementalScrollAudit();finishReport(theme);return}
            for(page in listOf("PLAYLISTS","LIBRARY","SCAN","NOW_PLAYING","SETTINGS","STREAMING","DIZZYLAB_ACCOUNT")) capture(page.lowercase()){set("openedPlaylist",null);render(page)}
            ui{(get("miniPlayButton") as android.widget.Button).performClick()};Thread.sleep(400)
            check((get("player") as MusicPlayer).isPlaying()){ "Existing playback control failed" }
            ui{(get("miniPlayButton") as android.widget.Button).performClick()};Thread.sleep(200)
            check(!(get("player") as MusicPlayer).isPlaying()){ "Existing pause control failed" }
            capture("library_filters"){set("libraryFiltersExpanded",true);render("LIBRARY")}
            capture("playlist_detail"){set("openedPlaylist",playlist);render("PLAYLISTS")}
            capture("dizzylab_music"){set("onlineMusicSource",OnlineMusicSource.DIZZYLAB);set("openedDizzylabAlbum",null);render("ONLINE")}
            capture("dizzylab_album"){set("openedDizzylabAlbum",StreamAlbumDetails(album,listOf(track),circle=track.artist,releaseDate="2026-10-29",description="独立音乐与日常灵感。",tags=listOf("器乐"),purchased=true));render("ONLINE")}
            capture("dizzylab_feed"){set("dizzyAccountSection","feed");set("dizzyAccountLoading",false);render("DIZZYLAB_ACCOUNT")}
            capture("dizzylab_following"){set("dizzyAccountSection","following");render("DIZZYLAB_ACCOUNT")}
            capture("webdav_browser"){set("streamSource",enumValue("StreamSource","WEBDAV"));render("STREAMING")}
            for(screen in listOf("HOME","SEARCH","ARTISTS","SONGS","ALBUMS","PLAYLISTS","ARTIST_DETAIL","ALBUM_DETAIL","PLAYLIST_DETAIL")) capture("subsonic_"+screen.lowercase()) {set("onlineMusicSource",OnlineMusicSource.SUBSONIC);set("subsonicScreen",enumValue("SubsonicScreen",screen));render("ONLINE")}
            capture("library_selection") {(get("selectedTrackIds") as MutableSet<String>).add(track.id);set("selectionMode",true);set("selectionFromLibrary",true);render("LIBRARY")}
            ui{set("selectionMode",false);(get("selectedTrackIds") as MutableSet<String>).clear()}
            if (arguments.getString("scope","full")=="full") {
                val dialogs=listOf("showAccountDialog","showLocalProfileDialog","showStreamingAccountsDialog","showAppearanceSettingsDialog","showDarkModeDialog","showThemeSettingsDialog","showBackgroundSettingsDialog","showPermissionSettingsDialog","showAllFilesAccessDialog","showNotificationSettingsDialog","showPlaybackSettingsDialog","showStreamingSettingsDialog","showEqualizerDialog","showFloatingLyricsSettingsDialog","showStorageSettingsDialog","showCacheSettingsDialog","showDiagnosticsDialog","showLogPreviewDialog","showScanSettingsDialog","showStreamCacheLimitDialog","showOutputDirectoryDialog","showConfigDialog","showDownloadListDialog","showSoftwareDetailsDialog","showUpdateSettingsDialog","showReadmeDialog","showPlaybackModeDialog","showSpeedDialog","showCurrentQueueDialog","showVolumeDialog","showSubsonicManagementDialog","showWebDavManagementDialog","showDizzylabRedemption")
                for (name in dialogs) {
                    ui{set("openedPlaylist",null);render("SETTINGS")};Thread.sleep(200)
                    capture(name){if(name=="showLogPreviewDialog")call(name,emptySet<String>()) else call(name)}
                    if(name=="showDizzylabRedemption") { click("检查兑换码");capture("cdk_inline_error"){} }
                    dismiss()
                }
                capture("webview_chrome"){set("dizzylabToken", "");set("dizzylabCookie", "");call("showDizzylabWebPage","https://www.dizzylab.net/albums/login/","网页登录",null)}
                click("关闭网页")
                val forms=listOf("showPlaylistMetaDialog" to playlist,"showEditDialog" to track,"showAudioMetadataEditDialog" to track,"showTrackDetailsDialog" to track,"showNowPlayingMenu" to track,"showAddToPlaylistDialog" to track,"showSaveEqualizerPresetDialog" to listOf(0,0,0,0,0),"showSubsonicEditDialog" to null,"showWebDavEditDialog" to null,"showCreatePlaylistDialog" to null,"showCreateSubsonicPlaylistDialog" to null)
                for ((name,arg) in forms){capture(name){call(name,arg)};dismiss()}
                capture("server_form_keyboard") {call("showSubsonicEditDialog",null)}
                val inputRoot=uiAutomation.rootInActiveWindow
                fun firstInput(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if(node==null)return null
                    if(node.className?.toString()?.endsWith("EditText")==true)return node
                    for(i in 0 until node.childCount) firstInput(node.getChild(i))?.let{return it}
                    return null
                }
                firstInput(inputRoot)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                capture("server_keyboard_visible"){}
                dismiss();runCatching { click("取消") }

                ui{call("showThemeSettingsDialog")};Thread.sleep(300);click("琥珀");click("取消")
                check(get("themeColor")==seed){"Cancel changed saved theme"}
                ui{call("showThemeSettingsDialog")};Thread.sleep(300);click("琥珀");click("保存")
                check(get("themeColor")==0xFFF2B84B.toInt()){ "Theme preset did not apply" }
                check(targetContext.getSharedPreferences("settings",0).getInt("themeColor",0)==0xFFF2B84B.toInt()) {"Theme did not persist"}
                capture("saved_theme"){render("SETTINGS")}
                capture("dizzylab_redeem_web_notice"){call("showDizzylabRedeemResult","6",false)};dismiss()
                capture("scan_folders"){call("showScanFolderManager","额外扫描文件夹",mutableListOf<String>(),1013)};dismiss()
                ui{call("showDarkModeDialog")};Thread.sleep(250);click("深色")
                check((get("darkMode") as Enum<*>).name == "DARK") { "Dark mode did not apply" }
                check((get("miniTime") as android.widget.TextView).currentTextColor == com.supermite.smp.ui.Palette.MUTED) { "Persistent player text did not follow dark mode" }
                check((get("miniSeek") as android.widget.SeekBar).progressTintList?.defaultColor == com.supermite.smp.ui.Palette.ACCENT) { "Persistent player slider did not follow the seed" }
                capture("saved_dark_mode"){render("SETTINGS")}
                ui{call("showDarkModeDialog")};Thread.sleep(250);click("跟随系统")
                check((get("darkMode") as Enum<*>).name == "SYSTEM") { "System mode did not apply" }
                capture("system_mode"){render("SETTINGS")}
                capture("first_launch"){
                    targetContext.getSharedPreferences("settings",0).edit().putBoolean("firstLaunchPromptDone",false).commit()
                    call("showFirstLaunchDialogIfNeeded")
                };dismiss()
                capture("background_image"){
                    set("backgroundImageUri",android.net.Uri.fromFile(File(activity.filesDir,"audit-cover.png")).toString())
                    set("backgroundAlpha",0.8f);call("saveSettings");call("rebuildShell",enumValue("Page","SETTINGS"))
                }
                check(targetContext.getSharedPreferences("settings",0).getFloat("backgroundAlpha",0f)==0.8f){ "Background opacity did not persist" }
            }
            if (arguments.getString("scope","full") == "full") refreshAudit()
            capture("dizzylab_guest"){set("dizzylabToken", "");set("dizzylabCookie","");set("dizzyAccountSection","home");render("DIZZYLAB_ACCOUNT")}
            capture("dizzylab_error"){set("onlineMusicSource",OnlineMusicSource.DIZZYLAB);set("openedDizzylabAlbum",null);set("dizzylabAlbums",emptyList<StreamAlbum>());set("dizzyMusicError","网络暂时不可用，请检查连接后重试。");render("ONLINE")}
            capture("empty_library"){set("allTracks",emptyList<Track>());set("libraryFiltersExpanded",false);render("LIBRARY")}
            ui{render("SCAN")};Thread.sleep(100)
            ui{activity.onBackPressedDispatcher.onBackPressed()};Thread.sleep(100)
            check((get("page") as Enum<*>).name == "LIBRARY"){"Compatible back dispatcher bypassed library navigation"}
            ui{activity.onBackPressedDispatcher.onBackPressed()};Thread.sleep(100)
            check((get("page") as Enum<*>).name == "PLAYLISTS"){"Back navigation did not return to playlists"}
            finishReport(theme)
        }catch(e:Throwable){finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream","Visual audit failed: $e\n")})}
    }
}
