package com.supermite.smp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.supermite.smp.conversion.NcmConverter
import com.supermite.smp.data.LibraryStore
import com.supermite.smp.data.MusicScanner
import com.supermite.smp.data.Playlist
import com.supermite.smp.data.Track
import com.supermite.smp.data.TrackEdit
import com.supermite.smp.playback.MusicPlayer
import com.supermite.smp.stream.DizzylabClient
import com.supermite.smp.stream.StreamAlbum
import com.supermite.smp.stream.StreamAlbumDetails
import com.supermite.smp.ui.Palette
import com.supermite.smp.ui.PlaylistAdapter
import com.supermite.smp.ui.TrackAdapter
import com.supermite.smp.ui.bodyStyle
import com.supermite.smp.ui.dp
import com.supermite.smp.ui.formatDuration
import com.supermite.smp.ui.panelDrawable
import com.supermite.smp.ui.stars
import com.supermite.smp.ui.titleStyle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : Activity(), MusicPlayer.Listener {
    private lateinit var store: LibraryStore
    private lateinit var scanner: MusicScanner
    private lateinit var player: MusicPlayer
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSession
    private lateinit var headerTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var content: LinearLayout
    private lateinit var miniPlayerPanel: View
    private lateinit var miniTitle: TextView
    private lateinit var miniMeta: TextView
    private lateinit var miniThumb: ImageView
    private lateinit var miniPlayButton: Button
    private lateinit var miniSeek: SeekBar
    private lateinit var miniTime: TextView

    private var nowPagePlayButton: Button? = null
    private var nowPageSeek: SeekBar? = null
    private var nowPageTime: TextView? = null
    private val navButtons = mutableMapOf<Page, Button>()

    private var allTracks: List<Track> = emptyList()
    private var visibleTracks: List<Track> = emptyList()
    private var currentQueue: List<Track> = emptyList()
    private var currentIndex: Int = -1
    private var page: Page = Page.SCAN
    private var openedPlaylist: Playlist? = null
    private var selectionMode: Boolean = false
    private var selectionFromLibrary: Boolean = false
    private var selectionPlaylist: Playlist? = null
    private val selectedTrackIds = mutableSetOf<String>()
    private var selectionCountText: TextView? = null
    private var pendingListFirstVisible: Int = -1
    private var pendingListTopOffset: Int = 0
    private var libraryQuery: String = ""
    private var selectedTag: String = ""
    private var minRating: Int = 0
    private var librarySortMode: LibrarySortMode = LibrarySortMode.AZ
    private var isUserSeeking = false
    private var playbackPositionMs = 0L
    private var playbackDurationMs = 0L
    private var playbackMode = PlaybackMode.SEQUENTIAL
    private val pseudoShuffleOrder = mutableListOf<Int>()
    private var pseudoShufflePosition = -1
    private var themeColor: Int = Palette.ACCENT
    private var notificationEnabled: Boolean = false
    private var lockscreenNotificationEnabled: Boolean = false
    private var floatingLyricsEnabled: Boolean = false
    private var backgroundImageUri: String = ""
    private var backgroundAlpha: Float = 0.35f
    private var skipNoMediaFolders: Boolean = false
    private var libraryFiltersExpanded: Boolean = false
    private var profileName: String = "profile"
    private var profileAvatarUri: String = ""
    private var dizzylabCookie: String = ""
    private var dizzylabUserId: String = ""
    private var streamPreloadCount: Int = 1
    private var streamDownloadFolderUri: String = ""
    private var streamCacheLimitGb: Float = 2.0f
    private var streamCacheFolderUri: String = ""
    private var restorePlaybackOnLaunch: Boolean = false
    private var audioFocusBehavior: AudioFocusBehavior = AudioFocusBehavior.PAUSE
    private var streamSource: StreamSource? = null
    private var dizzylabQuery: String = ""
    private var dizzylabVisibleAlbumCount: Int = 40
    private var pendingStreamScrollY: Int = -1
    private var dizzylabAlbums: List<StreamAlbum> = emptyList()
    private var openedDizzylabAlbum: StreamAlbumDetails? = null
    private var pendingStreamDownloadAfterFolder: (() -> Unit)? = null
    private var nowPageLyrics: TextView? = null
    private var pendingRestoreTrackId: String? = null
    private var pendingRestoreSeekMs: Long = -1L
    private var pendingRestoreShouldPlay: Boolean = false
    private var restorePreparingPaused: Boolean = false
    private var pausedByAudioFocus: Boolean = false
    private var playlistCategory: PlaylistCategory = PlaylistCategory.COMMON
    private var floatingLyricsView: StrokeTextView? = null
    private var floatingLyricsAdded: Boolean = false
    private var equalizerPreset: String = "默认"
    private var equalizerLevels: MutableList<Int> = MutableList(5) { 0 }
    private val equalizerPresets = mutableMapOf("默认" to List(5) { 0 })
    private val lyricsCache = mutableMapOf<String, List<LyricLine>>()
    private val imageExecutor = Executors.newFixedThreadPool(3)
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        runOnUiThread { handleAudioFocusChange(change) }
    }

    private val mediaSessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            if (player.currentTrack == null) {
                toggleOrPlayFirst()
            } else if (requestPlaybackFocus()) {
                pausedByAudioFocus = false
                player.resume()
            }
            refreshPlayButtons()
        }

        override fun onPause() {
            abandonPlaybackFocus()
            player.pause()
            refreshPlayButtons()
        }

        override fun onSkipToNext() {
            playNextOrFirst()
        }

        override fun onSkipToPrevious() {
            playPreviousOrFirst()
        }

        override fun onSeekTo(pos: Long) {
            player.seekTo(pos)
        }

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
            if (event.action != KeyEvent.ACTION_UP) return true
            return handleMediaKey(event.keyCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LibraryStore(this)
        scanner = MusicScanner(this)
        player = MusicPlayer(this).apply { listener = this@MainActivity }
        player.headersForTrack = { track ->
            if (track.id.startsWith("stream:dizzylab:") && dizzylabCookie.isNotBlank()) {
                DizzylabClient(dizzylabCookie).streamHeaders()
            } else {
                emptyMap()
            }
        }
        trackAdapter = TrackAdapter(this, store.edits)
        trackAdapter.onMoreClick = { track -> showTrackActions(track) }
        trackAdapter.onTrackClick = { track ->
            if (selectionMode) toggleSelection(track) else playTrack(track, queueForCurrentView())
        }
        trackAdapter.onTrackLongClick = { track, position -> enterSelectionMode(track, (position - 3).coerceAtLeast(0), 0) }
        playlistAdapter = PlaylistAdapter(this) { trackId -> allTracks.firstOrNull { it.id == trackId }?.let { resolvedArtworkPath(it) } }
        audioManager = getSystemService(AudioManager::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        mediaSession = MediaSession(this, "SMP").apply {
            setCallback(mediaSessionCallback)
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
        loadSettings()
        scanner.skipNoMediaFolders = skipNoMediaFolders
        player.setEqualizerLevels(equalizerLevels)
        allTracks = store.savedTracks().sortedWith(MusicScanner.trackComparator)
        PlaybackControlBus.handler = { action -> handlePlaybackAction(action) }
        createNotificationChannel()
        buildShell()
        handlePlaybackIntent(intent)
        render(Page.PLAYLISTS)
        restorePlaybackState()
        showFirstLaunchDialogIfNeeded()
        updateFloatingLyrics()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handlePlaybackIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateFloatingLyrics()
        if (page == Page.SETTINGS && ::content.isInitialized) render(Page.SETTINGS)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildShell(page)
        updateNowPlayingViews(player.currentTrack ?: return)
    }

    override fun onDestroy() {
        savePlaybackState()
        abandonPlaybackFocus()
        removeFloatingLyrics()
        if (::player.isInitialized) player.pause()
        PlaybackControlBus.handler = null
        mediaSession.isActive = false
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android SDK")
    override fun onBackPressed() {
        when {
            page == Page.STREAMING && openedDizzylabAlbum != null -> {
                openedDizzylabAlbum = null
                render(Page.STREAMING)
            }
            page == Page.STREAMING && streamSource != null -> {
                streamSource = null
                dizzylabAlbums = emptyList()
                render(Page.STREAMING)
            }
            page == Page.SCAN -> render(Page.LIBRARY)
            page == Page.LIBRARY -> render(Page.PLAYLISTS)
            page == Page.PLAYLISTS && openedPlaylist != null -> {
                openedPlaylist = null
                render(Page.PLAYLISTS)
            }
            page == Page.PLAYLISTS || page == Page.SETTINGS -> render(Page.NOW_PLAYING)
            page == Page.NOW_PLAYING -> super.onBackPressed()
            else -> render(Page.NOW_PLAYING)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_AUDIO -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    scanMediaStore()
                } else {
                    setStatus("需要音乐读取权限才能自动扫描本地音乐，也可以手动选择文件夹或导入文件。")
                }
            }
            REQUEST_NOTIFICATIONS -> {
                if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                    notificationEnabled = false
                    saveSettings()
                    setStatus("通知权限未授予，状态栏播放控件已关闭。")
                }
                if (page == Page.SETTINGS) render(Page.SETTINGS)
            }
        }
    }

    @Deprecated("Deprecated in Android SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQUEST_STREAM_DOWNLOAD_DIR && pendingStreamDownloadAfterFolder != null) {
                Toast.makeText(this, "未重新授权下载文件夹，已使用默认下载目录。", Toast.LENGTH_LONG).show()
                streamDownloadFolderUri = ""
                saveSettings()
                resumePendingStreamDownload()
            }
            return
        }
        when (requestCode) {
            REQUEST_TREE -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                scanTree(uri)
            }
            REQUEST_IMPORT -> {
                val uris = mutableListOf<Uri>()
                data.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) uris += clip.getItemAt(index).uri
                }
                data.data?.let { uris += it }
                uris.distinct().forEach { persistUriPermission(it, data.flags) }
                scanImportedFiles(uris.distinct())
            }
            REQUEST_BACKGROUND -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                backgroundImageUri = uri.toString()
                saveSettings()
                rebuildShell(Page.SETTINGS)
            }
            REQUEST_AVATAR -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                profileAvatarUri = uri.toString()
                saveSettings()
                rebuildShell(Page.SETTINGS)
            }
            REQUEST_STREAM_DOWNLOAD_DIR -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                streamDownloadFolderUri = uri.toString()
                saveSettings()
                val pendingDownload = pendingStreamDownloadAfterFolder != null
                if (pendingDownload) {
                    if (isStreamDownloadFolderUsable()) {
                        Toast.makeText(this, "下载文件夹授权已更新，继续下载。", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "系统仍拒绝访问该文件夹，已使用默认下载目录。", Toast.LENGTH_LONG).show()
                        streamDownloadFolderUri = ""
                        saveSettings()
                    }
                    resumePendingStreamDownload()
                } else {
                    if (!isStreamDownloadFolderUsable()) {
                        Toast.makeText(this, "无法保存该下载文件夹授权，已恢复默认下载目录。", Toast.LENGTH_LONG).show()
                        streamDownloadFolderUri = ""
                        saveSettings()
                    }
                    render(Page.SETTINGS)
                }
            }
            REQUEST_STREAM_CACHE_DIR -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                streamCacheFolderUri = uri.toString()
                saveSettings()
                render(Page.SETTINGS)
            }
            REQUEST_CONVERT -> {
                val uris = mutableListOf<Uri>()
                data.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) uris += clip.getItemAt(index).uri
                }
                data.data?.let { uris += it }
                uris.distinct().forEach { persistUriPermission(it, data.flags) }
                convertNcmFiles(uris.distinct())
            }
            REQUEST_CONFIG_EXPORT -> data.data?.let { uri ->
                exportConfigTo(uri)
            }
            REQUEST_CONFIG_IMPORT -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                importConfigFrom(uri)
            }
        }
    }

    override fun onPrepared(track: Track) {
        playbackPositionMs = 0L
        playbackDurationMs = track.durationMs.coerceAtLeast(1L)
        if (pendingRestoreTrackId == track.id) {
            if (pendingRestoreSeekMs > 0L) player.seekTo(pendingRestoreSeekMs)
            if (!pendingRestoreShouldPlay) player.pause()
            playbackPositionMs = pendingRestoreSeekMs.coerceAtLeast(0L)
            pendingRestoreTrackId = null
            pendingRestoreSeekMs = -1L
        }
        updateNowPlayingViews(track)
        updateFloatingLyrics()
        broadcastPlaybackState("com.android.music.metachanged")
    }

    override fun onProgress(positionMs: Long, durationMs: Long) {
        playbackPositionMs = positionMs
        playbackDurationMs = durationMs.coerceAtLeast(1L)
        if (!isUserSeeking) {
            updateSeek(miniSeek, miniTime)
            updateSeek(nowPageSeek, nowPageTime)
        }
        updatePlaybackNotification()
        updateMediaSessionState()
        updateLyricView(player.currentTrack)
        updateFloatingLyrics()
        savePlaybackState()
    }

    override fun onCompleted() {
        if (playbackMode == PlaybackMode.REPEAT_ONE && currentIndex in currentQueue.indices) {
            playTrack(currentQueue[currentIndex], currentQueue, countPlay = false)
        } else {
            playNext()
        }
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        abandonPlaybackFocus()
        miniPlayButton.text = ""
        nowPagePlayButton?.text = "播放"
        updateFloatingLyrics()
        updatePlaybackNotification()
        broadcastPlaybackState("com.android.music.playstatechanged")
    }

    private fun requestPlaybackFocus(): Boolean {
        if (!::audioManager.isInitialized || audioFocusBehavior == AudioFocusBehavior.MIX) {
            player.setVolume(1.0f)
            return true
        }
        val result = audioManager.requestAudioFocus(
            audioFocusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) {
            player.setVolume(1.0f)
        } else {
            showAudioFocusDeniedDialog()
        }
        return granted
    }

    private fun showAudioFocusDeniedDialog() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("无法获取音频焦点")
            .setMessage("系统没有授予 SMP 音频焦点，可能有其他应用正在独占音频。当前播放状态已保持不变。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun abandonPlaybackFocus() {
        if (::audioManager.isInitialized && audioFocusBehavior != AudioFocusBehavior.MIX) {
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        pausedByAudioFocus = false
        if (::player.isInitialized) player.setVolume(1.0f)
    }

    private fun handleAudioFocusChange(change: Int) {
        if (!::player.isInitialized) return
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.setVolume(1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                when (audioFocusBehavior) {
                    AudioFocusBehavior.MIX -> player.setVolume(1.0f)
                    AudioFocusBehavior.DUCK -> player.setVolume(0.25f)
                    AudioFocusBehavior.PAUSE -> {
                        if (player.isPlaying()) pausedByAudioFocus = true
                        player.pause()
                        refreshPlayButtons()
                        updatePlaybackNotification()
                        updateMediaSessionState()
                    }
                }
            }
        }
    }

    private fun buildShell() {
        val frame = FrameLayout(this)
        addBackgroundImage(frame)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(contentOverlayColor())
        }
        root.addView(buildHeader())
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(10)
            }
        }
        root.addView(content)
        miniPlayerPanel = buildMiniPlayer()
        root.addView(miniPlayerPanel)
        root.addView(buildBottomNavigation())
        frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)
    }

    private fun addBackgroundImage(frame: FrameLayout) {
        val uri = backgroundImageUri.takeIf { it.isNotBlank() } ?: return
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = backgroundAlpha.coerceIn(0f, 1f)
        }
        runCatching {
            contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                image.setImageBitmap(BitmapFactory.decodeStream(input))
            }
        }.onSuccess {
            frame.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                headerGradientColors()
            ).apply { cornerRadius = dp(8).toFloat() }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerTitle = TextView(this).apply {
            text = "SMP"
            titleStyle(24f)
        }
        titleRow.addView(headerTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(titleRow)
        statusText = TextView(this).apply {
            text = "准备扫描或导入本地音乐"
            bodyStyle(13f)
        }
        header.addView(statusText)
        return header
    }

    private fun buildMiniPlayer(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { render(Page.NOW_PLAYING) }
        }
        miniThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
        }
        row.addView(miniThumb, LinearLayout.LayoutParams(dp(54), dp(54)))
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        miniTitle = TextView(this).apply {
            text = "未播放"
            titleStyle(16f)
            maxLines = 1
        }
        miniMeta = TextView(this).apply {
            text = "点击歌曲开始播放"
            bodyStyle(12.5f)
            maxLines = 1
        }
        textBox.addView(miniTitle)
        textBox.addView(miniMeta)
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        miniPlayButton = iconActionButton("", R.drawable.ic_play_block) {
            toggleOrPlayFirst()
        }
        row.addView(iconActionButton("", R.drawable.ic_previous_track) { playPreviousOrFirst() }, LinearLayout.LayoutParams(dp(46), dp(42)).apply {
            rightMargin = dp(4)
        })
        row.addView(miniPlayButton, LinearLayout.LayoutParams(dp(46), dp(42)))
        row.addView(iconActionButton("", R.drawable.ic_next_track) { playNextOrFirst() }, LinearLayout.LayoutParams(dp(46), dp(42)).apply {
            leftMargin = dp(4)
        })
        panel.addView(row)
        miniSeek = buildSeekBar()
        miniTime = TextView(this).apply {
            text = "0:00 / 0:00"
            bodyStyle(12f)
        }
        panel.addView(miniSeek)
        panel.addView(miniTime)
        return panel
    }

    private fun buildBottomNavigation(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        listOf(
            Page.NOW_PLAYING to "正在播放",
            Page.PLAYLISTS to "音乐列表",
            Page.SETTINGS to "设置"
        ).forEach { (target, label) ->
            val button = actionButton(label) { render(target) }
            navButtons[target] = button
            row.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            })
        }
        return row
    }

    private fun render(target: Page) {
        if (target != Page.LIBRARY && target != Page.PLAYLISTS) clearSelection()
        page = target
        content.removeAllViews()
        nowPagePlayButton = null
        nowPageSeek = null
        nowPageTime = null
        nowPageLyrics = null
        miniPlayerPanel.visibility = if (target == Page.NOW_PLAYING || target == Page.SETTINGS) View.GONE else View.VISIBLE
        headerTitle.text = if (target == Page.SETTINGS) "SMP" else ""
        navButtons.forEach { (navPage, button) ->
            button.background = ColorDrawable(Color.TRANSPARENT)
            button.setTextColor(if (navPage == target) Palette.TEXT else Palette.MUTED)
        }
        when (target) {
            Page.SCAN -> renderScanPage()
            Page.LIBRARY -> renderLibraryPage()
            Page.PLAYLISTS -> renderPlaylistsPage()
            Page.NOW_PLAYING -> renderNowPlayingPage()
            Page.SETTINGS -> renderSettingsPage()
            Page.STREAMING -> renderStreamingPage()
        }
    }

    private fun renderScanPage() {
        content.addView(sectionTitle("扫描"))
        content.addView(scanAction("自动扫描系统音乐库", "读取系统媒体库、CUE 文件、同名 LRC 歌词，并提取内嵌封面。") {
            if (hasAudioPermission()) scanMediaStore() else requestAudioPermission()
        })
        content.addView(scanAction("选择文件夹扫描", "授权一个音乐目录，递归扫描音频、CUE 和歌词文件。") {
            openTreePicker()
        })
        content.addView(scanAction("手动导入文件", "选择一个或多个音频、CUE 或 LRC 文件，适合补充单曲。") {
            openManualImport()
        })
        content.addView(TextView(this).apply {
            text = "当前音乐库：${allTracks.size} 首；已识别歌词：${allTracks.count { it.hasLyrics }} 首。"
            bodyStyle(14f)
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun renderLibraryPage() {
        content.addView(sectionTitle("音乐库"))
        val toolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolRow.addView(actionButton(if (libraryFiltersExpanded) "收起搜索筛选" else "搜索筛选") {
            libraryFiltersExpanded = !libraryFiltersExpanded
            render(Page.LIBRARY)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        toolRow.addView(iconActionButton("扫描", R.drawable.ic_checkmore) { render(Page.SCAN) }, LinearLayout.LayoutParams(dp(86), dp(44)).apply {
            leftMargin = dp(8)
        })
        content.addView(toolRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })
        val filterBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val search = editText("搜索标题、艺术家、专辑、别名、点评", libraryQuery) { value ->
            libraryQuery = value
            updateLibraryList()
        }
        val tagSpinner = Spinner(this).apply {
            val tags = listOf("全部标签") + store.edits.values.flatMap { it.tags }.distinct().sorted()
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, tags).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(tags.indexOf(selectedTag).takeIf { it >= 0 } ?: 0)
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedTag = if (position == 0) "" else tags[position]
                    updateLibraryList()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        val ratingSpinner = Spinner(this).apply {
            val ratings = listOf("全部评分", "1 星以上", "2 星以上", "3 星以上", "4 星以上", "5 星")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, ratings).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(minRating.coerceIn(0, 5))
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    minRating = position
                    updateLibraryList()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        val sortSpinner = Spinner(this).apply {
            val labels = LibrarySortMode.entries.map { it.label }
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, labels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(librarySortMode.ordinal.coerceIn(labels.indices))
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    librarySortMode = LibrarySortMode.entries[position]
                    updateLibraryList()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(56), 1f))
        searchRow.addView(iconActionButton("扫描", R.drawable.ic_checkmore) { render(Page.SCAN) }, LinearLayout.LayoutParams(dp(82), dp(48)).apply {
            leftMargin = dp(8)
        })
        filterBox.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        filterBox.addView(tagSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
        filterBox.addView(ratingSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
        filterBox.addView(sortSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
        if (libraryFiltersExpanded) content.addView(filterBox)

        if (selectionMode && selectionFromLibrary) content.addView(selectionBar())
        val list = trackListView()
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
        updateLibraryList()
    }

    private fun renderPlaylistsPage() {
        if (openedPlaylist != null) {
            content.addView(sectionTitle(openedPlaylist!!.name))
            content.addView(actionButton("返回播放列表") {
                openedPlaylist = null
                clearSelection()
                render(Page.PLAYLISTS)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
            content.addView(playlistDetails(openedPlaylist!!))
            val tracks = openedPlaylist!!.trackIds.distinct().mapNotNull { id -> allTracks.firstOrNull { it.id == id } }
            visibleTracks = tracks
            trackAdapter.tracks = tracks
            if (selectionMode && selectionPlaylist?.id == openedPlaylist!!.id) content.addView(selectionBar())
            content.addView(trackListView(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
            setStatus("${openedPlaylist!!.name}：${tracks.size} 首")
            return
        }

        content.addView(titleActionRow("播放列表", "+") { showCreatePlaylistDialog() })
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeRow.addView(actionButton("本地音乐") {
            openedPlaylist = null
            clearSelection()
            render(Page.LIBRARY)
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        modeRow.addView(actionButton("流媒体模式") {
            openedPlaylist = null
            clearSelection()
            render(Page.STREAMING)
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(8) })
        content.addView(modeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        val categoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        PlaylistCategory.entries.forEach { category ->
            categoryRow.addView(textTabButton(category.label, playlistCategory == category) {
                playlistCategory = category
                render(Page.PLAYLISTS)
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            })
        }
        attachPlaylistCategorySwipe(categoryRow)
        content.addView(categoryRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        val list = ListView(this).apply {
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = dp(8)
            cacheColorHint = Color.TRANSPARENT
            selector = ColorDrawable(0x2235C2A1)
            adapter = playlistAdapter
            setOnItemClickListener { _, _, position, _ ->
                val playlist = playlistAdapter.getItem(position)
                if (playlist.id == LibraryStore.LOCAL_ID) {
                    openedPlaylist = null
                    render(Page.LIBRARY)
                } else {
                    openedPlaylist = playlist
                    clearSelection()
                    render(Page.PLAYLISTS)
                }
            }
            setOnItemLongClickListener { _, _, position, _ ->
                val playlist = playlistAdapter.getItem(position)
                if (playlist.isLocked) {
                    Toast.makeText(this@MainActivity, "该歌单不可删除", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(playlist.name)
                        .setItems(arrayOf("删除播放列表")) { _, _ ->
                            store.deletePlaylist(playlist.id)
                            render(Page.PLAYLISTS)
                        }
                        .show()
                }
                true
            }
        }
        attachPlaylistCategorySwipe(list)
        playlistAdapter.playlists = categorizedPlaylists()
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
        setStatus("播放列表：${playlistCategory.label}")
    }

    private fun categorizedPlaylists(): List<Playlist> {
        val playlists = store.visiblePlaylists(store.history.map { it.trackId }).filter { it.id != LibraryStore.LOCAL_ID }
        return when (playlistCategory) {
            PlaylistCategory.COMMON -> playlists.filter { it.systemType.isBlank() || it.systemType in setOf("favorites", "history", "ranking") }
            PlaylistCategory.CUE -> playlists.filter { it.systemType == "cue" }
            PlaylistCategory.ALBUM -> playlists.filter { it.systemType == "album" }
        }
    }

    private fun playlistDetails(playlist: Playlist): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
        }
        playlist.trackIds.firstOrNull()
            ?.let { id -> allTracks.firstOrNull { it.id == id } }
            ?.let { loadArtwork(it, cover) }
            ?: cover.setImageResource(R.drawable.ic_cover_placeholder)
        row.addView(cover, LinearLayout.LayoutParams(dp(92), dp(92)))

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(playlist.createdAt))
        val playCount = playlist.trackIds.distinct().sumOf { store.playCount(it) }
        textBox.addView(TextView(this).apply {
            text = playlist.description.ifBlank { "暂无介绍" }
            bodyStyle(13f)
            maxLines = 2
        })
        textBox.addView(TextView(this).apply {
            text = "创建时间：$created"
            bodyStyle(12f)
        })
        textBox.addView(TextView(this).apply {
            text = "总播放次数：$playCount 次"
            bodyStyle(12f)
        })
        textBox.addView(TextView(this).apply {
            text = "标签：" + playlist.tags.joinToString(" / ").ifBlank { "无" }
            bodyStyle(12f, Palette.ACCENT_ALT)
            maxLines = 1
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!playlist.isLocked || playlist.systemType == "album" || playlist.id == LibraryStore.FAVORITES_ID) {
            row.addView(iconActionButton("编辑", R.drawable.ic_checkmore) { showPlaylistMetaDialog(playlist) }, LinearLayout.LayoutParams(dp(72), dp(42)))
        }
        return row
    }

    private fun showPlaylistMetaDialog(playlist: Playlist) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val description = dialogInput("歌单介绍", playlist.description).apply {
            minLines = 3
            maxLines = 5
            setSingleLine(false)
        }
        val tags = dialogInput("歌单标签，用逗号分隔", playlist.tags.joinToString(", "))
        box.addView(description)
        box.addView(tags)
        AlertDialog.Builder(this)
            .setTitle("编辑歌单详情")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                store.savePlaylistMeta(
                    playlist.id,
                    description.text.toString(),
                    tags.text.toString().split(',', '，', '#').map { it.trim() }.filter { it.isNotBlank() }
                )
                openedPlaylist = store.visiblePlaylists(store.history.map { it.trackId }).firstOrNull { it.id == playlist.id }
                render(Page.PLAYLISTS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun renderStreamingPage() {
        content.addView(actionButton("返回音乐列表") {
            streamSource = null
            openedDizzylabAlbum = null
            render(Page.PLAYLISTS)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        content.addView(sectionTitle("流媒体模式"))
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (openedDizzylabAlbum != null) {
            renderDizzylabAlbum(box, openedDizzylabAlbum!!)
        } else if (streamSource == StreamSource.DIZZYLAB && dizzylabAlbums.isNotEmpty()) {
            box.addView(actionButton("返回流媒体来源") {
                streamSource = null
                dizzylabAlbums = emptyList()
                dizzylabQuery = ""
                dizzylabVisibleAlbumCount = 40
                render(Page.STREAMING)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
            val searchRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val search = editText("搜索已购专辑", dizzylabQuery) { value ->
                dizzylabQuery = value
                render(Page.STREAMING)
            }
            searchRow.addView(search, LinearLayout.LayoutParams(0, dp(54), 1f))
            searchRow.addView(actionButton("在线搜索") { loadDizzylabAlbums(dizzylabQuery) }, LinearLayout.LayoutParams(dp(92), dp(48)).apply {
                leftMargin = dp(8)
            })
            box.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8); bottomMargin = dp(8) })
            val filteredAlbums = dizzylabAlbums.filter { album ->
                dizzylabQuery.isBlank() || album.title.contains(dizzylabQuery, ignoreCase = true)
            }
            filteredAlbums.take(dizzylabVisibleAlbumCount).forEach { album ->
                box.addView(streamAlbumCard(album, "DizzyLab 专辑") {
                    loadDizzylabAlbum(album)
                })
            }
            if (filteredAlbums.size > dizzylabVisibleAlbumCount) {
                box.addView(actionButton("加载更多（${dizzylabVisibleAlbumCount}/${filteredAlbums.size}）") {
                    pendingStreamScrollY = scroll.scrollY
                    dizzylabVisibleAlbumCount += 40
                    render(Page.STREAMING)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                })
            }
        } else {
            box.addView(streamSourceCard("DizzyLab", if (dizzylabCookie.isBlank()) "未登录，点击登录或加载账户专辑" else "已登录，点击加载账户专辑", R.drawable.ic_dizzylab) {
                if (dizzylabCookie.isBlank()) showDizzylabLogin() else loadDizzylabAlbums()
            })
            box.addView(streamSourceCard("Navidrome 服务器", "待后续更新", R.drawable.ic_navidrome) {
                Toast.makeText(this, "Navidrome 暂待后续更新", Toast.LENGTH_SHORT).show()
            })
            box.addView(streamSourceCard("DAV 服务器", "待后续更新", R.drawable.ic_dav) {
                Toast.makeText(this, "DAV 暂待后续更新", Toast.LENGTH_SHORT).show()
            })
        }
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (pendingStreamScrollY >= 0) {
            val restoreY = pendingStreamScrollY
            pendingStreamScrollY = -1
            scroll.post { scroll.scrollTo(0, restoreY) }
        }
        setStatus("流媒体模式")
    }

    private fun renderDizzylabAlbum(box: LinearLayout, details: StreamAlbumDetails) {
        box.addView(actionButton("返回 DizzyLab 专辑列表") {
            openedDizzylabAlbum = null
            render(Page.STREAMING)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        box.addView(streamAlbumDetailsCard(details) { showDizzylabAlbumMenu(details) })
        if (details.tracks.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "未在该专辑页识别到可播放的串流音频。"
                bodyStyle(14f)
                setPadding(0, dp(10), 0, dp(10))
            })
            return
        }
        visibleTracks = details.tracks
        trackAdapter.tracks = details.tracks
        val height = (details.tracks.size.coerceAtLeast(1) * 86 + 12).coerceAtMost(24000)
        box.addView(trackListView(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height)).apply { topMargin = dp(8) })
    }

    private fun streamAlbumCard(album: StreamAlbum, body: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            setImageResource(R.drawable.ic_cover_placeholder)
        }
        loadRemoteImage(album.coverUrl, cover)
        row.addView(cover, LinearLayout.LayoutParams(dp(72), dp(72)))
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        textBox.addView(TextView(this).apply {
            text = album.title
            titleStyle(16f)
            maxLines = 2
        })
        textBox.addView(TextView(this).apply {
            text = body
            bodyStyle(13f)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun streamAlbumDetailsCard(details: StreamAlbumDetails, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
                topMargin = dp(8)
            }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            setImageResource(R.drawable.ic_cover_placeholder)
        }
        loadRemoteImage(details.album.coverUrl, cover)
        row.addView(cover, LinearLayout.LayoutParams(dp(108), dp(108)))
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        textBox.addView(TextView(this).apply {
            text = details.album.title
            titleStyle(17f)
            maxLines = 2
        })
        textBox.addView(TextView(this).apply {
            text = listOf(
                "社团：${details.circle.ifBlank { "未知" }}",
                "发布日期：${details.releaseDate.ifBlank { "未知" }}",
                "标签：${details.tags.joinToString(" / ").ifBlank { "无" }}",
                details.description.ifBlank { "暂无简介" }
            ).joinToString("\n")
            bodyStyle(12.5f)
            maxLines = 6
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun streamSourceCard(title: String, body: String, iconRes: Int, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        row.addView(ImageView(this).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }, LinearLayout.LayoutParams(dp(54), dp(54)))
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        textBox.addView(TextView(this).apply {
            text = title
            titleStyle(16f)
        })
        textBox.addView(TextView(this).apply {
            text = body
            bodyStyle(13f)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun streamTextCard(title: String, body: String, onClick: () -> Unit): View {
        return settingCard(title, body, onClick)
    }

    private fun renderNowPlayingPage() {
        val track = player.currentTrack
        content.addView(sectionTitle("正在播放"))
        if (track == null) {
            content.addView(TextView(this).apply {
                text = "还没有正在播放的音乐。"
                bodyStyle(15f)
            })
            return
        }

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
        }
        attachConsumingHorizontalSwipe(art, onLeft = { playNextOrFirst() }, onRight = { playPreviousOrFirst() })
        loadArtwork(track, art)
        box.addView(art, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)))
        box.addView(TextView(this).apply {
            text = displayTitle(track)
            titleStyle(22f)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
        })
        box.addView(TextView(this).apply {
            text = listOf(track.displayArtist, track.displayAlbum).joinToString(" · ")
            bodyStyle(14f)
            gravity = Gravity.CENTER
        })

        nowPageSeek = buildSeekBar()
        nowPageTime = TextView(this).apply {
            bodyStyle(12f)
            gravity = Gravity.CENTER
        }
        box.addView(nowPageSeek)
        box.addView(nowPageTime)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(iconActionButton("上一首", R.drawable.ic_previous_track) { playPrevious() }, weightedParams())
        nowPagePlayButton = iconActionButton(if (player.isPlaying()) "暂停" else "播放", if (player.isPlaying()) R.drawable.ic_pause_circle else R.drawable.ic_play_block) {
            toggleOrPlayFirst()
        }
        controls.addView(nowPagePlayButton, weightedParams())
        controls.addView(iconActionButton("下一首", R.drawable.ic_next_track) { playNext() }, weightedParams())
        controls.addView(iconActionButton("菜单", R.drawable.ic_menu) { showNowPlayingMenu(track) }, weightedParams())
        box.addView(controls)

        box.addView(TextView(this).apply {
            text = "歌词"
            titleStyle(17f)
            setPadding(0, dp(18), 0, dp(8))
        })
        nowPageLyrics = TextView(this).apply {
            text = lyricTextAt(track, playbackPositionMs)
            bodyStyle(15f, Palette.TEXT)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1.0f)
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
        }
        box.addView(nowPageLyrics, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        updateSeek(nowPageSeek, nowPageTime)
        setStatus("正在播放：${displayTitle(track)}")
    }

    private fun renderSettingsPage() {
        content.addView(sectionTitle("设置"))
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(profileHeaderCard())
        box.addView(settingCard("用户帐号", "查看或编辑本地账号，管理流媒体账号凭证") { showAccountDialog() })
        box.addView(settingCard("软件配色", "预设主题色或调色盘自定义颜色") { showThemeSettingsDialog() })
        box.addView(settingCard("背景图片", "从本地图库选择界面背景，并调整透明度：${(backgroundAlpha * 100).toInt()}%") {
            showBackgroundSettingsDialog()
        })
        box.addView(settingCard("权限", permissionSummary()) {
            showPermissionSettingsDialog()
        })
        box.addView(settingCard("播放设置", "启动恢复：${if (restorePlaybackOnLaunch) "继续播放" else "保持暂停"}；其他音乐：${audioFocusBehavior.label}") {
            showPlaybackSettingsDialog()
        })
        box.addView(settingCard("扫描设置", "含 .nomedia 的文件夹：${if (skipNoMediaFolders) "跳过" else "扫描"}") {
            showScanSettingsDialog()
        })
        box.addView(settingCard("配置文件", "将当前设置、用户歌单和我喜欢的音乐导出为 JSON，或从 JSON 导入。") {
            showConfigDialog()
        })
        box.addView(settingCard("串流选项", "预加载：$streamPreloadCount 首；下载目录：${streamFolderLabel(streamDownloadFolderUri, "应用默认目录")}；缓存上限：${"%.1f".format(streamCacheLimitGb)} GB") {
            showStreamingSettingsDialog()
        })
        box.addView(settingCard("音乐格式转换", "选择 NCM 文件并转换为 MP3/FLAC。转换核心移植自 NCMConverter4a。") {
            openNcmConvertPicker()
        })
        box.addView(settingCard("软件详情", "版本号、作者、构建说明、软件介绍和 GitHub 仓库") {
            showSoftwareDetailsDialog()
        })
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setStatus("设置")
    }

    private fun showSoftwareDetailsDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        box.addView(settingCard("版本号", "1.4.2"))
        box.addView(settingCard("软件作者", "SuperMite"))
        box.addView(settingCard("构建提醒", "本软件使用 ChatGPT 辅助构建；音乐格式转换功能的 NCM 解密核心参考并移植自 MIT 许可项目 NCMConverter4a（https://github.com/cdb96/NCMConverter4a）。"))
        box.addView(settingCard("软件介绍", readBundledReadme()))
        box.addView(settingCard("GitHub 仓库", "暂未构建仓库"))
        scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle("软件详情")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun profileHeaderCard(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener { showAccountDialog() }
        }
        row.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            if (profileAvatarUri.isBlank()) {
                setImageResource(R.drawable.ic_music_disc)
            } else {
                runCatching {
                    contentResolver.openInputStream(Uri.parse(profileAvatarUri))?.use { input ->
                        setImageBitmap(BitmapFactory.decodeStream(input))
                    }
                }.onFailure { setImageResource(R.drawable.ic_music_disc) }
            }
        }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        textBox.addView(TextView(this).apply {
            text = profileName.ifBlank { "profile" }
            titleStyle(18f)
        })
        textBox.addView(TextView(this).apply {
            text = "本地账号"
            bodyStyle(13f)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun showAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("用户帐号")
            .setItems(arrayOf("本地账号", "流媒体账号凭证")) { _, which ->
                if (which == 0) showLocalProfileDialog() else showStreamingAccountsDialog()
            }
            .show()
    }

    private fun showLocalProfileDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val nameInput = dialogInput("本地账号名称", profileName.ifBlank { "profile" })
        box.addView(nameInput)
        box.addView(actionButton("更改头像") { openAvatarPicker() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
            topMargin = dp(8)
        })
        AlertDialog.Builder(this)
            .setTitle("本地账号")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                profileName = nameInput.text.toString().trim().ifBlank { "profile" }
                saveSettings()
                rebuildShell(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showStreamingAccountsDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        box.addView(streamSourceCard("DizzyLab", if (dizzylabCookie.isBlank()) "未登录" else "已登录", R.drawable.ic_dizzylab) {
            if (dizzylabCookie.isBlank()) {
                showDizzylabLogin()
            } else {
                confirmClearDizzylabCookie()
            }
        })
        box.addView(streamSourceCard("Navidrome 服务器", "待后续更新", R.drawable.ic_navidrome) {
            Toast.makeText(this, "Navidrome 暂待后续更新", Toast.LENGTH_SHORT).show()
        })
        box.addView(streamSourceCard("DAV 服务器", "待后续更新", R.drawable.ic_dav) {
            Toast.makeText(this, "DAV 暂待后续更新", Toast.LENGTH_SHORT).show()
        })
        scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle("流媒体账号凭证")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun confirmClearDizzylabCookie() {
        AlertDialog.Builder(this)
            .setTitle("清除 DizzyLab 登录状态")
            .setMessage("确定要清除已保存的 DizzyLab Cookie 吗？清除后需要重新登录。")
            .setPositiveButton("清除") { _, _ ->
                dizzylabCookie = ""
                dizzylabUserId = ""
                dizzylabAlbums = emptyList()
                openedDizzylabAlbum = null
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                saveSettings()
                render(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showStreamingSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val label = TextView(this).apply {
            text = "预加载音频数量：$streamPreloadCount"
            bodyStyle(15f, Color.DKGRAY)
        }
        val valueLabel = TextView(this).apply {
            text = "$streamPreloadCount 首"
            titleStyle(22f)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
        }
        val slider = SeekBar(this).apply {
            max = 5
            progress = streamPreloadCount.coerceIn(0, 5)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    streamPreloadCount = progress.coerceIn(0, 5)
                    label.text = "预加载音频数量：$streamPreloadCount"
                    valueLabel.text = "$streamPreloadCount 首"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val cacheLimitInput = dialogInput("缓存空间上限（GB）", "%.1f".format(streamCacheLimitGb))
        box.addView(label)
        box.addView(valueLabel)
        box.addView(slider)
        box.addView(TextView(this).apply {
            text = "下载目录：" + streamFolderLabel(streamDownloadFolderUri, "应用默认目录")
            bodyStyle(14f, Color.DKGRAY)
            setPadding(0, dp(8), 0, dp(8))
        })
        box.addView(TextView(this).apply {
            text = "缓存目录：" + streamFolderLabel(streamCacheFolderUri, "Android 数据目录")
            bodyStyle(14f, Color.DKGRAY)
            setPadding(0, dp(4), 0, dp(8))
        })
        box.addView(cacheLimitInput)
        AlertDialog.Builder(this)
            .setTitle("串流选项")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                streamCacheLimitGb = cacheLimitInput.text.toString().toFloatOrNull()?.coerceAtLeast(0.1f) ?: streamCacheLimitGb
                saveSettings()
                enforceStreamCacheLimit()
                render(Page.SETTINGS)
            }
            .setNeutralButton("选择下载文件夹") { _, _ ->
                streamCacheLimitGb = cacheLimitInput.text.toString().toFloatOrNull()?.coerceAtLeast(0.1f) ?: streamCacheLimitGb
                saveSettings()
                openStreamDownloadFolderPicker()
            }
            .setNegativeButton("选择缓存文件夹") { _, _ ->
                streamCacheLimitGb = cacheLimitInput.text.toString().toFloatOrNull()?.coerceAtLeast(0.1f) ?: streamCacheLimitGb
                saveSettings()
                openStreamCacheFolderPicker()
            }
            .show()
    }

    private fun showThemeSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        val preview = TextView(this).apply {
            text = "当前主题色"
            titleStyle(15f)
            gravity = Gravity.CENTER
            background = panelDrawable(themeColor, 8, this@MainActivity)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        var red = Color.red(themeColor)
        var green = Color.green(themeColor)
        var blue = Color.blue(themeColor)
        fun applyPreview() {
            themeColor = Color.rgb(red, green, blue)
            preview.background = panelDrawable(themeColor, 8, this@MainActivity)
        }
        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(
            "青绿" to 0xFF35C2A1.toInt(),
            "琥珀" to 0xFFF2B84B.toInt(),
            "蓝色" to 0xFF4A90E2.toInt(),
            "紫色" to 0xFF9B6CFF.toInt()
        ).forEach { (label, color) ->
            presets.addView(actionButton(label) {
                red = Color.red(color)
                green = Color.green(color)
                blue = Color.blue(color)
                applyPreview()
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            })
        }
        fun colorSlider(label: String, initial: Int, onChanged: (Int) -> Unit): View {
            val boxRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            val text = TextView(this).apply {
                this.text = "$label: $initial"
                bodyStyle(13f)
            }
            val slider = SeekBar(this).apply {
                max = 255
                progress = initial
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        onChanged(progress)
                        text.text = "$label: $progress"
                        applyPreview()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
            boxRow.addView(text)
            boxRow.addView(slider)
            return boxRow
        }
        box.addView(preview)
        box.addView(presets, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) })
        box.addView(colorSlider("R", red) { red = it })
        box.addView(colorSlider("G", green) { green = it })
        box.addView(colorSlider("B", blue) { blue = it })
        AlertDialog.Builder(this)
            .setTitle("软件配色")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                saveSettings()
                rebuildShell(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNotificationSettingsDialog() {
        val labels = arrayOf("状态栏播放开关", "锁屏显示播放控件")
        val checked = booleanArrayOf(notificationEnabled, lockscreenNotificationEnabled)
        AlertDialog.Builder(this)
            .setTitle("通知")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (which == 0) notificationEnabled = isChecked else lockscreenNotificationEnabled = isChecked
                saveSettings()
                if ((notificationEnabled || lockscreenNotificationEnabled) && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                }
                updatePlaybackNotification()
            }
            .setPositiveButton("完成") { _, _ -> render(Page.SETTINGS) }
            .show()
    }

    private fun showFloatingLyricsSettingsDialog() {
        val labels = arrayOf("开启悬浮歌词")
        val checked = booleanArrayOf(floatingLyricsEnabled)
        AlertDialog.Builder(this)
            .setTitle("悬浮歌词")
            .setMultiChoiceItems(labels, checked) { _, _, isChecked ->
                floatingLyricsEnabled = isChecked
                saveSettings()
                if (isChecked && !hasOverlayPermission()) openOverlaySettings()
                updateFloatingLyrics()
            }
            .setPositiveButton("完成") { _, _ -> render(Page.SETTINGS) }
            .show()
    }

    private fun permissionSummary(): String {
        return listOf(
            "媒体：${if (hasAudioPermission()) "已授权" else "未授权"}",
            "通知：${if (notificationEnabled && hasNotificationPermission()) "开启" else "关闭或未授权"}",
            "悬浮歌词：${if (floatingLyricsEnabled && hasOverlayPermission()) "开启" else "关闭或未授权"}",
            "文件管理：${if (hasAllFilesAccess()) "已开启" else "未开启"}"
        ).joinToString("；")
    }

    private fun showPermissionSettingsDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        box.addView(settingCard(
            "媒体专用权限",
            if (hasAudioPermission()) "已授权。默认使用 Android 的音乐媒体读取权限扫描本地音乐。" else "未授权。用于扫描系统音乐库，推荐保持使用这一权限。"
        ) {
            if (hasAudioPermission()) {
                Toast.makeText(this, "媒体专用权限已授权", Toast.LENGTH_SHORT).show()
            } else {
                requestAudioPermission()
            }
        })
        box.addView(settingCard(
            "通知与状态栏播放",
            "状态栏播放：${if (notificationEnabled) "开启" else "关闭"}；锁屏通知：${if (lockscreenNotificationEnabled) "开启" else "关闭"}；系统通知权限：${if (hasNotificationPermission()) "已授权" else "未授权"}。"
        ) {
            showNotificationSettingsDialog()
        })
        box.addView(settingCard(
            "悬浮歌词",
            "开关：${if (floatingLyricsEnabled) "开启" else "关闭"}；悬浮窗权限：${if (hasOverlayPermission()) "已授权" else "未授权"}。开启后会在屏幕顶部显示歌名、歌手或当前歌词。"
        ) {
            showFloatingLyricsSettingsDialog()
        })
        box.addView(settingCard(
            "下载文件夹权限",
            "当前下载目录：${streamFolderLabel(streamDownloadFolderUri, "应用默认目录")}。可重新选择文件夹，系统拒绝时会回退默认目录。"
        ) {
            openStreamDownloadFolderPicker()
        })
        box.addView(settingCard(
            "文件管理权限",
            if (hasAllFilesAccess()) {
                "已开启。下载和删除源文件时可获得更长久的文件访问能力。"
            } else {
                "未开启。默认不需要；如经常遇到下载目录授权失效或删除源文件失败，可在系统设置中开启所有文件访问权限。"
            }
        ) {
            showAllFilesAccessDialog()
        })
        scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle("软件权限")
            .setView(scroll)
            .setPositiveButton("关闭") { _, _ -> render(Page.SETTINGS) }
            .show()
    }

    private fun showAllFilesAccessDialog() {
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(this, "当前 Android 版本不需要单独开启所有文件访问权限。", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("文件管理权限")
            .setMessage("SMP 默认使用音乐媒体专用权限。开启所有文件访问权限后，下载与删除源文件的授权更稳定，但该权限范围更大，请只在需要时开启。")
            .setPositiveButton(if (hasAllFilesAccess()) "查看系统设置" else "前往开启") { _, _ ->
                openAllFilesAccessSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPlaybackSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val restoreLabels = arrayOf("再次打开后保持暂停", "再次打开后继续播放")
        val focusLabels = AudioFocusBehavior.entries.map { it.label }.toTypedArray()
        var selectedRestore = if (restorePlaybackOnLaunch) 1 else 0
        var selectedFocus = audioFocusBehavior.ordinal
        box.addView(TextView(this).apply {
            text = "退出前未暂停播放时"
            titleStyle(15f)
            setPadding(0, 0, 0, dp(6))
        })
        val restoreSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, restoreLabels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selectedRestore)
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedRestore = position
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        box.addView(restoreSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        box.addView(TextView(this).apply {
            text = "设备播放其他音乐时"
            titleStyle(15f)
            setPadding(0, dp(12), 0, dp(6))
        })
        val focusSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, focusLabels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selectedFocus)
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedFocus = position
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        box.addView(focusSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        box.addView(actionButton("音效调整器：$equalizerPreset") { showEqualizerDialog() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(12)
        })
        AlertDialog.Builder(this)
            .setTitle("播放设置")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                restorePlaybackOnLaunch = selectedRestore == 1
                audioFocusBehavior = AudioFocusBehavior.entries.getOrElse(selectedFocus) { AudioFocusBehavior.PAUSE }
                if (audioFocusBehavior == AudioFocusBehavior.MIX || !player.isPlaying()) abandonPlaybackFocus()
                saveSettings()
                render(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEqualizerDialog() {
        val frequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val names = equalizerPresets.keys.toList()
        var selectedPreset = names.indexOf(equalizerPreset).takeIf { it >= 0 } ?: 0
        val working = (equalizerPresets[names[selectedPreset]] ?: List(5) { 0 }).toMutableList()
        val presetSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, names).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selectedPreset)
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
        }
        box.addView(presetSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        val labels = mutableListOf<TextView>()
        frequencies.forEachIndexed { index, frequency ->
            val label = TextView(this).apply {
                text = "$frequency：${working[index]} mB"
                bodyStyle(13f)
            }
            labels += label
            box.addView(label)
            box.addView(SeekBar(this).apply {
                max = 3000
                progress = working[index] + 1500
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        working[index] = (progress - 1500).coerceIn(-1500, 1500)
                        label.text = "$frequency：${working[index]} mB"
                        player.setEqualizerLevels(working)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            })
        }
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPreset = position
                val values = equalizerPresets[names[position]] ?: List(5) { 0 }
                values.forEachIndexed { index, value ->
                    working[index] = value
                    labels.getOrNull(index)?.text = "${frequencies[index]}：$value mB"
                }
                player.setEqualizerLevels(working)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        AlertDialog.Builder(this)
            .setTitle("音效调整器")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                equalizerPreset = names[selectedPreset]
                if (equalizerPreset != "默认") equalizerPresets[equalizerPreset] = working.toList()
                equalizerLevels = working
                player.setEqualizerLevels(equalizerLevels)
                saveSettings()
            }
            .setNeutralButton("保存为预设") { _, _ ->
                showSaveEqualizerPresetDialog(working)
            }
            .setNegativeButton("取消") { _, _ ->
                player.setEqualizerLevels(equalizerLevels)
            }
            .show()
    }

    private fun showSaveEqualizerPresetDialog(levels: List<Int>) {
        val input = dialogInput("预设名称", "自定义预设")
        AlertDialog.Builder(this)
            .setTitle("保存音效预设")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "自定义预设" }
                if (name == "默认") {
                    Toast.makeText(this, "默认预设不可覆盖", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                equalizerPresets[name] = levels.take(5)
                equalizerPreset = name
                equalizerLevels = levels.take(5).toMutableList()
                player.setEqualizerLevels(equalizerLevels)
                saveSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showScanSettingsDialog() {
        val labels = arrayOf("扫描时跳过含 .nomedia 的文件夹")
        val checked = booleanArrayOf(skipNoMediaFolders)
        AlertDialog.Builder(this)
            .setTitle("扫描设置")
            .setMultiChoiceItems(labels, checked) { _, _, isChecked ->
                skipNoMediaFolders = isChecked
                scanner.skipNoMediaFolders = isChecked
                saveSettings()
            }
            .setPositiveButton("完成") { _, _ -> render(Page.SETTINGS) }
            .show()
    }

    private fun showConfigDialog() {
        AlertDialog.Builder(this)
            .setTitle("配置文件")
            .setItems(arrayOf("导出 JSON 配置", "导入 JSON 配置")) { _, which ->
                if (which == 0) openConfigExport() else openConfigImport()
            }
            .show()
    }

    private fun showBackgroundSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        val label = TextView(this).apply {
            text = "背景透明度：${(backgroundAlpha * 100).toInt()}%"
            bodyStyle(15f, Color.DKGRAY)
        }
        val slider = SeekBar(this).apply {
            max = 100
            progress = (backgroundAlpha * 100).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    backgroundAlpha = progress / 100f
                    label.text = "背景透明度：$progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        box.addView(TextView(this).apply {
            text = if (backgroundImageUri.isBlank()) "当前未设置背景图片" else "已设置背景图片"
            bodyStyle(14f, Color.DKGRAY)
        })
        box.addView(label)
        box.addView(slider)
        AlertDialog.Builder(this)
            .setTitle("背景图片")
            .setView(box)
            .setPositiveButton("选择图片") { _, _ ->
                saveSettings()
                openBackgroundPicker()
            }
            .setNeutralButton("移除图片") { _, _ ->
                backgroundImageUri = ""
                saveSettings()
                rebuildShell(Page.SETTINGS)
            }
            .setNegativeButton("完成") { _, _ ->
                saveSettings()
                rebuildShell(Page.SETTINGS)
            }
            .show()
    }

    private fun scanMediaStore() {
        setStatus("正在扫描系统音乐库、CUE、歌词和封面...")
        Thread {
            val scanned = runCatching { scanner.scanMediaStore() }.getOrElse {
                runOnUiThread { Toast.makeText(this, it.message ?: "扫描失败", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            runOnUiThread { mergeScannedTracks(scanned, replace = true, source = "系统扫描") }
        }.start()
    }

    private fun showFirstLaunchDialogIfNeeded() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("firstLaunchPromptDone", false)) return
        prefs.edit().putBoolean("firstLaunchPromptDone", true).apply()
        AlertDialog.Builder(this)
            .setTitle("初始化 SMP")
            .setMessage("首次打开可以立即扫描本地音乐，并开启状态栏播放控件。之后可在“音乐列表 > 本地音乐 > 搜索筛选右侧的扫描”进入扫描界面。")
            .setPositiveButton("扫描并开启通知") { _, _ ->
                notificationEnabled = true
                saveSettings()
                requestNotificationPermissionIfNeeded()
                autoScanOnceIfNeeded()
            }
            .setNegativeButton("仅扫描音乐") { _, _ ->
                autoScanOnceIfNeeded()
            }
            .setNeutralButton("稍后") { _, _ ->
                setStatus("可在音乐库搜索筛选右侧进入扫描界面。")
            }
            .show()
    }

    private fun autoScanOnceIfNeeded() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("autoScanOnceDone", false)) return
        prefs.edit().putBoolean("autoScanOnceDone", true).apply()
        if (hasAudioPermission()) {
            scanMediaStore()
        } else {
            requestAudioPermission()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun scanTree(uri: Uri) {
        setStatus("正在扫描授权文件夹...")
        Thread {
            val scanned = runCatching { scanner.scanDocumentTree(uri) }.getOrElse {
                runOnUiThread { Toast.makeText(this, it.message ?: "文件夹扫描失败", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            runOnUiThread { mergeScannedTracks(scanned, replace = false, source = "文件夹扫描") }
        }.start()
    }

    private fun scanImportedFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        setStatus("正在导入 ${uris.size} 个文件...")
        Thread {
            val scanned = runCatching { scanner.scanDocumentFiles(uris) }.getOrElse {
                runOnUiThread { Toast.makeText(this, it.message ?: "导入失败", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            runOnUiThread { mergeScannedTracks(scanned, replace = false, source = "手动导入") }
        }.start()
    }

    private fun mergeScannedTracks(scanned: List<Track>, replace: Boolean, source: String) {
        allTracks = (if (replace) scanned else allTracks + scanned)
            .distinctBy { it.id }
            .sortedWith(MusicScanner.trackComparator)
        store.saveTracks(allTracks)
        store.createAlbumPlaylistsIfNeeded(allTracks)
        store.createCuePlaylistsFromTracks(allTracks)
        setStatus("$source 完成：${allTracks.size} 首，歌词 ${allTracks.count { it.hasLyrics }} 首。")
        if (page == Page.LIBRARY) render(Page.LIBRARY)
        if (page == Page.PLAYLISTS) render(Page.PLAYLISTS)
        if (page == Page.SCAN) render(Page.SCAN)
    }

    private fun updateLibraryList() {
        if (page != Page.LIBRARY) return
        visibleTracks = filterTracks(allTracks)
        trackAdapter.tracks = visibleTracks
        setStatus("音乐库：${visibleTracks.size} / ${allTracks.size} 首，排序：${librarySortMode.label}。")
    }

    private fun filterTracks(tracks: List<Track>): List<Track> {
        val query = libraryQuery.trim().lowercase(Locale.ROOT)
        return tracks.filter { track ->
            val edit = store.edits[track.id] ?: TrackEdit()
            val haystack = listOf(
                track.displayTitle,
                track.displayArtist,
                track.displayAlbum,
                edit.alias,
                edit.comment,
                edit.tags.joinToString(" ")
            ).joinToString(" ").lowercase(Locale.ROOT)
            val matchesQuery = query.isBlank() || haystack.contains(query)
            val matchesTag = selectedTag.isBlank() || edit.tags.any { it.equals(selectedTag, ignoreCase = true) }
            val matchesRating = minRating == 0 || edit.rating >= minRating
            matchesQuery && matchesTag && matchesRating
        }.let { sortTracks(it) }
    }

    private fun sortTracks(tracks: List<Track>): List<Track> {
        val comparator = when (librarySortMode) {
            LibrarySortMode.AZ -> MusicScanner.trackComparator
            LibrarySortMode.ZA -> MusicScanner.trackComparator.reversed()
            LibrarySortMode.ALBUM -> Comparator<Track> { left, right ->
                val albumCompare = String.CASE_INSENSITIVE_ORDER.compare(left.displayAlbum, right.displayAlbum)
                if (albumCompare != 0) albumCompare else compareValuesBy(left, right, { it.trackNumber }, { it.displayTitle.lowercase(Locale.ROOT) })
            }
            LibrarySortMode.DURATION -> compareBy<Track> { it.durationMs }.then(MusicScanner.trackComparator)
            LibrarySortMode.YEAR -> compareBy<Track> { if (it.year <= 0) Int.MAX_VALUE else it.year }.then(MusicScanner.trackComparator)
            LibrarySortMode.ARTIST -> Comparator<Track> { left, right ->
                val artistCompare = String.CASE_INSENSITIVE_ORDER.compare(left.displayArtist, right.displayArtist)
                if (artistCompare != 0) artistCompare else MusicScanner.trackComparator.compare(left, right)
            }
        }
        return tracks.sortedWith(comparator)
    }

    private fun trackListView(): ListView {
        trackAdapter.selectionMode = selectionMode
        trackAdapter.selectedIds = selectedTrackIds.toSet()
        return ListView(this).apply {
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = dp(8)
            cacheColorHint = Color.TRANSPARENT
            selector = ColorDrawable(0x2235C2A1)
            adapter = trackAdapter
            setOnItemClickListener { _, _, position, _ ->
                val track = trackAdapter.getItem(position)
                if (selectionMode) toggleSelection(track) else playTrack(track, queueForCurrentView())
            }
            setOnItemLongClickListener { parent, _, position, _ ->
                if (page == Page.STREAMING) {
                    showTrackActions(trackAdapter.getItem(position))
                } else {
                    val list = parent as? ListView
                    enterSelectionMode(
                        track = trackAdapter.getItem(position),
                        firstVisible = list?.firstVisiblePosition ?: position,
                        topOffset = list?.getChildAt(0)?.top ?: 0
                    )
                }
                true
            }
            if (pendingListFirstVisible >= 0) {
                val first = pendingListFirstVisible
                val offset = pendingListTopOffset
                pendingListFirstVisible = -1
                pendingListTopOffset = 0
                post { setSelectionFromTop(first, offset) }
            }
        }
    }

    private fun enterSelectionMode(track: Track, firstVisible: Int = -1, topOffset: Int = 0) {
        selectionMode = true
        selectionFromLibrary = page == Page.LIBRARY
        selectionPlaylist = if (page == Page.PLAYLISTS) openedPlaylist else null
        selectedTrackIds.clear()
        selectedTrackIds.add(track.id)
        pendingListFirstVisible = firstVisible
        pendingListTopOffset = topOffset
        renderCurrentListPage()
    }

    private fun toggleSelection(track: Track) {
        if (track.id in selectedTrackIds) selectedTrackIds.remove(track.id) else selectedTrackIds.add(track.id)
        if (selectedTrackIds.isEmpty()) {
            clearSelection()
            renderCurrentListPage()
        } else {
            updateSelectionViews()
        }
    }

    private fun clearSelection() {
        selectionMode = false
        selectionFromLibrary = false
        selectionPlaylist = null
        selectedTrackIds.clear()
        selectionCountText = null
        if (::trackAdapter.isInitialized) {
            trackAdapter.selectionMode = false
            trackAdapter.selectedIds = emptySet()
        }
    }

    private fun renderCurrentListPage() {
        if (selectionFromLibrary || page == Page.LIBRARY) {
            render(Page.LIBRARY)
        } else {
            render(Page.PLAYLISTS)
        }
    }

    private fun selectionBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        selectionCountText = TextView(this).apply {
            text = "已选 ${selectedTrackIds.size} 首"
            titleStyle(15f)
        }
        bar.addView(selectionCountText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(actionButton("收藏") { favoriteSelectedTracks() }, LinearLayout.LayoutParams(dp(62), dp(38)))
        bar.addView(actionButton(if (selectionFromLibrary) "加入" else "移动") { showAddSelectedToPlaylistDialog() }, LinearLayout.LayoutParams(dp(62), dp(38)).apply { leftMargin = dp(6) })
        bar.addView(actionButton("删除") { confirmDeleteSelectedTracks() }, LinearLayout.LayoutParams(dp(62), dp(38)).apply { leftMargin = dp(6) })
        bar.addView(actionButton("取消") {
            clearSelection()
            renderCurrentListPage()
        }, LinearLayout.LayoutParams(dp(62), dp(38)).apply { leftMargin = dp(6) })
        return bar
    }

    private fun updateSelectionViews() {
        selectionCountText?.text = "已选 ${selectedTrackIds.size} 首"
        trackAdapter.selectionMode = selectionMode
        trackAdapter.selectedIds = selectedTrackIds.toSet()
        trackAdapter.notifyDataSetChanged()
    }

    private fun favoriteSelectedTracks() {
        selectedTrackIds.toList().asReversed().forEach { store.setFavorite(it, true) }
        Toast.makeText(this, "已加入我喜欢的音乐", Toast.LENGTH_SHORT).show()
        clearSelection()
        renderCurrentListPage()
    }

    private fun showAddSelectedToPlaylistDialog() {
        val ids = selectedTrackIds.toList()
        val playlists = store.visiblePlaylists(store.history.map { it.trackId })
            .filter { it.id != LibraryStore.HISTORY_ID && it.id != LibraryStore.LOCAL_ID }
            .filter { selectionFromLibrary || it.id != selectionPlaylist?.id }
        val names = playlists.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (selectionFromLibrary) "加入播放列表" else "移动到播放列表")
            .setItems(names) { _, which ->
                val playlist = playlists[which]
                if (playlist.id == LibraryStore.FAVORITES_ID) {
                    ids.asReversed().forEach { id -> store.setFavorite(id, true) }
                } else {
                    store.addToPlaylist(playlist.id, ids)
                }
                if (!selectionFromLibrary) {
                    selectionPlaylist?.let { store.removeTracksFromPlaylist(it.id, ids) }
                    openedPlaylist = selectionPlaylist?.let { current ->
                        store.visiblePlaylists(store.history.map { it.trackId }).firstOrNull { it.id == current.id }
                    }
                }
                Toast.makeText(this, if (selectionFromLibrary) "已加入 ${playlist.name}" else "已移动到 ${playlist.name}", Toast.LENGTH_SHORT).show()
                clearSelection()
                renderCurrentListPage()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteSelectedTracks() {
        val count = selectedTrackIds.size
        val message = if (selectionFromLibrary) {
            "将尝试删除 $count 首音乐的源文件，并从音乐库和歌单中移除记录。此操作不可撤销，是否继续？"
        } else {
            "将从当前歌单移除 $count 首音乐，不会删除源文件。是否继续？"
        }
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage(message)
            .setPositiveButton("删除") { _, _ -> deleteSelectedTracks() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteSelectedTracks() {
        val ids = selectedTrackIds.toSet()
        if (selectionFromLibrary) {
            val targets = allTracks.filter { it.id in ids }
            var deleted = 0
            targets.forEach { track ->
                val removed = runCatching {
                    val uri = Uri.parse(track.uri)
                    when (uri.scheme) {
                        "content" -> contentResolver.delete(uri, null, null) > 0 || deleteByFilePathIfAllowed(track)
                        "file" -> File(uri.path.orEmpty()).delete()
                        else -> deleteByFilePathIfAllowed(track)
                    }
                }.getOrDefault(false)
                if (removed) deleted++
            }
            allTracks = allTracks.filterNot { it.id in ids }
            store.saveTracks(allTracks)
            store.removeTracksEverywhere(ids)
            Toast.makeText(this, "已移除 ${countDeletedLabel(deleted, targets.size)}", Toast.LENGTH_SHORT).show()
        } else {
            selectionPlaylist?.let { playlist -> store.removeTracksFromPlaylist(playlist.id, ids) }
            openedPlaylist = selectionPlaylist?.let { playlist ->
                store.visiblePlaylists(store.history.map { it.trackId }).firstOrNull { it.id == playlist.id }
            }
            Toast.makeText(this, "已从歌单移除", Toast.LENGTH_SHORT).show()
        }
        clearSelection()
        renderCurrentListPage()
    }

    private fun countDeletedLabel(deleted: Int, total: Int): String = "$deleted / $total 首"

    private fun deleteByFilePathIfAllowed(track: Track): Boolean {
        if (!hasAllFilesAccess()) return false
        return listOf(track.sourcePath, Uri.parse(track.uri).path.orEmpty())
            .filter { it.isNotBlank() }
            .map { File(it) }
            .firstOrNull { it.exists() && it.isFile }
            ?.delete() == true
    }

    private fun playTrack(track: Track, queue: List<Track>, countPlay: Boolean = true) {
        if (!restorePreparingPaused && !requestPlaybackFocus()) return
        val baseQueue = queue.ifEmpty { listOf(track) }
        val nextQueue = if (playbackMode == PlaybackMode.PSEUDO_RANDOM) {
            if (shouldCreatePseudoQueue(baseQueue)) createPseudoQueue(baseQueue, track.id) else currentQueue
        } else {
            baseQueue
        }
        val queueChanged = currentQueue.map { it.id } != nextQueue.map { it.id }
        currentQueue = nextQueue
        currentIndex = currentQueue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        if (playbackMode != PlaybackMode.PSEUDO_RANDOM && queueChanged) clearPseudoShuffleState()
        if (countPlay) store.addHistory(track.id)
        pausedByAudioFocus = false
        player.play(track, startWhenReady = !restorePreparingPaused)
        restorePreparingPaused = false
        if (page == Page.PLAYLISTS && openedPlaylist?.id == LibraryStore.HISTORY_ID) render(Page.PLAYLISTS)
    }

    private fun toggleOrPlayFirst() {
        if (player.currentTrack == null) {
            val libraryQueue = allTracks.sortedWith(MusicScanner.trackComparator)
            val firstTrack = libraryQueue.firstOrNull()
            if (firstTrack == null) {
                Toast.makeText(this, "音乐库为空，请先扫描或导入音乐", Toast.LENGTH_SHORT).show()
            } else {
                playTrack(firstTrack, libraryQueue)
            }
            return
        }
        if (player.isPlaying()) {
            abandonPlaybackFocus()
            player.pause()
        } else if (requestPlaybackFocus()) {
            pausedByAudioFocus = false
            player.resume()
        }
        refreshPlayButtons()
    }

    private fun playPreviousOrFirst() {
        if (currentQueue.isEmpty()) toggleOrPlayFirst() else playPrevious()
    }

    private fun playNextOrFirst() {
        if (currentQueue.isEmpty()) toggleOrPlayFirst() else playNext()
    }

    private fun playNext() {
        if (currentQueue.isEmpty()) return
        currentIndex = when (playbackMode) {
            PlaybackMode.TRUE_RANDOM -> {
                if (currentQueue.size <= 1) 0 else {
                    var next = Random.nextInt(currentQueue.size)
                    if (next == currentIndex) next = (next + 1) % currentQueue.size
                    next
                }
            }
            PlaybackMode.PSEUDO_RANDOM -> nextPseudoShuffleIndex()
            PlaybackMode.REPEAT_ONE -> currentIndex.coerceIn(0, currentQueue.lastIndex)
            PlaybackMode.SEQUENTIAL -> if (currentIndex + 1 >= currentQueue.size) 0 else currentIndex + 1
        }
        playTrack(currentQueue[currentIndex], currentQueue)
    }

    private fun playPrevious() {
        if (currentQueue.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) currentQueue.lastIndex else currentIndex - 1
        playTrack(currentQueue[currentIndex], currentQueue)
    }

    private fun shouldCreatePseudoQueue(queue: List<Track>): Boolean {
        if (currentQueue.isEmpty()) return true
        val currentIds = currentQueue.map { it.id }.toSet()
        val nextIds = queue.map { it.id }.toSet()
        return currentIds != nextIds || currentQueue.size != queue.size
    }

    private fun createPseudoQueue(queue: List<Track>, firstTrackId: String): List<Track> {
        val shuffled = queue.shuffled().toMutableList()
        val selectedIndex = shuffled.indexOfFirst { it.id == firstTrackId }
        if (selectedIndex > 0) {
            val selected = shuffled.removeAt(selectedIndex)
            shuffled.add(0, selected)
        }
        return shuffled
    }

    private fun applyPseudoShuffleToCurrentQueue() {
        if (currentQueue.isEmpty()) return
        val trackId = player.currentTrack?.id ?: currentQueue.getOrNull(currentIndex)?.id ?: return
        currentQueue = createPseudoQueue(currentQueue, trackId)
        currentIndex = currentQueue.indexOfFirst { it.id == trackId }.takeIf { it >= 0 } ?: 0
        clearPseudoShuffleState()
    }

    private fun clearPseudoShuffleState() {
        pseudoShuffleOrder.clear()
        pseudoShufflePosition = -1
    }

    private fun nextPseudoShuffleIndex(): Int {
        if (currentQueue.isEmpty()) return -1
        return if (currentIndex + 1 >= currentQueue.size) 0 else currentIndex + 1
    }

    private fun previousPseudoShuffleIndex(): Int {
        if (currentQueue.isEmpty()) return -1
        return if (currentIndex - 1 < 0) currentQueue.lastIndex else currentIndex - 1
    }

    private fun showTrackActions(track: Track) {
        if (track.id.startsWith("stream:dizzylab:")) {
            AlertDialog.Builder(this)
                .setTitle(displayTitle(track))
                .setItems(arrayOf("播放", "下载", "歌曲详细信息")) { _, which ->
                    when (which) {
                        0 -> playTrack(track, queueForCurrentView())
                        1 -> downloadStreamTrack(track)
                        2 -> showTrackDetailsDialog(track)
                    }
                }
                .show()
            return
        }
        val playlist = openedPlaylist
        val favoriteText = if (store.isFavorite(track.id)) "取消喜欢" else "加入我喜欢的音乐"
        val actions = mutableListOf("播放", favoriteText, "加入播放列表", "编辑音乐信息")
        if (playlist != null && !playlist.isLocked) actions.add("从当前播放列表移除")
        AlertDialog.Builder(this)
            .setTitle(displayTitle(track))
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "播放" -> playTrack(track, queueForCurrentView())
                    "加入我喜欢的音乐" -> {
                        store.setFavorite(track.id, true)
                        renderIfPlaylistChanged()
                    }
                    "取消喜欢" -> {
                        store.setFavorite(track.id, false)
                        renderIfPlaylistChanged()
                    }
                    "加入播放列表" -> showAddToPlaylistDialog(track)
                    "编辑音乐信息" -> showEditDialog(track)
                    "从当前播放列表移除" -> {
                        store.removeFromPlaylist(playlist!!.id, track.id)
                        openedPlaylist = store.visiblePlaylists(store.history.map { it.trackId }).firstOrNull { it.id == playlist.id }
                        render(Page.PLAYLISTS)
                    }
                }
            }
            .show()
    }

    private fun renderIfPlaylistChanged() {
        if (page == Page.PLAYLISTS) render(Page.PLAYLISTS)
    }

    private fun renderIfLibraryVisible() {
        if (page == Page.LIBRARY || page == Page.PLAYLISTS || page == Page.STREAMING) render(page)
    }

    private fun queueForCurrentView(): List<Track> {
        return when {
            page == Page.LIBRARY -> sortTracks(allTracks)
            page == Page.PLAYLISTS && openedPlaylist != null -> visibleTracks
            else -> visibleTracks
        }
    }

    private fun showAddToPlaylistDialog(track: Track) {
        val playlists = store.visiblePlaylists(store.history.map { it.trackId }).filter { it.id != LibraryStore.HISTORY_ID }
        val names = playlists.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("加入播放列表")
            .setItems(names) { _, which ->
                val playlist = playlists[which]
                if (playlist.id == LibraryStore.FAVORITES_ID) store.setFavorite(track.id, true) else store.addToPlaylist(playlist.id, track.id)
                Toast.makeText(this, "已加入 ${playlist.name}", Toast.LENGTH_SHORT).show()
                renderIfPlaylistChanged()
            }
            .setPositiveButton("新建") { _, _ ->
                showCreatePlaylistDialog { playlist ->
                    store.addToPlaylist(playlist.id, track.id)
                    renderIfPlaylistChanged()
                }
            }
            .show()
    }

    private fun showCreatePlaylistDialog(onCreated: ((Playlist) -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "播放列表名称"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        AlertDialog.Builder(this)
            .setTitle("新建播放列表")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val playlist = store.createPlaylist(input.text.toString())
                onCreated?.invoke(playlist)
                openedPlaylist = null
                render(Page.PLAYLISTS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNowPlayingMenu(track: Track) {
        val items = arrayOf(
            "编辑音乐信息",
            "播放模式",
            "播放倍速",
            "正在播放列表",
            "收藏到歌单",
            "音量调节",
            "歌曲详细信息"
        )
        val dialog = AlertDialog.Builder(this).setTitle("播放菜单").create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        items.forEach { item ->
            box.addView(menuItem(item) {
                dialog.dismiss()
                when (item) {
                    "编辑音乐信息" -> showEditDialog(track)
                    "播放模式" -> showPlaybackModeDialog()
                    "播放倍速" -> showSpeedDialog()
                    "正在播放列表" -> showCurrentQueueDialog()
                    "收藏到歌单" -> showAddToPlaylistDialog(track)
                    "音量调节" -> showVolumeDialog()
                    "歌曲详细信息" -> showTrackDetailsDialog(track)
                }
            })
        }
        dialog.setView(box)
        dialog.show()
    }

    private fun showPlaybackModeDialog() {
        val labels = PlaybackMode.entries.map { it.label }.toTypedArray()
        val checked = playbackMode.ordinal
        AlertDialog.Builder(this)
            .setTitle("播放模式")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                playbackMode = PlaybackMode.entries[which]
                if (playbackMode == PlaybackMode.PSEUDO_RANDOM) {
                    applyPseudoShuffleToCurrentQueue()
                } else {
                    clearPseudoShuffleState()
                }
                savePlaybackState()
                setStatus("播放模式：${labels[which]}")
                dialog.dismiss()
            }
            .show()
    }

    private fun showSpeedDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val label = TextView(this).apply {
            text = "播放倍速：${"%.2f".format(player.speed())}x"
            bodyStyle(15f, Color.DKGRAY)
        }
        val valueLabel = TextView(this).apply {
            text = "${"%.2f".format(player.speed())}x"
            titleStyle(24f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        val slider = SeekBar(this).apply {
            max = 150
            progress = ((player.speed() - 0.5f) * 100).toInt().coerceIn(0, 150)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val speed = 0.5f + progress / 100f
                    player.setSpeed(speed)
                    label.text = "播放倍速：${"%.2f".format(speed)}x"
                    valueLabel.text = "${"%.2f".format(speed)}x"
                    savePlaybackState()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        box.addView(label)
        box.addView(valueLabel)
        box.addView(slider)
        AlertDialog.Builder(this)
            .setTitle("播放倍速")
            .setView(box)
            .setPositiveButton("完成", null)
            .show()
    }

    private fun showCurrentQueueDialog() {
        if (currentQueue.isEmpty()) {
            Toast.makeText(this, "当前没有播放列表", Toast.LENGTH_SHORT).show()
            return
        }
        val names = currentQueue.mapIndexed { index, track ->
            val prefix = if (index == currentIndex) "▶ " else ""
            prefix + displayTitle(track)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("正在播放列表")
            .setItems(names) { _, which ->
                playTrack(currentQueue[which], currentQueue)
            }
            .show()
    }

    private fun showVolumeDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val label = TextView(this).apply {
            text = "系统音量：${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"
            bodyStyle(15f, Color.DKGRAY)
        }
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val valueLabel = TextView(this).apply {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            text = "$current / $maxVolume (${if (maxVolume == 0) 0 else current * 100 / maxVolume}%)"
            titleStyle(22f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        val slider = SeekBar(this).apply {
            max = maxVolume
            progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    label.text = "系统音量：$progress / $maxVolume"
                    valueLabel.text = "$progress / $maxVolume (${if (maxVolume == 0) 0 else progress * 100 / maxVolume}%)"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        box.addView(label)
        box.addView(valueLabel)
        box.addView(slider)
        AlertDialog.Builder(this)
            .setTitle("音量调节")
            .setView(box)
            .setPositiveButton("完成", null)
            .show()
    }

    private fun showTrackDetailsDialog(track: Track) {
        val edit = store.edits[track.id] ?: TrackEdit()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        box.addView(detailRow("歌手", track.displayArtist) {
            jumpToLibrary(query = track.displayArtist)
        })
        box.addView(detailRow("专辑", track.displayAlbum) {
            jumpToLibrary(query = track.displayAlbum)
        })
        val tagsText = edit.tags.joinToString(", ").ifBlank { "无" }
        box.addView(detailRow("标签", tagsText) {
            edit.tags.firstOrNull()?.let { jumpToLibrary(tag = it) }
        })
        box.addView(detailRow("点评", edit.comment.ifBlank { "无" }) {
            if (edit.comment.isNotBlank()) jumpToLibrary(query = edit.comment)
        })
        box.addView(detailRow("播放次数", "${store.playCount(track.id)} 次") {
            jumpToLibrary(query = displayTitle(track))
        })
        box.addView(detailRow("格式", track.mimeType.ifBlank { "未知" }, null))
        box.addView(detailRow("时长", formatDuration(track.durationMs), null))
        AlertDialog.Builder(this)
            .setTitle(displayTitle(track))
            .setView(box)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun detailRow(label: String, value: String, onClick: (() -> Unit)?): TextView {
        return TextView(this).apply {
            text = "$label：$value"
            bodyStyle(15f, if (onClick == null) Color.DKGRAY else Palette.ACCENT)
            setPadding(0, dp(7), 0, dp(7))
            onClick?.let { setOnClickListener { it() } }
        }
    }

    private fun menuItem(text: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            this.text = text
            titleStyle(15f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun settingCard(title: String, body: String = "", onClick: (() -> Unit)? = null): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        box.addView(TextView(this).apply {
            text = title
            titleStyle(16f)
        })
        if (body.isNotBlank()) {
            box.addView(TextView(this).apply {
                text = body
                bodyStyle(13f)
            })
        }
        onClick?.let { box.setOnClickListener { it() } }
        return box
    }

    private fun jumpToLibrary(query: String = "", tag: String = "") {
        libraryQuery = query
        selectedTag = tag
        openedPlaylist = null
        render(Page.LIBRARY)
    }

    private fun showEditDialog(track: Track) {
        val edit = store.edits[track.id] ?: TrackEdit()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val alias = dialogInput("别名", edit.alias)
        val comment = dialogInput("点评", edit.comment).apply {
            minLines = 3
            maxLines = 5
            setSingleLine(false)
        }
        val tags = dialogInput("标签，用逗号分隔", edit.tags.joinToString(", "))
        val ratingLabel = TextView(this).apply {
            text = "星级评分：${stars(edit.rating)}"
            bodyStyle(14f, Color.DKGRAY)
        }
        var selectedRating = edit.rating.coerceIn(0, 5)
        val rating = SeekBar(this).apply {
            max = 5
            progress = selectedRating
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedRating = progress.coerceIn(0, 5)
                    ratingLabel.text = "星级评分：${stars(selectedRating)}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            setPadding(0, dp(4), 0, dp(8))
        }
        box.addView(alias)
        box.addView(comment)
        box.addView(tags)
        box.addView(ratingLabel)
        box.addView(rating)

        AlertDialog.Builder(this)
            .setTitle(track.displayTitle)
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                store.saveEdit(
                    track.id,
                    TrackEdit(
                        alias = alias.text.toString().trim(),
                        comment = comment.text.toString().trim(),
                        tags = tags.text.toString().split(',', '，', '#').map { it.trim() }.filter { it.isNotBlank() },
                        rating = selectedRating
                    )
                )
                trackAdapter.notifyDataSetChanged()
                if (page == Page.LIBRARY) render(Page.LIBRARY) else renderIfPlaylistChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateNowPlayingViews(track: Track) {
        miniTitle.text = displayTitle(track)
        miniMeta.text = listOf(track.displayArtist, track.displayAlbum, if (track.hasLyrics) "歌词" else "").filter { it.isNotBlank() }.joinToString(" · ")
        loadArtwork(track, miniThumb)
        updateMediaSessionMetadata(track)
        refreshPlayButtons()
        updateSeek(miniSeek, miniTime)
        updatePlaybackNotification()
        if (page == Page.NOW_PLAYING) render(Page.NOW_PLAYING)
    }

    private fun updateMediaSessionMetadata(track: Track) {
        if (!::mediaSession.isInitialized) return
        val artwork = decodeArtworkPath(resolvedArtworkPath(track))
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, displayTitle(track))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.displayArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.displayAlbum)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, playbackDurationMs.coerceAtLeast(track.durationMs))
                .apply { artwork?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
    }

    private fun refreshPlayButtons() {
        val text = if (player.isPlaying()) "暂停" else "播放"
        val icon = if (player.isPlaying()) R.drawable.ic_pause_circle else R.drawable.ic_play_block
        miniPlayButton.text = ""
        setButtonIcon(miniPlayButton, icon)
        nowPagePlayButton?.text = text
        nowPagePlayButton?.let { setButtonIcon(it, icon) }
        updatePlaybackNotification()
        updateMediaSessionState()
        broadcastPlaybackState("com.android.music.playstatechanged")
    }

    private fun buildSeekBar(): SeekBar {
        return SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserSeeking = true
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserSeeking = false
                    player.seekTo((seekBar?.progress ?: 0).toLong())
                }
            })
        }
    }

    private fun updateSeek(seekBar: SeekBar?, label: TextView?) {
        if (seekBar == null || label == null) return
        seekBar.max = playbackDurationMs.coerceAtLeast(1L).toInt()
        seekBar.progress = playbackPositionMs.coerceIn(0L, playbackDurationMs).toInt()
        label.text = "${formatDuration(playbackPositionMs)} / ${formatDuration(playbackDurationMs)}"
    }

    private fun loadArtwork(track: Track, target: ImageView) {
        if (track.artworkPath.startsWith("http://") || track.artworkPath.startsWith("https://")) {
            target.setImageResource(R.drawable.ic_cover_placeholder)
            loadRemoteImage(track.artworkPath, target)
            return
        }
        val bitmap = decodeArtworkPath(track.artworkPath) ?: siblingArtworkFile(track)?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (bitmap != null) {
            target.setImageBitmap(bitmap)
        } else {
            target.setImageResource(R.drawable.ic_cover_placeholder)
            target.setBackgroundColor(Palette.PANEL_ALT)
        }
    }

    private fun resolvedArtworkPath(track: Track): String {
        val explicit = track.artworkPath.takeIf { it.isNotBlank() }
        if (explicit != null && (explicit.startsWith("content://") || File(explicit).exists())) return explicit
        return siblingArtworkFile(track)?.absolutePath.orEmpty()
    }

    private fun decodeArtworkPath(path: String): android.graphics.Bitmap? {
        if (path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) return null
        return runCatching {
            if (path.startsWith("content://")) {
                contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
            } else {
                File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
        }.getOrNull()
    }

    private fun siblingArtworkFile(track: Track): File? {
        val dir = localTrackFile(track)?.parentFile ?: return null
        val images = dir.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp")
        }?.toList().orEmpty()
        if (images.isEmpty()) return null
        val preferredNames = listOf("cover", "folder", "album", "front")
        return images.minWithOrNull(
            compareBy<File> { file ->
                val name = file.nameWithoutExtension.lowercase(Locale.ROOT)
                preferredNames.indexOfFirst { name.contains(it) }.takeIf { it >= 0 } ?: preferredNames.size
            }.thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    private fun localTrackFile(track: Track): File? {
        val candidates = mutableListOf<String>()
        if (track.uri.startsWith("file://")) Uri.parse(track.uri).path?.let { candidates += it }
        if (track.sourcePath.startsWith("file://")) Uri.parse(track.sourcePath).path?.let { candidates += it }
        if (track.sourcePath.startsWith("/") || track.sourcePath.matches(Regex("""^[A-Za-z]:[\\/].*"""))) candidates += track.sourcePath
        return candidates.asSequence().map { File(it) }.firstOrNull { it.exists() }
    }

    private fun loadRemoteImage(url: String, target: ImageView) {
        if (url.isBlank()) return
        val expected = url
        target.tag = expected
        imageExecutor.execute {
            val bitmap = runCatching {
                val bytes = if (expected.contains("dizzylab.net", ignoreCase = true) && dizzylabCookie.isNotBlank()) {
                    DizzylabClient(dizzylabCookie).imageBytes(expected)
                } else {
                    URL(expected).openStream().use { it.readBytes() }
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.onFailure {
                Log.w(TAG, "Remote image failed: $expected", it)
            }.getOrNull()
            if (bitmap != null) {
                runOnUiThread {
                    if (target.tag == expected) target.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun updateLyricView(track: Track?) {
        if (track == null || nowPageLyrics == null) return
        nowPageLyrics?.text = lyricTextAt(track, playbackPositionMs)
    }

    private fun updateFloatingLyrics() {
        if (!floatingLyricsEnabled || !hasOverlayPermission()) {
            removeFloatingLyrics()
            return
        }
        val track = player.currentTrack ?: run {
            removeFloatingLyrics()
            return
        }
        val view = floatingLyricsView ?: StrokeTextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            floatingLyricsView = this
        }
        view.text = floatingLyricText(track)
        if (!floatingLyricsAdded) {
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 0
            }
            getSystemService(WindowManager::class.java).addView(view, params)
            floatingLyricsAdded = true
        }
    }

    private fun removeFloatingLyrics() {
        val view = floatingLyricsView ?: return
        if (floatingLyricsAdded) {
            runCatching { getSystemService(WindowManager::class.java).removeView(view) }
        }
        floatingLyricsAdded = false
    }

    private fun floatingLyricText(track: Track): String {
        if (track.hasLyrics) {
            val lines = timedLyrics(track)
            val current = lines.indexOfLast { it.timeMs <= playbackPositionMs }
            if (current >= 0) return lines[current].text
        }
        return listOf(displayTitle(track), track.displayArtist).filter { it.isNotBlank() }.joinToString(" - ")
    }

    private fun lyricTextAt(track: Track, positionMs: Long): String {
        if (track.lyricsUri.isBlank()) return "未找到配套歌词文件。"
        val lines = timedLyrics(track)
        if (lines.isEmpty()) return "未检测到可按时间显示的 LRC 时间轴。"
        val current = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        val from = (current - 1).coerceAtLeast(0)
        val to = (current + 1).coerceAtMost(lines.lastIndex)
        return (from..to).joinToString("\n") { index ->
            if (index == current) "▶ ${lines[index].text}" else lines[index].text
        }
    }

    private fun timedLyrics(track: Track): List<LyricLine> {
        return lyricsCache.getOrPut(track.lyricsUri) {
            val text = scanner.readText(track.lyricsUri) ?: return@getOrPut emptyList()
            val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
            text.lineSequence().flatMap { raw ->
                val matches = pattern.findAll(raw).toList()
                val body = raw.replace(pattern, "").trim()
                if (matches.isEmpty() || body.isBlank()) {
                    emptySequence()
                } else {
                    matches.asSequence().mapNotNull { match ->
                        val minute = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                        val second = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                        val fraction = match.groupValues.getOrNull(3).orEmpty()
                        val millis = when (fraction.length) {
                            1 -> fraction.toLongOrNull()?.times(100)
                            2 -> fraction.toLongOrNull()?.times(10)
                            else -> fraction.take(3).toLongOrNull()
                        } ?: 0L
                        LyricLine((minute * 60_000L) + (second * 1000L) + millis, body)
                    }
                }
            }.sortedBy { it.timeMs }.toList()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        themeColor = prefs.getInt("themeColor", Palette.ACCENT)
        notificationEnabled = prefs.getBoolean("notificationEnabled", false)
        lockscreenNotificationEnabled = prefs.getBoolean("lockscreenNotificationEnabled", false)
        floatingLyricsEnabled = prefs.getBoolean("floatingLyricsEnabled", false)
        backgroundImageUri = prefs.getString("backgroundImageUri", "") ?: ""
        backgroundAlpha = prefs.getFloat("backgroundAlpha", 0.35f).coerceIn(0f, 1f)
        skipNoMediaFolders = prefs.getBoolean("skipNoMediaFolders", false)
        playbackMode = PlaybackMode.entries.getOrElse(prefs.getInt("playbackMode", 0)) { PlaybackMode.SEQUENTIAL }
        profileName = prefs.getString("profileName", "profile")?.ifBlank { "profile" } ?: "profile"
        profileAvatarUri = prefs.getString("profileAvatarUri", "") ?: ""
        dizzylabCookie = prefs.getString("dizzylabCookie", "") ?: ""
        dizzylabUserId = prefs.getString("dizzylabUserId", "") ?: ""
        streamPreloadCount = prefs.getInt("streamPreloadCount", 1).coerceIn(0, 5)
        streamDownloadFolderUri = prefs.getString("streamDownloadFolderUri", "") ?: ""
        streamCacheLimitGb = prefs.getFloat("streamCacheLimitGb", 2.0f).coerceAtLeast(0.1f)
        streamCacheFolderUri = prefs.getString("streamCacheFolderUri", "") ?: ""
        restorePlaybackOnLaunch = prefs.getBoolean("restorePlaybackOnLaunch", false)
        audioFocusBehavior = AudioFocusBehavior.entries.getOrElse(prefs.getInt("audioFocusBehavior", AudioFocusBehavior.PAUSE.ordinal)) { AudioFocusBehavior.PAUSE }
        equalizerPreset = prefs.getString("equalizerPreset", "默认") ?: "默认"
        loadEqualizerPresets(prefs.getString("equalizerPresets", "") ?: "")
        equalizerLevels = equalizerPresets[equalizerPreset]?.toMutableList() ?: MutableList(5) { 0 }
        if (!prefs.getBoolean("localProfileCreated", false)) {
            profileName = "profile"
            prefs.edit()
                .putBoolean("localProfileCreated", true)
                .putString("profileName", profileName)
                .apply()
        }
    }

    private fun saveSettings() {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("themeColor", themeColor)
            .putBoolean("notificationEnabled", notificationEnabled)
            .putBoolean("lockscreenNotificationEnabled", lockscreenNotificationEnabled)
            .putBoolean("floatingLyricsEnabled", floatingLyricsEnabled)
            .putString("backgroundImageUri", backgroundImageUri)
            .putFloat("backgroundAlpha", backgroundAlpha)
            .putBoolean("skipNoMediaFolders", skipNoMediaFolders)
            .putInt("playbackMode", playbackMode.ordinal)
            .putString("profileName", profileName.ifBlank { "profile" })
            .putString("profileAvatarUri", profileAvatarUri)
            .putString("dizzylabCookie", dizzylabCookie)
            .putString("dizzylabUserId", dizzylabUserId)
            .putInt("streamPreloadCount", streamPreloadCount.coerceIn(0, 5))
            .putString("streamDownloadFolderUri", streamDownloadFolderUri)
            .putFloat("streamCacheLimitGb", streamCacheLimitGb)
            .putString("streamCacheFolderUri", streamCacheFolderUri)
            .putBoolean("restorePlaybackOnLaunch", restorePlaybackOnLaunch)
            .putInt("audioFocusBehavior", audioFocusBehavior.ordinal)
            .putString("equalizerPreset", equalizerPreset)
            .putString("equalizerPresets", equalizerPresetsJson())
            .putBoolean("localProfileCreated", true)
            .apply()
    }

    private fun loadEqualizerPresets(raw: String) {
        equalizerPresets.clear()
        equalizerPresets["默认"] = List(5) { 0 }
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        root.keys().forEach { name ->
            val values = root.optJSONArray(name)?.let { array ->
                (0 until minOf(array.length(), 5)).map { index -> array.optInt(index, 0).coerceIn(-1500, 1500) }
            }.orEmpty()
            if (values.size == 5) equalizerPresets[name.ifBlank { "未命名" }] = values
        }
        equalizerPresets["默认"] = List(5) { 0 }
    }

    private fun equalizerPresetsJson(): String {
        val root = JSONObject()
        equalizerPresets.forEach { (name, values) -> root.put(name, JSONArray(values)) }
        return root.toString()
    }

    private fun savePlaybackState() {
        if (!::player.isInitialized) return
        val track = player.currentTrack
        val queueIds = JSONArray(currentQueue.map { it.id }).toString()
        getSharedPreferences("playback_state", MODE_PRIVATE).edit()
            .putString("trackId", track?.id.orEmpty())
            .putLong("positionMs", playbackPositionMs)
            .putString("queueIds", queueIds)
            .putInt("currentIndex", currentIndex)
            .putInt("playbackMode", playbackMode.ordinal)
            .putFloat("speed", if (::player.isInitialized) player.speed() else 1.0f)
            .putBoolean("wasPlaying", if (::player.isInitialized) player.isPlaying() else false)
            .apply()
    }

    private fun restorePlaybackState() {
        val prefs = getSharedPreferences("playback_state", MODE_PRIVATE)
        val trackId = prefs.getString("trackId", "").orEmpty()
        if (trackId.isBlank() || allTracks.isEmpty()) return
        val queueIds = runCatching { JSONArray(prefs.getString("queueIds", "[]")) }.getOrDefault(JSONArray())
        val queue = (0 until queueIds.length())
            .mapNotNull { index -> allTracks.firstOrNull { it.id == queueIds.optString(index) } }
            .ifEmpty { allTracks.sortedWith(MusicScanner.trackComparator) }
        val track = queue.firstOrNull { it.id == trackId } ?: allTracks.firstOrNull { it.id == trackId } ?: return
        playbackMode = PlaybackMode.entries.getOrElse(prefs.getInt("playbackMode", playbackMode.ordinal)) { PlaybackMode.SEQUENTIAL }
        player.setSpeed(prefs.getFloat("speed", 1.0f))
        pendingRestoreTrackId = track.id
        pendingRestoreSeekMs = prefs.getLong("positionMs", 0L).coerceAtLeast(0L)
        pendingRestoreShouldPlay = restorePlaybackOnLaunch && prefs.getBoolean("wasPlaying", false)
        restorePreparingPaused = !pendingRestoreShouldPlay
        playTrack(track, queue, countPlay = false)
    }

    private fun currentSettingsJson(): JSONObject {
        return JSONObject()
            .put("themeColor", themeColor)
            .put("notificationEnabled", notificationEnabled)
            .put("lockscreenNotificationEnabled", lockscreenNotificationEnabled)
            .put("floatingLyricsEnabled", floatingLyricsEnabled)
            .put("backgroundImageUri", backgroundImageUri)
            .put("backgroundAlpha", backgroundAlpha)
            .put("skipNoMediaFolders", skipNoMediaFolders)
            .put("profileName", profileName)
            .put("profileAvatarUri", profileAvatarUri)
            .put("localAccount", JSONObject().put("name", profileName).put("avatarUri", profileAvatarUri))
            .put("dizzylabCookie", dizzylabCookie)
            .put("dizzylabUserId", dizzylabUserId)
            .put(
                "streamingAccounts",
                JSONObject().put(
                    "dizzylab",
                    JSONObject()
                        .put("cookie", dizzylabCookie)
                        .put("userId", dizzylabUserId)
                        .put("loggedIn", dizzylabCookie.isNotBlank())
                )
            )
            .put("streamPreloadCount", streamPreloadCount)
            .put("streamDownloadFolderUri", streamDownloadFolderUri)
            .put("streamCacheLimitGb", streamCacheLimitGb)
            .put("streamCacheFolderUri", streamCacheFolderUri)
            .put("restorePlaybackOnLaunch", restorePlaybackOnLaunch)
            .put("audioFocusBehavior", audioFocusBehavior.ordinal)
            .put("equalizerPreset", equalizerPreset)
            .put("equalizerPresets", JSONObject(equalizerPresets.mapValues { JSONArray(it.value) }))
    }

    private fun applyImportedSettings(settings: JSONObject) {
        if (settings.length() == 0) return
        themeColor = settings.optInt("themeColor", themeColor)
        notificationEnabled = settings.optBoolean("notificationEnabled", notificationEnabled)
        lockscreenNotificationEnabled = settings.optBoolean("lockscreenNotificationEnabled", lockscreenNotificationEnabled)
        floatingLyricsEnabled = settings.optBoolean("floatingLyricsEnabled", floatingLyricsEnabled)
        backgroundImageUri = settings.optString("backgroundImageUri", backgroundImageUri)
        backgroundAlpha = settings.optDouble("backgroundAlpha", backgroundAlpha.toDouble()).toFloat().coerceIn(0f, 1f)
        skipNoMediaFolders = settings.optBoolean("skipNoMediaFolders", skipNoMediaFolders)
        profileName = settings.optString("profileName", profileName).ifBlank { "profile" }
        profileAvatarUri = settings.optString("profileAvatarUri", profileAvatarUri)
        settings.optJSONObject("localAccount")?.let { account ->
            profileName = account.optString("name", profileName).ifBlank { "profile" }
            profileAvatarUri = account.optString("avatarUri", profileAvatarUri)
        }
        dizzylabCookie = settings.optString("dizzylabCookie", dizzylabCookie)
        dizzylabUserId = settings.optString("dizzylabUserId", dizzylabUserId)
        settings.optJSONObject("streamingAccounts")
            ?.optJSONObject("dizzylab")
            ?.let { dizzylab ->
                dizzylabCookie = dizzylab.optString("cookie", dizzylabCookie)
                dizzylabUserId = dizzylab.optString("userId", dizzylabUserId)
            }
        streamPreloadCount = settings.optInt("streamPreloadCount", streamPreloadCount).coerceIn(0, 5)
        streamDownloadFolderUri = settings.optString("streamDownloadFolderUri", streamDownloadFolderUri)
        streamCacheLimitGb = settings.optDouble("streamCacheLimitGb", streamCacheLimitGb.toDouble()).toFloat().coerceAtLeast(0.1f)
        streamCacheFolderUri = settings.optString("streamCacheFolderUri", streamCacheFolderUri)
        restorePlaybackOnLaunch = settings.optBoolean("restorePlaybackOnLaunch", restorePlaybackOnLaunch)
        audioFocusBehavior = AudioFocusBehavior.entries.getOrElse(settings.optInt("audioFocusBehavior", audioFocusBehavior.ordinal)) { AudioFocusBehavior.PAUSE }
        settings.optJSONObject("equalizerPresets")?.let { loadEqualizerPresets(it.toString()) }
        equalizerPreset = settings.optString("equalizerPreset", equalizerPreset)
        equalizerLevels = equalizerPresets[equalizerPreset]?.toMutableList() ?: MutableList(5) { 0 }
        player.setEqualizerLevels(equalizerLevels)
        scanner.skipNoMediaFolders = skipNoMediaFolders
        saveSettings()
    }

    private fun readBundledReadme(): String {
        return runCatching {
            assets.open("readme.md").bufferedReader().use { it.readText() }
        }.getOrElse { "README 内容读取失败。" }
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "播放控制", NotificationManager.IMPORTANCE_LOW)
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(DOWNLOAD_CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun updatePlaybackNotification() {
        if (!notificationEnabled) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }
        val track = player.currentTrack ?: return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val artwork = track.artworkPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }?.let {
            BitmapFactory.decodeFile(it.absolutePath)
        }
        val playPause = if (player.isPlaying()) "暂停" else "播放"
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_disc)
            .setContentTitle(displayTitle(track))
            .setContentText(listOf(track.displayArtist, track.displayAlbum).filter { it.isNotBlank() }.joinToString(" · "))
            .setLargeIcon(artwork)
            .setOngoing(player.isPlaying())
            .setShowWhen(false)
            .setVisibility(if (lockscreenNotificationEnabled) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
            .setProgress(playbackDurationMs.toInt().coerceAtLeast(1), playbackPositionMs.toInt().coerceAtLeast(0), false)
            .setContentIntent(playbackPendingIntent(ACTION_OPEN))
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_previous_track), "上一首", playbackPendingIntent(ACTION_PREV)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, if (player.isPlaying()) R.drawable.ic_pause_circle else R.drawable.ic_play_block), playPause, playbackPendingIntent(ACTION_PLAY_PAUSE)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_next_track), "下一首", playbackPendingIntent(ACTION_NEXT)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_previous_track), "-10s", playbackPendingIntent(ACTION_REWIND)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_next_track), "+10s", playbackPendingIntent(ACTION_FORWARD)).build())
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun playbackPendingIntent(action: String): PendingIntent {
        if (action == ACTION_OPEN) {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                this.action = action
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(this, action.hashCode(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val intent = Intent(this, PlaybackActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun handlePlaybackIntent(intent: Intent) {
        if (intent.action == ACTION_OPEN) {
            render(Page.NOW_PLAYING)
        }
    }

    private fun handlePlaybackAction(action: String) {
        when (action) {
            ACTION_PREV -> playPreviousOrFirst()
            ACTION_NEXT -> playNextOrFirst()
            ACTION_PLAY_PAUSE -> toggleOrPlayFirst()
            ACTION_REWIND -> player.seekTo((playbackPositionMs - 10_000L).coerceAtLeast(0L))
            ACTION_FORWARD -> player.seekTo((playbackPositionMs + 10_000L).coerceAtMost(playbackDurationMs))
        }
        updatePlaybackNotification()
    }

    private fun handleMediaKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> toggleOrPlayFirst()
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (player.currentTrack == null) {
                    toggleOrPlayFirst()
                } else if (requestPlaybackFocus()) {
                    pausedByAudioFocus = false
                    player.resume()
                }
                refreshPlayButtons()
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                abandonPlaybackFocus()
                player.pause()
                refreshPlayButtons()
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> playNextOrFirst()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> playPreviousOrFirst()
            KeyEvent.KEYCODE_MEDIA_REWIND -> player.seekTo((playbackPositionMs - 10_000L).coerceAtLeast(0L))
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> player.seekTo((playbackPositionMs + 10_000L).coerceAtMost(playbackDurationMs))
            else -> return false
        }
        updatePlaybackNotification()
        updateMediaSessionState()
        return true
    }

    private fun updateMediaSessionState() {
        if (!::mediaSession.isInitialized) return
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_FAST_FORWARD
        val state = if (player.isPlaying()) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, playbackPositionMs, if (player.isPlaying()) player.speed() else 0f)
                .build()
        )
    }

    private fun broadcastPlaybackState(action: String) {
        val track = player.currentTrack ?: return
        Intent(action).apply {
            putExtra("id", track.id)
            putExtra("track", displayTitle(track))
            putExtra("artist", track.displayArtist)
            putExtra("album", track.displayAlbum)
            putExtra("duration", playbackDurationMs)
            putExtra("position", playbackPositionMs)
            putExtra("playing", player.isPlaying())
            putExtra("package", packageName)
        }.also { sendBroadcast(it) }
    }

    private fun displayTitle(track: Track): String {
        return store.edits[track.id]?.alias?.takeIf { it.isNotBlank() } ?: track.displayTitle
    }

    private fun openTreePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_TREE)
    }

    private fun openManualImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "text/plain", "application/octet-stream"))
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    private fun openBackgroundPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_BACKGROUND)
    }

    private fun openAvatarPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_AVATAR)
    }

    private fun openStreamDownloadFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_STREAM_DOWNLOAD_DIR)
    }

    private fun openStreamCacheFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_STREAM_CACHE_DIR)
    }

    private fun openNcmConvertPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "audio/*", "*/*"))
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_CONVERT)
    }

    private fun showDizzylabLogin() {
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    saveDizzylabCookieFromWeb(url.orEmpty())
                }
            }
            loadUrl(DIZZYLAB_LOGIN_URL)
        }
        AlertDialog.Builder(this)
            .setTitle("DizzyLab 登录")
            .setView(webView)
            .setPositiveButton("登录完成") { _, _ ->
                saveDizzylabCookieFromWeb(webView.url.orEmpty())
                if (dizzylabCookie.isBlank()) {
                    Toast.makeText(this, "未检测到 DizzyLab Cookie", Toast.LENGTH_SHORT).show()
                } else {
                    loadDizzylabAlbums()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun saveDizzylabCookieFromWeb(url: String) {
        if (!url.contains("dizzylab.net")) return
        val cookie = CookieManager.getInstance().getCookie("https://www.dizzylab.net/").orEmpty()
        if (cookie.isBlank()) return
        dizzylabCookie = cookie
        Regex("""/u/([^/]+)/?""").find(url)?.groupValues?.getOrNull(1)?.let { id ->
            if (id.isNotBlank() && id != "albums") dizzylabUserId = id
        }
        saveSettings()
    }

    private fun loadDizzylabAlbums(query: String = "") {
        if (dizzylabCookie.isBlank()) {
            showDizzylabLogin()
            return
        }
        streamSource = StreamSource.DIZZYLAB
        setStatus(if (query.isBlank()) "正在加载 DizzyLab 账户专辑..." else "正在搜索 DizzyLab：$query")
        Thread {
            runCatching {
                val client = DizzylabClient(dizzylabCookie)
                val (userId, musicUrl) = client.discoverMusicUrl(dizzylabUserId)
                dizzylabUserId = userId
                client.albums(musicUrl, query)
            }.onSuccess { albums ->
                runOnUiThread {
                    dizzylabAlbums = albums
                    dizzylabVisibleAlbumCount = 40
                    saveSettings()
                    render(Page.STREAMING)
                    setStatus("DizzyLab：${albums.size} 张专辑")
                }
            }.onFailure { error ->
                runOnUiThread {
                    Toast.makeText(this, error.message ?: "DizzyLab 专辑加载失败", Toast.LENGTH_SHORT).show()
                    setStatus("DizzyLab 专辑加载失败")
                }
            }
        }.start()
    }

    private fun loadDizzylabAlbum(album: StreamAlbum) {
        setStatus("正在加载专辑：${album.title}")
        Thread {
            runCatching { DizzylabClient(dizzylabCookie).albumDetails(album) }
                .onSuccess { details ->
                    runOnUiThread {
                        openedDizzylabAlbum = details
                        render(Page.STREAMING)
                        setStatus("${details.album.title}：${details.tracks.size} 首")
                    }
                }
                .onFailure { error ->
                    runOnUiThread { Toast.makeText(this, error.message ?: "专辑加载失败", Toast.LENGTH_SHORT).show() }
                }
        }.start()
    }

    private fun showDizzylabAlbumMenu(details: StreamAlbumDetails) {
        AlertDialog.Builder(this)
            .setTitle(details.album.title)
            .setItems(arrayOf("下载整张专辑")) { _, _ ->
                downloadStreamAlbum(details)
            }
            .show()
    }

    private fun ensureStreamDownloadFolderReady(onReady: () -> Unit): Boolean {
        if (streamDownloadFolderUri.isBlank() || isStreamDownloadFolderUsable()) return true
        AlertDialog.Builder(this)
            .setTitle("下载文件夹授权失效")
            .setMessage("系统拒绝访问此前选择的下载文件夹。请重新选择文件夹；如果取消或授权仍失败，本次下载将使用默认目录。")
            .setPositiveButton("重新选择") { _, _ ->
                pendingStreamDownloadAfterFolder = onReady
                openStreamDownloadFolderPicker()
            }
            .setNegativeButton("使用默认目录") { _, _ ->
                streamDownloadFolderUri = ""
                saveSettings()
                Toast.makeText(this, "已使用默认下载目录。", Toast.LENGTH_SHORT).show()
                onReady()
            }
            .show()
        return false
    }

    private fun resumePendingStreamDownload() {
        val action = pendingStreamDownloadAfterFolder
        pendingStreamDownloadAfterFolder = null
        action?.invoke()
    }

    private fun downloadStreamAlbum(details: StreamAlbumDetails) {
        if (details.tracks.isEmpty()) {
            Toast.makeText(this, "没有可下载的音频", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ensureStreamDownloadFolderReady { downloadStreamAlbum(details) }) return
        setStatus("正在下载专辑：${details.album.title}")
        Thread {
            val downloaded = mutableListOf<Track>()
            updateDownloadNotification("下载 ${details.album.title}", 0, details.tracks.size)
            val coverPath = downloadStreamCover(details)
            details.tracks.forEachIndexed { index, track ->
                downloadStreamTrackSync(track, details, coverPath)?.let { downloaded += it }
                updateDownloadNotification("下载 ${details.album.title}", index + 1, details.tracks.size)
            }
            addDownloadedTracksToLibrary(downloaded, details)
            finishDownloadNotification("下载完成：${details.album.title}", downloaded.size, details.tracks.size)
            runOnUiThread {
                Toast.makeText(this, "专辑下载完成：${downloaded.size} / ${details.tracks.size}", Toast.LENGTH_LONG).show()
                setStatus("下载完成：${downloaded.size} / ${details.tracks.size}")
            }
        }.start()
    }

    private fun downloadStreamTrack(track: Track) {
        if (!ensureStreamDownloadFolderReady { downloadStreamTrack(track) }) return
        setStatus("正在下载：${displayTitle(track)}")
        Thread {
            val details = openedDizzylabAlbum ?: StreamAlbumDetails(
                album = StreamAlbum(track.displayAlbum, track.displayAlbum, "", track.artworkPath),
                tracks = listOf(track),
                circle = track.displayArtist
            )
            updateDownloadNotification("下载 ${displayTitle(track)}", 0, 1)
            val coverPath = downloadStreamCover(details)
            val downloaded = downloadStreamTrackSync(track, details, coverPath)
            if (downloaded != null) addDownloadedTracksToLibrary(listOf(downloaded), details)
            finishDownloadNotification("下载完成：${displayTitle(track)}", if (downloaded == null) 0 else 1, 1)
            runOnUiThread {
                Toast.makeText(this, if (downloaded != null) "下载完成" else "下载失败", Toast.LENGTH_SHORT).show()
                setStatus(if (downloaded != null) "下载完成：${displayTitle(track)}" else "下载失败：${displayTitle(track)}")
            }
        }.start()
    }

    private fun downloadStreamTrackSync(track: Track, details: StreamAlbumDetails, coverPath: String): Track? {
        return runCatching {
            val bytes = DizzylabClient(dizzylabCookie).download(track.uri, details.album.url)
            val fileName = safeFileName("${track.displayTitle}.${track.uri.substringBefore("?").substringAfterLast('.', "mp3")}")
            val uri = writeStreamDownloadBytes("dizzylab", details.album.title, fileName, track.mimeType.ifBlank { "audio/mpeg" }, bytes)
            track.copy(
                id = "download:dizzylab:${uri.hashCode()}",
                uri = uri,
                album = details.album.title,
                artist = track.artist.ifBlank { details.circle },
                sourcePath = uri,
                artworkPath = coverPath
            )
        }.onFailure {
            Log.w(TAG, "DizzyLab track download failed: ${track.uri}", it)
        }.getOrNull()
    }

    private fun downloadStreamCover(details: StreamAlbumDetails): String {
        val url = details.album.coverUrl.ifBlank { details.tracks.firstOrNull()?.artworkPath.orEmpty() }
        if (url.isBlank()) return ""
        return runCatching {
            val bytes = if (url.contains("dizzylab.net", ignoreCase = true) && dizzylabCookie.isNotBlank()) {
                DizzylabClient(dizzylabCookie).imageBytes(url, details.album.url)
            } else {
                URL(url).openStream().use { it.readBytes() }
            }
            val coverName = safeFileName("${details.album.title}.jpg")
            writeStreamDownloadBytes("dizzylab", details.album.title, coverName, "image/jpeg", bytes)
            val localDir = File(filesDir, "stream_artwork").apply { mkdirs() }
            val localFile = File(localDir, "${details.album.url.hashCode()}.jpg")
            localFile.writeBytes(bytes)
            localFile.absolutePath
        }.onFailure {
            Log.w(TAG, "DizzyLab cover download failed: $url", it)
        }.getOrDefault("")
    }

    private fun writeStreamDownloadBytes(source: String, album: String, fileName: String, mimeType: String, bytes: ByteArray): String {
        if (streamDownloadFolderUri.isBlank() || !hasPersistedWritePermission(streamDownloadFolderUri)) {
            if (streamDownloadFolderUri.isNotBlank()) {
                Log.w(TAG, "Stream download folder permission missing, falling back to app directory: $streamDownloadFolderUri")
                streamDownloadFolderUri = ""
                saveSettings()
                runOnUiThread {
                    Toast.makeText(this, "系统拒绝访问下载文件夹，已改用默认下载目录。", Toast.LENGTH_LONG).show()
                }
            }
            return writeDefaultStreamDownloadBytes(source, album, fileName, bytes)
        }
        return runCatching {
            val root = Uri.parse(streamDownloadFolderUri)
            val sourceDir = findOrCreateDocumentDir(root, source)
            val albumDir = findOrCreateDocumentDir(root, safeFileName(album), sourceDir)
            val fileUri = DocumentsContract.createDocument(contentResolver, albumDir, mimeType, fileName)
                ?: error("无法创建下载文件")
            contentResolver.openOutputStream(fileUri)?.use { it.write(bytes) } ?: error("无法写入下载文件")
            fileUri.toString()
        }.getOrElse { error ->
            Log.w(TAG, "Stream download folder write failed, falling back to app directory: $streamDownloadFolderUri", error)
            streamDownloadFolderUri = ""
            saveSettings()
            runOnUiThread {
                Toast.makeText(this, "下载文件夹写入失败，已改用默认下载目录。", Toast.LENGTH_LONG).show()
            }
            writeDefaultStreamDownloadBytes(source, album, fileName, bytes)
        }
    }

    private fun writeDefaultStreamDownloadBytes(source: String, album: String, fileName: String, bytes: ByteArray): String {
        val baseDir = if (hasAllFilesAccess()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SMP")
        } else {
            getExternalFilesDir(null) ?: filesDir
        }
        val dir = File(baseDir, "StreamDownloads/$source/${safeFileName(album)}").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    private fun hasPersistedWritePermission(uri: String): Boolean {
        val target = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.isWritePermission && permission.uri == target
        }
    }

    private fun isStreamDownloadFolderUsable(): Boolean {
        if (streamDownloadFolderUri.isBlank() || !hasPersistedWritePermission(streamDownloadFolderUri)) return false
        return runCatching {
            val treeUri = Uri.parse(streamDownloadFolderUri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null
            )?.use { cursor -> cursor.moveToFirst() } ?: true
        }.getOrDefault(false)
    }

    private fun findOrCreateDocumentDir(treeUri: Uri, name: String, parentUri: Uri? = null): Uri {
        val parent = parentUri ?: DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(parent))
        contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == name && cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                }
            }
        }
        return DocumentsContract.createDocument(contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: error("无法创建目录：$name")
    }

    private fun addDownloadedTracksToLibrary(downloaded: List<Track>, details: StreamAlbumDetails) {
        if (downloaded.isEmpty()) return
        runOnUiThread {
            val albumCoverPath = downloaded.firstOrNull { it.artworkPath.isNotBlank() }?.artworkPath.orEmpty()
            val normalized = downloaded.map { track ->
                if (track.artworkPath.isBlank() && albumCoverPath.isNotBlank()) track.copy(artworkPath = albumCoverPath) else track
            }
            allTracks = (allTracks + normalized).distinctBy { it.id }.sortedWith(MusicScanner.trackComparator)
            store.saveTracks(allTracks)
            store.createAlbumPlaylistsIfNeeded(allTracks)
            store.saveAlbumPlaylistMeta(details.album.title, details.description, details.tags)
            renderIfLibraryVisible()
        }
    }

    private fun updateDownloadNotification(title: String, progress: Int, max: Int) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = Notification.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_disc)
            .setContentTitle(title)
            .setContentText("$progress / $max")
            .setProgress(max.coerceAtLeast(1), progress.coerceAtLeast(0), false)
            .setOngoing(true)
            .build()
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    private fun finishDownloadNotification(title: String, progress: Int, max: Int) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = Notification.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_disc)
            .setContentTitle(title)
            .setContentText("$progress / $max")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .build()
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    private fun openConfigExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "smp_config.json")
        }
        startActivityForResult(intent, REQUEST_CONFIG_EXPORT)
    }

    private fun openConfigImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_CONFIG_IMPORT)
    }

    private fun exportConfigTo(uri: Uri) {
        val root = store.exportUserConfig(currentSettingsJson())
        runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            }
        }.onSuccess {
            Toast.makeText(this, "配置已导出", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, it.message ?: "配置导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importConfigFrom(uri: Uri) {
        runCatching {
            val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val settings = store.importUserConfig(JSONObject(text))
            applyImportedSettings(settings)
        }.onSuccess {
            allTracks = store.savedTracks().sortedWith(MusicScanner.trackComparator)
            rebuildShell(Page.SETTINGS)
            Toast.makeText(this, "配置已导入", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, it.message ?: "配置导入失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertNcmFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        setStatus("正在转换 ${uris.size} 个音乐文件...")
        Thread {
            val converter = NcmConverter(this)
            val results = mutableListOf<String>()
            var success = 0
            uris.forEach { uri ->
                runCatching { converter.convert(uri) }
                    .onSuccess { result ->
                        success++
                        results += result.outputFile.absolutePath
                    }
                    .onFailure { error ->
                        results += "失败：${error.message ?: uri}"
                    }
            }
            runOnUiThread {
                val message = "转换完成：$success / ${uris.size}\n输出目录：${(getExternalFilesDir(null) ?: filesDir).absolutePath}\\ConvertedMusic"
                AlertDialog.Builder(this)
                    .setTitle("音乐格式转换")
                    .setMessage(message + if (results.isNotEmpty()) "\n\n" + results.take(6).joinToString("\n") else "")
                    .setPositiveButton("完成", null)
                    .show()
                setStatus("音乐格式转换完成：$success / ${uris.size}")
            }
        }.start()
    }

    private fun persistUriPermission(uri: Uri, flags: Int) {
        val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching { contentResolver.takePersistableUriPermission(uri, takeFlags) }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
    }

    private fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        requestPermissions(arrayOf(permission), REQUEST_AUDIO)
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT < 30) return
        val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching {
            startActivity(appSettings)
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                .onFailure { error ->
                    Toast.makeText(this, error.message ?: "无法打开文件管理权限设置", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }.onFailure {
            Toast.makeText(this, it.message ?: "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    private fun rebuildShell(target: Page) {
        buildShell()
        render(target)
        player.currentTrack?.let { updateNowPlayingViews(it) }
    }

    private fun headerGradientColors(): IntArray {
        return intArrayOf(
            mixColor(Palette.BG, themeColor, 0.28f),
            mixColor(Palette.PANEL_ALT, themeColor, 0.22f),
            mixColor(Palette.PANEL_ALT, themeColor, 0.38f)
        )
    }

    private fun contentOverlayColor(): Int {
        if (backgroundImageUri.isBlank()) return Palette.BG
        return Color.argb(210, Color.red(Palette.BG), Color.green(Palette.BG), Color.blue(Palette.BG))
    }

    private fun mixColor(start: Int, end: Int, ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        return Color.rgb(
            (Color.red(start) * inverse + Color.red(end) * clamped).toInt(),
            (Color.green(start) * inverse + Color.green(end) * clamped).toInt(),
            (Color.blue(start) * inverse + Color.blue(end) * clamped).toInt()
        )
    }

    private fun safeFileName(value: String): String {
        return value.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).ifBlank { "download.mp3" }
    }

    private fun streamFolderLabel(uri: String, fallback: String): String {
        if (uri.isBlank()) return fallback
        return Uri.decode(uri).substringAfterLast('/').substringAfterLast(':').ifBlank { "已选择自定义目录" }
    }

    private fun streamCacheDir(): File {
        return File(getExternalFilesDir(null) ?: filesDir, "StreamCache").apply { mkdirs() }
    }

    private fun enforceStreamCacheLimit() {
        val limitBytes = (streamCacheLimitGb.coerceAtLeast(0.1f) * 1024f * 1024f * 1024f).toLong()
        val files = streamCacheDir().walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toMutableList()
        var total = files.sumOf { it.length() }
        while (total > limitBytes && files.isNotEmpty()) {
            val file = files.removeAt(0)
            total -= file.length()
            file.delete()
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            titleStyle(20f)
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun titleActionRow(title: String, actionText: String, onAction: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        row.addView(TextView(this).apply {
            text = title
            titleStyle(20f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = actionText
            titleStyle(28f)
            gravity = Gravity.CENTER
            setOnClickListener { onAction() }
        }, LinearLayout.LayoutParams(dp(48), dp(42)))
        return row
    }

    private fun textTabButton(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(if (selected) Palette.TEXT else Palette.MUTED)
            typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            setOnClickListener { onClick() }
        }
    }

    private fun attachPlaylistCategorySwipe(view: View) {
        attachPassiveHorizontalSwipe(
            view,
            onLeft = { switchPlaylistCategory(1) },
            onRight = { switchPlaylistCategory(-1) }
        )
    }

    private fun switchPlaylistCategory(direction: Int) {
        val entries = PlaylistCategory.entries
        val current = entries.indexOf(playlistCategory).takeIf { it >= 0 } ?: 0
        playlistCategory = entries[(current + direction + entries.size) % entries.size]
        render(Page.PLAYLISTS)
    }

    private fun attachConsumingHorizontalSwipe(view: View, onLeft: () -> Unit, onRight: () -> Unit) {
        var startX = 0f
        var startY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handleHorizontalSwipe(startX, startY, event.x, event.y, onLeft, onRight)
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun attachPassiveHorizontalSwipe(view: View, onLeft: () -> Unit, onRight: () -> Unit) {
        var startX = 0f
        var startY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    false
                }
                MotionEvent.ACTION_UP -> handleHorizontalSwipe(startX, startY, event.x, event.y, onLeft, onRight)
                else -> false
            }
        }
    }

    private fun handleHorizontalSwipe(startX: Float, startY: Float, endX: Float, endY: Float, onLeft: () -> Unit, onRight: () -> Unit): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        if (kotlin.math.abs(dx) < dp(72) || kotlin.math.abs(dx) < kotlin.math.abs(dy) * 1.4f) return false
        if (dx < 0) onLeft() else onRight()
        return true
    }

    private fun scanAction(title: String, subtitle: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = title
            titleStyle(17f)
        })
        row.addView(TextView(this).apply {
            text = subtitle
            bodyStyle(13f)
        })
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
        return row
    }

    private fun editText(hintText: String, value: String = "", onChanged: (String) -> Unit): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.TEXT)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            setText(value)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged(s?.toString().orEmpty())
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
    }

    private fun dialogInput(hintText: String, value: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(value)
            setSingleLine(true)
            textSize = 15f
        }
    }

    private fun actionButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Palette.TEXT)
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(6), 0, dp(6), 0)
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            setOnClickListener { onClick() }
        }
    }

    private fun iconActionButton(text: String, iconRes: Int, onClick: () -> Unit): Button {
        return actionButton(text, onClick).apply {
            setButtonIcon(this, iconRes)
            compoundDrawablePadding = dp(4)
        }
    }

    private fun setButtonIcon(button: Button, iconRes: Int) {
        val icon: Drawable = getDrawable(iconRes) ?: return
        val size = dp(18)
        icon.setBounds(0, 0, size, size)
        button.setCompoundDrawables(icon, null, null, null)
    }

    private fun weightedParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            leftMargin = dp(3)
            rightMargin = dp(3)
        }
    }

    private enum class LibrarySortMode(val label: String) {
        AZ("A-Z 正序"),
        ZA("A-Z 倒序"),
        ALBUM("按专辑排序"),
        DURATION("按歌曲时长排序"),
        YEAR("按发行日期排序"),
        ARTIST("按歌手排序")
    }

    private enum class PlaybackMode(val label: String) {
        SEQUENTIAL("顺序播放"),
        TRUE_RANDOM("真随机"),
        PSEUDO_RANDOM("伪随机"),
        REPEAT_ONE("单曲循环")
    }

    private data class LyricLine(val timeMs: Long, val text: String)

    private enum class Page {
        SCAN,
        LIBRARY,
        PLAYLISTS,
        NOW_PLAYING,
        SETTINGS,
        STREAMING
    }

    private enum class StreamSource {
        DIZZYLAB
    }

    private enum class PlaylistCategory(val label: String) {
        COMMON("常用歌单"),
        CUE("CUE 歌单"),
        ALBUM("专辑歌单")
    }

    private enum class AudioFocusBehavior(val label: String) {
        MIX("同时播放"),
        DUCK("压低音量"),
        PAUSE("暂停播放")
    }

    companion object {
        private const val REQUEST_AUDIO = 1001
        private const val REQUEST_TREE = 1002
        private const val REQUEST_IMPORT = 1003
        private const val REQUEST_NOTIFICATIONS = 1004
        private const val REQUEST_BACKGROUND = 1005
        private const val REQUEST_CONVERT = 1006
        private const val REQUEST_CONFIG_EXPORT = 1007
        private const val REQUEST_CONFIG_IMPORT = 1008
        private const val REQUEST_AVATAR = 1009
        private const val REQUEST_STREAM_DOWNLOAD_DIR = 1010
        private const val REQUEST_STREAM_CACHE_DIR = 1011
        private const val NOTIFICATION_CHANNEL_ID = "playback"
        private const val DOWNLOAD_CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 11
        private const val DOWNLOAD_NOTIFICATION_ID = 21
        private const val TAG = "SMP"
        private const val DIZZYLAB_LOGIN_URL = "https://www.dizzylab.net/albums/login/"
        private const val ACTION_OPEN = "com.supermite.smp.OPEN"
        private const val ACTION_PREV = "com.supermite.smp.PREV"
        private const val ACTION_NEXT = "com.supermite.smp.NEXT"
        private const val ACTION_PLAY_PAUSE = "com.supermite.smp.PLAY_PAUSE"
        private const val ACTION_REWIND = "com.supermite.smp.REWIND"
        private const val ACTION_FORWARD = "com.supermite.smp.FORWARD"
    }
}

private class StrokeTextView(context: android.content.Context) : TextView(context) {
    init {
        setTextColor(Color.WHITE)
        paint.isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        val originalColor = currentTextColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        setTextColor(Color.BLACK)
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        setTextColor(originalColor)
        super.onDraw(canvas)
    }
}
