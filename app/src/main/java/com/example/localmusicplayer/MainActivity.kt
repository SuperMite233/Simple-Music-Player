package com.supermite.smp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.ContentValues
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
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
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
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.supermite.smp.conversion.AudioMetadataUpdate
import com.supermite.smp.conversion.AudioMetadataWriter
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
import com.supermite.smp.stream.WebDavClient
import com.supermite.smp.stream.WebDavItem
import com.supermite.smp.data.WebDavServer
import java.util.UUID
import com.supermite.smp.ui.Palette
import com.supermite.smp.ui.PlaylistAdapter
import com.supermite.smp.ui.TrackAdapter
import com.supermite.smp.ui.bodyStyle
import com.supermite.smp.ui.contrastTextColor
import com.supermite.smp.ui.dp
import com.supermite.smp.ui.formatDuration
import com.supermite.smp.ui.panelDrawable
import com.supermite.smp.ui.stars
import com.supermite.smp.ui.titleStyle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
    private var rootLayout: LinearLayout? = null
    private var headerPanel: LinearLayout? = null
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
    private val navButtons = mutableMapOf<Page, TextView>()
    private var bottomNavigationPanel: LinearLayout? = null

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
    private var darkMode: DarkMode = DarkMode.SYSTEM
    private var includeBetaUpdates: Boolean = false
    private var autoCheckUpdates: Boolean = true
    private var notificationEnabled: Boolean = false
    private var lockscreenNotificationEnabled: Boolean = false
    private var floatingLyricsEnabled: Boolean = false
    private var floatingLyricsHideInApp: Boolean = false
    private var floatingLyricsAlpha: Float = 1.0f
    private var floatingLyricsTextSizeSp: Float = 15.0f
    private var floatingLyricsStrokeEnabled: Boolean = true
    private var floatingLyricsPosition: FloatingLyricsPosition = FloatingLyricsPosition.TOP
    private var backgroundImageUri: String = ""
    private var backgroundAlpha: Float = 0.35f
    private var skipNoMediaFolders: Boolean = false
    private var debugMode: Boolean = false
    private var autoWriteMetadata: Boolean = false
    private var allowMobileDataDownload: Boolean = true
    private val extraScanFolderUris = mutableListOf<String>()
    private val skippedScanFolderUris = mutableListOf<String>()
    private var libraryFiltersExpanded: Boolean = false
    private var profileName: String = "profile"
    private var profileAvatarUri: String = ""
    private var dizzylabCookie: String = ""
    private var dizzylabUserId: String = ""
    private var streamPreloadCount: Int = 1
    private var streamDownloadFolderUri: String = ""
    private var streamCacheLimitGb: Float = 2.0f
    private var streamCacheFolderUri: String = ""
    private var outputDirectoryMode: OutputDirectoryMode = OutputDirectoryMode.INTERNAL
    private var restorePlaybackOnLaunch: Boolean = false
    private var audioFocusBehavior: AudioFocusBehavior = AudioFocusBehavior.PAUSE
    private var streamSource: StreamSource? = null
    private var dizzylabQuery: String = ""
    private var dizzylabVisibleAlbumCount: Int = 40
    private var pendingStreamScrollY: Int = -1
    private var streamScrollView: ScrollView? = null
    private var streamContentBox: LinearLayout? = null
    private var streamSearchEdit: EditText? = null
    private var dizzylabAlbums: List<StreamAlbum> = emptyList()
    private var openedDizzylabAlbum: StreamAlbumDetails? = null
    private var pendingStreamDownloadAfterFolder: (() -> Unit)? = null
    private var webDavCurrentPath: String = ""
    private var webDavCurrentServer: WebDavServer? = null
    private var webDavCurrentItems: List<WebDavItem> = emptyList()
    private var webDavCache: MutableMap<String, List<WebDavItem>> = mutableMapOf()
    private var webDavSearchQuery: String = ""
    private var nowPageLyrics: TextView? = null
    private var nowPageLyricsScroll: ScrollView? = null
    private var nowPageLyricsBox: LinearLayout? = null
    private var nowPlayingArtworkHidden: Boolean = false
    private var lastLyricsUserInteractionAt: Long = 0L
    private var pendingRestoreTrackId: String? = null
    private var pendingRestoreSeekMs: Long = -1L
    private var pendingRestoreShouldPlay: Boolean = false
    private var restorePreparingPaused: Boolean = false
    private var pausedByAudioFocus: Boolean = false
    private var playlistCategory: PlaylistCategory = PlaylistCategory.COMMON
    private var floatingLyricsView: StrokeTextView? = null
    private var floatingLyricsAdded: Boolean = false
    private var appInForeground: Boolean = false
    private var loadingOverlay: View? = null
    private var loadingCount: Int = 0
    private var updateCheckStarted: Boolean = false
    private var equalizerPreset: String = "默认"
    private var equalizerLevels: MutableList<Int> = MutableList(5) { 0 }
    private val equalizerPresets = mutableMapOf("默认" to List(5) { 0 })
    private val lyricsCache = mutableMapOf<String, List<LyricLine>>()
    private var pendingMetadataCoverTrackId: String = ""
    private var pendingMetadataCoverUri: String = ""
    private var pendingMetadataCoverLabel: TextView? = null
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
        setTheme(startupThemeStyle())
        super.onCreate(savedInstanceState)
        appInForeground = true
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
        initDebugLogger()
        scanner.skipNoMediaFolders = skipNoMediaFolders
        scanner.excludedPaths = listOf(
            cacheDir.absolutePath,
            filesDir.absolutePath,
            streamCacheDir().absolutePath,
            getExternalFilesDir(null)?.absolutePath ?: "",
            externalCacheDir?.absolutePath ?: ""
        ).filter { it.isNotBlank() }
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
        if (autoCheckUpdates) checkForUpdates(silent = true)
        Thread { backgroundSilentRescan() }.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handlePlaybackIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        appInForeground = true
        updateFloatingLyrics()
        if (page == Page.SETTINGS && ::content.isInitialized) render(Page.SETTINGS)
    }

    override fun onPause() {
        appInForeground = false
        updateFloatingLyrics()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyAppPalette()
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
            page == Page.STREAMING && streamSource == StreamSource.WEBDAV && webDavCurrentPath.isNotBlank() -> {
                val parent = webDavCurrentPath.substringBeforeLast('/').ifBlank { "" }
                navigateWebDavDir(parent)
            }
            page == Page.STREAMING && streamSource == StreamSource.WEBDAV -> {
                streamSource = null
                webDavCurrentServer = null
                webDavCurrentPath = ""
                webDavCurrentItems = emptyList()
                webDavCache.clear()
                webDavSearchQuery = ""
                render(Page.STREAMING)
            }
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
                autoScanOnceIfNeeded()
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
            REQUEST_METADATA_COVER -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                pendingMetadataCoverUri = uri.toString()
                pendingMetadataCoverLabel?.text = "已选择封面：${queryDisplayName(uri)}"
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
            REQUEST_EXTRA_SCAN_DIR -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                addUniqueUri(extraScanFolderUris, uri.toString())
                saveSettings()
                showScanSettingsDialog()
            }
            REQUEST_SKIP_SCAN_DIR -> data.data?.let { uri ->
                persistUriPermission(uri, data.flags)
                addUniqueUri(skippedScanFolderUris, uri.toString())
                saveSettings()
                showScanSettingsDialog()
            }
        }
    }

    override fun onPrepared(track: Track) {
        playbackPositionMs = 0L
        playbackDurationMs = track.durationMs.coerceAtLeast(1L)
        trackAdapter.currentTrackId = track.id
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
        trackAdapter.currentTrackId = null
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
            Log.w(TAG, "Audio focus request denied; continuing playback without focus.")
            player.setVolume(1.0f)
        }
        return true
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
        rootLayout = root
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
        loadingOverlay = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        frame.addView(loadingOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(frame)
    }

    private fun showLoading() {
        loadingCount++
        loadingOverlay?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingCount = (loadingCount - 1).coerceAtLeast(0)
        if (loadingCount == 0) loadingOverlay?.visibility = View.GONE
    }

    private fun applyCardPressEffect(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).setInterpolator(DecelerateInterpolator()).start()
            }
            false
        }
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
        headerPanel = header
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
        val panel = object : LinearLayout(this@MainActivity) {
            private var swipeStartX = 0f
            private var swiping = false

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        swipeStartX = ev.x
                        swiping = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!swiping && kotlin.math.abs(ev.x - swipeStartX) > dp(30).toFloat()) {
                            swiping = true
                            return true
                        }
                    }
                }
                return swiping
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        if (swiping) {
                            val dx = event.x - swipeStartX
                            if (kotlin.math.abs(dx) > dp(60).toFloat()) {
                                if (dx < 0) playNextOrFirst() else playPreviousOrFirst()
                            }
                            swiping = false
                            return true
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> swiping = false
                }
                return super.onTouchEvent(event)
            }
        }.apply {
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
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = roundedStrokeDrawable(Palette.PANEL, Palette.PANEL_ALT, 10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        bottomNavigationPanel = row
        listOf(
            Triple(Page.NOW_PLAYING, "正在播放", R.drawable.ic_music_disc),
            Triple(Page.PLAYLISTS, "音乐列表", R.drawable.ic_musiclist),
            Triple(Page.SETTINGS, "设置", R.drawable.ic_menu)
        ).forEach { (target, label, iconRes) ->
            val button = navButton(label, iconRes) { render(target) }
            navButtons[target] = button
            row.addView(button, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            })
        }
        return row
    }

    private fun render(target: Page) {
        if (target != Page.LIBRARY && target != Page.PLAYLISTS) clearSelection()
        page = target
        if (content.childCount > 0) {
            val old = content.getChildAt(0)
            old.animate().alpha(0f).setDuration(120).withEndAction {
                content.removeAllViews()
                nowPagePlayButton = null; nowPageSeek = null; nowPageTime = null
                nowPageLyrics = null; nowPageLyricsScroll = null; nowPageLyricsBox = null
                miniPlayerPanel.visibility = if (target == Page.NOW_PLAYING || target == Page.SETTINGS) View.GONE else View.VISIBLE
                headerTitle.text = if (target == Page.SETTINGS) "SMP" else ""
                updateNavButtons(target)
                buildPageContent(target)
                if (content.childCount > 0) {
                    content.getChildAt(0).alpha = 0f
                    content.getChildAt(0).animate().alpha(1f).setDuration(120).start()
                }
            }.start()
        } else {
            content.removeAllViews()
            nowPagePlayButton = null; nowPageSeek = null; nowPageTime = null
            nowPageLyrics = null; nowPageLyricsScroll = null; nowPageLyricsBox = null
            miniPlayerPanel.visibility = if (target == Page.NOW_PLAYING || target == Page.SETTINGS) View.GONE else View.VISIBLE
            headerTitle.text = if (target == Page.SETTINGS) "SMP" else ""
            updateNavButtons(target)
            buildPageContent(target)
            if (content.childCount > 0) {
                content.getChildAt(0).alpha = 0f
                content.getChildAt(0).animate().alpha(1f).setDuration(120).start()
            }
        }
    }

    private fun updateNavButtons(target: Page) {
        navButtons.forEach { (navPage, button) ->
            button.background = ColorDrawable(Color.TRANSPARENT)
            button.setTextColor(if (navPage == target) Palette.TEXT else Palette.MUTED)
            tintTextViewDrawables(button, if (navPage == target) Palette.TEXT else Palette.MUTED)
        }
    }

    private fun buildPageContent(target: Page) {
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
        content.addView(scanAction("尝试通过 LrcLib 获取歌词", "为音乐库中具备曲名、作者和专辑元数据的音乐匹配带时间轴的 LRC 歌词。") {
            matchLyricsForTracks(allTracks, "全库歌词匹配")
        })
        content.addView(scanAction("通过 Last.fm 获取音频元数据", "为音乐库中缺少元数据的曲目获取标题、歌手、专辑等信息。部分情况需要手动剔除错误元数据才能匹配到正确数据。") {
            fetchLastFmMetadata()
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
                    dialogBuilder()
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
            PlaylistCategory.AUTO -> playlists.filter { it.systemType == "cue" || it.systemType == "album" }
        }
    }

    private fun Playlist.canAcceptManualAdds(): Boolean {
        return systemType.isBlank() || id == LibraryStore.FAVORITES_ID
    }

    private fun Playlist.canManuallyEditTracks(): Boolean {
        return systemType.isBlank()
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
        dialogBuilder()
            .setTitle("编辑歌单详情")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                store.savePlaylistMeta(
                    playlist.id,
                    description.text.toString(),
                    tags.text.toString().split(Regex("[,，#]")).map { it.trim() }.filter { it.isNotBlank() }
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
            streamScrollView = null
            streamContentBox = null
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
            streamScrollView = scroll
            streamContentBox = box
            box.addView(actionButton("返回流媒体来源") {
                streamSource = null
                dizzylabAlbums = emptyList()
                dizzylabQuery = ""
                dizzylabVisibleAlbumCount = 40
                streamScrollView = null
                streamContentBox = null
                render(Page.STREAMING)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
            val searchRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val search = editText("搜索已购专辑", dizzylabQuery) { value ->
                dizzylabQuery = value
                filterStreamingAlbums()
            }
            streamSearchEdit = search
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
                    pendingStreamScrollY = streamScrollView?.scrollY ?: -1
                    loadDizzylabAlbum(album)
                })
            }
            if (filteredAlbums.size > dizzylabVisibleAlbumCount) {
                box.addView(actionButton("加载更多（${dizzylabVisibleAlbumCount}/${filteredAlbums.size}）") {
                    loadMoreStreamingAlbums()
                }.apply { tag = "load_more" }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                })
            }
        } else if (streamSource == StreamSource.WEBDAV && webDavCurrentServer != null) {
            renderWebDavBrowser(box)
        } else {
            box.addView(streamSourceCard("DizzyLab", if (dizzylabCookie.isBlank()) "未登录，点击登录或加载账户专辑" else "已登录，点击加载账户专辑", R.drawable.ic_dizzylab) {
                if (dizzylabCookie.isBlank()) showDizzylabLogin() else loadDizzylabAlbums()
            })
            box.addView(streamSourceCard("Navidrome 服务器", "待后续更新", R.drawable.ic_navidrome) {
                Toast.makeText(this, "Navidrome 暂待后续更新", Toast.LENGTH_SHORT).show()
            })
            box.addView(streamSourceCard("WebDav 服务器", if (store.webDavServers.isEmpty()) "未添加服务器，请在设置中添加" else "已添加 ${store.webDavServers.size} 台服务器", R.drawable.ic_dav) {
                if (store.webDavServers.isEmpty()) {
                    Toast.makeText(this, "请先在设置-用户帐号-流媒体账号凭证中添加 WebDav 服务器", Toast.LENGTH_SHORT).show()
                } else {
                    showWebDavServerPicker()
                }
            })
        }
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (pendingStreamScrollY >= 0 && openedDizzylabAlbum == null) {
            val restoreY = pendingStreamScrollY
            pendingStreamScrollY = -1
            scroll.post { scroll.scrollTo(0, restoreY) }
        }
        setStatus("流媒体模式")
    }

    private fun loadMoreStreamingAlbums() {
        val box = streamContentBox ?: return
        val filteredAlbums = dizzylabAlbums.filter { album ->
            dizzylabQuery.isBlank() || album.title.contains(dizzylabQuery, ignoreCase = true)
        }
        val oldCount = dizzylabVisibleAlbumCount
        val newAlbums = filteredAlbums.drop(oldCount).take(40)
        if (newAlbums.isEmpty()) return
        dizzylabVisibleAlbumCount += 40
        if (box.childCount > 0) box.removeViewAt(box.childCount - 1)
        newAlbums.forEach { album ->
            box.addView(streamAlbumCard(album, "DizzyLab 专辑") {
                pendingStreamScrollY = streamScrollView?.scrollY ?: -1
                loadDizzylabAlbum(album)
            })
        }
        if (filteredAlbums.size > dizzylabVisibleAlbumCount) {
            box.addView(actionButton("加载更多（${dizzylabVisibleAlbumCount}/${filteredAlbums.size}）") {
                loadMoreStreamingAlbums()
            }.apply { tag = "load_more" }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            })
        }
    }

    private fun filterStreamingAlbums() {
        val box = streamContentBox ?: return
        var visibleCount = 0
        var visibleTotal = 0
        dizzylabAlbums.forEach { album ->
            if (dizzylabQuery.isBlank() || album.title.contains(dizzylabQuery, ignoreCase = true)) {
                visibleTotal++
            }
        }
        for (i in 0 until box.childCount) {
            val child = box.getChildAt(i)
            val tag = child.tag as? String ?: continue
            if (!tag.startsWith("album_card:")) continue
            val albumId = tag.removePrefix("album_card:")
            val album = dizzylabAlbums.firstOrNull { it.id == albumId }
            if (album != null && (dizzylabQuery.isBlank() || album.title.contains(dizzylabQuery, ignoreCase = true))) {
                child.visibility = View.VISIBLE
                visibleCount++
            } else {
                child.visibility = View.GONE
            }
        }
        val lastChild = if (box.childCount > 0) box.getChildAt(box.childCount - 1) else null
        val isLoadMore = (lastChild?.tag as? String)?.startsWith("load_more") == true
        if (visibleTotal > visibleCount) {
            if (isLoadMore) box.removeViewAt(box.childCount - 1)
            box.addView(actionButton("加载更多（${visibleCount}/${visibleTotal}）") {
                loadMoreStreamingAlbums()
            }.apply { tag = "load_more" }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            })
        } else if (isLoadMore) {
            box.removeViewAt(box.childCount - 1)
        }
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
            applyCardPressEffect(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            tag = "album_card:${album.id}"
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
            applyCardPressEffect(this)
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
            applyCardPressEffect(this)
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
        val artFrame = FrameLayout(this).apply {
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
        }
        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = if (nowPlayingArtworkHidden) View.GONE else View.VISIBLE
        }
        attachArtworkSwipe(
            artFrame,
            onLeft = { playNextOrFirst() },
            onRight = { playPreviousOrFirst() },
            onUp = { showCurrentQueueDialog() },
            onDown = { showTrackDetailsDialog(track) },
            onSingleTap = {        
                nowPlayingArtworkHidden = !nowPlayingArtworkHidden
                render(Page.NOW_PLAYING)
        },
            onDoubleTap = {
                if (nowPlayingArtworkHidden) seekToVisibleLyric(track) else toggleFavoriteFromArtwork(track)
            }
        )
        loadArtwork(track, art)
        artFrame.addView(art, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        nowPageLyricsScroll = ScrollView(this).apply {
            visibility = if (nowPlayingArtworkHidden) View.VISIBLE else View.GONE
            isFillViewport = true
            var downX = 0f
            var downY = 0f
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                    lastLyricsUserInteractionAt = System.currentTimeMillis()
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        val isTap = kotlin.math.abs(event.x - downX) < dp(18) && kotlin.math.abs(event.y - downY) < dp(18)
                        if (isTap && isLyricsBlankTap(event.y)) {
                            nowPlayingArtworkHidden = true
                            render(Page.NOW_PLAYING)
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }
        nowPageLyricsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(14), dp(8), dp(14))
        }
        nowPageLyricsScroll?.addView(nowPageLyricsBox, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        artFrame.addView(nowPageLyricsScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.addView(artFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)))
        val titleView = TextView(this).apply {
            text = displayTitle(track)
            titleStyle(22f)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
        }
        attachNowPlayingBlankGestures(titleView, track)
        box.addView(titleView)
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        metaRow.addView(nowPlayingMetaText(track.displayArtist) { jumpToLibrary(query = track.displayArtist) })
        metaRow.addView(nowPlayingMetaText(track.displayAlbum) { jumpToLibrary(query = track.displayAlbum) })
        box.addView(metaRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val blankGestureArea = View(this).apply {
            background = ColorDrawable(Color.TRANSPARENT)
        }
        attachNowPlayingBlankGestures(blankGestureArea, track)
        box.addView(blankGestureArea, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))

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

        val bottomBlankGestureArea = View(this).apply {
        background = ColorDrawable(Color.TRANSPARENT)
        }
        attachNowPlayingBlankGestures(bottomBlankGestureArea, track)
        box.addView(bottomBlankGestureArea, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(120)
        ))

        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        updateLyricView(track)
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
        box.addView(settingCard("外观设置", "配色、深色模式和背景图片") {
            showAppearanceSettingsDialog()
        })
        box.addView(settingCard("权限", permissionSummary()) {
            showPermissionSettingsDialog()
        })
        box.addView(settingCard("播放设置", "启动恢复：${if (restorePlaybackOnLaunch) "继续播放" else "保持暂停"}；其他音乐：${audioFocusBehavior.label}") {
            showPlaybackSettingsDialog()
        })
        box.addView(settingCard("存储设置", "扫描、串流下载/缓存、配置文件和音乐格式转换") {
            showStorageSettingsDialog()
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
        box.addView(settingCard("版本号", APP_VERSION) {
            showUpdateSettingsDialog()
        })
        box.addView(settingCard(
            "更新设置",
            "自动检查：${if (autoCheckUpdates) "开启" else "关闭"}；下载通道：${if (includeBetaUpdates) "测试版" else "正式版"}"
        ) {
            showUpdateSettingsDialog()
        })
        box.addView(settingCard("软件作者", "SuperMite") {
            openExternalUrl(AUTHOR_URL)
        })
        box.addView(settingCard("构建提醒", "本软件使用 ChatGPT 辅助构建；音乐格式转换功能的 NCM 解密核心参考并移植自 MIT 许可项目 NCMConverter4a（https://github.com/cdb96/NCMConverter4a）。"))
        box.addView(settingCard("软件介绍", "点击查看 README 介绍") {
            showReadmeDialog()
        })
        box.addView(settingCard("GitHub 仓库", REPO_URL) {
            openExternalUrl(REPO_URL)
        })
        box.addView(settingCard("问题反馈", ISSUES_URL) {
            openExternalUrl(ISSUES_URL)
        })
        scroll.addView(box)
        dialogBuilder()
            .setTitle("软件详情")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showUpdateSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val autoBox = CheckBox(this).apply {
            text = "启动时自动检查更新"
            isChecked = autoCheckUpdates
            setTextColor(Palette.TEXT)
        }
        val betaBox = CheckBox(this).apply {
            text = "下载测试版（只检查包含 beta 字段的 release）"
            isChecked = includeBetaUpdates
            setTextColor(Palette.TEXT)
        }
        box.addView(autoBox)
        box.addView(betaBox)
        box.addView(actionButton("手动检查更新") {
            autoCheckUpdates = autoBox.isChecked
            includeBetaUpdates = betaBox.isChecked
            saveSettings()
            checkForUpdates(silent = false)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(10)
        })
        dialogBuilder()
            .setTitle("更新设置")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                autoCheckUpdates = autoBox.isChecked
                includeBetaUpdates = betaBox.isChecked
                saveSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showReadmeDialog() {
        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            text = readBundledReadme()
            bodyStyle(14f)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        })
        dialogBuilder()
            .setTitle("软件介绍")
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
        dialogBuilder()
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
        dialogBuilder()
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
        box.addView(streamSourceCard("WebDav 服务器", if (store.webDavServers.isEmpty()) "未添加服务器" else "已添加 ${store.webDavServers.size} 台服务器", R.drawable.ic_dav) {
            showWebDavManagementDialog()
        })
        scroll.addView(box)
        dialogBuilder()
            .setTitle("流媒体账号凭证")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun confirmClearDizzylabCookie() {
        dialogBuilder()
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
            bodyStyle(15f, Palette.MUTED)
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
        box.addView(label)
        box.addView(valueLabel)
        box.addView(slider)
        val mobileCheck = CheckBox(this).apply {
            text = "允许使用流量下载（关闭后下载前会询问）"
            isChecked = allowMobileDataDownload
            setTextColor(Palette.TEXT)
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(mobileCheck)
        dialogBuilder()
            .setTitle("串流预加载")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                allowMobileDataDownload = mobileCheck.isChecked
                saveSettings()
                render(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showStorageSettingsDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        box.addView(settingCard("扫描设置", "含 .nomedia 的文件夹：${if (skipNoMediaFolders) "跳过" else "扫描"}") {
            showScanSettingsDialog()
        })
        box.addView(settingCard("下载目录", streamFolderLabel(streamDownloadFolderUri, "应用默认目录")) {
            openStreamDownloadFolderPicker()
        })
        box.addView(settingCard("缓存位置", streamFolderLabel(streamCacheFolderUri, "Android 数据目录")) {
            dialogBuilder()
                .setTitle("缓存位置")
                .setItems(arrayOf("选择文件夹", "恢复默认目录")) { _, which ->
                    if (which == 0) {
                        openStreamCacheFolderPicker()
                    } else {
                        streamCacheFolderUri = ""
                        saveSettings()
                        render(Page.SETTINGS)
                        Toast.makeText(this, "已恢复为默认目录", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        })
        box.addView(settingCard("缓存空间上限", "${"%.1f".format(streamCacheLimitGb)} GB") {
            showStreamCacheLimitDialog()
        })
        box.addView(settingCard("输出文件目录", outputDirectoryMode.label) {
            showOutputDirectoryDialog()
        })
        box.addView(settingCard("配置文件", "将当前设置、用户歌单和我喜欢的音乐导出为 JSON，或从 JSON 导入。") {
            showConfigDialog()
        })
        box.addView(settingCard("音乐格式转换", "选择 NCM 文件并转换为 MP3/FLAC。转换核心移植自 NCMConverter4a。") {
            openNcmConvertPicker()
        })
        box.addView(settingCard("调试模式", if (debugMode) "已开启，报错将产生日志文件" else "已关闭") {
            debugMode = !debugMode
            saveSettings()
            render(Page.SETTINGS)
            Toast.makeText(this, "调试模式已${if (debugMode) "开启" else "关闭"}", Toast.LENGTH_SHORT).show()
        })
        box.addView(settingCard("元数据自动写入", if (autoWriteMetadata) "开启后 Last.fm 匹配到的元数据直接写入音频文件" else "关闭（Last.fm 匹配结果仅更新音乐库）") {
            autoWriteMetadata = !autoWriteMetadata
            saveSettings()
            render(Page.SETTINGS)
            Toast.makeText(this, if (autoWriteMetadata) "已开启（Last.fm 匹配后写入文件，需文件管理权限）" else "已关闭", Toast.LENGTH_SHORT).show()
        })
        scroll.addView(box)
        dialogBuilder()
            .setTitle("存储设置")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showStreamCacheLimitDialog() {
        val input = dialogInput("缓存空间上限（GB）", "%.1f".format(streamCacheLimitGb))
        dialogBuilder()
            .setTitle("缓存空间上限")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                streamCacheLimitGb = input.text.toString().toFloatOrNull()?.coerceAtLeast(0.1f) ?: streamCacheLimitGb
                saveSettings()
                enforceStreamCacheLimit()
                render(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showOutputDirectoryDialog() {
        val entries = OutputDirectoryMode.entries
        val labels = entries.map { it.label }.toTypedArray()
        dialogBuilder()
            .setTitle("输出文件目录")
            .setSingleChoiceItems(labels, outputDirectoryMode.ordinal) { dialog, which ->
                outputDirectoryMode = entries[which]
                saveSettings()
                if (outputDirectoryMode == OutputDirectoryMode.SOURCE && !hasAllFilesAccess()) {
                    Toast.makeText(this, "与源文件相同目录需要文件管理权限；权限不足时会回退到软件内部目录。", Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
                render(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAppearanceSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        box.addView(settingCard("软件配色", "预设主题色或调色盘自定义颜色，按钮和重点控件会同步使用主题色。") {
            showThemeSettingsDialog()
        })
        box.addView(settingCard("深色模式", darkMode.label) {
            showDarkModeDialog()
        })
        box.addView(settingCard("背景图片", "透明度：${(backgroundAlpha * 100).toInt()}%；当前：${if (backgroundImageUri.isBlank()) "未设置" else "已设置"}") {
            showBackgroundSettingsDialog()
        })
        dialogBuilder()
            .setTitle("外观设置")
            .setView(box)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showDarkModeDialog() {
        val labels = DarkMode.entries.map { it.label }.toTypedArray()
        dialogBuilder()
            .setTitle("深色模式")
            .setSingleChoiceItems(labels, darkMode.ordinal) { dialog, which ->
                darkMode = DarkMode.entries[which]
                applyAppPalette()
                saveSettings()
                dialog.dismiss()
                render(Page.SETTINGS)
                updateFloatingLyrics()
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
        dialogBuilder()
            .setTitle("软件配色")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                applyAppPalette()
                saveSettings()
                render(Page.SETTINGS)
                updateFloatingLyrics()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNotificationSettingsDialog() {
        val labels = arrayOf("状态栏播放开关", "锁屏显示播放控件")
        val checked = booleanArrayOf(notificationEnabled, lockscreenNotificationEnabled)
        dialogBuilder()
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val enabledBox = CheckBox(this).apply {
            text = "开启悬浮歌词"
            setTextColor(Palette.TEXT)
            isChecked = floatingLyricsEnabled
        }
        val hideBox = CheckBox(this).apply {
            text = "在软件中隐藏悬浮歌词"
            setTextColor(Palette.TEXT)
            isChecked = floatingLyricsHideInApp
        }
        val strokeBox = CheckBox(this).apply {
            text = "开启文字描边"
            setTextColor(Palette.TEXT)
            isChecked = floatingLyricsStrokeEnabled
        }
        val alphaLabel = TextView(this).apply {
            text = "文本透明度：${(floatingLyricsAlpha * 100).toInt()}%"
            bodyStyle(13f)
        }
        val alphaSlider = SeekBar(this).apply {
            max = 100
            progress = (floatingLyricsAlpha * 100).toInt().coerceIn(20, 100)
        }
        val sizeLabel = TextView(this).apply {
            text = "字号大小：${floatingLyricsTextSizeSp.toInt()}sp"
            bodyStyle(13f)
        }
        val sizeSlider = SeekBar(this).apply {
            max = 20
            progress = (floatingLyricsTextSizeSp.toInt() - 12).coerceIn(0, 20)
        }
        val preview = StrokeTextView(this).apply {
            text = "SMP 悬浮歌词预览"
            gravity = Gravity.CENTER
            textSize = floatingLyricsTextSizeSp
            strokeEnabled = floatingLyricsStrokeEnabled
            setTextColor(applyAlpha(themeColor, floatingLyricsAlpha))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
        }
        fun updatePreview() {
            val alpha = alphaSlider.progress.coerceIn(20, 100) / 100f
            val size = (12 + sizeSlider.progress.coerceIn(0, 20)).toFloat()
            preview.textSize = size
            preview.strokeEnabled = strokeBox.isChecked
            preview.setTextColor(applyAlpha(themeColor, alpha))
            alphaLabel.text = "文本透明度：${(alpha * 100).toInt()}%"
            sizeLabel.text = "字号大小：${size.toInt()}sp"
        }
        alphaSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        strokeBox.setOnCheckedChangeListener { _, _ -> updatePreview() }
        val positionLabels = FloatingLyricsPosition.entries.map { it.label }.toTypedArray()
        val positionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, positionLabels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(floatingLyricsPosition.ordinal)
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
        }
        box.addView(enabledBox)
        box.addView(hideBox)
        box.addView(strokeBox)
        box.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        })
        box.addView(alphaLabel)
        box.addView(alphaSlider)
        box.addView(sizeLabel)
        box.addView(sizeSlider)
        box.addView(TextView(this).apply {
            text = "歌词位置"
            bodyStyle(13f)
            setPadding(0, dp(10), 0, dp(6))
        })
        box.addView(positionSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        dialogBuilder()
            .setTitle("悬浮歌词")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                floatingLyricsEnabled = enabledBox.isChecked
                floatingLyricsHideInApp = hideBox.isChecked
                floatingLyricsAlpha = (alphaSlider.progress.coerceIn(20, 100) / 100f).coerceIn(0.2f, 1f)
                floatingLyricsTextSizeSp = (12 + sizeSlider.progress.coerceIn(0, 20)).toFloat()
                floatingLyricsStrokeEnabled = strokeBox.isChecked
                floatingLyricsPosition = FloatingLyricsPosition.entries.getOrElse(positionSpinner.selectedItemPosition) { FloatingLyricsPosition.TOP }
                saveSettings()
                if (floatingLyricsEnabled && !hasOverlayPermission()) openOverlaySettings()
                updateFloatingLyrics()
                render(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun permissionSummary(): String {
        return listOf(
            "媒体：${if (hasAudioPermission()) "已授权" else "未授权"}",
            "通知：${if (notificationEnabled && hasNotificationPermission()) "开启" else "关闭或未授权"}",
            "悬浮窗权限：${if (hasOverlayPermission()) "已授权" else "未授权"}",
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
            "悬浮窗权限",
            "当前：${if (hasOverlayPermission()) "已授权" else "未授权"}。悬浮歌词的开关、透明度和位置已移动到播放设置。"
        ) {
            if (hasOverlayPermission()) {
                Toast.makeText(this, "悬浮窗权限已授权", Toast.LENGTH_SHORT).show()
            } else {
                openOverlaySettings()
            }
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
        dialogBuilder()
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
        dialogBuilder()
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
        box.addView(actionButton("串流预加载：$streamPreloadCount 首") {
            showStreamingSettingsDialog()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(12)
        })
        box.addView(actionButton(
            "悬浮歌词：${if (floatingLyricsEnabled) "开启" else "关闭"} / ${floatingLyricsPosition.label} / ${(floatingLyricsAlpha * 100).toInt()}% / ${floatingLyricsTextSizeSp.toInt()}sp"
        ) { showFloatingLyricsSettingsDialog() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(12)
        })
        box.addView(actionButton("音效调整器：$equalizerPreset") { showEqualizerDialog() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(12)
        })
        dialogBuilder()
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
        dialogBuilder()
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
        dialogBuilder()
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        val noMedia = CheckBox(this).apply {
            text = "扫描时跳过含 .nomedia 的文件夹"
            isChecked = skipNoMediaFolders
            bodyStyle(15f)
            setOnCheckedChangeListener { _, checked ->
                skipNoMediaFolders = checked
                scanner.skipNoMediaFolders = checked
                saveSettings()
            }
        }
        box.addView(noMedia)
        box.addView(settingCard("媒体库：额外扫描文件夹", "已添加 ${extraScanFolderUris.size} 个文件夹；自动扫描时会一并扫描。") {
            showScanFolderManager("额外扫描文件夹", extraScanFolderUris, REQUEST_EXTRA_SCAN_DIR)
        })
        box.addView(settingCard("媒体库：跳过文件夹", "已添加 ${skippedScanFolderUris.size} 个文件夹；自动扫描时会跳过。") {
            showScanFolderManager("跳过文件夹", skippedScanFolderUris, REQUEST_SKIP_SCAN_DIR)
        })
        dialogBuilder()
            .setTitle("扫描设置")
            .setView(box)
            .setPositiveButton("完成") { _, _ -> render(Page.SETTINGS) }
            .show()
    }

    private fun showScanFolderManager(title: String, folders: MutableList<String>, requestCode: Int) {
        val labels = folders.mapIndexed { index, uri -> "${index + 1}. ${folderDisplayName(uri)}" }.toMutableList()
        labels += "添加文件夹"
        dialogBuilder()
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == folders.size) {
                    openScanFolderPicker(requestCode)
                } else {
                    confirmRemoveScanFolder(title, folders, which)
                }
            }
            .setNegativeButton("返回") { _, _ -> showScanSettingsDialog() }
            .show()
    }

    private fun confirmRemoveScanFolder(title: String, folders: MutableList<String>, index: Int) {
        val uri = folders.getOrNull(index) ?: return
        dialogBuilder()
            .setTitle("移除文件夹")
            .setMessage(folderDisplayName(uri))
            .setPositiveButton("移除") { _, _ ->
                folders.removeAt(index)
                saveSettings()
                showScanFolderManager(title, folders, if (folders === extraScanFolderUris) REQUEST_EXTRA_SCAN_DIR else REQUEST_SKIP_SCAN_DIR)
            }
            .setNegativeButton("取消") { _, _ ->
                showScanFolderManager(title, folders, if (folders === extraScanFolderUris) REQUEST_EXTRA_SCAN_DIR else REQUEST_SKIP_SCAN_DIR)
            }
            .show()
    }

    private fun showConfigDialog() {
        dialogBuilder()
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
            bodyStyle(15f, Palette.MUTED)
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
            bodyStyle(14f, Palette.MUTED)
        })
        box.addView(label)
        box.addView(slider)
        dialogBuilder()
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
        setStatus("正在扫描系统音乐库、软件目录、CUE、歌词和封面...")
        Thread {
            val scanned = runCatching {
                val result = mutableListOf<Track>()
                result += scanner.scanMediaStore()
                appScanRoots().forEach { root ->
                    result += scanner.scanFileDirectory(root)
                }
                extraScanFolderUris
                    .filterNot { skippedScanFolderUris.contains(it) }
                    .forEach { uriText ->
                        runCatching { result += scanner.scanDocumentTree(Uri.parse(uriText)) }
                            .onFailure { Log.w(TAG, "Extra scan folder failed: $uriText", it) }
                    }
                filterSkippedTracks(result)
                    .distinctBy { it.id }
                    .sortedWith(MusicScanner.trackComparator)
            }.getOrElse {
                runOnUiThread { Toast.makeText(this, it.message ?: "扫描失败", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            runOnUiThread { mergeScannedTracks(scanned, replace = true, source = "系统扫描") }
        }.start()
    }

    private fun appScanRoots(): List<File> {
        return listOfNotNull(
            filesDir,
            getExternalFilesDir(null),
            getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ).distinctBy { it.absolutePath }.filter { it.exists() && it.isDirectory }
    }

    private fun filterSkippedTracks(tracks: List<Track>): List<Track> {
        if (skippedScanFolderUris.isEmpty()) return tracks
        val skippedPaths = skippedScanFolderUris.mapNotNull { uriText ->
            val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return@mapNotNull null
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).absolutePath }
                else -> null
            }
        }
        val skippedUriPrefixes = skippedScanFolderUris.filter { it.startsWith("content://") }
        return tracks.filter { track ->
            val local = localTrackFile(track)?.absolutePath.orEmpty()
            val uri = track.uri
            val source = track.sourcePath
            skippedPaths.none { path -> local.startsWith(path) || source.startsWith(path) } &&
                skippedUriPrefixes.none { prefix -> uri.startsWith(prefix) || source.startsWith(prefix) }
        }
    }

    private fun showFirstLaunchDialogIfNeeded() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("firstLaunchPromptDone", false)) return
        prefs.edit().putBoolean("firstLaunchPromptDone", true).apply()
        dialogBuilder()
            .setTitle("初始化 SMP")
            .setMessage("首次打开可以立即扫描本地音乐，并开启状态栏播放控件。之后可在“音乐列表 > 本地音乐 > 搜索筛选右侧的扫描”进入扫描界面。")
            .setPositiveButton("扫描并开启通知") { _, _ ->
                notificationEnabled = true
                saveSettings()
                requestNotificationPermissionIfNeeded()
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
        val canEditSource = selectionFromLibrary || selectionPlaylist?.canManuallyEditTracks() == true
        bar.addView(actionButton(if (canEditSource && !selectionFromLibrary) "移动" else "加入") { showAddSelectedToPlaylistDialog() }, LinearLayout.LayoutParams(dp(62), dp(38)).apply { leftMargin = dp(6) })
        bar.addView(actionButton("歌词") { matchSelectedLyrics() }, LinearLayout.LayoutParams(dp(62), dp(38)).apply { leftMargin = dp(6) })
        bar.addView(actionButton("元数据") { matchSelectedMetadata() }, LinearLayout.LayoutParams(dp(72), dp(38)).apply { leftMargin = dp(6) })
        if (selectionFromLibrary || selectionPlaylist?.canManuallyEditTracks() == true) {
            bar.addView(actionButton("删除") { confirmDeleteSelectedTracks() }, LinearLayout.LayoutParams(dp(62), dp(38)).apply { leftMargin = dp(6) })
        }
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

    private fun matchSelectedLyrics() {
        val selected = selectedTrackIds.mapNotNull { id -> allTracks.firstOrNull { it.id == id } }
        clearSelection()
        renderCurrentListPage()
        matchLyricsForTracks(selected, "批量歌词匹配")
    }

    private fun matchSelectedMetadata() {
        val selected = selectedTrackIds.mapNotNull { id -> allTracks.firstOrNull { it.id == id } }
        clearSelection()
        renderCurrentListPage()
        fetchLastFmMetadataForTracks(selected)
    }

    private fun showAddSelectedToPlaylistDialog() {
        val ids = selectedTrackIds.toList()
        val playlists = store.visiblePlaylists(store.history.map { it.trackId })
            .filter { it.canAcceptManualAdds() }
            .filter { selectionFromLibrary || it.id != selectionPlaylist?.id }
        val names = playlists.map { it.name }.toTypedArray()
        val shouldMove = !selectionFromLibrary && selectionPlaylist?.canManuallyEditTracks() == true
        dialogBuilder()
            .setTitle(if (shouldMove) "移动到播放列表" else "加入播放列表")
            .setItems(names) { _, which ->
                val playlist = playlists[which]
                if (playlist.id == LibraryStore.FAVORITES_ID) {
                    ids.asReversed().forEach { id -> store.setFavorite(id, true) }
                } else {
                    store.addToPlaylist(playlist.id, ids)
                }
                if (shouldMove) {
                    selectionPlaylist?.let { store.removeTracksFromPlaylist(it.id, ids) }
                    openedPlaylist = selectionPlaylist?.let { current ->
                        store.visiblePlaylists(store.history.map { it.trackId }).firstOrNull { it.id == current.id }
                    }
                }
                Toast.makeText(this, if (shouldMove) "已移动到 ${playlist.name}" else "已加入 ${playlist.name}", Toast.LENGTH_SHORT).show()
                clearSelection()
                renderCurrentListPage()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteSelectedTracks() {
        if (!selectionFromLibrary && selectionPlaylist?.canManuallyEditTracks() != true) {
            Toast.makeText(this, "自动歌单不允许手动删除歌曲", Toast.LENGTH_SHORT).show()
            return
        }
        val count = selectedTrackIds.size
        val message = if (selectionFromLibrary) {
            "将尝试删除 $count 首音乐的源文件，并从音乐库和歌单中移除记录。此操作不可撤销，是否继续？"
        } else {
            "将从当前歌单移除 $count 首音乐，不会删除源文件。是否继续？"
        }
        dialogBuilder()
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
            dialogBuilder()
                .setTitle(displayTitle(track))
                .setItems(arrayOf("播放", "下载", "匹配元数据", "歌曲详细信息")) { _, which ->
                    when (which) {
                        0 -> playTrack(track, queueForCurrentView())
                        1 -> downloadStreamTrack(track)
                        2 -> fetchLastFmMetadataForTracks(listOf(track))
                        3 -> showTrackDetailsDialog(track)
                    }
                }
                .show()
            return
        }
        val playlist = openedPlaylist
        val favoriteText = if (store.isFavorite(track.id)) "取消喜欢" else "加入我喜欢的音乐"
        val actions = mutableListOf("播放", favoriteText, "加入播放列表", "匹配歌词", "匹配元数据", "获取封面", "编辑音乐信息", "编辑音频元数据")
        if (playlist != null && playlist.canManuallyEditTracks()) actions.add("从当前播放列表移除")
        dialogBuilder()
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
                    "匹配歌词" -> matchLyricsForTracks(listOf(track), "歌词匹配")
                    "匹配元数据" -> fetchLastFmMetadataForTracks(listOf(track))
                    "获取封面" -> fetchLastFmAlbumArtForTrack(track)
                    "编辑音乐信息" -> showEditDialog(track)
                    "编辑音频元数据" -> showAudioMetadataEditDialog(track)
                    "从当前播放列表移除" -> {
                        store.removeFromPlaylist(playlist!!.id, track.id)
                        openedPlaylist = store.visiblePlaylists(store.history.map { it.trackId }).firstOrNull { it.id == playlist.id }
                        render(Page.PLAYLISTS)
                    }
                }
            }
            .show()
    }

    private fun toggleFavoriteFromArtwork(track: Track) {
        if (track.id.startsWith("stream:")) {
            Toast.makeText(this, "串流音乐请先下载后再收藏到本地歌单", Toast.LENGTH_SHORT).show()
            return
        }
        val next = !store.isFavorite(track.id)
        store.setFavorite(track.id, next)
        Toast.makeText(this, if (next) "已加入我喜欢的音乐" else "已从我喜欢的音乐移除", Toast.LENGTH_SHORT).show()
        setStatus(if (next) "已收藏：${displayTitle(track)}" else "已取消收藏：${displayTitle(track)}")
        renderIfPlaylistChanged()
    }

    private fun renderIfPlaylistChanged() {
        if (page == Page.PLAYLISTS) render(Page.PLAYLISTS)
    }

    private fun renderIfLibraryVisible() {
        if (page == Page.LIBRARY || page == Page.PLAYLISTS || page == Page.STREAMING) render(page)
    }

    private fun matchLyricsForTracks(sourceTracks: List<Track>, title: String) {
        val candidates = sourceTracks
            .distinctBy { it.id }
            .filter { canMatchLrcLib(it) }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "没有可匹配的音乐，需要曲名、作者和专辑元数据", Toast.LENGTH_SHORT).show()
            return
        }
        setStatus("$title：0 / ${candidates.size}")
        Thread {
            val processed = AtomicInteger(0)
            val matched = AtomicInteger(0)
            val executor = Executors.newFixedThreadPool(5)
            val latch = CountDownLatch(candidates.size)
            updateLyricsNotification(title, 0, candidates.size, matched.get())
            candidates.forEach { track ->
                executor.execute {
                    try {
                        val syncedLyrics = fetchLrcLibLyrics(track)
                        if (!syncedLyrics.isNullOrBlank()) {
                            val lyricsUri = saveLrcLibLyrics(track, syncedLyrics)
                            matched.incrementAndGet()
                            runOnUiThread { bindLyricsToTrack(track.id, lyricsUri) }
                        }
                    } finally {
                        val done = processed.incrementAndGet()
                        updateLyricsNotification(title, done, candidates.size, matched.get())
                        latch.countDown()
                    }
                }
            }
            latch.await(20, TimeUnit.MINUTES)
            executor.shutdownNow()
            runOnUiThread {
                finishLyricsNotification("$title 完成", matched.get(), candidates.size)
                Toast.makeText(this, "歌词匹配完成：${matched.get()} / ${candidates.size}", Toast.LENGTH_LONG).show()
                setStatus("$title 完成：${matched.get()} / ${candidates.size}")
                renderIfLibraryVisible()
            }
        }.start()
    }

    private fun bindLyricsToTrack(trackId: String, lyricsUri: String) {
        val updatedTrack = allTracks.firstOrNull { it.id == trackId }?.copy(lyricsUri = lyricsUri) ?: return
        allTracks = allTracks.map { if (it.id == trackId) updatedTrack else it }.sortedWith(MusicScanner.trackComparator)
        visibleTracks = visibleTracks.map { if (it.id == trackId) updatedTrack else it }
        currentQueue = currentQueue.map { if (it.id == trackId) updatedTrack else it }
        player.replaceCurrentTrack(updatedTrack)
        lyricsCache.remove(lyricsUri)
        store.saveTracks(allTracks)
        trackAdapter.tracks = visibleTracks
        trackAdapter.notifyDataSetChanged()
        if (player.currentTrack?.id == trackId) updateNowPlayingViews(updatedTrack)
    }

    private fun canMatchLrcLib(track: Track): Boolean {
        return track.title.isNotBlank() && track.artist.isNotBlank() && track.album.isNotBlank()
    }

    private fun fetchLrcLibLyrics(track: Track): String? {
        return runCatching {
            val params = mutableListOf(
                "track_name" to track.title,
                "artist_name" to track.artist,
                "album_name" to track.album
            )
            if (track.durationMs > 0L) params += "duration" to (track.durationMs / 1000L).toString()
            val query = params.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }
            val connection = (URL("https://lrclib.net/api/get?$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "SMP/$APP_VERSION")
            }
            if (connection.responseCode !in 200..299) return@runCatching null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("syncedLyrics")
                .takeIf { it.isNotBlank() && Regex("""\[\d{1,2}:\d{2}""").containsMatchIn(it) }
        }.onFailure {
            Log.w(TAG, "LrcLib match failed: ${track.title}", it)
        }.getOrNull()
    }

    private fun saveLrcLibLyrics(track: Track, lyrics: String): String {
        val songFile = localTrackFile(track)
        val baseName = songFile?.nameWithoutExtension ?: track.title
        val fileName = safeFileName("${baseName}.lrc")
        val bytes = lyrics.toByteArray(Charsets.UTF_8)
        return when (outputDirectoryMode) {
            OutputDirectoryMode.DOWNLOAD -> writeDownloadOutputBytes("lrc", fileName, "text/plain", bytes)
            OutputDirectoryMode.SOURCE -> {
                val sourceDir = localTrackFile(track)?.parentFile
                if (sourceDir != null && sourceDir.canWrite()) {
                    val file = File(sourceDir, fileName)
                    file.writeBytes(bytes)
                    Uri.fromFile(file).toString()
                } else {
                    writeInternalOutputBytes("lrc", fileName, bytes)
                }
            }
            OutputDirectoryMode.INTERNAL -> writeInternalOutputBytes("lrc", fileName, bytes)
        }
    }

    private fun writeInternalOutputBytes(folder: String, fileName: String, bytes: ByteArray): String {
        val dir = File(getExternalFilesDir(null) ?: filesDir, folder).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    private fun writeDownloadOutputBytes(folder: String, fileName: String, mimeType: String, bytes: ByteArray): String {
        if (streamDownloadFolderUri.isNotBlank() && hasPersistedWritePermission(streamDownloadFolderUri)) {
            return runCatching {
                val root = Uri.parse(streamDownloadFolderUri)
                val folderDir = findOrCreateDocumentDir(root, folder)
                val fileUri = DocumentsContract.createDocument(contentResolver, folderDir, mimeType, fileName)
                    ?: error("无法创建输出文件")
                contentResolver.openOutputStream(fileUri)?.use { it.write(bytes) } ?: error("无法写入输出文件")
                fileUri.toString()
            }.getOrElse { error ->
                Log.w(TAG, "Configured output folder write failed: $streamDownloadFolderUri", error)
                writePublicDownloadOrInternal(folder, fileName, bytes)
            }
        }
        return writePublicDownloadOrInternal(folder, fileName, bytes)
    }

    private fun writePublicDownloadOrInternal(folder: String, fileName: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/SMP/$folder")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建下载目录输出文件")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入下载目录输出文件")
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                return uri.toString()
            }.onFailure { Log.w(TAG, "MediaStore download output failed: $folder/$fileName", it) }
        }
        val baseDir = if (hasAllFilesAccess()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SMP")
        } else {
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: getExternalFilesDir(null) ?: filesDir
        }
        val dir = File(baseDir, folder).apply { mkdirs() }
        val file = File(dir, fileName)
        return runCatching {
            file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        }.getOrElse {
            writeInternalOutputBytes(folder, fileName, bytes)
        }
    }

    private fun queueForCurrentView(): List<Track> {
        return when {
            page == Page.LIBRARY -> sortTracks(allTracks)
            page == Page.PLAYLISTS && openedPlaylist != null -> visibleTracks
            else -> visibleTracks
        }
    }

    private fun showAddToPlaylistDialog(track: Track) {
        val playlists = store.visiblePlaylists(store.history.map { it.trackId }).filter { it.canAcceptManualAdds() }
        val names = playlists.map { it.name }.toTypedArray()
        dialogBuilder()
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
        val input = dialogInput("播放列表名称", "").apply { imeOptions = EditorInfo.IME_ACTION_DONE }
        dialogBuilder()
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
            "编辑音频元数据",
            "播放模式",
            "播放倍速",
            "正在播放列表",
            "收藏到歌单",
            "音量调节",
            "歌曲详细信息"
        )
        val dialog = dialogBuilder().setTitle("播放菜单").create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        items.forEach { item ->
            box.addView(menuItem(item, nowPlayingMenuIcon(item)) {
                dialog.dismiss()
                when (item) {
                    "编辑音乐信息" -> showEditDialog(track)
                    "编辑音频元数据" -> showAudioMetadataEditDialog(track)
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

    private fun nowPlayingMenuIcon(item: String): Int {
        return when (item) {
            "播放模式" -> playbackModeIcon(playbackMode)
            "音量调节" -> R.drawable.ic_volume
            "正在播放列表" -> R.drawable.ic_musiclist
            "收藏到歌单" -> R.drawable.ic_collection
            "编辑音乐信息", "编辑音频元数据", "歌曲详细信息" -> R.drawable.ic_menu
            "播放倍速" -> R.drawable.ic_loop
            else -> R.drawable.ic_menu
        }
    }

    private fun showPlaybackModeDialog() {
        val dialog = dialogBuilder().setTitle("播放模式").create()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        PlaybackMode.entries.forEach { mode ->
            val text = if (mode == playbackMode) "${mode.label}  ✓" else mode.label
            box.addView(menuItem(text, playbackModeIcon(mode)) {
                playbackMode = mode
                if (playbackMode == PlaybackMode.PSEUDO_RANDOM) {
                    applyPseudoShuffleToCurrentQueue()
                } else {
                    clearPseudoShuffleState()
                }
                savePlaybackState()
                setStatus("播放模式：${mode.label}")
                dialog.dismiss()
            })
        }
        dialog.setView(box)
        dialog.show()
    }

    private fun playbackModeIcon(mode: PlaybackMode): Int {
        return when (mode) {
            PlaybackMode.SEQUENTIAL -> R.drawable.ic_loop
            PlaybackMode.TRUE_RANDOM, PlaybackMode.PSEUDO_RANDOM -> R.drawable.ic_random
            PlaybackMode.REPEAT_ONE -> R.drawable.ic_singlecycle
        }
    }

    private fun showSpeedDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val label = TextView(this).apply {
            text = "播放倍速：${"%.2f".format(player.speed())}x"
            bodyStyle(15f, Palette.MUTED)
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
        dialogBuilder()
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
            val prefix = if (index == currentIndex) "? " else ""
            prefix + displayTitle(track)
        }.toTypedArray()
        dialogBuilder()
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
            bodyStyle(15f, Palette.MUTED)
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
        dialogBuilder()
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
        if (track.date.isNotBlank()) box.addView(detailRow("时间戳", track.date, null))
        if (track.composer.isNotBlank()) box.addView(detailRow("作曲家", track.composer, null))
        box.addView(detailRow("格式", track.mimeType.ifBlank { "未知" }, null))
        box.addView(detailRow("时长", formatDuration(track.durationMs), null))
        dialogBuilder()
            .setTitle(displayTitle(track))
            .setView(box)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun detailRow(label: String, value: String, onClick: (() -> Unit)?): TextView {
        return TextView(this).apply {
            text = "$label：$value"
            bodyStyle(15f, if (onClick == null) Palette.MUTED else Palette.ACCENT)
            setPadding(0, dp(7), 0, dp(7))
            onClick?.let { setOnClickListener { it() } }
        }
    }

    private fun menuItem(text: String, iconRes: Int? = null, onClick: () -> Unit): View {
        return TextView(this).apply {
            this.text = text
            titleStyle(15f)
            iconRes?.let {
                setCompoundDrawables(tintedDrawable(it, Palette.TEXT, 20), null, null, null)
                compoundDrawablePadding = dp(10)
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
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
        libraryFiltersExpanded = true
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
            bodyStyle(14f, Palette.MUTED)
        }
        var selectedRating = edit.rating.coerceIn(0, 5)
        val rating = RatingBar(this, null, android.R.attr.ratingBarStyleSmall).apply {
            numStars = 5
            stepSize = 1f
            rating = selectedRating.toFloat()
            setIsIndicator(false)
            setOnRatingBarChangeListener { _, value, _ ->
                selectedRating = value.toInt().coerceIn(0, 5)
                ratingLabel.text = "星级评分：${stars(selectedRating)}"
            }
            setPadding(0, dp(8), 0, dp(8))
        }
        box.addView(alias)
        box.addView(comment)
        box.addView(tags)
        box.addView(ratingLabel)
        box.addView(rating)

        dialogBuilder()
            .setTitle(track.displayTitle)
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                store.saveEdit(
                    track.id,
                    TrackEdit(
                        alias = alias.text.toString().trim(),
                        comment = comment.text.toString().trim(),
                        tags = tags.text.toString().split(Regex("[,，#]")).map { it.trim() }.filter { it.isNotBlank() },
                        rating = selectedRating
                    )
                )
                trackAdapter.notifyDataSetChanged()
                if (page == Page.LIBRARY) render(Page.LIBRARY) else renderIfPlaylistChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAudioMetadataEditDialog(track: Track) {
        if (track.id.startsWith("stream:") || track.isCueTrack) {
            Toast.makeText(this, "串流或 CUE 分轨不能直接编辑源文件元数据。", Toast.LENGTH_LONG).show()
            return
        }
        val sourceFile = localTrackFile(track)
        if (sourceFile == null || !sourceFile.canWrite()) {
            Toast.makeText(this, "无法直接写入该音频文件，请确认文件路径和文件管理权限。", Toast.LENGTH_LONG).show()
            return
        }
        pendingMetadataCoverTrackId = track.id
        pendingMetadataCoverUri = ""
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val title = dialogInput("曲名", track.title)
        val artist = dialogInput("歌手", track.artist)
        val composer = dialogInput("作曲", track.composer)
        val year = dialogInput("年份", track.year.takeIf { it > 0 }?.toString().orEmpty())
        val lyrics = dialogInput("歌词", readTrackLyricsSafely(track)).apply {
            minLines = 3
            maxLines = 6
            setSingleLine(false)
        }
        val album = dialogInput("专辑", track.album)
        val coverLabel = TextView(this).apply {
            text = "封面：${if (track.artworkPath.isBlank()) "未选择" else "保留当前封面"}"
            bodyStyle(13f, Palette.MUTED)
            setPadding(0, dp(8), 0, dp(8))
        }
        pendingMetadataCoverLabel = coverLabel
        box.addView(title)
        box.addView(artist)
        box.addView(composer)
        box.addView(year)
        box.addView(lyrics)
        box.addView(album)
        box.addView(coverLabel)
        box.addView(actionButton("选择封面图片") {
            pendingMetadataCoverTrackId = track.id
            pendingMetadataCoverLabel = coverLabel
            openMetadataCoverPicker()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        dialogBuilder()
            .setTitle("编辑音频元数据")
            .setView(box)
            .setPositiveButton("写入") { _, _ ->
                writeAudioMetadata(
                    track,
                    sourceFile,
                    AudioMetadataUpdate(
                        title = title.text.toString().trim().ifBlank { track.displayTitle },
                        artist = artist.text.toString().trim(),
                        composer = composer.text.toString().trim(),
                        year = year.text.toString().trim(),
                        lyrics = lyrics.text.toString(),
                        album = album.text.toString().trim(),
                        coverBytes = pendingMetadataCoverUri.takeIf { pendingMetadataCoverTrackId == track.id && it.isNotBlank() }
                            ?.let { uri -> contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } }
                    )
                )
            }
            .setNegativeButton("取消", null)
            .setOnDismissListener {
                pendingMetadataCoverLabel = null
            }
            .show()
    }

    private fun writeAudioMetadata(track: Track, file: File, update: AudioMetadataUpdate) {
        Thread {
            val result = AudioMetadataWriter().write(file, update)
            runOnUiThread {
                if (result.success) {
                    val updated = track.copy(
                        title = update.title.trim().ifBlank { track.title },
                        artist = update.artist.trim(),
                        composer = update.composer.trim(),
                        year = update.year.trim().toIntOrNull() ?: track.year,
                        album = update.album.trim()
                    )
                    replaceTrackInMemory(updated)
                    rescanTrackFile(updated)
                    Toast.makeText(this, listOf("元数据已写入", result.warnings.joinToString("；")).filter { it.isNotBlank() }.joinToString("："), Toast.LENGTH_LONG).show()
                    renderIfLibraryVisible()
                    if (page == Page.NOW_PLAYING) render(Page.NOW_PLAYING)
                } else {
                    Toast.makeText(this, "元数据写入失败：${result.warnings.joinToString("；")}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun replaceTrackInMemory(updated: Track) {
        allTracks = allTracks.map { if (it.id == updated.id) updated else it }.sortedWith(MusicScanner.trackComparator)
        visibleTracks = visibleTracks.map { if (it.id == updated.id) updated else it }
        currentQueue = currentQueue.map { if (it.id == updated.id) updated else it }
        store.saveTracks(allTracks)
        if (currentIndex in currentQueue.indices && currentQueue[currentIndex].id == updated.id) {
            updateMediaSessionMetadata(updated)
            updatePlaybackNotification()
        }
    }

    private fun rescanTrackFile(track: Track) {
        try {
            val file = localTrackFile(track) ?: return
            android.media.MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
        } catch (_: Exception) {}
    }

    private fun readTrackLyricsSafely(track: Track): String {
        if (track.lyricsUri.isBlank()) return ""
        return runCatching { scanner.readText(track.lyricsUri).orEmpty() }.getOrDefault("")
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

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "image"
    }

    private fun folderDisplayName(uriText: String): String {
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return uriText
        if (uri.scheme == "file") return uri.path.orEmpty().ifBlank { uriText }
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        return documentId?.substringAfterLast(':')?.ifBlank { documentId } ?: uri.lastPathSegment.orEmpty().ifBlank { uriText }
    }

    private fun addUniqueUri(target: MutableList<String>, uri: String) {
        if (uri.isBlank() || target.contains(uri)) return
        target += uri
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
        if (track == null || nowPageLyricsBox == null) return
        val box = nowPageLyricsBox ?: return
        val lines = runCatching { timedLyrics(track) }.getOrDefault(emptyList())
        if (box.childCount == 0) {
            if (track.lyricsUri.isBlank() || lines.isEmpty()) {
                box.addView(TextView(this).apply {
                    text = if (track.lyricsUri.isBlank()) "未找到配套歌词文件。" else "未检测到可按时间显示的 LRC 时间轴。"
                    bodyStyle(16f, Palette.MUTED)
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(108), dp(8), dp(108))
                    setOnClickListener {
                        nowPlayingArtworkHidden = false
                        render(Page.NOW_PLAYING)
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            } else {
                lines.forEachIndexed { index, line ->
                    box.addView(TextView(this).apply {
                        text = line.text
                        tag = index
                        bodyStyle(16f, Palette.MUTED)
                        gravity = Gravity.CENTER
                        setLineSpacing(dp(3).toFloat(), 1.0f)
                        setPadding(dp(8), dp(7), dp(8), dp(7))
                        attachLyricLineDoubleTap(this, line.timeMs)
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
        }
        if (lines.isEmpty()) return
        val current = lines.indexOfLast { it.timeMs <= playbackPositionMs }.coerceAtLeast(0)
        for (index in 0 until box.childCount) {
            val child = box.getChildAt(index) as? TextView ?: continue
            if (index == current) {
                child.setTextColor(themeColor)
                child.textSize = 18f
                child.paint.isFakeBoldText = true
            } else {
                child.setTextColor(Palette.MUTED)
                child.textSize = 16f
                child.paint.isFakeBoldText = false
            }
        }
        if (nowPlayingArtworkHidden && System.currentTimeMillis() - lastLyricsUserInteractionAt > 5_000L) {
            scrollLyricsToIndex(current)
        }
    }

    private fun isLyricsBlankTap(y: Float): Boolean {
        val scroll = nowPageLyricsScroll ?: return false
        val box = nowPageLyricsBox ?: return true
        val contentY = (scroll.scrollY + y).toInt()
        for (index in 0 until box.childCount) {
            val child = box.getChildAt(index)
            if (child.tag is Int && contentY in child.top..child.bottom) return false
        }
        return true
    }

    private fun attachLyricLineDoubleTap(view: TextView, timeMs: Long) {
        var lastTapAt = 0L
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastLyricsUserInteractionAt = System.currentTimeMillis()
            }
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val now = System.currentTimeMillis()
                if (now - lastTapAt <= 320L) {
                    player.seekTo(timeMs)
                    playbackPositionMs = timeMs
                    lastTapAt = 0L
                } else {
                    lastTapAt = now
                }
            }
            true
        }
    }

    private fun seekToVisibleLyric(track: Track) {
        val lines = timedLyrics(track)
        if (lines.isEmpty()) {
            nowPlayingArtworkHidden = false
            render(Page.NOW_PLAYING)
            return
        }
        val scroll = nowPageLyricsScroll ?: return
        val box = nowPageLyricsBox ?: return
        val center = scroll.scrollY + scroll.height / 2
        val best = (0 until box.childCount).minByOrNull { index ->
            val child = box.getChildAt(index)
            kotlin.math.abs((child.top + child.bottom) / 2 - center)
        } ?: return
        lines.getOrNull(best)?.let {
            player.seekTo(it.timeMs)
            playbackPositionMs = it.timeMs
            lastLyricsUserInteractionAt = System.currentTimeMillis()
        }
    }

    private fun scrollLyricsToIndex(index: Int) {
        val scroll = nowPageLyricsScroll ?: return
        val box = nowPageLyricsBox ?: return
        val child = box.getChildAt(index) ?: return
        val targetY = (child.top - scroll.height / 2 + child.height / 2).coerceAtLeast(0)
        scroll.smoothScrollTo(0, targetY)
    }

    private fun updateFloatingLyrics() {
        if (!floatingLyricsEnabled || !hasOverlayPermission() || (floatingLyricsHideInApp && appInForeground)) {
            removeFloatingLyrics()
            return
        }
        val track = player.currentTrack ?: run {
            removeFloatingLyrics()
            return
        }
        val view = floatingLyricsView ?: StrokeTextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            floatingLyricsView = this
        }
        view.textSize = floatingLyricsTextSizeSp
        view.strokeEnabled = floatingLyricsStrokeEnabled
        view.text = floatingLyricText(track)
        view.setTextColor(applyAlpha(themeColor, floatingLyricsAlpha))
        val params = floatingLyricsLayoutParams()
        if (!floatingLyricsAdded) {
            getSystemService(WindowManager::class.java).addView(view, params)
            floatingLyricsAdded = true
        } else {
            runCatching { getSystemService(WindowManager::class.java).updateViewLayout(view, params) }
        }
    }

    private fun floatingLyricsLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val height = resources.displayMetrics.heightPixels
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = when (floatingLyricsPosition) {
                FloatingLyricsPosition.TOP -> 0
                FloatingLyricsPosition.LOWER -> (height * 0.75f).toInt()
                FloatingLyricsPosition.BOTTOM -> (height - dp(96)).coerceAtLeast(0)
            }
        }
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        return Color.argb(
            (255 * alpha.coerceIn(0f, 1f)).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
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
            if (index == current) "> ${lines[index].text}" else lines[index].text
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
        darkMode = DarkMode.entries.getOrElse(prefs.getInt("darkMode", DarkMode.SYSTEM.ordinal)) { DarkMode.SYSTEM }
        includeBetaUpdates = if (prefs.contains("includeBetaUpdates")) {
            prefs.getBoolean("includeBetaUpdates", false)
        } else {
            packageName.contains("beta", ignoreCase = true) || APP_VERSION.contains("beta", ignoreCase = true)
        }
        autoCheckUpdates = prefs.getBoolean("autoCheckUpdates", true)
        notificationEnabled = prefs.getBoolean("notificationEnabled", false)
        lockscreenNotificationEnabled = prefs.getBoolean("lockscreenNotificationEnabled", false)
        floatingLyricsEnabled = prefs.getBoolean("floatingLyricsEnabled", false)
        floatingLyricsHideInApp = prefs.getBoolean("floatingLyricsHideInApp", false)
        floatingLyricsAlpha = prefs.getFloat("floatingLyricsAlpha", 1.0f).coerceIn(0.2f, 1.0f)
        floatingLyricsTextSizeSp = prefs.getFloat("floatingLyricsTextSizeSp", 15.0f).coerceIn(12.0f, 32.0f)
        floatingLyricsStrokeEnabled = prefs.getBoolean("floatingLyricsStrokeEnabled", true)
        floatingLyricsPosition = FloatingLyricsPosition.entries.getOrElse(prefs.getInt("floatingLyricsPosition", FloatingLyricsPosition.TOP.ordinal)) { FloatingLyricsPosition.TOP }
        backgroundImageUri = prefs.getString("backgroundImageUri", "") ?: ""
        backgroundAlpha = prefs.getFloat("backgroundAlpha", 0.35f).coerceIn(0f, 1f)
        skipNoMediaFolders = prefs.getBoolean("skipNoMediaFolders", false)
        debugMode = prefs.getBoolean("debugMode", false)
        autoWriteMetadata = prefs.getBoolean("autoWriteMetadata", false)
        allowMobileDataDownload = prefs.getBoolean("allowMobileDataDownload", true)
        extraScanFolderUris.replaceAllFromJson(prefs.getString("extraScanFolderUris", "") ?: "")
        skippedScanFolderUris.replaceAllFromJson(prefs.getString("skippedScanFolderUris", "") ?: "")
        playbackMode = PlaybackMode.entries.getOrElse(prefs.getInt("playbackMode", 0)) { PlaybackMode.SEQUENTIAL }
        profileName = prefs.getString("profileName", "profile")?.ifBlank { "profile" } ?: "profile"
        profileAvatarUri = prefs.getString("profileAvatarUri", "") ?: ""
        dizzylabCookie = prefs.getString("dizzylabCookie", "") ?: ""
        dizzylabUserId = prefs.getString("dizzylabUserId", "") ?: ""
        streamPreloadCount = prefs.getInt("streamPreloadCount", 1).coerceIn(0, 5)
        streamDownloadFolderUri = prefs.getString("streamDownloadFolderUri", "") ?: ""
        streamCacheLimitGb = prefs.getFloat("streamCacheLimitGb", 2.0f).coerceAtLeast(0.1f)
        streamCacheFolderUri = prefs.getString("streamCacheFolderUri", "") ?: ""
        outputDirectoryMode = OutputDirectoryMode.entries.getOrElse(prefs.getInt("outputDirectoryMode", OutputDirectoryMode.INTERNAL.ordinal)) { OutputDirectoryMode.INTERNAL }
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
        if (!prefs.contains("includeBetaUpdates")) {
            prefs.edit().putBoolean("includeBetaUpdates", includeBetaUpdates).apply()
        }
        applyAppPalette()
    }

    private fun saveSettings() {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("themeColor", themeColor)
            .putInt("darkMode", darkMode.ordinal)
            .putBoolean("includeBetaUpdates", includeBetaUpdates)
            .putBoolean("autoCheckUpdates", autoCheckUpdates)
            .putBoolean("notificationEnabled", notificationEnabled)
            .putBoolean("lockscreenNotificationEnabled", lockscreenNotificationEnabled)
            .putBoolean("floatingLyricsEnabled", floatingLyricsEnabled)
            .putBoolean("floatingLyricsHideInApp", floatingLyricsHideInApp)
            .putFloat("floatingLyricsAlpha", floatingLyricsAlpha.coerceIn(0.2f, 1.0f))
            .putFloat("floatingLyricsTextSizeSp", floatingLyricsTextSizeSp.coerceIn(12.0f, 32.0f))
            .putBoolean("floatingLyricsStrokeEnabled", floatingLyricsStrokeEnabled)
            .putInt("floatingLyricsPosition", floatingLyricsPosition.ordinal)
            .putString("backgroundImageUri", backgroundImageUri)
            .putFloat("backgroundAlpha", backgroundAlpha)
            .putBoolean("skipNoMediaFolders", skipNoMediaFolders)
            .putBoolean("debugMode", debugMode)
            .putBoolean("autoWriteMetadata", autoWriteMetadata)
            .putBoolean("allowMobileDataDownload", allowMobileDataDownload)
            .putString("extraScanFolderUris", stringListJson(extraScanFolderUris))
            .putString("skippedScanFolderUris", stringListJson(skippedScanFolderUris))
            .putInt("playbackMode", playbackMode.ordinal)
            .putString("profileName", profileName.ifBlank { "profile" })
            .putString("profileAvatarUri", profileAvatarUri)
            .putString("dizzylabCookie", dizzylabCookie)
            .putString("dizzylabUserId", dizzylabUserId)
            .putInt("streamPreloadCount", streamPreloadCount.coerceIn(0, 5))
            .putString("streamDownloadFolderUri", streamDownloadFolderUri)
            .putFloat("streamCacheLimitGb", streamCacheLimitGb)
            .putString("streamCacheFolderUri", streamCacheFolderUri)
            .putInt("outputDirectoryMode", outputDirectoryMode.ordinal)
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

    private fun stringListJson(values: List<String>): String = JSONArray(values).toString()

    private fun MutableList<String>.replaceAllFromJson(raw: String) {
        clear()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
        for (index in 0 until array.length()) {
            val value = array.optString(index)
            if (value.isNotBlank() && !contains(value)) add(value)
        }
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
            .put("darkMode", darkMode.ordinal)
            .put("includeBetaUpdates", includeBetaUpdates)
            .put("autoCheckUpdates", autoCheckUpdates)
            .put("notificationEnabled", notificationEnabled)
            .put("lockscreenNotificationEnabled", lockscreenNotificationEnabled)
            .put("floatingLyricsEnabled", floatingLyricsEnabled)
            .put("floatingLyricsHideInApp", floatingLyricsHideInApp)
            .put("floatingLyricsAlpha", floatingLyricsAlpha)
            .put("floatingLyricsTextSizeSp", floatingLyricsTextSizeSp)
            .put("floatingLyricsStrokeEnabled", floatingLyricsStrokeEnabled)
            .put("floatingLyricsPosition", floatingLyricsPosition.ordinal)
            .put("backgroundImageUri", backgroundImageUri)
            .put("backgroundAlpha", backgroundAlpha)
            .put("skipNoMediaFolders", skipNoMediaFolders)
            .put("extraScanFolderUris", JSONArray(extraScanFolderUris))
            .put("skippedScanFolderUris", JSONArray(skippedScanFolderUris))
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
            .put("outputDirectoryMode", outputDirectoryMode.ordinal)
            .put("restorePlaybackOnLaunch", restorePlaybackOnLaunch)
            .put("audioFocusBehavior", audioFocusBehavior.ordinal)
            .put("equalizerPreset", equalizerPreset)
            .put("equalizerPresets", JSONObject(equalizerPresets.mapValues { JSONArray(it.value) }))
    }

    private fun applyImportedSettings(settings: JSONObject) {
        if (settings.length() == 0) return
        themeColor = settings.optInt("themeColor", themeColor)
        darkMode = DarkMode.entries.getOrElse(settings.optInt("darkMode", darkMode.ordinal)) { DarkMode.SYSTEM }
        includeBetaUpdates = settings.optBoolean("includeBetaUpdates", includeBetaUpdates)
        autoCheckUpdates = settings.optBoolean("autoCheckUpdates", autoCheckUpdates)
        notificationEnabled = settings.optBoolean("notificationEnabled", notificationEnabled)
        lockscreenNotificationEnabled = settings.optBoolean("lockscreenNotificationEnabled", lockscreenNotificationEnabled)
        floatingLyricsEnabled = settings.optBoolean("floatingLyricsEnabled", floatingLyricsEnabled)
        floatingLyricsHideInApp = settings.optBoolean("floatingLyricsHideInApp", floatingLyricsHideInApp)
        floatingLyricsAlpha = settings.optDouble("floatingLyricsAlpha", floatingLyricsAlpha.toDouble()).toFloat().coerceIn(0.2f, 1.0f)
        floatingLyricsTextSizeSp = settings.optDouble("floatingLyricsTextSizeSp", floatingLyricsTextSizeSp.toDouble()).toFloat().coerceIn(12.0f, 32.0f)
        floatingLyricsStrokeEnabled = settings.optBoolean("floatingLyricsStrokeEnabled", floatingLyricsStrokeEnabled)
        floatingLyricsPosition = FloatingLyricsPosition.entries.getOrElse(settings.optInt("floatingLyricsPosition", floatingLyricsPosition.ordinal)) { FloatingLyricsPosition.TOP }
        backgroundImageUri = settings.optString("backgroundImageUri", backgroundImageUri)
        backgroundAlpha = settings.optDouble("backgroundAlpha", backgroundAlpha.toDouble()).toFloat().coerceIn(0f, 1f)
        skipNoMediaFolders = settings.optBoolean("skipNoMediaFolders", skipNoMediaFolders)
        settings.optJSONArray("extraScanFolderUris")?.let { array ->
            extraScanFolderUris.clear()
            for (index in 0 until array.length()) addUniqueUri(extraScanFolderUris, array.optString(index))
        }
        settings.optJSONArray("skippedScanFolderUris")?.let { array ->
            skippedScanFolderUris.clear()
            for (index in 0 until array.length()) addUniqueUri(skippedScanFolderUris, array.optString(index))
        }
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
        outputDirectoryMode = OutputDirectoryMode.entries.getOrElse(settings.optInt("outputDirectoryMode", outputDirectoryMode.ordinal)) { outputDirectoryMode }
        restorePlaybackOnLaunch = settings.optBoolean("restorePlaybackOnLaunch", restorePlaybackOnLaunch)
        audioFocusBehavior = AudioFocusBehavior.entries.getOrElse(settings.optInt("audioFocusBehavior", audioFocusBehavior.ordinal)) { AudioFocusBehavior.PAUSE }
        settings.optJSONObject("equalizerPresets")?.let { loadEqualizerPresets(it.toString()) }
        equalizerPreset = settings.optString("equalizerPreset", equalizerPreset)
        equalizerLevels = equalizerPresets[equalizerPreset]?.toMutableList() ?: MutableList(5) { 0 }
        player.setEqualizerLevels(equalizerLevels)
        scanner.skipNoMediaFolders = skipNoMediaFolders
        applyAppPalette()
        saveSettings()
    }

    private fun startupThemeStyle(): Int {
        val mode = DarkMode.entries.getOrElse(
            getSharedPreferences("settings", MODE_PRIVATE).getInt("darkMode", DarkMode.SYSTEM.ordinal)
        ) { DarkMode.SYSTEM }
        return if (isDarkModeActive(mode)) R.style.AppTheme_Dark else R.style.AppTheme_Light
    }

    private fun applyAppPalette() {
        Palette.apply(isDarkModeActive(darkMode), themeColor)
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        if (Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = if (isDarkModeActive(darkMode)) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        refreshShellColors()
    }

    private fun refreshShellColors() {
        rootLayout?.setBackgroundColor(contentOverlayColor())
        headerPanel?.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            headerGradientColors()
        ).apply { cornerRadius = dp(8).toFloat() }
        if (::headerTitle.isInitialized) headerTitle.setTextColor(Palette.TEXT)
        if (::statusText.isInitialized) statusText.setTextColor(Palette.MUTED)
        if (::miniPlayerPanel.isInitialized) miniPlayerPanel.background = panelDrawable(Palette.PANEL, 8, this)
        if (::miniTitle.isInitialized) miniTitle.setTextColor(Palette.TEXT)
        if (::miniMeta.isInitialized) miniMeta.setTextColor(Palette.MUTED)
        if (::miniThumb.isInitialized) miniThumb.background = panelDrawable(Palette.PANEL_ALT, 8, this)
        bottomNavigationPanel?.background = roundedStrokeDrawable(Palette.PANEL, Palette.PANEL_ALT, 10)
        refreshThemedViews(rootLayout)
        navButtons.forEach { (navPage, button) ->
            button.setTextColor(if (navPage == page) Palette.TEXT else Palette.MUTED)
            tintTextViewDrawables(button, if (navPage == page) Palette.TEXT else Palette.MUTED)
        }
    }

    private fun refreshThemedViews(view: View?) {
        when (view) {
            is Button -> if (view.tag == "accentButton") {
                view.setTextColor(buttonTextColor())
                view.background = panelDrawable(themeColor, 8, this)
                tintButtonDrawables(view, view.currentTextColor)
            }
            is ViewGroup -> {
                for (index in 0 until view.childCount) refreshThemedViews(view.getChildAt(index))
            }
        }
    }

    private fun buttonTextColor(): Int {
        return if (isDarkModeActive(darkMode)) Palette.TEXT else contrastTextColor(themeColor)
    }

    private fun dialogBuilder(): AlertDialog.Builder {
        val style = if (isDarkModeActive(darkMode)) R.style.AppTheme_Dialog_Dark else R.style.AppTheme_Dialog_Light
        return RoundedDialogBuilder(style)
    }

    private inner class RoundedDialogBuilder(style: Int) : AlertDialog.Builder(this@MainActivity, style) {
        override fun show(): AlertDialog {
            val dialog = super.show()
            styleNativeDialog(dialog)
            return dialog
        }

        override fun create(): AlertDialog {
            val dialog = super.create()
            dialog.setOnShowListener { styleNativeDialog(dialog) }
            return dialog
        }
    }

    private fun styleNativeDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(panelDrawable(Palette.PANEL, 14, this))
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            dialog.getButton(which)?.setTextColor(themeColor)
        }
    }

    private fun isDarkModeActive(mode: DarkMode): Boolean {
        return when (mode) {
            DarkMode.LIGHT -> false
            DarkMode.DARK -> true
            DarkMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun checkForUpdates(silent: Boolean) {
        if (silent && updateCheckStarted) return
        if (silent) updateCheckStarted = true
        Thread {
            val result = runCatching { fetchLatestRelease() }
            runOnUiThread {
                result.onSuccess { release ->
                    if (isNewerVersion(release.version, APP_VERSION)) {
                        showUpdateDialog(release)
                    } else if (!silent) {
                        Toast.makeText(this, "当前已是最新版本：$APP_VERSION", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Update check failed", error)
                    if (!silent) Toast.makeText(this, "检查更新失败，请稍后再试", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SMP/$APP_VERSION")
        }
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        val releases = JSONArray(text)
        val root = (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it) }
            .firstOrNull { release ->
                if (release.optBoolean("draft", false)) return@firstOrNull false
                val prerelease = release.optBoolean("prerelease", false)
                if (includeBetaUpdates) prerelease else !prerelease
            } ?: error("没有找到可用的 ${if (includeBetaUpdates) "预发行" else "正式"} release")
        val tag = root.optString("tag_name").ifBlank { root.optString("name") }
        return ReleaseInfo(
            version = tag.removePrefix("v").trim(),
            url = root.optString("html_url").ifBlank { RELEASES_URL }
        )
    }

    private fun showUpdateDialog(release: ReleaseInfo) {
        if (isFinishing || isDestroyed) return
        dialogBuilder()
            .setTitle("发现新版本 ${release.version}")
            .setMessage("当前版本：$APP_VERSION\n是否打开 GitHub Release 页面下载更新？")
            .setPositiveButton("打开") { _, _ -> openExternalUrl(release.url) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val count = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until count) {
            val left = latestParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun versionParts(value: String): List<Int> {
        return Regex("""\d+""").findAll(value).mapNotNull { it.value.toIntOrNull() }.toList()
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initDebugLogger() {
        WebDavClient.debugLogger = { msg -> writeDebugLog("WEBDAV", msg) }
    }

    private fun writeDebugLog(tag: String, msg: String) {
        if (!debugMode) return
        try {
            val dir = File(streamCacheDir(), "debug_logs").apply { mkdirs() }
            val file = File(dir, "webdav_debug.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
            file.appendText("[$timestamp][$tag] $msg\n")
        } catch (_: Exception) {}
    }

    private fun writeMetaDebugLog(msg: String) {
        if (!debugMode) return
        try {
            val dir = File(streamCacheDir(), "debug_logs").apply { mkdirs() }
            val file = File(dir, "metawrite_debug.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
            file.appendText("[$timestamp] $msg\n")
        } catch (_: Exception) {}
    }

    private fun loadWebDavCache(serverId: String): MutableMap<String, List<WebDavItem>> {
        try {
            val dir = File(streamCacheDir(), "webdav_index")
            val file = File(dir, "${serverId}.json")
            if (!file.exists()) return mutableMapOf()
            val ageHours = (System.currentTimeMillis() - file.lastModified()) / 3600000L
            if (ageHours > 24) { runCatching { file.delete() }; return mutableMapOf() }
            val root = JSONObject(file.readText())
            val cache = mutableMapOf<String, List<WebDavItem>>()
            root.keys().forEach { path ->
                val arr = root.optJSONArray(path) ?: return@forEach
                val items = mutableListOf<WebDavItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    items += WebDavItem(
                        name = obj.optString("n"), path = obj.optString("p"),
                        isDirectory = obj.optBoolean("d"), size = obj.optLong("s"),
                        lastModified = obj.optString("m")
                    )
                }
                cache[path] = items
            }
            return cache
        } catch (_: Exception) { return mutableMapOf() }
    }

    private fun saveWebDavCache(serverId: String, cache: Map<String, List<WebDavItem>>) {
        try {
            val dir = File(streamCacheDir(), "webdav_index").apply { mkdirs() }
            val root = JSONObject()
            cache.forEach { (path, items) ->
                val arr = JSONArray()
                items.forEach { item ->
                    arr.put(JSONObject().apply {
                        put("n", item.name); put("p", item.path)
                        put("d", item.isDirectory); put("s", item.size); put("m", item.lastModified)
                    })
                }
                root.put(path, arr)
            }
            File(dir, "${serverId}.json").writeText(root.toString())
        } catch (_: Exception) {}
    }

    private fun showWebDavManagementDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        if (store.webDavServers.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "未添加任何 WebDav 服务器\n\n取决于服务器的源文件质量，串流播放可能会非常消耗流量"
                bodyStyle(14f, Palette.MUTED)
                setPadding(dp(8), dp(4), dp(8), dp(12))
            })
        }
        store.webDavServers.forEach { server ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }
            val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textBox.addView(TextView(this).apply { text = server.displayName; titleStyle(15f); maxLines = 1 })
            textBox.addView(TextView(this).apply { text = server.url; bodyStyle(12f); maxLines = 1 })
            textBox.addView(TextView(this).apply {
                text = if (server.username.isNotBlank()) "需要认证" else "匿名访问"
                bodyStyle(11f, Palette.MUTED)
            })
            row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(actionButton("编辑") { showWebDavEditDialog(server) }, LinearLayout.LayoutParams(dp(60), dp(38)).apply { rightMargin = dp(4) })
            row.setOnLongClickListener {
                dialogBuilder().setTitle("删除服务器").setMessage("确定要删除服务器「${server.displayName}」吗？")
                    .setPositiveButton("删除") { _, _ -> store.webDavServers.removeAll { it.id == server.id }; store.save(); showWebDavManagementDialog() }
                    .setNegativeButton("取消", null).show()
                true
            }
            box.addView(row)
        }
        box.addView(actionButton("添加服务器") { showWebDavEditDialog(null) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(4) })
        scroll.addView(box)
        dialogBuilder().setTitle("WebDav 服务器管理").setView(scroll).setPositiveButton("关闭", null).show()
    }

    private fun showWebDavEditDialog(existing: WebDavServer?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(2)) }
        val urlInput = dialogInput("服务器地址 (https://...)", existing?.url.orEmpty())
        box.addView(TextView(this).apply { text = "服务器地址"; bodyStyle(12f, Palette.MUTED) })
        box.addView(urlInput)
        val userInput = dialogInput("用户名（选填）", existing?.username.orEmpty())
        box.addView(TextView(this).apply { text = "用户名（选填）"; bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(userInput)
        val passInput = dialogInput("密码（选填）", existing?.password.orEmpty())
        box.addView(TextView(this).apply { text = "密码（选填）"; bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(passInput)
        val nameInput = dialogInput("服务器备注", existing?.name.orEmpty())
        box.addView(TextView(this).apply { text = "服务器备注"; bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(nameInput)
        val aliasInput = dialogInput("服务器别名（优先显示）", existing?.alias.orEmpty())
        box.addView(TextView(this).apply { text = "服务器别名（优先显示）"; bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(aliasInput)
        val portInput = dialogInput("端口（选填，默认自动）", if (existing?.port ?: 0 > 0) existing!!.port.toString() else "")
        box.addView(TextView(this).apply { text = "端口（选填）"; bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(portInput)
        val certCheck = CheckBox(this).apply { text = "忽略证书问题"; isChecked = existing?.ignoreCert ?: false; setTextColor(Palette.TEXT); setPadding(0, dp(8), 0, 0) }
        box.addView(certCheck)
        dialogBuilder()
            .setTitle(if (existing == null) "添加 WebDav 服务器" else "编辑 WebDav 服务器")
            .setView(box)
            .setPositiveButton(if (existing == null) "添加并验证" else "保存") { _, _ ->
                val url = urlInput.text.toString().trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(this, "服务器地址必须以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val password = passInput.text.toString().trim()
                val server = WebDavServer(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = nameInput.text.toString().trim(),
                    alias = aliasInput.text.toString().trim(),
                    url = url.trimEnd('/'),
                    username = userInput.text.toString().trim(),
                    password = password,
                    port = portInput.text.toString().trim().toIntOrNull() ?: 0,
                    ignoreCert = certCheck.isChecked
                )
                setStatus("正在验证 WebDav 服务器...")
                writeDebugLog("TEST", "验证服务器: ${server.url}, port=${server.port}, user=${server.username.ifBlank { "无" }}, pwd=${password.ifBlank { "无" }}, ignoreCert=${server.ignoreCert}")
                Thread {
                    val result = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert).testConnection()
                    runOnUiThread {
                        result.onSuccess { msg ->
                            writeDebugLog("TEST", "验证成功: $msg")
                            if (existing != null) store.webDavServers.removeAll { it.id == server.id }
                            store.webDavServers.add(server); store.save()
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                            setStatus("WebDav 服务器验证成功"); showWebDavManagementDialog()
                        }.onFailure { error ->
                            writeDebugLog("TEST", "验证失败: ${error.message}")
                            Toast.makeText(this, "验证失败：${error.message}", Toast.LENGTH_SHORT).show()
                            setStatus("WebDav 服务器验证失败")
                        }
                    }
                }.start()
            }
            .setNegativeButton("取消", null).show()
    }

    private fun showWebDavServerPicker() {
        val servers = store.webDavServers.toList()
        if (servers.isEmpty()) { Toast.makeText(this, "请先在设置中添加 WebDav 服务器", Toast.LENGTH_SHORT).show(); return }
        dialogBuilder().setTitle("选择 WebDav 服务器")
            .setItems(servers.map { it.displayName }.toTypedArray()) { _, which -> connectWebDavServer(servers[which]) }.show()
    }

    private fun connectWebDavServer(server: WebDavServer) {
        streamSource = StreamSource.WEBDAV; webDavCurrentServer = server; webDavCurrentPath = ""; webDavSearchQuery = ""
        webDavCache = loadWebDavCache(server.id)
        val cached = webDavCache[""]
        if (cached != null) {
            webDavCurrentItems = cached; render(Page.STREAMING)
            setStatus("${server.displayName}：${cached.size} 项（缓存）")
            return
        }
        setStatus("正在连接 ${server.displayName}...")
        writeDebugLog("CONNECT", "连接服务器: ${server.url}, port=${server.port}")
        Thread {
            val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert)
            val items = runCatching { client.listDirectory("") }.getOrElse {
                runOnUiThread { writeDebugLog("CONNECT", "连接失败: ${it.message}"); Toast.makeText(this, "连接服务器失败：${it.message}", Toast.LENGTH_SHORT).show(); setStatus("WebDav 连接失败") }
                return@Thread
            }
            webDavCache[""] = items; saveWebDavCache(server.id, webDavCache)
            runOnUiThread { webDavCurrentItems = items; render(Page.STREAMING); setStatus("${server.displayName}：${items.size} 项") }
        }.start()
    }

    private fun navigateWebDavDir(path: String) {
        val server = webDavCurrentServer ?: return
        webDavCurrentPath = path; webDavSearchQuery = ""
        val cacheKey = path.trimEnd('/').ifBlank { "" }
        val cached = webDavCache[cacheKey]
        if (cached != null) { webDavCurrentItems = cached; render(Page.STREAMING); setStatus("${server.displayName}：${cached.size} 项（缓存）"); return }
        setStatus("正在加载目录..."); writeDebugLog("NAVIGATE", "加载目录: \"$cacheKey\"")
        Thread {
            val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert)
            val items = runCatching { client.listDirectory(cacheKey) }.getOrElse {
                runOnUiThread { writeDebugLog("NAVIGATE", "加载失败: ${it.message}"); Toast.makeText(this, "加载目录失败：${it.message}", Toast.LENGTH_SHORT).show(); setStatus("WebDav 加载失败") }
                return@Thread
            }
            webDavCache[cacheKey] = items; saveWebDavCache(server.id, webDavCache)
            runOnUiThread { webDavCurrentItems = items; render(Page.STREAMING); setStatus("${server.displayName}：${items.size} 项") }
        }.start()
    }

    private fun refreshWebDavDir(server: WebDavServer, path: String) {
        val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert)
        val items = runCatching { client.listDirectory(path) }.getOrElse { return }
        webDavCache[path] = items; saveWebDavCache(server.id, webDavCache)
        runOnUiThread { if (webDavCurrentPath == path && webDavCurrentServer?.id == server.id) { webDavCurrentItems = items; render(Page.STREAMING); setStatus("${server.displayName}：${items.size} 项") } }
    }

    private fun renderWebDavBrowser(box: LinearLayout) {
        val server = webDavCurrentServer ?: return
        box.addView(actionButton("返回流媒体来源") {
            streamSource = null; webDavCurrentServer = null; webDavCurrentPath = ""; webDavCurrentItems = emptyList(); webDavCache.clear(); webDavSearchQuery = ""; render(Page.STREAMING)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        if (webDavCurrentPath.isNotBlank()) {
            val parentPath = webDavCurrentPath.substringBeforeLast('/').ifBlank { "" }
            val navRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
            }
            navRow.addView(actionButton("返回上级") { navigateWebDavDir(parentPath) }, LinearLayout.LayoutParams(0, dp(42), 1f))
            navRow.addView(actionButton("下载整张专辑") { downloadWebDavAlbum() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(6) })
            box.addView(navRow)
        }
        val searchEdit = editText("搜索当前目录", webDavSearchQuery) { value ->
            webDavSearchQuery = value
            filterWebDavItems(box)
        }
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        searchRow.addView(searchEdit, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchRow.addView(actionButton("刷新") {
            val srv = webDavCurrentServer ?: return@actionButton; setStatus("正在刷新..."); Thread { refreshWebDavDir(srv, webDavCurrentPath); runOnUiThread { webDavSearchQuery = ""; filterWebDavItems(box) } }.start()
        }, LinearLayout.LayoutParams(dp(60), dp(42)).apply { leftMargin = dp(6) })
        box.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6); bottomMargin = dp(6) })
        val dirs = webDavCurrentItems.filter { it.isDirectory }
        val files = webDavCurrentItems.filter { !it.isDirectory && isMusicFile(it.name) }
        if (dirs.isEmpty() && files.isEmpty()) { box.addView(TextView(this).apply { text = "当前目录为空或无音乐文件"; bodyStyle(14f, Palette.MUTED); setPadding(dp(12), dp(20), dp(12), dp(20)) }); return }
        dirs.forEach { item -> box.addView(webDavItemCard(item, true) { navigateWebDavDir(item.path) }) }
        if (files.isNotEmpty()) {
            if (dirs.isNotEmpty()) { box.addView(TextView(this).apply { text = "音乐文件（${files.size}）"; bodyStyle(12f, Palette.MUTED); setPadding(dp(12), dp(8), dp(12), dp(4)) }) }
            files.forEach { item -> val track = webDavItemToTrack(item, server); box.addView(webDavTrackCard(track, item)) }
        }
        filterWebDavItems(box)
    }

    private fun filterWebDavItems(box: LinearLayout) {
        var itemCount = 0
        for (i in 0 until box.childCount) {
            val child = box.getChildAt(i)
            val tag = child.tag as? String ?: continue
            if (tag == "dav_item") {
                val card = (child as? LinearLayout)?.getChildAt(1) as? LinearLayout
                val title = (card?.getChildAt(0) as? TextView)?.text?.toString() ?: ""
                val match = webDavSearchQuery.isBlank() || title.contains(webDavSearchQuery, ignoreCase = true)
                child.visibility = if (match) View.VISIBLE else View.GONE
                if (match) itemCount++
            }
        }
        if (webDavSearchQuery.isNotBlank()) {
            setStatus("找到 $itemCount 项")
        }
    }

    private fun isMusicFile(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return listOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".wma", ".opus").any { lower.endsWith(it) }
    }

    private fun isLyricsFile(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT); return lower.endsWith(".lrc") || lower.endsWith(".txt")
    }

    private fun webDavItemToTrack(item: WebDavItem, server: WebDavServer): Track {
        val fullUrl = "${server.url.trimEnd('/')}/${item.path.trimStart('/')}"
        val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = when (ext) { "flac" -> "audio/flac"; "wav" -> "audio/wav"; "m4a" -> "audio/mp4"; "ogg" -> "audio/ogg"; "aac" -> "audio/aac"; "opus" -> "audio/ogg"; else -> "audio/mpeg" }
        return Track(id = "webdav:${fullUrl.hashCode()}", uri = fullUrl, title = item.name.substringBeforeLast('.'), artist = server.displayName, album = webDavCurrentPath.substringAfterLast('/').ifBlank { server.displayName }, durationMs = 0L, mimeType = mimeType, sourcePath = fullUrl)
    }

    private fun fetchWebDavTrackMetadata(item: WebDavItem, server: WebDavServer): Track? {
        val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert)
        val headBytes = client.fetchRangeBytes(item.path, 0, 131072) ?: return null
        return runCatching {
            val tmpFile = File(cacheDir, "webdav_meta_${System.currentTimeMillis()}")
            tmpFile.writeBytes(headBytes)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tmpFile.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            runCatching { tmpFile.delete() }
            if (title.isBlank() && artist.isBlank()) return null
            val fullUrl = "${server.url.trimEnd('/')}/${item.path.trimStart('/')}"
            val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mimeType = when (ext) { "flac" -> "audio/flac"; "wav" -> "audio/wav"; "m4a" -> "audio/mp4"; "ogg" -> "audio/ogg"; "aac" -> "audio/aac"; else -> "audio/mpeg" }
            Track(id = "webdav:${fullUrl.hashCode()}", uri = fullUrl, title = title.ifBlank { item.name.substringBeforeLast('.') }, artist = artist.ifBlank { server.displayName }, album = album.ifBlank { webDavCurrentPath.substringAfterLast('/').ifBlank { server.displayName } }, durationMs = duration, mimeType = mimeType, sourcePath = fullUrl)
        }.getOrNull()
    }

    private fun webDavItemCard(item: WebDavItem, isDir: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); background = panelDrawable(Palette.PANEL, 8, this@MainActivity); setOnClickListener { onClick() }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }; tag = "dav_item" }
        row.addView(TextView(this).apply { text = if (isDir) "\uD83D\uDCC1" else "\uD83C\uDFB5"; textSize = 22f }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) })
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply { text = item.name; titleStyle(15f); maxLines = 2 })
        if (!isDir && item.size > 0) { textBox.addView(TextView(this).apply { text = formatFileSize(item.size); bodyStyle(12f, Palette.MUTED) }) }
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); return row
    }

    private fun webDavTrackCard(track: Track, item: WebDavItem): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); background = panelDrawable(Palette.PANEL, 8, this@MainActivity); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }; setOnClickListener { playWebDavTrack(track) }; setOnLongClickListener { dialogBuilder().setTitle(track.displayTitle).setItems(arrayOf("下载")) { _, _ -> downloadWebDavTrack(track, item) }.show(); true }; tag = "dav_item" }
        row.addView(TextView(this).apply { text = "\uD83C\uDFB5"; textSize = 22f }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) })
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply { text = track.displayTitle; titleStyle(15f); maxLines = 2 })
        textBox.addView(TextView(this).apply { text = listOfNotNull(if (item.size > 0) formatFileSize(item.size) else null, item.lastModified.ifBlank { null }).joinToString(" \u00B7 "); bodyStyle(11.5f, Palette.MUTED) })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); return row
    }

    private fun formatFileSize(bytes: Long): String {
        return when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0); bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0)); else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0)) }
    }

    private fun playWebDavTrack(track: Track) {
        val server = webDavCurrentServer ?: return
        val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert)
        val streamPreload = streamPreloadCount.coerceIn(0, 5)
        val item = webDavCurrentItems.firstOrNull { webDavItemToTrack(it, server).id == track.id }
        val allMusic = webDavCurrentItems.filter { !it.isDirectory && isMusicFile(it.name) }
        val allTracks = allMusic.map { webDavItemToTrack(it, server) }
        val startIdx = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        currentQueue = if (allTracks.size > 1) {
            val preload = allTracks.drop(startIdx).take(streamPreload + 1)
            val rest = allTracks.filter { it.id !in preload.map { t -> t.id } }
            preload + rest
        } else listOf(track)
        player.headersForTrack = { t ->
            if (t.id.startsWith("webdav:")) client.streamHeaders()
            else if (t.id.startsWith("stream:dizzylab:") && dizzylabCookie.isNotBlank()) DizzylabClient(dizzylabCookie).streamHeaders()
            else emptyMap()
        }
        val cacheDir = File(streamCacheDir(), "webdav_stream").apply { mkdirs() }
        if (item != null) {
            val ext = item.name.substringAfterLast('.', "mp3")
            val cacheFile = File(cacheDir, "${item.path.hashCode()}_${safeFileName(track.displayTitle)}.$ext")
            if (cacheFile.exists()) {
                val meta = rescanMetadata(Uri.fromFile(cacheFile).toString(), track)
                val cachedTrack = meta.copy(uri = Uri.fromFile(cacheFile).toString(), sourcePath = cacheFile.absolutePath)
                playTrack(cachedTrack, currentQueue)
            } else {
                setStatus("正在缓冲 ${track.displayTitle}...")
                Thread {
                    val bytes = runCatching { client.download(item.path) }.getOrElse {
                        runOnUiThread { Toast.makeText(this, "缓冲失败", Toast.LENGTH_SHORT).show(); setStatus("缓冲失败") }
                        return@Thread
                    }
                    cacheFile.writeBytes(bytes)
                    enforceStreamCacheLimit()
                    val meta = rescanMetadata(Uri.fromFile(cacheFile).toString(), track)
                    val cachedTrack = meta.copy(uri = Uri.fromFile(cacheFile).toString(), sourcePath = cacheFile.absolutePath)
                    runOnUiThread { playTrack(cachedTrack, currentQueue) }
                }.start()
            }
            preloadWebDavTracks(client, allMusic, allTracks, item, cacheDir)
            return
        }
        playTrack(track, currentQueue)
    }

    private fun preloadWebDavTracks(client: WebDavClient, allItems: List<WebDavItem>, allTracks: List<Track>, current: WebDavItem, cacheDir: File) {
        val preloadCount = streamPreloadCount.coerceIn(0, 5)
        if (preloadCount <= 0) return
        val startIdx = allItems.indexOf(current)
        if (startIdx < 0) return
        allItems.drop(startIdx + 1).take(preloadCount).forEach { item ->
            val ext = item.name.substringAfterLast('.', "mp3")
            val cacheFile = File(cacheDir, "${item.path.hashCode()}_${safeFileName(item.name.substringBeforeLast('.'))}.$ext")
            if (!cacheFile.exists()) {
                Thread {
                    runCatching {
                        val bytes = client.download(item.path)
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeBytes(bytes)
                        enforceStreamCacheLimit()
                    }
                }.start()
            }
        }
    }

    private fun downloadWebDavTrack(track: Track, item: WebDavItem) {
        val server = webDavCurrentServer ?: return
        if (!ensureStreamDownloadFolderReady { downloadWebDavTrack(track, item) }) return
        val folderName = item.path.substringBeforeLast('/').substringAfterLast('/').ifBlank {
            webDavCurrentPath.substringAfterLast('/').ifBlank { server.name.ifBlank { server.url.substringAfterLast('/').trimEnd('/').ifBlank { "webdav" } } }
        }
        writeDebugLog("DOWNLOAD", "下载文件夹: $folderName, 文件: ${item.name}")
        checkMobileDataForDownload {
        setStatus("正在下载：${track.displayTitle}"); writeDebugLog("DOWNLOAD", "开始下载: ${item.path}")
        Thread {
            val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert)
            runCatching {
                val ext = item.name.substringAfterLast('.', "mp3")
                val cacheFile = File(streamCacheDir(), "webdav_stream/${item.path.hashCode()}_${safeFileName(track.displayTitle)}.$ext")
                val bytes = if (cacheFile.exists()) cacheFile.readBytes() else { val b = client.download(item.path); cacheFile.parentFile?.mkdirs(); cacheFile.writeBytes(b); b }
                val fileName = safeFileName("${track.displayTitle}.$ext")
                val lyricsBytes = tryDownloadWebDavLyrics(client, item)
                val lyricsUri = if (lyricsBytes != null) { writeStreamDownloadBytes("webdav", folderName, safeFileName("${track.displayTitle}.lrc"), "text/plain", lyricsBytes) } else ""
                val uri = writeStreamDownloadBytes("webdav", folderName, fileName, track.mimeType.ifBlank { "audio/mpeg" }, bytes)
                val scanned = rescanMetadata(uri, track)
                val downloaded = scanned.copy(id = "download:webdav:${uri.hashCode()}", uri = uri, lyricsUri = lyricsUri, sourcePath = localPathForUriString(uri).ifBlank { fileName })
                downloadWebDavExtraFiles(client, item, folderName)
                runOnUiThread {
                    allTracks = (allTracks.filter { it.sourcePath != downloaded.sourcePath || it.id.startsWith("download:") != downloaded.id.startsWith("download:") } + downloaded).distinctBy { it.id }.sortedWith(MusicScanner.trackComparator)
                    store.saveTracks(allTracks)
                    store.createAlbumPlaylistsIfNeeded(allTracks)
                    renderIfLibraryVisible()
                    writeDebugLog("DOWNLOAD", "下载完成: ${track.displayTitle}")
                    Toast.makeText(this, "下载完成：${track.displayTitle}", Toast.LENGTH_SHORT).show()
                    setStatus("下载完成：${track.displayTitle}")
                }
            }.onFailure { error -> runOnUiThread { writeDebugLog("DOWNLOAD", "下载失败: ${error.message}"); Toast.makeText(this, "下载失败：${error.message}", Toast.LENGTH_SHORT).show(); setStatus("下载失败") } }
        }.start()
        }
    }

    private fun downloadWebDavExtraFiles(client: WebDavClient, item: WebDavItem, folderName: String) {
        val dirPath = item.path.substringBeforeLast('/')
        if (dirPath.isBlank()) return
        runCatching {
            val items = client.listDirectory(dirPath)
            items.filter { it.name.endsWith(".cue", ignoreCase = true) || it.name.endsWith(".jpg", ignoreCase = true) || it.name.endsWith(".jpeg", ignoreCase = true) || it.name.endsWith(".png", ignoreCase = true) }.forEach { extra ->
                val extraBytes = client.download(extra.path)
                if (extraBytes.isNotEmpty()) {
                    val mime = when { extra.name.lowercase().endsWith(".png") -> "image/png"; extra.name.lowercase().endsWith(".jpg") || extra.name.lowercase().endsWith(".jpeg") -> "image/jpeg"; else -> "text/plain" }
                    writeStreamDownloadBytes("webdav", folderName, safeFileName(extra.name), mime, extraBytes)
                }
            }
        }
    }

    private fun rescanMetadata(uri: String, fallback: Track): Track {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, Uri.parse(uri))
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.ifBlank { null }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.ifBlank { null }
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.ifBlank { null }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val coverBytes = retriever.embeddedPicture
            val artworkPath = if (coverBytes != null) {
                val artDir = File(filesDir, "artwork").apply { mkdirs() }
                val artFile = File(artDir, "${uri.hashCode()}.jpg")
                artFile.writeBytes(coverBytes)
                artFile.absolutePath
            } else fallback.artworkPath
            retriever.release()
            fallback.copy(
                title = title ?: fallback.title,
                artist = artist ?: fallback.artist,
                album = album ?: fallback.album,
                durationMs = duration ?: fallback.durationMs,
                artworkPath = artworkPath
            )
        }.getOrElse { fallback }
    }

    private fun tryDownloadWebDavLyrics(client: WebDavClient, item: WebDavItem): ByteArray? {
        return runCatching {
            val dirPath = item.path.substringBeforeLast('/'); val baseName = item.name.substringBeforeLast('.')
            val items = client.listDirectory(dirPath)
            val lrcItem = items.firstOrNull { !it.isDirectory && isLyricsFile(it.name) && it.name.substringBeforeLast('.').equals(baseName, ignoreCase = true) } ?: return null
            client.download(lrcItem.path)
        }.getOrNull()
    }

    private fun downloadWebDavAlbum() {
        val server = webDavCurrentServer ?: return
        val allFiles = webDavCurrentItems.filter { !it.isDirectory && isMusicFile(it.name) }
        if (allFiles.isEmpty()) { Toast.makeText(this, "当前目录没有音乐文件", Toast.LENGTH_SHORT).show(); return }
        if (!ensureStreamDownloadFolderReady { downloadWebDavAlbum() }) return
        checkMobileDataForDownload {
        val folderName = webDavCurrentPath.substringAfterLast('/').ifBlank { server.name }
        val total = allFiles.size
        setStatus("正在下载专辑 (0/$total)...")
        updateDownloadNotification("下载专辑", 0, total)
        Thread {
            val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert)
            val downloaded = mutableListOf<Track>()
            allFiles.forEachIndexed { index, item ->
                runCatching {
                    val bytes = client.download(item.path)
                    val track = webDavItemToTrack(item, server)
                    val fileName = safeFileName("${track.displayTitle}.${track.uri.substringBefore("?").substringAfterLast('.', "mp3")}")
                    val lyricsBytes = tryDownloadWebDavLyrics(client, item)
                    val lyricsUri = if (lyricsBytes != null) { val lrcName = safeFileName("${track.displayTitle}.lrc"); writeStreamDownloadBytes("webdav", folderName, lrcName, "text/plain", lyricsBytes) } else ""
                    val uri = writeStreamDownloadBytes("webdav", folderName, fileName, track.mimeType.ifBlank { "audio/mpeg" }, bytes)
                    val scanned = rescanMetadata(uri, track)
                    downloaded += scanned.copy(id = "download:webdav:${uri.hashCode()}", uri = uri, lyricsUri = lyricsUri, sourcePath = localPathForUriString(uri).ifBlank { fileName })
                }
                runOnUiThread { updateDownloadNotification("下载专辑", index + 1, total); setStatus("正在下载专辑 (${index + 1}/$total)...") }
            }
            if (downloaded.isNotEmpty()) {
                addDownloadedTracksToLibrary(downloaded, StreamAlbumDetails(album = StreamAlbum(server.name, server.name, server.url), tracks = downloaded, circle = server.name))
            }
            runOnUiThread {
                finishDownloadNotification("下载完成", downloaded.size, total)
                Toast.makeText(this, "专辑下载完成：${downloaded.size} / $total", Toast.LENGTH_LONG).show()
                setStatus("下载完成：${downloaded.size} / $total")
            }
        }.start()
        }
    }

    private fun readBundledReadme(): String {
        return runCatching {
            assets.open("readme.md").bufferedReader().use { it.readText() }
        }.getOrElse { "README 内容读取失败。" }
    }

    private fun backgroundSilentRescan() {
        val tracks = allTracks.toList()
        if (tracks.isEmpty()) return
        var changes = 0
        val updatedTracks = tracks.toMutableList()
        tracks.forEachIndexed { index, track ->
            if (track.isCueTrack || track.id.startsWith("stream:") || track.id.startsWith("download:webdav:")) return@forEachIndexed
            val file = localTrackFile(track)
            if (file == null || !file.exists()) return@forEachIndexed
            val metadata = readFileMetadataDirectly(file)
            if (metadata != null) {
                val current = updatedTracks[index]
                val changed = metadata.title.isNotBlank() && metadata.title != current.title ||
                    metadata.artist.isNotBlank() && metadata.artist != current.artist ||
                    metadata.album.isNotBlank() && metadata.album != current.album ||
                    metadata.durationMs > 0 && metadata.durationMs != current.durationMs
                if (changed) {
                    updatedTracks[index] = current.copy(
                        title = metadata.title.ifBlank { current.title },
                        artist = metadata.artist.ifBlank { current.artist },
                        album = metadata.album.ifBlank { current.album },
                        durationMs = if (metadata.durationMs > 0) metadata.durationMs else current.durationMs
                    )
                    changes++
                }
            }
        }
        if (changes > 0) {
            runOnUiThread {
                allTracks = updatedTracks.sortedWith(MusicScanner.trackComparator)
                visibleTracks = if (page == Page.LIBRARY) allTracks.filter { track ->
                    libraryQuery.isBlank() || track.displayTitle.contains(libraryQuery, ignoreCase = true) ||
                        track.displayArtist.contains(libraryQuery, ignoreCase = true)
                } else visibleTracks
                store.saveTracks(allTracks)
                renderIfLibraryVisible()
                renderIfPlaylistChanged()
            }
        }
    }

    private fun readFileMetadataDirectly(file: File): Track? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            Track(id = "", uri = "", title = title, artist = artist, album = album, durationMs = duration)
        }.getOrNull()
    }

    private fun fetchLastFmMetadata() {
        fetchLastFmMetadataForTracks(allTracks.filter { track ->
            !track.isCueTrack && (isUnknownOrBlank(track.title) || isUnknownOrBlank(track.artist) || isUnknownOrBlank(track.album) || track.durationMs <= 0L)
        })
    }

    private fun fetchLastFmMetadataForTracks(tracks: List<Track>) {
        val candidates = tracks.filter { track ->
            !track.isCueTrack && (isUnknownOrBlank(track.title) || isUnknownOrBlank(track.artist) || isUnknownOrBlank(track.album) || track.durationMs <= 0L || track.artworkPath.isBlank())
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "没有需要补充元数据的曲目", Toast.LENGTH_SHORT).show()
            return
        }
        val total = candidates.size
        val matched = java.util.concurrent.atomic.AtomicInteger(0)
        val processed = java.util.concurrent.atomic.AtomicInteger(0)
        setStatus("Last.fm 元数据匹配 (0/$total)...")
        updateLyricsNotification("Last.fm 元数据匹配", 0, total, 0)
        val executor = Executors.newFixedThreadPool(5)
        val latch = CountDownLatch(candidates.size)
        candidates.forEach { track ->
            executor.execute {
                try {
                    val result = queryLastFmSmart(track)
                    if (result != null) {
                        val updated = track.copy(
                            title = if (isUnknownOrBlank(track.title) && result.title.isNotBlank()) result.title else track.title,
                            artist = if (isUnknownOrBlank(track.artist) && result.artist.isNotBlank()) result.artist else track.artist,
                            album = if (isUnknownOrBlank(track.album) && result.album.isNotBlank()) result.album else track.album,
                            durationMs = if (track.durationMs <= 0L && result.durationMs > 0L) result.durationMs else track.durationMs
                        )
                        if (updated.title != track.title || updated.artist != track.artist || updated.album != track.album || updated.durationMs != track.durationMs) {
                                runOnUiThread {
                                allTracks = allTracks.map { if (it.id == track.id) updated else it }.sortedWith(MusicScanner.trackComparator)
                                visibleTracks = visibleTracks.map { if (it.id == track.id) updated else it }
                                currentQueue = currentQueue.map { if (it.id == track.id) updated else it }
                                store.saveTracks(allTracks)
                                if (autoWriteMetadata) {
                                    Thread { writeMetadataToFile(updated) }.start()
                                }
                            }
                            matched.incrementAndGet()
                        }
                        if (track.artworkPath.isBlank() && updated.artist.isNotBlank() && updated.album.isNotBlank()) {
                            val artUrl = queryLastFmAlbumArt(updated.artist, updated.album)
                            if (artUrl != null) {
                                val artFinal = updated.copy(artworkPath = artUrl)
                                runOnUiThread {
                                    allTracks = allTracks.map { if (it.id == track.id) artFinal else it }.sortedWith(MusicScanner.trackComparator)
                                    visibleTracks = visibleTracks.map { if (it.id == track.id) artFinal else it }
                                    currentQueue = currentQueue.map { if (it.id == track.id) artFinal else it }
                                    store.saveTracks(allTracks)
                                }
                                if (autoWriteMetadata) {
                                    val artBytes = downloadCoverImage(artUrl)
                                    if (artBytes != null && artBytes.isNotEmpty()) {
                                        val file = localTrackFile(track)
                                        if (file != null) {
                                            writeCoverToFile(file, artBytes)
                                        } else {
                                            writeMetaDebugLog("封面写入跳过: 文件路径解析失败, sourcePath=${track.sourcePath}")
                                        }
                                    } else {
                                        writeMetaDebugLog("封面下载失败: URL=$artUrl")
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                val done = processed.incrementAndGet()
                runOnUiThread {
                    updateLyricsNotification("Last.fm 元数据匹配", done, total, matched.get())
                    setStatus("Last.fm ($done/$total) 匹配 ${matched.get()} 首")
                }
                latch.countDown()
            }
        }
        Thread {
            latch.await(5, TimeUnit.MINUTES)
            executor.shutdownNow()
            runOnUiThread {
                finishLyricsNotification("Last.fm 完成", matched.get(), total)
                Toast.makeText(this, "Last.fm 匹配完成：${matched.get()} / $total", Toast.LENGTH_LONG).show()
                setStatus("Last.fm 完成：${matched.get()} / $total")
                if (autoWriteMetadata && !hasAllFilesAccess()) {
                    Toast.makeText(this, "元数据自动写入需要文件管理权限", Toast.LENGTH_LONG).show()
                }
                renderIfLibraryVisible()
            }
        }.start()
    }

    private fun isUnknownOrBlank(value: String): Boolean {
        return value.isBlank() || value.trim().equals("<unknown>", ignoreCase = true)
    }

    private fun queryLastFmSmart(track: Track): Track? {
        val title = if (!isUnknownOrBlank(track.title)) track.title else track.sourcePath.substringAfterLast('/').substringBeforeLast('.')
        val artist = if (!isUnknownOrBlank(track.artist)) track.artist else ""
        if (title.isBlank()) return null
        val params = mutableListOf(
            "method" to "track.getInfo",
            "api_key" to LASTFM_API_KEY,
            "track" to URLEncoder.encode(title, "UTF-8"),
            "format" to "json"
        )
        if (artist.isNotBlank()) params += "artist" to URLEncoder.encode(artist, "UTF-8")
        val query = params.joinToString("&") { "${it.first}=${it.second}" }
        return runCatching {
            val url = URL("$LASTFM_BASE?$query")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("User-Agent", "SMP/$APP_VERSION")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val trackObj = root.optJSONObject("track") ?: return null
            val fetchedTitle = trackObj.optString("name").trim().ifBlank { null }
            val fetchedArtist = trackObj.optJSONObject("artist")?.optString("name")?.trim()?.ifBlank { null }
            val fetchedAlbum = trackObj.optJSONObject("album")?.optString("title")?.trim()?.ifBlank { null }
            val fetchedDuration = trackObj.optLong("duration", 0L).let { if (it > 0) it * 1000L else 0L }
            if (fetchedTitle == null && fetchedArtist == null) return null
            Track(
                id = track.id, uri = track.uri,
                title = fetchedTitle ?: title,
                artist = fetchedArtist ?: artist,
                album = fetchedAlbum ?: track.album,
                durationMs = if (fetchedDuration > 0) fetchedDuration else track.durationMs,
                sourcePath = track.sourcePath, artworkPath = track.artworkPath,
                lyricsUri = track.lyricsUri
            )
        }.onFailure { Log.w(TAG, "Last.fm query failed: $title", it) }.getOrNull()
    }

    private fun queryLastFmAlbumArt(artist: String, album: String): String? {
        return runCatching {
            val params = listOf(
                "method" to "album.getInfo",
                "api_key" to LASTFM_API_KEY,
                "artist" to URLEncoder.encode(artist, "UTF-8"),
                "album" to URLEncoder.encode(album, "UTF-8"),
                "autocorrect" to "1",
                "format" to "json"
            ).joinToString("&") { "${it.first}=${it.second}" }
            val url = URL("$LASTFM_BASE?$params")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("User-Agent", "SMP/$APP_VERSION")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val albumObj = JSONObject(body).optJSONObject("album") ?: return null
            val images = albumObj.optJSONArray("image") ?: return null
            val sizes = listOf("extralarge", "large", "medium", "small")
            for (size in sizes) {
                for (i in 0 until images.length()) {
                    val img = images.optJSONObject(i) ?: continue
                    if (img.optString("size").equals(size, ignoreCase = true)) {
                        val imgUrl = img.optString("#text").trim()
                        if (imgUrl.isNotBlank()) return imgUrl
                    }
                }
            }
            null
        }.onFailure { Log.w(TAG, "Last.fm album art failed: $artist - $album", it) }.getOrNull()
    }

    private fun fetchLastFmAlbumArtForTrack(track: Track) {
        if (track.artist.isBlank() || track.album.isBlank()) {
            Toast.makeText(this, "需要歌手和专辑信息才能获取封面", Toast.LENGTH_SHORT).show()
            return
        }
        setStatus("正在获取封面：${track.displayTitle}...")
        Thread {
            val artUrl = queryLastFmAlbumArt(track.artist, track.album)
            val artBytes = if (autoWriteMetadata && artUrl != null) downloadCoverImage(artUrl) else null
            runOnUiThread {
                if (artUrl != null) {
                    val updated = track.copy(artworkPath = artUrl)
                    allTracks = allTracks.map { if (it.id == track.id) updated else it }.sortedWith(MusicScanner.trackComparator)
                    visibleTracks = visibleTracks.map { if (it.id == track.id) updated else it }
                    currentQueue = currentQueue.map { if (it.id == track.id) updated else it }
                    store.saveTracks(allTracks)
                    renderIfLibraryVisible()
                    if (player.currentTrack?.id == track.id) updateNowPlayingViews(updated)
                    Toast.makeText(this, "封面已更新", Toast.LENGTH_SHORT).show()
                    setStatus("封面已更新：${track.displayTitle}")
                } else {
                    Toast.makeText(this, "未找到封面", Toast.LENGTH_SHORT).show()
                    setStatus("未找到封面")
                }
            }
            if (artBytes != null) {
                val file = localTrackFile(track)
                if (file != null) {
                    writeCoverToFile(file, artBytes)
                }
            }
        }.start()
    }

    private fun writeMetadataToFile(track: Track) {
        val file = localTrackFile(track) ?: return
        runCatching {
            val result = AudioMetadataWriter().write(file, AudioMetadataUpdate(
                title = track.displayTitle,
                artist = track.displayArtist,
                album = track.displayAlbum,
                year = track.date.take(4)
            ))
            if (!result.success) {
                writeMetaDebugLog("元数据写入失败: ${track.displayTitle} - ${result.warnings.joinToString("; ")}")
            }
        }.onFailure { writeMetaDebugLog("元数据写入异常: ${track.displayTitle} - ${it.message}") }
    }

    private fun downloadCoverImage(url: String): ByteArray? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000; readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (connection.responseCode !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(200) ?: ""
                throw Exception("HTTP ${connection.responseCode} $err")
            }
            connection.inputStream.use { it.readBytes() }
        }.onFailure {
            writeMetaDebugLog("封面下载失败: ${it.message ?: it.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun writeCoverToFile(file: File, coverBytes: ByteArray) {
        val result = AudioMetadataWriter().writeCoverOnly(file, coverBytes)
        if (!result.success) {
            writeMetaDebugLog("封面写入失败: ${file.name} - ${result.warnings.joinToString("; ")}")
        }
    }

    private fun isOnMobileData(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        val connectivityManager = getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun checkMobileDataForDownload(onProceed: () -> Unit) {
        if (allowMobileDataDownload || !isOnMobileData()) {
            onProceed()
        } else {
            dialogBuilder()
                .setTitle("流量提醒")
                .setMessage("当前正在使用移动数据，下载可能消耗较多流量。是否继续？")
                .setPositiveButton("继续下载") { _, _ -> onProceed() }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun createNotificationChannel() {
        val playbackChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "播放控制", NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(playbackChannel)
        notificationManager.createNotificationChannel(
            NotificationChannel(DOWNLOAD_CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun updatePlaybackNotification() {
        if (!notificationEnabled && !lockscreenNotificationEnabled) {
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.cancel(FLOATING_LYRICS_NOTIFICATION_ID)
            return
        }
        val track = player.currentTrack ?: run {
            notificationManager.cancel(FLOATING_LYRICS_NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationManager.cancel(FLOATING_LYRICS_NOTIFICATION_ID)
            return
        }
        val artwork = decodeArtworkPath(resolvedArtworkPath(track))
        val playPause = if (player.isPlaying()) "暂停" else "播放"
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_disc)
            .setContentTitle(displayTitle(track))
            .setContentText(listOf(track.displayArtist, track.displayAlbum).filter { it.isNotBlank() }.joinToString(" · "))
            .setLargeIcon(artwork)
            .setCategory(Notification.CATEGORY_TRANSPORT)
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
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
        updateFloatingLyricsToggleNotification()
    }

    private fun updateFloatingLyricsToggleNotification() {
        if (!player.isPlaying() || (!notificationEnabled && !lockscreenNotificationEnabled)) {
            notificationManager.cancel(FLOATING_LYRICS_NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationManager.cancel(FLOATING_LYRICS_NOTIFICATION_ID)
            return
        }
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_musiclist)
            .setContentTitle("悬浮歌词")
            .setContentText(if (floatingLyricsEnabled) "已开启，点击关闭" else "已关闭，点击开启")
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(false)
            .setShowWhen(false)
            .setVisibility(if (lockscreenNotificationEnabled) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
            .setContentIntent(playbackPendingIntent(ACTION_TOGGLE_FLOATING_LYRICS))
            .build()
        notificationManager.notify(FLOATING_LYRICS_NOTIFICATION_ID, notification)
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
            ACTION_TOGGLE_FLOATING_LYRICS -> toggleFloatingLyricsFromNotification()
            ACTION_REWIND -> player.seekTo((playbackPositionMs - 10_000L).coerceAtLeast(0L))
            ACTION_FORWARD -> player.seekTo((playbackPositionMs + 10_000L).coerceAtMost(playbackDurationMs))
        }
        updatePlaybackNotification()
    }

    private fun toggleFloatingLyricsFromNotification() {
        floatingLyricsEnabled = !floatingLyricsEnabled
        saveSettings()
        if (floatingLyricsEnabled && !hasOverlayPermission()) {
            floatingLyricsEnabled = false
            saveSettings()
        }
        updateFloatingLyrics()
        updateFloatingLyricsToggleNotification()
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

    private fun openScanFolderPicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
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

    private fun openMetadataCoverPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_METADATA_COVER)
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
        dialogBuilder()
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
        dialogBuilder()
            .setTitle(details.album.title)
            .setItems(arrayOf("下载整张专辑")) { _, _ ->
                downloadStreamAlbum(details)
            }
            .show()
    }

    private fun ensureStreamDownloadFolderReady(onReady: () -> Unit): Boolean {
        if (streamDownloadFolderUri.isBlank() || isStreamDownloadFolderUsable()) return true
        dialogBuilder()
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
            val taggedBytes = tagDownloadedStreamBytes(fileName, bytes, track, details, coverPath)
            val uri = writeStreamDownloadBytes("dizzylab", details.album.title, fileName, track.mimeType.ifBlank { "audio/mpeg" }, taggedBytes)
            track.copy(
                id = "download:dizzylab:${uri.hashCode()}",
                uri = uri,
                album = details.album.title,
                artist = track.artist.ifBlank { details.circle },
                sourcePath = localPathForUriString(uri).ifBlank { fileName },
                artworkPath = coverPath
            )
        }.onFailure {
            Log.w(TAG, "DizzyLab track download failed: ${track.uri}", it)
        }.getOrNull()
    }

    private fun tagDownloadedStreamBytes(
        fileName: String,
        bytes: ByteArray,
        track: Track,
        details: StreamAlbumDetails,
        coverPath: String
    ): ByteArray {
        val dir = File(cacheDir, "stream_metadata").apply { mkdirs() }
        val file = File(dir, safeFileName("${System.currentTimeMillis()}-$fileName"))
        return runCatching {
            file.writeBytes(bytes)
            val coverBytes = coverPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile }?.readBytes()
            AudioMetadataWriter().write(
                file,
                AudioMetadataUpdate(
                    title = track.displayTitle,
                    artist = track.artist.ifBlank { details.circle },
                    composer = track.composer,
                    year = track.date.take(4).ifBlank { details.releaseDate.take(4) },
                    album = details.album.title,
                    coverBytes = coverBytes
                )
            )
            file.readBytes()
        }.getOrElse { error ->
            Log.w(TAG, "Stream metadata write failed: $fileName", error)
            bytes
        }.also {
            runCatching { file.delete() }
        }
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
            val sourcePaths = normalized.map { it.sourcePath }.toSet()
            allTracks = (allTracks.filter { it.sourcePath !in sourcePaths } + normalized).sortedWith(MusicScanner.trackComparator)
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

    private fun updateLyricsNotification(title: String, progress: Int, max: Int, matched: Int) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = Notification.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_disc)
            .setContentTitle(title)
            .setContentText("已处理 $progress / $max，匹配 $matched 首")
            .setProgress(max.coerceAtLeast(1), progress.coerceAtLeast(0), false)
            .setOngoing(true)
            .build()
        notificationManager.notify(LYRICS_NOTIFICATION_ID, notification)
    }

    private fun finishLyricsNotification(title: String, matched: Int, max: Int) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = Notification.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_disc)
            .setContentTitle(title)
            .setContentText("匹配 $matched / $max 首")
            .setAutoCancel(true)
            .build()
        notificationManager.notify(LYRICS_NOTIFICATION_ID, notification)
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
                runCatching {
                    val outputDir = ncmOutputDirectory(uri)
                    val result = converter.convert(uri, outputDir)
                    if (outputDirectoryMode == OutputDirectoryMode.DOWNLOAD) {
                        val mimeType = if (result.format == "flac") "audio/flac" else "audio/mpeg"
                        val outputUri = writeDownloadOutputBytes("ConvertedMusic", result.outputFile.name, mimeType, result.outputFile.readBytes())
                        result.outputFile.delete()
                        outputUri
                    } else {
                        result.outputFile.absolutePath
                    }
                }
                    .onSuccess { result ->
                        success++
                        results += result
                    }
                    .onFailure { error ->
                        results += "失败：${error.message ?: uri}"
                    }
            }
            runOnUiThread {
                val message = "转换完成：$success / ${uris.size}\n输出位置：${outputDirectoryMode.label}"
                dialogBuilder()
                    .setTitle("音乐格式转换")
                    .setMessage(message + if (results.isNotEmpty()) "\n\n" + results.take(6).joinToString("\n") else "")
                    .setPositiveButton("完成", null)
                    .show()
                setStatus("音乐格式转换完成：$success / ${uris.size}")
            }
        }.start()
    }

    private fun ncmOutputDirectory(uri: Uri): File? {
        return when (outputDirectoryMode) {
            OutputDirectoryMode.INTERNAL, OutputDirectoryMode.DOWNLOAD -> File(getExternalFilesDir(null) ?: filesDir, "ConvertedMusic").apply { mkdirs() }
            OutputDirectoryMode.SOURCE -> {
                if (!hasAllFilesAccess()) return File(getExternalFilesDir(null) ?: filesDir, "ConvertedMusic").apply { mkdirs() }
                localFileFromUri(uri)?.parentFile?.takeIf { it.canWrite() }
                    ?: File(getExternalFilesDir(null) ?: filesDir, "ConvertedMusic").apply { mkdirs() }
            }
        }
    }

    private fun localFileFromUri(uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let { File(it) }?.takeIf { it.exists() }
        if (uri.scheme != "content") return null
        return runCatching {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()?.let { File(it) }?.takeIf { it.exists() }
    }

    private fun localPathForUriString(uriText: String): String {
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return ""
        if (uri.scheme == "file") return uri.path.orEmpty()
        return localFileFromUri(uri)?.absolutePath.orEmpty()
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
            mixColor(Palette.PANEL, themeColor, 0.68f),
            mixColor(Palette.PANEL_ALT, themeColor, 0.58f),
            mixColor(Palette.PANEL, themeColor, 0.78f)
        )
    }

    private fun contentOverlayColor(): Int {
        if (backgroundImageUri.isBlank()) return Palette.BG
        val overlayAlpha = (226 - (backgroundAlpha.coerceIn(0f, 1f) * 156)).toInt().coerceIn(70, 226)
        return Color.argb(overlayAlpha, Color.red(Palette.BG), Color.green(Palette.BG), Color.blue(Palette.BG))
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
            applyCardPressEffect(this)
        }
    }

    private fun nowPlayingMetaText(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            bodyStyle(14f, Palette.ACCENT)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
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

    private fun attachArtworkSwipe(
        view: View,
        onLeft: () -> Unit,
        onRight: () -> Unit,
        onUp: () -> Unit,
        onDown: () -> Unit,
        onSingleTap: () -> Unit,
        onDoubleTap: () -> Unit
    ) {
        var startX = 0f
        var startY = 0f
        var lastTapAt = 0L
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    val isTap = kotlin.math.abs(dx) < dp(18) && kotlin.math.abs(dy) < dp(18)
                    val now = System.currentTimeMillis()
                    if (isTap && now - lastTapAt <= 320L) {
                        lastTapAt = 0L
                        onDoubleTap()
                    } else {
                        if (isTap) {
                            lastTapAt = now
                            view.postDelayed({
                                if (lastTapAt == now) {
                                    lastTapAt = 0L
                                    onSingleTap()
                                }
                            }, 330L)
                        } else {
                            handleFourWaySwipe(startX, startY, event.x, event.y, onLeft, onRight, onUp, onDown)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun attachNowPlayingBlankGestures(view: View, track: Track) {
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
                    val dx = event.x - startX
                    val dy = event.y - startY
                    val isTap = kotlin.math.abs(dx) < dp(18) && kotlin.math.abs(dy) < dp(18)
                    if (isTap) {
                        nowPlayingArtworkHidden = !nowPlayingArtworkHidden
                        render(Page.NOW_PLAYING)
                    } else if (kotlin.math.abs(dy) >= dp(72) && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.4f) {
                        if (dy < 0) showCurrentQueueDialog() else showTrackDetailsDialog(track)
                    }
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

    private fun handleFourWaySwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        onLeft: () -> Unit,
        onRight: () -> Unit,
        onUp: () -> Unit,
        onDown: () -> Unit
    ): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        val absX = kotlin.math.abs(dx)
        val absY = kotlin.math.abs(dy)
        if (maxOf(absX, absY) < dp(72)) return false
        if (absX > absY * 1.25f) {
            if (dx < 0) onLeft() else onRight()
            return true
        }
        if (absY > absX * 1.25f) {
            if (dy < 0) onUp() else onDown()
            return true
        }
        return false
    }

    private fun scanAction(title: String, subtitle: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = panelDrawable(Palette.PANEL, 8, this@MainActivity)
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
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
            setTextColor(Palette.TEXT)
            setHintTextColor(Palette.MUTED)
            textSize = 15f
            background = panelDrawable(Palette.PANEL_ALT, 8, this@MainActivity)
            setPadding(dp(12), 0, dp(12), 0)
        }
    }

    private fun actionButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            tag = "accentButton"
            this.text = text
            isAllCaps = false
            setTextColor(buttonTextColor())
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(6), 0, dp(6), 0)
            background = panelDrawable(themeColor, 8, this@MainActivity)
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
        }
    }

    private fun navButton(text: String, iconRes: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 12.5f
            includeFontPadding = false
            setPadding(0, dp(4), 0, dp(3))
            setCompoundDrawables(null, tintedDrawable(iconRes, Palette.MUTED, 22), null, null)
            compoundDrawablePadding = dp(3)
            setTextColor(Palette.MUTED)
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
        }
    }

    private fun iconActionButton(text: String, iconRes: Int, onClick: () -> Unit): Button {
        return actionButton(text, onClick).apply {
            setButtonIcon(this, iconRes)
            compoundDrawablePadding = dp(4)
        }
    }

    private fun setButtonIcon(button: Button, iconRes: Int) {
        val icon = tintedDrawable(iconRes, button.currentTextColor, 18) ?: return
        if (button.text.isNullOrBlank()) {
            button.gravity = Gravity.CENTER
            button.setPadding(0, dp(10), 0, 0)
            button.setCompoundDrawables(null, icon, null, null)
        } else {
            button.gravity = Gravity.CENTER
            button.setCompoundDrawables(icon, null, null, null)
        }
    }

    private fun tintedDrawable(iconRes: Int, color: Int, sizeDp: Int): Drawable? {
        return getDrawable(iconRes)?.mutate()?.apply {
            val size = dp(sizeDp)
            setBounds(0, 0, size, size)
            setTint(color)
        }
    }

    private fun tintButtonDrawables(button: Button, color: Int) {
        button.compoundDrawables.forEach { it?.setTint(color) }
    }

    private fun tintTextViewDrawables(textView: TextView, color: Int) {
        textView.compoundDrawables.forEach { it?.setTint(color) }
    }

    private fun roundedStrokeDrawable(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }
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

    private data class ReleaseInfo(val version: String, val url: String)

    private enum class Page {
        SCAN,
        LIBRARY,
        PLAYLISTS,
        NOW_PLAYING,
        SETTINGS,
        STREAMING
    }

    private enum class StreamSource {
        DIZZYLAB, WEBDAV
    }

    private enum class PlaylistCategory(val label: String) {
        COMMON("常用歌单"),
        AUTO("自动歌单")
    }

    private enum class AudioFocusBehavior(val label: String) {
        MIX("同时播放"),
        DUCK("压低音量"),
        PAUSE("暂停播放")
    }

    private enum class DarkMode(val label: String) {
        LIGHT("浅色"),
        DARK("深色"),
        SYSTEM("跟随系统")
    }

    private enum class FloatingLyricsPosition(val label: String) {
        TOP("顶部"),
        LOWER("中下部"),
        BOTTOM("底部")
    }

    private enum class OutputDirectoryMode(val label: String) {
        INTERNAL("使用软件内部目录"),
        DOWNLOAD("使用下载目录"),
        SOURCE("与源文件相同目录")
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
        private const val REQUEST_METADATA_COVER = 1012
        private const val REQUEST_EXTRA_SCAN_DIR = 1013
        private const val REQUEST_SKIP_SCAN_DIR = 1014
        private const val NOTIFICATION_CHANNEL_ID = "playback"
        private const val DOWNLOAD_CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 11
        private const val DOWNLOAD_NOTIFICATION_ID = 21
        private const val LYRICS_NOTIFICATION_ID = 31
        private const val FLOATING_LYRICS_NOTIFICATION_ID = 41
        private const val TAG = "SMP"
        private const val APP_VERSION = "1.5.3"
        private const val REPO_URL = "https://github.com/SuperMite233/Simple-Music-Player"
        private const val ISSUES_URL = "$REPO_URL/issues"
        private const val AUTHOR_URL = "https://space.bilibili.com/287415007"
        private const val RELEASES_URL = "$REPO_URL/releases"
        private const val RELEASES_API = "https://api.github.com/repos/SuperMite233/Simple-Music-Player/releases?per_page=20"
        private const val DIZZYLAB_LOGIN_URL = "https://www.dizzylab.net/albums/login/"
        private const val LASTFM_BASE = "https://ws.audioscrobbler.com/2.0/"
        private const val LASTFM_API_KEY = "67097e703d9a775d6501109447efd85d"
        private const val ACTION_OPEN = "com.supermite.smp.OPEN"
        private const val ACTION_PREV = "com.supermite.smp.PREV"
        private const val ACTION_NEXT = "com.supermite.smp.NEXT"
        private const val ACTION_PLAY_PAUSE = "com.supermite.smp.PLAY_PAUSE"
        private const val ACTION_TOGGLE_FLOATING_LYRICS = "com.supermite.smp.TOGGLE_FLOATING_LYRICS"
        private const val ACTION_REWIND = "com.supermite.smp.REWIND"
        private const val ACTION_FORWARD = "com.supermite.smp.FORWARD"
    }
}
private class StrokeTextView(context: android.content.Context) : TextView(context) {
    var strokeEnabled: Boolean = true

    init {
        setTextColor(Color.WHITE)
        paint.isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        if (!strokeEnabled) {
            super.onDraw(canvas)
            return
        }
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
