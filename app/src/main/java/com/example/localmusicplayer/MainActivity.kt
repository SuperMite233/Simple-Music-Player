package com.supermite.smp

import android.Manifest
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.WindowInsetsCompat
import com.supermite.smp.ui.materialButton
import com.supermite.smp.ui.materialCheckBox
import com.supermite.smp.ui.materialSeekBar
import com.supermite.smp.ui.styleMaterialTree
import com.supermite.smp.ui.setEmphasis
import com.supermite.smp.ui.rippleDrawable
import com.supermite.smp.ui.stateColors
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.app.Dialog
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
import android.webkit.WebResourceRequest
import androidx.appcompat.widget.PopupMenu
import com.supermite.smp.stream.OnlineMusicSource
import com.supermite.smp.stream.DizzylabApiClient
import com.supermite.smp.updates.PublishedRelease
import com.supermite.smp.updates.ReleasePolicy
import com.supermite.smp.cache.CacheManager
import com.supermite.smp.cache.CacheCategory
import com.supermite.smp.logging.AppLogger
import com.supermite.smp.logging.LogLevel
import com.supermite.smp.stream.DizzylabTrackRef
import com.supermite.smp.stream.DizzylabSessionStore
import com.supermite.smp.stream.DizzylabSection
import com.supermite.smp.stream.DizzylabUser
import com.supermite.smp.stream.DizzylabCircle
import com.supermite.smp.stream.DizzylabFeedEntry
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
import com.supermite.smp.conversion.ConvertedMediaPublisher
import com.supermite.smp.data.LibraryStore
import com.supermite.smp.data.MusicScanner
import com.supermite.smp.data.Playlist
import com.supermite.smp.data.Track
import com.supermite.smp.data.TrackEdit
import com.supermite.smp.playback.MusicPlayer
import com.supermite.smp.stream.DizzylabClient
import com.supermite.smp.stream.StreamAlbum
import com.supermite.smp.stream.StreamAlbumDetails
import com.supermite.smp.stream.SubsonicAlbum
import com.supermite.smp.stream.SubsonicArtist
import com.supermite.smp.stream.SubsonicClient
import com.supermite.smp.stream.SubsonicPlaylist
import com.supermite.smp.stream.SubsonicSong
import com.supermite.smp.stream.SubsonicTranscodeMode
import com.supermite.smp.stream.WebDavClient
import com.supermite.smp.stream.WebDavItem
import com.supermite.smp.data.WebDavServer
import com.supermite.smp.data.SubsonicServer
import java.util.UUID
import com.supermite.smp.ui.Palette
import com.supermite.smp.ui.PlaylistAdapter
import com.supermite.smp.ui.TrackAdapter
import com.supermite.smp.ui.Lang
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

class MainActivity : androidx.appcompat.app.AppCompatActivity(), MusicPlayer.Listener {
    private val navigationBackCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { this@MainActivity.onBackPressed() }
    }
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
    private var bottomNavigationPanel: com.google.android.material.navigation.NavigationBarView? = null
    private var updatingNavigation = false
    private var keyboardVisible = false

    private var allTracks: List<Track> = emptyList()
    private var visibleTracks: List<Track> = emptyList()
    private var currentQueue: List<Track> = emptyList()
    private var currentIndex: Int = -1
    private var page: Page = Page.SCAN
    private var pageRenderGeneration = 0
    private var openedPlaylist: Playlist? = null
    private var selectionMode: Boolean = false
    private var selectionFromLibrary: Boolean = false
    private var selectionPlaylist: Playlist? = null
    private val selectedTrackIds = mutableSetOf<String>()
    private var selectionCountText: TextView? = null
    private var pendingListFirstVisible: Int = -1
    private var pendingListTopOffset: Int = 0
    private var pendingPlaylistScrollY: Int = -1
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
    private var themeColor: Int = Palette.DEFAULT_SEED
    private var darkMode: DarkMode = DarkMode.SYSTEM
    private var language: String = "system"
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
    private var debugMode: Boolean = true
    private val cacheManager by lazy { CacheManager(this) }
    private var activePlaybackCacheLease: com.supermite.smp.cache.CacheLease? = null
    private val audioCacheFlights = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val audioCacheLocks = Array(32) { Any() }
    private var selectedLogExportIds: Set<String> = emptySet()
    private var pendingNcmConversionUris: List<Uri> = emptyList()
    private var autoWriteMetadata: Boolean = false
    private var allowMobileDataDownload: Boolean = true
    private val extraScanFolderUris = mutableListOf<String>()
    private val skippedScanFolderUris = mutableListOf<String>()
    private var libraryFiltersExpanded: Boolean = false
    private var profileName: String = "profile"
    private var profileAvatarUri: String = ""
    @Volatile private var dizzylabCookie: String = ""
    private var dizzylabUserId: String = ""
    @Volatile private var dizzylabToken: String = ""
    @Volatile private var dizzySessionRevision = 0
    private val dizzySessionLock = Any()
    private val dizzylabSessionStore by lazy { DizzylabSessionStore(this) }
    private var onlineMusicSource = OnlineMusicSource.SUBSONIC
    private var dizzySection = DizzylabSection.DISCOVER
    private var dizzyOffset = 0
    private var dizzyHasMore = false
    private var dizzyMusicLoaded = false
    private var dizzyMusicLoading = false
    private var dizzyMusicError = ""
    private var dizzyMusicRequest = 0
    private var dizzyCircles: List<DizzylabCircle> = emptyList()
    private var dizzyCircle: DizzylabCircle? = null
    private var dizzyUser: DizzylabUser? = null
    private var dizzyAccountSection = "home"
    private var dizzyAccountLoading = false
    private var dizzyAccountRequest = 0
    private var dizzyAccountError = ""
    private var dizzyAccountOffset = 0
    private var dizzyAccountHasMore = false
    private var dizzyFeed: List<DizzylabFeedEntry> = emptyList()
    private var dizzyFollowing: List<DizzylabCircle> = emptyList()
    private var dizzyAlbumReturnToAccount = false
    private var dizzyMutationInFlight = false
    private var dizzyDownloadBitrate = 320
    private var playbackRequestGeneration = 0
    private var streamPreloadCount: Int = 1
    private var streamDownloadFolderUri: String = ""
    private var streamCacheLimitGb: Float = 2.0f
    private var streamCacheFolderUri: String = ""
    private var weakNetworkMode: Boolean = false
    private var subsonicTranscodeMode: SubsonicTranscodeMode = SubsonicTranscodeMode.LOSSLESS
    private var outputDirectoryMode: OutputDirectoryMode = OutputDirectoryMode.INTERNAL
    private var restorePlaybackOnLaunch: Boolean = false
    private var audioFocusBehavior: AudioFocusBehavior = AudioFocusBehavior.PAUSE
    private var streamSource: StreamSource? = null
    private var dizzylabQuery: String = ""
    private var pendingStreamScrollY: Int = -1
    private var streamScrollView: ScrollView? = null
    private var streamContentBox: LinearLayout? = null
    private var streamSearchEdit: EditText? = null
    private var dizzylabAlbums: List<StreamAlbum> = emptyList()
    private val dizzyListPositions = mutableMapOf<String, Int>()
    private var dizzyListScrollView: ScrollView? = null
    private var dizzyListScrollKey = ""
    private var openedDizzylabAlbum: StreamAlbumDetails? = null
    private var pendingStreamDownloadAfterFolder: (() -> Unit)? = null
    private var webDavCurrentPath: String = ""
    private var webDavCurrentServer: WebDavServer? = null
    private var webDavCurrentItems: List<WebDavItem> = emptyList()
    private var webDavCache: MutableMap<String, List<WebDavItem>> = mutableMapOf()
    private var webDavSearchQuery: String = ""
    private var subsonicSearchQuery: String = ""
    private var subsonicRandomSongs: List<SubsonicSong> = emptyList()
    private var subsonicRandomRequested: Boolean = false
    private var subsonicArtists: List<SubsonicArtist> = emptyList()
    private var subsonicAlbums: List<SubsonicAlbum> = emptyList()
    private var subsonicPlaylists: List<SubsonicPlaylist> = emptyList()
    private var subsonicSongs: List<SubsonicSong> = emptyList()
    private var subsonicSectionTitle: String = ""
    private var subsonicScreen: SubsonicScreen = SubsonicScreen.HOME
    private val subsonicBackStack = mutableListOf<SubsonicScreen>()
    private var subsonicCurrentArtistName: String = ""
    private var subsonicCurrentAlbum: SubsonicAlbum? = null
    private var subsonicCurrentPlaylist: SubsonicPlaylist? = null
    private var subsonicVisibleArtistCount: Int = 0
    private var subsonicVisibleAlbumCount: Int = 0
    private var subsonicVisibleSongCount: Int = 0
    private var subsonicVisiblePlaylistCount: Int = 0
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
    private val downloadQueue = mutableListOf<DownloadTask>()
    private var downloadExecutor: java.util.concurrent.ExecutorService? = null
    private var downloadRefreshHandler: android.os.Handler? = null
    private var downloadRefreshRunnable: Runnable? = null
    private var downloadListDialogBox: LinearLayout? = null
    private var subsonicDialog: AlertDialog? = null
    private var downloadNotifyTotal: Int = 0
    private var downloadNotifyDone: Int = 0
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
        onBackPressedDispatcher.addCallback(this, navigationBackCallback)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!prefs.contains("language")) {
            language = if (Locale.getDefault().language == "zh") "zh" else "en"
            prefs.edit().putString("language", language).apply()
        }
        Lang.language = language
        appInForeground = true
        store = LibraryStore(this)
        scanner = MusicScanner(this)
        player = MusicPlayer(this).apply { listener = this@MainActivity }
        player.headersForTrack = ::streamHeadersForTrack
        trackAdapter = TrackAdapter(this, store.edits)
        trackAdapter.artworkLoader = ::loadArtwork
        trackAdapter.onMoreClick = { track -> showTrackActions(track) }
        trackAdapter.onTrackClick = { track ->
            if (selectionMode) toggleSelection(track) else playTrack(track, queueForCurrentView())
        }
        trackAdapter.onTrackLongClick = { track, position -> enterSelectionMode(track, (position - 3).coerceAtLeast(0), 0) }
        playlistAdapter = PlaylistAdapter(this) { trackId -> store.trackForRecord(trackId)?.let { resolvedArtworkPath(it) } }
        playlistAdapter.artworkLoader = { id, target ->
            store.trackForRecord(id)?.let { loadArtwork(it, target) } ?: target.setImageResource(R.drawable.ic_cover_placeholder)
        }
        audioManager = getSystemService(AudioManager::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        mediaSession = MediaSession(this, "SMP").apply {
            setCallback(mediaSessionCallback)
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
        loadSettings()
        initDebugLogger()
        selectedLogExportIds = savedInstanceState?.getStringArrayList("logExportIds")?.toSet().orEmpty()
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
        if (!handleExternalNcmIntent(intent)) showFirstLaunchDialogIfNeeded()
        updateFloatingLyrics()
        if (autoCheckUpdates) checkForUpdates(silent = true)
        Thread { backgroundSilentRescan() }.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) { setIntent(intent); handlePlaybackIntent(intent); handleExternalNcmIntent(intent) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("logExportIds", ArrayList(selectedLogExportIds))
        super.onSaveInstanceState(outState)
    }

    private fun handleExternalNcmIntent(incoming: Intent): Boolean {
        if (incoming.action != NcmOpenActivity.ACTION_CONVERT_NCM) return false
        val uri = incoming.data ?: return false
        incoming.action = Intent.ACTION_MAIN
        Thread {
            val result = runCatching { NcmInput.validate(this, uri) }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onSuccess { name ->
                    dialogBuilder().setTitle("转换 NCM 文件")
                        .setMessage("$name\n\n转换为 MP3/FLAC，完成后提交系统媒体更新。输出按当前目录设置处理，临时文件受缓存上限约束。")
                        .setPositiveButton("开始转换") { _, _ -> convertNcmFiles(listOf(uri)) }.setNegativeButton("取消", null).show()
                }.onFailure { Toast.makeText(this, it.message ?: "无法读取 NCM 文件", Toast.LENGTH_LONG).show() }
            }
        }.start()
        return true
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
        playbackRequestGeneration++
        dizzyMusicRequest++; dizzyAccountRequest++
        savePlaybackState()
        abandonPlaybackFocus()
        removeFloatingLyrics()
        if (::player.isInitialized) player.pause()
        PlaybackControlBus.handler = null
        mediaSession.isActive = false
        mediaSession.release()
        player.release()
        releasePlaybackCacheLease()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android SDK")
    override fun onBackPressed() {
        when {
            page == Page.STREAMING && streamSource == StreamSource.WEBDAV && webDavCurrentPath.isNotBlank() -> {
                val parent = webDavParentPath(webDavCurrentPath)
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
            page == Page.STREAMING && streamSource != null -> {
                streamSource = null
                render(Page.STREAMING)
            }
            page == Page.DIZZYLAB_ACCOUNT -> {
                if (dizzyAccountSection != "home") { dizzyAccountRequest++; dizzyAccountLoading = false; dizzyAccountSection = "home"; render(Page.DIZZYLAB_ACCOUNT) }
                else render(Page.SETTINGS)
            }
            page == Page.ONLINE && onlineMusicSource == OnlineMusicSource.DIZZYLAB && openedDizzylabAlbum != null -> {
                openedDizzylabAlbum = null
                if (dizzyAlbumReturnToAccount) { dizzyAlbumReturnToAccount = false; render(Page.DIZZYLAB_ACCOUNT) }
                else render(Page.ONLINE)
            }
            page == Page.ONLINE && onlineMusicSource == OnlineMusicSource.DIZZYLAB -> {
                if (dizzySection != DizzylabSection.DISCOVER) selectDizzylabSection(DizzylabSection.DISCOVER)
                else render(Page.PLAYLISTS)
            }
            page == Page.ONLINE && subsonicBackStack.isNotEmpty() -> {
                subsonicScreen = subsonicBackStack.removeAt(subsonicBackStack.lastIndex)
                render(Page.ONLINE)
            }
            page == Page.ONLINE && subsonicScreen != SubsonicScreen.HOME -> {
                subsonicScreen = SubsonicScreen.HOME
                render(Page.ONLINE)
            }
            page == Page.ONLINE -> render(Page.PLAYLISTS)
            page == Page.SCAN -> render(Page.LIBRARY)
            page == Page.LIBRARY -> render(Page.PLAYLISTS)
            page == Page.PLAYLISTS && openedPlaylist != null -> {
                openedPlaylist = null
                render(Page.PLAYLISTS)
            }
            page == Page.SETTINGS || page == Page.NOW_PLAYING -> render(Page.PLAYLISTS)
            else -> {
                navigationBackCallback.isEnabled = false
                try { super.onBackPressed() } finally { navigationBackCallback.isEnabled = true }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_NCM_STORAGE -> {
                val pending = pendingNcmConversionUris
                pendingNcmConversionUris = emptyList()
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) convertNcmFiles(pending)
                else Toast.makeText(this, "未获得写入系统音乐目录的权限，转换已取消", Toast.LENGTH_LONG).show()
            }
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
            REQUEST_LOG_EXPORT -> data.data?.let { uri ->
                val selected = selectedLogExportIds.toSet()
                selectedLogExportIds = emptySet()
                Thread {
                    val result = runCatching { contentResolver.openOutputStream(uri, "w")?.use { AppLogger.export(it, selected) } ?: error("无法写入日志文件") }
                    runOnUiThread {
                        if (!isDestroyed) Toast.makeText(this, if (result.isSuccess) "日志已导出（ZIP）" else result.exceptionOrNull()?.message ?: "日志导出失败", Toast.LENGTH_LONG).show()
                    }
                }.start()
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
        player.currentTrack?.let { submitSubsonicScrobble(it) }
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
        nowPagePlayButton?.text = Lang.get("play")
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

    private fun useNavigationRail() = resources.configuration.screenWidthDp >= 600

    private fun contentGutter(): Int {
        val width = resources.configuration.screenWidthDp - if (useNavigationRail()) 80 else 0
        return dp(if (width >= 888) (width - 840) / 2 else if (width >= 600) 24 else 16)
    }

    private fun buildShell() {
        val frame = FrameLayout(this)
        addBackgroundImage(frame)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(contentOverlayColor())
        }
        rootLayout = root
        root.addView(buildHeader())
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentGutter(), 0, contentGutter(), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(10)
            }
        }
        root.addView(content)
        miniPlayerPanel = buildMiniPlayer()
        root.addView(miniPlayerPanel)
        val navigation = buildBottomNavigation()
        if (useNavigationRail()) {
            val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            columns.addView(navigation, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.MATCH_PARENT))
            columns.addView(root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            frame.addView(columns, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            root.addView(navigation)
            frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        loadingOverlay = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Palette.ACCENT)
        }
        frame.addView(loadingOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            bottomNavigationPanel?.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
            if (::miniPlayerPanel.isInitialized) miniPlayerPanel.visibility = if (keyboardVisible || page == Page.NOW_PLAYING || page == Page.SETTINGS || page == Page.DIZZYLAB_ACCOUNT) View.GONE else View.VISIBLE
            WindowInsetsCompat.CONSUMED
        }
        setContentView(frame)
        ViewCompat.requestApplyInsets(frame)
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
        view.foreground = rippleDrawable(this, Color.TRANSPARENT)
        view.isFocusable = true
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
            setPadding(contentGutter(), dp(12), contentGutter(), dp(12))
            background = ColorDrawable(Palette.BG)
        }
        headerPanel = header
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerTitle = TextView(this).apply {
            text = "SMP"
            titleStyle(22f)
        }
        titleRow.addView(headerTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(titleRow)
        statusText = TextView(this).apply {
            text = "准备扫描或导入本地音乐"
            minimumHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            titleStyle(16f)
            background = rippleDrawable(this@MainActivity, Palette.CONTAINER, 16)
            setPadding(dp(16), dp(12), dp(12), dp(12))
            setCompoundDrawablesRelative(null, null, tintedDrawable(R.drawable.ic_expand_more, Palette.ACCENT, 28), null)
            compoundDrawablePadding = dp(12)
            contentDescription = "选择音乐来源"
            setOnClickListener { if (page == Page.ONLINE) showOnlineSourceMenu(this) }
        }
        header.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        return header
    }

    private fun buildMiniPlayer(): View {
        val panel = object : LinearLayout(this@MainActivity) {
            private var swipeStartX = 0f
            private var swiping = false
            private var swipeAllowed = false

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        swipeStartX = ev.x
                        swiping = false
                        swipeAllowed = childCount > 0 && ev.y <= getChildAt(0).bottom
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (swipeAllowed && !swiping && kotlin.math.abs(ev.x - swipeStartX) > dp(30).toFloat()) {
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
            setPadding(0, dp(8), 0, 0)
            background = ColorDrawable(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                leftMargin = contentGutter()
                rightMargin = contentGutter()
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { render(Page.NOW_PLAYING) }
        }
        miniThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 16, this@MainActivity)
            clipToOutline = true
        }
        row.addView(miniThumb, LinearLayout.LayoutParams(dp(54), dp(54)))
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        miniTitle = TextView(this).apply {
            text = "未播放"
            titleStyle(16f)
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxLines = 1
        }
        miniMeta = TextView(this).apply {
            text = "点击歌曲开始播放"
            bodyStyle(14f)
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxLines = 1
        }
        textBox.addView(miniTitle)
        textBox.addView(miniMeta)
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        miniPlayButton = playerControlButton(R.drawable.ic_play_block, compact = true) {
            toggleOrPlayFirst()
        }
        row.addView(playerControlButton(R.drawable.ic_previous_track, compact = true) { playPreviousOrFirst() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(miniPlayButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(playerControlButton(R.drawable.ic_next_track, compact = true) { playNextOrFirst() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(row)
        miniSeek = buildSeekBar().also { com.supermite.smp.ui.stylePlayerSeekBar(this, it, compact = true) }
        miniTime = com.supermite.smp.ui.PlaybackTimeView(this).apply {
            text = "0:00 / 0:00"
            bodyStyle(12f)
            setPadding(dp(8), 0, dp(8), dp(4))
        }
        panel.addView(miniSeek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        panel.addView(miniTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return panel
    }

    private fun buildBottomNavigation(): View {
        val navigation: com.google.android.material.navigation.NavigationBarView = if (useNavigationRail()) {
            com.google.android.material.navigationrail.NavigationRailView(this).apply { menuGravity = Gravity.CENTER }
        } else BottomNavigationView(this)
        return navigation.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(Palette.CONTAINER)
            elevation = 0f
            labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
            itemIconSize = dp(24)
            itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(Palette.SELECTED)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            itemIconTintList = android.content.res.ColorStateList(states, intArrayOf(Palette.ON_SELECTED, Palette.MUTED))
            itemTextColor = android.content.res.ColorStateList(states, intArrayOf(Palette.TEXT, Palette.MUTED))
            itemRippleColor = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(Palette.TEXT, 31))
            listOf(Triple(Page.ONLINE, Lang.get("nav_online"), R.drawable.ic_online_music),
                Triple(Page.PLAYLISTS, Lang.get("nav_library"), R.drawable.ic_musiclist),
                Triple(Page.SETTINGS, Lang.get("nav_settings"), R.drawable.ic_settings)).forEachIndexed { index, (target, label, icon) ->
                menu.add(0, index + 1, index, label).setIcon(icon).setCheckable(true)
            }
            setOnItemSelectedListener { item ->
                if (!updatingNavigation) render(when (item.itemId) { 1 -> Page.ONLINE; 2 -> Page.PLAYLISTS; else -> Page.SETTINGS })
                true
            }
            minimumHeight = dp(80)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            bottomNavigationPanel = this
        }
    }

    private fun render(target: Page) {
        rememberDizzylabListPosition()
        dizzyListScrollView = null
        AppLogger.debug("NAVIGATION", "Open ${target.name}")
        val renderId = ++pageRenderGeneration
        if (target != Page.LIBRARY && target != Page.PLAYLISTS) clearSelection()
        page = target
        if (content.childCount > 0 && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            val old = content.getChildAt(0)
            old.animate().cancel()
            old.animate().alpha(0f).setDuration(120).withEndAction {
                if (renderId != pageRenderGeneration || isDestroyed) return@withEndAction
                content.removeAllViews()
                nowPagePlayButton = null; nowPageSeek = null; nowPageTime = null
                nowPageLyrics = null; nowPageLyricsScroll = null; nowPageLyricsBox = null
                miniPlayerPanel.visibility = if (keyboardVisible || target == Page.NOW_PLAYING || target == Page.SETTINGS || target == Page.DIZZYLAB_ACCOUNT) View.GONE else View.VISIBLE
                headerTitle.text = if (target == Page.ONLINE) Lang.get("nav_online") else "SMP"
                statusText.visibility = if (target == Page.ONLINE) View.VISIBLE else View.GONE
                updateNavButtons(target)
                buildPageContent(target)
                if (content.childCount > 0) {
                    content.getChildAt(0).alpha = 0f
                    content.getChildAt(0).animate().alpha(1f).setDuration(if (android.animation.ValueAnimator.areAnimatorsEnabled()) 120 else 0).start()
                }
            }.start()
        } else {
            content.removeAllViews()
            nowPagePlayButton = null; nowPageSeek = null; nowPageTime = null
            nowPageLyrics = null; nowPageLyricsScroll = null; nowPageLyricsBox = null
            miniPlayerPanel.visibility = if (keyboardVisible || target == Page.NOW_PLAYING || target == Page.SETTINGS || target == Page.DIZZYLAB_ACCOUNT) View.GONE else View.VISIBLE
            headerTitle.text = if (target == Page.ONLINE) Lang.get("nav_online") else "SMP"
            statusText.visibility = if (target == Page.ONLINE) View.VISIBLE else View.GONE
            updateNavButtons(target)
            buildPageContent(target)
            if (content.childCount > 0) {
                content.getChildAt(0).alpha = 0f
                content.getChildAt(0).animate().alpha(1f).setDuration(if (android.animation.ValueAnimator.areAnimatorsEnabled()) 120 else 0).start()
            }
        }
    }

    private fun updateNavButtons(target: Page) {
        updatingNavigation = true
        bottomNavigationPanel?.selectedItemId = when (target) {
            Page.ONLINE -> 1
            Page.SETTINGS, Page.DIZZYLAB_ACCOUNT -> 3
            else -> 2
        }
        updatingNavigation = false
    }

    private fun buildPageContent(target: Page) {
        when (target) {
            Page.SCAN -> renderScanPage()
            Page.LIBRARY -> renderLibraryPage()
            Page.PLAYLISTS -> renderPlaylistsPage()
            Page.NOW_PLAYING -> renderNowPlayingPage()
            Page.SETTINGS -> renderSettingsPage()
            Page.ONLINE -> renderOnlineMusicPage()
            Page.DIZZYLAB_ACCOUNT -> renderDizzylabAccountPage()
            Page.STREAMING -> renderStreamingPage()
        }
        if (target == Page.SCAN) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            while (content.childCount > 0) { val child = content.getChildAt(0); content.removeViewAt(0); box.addView(child) }
            content.addView(ScrollView(this).apply { addView(box) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        updateMiniPlayerDensity(target)
        styleMaterialTree(content)
    }

    private fun updateMiniPlayerDensity(target: Page = page) {
        if (!::miniSeek.isInitialized) return
        val compact = resources.configuration.screenHeightDp < 500 ||
            (target == Page.LIBRARY && libraryFiltersExpanded && resources.configuration.fontScale > 1.3f)
        miniSeek.visibility = if (compact) View.GONE else View.VISIBLE
        miniTime.visibility = if (compact) View.GONE else View.VISIBLE
    }

    private fun renderScanPage() {
        content.addView(sectionTitle(Lang.get("scan_page")))
        content.addView(scanAction(Lang.get("scan_media"), Lang.get("scan_media_desc")) {
            if (hasAudioPermission()) scanMediaStore() else requestAudioPermission()
        })
        content.addView(scanAction(Lang.get("scan_folder"), Lang.get("scan_folder_desc")) {
            openTreePicker()
        })
        content.addView(scanAction(Lang.get("scan_import"), Lang.get("scan_import_desc")) {
            openManualImport()
        })
        content.addView(scanAction(Lang.get("lrclib_lyrics"), Lang.get("lrclib_desc")) {
            matchLyricsForTracks(allTracks, "全库歌词匹配")
        })
        content.addView(scanAction(Lang.get("lastfm_meta"), Lang.get("lastfm_desc")) {
            fetchLastFmMetadata()
        })
        content.addView(TextView(this).apply {
            text = Lang.get("library_count").format(allTracks.size, allTracks.count { it.hasLyrics })
            bodyStyle(14f)
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun renderLibraryPage() {
        content.addView(sectionTitle(Lang.get("library")))
        val toolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolRow.addView(actionButton(if (libraryFiltersExpanded) Lang.get("collapse_filter") else Lang.get("search_filter")) {
            libraryFiltersExpanded = !libraryFiltersExpanded
            render(Page.LIBRARY)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        toolRow.addView(iconActionButton(Lang.get("scan"), R.drawable.ic_checkmore) { render(Page.SCAN) }, LinearLayout.LayoutParams(dp(86), dp(44)).apply {
            leftMargin = dp(8)
        })
        content.addView(toolRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })
        val filterBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val search = editText("搜索标题、艺术家、专辑、别名、点评", libraryQuery) { value ->
            libraryQuery = value
            updateLibraryList()
        }
        val tagSpinner = androidx.appcompat.widget.AppCompatSpinner(this).apply {
            val tags = listOf("全部标签") + store.edits.values.flatMap { it.tags }.distinct().sorted()
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, tags).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(tags.indexOf(selectedTag).takeIf { it >= 0 } ?: 0)
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedTag = if (position == 0) "" else tags[position]
                    updateLibraryList()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        val ratingSpinner = androidx.appcompat.widget.AppCompatSpinner(this).apply {
            val ratings = listOf("全部评分", "1 星以上", "2 星以上", "3 星以上", "4 星以上", "5 星")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, ratings).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(minRating.coerceIn(0, 5))
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    minRating = position
                    updateLibraryList()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        val sortSpinner = androidx.appcompat.widget.AppCompatSpinner(this).apply {
            val labels = LibrarySortMode.entries.map { it.label }
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, labels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(librarySortMode.ordinal.coerceIn(labels.indices))
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    librarySortMode = LibrarySortMode.entries[position]
                    updateLibraryList()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(56), 1f))
        searchRow.addView(iconActionButton(Lang.get("scan"), R.drawable.ic_checkmore) { render(Page.SCAN) }, LinearLayout.LayoutParams(dp(82), dp(48)).apply {
            leftMargin = dp(8)
        })
        filterBox.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        filterBox.addView(tagSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
        filterBox.addView(ratingSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
        filterBox.addView(sortSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
        if (libraryFiltersExpanded && !selectionMode) {
            val filters = ScrollView(this).apply { addView(filterBox); isFillViewport = false }
            val heightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
            val maximum = if (resources.configuration.fontScale > 1.3f || heightDp < 500) (heightDp * 0.23f).toInt().coerceAtLeast(96) else 240
            content.addView(filters, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(maximum)))
        }

        if (selectionMode && selectionFromLibrary) content.addView(selectionBar())
        val list = trackListView()
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
        updateLibraryList()
    }

    private fun renderPlaylistsPage() {
        if (openedPlaylist != null) {
            content.addView(sectionTitle(openedPlaylist!!.name))
            content.addView(playlistDetails(openedPlaylist!!))
            val tracks = openedPlaylist!!.trackIds.distinct().mapNotNull(store::trackForRecord)
            visibleTracks = tracks
            trackAdapter.tracks = tracks
            if (selectionMode && selectionPlaylist?.id == openedPlaylist!!.id) content.addView(selectionBar())
            content.addView(trackListView(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
            setStatus("${openedPlaylist!!.name}：${tracks.size} 首")
            return
        }

        content.addView(titleActionRow(Lang.get("playlist_list"), "+") { showCreatePlaylistDialog() })
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeRow.addView(actionButton(Lang.get("music_list")) {
            openedPlaylist = null
            clearSelection()
            render(Page.LIBRARY)
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        modeRow.addView(actionButton(Lang.get("streaming_mode")) {
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
        if (resources.configuration.screenHeightDp < 500) {
            content.removeView(modeRow)
            content.removeView(categoryRow)
            val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            quickRow.addView(modeRow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            quickRow.addView(categoryRow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
            content.addView(quickRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
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
                    pendingPlaylistScrollY = this@apply.firstVisiblePosition
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
        if (pendingPlaylistScrollY >= 0 && openedPlaylist == null) {
            val restorePos = pendingPlaylistScrollY
            pendingPlaylistScrollY = -1
            list.post { list.setSelection(restorePos) }
        }
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
        setStatus("${Lang.get("playlist_list")}：${if (playlistCategory == PlaylistCategory.COMMON) Lang.get("common_playlists") else Lang.get("auto_playlists")}")
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
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
        }
        playlist.trackIds.firstOrNull()
            ?.let(store::trackForRecord)
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
            bodyStyle(14f)
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

    private fun onlineSourceLabel(): String = when (onlineMusicSource) {
        OnlineMusicSource.SUBSONIC -> "Subsonic：${selectedSubsonicServer()?.displayName ?: "未配置服务器"}"
        OnlineMusicSource.DIZZYLAB -> "Dizzylab：${dizzyUser?.name ?: if (hasDizzylabSession()) "待验证" else "游客"}"
    }

    private fun showOnlineSourceMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            OnlineMusicSource.entries.forEachIndexed { index, source ->
                menu.add(0, index, index, source.title).apply { isCheckable = true; isChecked = source == onlineMusicSource }
            }
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener { item ->
                onlineMusicSource = OnlineMusicSource.entries[item.itemId]
                saveSettings()
                render(Page.ONLINE)
                true
            }
        }.show()
    }

    private fun renderOnlineMusicPage() {
        when (onlineMusicSource) {
            OnlineMusicSource.SUBSONIC -> renderSubsonicPage()
            OnlineMusicSource.DIZZYLAB -> renderDizzylabMusicPage()
        }
        setStatus(onlineSourceLabel())
    }

    private fun hasDizzylabSession() = dizzylabToken.isNotBlank() || dizzylabCookie.split(';').any {
        val value = it.trim()
        value.startsWith("sessionid=") && value.substringAfter('=').isNotBlank()
    }

    private fun dizzylabApi(requireLogin: Boolean): DizzylabApiClient {
        val snapshot = synchronized(dizzySessionLock) { Triple(dizzylabToken, dizzylabCookie, dizzySessionRevision) }
        var token = snapshot.first
        if (token.isBlank() && snapshot.second.isNotBlank()) {
            val resolved = runCatching { DizzylabApiClient(cookie = snapshot.second).sessionFromWebsite() }
            if (resolved.isSuccess) {
                val (value, user) = resolved.getOrThrow()
                synchronized(dizzySessionLock) {
                    check(snapshot.third == dizzySessionRevision) { "账号已切换，请重试" }
                    token = value
                    dizzylabToken = value
                    dizzylabUserId = user.id
                    dizzyUser = user
                    dizzylabSessionStore.save(snapshot.second, value, user.id)
                }
            } else if (requireLogin) throw resolved.exceptionOrNull()!!
        }
        if (requireLogin) check(token.isNotBlank()) { "请先登录 Dizzylab" }
        check(snapshot.third == dizzySessionRevision) { "账号已切换，请重试" }
        return DizzylabApiClient(token, snapshot.second)
    }

    private fun selectDizzylabSection(section: DizzylabSection) {
        if (section != DizzylabSection.DISCOVER && section != DizzylabSection.CIRCLES && !hasDizzylabSession()) {
            showDizzylabLogin { selectDizzylabSection(section) }
            return
        }
        onlineMusicSource = OnlineMusicSource.DIZZYLAB
        dizzySection = section
        openedDizzylabAlbum = null
        dizzyAlbumReturnToAccount = false
        dizzyMusicLoading = false
        loadDizzylabMusic(reset = true)
    }

    private fun dizzylabListKey() = "$dizzySessionRevision|${dizzySection.name}|${dizzylabQuery.trim()}|${dizzyCircle?.id.orEmpty()}"

    private fun rememberDizzylabListPosition() {
        val scroll = dizzyListScrollView ?: return
        if (scroll.isAttachedToWindow && dizzyListScrollKey.isNotBlank()) {
            dizzyListPositions[dizzyListScrollKey] = scroll.scrollY
        }
    }

    private fun renderDizzylabMusicPage() {
        content.addView(sectionTitle("Dizzylab"))
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val details = openedDizzylabAlbum
        if (details != null) renderDizzylabAlbum(box, details) else {
            val searchRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val search = editText("搜索 Dizzylab 专辑", dizzylabQuery) { dizzylabQuery = it }
            val submit = { selectDizzylabSection(if (dizzylabQuery.isBlank()) DizzylabSection.DISCOVER else DizzylabSection.SEARCH) }
            search.imeOptions = EditorInfo.IME_ACTION_SEARCH
            search.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEARCH) { submit(); true } else false }
            searchRow.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
            searchRow.addView(actionButton("搜索", submit), LinearLayout.LayoutParams(dp(78), dp(46)).apply { leftMargin = dp(8) })
            box.addView(searchRow)
            val row = LinearLayout(this)
            listOf(DizzylabSection.DISCOVER, DizzylabSection.PURCHASED, DizzylabSection.LIKED, DizzylabSection.CIRCLES).forEach { section ->
                val icon = when (section) { DizzylabSection.CIRCLES -> R.drawable.ic_groups; DizzylabSection.DISCOVER -> R.drawable.ic_compass_outline; DizzylabSection.PURCHASED -> R.drawable.ic_check_circle; else -> R.drawable.ic_heart_outline }
                row.addView(subsonicFeatureButton(when (section) {
                    DizzylabSection.DISCOVER -> "发现"; DizzylabSection.PURCHASED -> "已购"; DizzylabSection.LIKED -> "喜欢"; else -> "社团"
                }, icon) { selectDizzylabSection(section) }, LinearLayout.LayoutParams(0, dp(76), 1f).apply { setMargins(dp(3), dp(10), dp(3), dp(8)) })
            }
            box.addView(row)
            box.addView(sectionTitle(if (dizzySection == DizzylabSection.CIRCLE) dizzyCircle?.name ?: "社团作品" else dizzySection.title))
            if (dizzyMusicError.isNotBlank()) box.addView(settingCard("加载失败", dizzyMusicError + " · 点击重试") { loadDizzylabMusic(dizzyOffset == 0) })
            if (dizzySection == DizzylabSection.CIRCLES) dizzyCircles.forEach { circle ->
                box.addView(streamAlbumCard(StreamAlbum(circle.id, circle.name, "", circle.cover), plainDizzylabText(circle.description)) {
                    dizzyCircle = circle
                    selectDizzylabSection(DizzylabSection.CIRCLE)
                })
            } else dizzylabAlbums.forEach { album ->
                box.addView(streamAlbumCard(album, listOf(album.circle, when {
                    album.purchased || dizzySection == DizzylabSection.PURCHASED -> "已购"
                    album.price == 0.0 -> "免费"
                    album.price != null -> "¥ ${album.price}"
                    else -> "Dizzylab 专辑"
                }).filter { it.isNotBlank() }.joinToString(" · ")) { loadDizzylabAlbum(album) })
            }
            if (dizzyMusicLoading) box.addView(TextView(this).apply { text = "正在加载…"; bodyStyle(14f); setPadding(0, dp(16), 0, dp(16)) })
            else if (dizzyHasMore) box.addView(actionButton("加载更多") { loadDizzylabMusic(false) })
            else if (dizzyMusicLoaded && dizzylabAlbums.isEmpty() && dizzyCircles.isEmpty() && dizzyMusicError.isBlank())
                box.addView(settingCard("暂无内容", "切换分类或试试其他关键词。"))
            if (!dizzyMusicLoaded && !dizzyMusicLoading) loadDizzylabMusic(true, renderNow = false)
        }
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (details == null) {
            val key = dizzylabListKey()
            val restoredY = dizzyListPositions[key] ?: 0
            dizzyListScrollView = scroll
            dizzyListScrollKey = key
            scroll.doOnLayout {
                if (dizzyListScrollView === scroll) {
                    scroll.scrollTo(0, restoredY)
                    scroll.setOnScrollChangeListener { _, _, y, _, _ ->
                        if (dizzyListScrollView === scroll && scroll.isAttachedToWindow) dizzyListPositions[key] = y
                    }
                }
            }
        }
    }

    private data class DizzyMusicResult(val albums: List<StreamAlbum>, val circles: List<DizzylabCircle>, val offset: Int, val more: Boolean)

    private fun loadDizzylabMusic(reset: Boolean, renderNow: Boolean = true) {
        if (dizzyMusicLoading) return
        val request = ++dizzyMusicRequest
        val revision = dizzySessionRevision
        val section = dizzySection
        val query = dizzylabQuery.trim()
        val circle = dizzyCircle
        rememberDizzylabListPosition()
        if (reset) {
            dizzyListPositions[dizzylabListKey()] = 0
            dizzyListScrollView = null
            dizzyOffset = 0; dizzylabAlbums = emptyList(); dizzyCircles = emptyList(); dizzyHasMore = false
        }
        val offset = dizzyOffset
        dizzyMusicLoading = true; dizzyMusicLoaded = true; dizzyMusicError = ""
        if (renderNow) render(Page.ONLINE)
        Thread {
            val result = runCatching {
                val api = dizzylabApi(section != DizzylabSection.DISCOVER && section != DizzylabSection.CIRCLES)
                if (section == DizzylabSection.CIRCLES) {
                    val p = api.circles(offset)
                    DizzyMusicResult(emptyList(), p.items, p.nextOffset, p.hasMore)
                } else {
                    val p = if (section == DizzylabSection.CIRCLE) api.circleAlbums(circle ?: error("请选择社团"), offset) else api.albums(section, offset, query)
                    DizzyMusicResult(p.items, emptyList(), p.nextOffset, p.hasMore)
                }
            }
            runOnUiThread {
                if (isDestroyed || request != dizzyMusicRequest || revision != dizzySessionRevision) return@runOnUiThread
                dizzyMusicLoading = false
                result.onSuccess { p ->
                    dizzylabAlbums = (dizzylabAlbums + p.albums).distinctBy { it.id }
                    dizzyCircles = (dizzyCircles + p.circles).distinctBy { it.id }
                    dizzyOffset = p.offset; dizzyHasMore = p.more
                }.onFailure { dizzyMusicError = it.message ?: "网络请求失败" }
                if (page == Page.ONLINE && onlineMusicSource == OnlineMusicSource.DIZZYLAB) render(Page.ONLINE)
            }
        }.start()
    }

    private fun plainDizzylabText(value: String): String = android.text.Html.fromHtml(value, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun renderDizzylabAccountPage() {
        content.addView(actionButton(if (dizzyAccountSection == "home") "返回设置" else "返回 Dizzylab 账户") {
            if (dizzyAccountSection == "home") render(Page.SETTINGS)
            else { dizzyAccountRequest++; dizzyAccountLoading = false; dizzyAccountSection = "home"; render(Page.DIZZYLAB_ACCOUNT) }
        })
        content.addView(sectionTitle("Dizzylab 账户"))
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (!hasDizzylabSession()) {
            box.addView(streamSourceCard("登录 Dizzylab", "管理账户、消息、关注动态与 CDK 兑换", R.drawable.ic_dizzylab) { showDizzylabLogin() })
        } else if (dizzyAccountSection == "home") {
            val user = dizzyUser
            box.addView(streamSourceCard(user?.name ?: "Dizzylab", user?.let { "${it.email}\nUID ${it.id} · ${it.group}" } ?: "正在验证登录状态…", R.drawable.ic_dizzylab) { loadDizzylabAccount() })
            if (dizzyAccountError.isNotBlank()) box.addView(settingCard("账户验证失败", "$dizzyAccountError · 点击重试") { loadDizzylabAccount() })
            box.addView(settingCard("账户资料与设置", "头像、昵称、简介、邮箱及其他账户设置") {
                if (dizzylabUserId.isBlank()) Toast.makeText(this, "请先完成账户验证", Toast.LENGTH_SHORT).show()
                else showDizzylabWebPage("${DizzylabApiClient.BASE}/u/$dizzylabUserId/options/", "账户管理")
            })
            box.addView(settingCard("收件箱", "查看消息与在网页中回复") { showDizzylabWebPage("${DizzylabApiClient.BASE}/albums/msgbox/", "Dizzylab 收件箱") })
            box.addView(settingCard("关注动态", "关注社团的最新发布") { openDizzylabAccountList("feed") })
            box.addView(settingCard("关注社团", "查看关注的社团及其作品") { openDizzylabAccountList("following") })
            box.addView(settingCard("CDK 兑换", "输入兑换码，确认专辑后兑换") { showDizzylabRedemption() })
            box.addView(settingCard("我的订单", "在网页中查看数字商品和 BOOST 记录") { showDizzylabWebPage("${DizzylabApiClient.BASE}/albums/purchases/", "Dizzylab 订单") })
            box.addView(settingCard("下载音质", "MP3 $dizzyDownloadBitrate kbps · 使用播放器现有下载队列") {
                dialogBuilder().setTitle("Dizzylab 下载音质").setSingleChoiceItems(arrayOf("MP3 128 kbps", "MP3 320 kbps"), if (dizzyDownloadBitrate == 128) 0 else 1) { d, which ->
                    dizzyDownloadBitrate = if (which == 0) 128 else 320; saveSettings(); d.dismiss(); render(Page.DIZZYLAB_ACCOUNT)
                }.show()
            })
            box.addView(settingCard("重新登录", "更新本机 Dizzylab 登录状态") { showDizzylabLogin() })
            box.addView(settingCard("退出 Dizzylab", "保留已下载音乐和其他来源的账号") { confirmClearDizzylabCookie() })
            if (user == null && !dizzyAccountLoading && dizzyAccountError.isBlank()) loadDizzylabAccount()
        } else {
            box.addView(sectionTitle(if (dizzyAccountSection == "feed") "关注动态" else "关注社团"))
            box.addView(actionButton("刷新") { loadDizzylabAccountList(true) })
            if (dizzyAccountError.isNotBlank()) box.addView(settingCard("加载失败", dizzyAccountError) { loadDizzylabAccountList(true) })
            if (dizzyAccountSection == "feed") dizzyFeed.forEach { entry ->
                box.addView(sectionTitle(entry.circle.name))
                entry.albums.forEach { album -> box.addView(streamAlbumCard(album, entry.date) { loadDizzylabAlbum(album, fromAccount = true) }) }
            } else dizzyFollowing.forEach { circle ->
                box.addView(streamAlbumCard(StreamAlbum(circle.id, circle.name, "", circle.cover), "查看社团作品") {
                    dizzyCircle = circle; selectDizzylabSection(DizzylabSection.CIRCLE)
                })
            }
            if (dizzyAccountLoading) box.addView(TextView(this).apply { text = "正在加载…"; bodyStyle(14f) })
            else if (dizzyAccountHasMore) box.addView(actionButton("加载更多") { loadDizzylabAccountList(false) })
            else if (dizzyFeed.isEmpty() && dizzyFollowing.isEmpty() && dizzyAccountError.isBlank()) box.addView(settingCard("暂无内容", "关注社团后可在此查看更新。"))
        }
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setStatus("Dizzylab 账户")
        val key = "account|$dizzySessionRevision|$dizzyAccountSection"
        val restoredY = dizzyListPositions[key] ?: 0
        dizzyListScrollView = scroll
        dizzyListScrollKey = key
        scroll.doOnLayout {
            if (dizzyListScrollView === scroll) {
                scroll.scrollTo(0, restoredY)
                scroll.setOnScrollChangeListener { _, _, y, _, _ ->
                    if (dizzyListScrollView === scroll && scroll.isAttachedToWindow) dizzyListPositions[key] = y
                }
            }
        }
    }

    private fun loadDizzylabAccount(onSuccess: (() -> Unit)? = null) {
        if (dizzyAccountLoading) return
        val revision = dizzySessionRevision
        val request = ++dizzyAccountRequest
        dizzyAccountLoading = true; dizzyAccountError = ""
        Thread {
            val result = runCatching { dizzylabApi(true).user() }
            runOnUiThread {
                if (isDestroyed || revision != dizzySessionRevision || request != dizzyAccountRequest) return@runOnUiThread
                dizzyAccountLoading = false
                result.onSuccess { user -> dizzyUser = user; dizzylabUserId = user.id; saveSettings(); onSuccess?.invoke() }
                    .onFailure { dizzyAccountError = it.message ?: "账户验证失败" }
                if (page == Page.DIZZYLAB_ACCOUNT) render(Page.DIZZYLAB_ACCOUNT)
                else if (page == Page.ONLINE) setStatus(onlineSourceLabel())
            }
        }.start()
    }

    private fun openDizzylabAccountList(section: String) {
        dizzyAccountRequest++; dizzyAccountLoading = false
        dizzyAccountSection = section
        dizzyAccountHasMore = false
        loadDizzylabAccountList(true)
    }

    private fun loadDizzylabAccountList(reset: Boolean) {
        if (dizzyAccountLoading) return
        val request = ++dizzyAccountRequest
        val revision = dizzySessionRevision
        val section = dizzyAccountSection
        if (reset) { dizzyAccountOffset = 0; dizzyFeed = emptyList(); dizzyFollowing = emptyList() }
        val offset = dizzyAccountOffset
        dizzyAccountLoading = true; dizzyAccountError = ""
        render(Page.DIZZYLAB_ACCOUNT)
        Thread {
            val result = runCatching {
                val api = dizzylabApi(true)
                if (section == "feed") {
                    val p = api.feed(offset)
                    Triple(p.items, emptyList<DizzylabCircle>(), p.nextOffset to p.hasMore)
                } else {
                    val p = api.circles(offset, following = true)
                    Triple(emptyList<DizzylabFeedEntry>(), p.items, p.nextOffset to p.hasMore)
                }
            }
            runOnUiThread {
                if (isDestroyed || request != dizzyAccountRequest || revision != dizzySessionRevision) return@runOnUiThread
                dizzyAccountLoading = false
                result.onSuccess { (feed, following, next) ->
                    dizzyFeed = (dizzyFeed + feed).distinctBy { it.circle.id + it.date }
                    dizzyFollowing = (dizzyFollowing + following).distinctBy { it.id }
                    dizzyAccountOffset = next.first; dizzyAccountHasMore = next.second
                }.onFailure { dizzyAccountError = it.message ?: "加载失败" }
                if (page == Page.DIZZYLAB_ACCOUNT && dizzyAccountSection == section) render(Page.DIZZYLAB_ACCOUNT)
            }
        }.start()
    }

    private fun <T> performDizzylabAction(action: (DizzylabApiClient) -> T, success: (T) -> Unit) {
        if (!hasDizzylabSession()) { showDizzylabLogin(); return }
        if (dizzyMutationInFlight) return
        dizzyMutationInFlight = true
        val revision = dizzySessionRevision
        showLoading()
        Thread {
            val result = runCatching { action(dizzylabApi(true)) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                hideLoading(); dizzyMutationInFlight = false
                if (revision != dizzySessionRevision) return@runOnUiThread
                result.onSuccess(success).onFailure { Toast.makeText(this, it.message ?: "操作未完成，请刷新后确认状态", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showDizzylabRedemption() {
        val input = editText("输入兑换码", "") { }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0); addView(input) }
        val dialog = dialogBuilder().setTitle("兑换 CDK").setView(box).setPositiveButton("检查兑换码", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = input.text.toString().trim()
                if (code.isBlank()) { input.error = "请输入兑换码"; return@setOnClickListener }
                input.text.clear(); dialog.dismiss()
                performDizzylabAction({ it.redeem(code) }) { preview ->
                    when (preview.result) {
                        "4" -> dialogBuilder().setTitle("确认兑换")
                            .setMessage(preview.album?.title ?: "兑换码有效，确认将对应内容加入你的账号？")
                            .setPositiveButton("确认兑换") { _, _ ->
                                performDizzylabAction({ it.redeem(code, confirm = true) }) { response ->
                                    if (response.result == "1") {
                                        dizzyMusicLoaded = false
                                        Toast.makeText(this, "兑换成功，可在已购音乐中查看", Toast.LENGTH_LONG).show()
                                    } else showDizzylabRedeemResult(response.result, confirmation = true)
                                }
                            }.setNegativeButton("取消", null).show()
                        else -> showDizzylabRedeemResult(preview.result)
                    }
                }
            }
        }
        dialog.setOnDismissListener { input.text.clear() }
        dialog.show()
    }

    private fun showDizzylabRedeemResult(result: String, confirmation: Boolean = false) {
        if (result == "6") {
            dialogBuilder().setTitle("需要网页兑换").setMessage("该兑换码需要在 Dizzylab 网页中完成。").setPositiveButton("打开网页") { _, _ ->
                showDizzylabWebPage("${DizzylabApiClient.BASE}/redeem/", "CDK 兑换")
            }.setNegativeButton("关闭", null).show()
        } else Toast.makeText(this, when {
            !confirmation && result == "1" -> "兑换码无效或已被使用"
            !confirmation && result == "2" -> "你已经拥有该专辑"
            else -> "兑换尚未确认，请刷新已购音乐或在网页检查结果"
        }, Toast.LENGTH_LONG).show()
    }

    private fun renderSubsonicPage() {
        val server = selectedSubsonicServer()
        if (server != null) store.rememberTracks((subsonicSongs + subsonicRandomSongs).distinctBy { it.id }.map { subsonicSongToTrack(it, server) })
        content.addView(sectionTitle(Lang.get("subsonic_status")))
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (server == null) {
            box.addView(settingCard(Lang.get("subsonic_no_server"), Lang.get("subsonic_select_server")) {
                showSubsonicManagementDialog()
            })
            scroll.addView(box)
            content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setStatus(Lang.get("subsonic_not_configured"))
            return
        }
        renderSubsonicTopBar(box)
        when (subsonicScreen) {
            SubsonicScreen.HOME -> renderSubsonicHome(box)
            SubsonicScreen.SEARCH -> renderSubsonicSearchResults(box)
            SubsonicScreen.ARTISTS -> renderSubsonicArtistList(box)
            SubsonicScreen.SONGS -> renderSubsonicSongList(box, subsonicSectionTitle.ifBlank { "歌曲" }, subsonicSongs)
            SubsonicScreen.ALBUMS -> renderSubsonicAlbumList(box, subsonicSectionTitle.ifBlank { "专辑" }, subsonicAlbums)
            SubsonicScreen.PLAYLISTS -> renderSubsonicPlaylistList(box)
            SubsonicScreen.ARTIST_DETAIL -> renderSubsonicAlbumList(box, subsonicSectionTitle.ifBlank { subsonicCurrentArtistName.ifBlank { "艺术家" } }, subsonicAlbums)
            SubsonicScreen.ALBUM_DETAIL -> renderSubsonicAlbumDetail(box)
            SubsonicScreen.PLAYLIST_DETAIL -> renderSubsonicPlaylistDetail(box)
        }
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setStatus(Lang.get("subsonic_status") + "：" + server.displayName)
    }

    private fun selectedSubsonicServer(): SubsonicServer? {
        return store.subsonicServers.firstOrNull { it.id == store.selectedSubsonicServerId }
    }

    private fun subsonicClient(server: SubsonicServer): SubsonicClient {
        return SubsonicClient(server, weakNetworkMode)
    }

    private fun effectiveSubsonicTranscodeMode(): SubsonicTranscodeMode {
        return if (weakNetworkMode) SubsonicTranscodeMode.KBPS_96 else subsonicTranscodeMode
    }

    private fun effectiveSubsonicMimeType(song: SubsonicSong): String {
        return if (effectiveSubsonicTranscodeMode().maxBitRate != null) "audio/mpeg" else song.contentType.ifBlank { "audio/mpeg" }
    }

    private fun effectiveSubsonicSuffix(song: SubsonicSong): String {
        return if (effectiveSubsonicTranscodeMode().maxBitRate != null) "mp3" else song.suffix.ifBlank { song.contentType.substringAfterLast('/', "mp3") }.ifBlank { "mp3" }
    }

    private fun clearSubsonicPageState() {
        subsonicSearchQuery = ""
        subsonicRandomSongs = emptyList()
        subsonicRandomRequested = false
        subsonicArtists = emptyList()
        subsonicAlbums = emptyList()
        subsonicPlaylists = emptyList()
        subsonicSongs = emptyList()
        subsonicSectionTitle = ""
        subsonicScreen = SubsonicScreen.HOME
        subsonicBackStack.clear()
        subsonicCurrentArtistName = ""
        subsonicCurrentAlbum = null
        subsonicCurrentPlaylist = null
        subsonicVisibleArtistCount = 0
        subsonicVisibleAlbumCount = 0
        subsonicVisibleSongCount = 0
        subsonicVisiblePlaylistCount = 0
    }

    private fun subsonicFeatureButton(text: String, iconRes: Int, onClick: () -> Unit): View {
        return TextView(this).apply {
            this.text = text
            titleStyle(13f)
            gravity = Gravity.CENTER
            setCompoundDrawables(null, tintedDrawable(iconRes, Palette.TEXT, 26), null, null)
            compoundDrawablePadding = dp(6)
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
        }
    }

    private fun renderSubsonicTopBar(box: LinearLayout) {
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val search = editText("搜索 Subsonic 音乐", subsonicSearchQuery) { subsonicSearchQuery = it }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        searchRow.addView(iconActionButton("搜索", R.drawable.ic_checkmore) {
            loadSubsonicSearch(subsonicSearchQuery)
        }, LinearLayout.LayoutParams(dp(78), dp(46)).apply { leftMargin = dp(8) })
        box.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        })
        if (subsonicScreen != SubsonicScreen.HOME) {
            box.addView(actionButton("返回上一层") {
                if (subsonicBackStack.isNotEmpty()) {
                    subsonicScreen = subsonicBackStack.removeAt(subsonicBackStack.lastIndex)
                } else {
                    subsonicScreen = SubsonicScreen.HOME
                }
                render(Page.ONLINE)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                bottomMargin = dp(8)
            })
        }
    }

    private fun renderSubsonicHome(box: LinearLayout) {
        val featureRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        featureRow.addView(subsonicFeatureButton("艺术家", R.drawable.ic_artist) {
            pushSubsonicScreen(SubsonicScreen.ARTISTS)
            loadSubsonicArtists()
        }, LinearLayout.LayoutParams(0, dp(76), 1f).apply { rightMargin = dp(5) })
        featureRow.addView(subsonicFeatureButton("歌曲", R.drawable.ic_song) {
            pushSubsonicScreen(SubsonicScreen.SONGS)
            loadSubsonicSongs()
        }, LinearLayout.LayoutParams(0, dp(76), 1f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        featureRow.addView(subsonicFeatureButton("专辑", R.drawable.ic_collection) {
            pushSubsonicScreen(SubsonicScreen.ALBUMS)
            loadSubsonicAlbums()
        }, LinearLayout.LayoutParams(0, dp(76), 1f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        featureRow.addView(subsonicFeatureButton("歌单", R.drawable.ic_collection) {
            pushSubsonicScreen(SubsonicScreen.PLAYLISTS)
            loadSubsonicPlaylists()
        }, LinearLayout.LayoutParams(0, dp(76), 1f).apply { leftMargin = dp(5) })
        box.addView(featureRow)

        val randomHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(6))
        }
        randomHeader.addView(TextView(this).apply { text = "随机音乐"; titleStyle(18f) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        randomHeader.addView(iconActionButton("刷新", R.drawable.ic_refresh) { loadSubsonicRandom(force = true) }, LinearLayout.LayoutParams(dp(86), dp(40)))
        box.addView(randomHeader)
        if (subsonicRandomSongs.isEmpty()) {
            box.addView(settingCard("暂无随机音乐", "点击刷新获取 4 首音乐。") { loadSubsonicRandom(force = true) })
            if (!subsonicRandomRequested) loadSubsonicRandom(force = false)
        } else {
            val randomQueue = subsonicRandomSongs.take(4)
            randomQueue.forEach { song -> box.addView(subsonicSongCard(song, randomQueue)) }
        }
    }

    private fun renderSubsonicSearchResults(box: LinearLayout) {
        box.addView(sectionTitle(subsonicSectionTitle.ifBlank { "搜索结果" }))
        if (subsonicArtists.isEmpty() && subsonicAlbums.isEmpty() && subsonicSongs.isEmpty()) {
            box.addView(settingCard("暂无结果", "没有匹配到艺术家、专辑或歌曲。"))
            return
        }
        if (subsonicArtists.isNotEmpty()) {
            box.addView(TextView(this).apply { text = "艺术家"; titleStyle(17f); setPadding(0, dp(8), 0, dp(6)) })
            subsonicArtists.forEach { artist -> box.addView(subsonicArtistCard(artist)) }
        }
        if (subsonicAlbums.isNotEmpty()) {
            box.addView(TextView(this).apply { text = "专辑"; titleStyle(17f); setPadding(0, dp(8), 0, dp(6)) })
            subsonicAlbums.forEach { album -> box.addView(subsonicAlbumCard(album)) }
        }
        if (subsonicSongs.isNotEmpty()) {
            box.addView(TextView(this).apply { text = "歌曲"; titleStyle(17f); setPadding(0, dp(8), 0, dp(6)) })
            subsonicSongs.forEach { song -> box.addView(subsonicSongCard(song, subsonicSongs)) }
        }
    }

    private fun renderSubsonicArtistList(box: LinearLayout) {
        box.addView(sectionTitle(subsonicSectionTitle.ifBlank { "艺术家" }))
        if (subsonicArtists.isEmpty()) {
            box.addView(settingCard("暂无艺术家", "下拉刷新或重新进入后重试。"))
            return
        }
        val visibleCount = subsonicVisibleCount(subsonicArtists.size, subsonicVisibleArtistCount)
        subsonicArtists.take(visibleCount).forEach { artist -> box.addView(subsonicArtistCard(artist)) }
        if (visibleCount < subsonicArtists.size) {
            box.addView(subsonicLoadMoreButton("艺术家", visibleCount, subsonicArtists.size) {
                subsonicVisibleArtistCount = minOf(subsonicArtists.size, visibleCount + SUBSONIC_PAGE_SIZE)
                render(Page.ONLINE)
            })
        }
    }

    private fun renderSubsonicAlbumList(box: LinearLayout, title: String, albums: List<SubsonicAlbum>) {
        box.addView(sectionTitle(title))
        if (albums.isEmpty()) {
            box.addView(settingCard("暂无专辑", "当前没有可显示的专辑。"))
            return
        }
        val visibleCount = subsonicVisibleCount(albums.size, subsonicVisibleAlbumCount)
        albums.take(visibleCount).forEach { album -> box.addView(subsonicAlbumCard(album)) }
        if (visibleCount < albums.size) {
            box.addView(subsonicLoadMoreButton("专辑", visibleCount, albums.size) {
                subsonicVisibleAlbumCount = minOf(albums.size, visibleCount + SUBSONIC_PAGE_SIZE)
                selectedSubsonicServer()?.let { cacheSubsonicArtwork(it, albums = albums.drop(visibleCount).take(SUBSONIC_PAGE_SIZE)) }
                render(Page.ONLINE)
            })
        }
    }

    private fun renderSubsonicSongList(box: LinearLayout, title: String, songs: List<SubsonicSong>) {
        box.addView(sectionTitle(title))
        if (songs.isEmpty()) {
            box.addView(settingCard("暂无歌曲", "当前没有可显示的歌曲。"))
            return
        }
        val visibleCount = subsonicVisibleCount(songs.size, subsonicVisibleSongCount)
        songs.take(visibleCount).forEach { song -> box.addView(subsonicSongCard(song, songs)) }
        if (visibleCount < songs.size) {
            box.addView(subsonicLoadMoreButton("歌曲", visibleCount, songs.size) {
                subsonicVisibleSongCount = minOf(songs.size, visibleCount + SUBSONIC_PAGE_SIZE)
                selectedSubsonicServer()?.let { cacheSubsonicArtwork(it, songs = songs.drop(visibleCount).take(SUBSONIC_PAGE_SIZE)) }
                render(Page.ONLINE)
            })
        }
    }

    private fun renderSubsonicPlaylistList(box: LinearLayout) {
        box.addView(sectionTitle(subsonicSectionTitle.ifBlank { "歌单" }))
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        addRow.addView(TextView(this).apply { text = "+"; titleStyle(22f); setTextColor(Palette.ACCENT) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        addRow.addView(TextView(this).apply { text = Lang.get("subsonic_create_playlist"); titleStyle(15f) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addRow.setOnClickListener {
            showCreateSubsonicPlaylistDialog()
        }
        box.addView(addRow)
        box.addView(subsonicPlaylistCard(
            playlist = SubsonicPlaylist(
                id = "__favorites__",
                name = "喜爱的音乐"
            ),
            description = "Subsonic 收藏歌曲",
            onClick = { openSubsonicFavoritesPlaylist() },
            onLongClick = { openSubsonicFavoritesPlaylistAndDownload() }
        ))
        if (subsonicPlaylists.isEmpty()) {
            box.addView(settingCard("暂无服务器歌单", "当前账号没有其他歌单。"))
            return
        }
        val visibleCount = subsonicVisibleCount(subsonicPlaylists.size, subsonicVisiblePlaylistCount)
        subsonicPlaylists.take(visibleCount).forEach { playlist ->
            box.addView(subsonicPlaylistCard(
                playlist = playlist,
                onClick = { openSubsonicPlaylist(playlist) },
                onLongClick = { openSubsonicPlaylist(playlist, downloadAfterLoad = true) }
            ))
        }
        if (visibleCount < subsonicPlaylists.size) {
            box.addView(subsonicLoadMoreButton("歌单", visibleCount, subsonicPlaylists.size) {
                subsonicVisiblePlaylistCount = minOf(subsonicPlaylists.size, visibleCount + SUBSONIC_PAGE_SIZE)
                selectedSubsonicServer()?.let { cacheSubsonicArtwork(it, playlists = subsonicPlaylists.drop(visibleCount).take(SUBSONIC_PAGE_SIZE)) }
                render(Page.ONLINE)
            })
        }
    }

    private fun renderSubsonicAlbumDetail(box: LinearLayout) {
        val album = subsonicCurrentAlbum
        box.addView(sectionTitle(subsonicSectionTitle.ifBlank { album?.title ?: "专辑" }))
        if (album != null) {
            box.addView(settingCard(
                album.title,
                listOf(album.artist, "${subsonicSongs.size} 首", album.year.takeIf { it > 0 }?.toString())
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            ))
            box.addView(actionButton("下载整张专辑") {
                downloadSubsonicAlbum(album, subsonicSongs)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                bottomMargin = dp(8)
            })
        }
        renderSubsonicSongList(box, "专辑歌曲", subsonicSongs)
    }

    private fun renderSubsonicPlaylistDetail(box: LinearLayout) {
        val playlist = subsonicCurrentPlaylist
        box.addView(sectionTitle(subsonicSectionTitle.ifBlank { playlist?.name ?: "歌单" }))
        if (playlist != null) {
            box.addView(settingCard(
                playlist.name,
                listOf(playlist.comment.ifBlank { "暂无简介" }, playlist.owner, "${subsonicSongs.size} 首", if (playlist.public) "公开歌单" else "")
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            ))
            box.addView(actionButton("下载整个歌单") {
                downloadSubsonicPlaylist(playlist, subsonicSongs)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                bottomMargin = dp(8)
            })
        }
        renderSubsonicSongList(box, "歌单歌曲", subsonicSongs)
    }

    private fun pushSubsonicScreen(target: SubsonicScreen) {
        if (subsonicScreen != target) {
            subsonicBackStack += subsonicScreen
            subsonicScreen = target
        }
    }

    private fun resetSubsonicVisibleCounts() {
        subsonicVisibleArtistCount = 0
        subsonicVisibleAlbumCount = 0
        subsonicVisibleSongCount = 0
        subsonicVisiblePlaylistCount = 0
    }

    private fun subsonicVisibleCount(total: Int, current: Int): Int {
        if (total <= 0) return 0
        return if (current <= 0) minOf(total, SUBSONIC_PAGE_SIZE) else minOf(total, current)
    }

    private fun subsonicLoadMoreButton(label: String, current: Int, total: Int, onClick: () -> Unit): View {
        return actionButton("加载更多 $label（$current/$total）") { onClick() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun cacheSubsonicArtwork(
        server: SubsonicServer,
        albums: List<SubsonicAlbum> = emptyList(),
        songs: List<SubsonicSong> = emptyList(),
        playlists: List<SubsonicPlaylist> = emptyList()
    ) {
        val client = subsonicClient(server)
        val urls = buildList {
            addAll(albums.map { client.coverArtUrl(it.coverArt) })
            addAll(songs.map { client.coverArtUrl(it.coverArt) })
            addAll(playlists.map { client.coverArtUrl(it.coverArt) })
        }.filter { it.isNotBlank() }.distinct()
        urls.forEach { cacheRemoteArtwork(it, true) }
    }

    private fun loadSubsonicRandom(force: Boolean) {
        val server = selectedSubsonicServer() ?: return
        if (!force && subsonicRandomSongs.isNotEmpty()) return
        subsonicRandomRequested = true
        setStatus("正在获取 Subsonic 随机音乐...")
        Thread {
            val songs = runCatching { subsonicClient(server).randomSongs(4) }.getOrElse {
                runOnUiThread { Toast.makeText(this, "Subsonic 随机音乐获取失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            cacheSubsonicArtwork(server, songs = songs)
            runOnUiThread {
                subsonicRandomSongs = songs
                if (page == Page.ONLINE) render(Page.ONLINE)
            }
        }.start()
    }

    private fun loadSubsonicSearch(query: String) {
        val server = selectedSubsonicServer() ?: return
        val q = query.trim()
        if (q.isBlank()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }
        setStatus("正在搜索 Subsonic...")
        pushSubsonicScreen(SubsonicScreen.SEARCH)
        Thread {
            val result = runCatching { subsonicClient(server).search(q) }
            result.getOrNull()?.let { cacheSubsonicArtwork(server, albums = it.albums, songs = it.songs) }
            runOnUiThread {
                resetSubsonicVisibleCounts()
                result.onSuccess {
                    subsonicSectionTitle = "搜索：$q"
                    subsonicArtists = it.artists
                    subsonicAlbums = it.albums
                    subsonicPlaylists = emptyList()
                    subsonicSongs = it.songs
                    subsonicCurrentAlbum = null
                    subsonicCurrentPlaylist = null
                    subsonicVisibleArtistCount = minOf(it.artists.size, SUBSONIC_PAGE_SIZE)
                    subsonicVisibleAlbumCount = minOf(it.albums.size, SUBSONIC_PAGE_SIZE)
                    subsonicVisibleSongCount = minOf(it.songs.size, SUBSONIC_PAGE_SIZE)
                    render(Page.ONLINE)
                }.onFailure {
                    Toast.makeText(this, "Subsonic 搜索失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun loadSubsonicArtists() {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载 Subsonic 艺术家...")
        Thread {
            val artists = runCatching { subsonicClient(server).artists() }.getOrElse {
                runOnUiThread { Toast.makeText(this, "艺术家加载失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            runOnUiThread {
                resetSubsonicVisibleCounts()
                subsonicScreen = SubsonicScreen.ARTISTS
                subsonicSectionTitle = "艺术家"
                subsonicArtists = artists
                subsonicVisibleArtistCount = minOf(artists.size, SUBSONIC_PAGE_SIZE)
                subsonicAlbums = emptyList()
                subsonicPlaylists = emptyList()
                subsonicSongs = emptyList()
                render(Page.ONLINE)
            }
        }.start()
    }

    private fun loadSubsonicSongs() {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载 Subsonic 歌曲...")
        Thread {
            val songs = runCatching { subsonicClient(server).randomSongs(80) }.getOrElse {
                runOnUiThread { Toast.makeText(this, "歌曲加载失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            cacheSubsonicArtwork(server, songs = songs.take(SUBSONIC_PAGE_SIZE))
            runOnUiThread {
                resetSubsonicVisibleCounts()
                subsonicScreen = SubsonicScreen.SONGS
                subsonicSectionTitle = "歌曲"
                subsonicArtists = emptyList()
                subsonicAlbums = emptyList()
                subsonicPlaylists = emptyList()
                subsonicSongs = songs
                subsonicVisibleSongCount = minOf(songs.size, SUBSONIC_PAGE_SIZE)
                render(Page.ONLINE)
            }
        }.start()
    }

    private fun loadSubsonicAlbums() {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载 Subsonic 专辑...")
        Thread {
            val albums = runCatching { subsonicClient(server).albums() }.getOrElse {
                runOnUiThread { Toast.makeText(this, "专辑加载失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            cacheSubsonicArtwork(server, albums = albums.take(SUBSONIC_PAGE_SIZE))
            runOnUiThread {
                resetSubsonicVisibleCounts()
                subsonicScreen = SubsonicScreen.ALBUMS
                subsonicSectionTitle = "专辑"
                subsonicArtists = emptyList()
                subsonicAlbums = albums
                subsonicVisibleAlbumCount = minOf(albums.size, SUBSONIC_PAGE_SIZE)
                subsonicPlaylists = emptyList()
                subsonicSongs = emptyList()
                subsonicCurrentAlbum = null
                render(Page.ONLINE)
            }
        }.start()
    }

    private fun loadSubsonicPlaylists() {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载 Subsonic 歌单...")
        Thread {
            val playlists = runCatching { subsonicClient(server).playlists() }.getOrElse {
                runOnUiThread { Toast.makeText(this, "歌单加载失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            cacheSubsonicArtwork(server, playlists = playlists.take(SUBSONIC_PAGE_SIZE))
            runOnUiThread {
                resetSubsonicVisibleCounts()
                subsonicScreen = SubsonicScreen.PLAYLISTS
                subsonicSectionTitle = "歌单"
                subsonicPlaylists = playlists
                subsonicVisiblePlaylistCount = minOf(playlists.size, SUBSONIC_PAGE_SIZE)
                render(Page.ONLINE)
            }
        }.start()
    }

    private fun subsonicArtistCard(artist: SubsonicArtist): View {
        return settingCard(artist.name, "${artist.albumCount} 张专辑") { openSubsonicArtist(artist.id, artist.name) }
    }

    private fun subsonicAlbumCard(album: SubsonicAlbum): View {
        val server = selectedSubsonicServer() ?: return settingCard(album.title)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            setOnClickListener { openSubsonicAlbum(album) }
            applyCardPressEffect(this)
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_cover_placeholder)
        }
        subsonicClient(server).coverArtUrl(album.coverArt).takeIf { it.isNotBlank() }?.let { loadRemoteImage(it, cover) }
        row.addView(cover, LinearLayout.LayoutParams(dp(58), dp(58)))
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        textBox.addView(TextView(this).apply { text = album.title; titleStyle(15f); maxLines = 2 })
        textBox.addView(TextView(this).apply {
            text = listOf(album.artist, "${album.songCount} 首", album.year.takeIf { it > 0 }?.toString()).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
            bodyStyle(12f, Palette.MUTED)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun subsonicPlaylistCard(
        playlist: SubsonicPlaylist,
        description: String = "",
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
            applyCardPressEffect(this)
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_collection)
        }
        selectedSubsonicServer()?.let { server ->
            subsonicClient(server).coverArtUrl(playlist.coverArt).takeIf { it.isNotBlank() }?.let { loadRemoteImage(it, cover) }
        }
        row.addView(cover, LinearLayout.LayoutParams(dp(58), dp(58)))
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        val descText = playlist.comment.ifBlank { description }.ifBlank { "暂无简介" }
        textBox.addView(TextView(this).apply {
            text = playlist.name
            titleStyle(15f)
            maxLines = 2
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
        })
        textBox.addView(TextView(this).apply {
            text = listOf(descText, playlist.owner, playlist.songCount.takeIf { it > 0 }?.let { "$it 首" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            bodyStyle(12f, Palette.MUTED)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun subsonicSongCard(song: SubsonicSong, queueSongs: List<SubsonicSong> = listOf(song)): View {
        val queue = queueSongs.ifEmpty { listOf(song) }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            setOnClickListener { playSubsonicSong(song, queue) }
            setOnLongClickListener {
                showSubsonicSongActions(song, queue)
                true
            }
        }
        val cover = ImageView(this).apply {
            setImageResource(R.drawable.ic_cover_placeholder)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        selectedSubsonicServer()?.let { server ->
            subsonicClient(server).coverArtUrl(song.coverArt).takeIf { it.isNotBlank() }?.let { loadRemoteImage(it, cover) }
        }
        row.addView(cover, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(10) })
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply { text = song.title; titleStyle(15f); maxLines = 2 })
        textBox.addView(TextView(this).apply {
            text = listOf(song.artist, song.album, formatDuration(song.durationSeconds * 1000L)).filter { it.isNotBlank() }.joinToString(" · ")
            bodyStyle(12f, Palette.MUTED)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(iconActionButton("", R.drawable.ic_checkmore) { showSubsonicSongActions(song, queue) }, LinearLayout.LayoutParams(dp(46), dp(40)))
        return row
    }

    private fun openSubsonicArtist(artistId: String, name: String = "") {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载艺术家...")
        pushSubsonicScreen(SubsonicScreen.ARTIST_DETAIL)
        Thread {
            val albums = runCatching { subsonicClient(server).artistAlbums(artistId) }.getOrElse {
                runOnUiThread { Toast.makeText(this, "艺术家加载失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            runOnUiThread {
                resetSubsonicVisibleCounts()
                subsonicScreen = SubsonicScreen.ARTIST_DETAIL
                subsonicCurrentArtistName = name
                subsonicSectionTitle = "艺术家：${name.ifBlank { artistId }}"
                subsonicArtists = emptyList()
                subsonicAlbums = albums
                subsonicVisibleAlbumCount = minOf(albums.size, SUBSONIC_PAGE_SIZE)
                subsonicPlaylists = emptyList()
                subsonicSongs = emptyList()
                render(Page.ONLINE)
            }
        }.start()
    }

    private fun openSubsonicAlbum(album: SubsonicAlbum) {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载专辑...")
        pushSubsonicScreen(SubsonicScreen.ALBUM_DETAIL)
        Thread {
            val songs = runCatching { subsonicClient(server).albumSongs(album.id) }.getOrElse {
                runOnUiThread { Toast.makeText(this, "专辑加载失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            cacheSubsonicArtwork(server, albums = listOf(album), songs = songs.take(SUBSONIC_PAGE_SIZE))
            runOnUiThread {
                resetSubsonicVisibleCounts()
                subsonicScreen = SubsonicScreen.ALBUM_DETAIL
                subsonicCurrentAlbum = album
                subsonicSectionTitle = "专辑：${album.title.ifBlank { album.id }}"
                subsonicArtists = emptyList()
                subsonicAlbums = emptyList()
                subsonicPlaylists = emptyList()
                subsonicSongs = songs
                subsonicVisibleSongCount = minOf(songs.size, SUBSONIC_PAGE_SIZE)
                render(Page.ONLINE)
            }
        }.start()
    }

    private fun openSubsonicFavoritesPlaylist() {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载 Subsonic 收藏歌曲...")
        pushSubsonicScreen(SubsonicScreen.PLAYLIST_DETAIL)
        Thread {
            val songs = runCatching { subsonicClient(server).favoriteSongs() }.getOrElse {
                runOnUiThread { Toast.makeText(this, "收藏歌曲加载失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            cacheSubsonicArtwork(server, songs = songs.take(SUBSONIC_PAGE_SIZE))
            runOnUiThread {
                resetSubsonicVisibleCounts()
                subsonicScreen = SubsonicScreen.PLAYLIST_DETAIL
                subsonicCurrentPlaylist = SubsonicPlaylist(id = "__favorites__", name = "喜爱的音乐", songCount = songs.size)
                subsonicSectionTitle = "歌单：喜爱的音乐"
                subsonicSongs = songs
                subsonicVisibleSongCount = minOf(songs.size, SUBSONIC_PAGE_SIZE)
                render(Page.ONLINE)
            }
        }.start()
    }

    private fun openSubsonicFavoritesPlaylistAndDownload() {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载 Subsonic 收藏歌曲...")
        Thread {
            val songs = runCatching { subsonicClient(server).favoriteSongs() }.getOrElse {
                runOnUiThread { Toast.makeText(this, "收藏歌曲加载失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            cacheSubsonicArtwork(server, songs = songs.take(SUBSONIC_PAGE_SIZE))
            runOnUiThread {
                resetSubsonicVisibleCounts()
                subsonicScreen = SubsonicScreen.PLAYLIST_DETAIL
                subsonicCurrentPlaylist = SubsonicPlaylist(id = "__favorites__", name = "喜爱的音乐", songCount = songs.size)
                subsonicSectionTitle = "歌单：喜爱的音乐"
                subsonicSongs = songs
                subsonicVisibleSongCount = minOf(songs.size, SUBSONIC_PAGE_SIZE)
                render(Page.ONLINE)
                downloadSubsonicPlaylist(subsonicCurrentPlaylist!!, songs)
            }
        }.start()
    }

    private fun openSubsonicPlaylist(playlist: SubsonicPlaylist, downloadAfterLoad: Boolean = false) {
        val server = selectedSubsonicServer() ?: return
        setStatus("正在加载歌单...")
        pushSubsonicScreen(SubsonicScreen.PLAYLIST_DETAIL)
        Thread {
            val result = runCatching { subsonicClient(server).playlistSongs(playlist.id) }
            result.getOrNull()?.let { (info, songs) ->
                cacheSubsonicArtwork(server, playlists = listOf(info ?: playlist), songs = songs.take(SUBSONIC_PAGE_SIZE))
            }
            runOnUiThread {
                result.onSuccess { (info, songs) ->
                    resetSubsonicVisibleCounts()
                    subsonicScreen = SubsonicScreen.PLAYLIST_DETAIL
                    subsonicCurrentPlaylist = info ?: playlist.copy(songCount = songs.size)
                    subsonicSectionTitle = "歌单：${playlist.name}"
                    subsonicSongs = songs
                    subsonicVisibleSongCount = minOf(songs.size, SUBSONIC_PAGE_SIZE)
                    render(Page.ONLINE)
                    if (downloadAfterLoad) downloadSubsonicPlaylist(subsonicCurrentPlaylist ?: playlist, songs)
                }.onFailure {
                    Toast.makeText(this, "歌单加载失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showSubsonicSongActions(song: SubsonicSong, queueSongs: List<SubsonicSong> = listOf(song)) {
        val queue = queueSongs.ifEmpty { listOf(song) }
        val items = arrayOf("播放", "下载", "收藏到歌单", "歌曲详细信息")
        dialogBuilder()
            .setTitle(song.title)
            .setItems(items) { _, which ->
                when (items[which]) {
                    "播放" -> playSubsonicSong(song, queue)
                    "下载" -> downloadSubsonicSong(song)
                    "收藏到歌单" -> showSubsonicAddToPlaylistDialog(song)
                    "歌曲详细信息" -> showTrackDetailsDialog(subsonicSongToTrack(song, selectedSubsonicServer() ?: return@setItems))
                }
            }
            .show()
    }

    private fun showSubsonicAddToPlaylistDialog(song: SubsonicSong) {
        val server = selectedSubsonicServer() ?: return
        Thread {
            val playlists = runCatching {
                if (subsonicPlaylists.isNotEmpty()) subsonicPlaylists else subsonicClient(server).playlists()
            }.getOrElse {
                runOnUiThread { Toast.makeText(this, "歌单读取失败：${it.message}", Toast.LENGTH_SHORT).show() }
                emptyList()
            }
            runOnUiThread {
                val names = listOf("喜爱的音乐") + playlists.map { it.name }
                dialogBuilder()
                    .setTitle("收藏到歌单")
                    .setItems(names.toTypedArray()) { _, which ->
                        if (which == 0) {
                            updateSubsonicStar(song, true)
                        } else {
                            val playlist = playlists[which - 1]
                            addSongToSubsonicPlaylist(server, playlist, song)
                        }
                    }
                    .setPositiveButton("新建歌单") { _, _ ->
                        showCreateSubsonicPlaylistDialog(song)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }.start()
    }

    private fun showCreateSubsonicPlaylistDialog(song: SubsonicSong? = null) {
        val input = dialogInput(Lang.get("subsonic_playlist_name"), "")
        dialogBuilder()
            .setTitle(Lang.get("subsonic_create_playlist"))
            .setView(input)
            .setPositiveButton(Lang.get("create")) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, Lang.get("subsonic_name_required"), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val server = selectedSubsonicServer() ?: return@setPositiveButton
                Thread {
                    val ok = runCatching {
                        subsonicClient(server).createPlaylist(name, song?.id?.let { listOf(it) } ?: emptyList())
                    }.getOrDefault(false)
                    runOnUiThread {
                        if (ok) {
                            Toast.makeText(this, Lang.get("subsonic_created"), Toast.LENGTH_SHORT).show()
                            loadSubsonicPlaylists()
                        } else {
                            Toast.makeText(this, Lang.get("subsonic_create_failed"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
            .setNegativeButton(Lang.get("cancel"), null)
            .show()
    }

    private fun addSongToSubsonicPlaylist(server: SubsonicServer, playlist: SubsonicPlaylist, song: SubsonicSong) {
        Thread {
            val result = runCatching { subsonicClient(server).addSongToPlaylist(playlist.id, song.id) }
            runOnUiThread {
                result.onSuccess {
                    Toast.makeText(this, "已加入 ${playlist.name}", Toast.LENGTH_SHORT).show()
                    if (subsonicScreen == SubsonicScreen.PLAYLIST_DETAIL && subsonicCurrentPlaylist?.id == playlist.id) {
                        openSubsonicPlaylist(playlist)
                    }
                }.onFailure {
                    Toast.makeText(this, "加入歌单失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun playSubsonicSong(song: SubsonicSong, queueSongs: List<SubsonicSong>) {
        val server = selectedSubsonicServer() ?: return
        val client = subsonicClient(server)
        val queue = queueSongs.ifEmpty { listOf(song) }.map { subsonicSongToTrack(it, server) }
        val track = queue.firstOrNull { it.id == subsonicSongToTrack(song, server).id } ?: subsonicSongToTrack(song, server)
        currentQueue = queue
        client.scrobble(song.id, submission = false)
        val lease = cacheManager.leaseAudio(subsonicCacheRelative(song,server.id))
        if (lease != null) {
            playCachedTrack(lease,track,queue)
        } else {
            playTrack(track, queue)
            cacheSubsonicSongAsync(song, client)
        }
        preloadSubsonicSongs(queueSongs, song, client)
    }

    private fun subsonicSongToTrack(song: SubsonicSong, server: SubsonicServer): Track {
        val client = subsonicClient(server)
        val coverUrl = client.coverArtUrl(song.coverArt)
        return Track(
            id = "stream:subsonic:${server.id}:${song.id}",
            uri = client.streamUrl(song.id, effectiveSubsonicTranscodeMode()),
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationMs = song.durationSeconds * 1000L,
            trackNumber = song.track,
            year = song.year,
            mimeType = effectiveSubsonicMimeType(song),
            sourcePath = "subsonic|${server.id}|${song.id}|${song.artistId}|${song.albumId}",
            artworkPath = cachedRemoteArtworkPath(coverUrl).ifBlank { coverUrl },
            artworkReference = "subsonic|${server.id}|${song.coverArt}"
        )
    }

    private fun subsonicInfo(track: Track): List<String> {
        if (!track.id.startsWith("stream:subsonic:")) return emptyList()
        return track.sourcePath.split('|')
    }

    private fun subsonicCacheFile(song: SubsonicSong): File {
        return cacheManager.file(CacheCategory.AUDIO,subsonicCacheRelative(song,selectedSubsonicServer()?.id.orEmpty()))
    }

    private fun subsonicCacheRelative(song: SubsonicSong, serverId: String): String {
        val identity = "$serverId|${song.id}|${effectiveSubsonicTranscodeMode().name}"
        return "subsonic/${com.supermite.smp.cache.CacheStorage.sha256(identity.toByteArray())}.${effectiveSubsonicSuffix(song)}"
    }

    private fun audioCacheLock(key: String) = audioCacheLocks[(key.hashCode() and Int.MAX_VALUE)%audioCacheLocks.size]

    private fun releasePlaybackCacheLease(nextUri: String? = null) {
        val lease=activePlaybackCacheLease ?: return
        if(nextUri!=null && Uri.fromFile(lease.file).toString()==nextUri)return
        activePlaybackCacheLease=null
        lease.close()
    }

    private fun playCachedTrack(lease: com.supermite.smp.cache.CacheLease, track: Track, queue: List<Track>, countPlay: Boolean = true) {
        val uri=Uri.fromFile(lease.file).toString()
        val cached=rescanMetadata(uri,track).copy(uri=uri,sourcePath=track.sourcePath)
        playTrack(cached,queue,countPlay=countPlay,resolveRemote=false)
        if(player.currentTrack?.uri==uri) {
            releasePlaybackCacheLease()
            activePlaybackCacheLease=lease
        } else lease.close()
    }

    private fun cacheSubsonicSongAsync(song: SubsonicSong, client: SubsonicClient) {
        if(!cacheManager.isEnabled(CacheCategory.AUDIO))return
        val relative=subsonicCacheRelative(song,client.cacheIdentity)
        val mode=effectiveSubsonicTranscodeMode()
        if(!audioCacheFlights.add(relative))return
        Thread {
            try {synchronized(audioCacheLock(relative)) {
                if(cacheManager.storage.find(CacheCategory.AUDIO,relative)!=null)return@synchronized
                if(!cacheManager.isEnabled(CacheCategory.AUDIO))return@synchronized
                cacheManager.storage.write(CacheCategory.AUDIO,relative,client.downloadBytes(song.id,mode))
            }} catch(failure:Exception) {AppLogger.warn("CACHE","Subsonic audio cache failed",failure)}
            finally {audioCacheFlights.remove(relative)}
        }.start()
    }

    private fun preloadSubsonicSongs(queueSongs: List<SubsonicSong>, current: SubsonicSong, client: SubsonicClient) {
        val count = streamPreloadCount.coerceIn(0, 5)
        if (count <= 0) return
        val start = queueSongs.indexOfFirst { it.id == current.id }
        if (start < 0) return
        queueSongs.drop(start + 1).take(count).forEach { cacheSubsonicSongAsync(it, client) }
    }

    private fun downloadSubsonicSong(song: SubsonicSong) {
        val server = selectedSubsonicServer() ?: return
        if (!ensureStreamDownloadFolderReady { downloadSubsonicSong(song) }) return
        checkMobileDataForDownload {
            downloadNotifyTotal = 1
            downloadNotifyDone = 0
            updateDownloadNotification("下载队列 0/1", 0, 1)
            enqueueDownload("subsonic", song.title, song.size) { task ->
                val downloaded = downloadSubsonicSongSync(song, server, task)
                if (downloaded != null) {
                    runOnUiThread { addDownloadedSubsonicTracksToLibrary(listOf(downloaded)) }
                } else {
                    task.status = "失败"
                }
            }
        }
    }

    private fun downloadSubsonicAlbum(album: SubsonicAlbum, songs: List<SubsonicSong>) {
        val server = selectedSubsonicServer() ?: return
        if (songs.isEmpty()) {
            Toast.makeText(this, "专辑内没有可下载的歌曲。", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ensureStreamDownloadFolderReady { downloadSubsonicAlbum(album, songs) }) return
        checkMobileDataForDownload {
            downloadNotifyTotal = songs.size
            downloadNotifyDone = 0
            updateDownloadNotification("下载队列 0/${songs.size}", 0, songs.size)
            songs.forEach { song ->
                enqueueDownload("subsonic", song.title, song.size) { task ->
                    val downloaded = downloadSubsonicSongSync(song, server, task, album)
                    if (downloaded != null) {
                        runOnUiThread { addDownloadedSubsonicTracksToLibrary(listOf(downloaded)) }
                    } else {
                        task.status = "失败"
                    }
                }
            }
        }
    }

    private fun downloadSubsonicPlaylist(playlist: SubsonicPlaylist, songs: List<SubsonicSong>) {
        val server = selectedSubsonicServer() ?: return
        if (songs.isEmpty()) {
            Toast.makeText(this, "歌单内没有可下载的歌曲。", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ensureStreamDownloadFolderReady { downloadSubsonicPlaylist(playlist, songs) }) return
        checkMobileDataForDownload {
            downloadNotifyTotal = songs.size
            downloadNotifyDone = 0
            updateDownloadNotification("下载队列 0/${songs.size}", 0, songs.size)
            songs.forEach { song ->
                enqueueDownload("subsonic", song.title, song.size) { task ->
                    val downloaded = downloadSubsonicSongSync(song, server, task)
                    if (downloaded != null) {
                        runOnUiThread { addDownloadedSubsonicTracksToLibrary(listOf(downloaded)) }
                    } else {
                        task.status = "失败"
                    }
                }
            }
        }
    }

    private fun downloadSubsonicSongSync(
        song: SubsonicSong,
        server: SubsonicServer,
        task: DownloadTask,
        albumOverride: SubsonicAlbum? = null
    ): Track? {
        return runCatching {
            val client = subsonicClient(server)
            val coverUrl = client.coverArtUrl(song.coverArt.ifBlank { albumOverride?.coverArt.orEmpty() })
            val coverPath = cacheRemoteArtwork(coverUrl, true)
            val bytes = client.downloadBytes(song.id, effectiveSubsonicTranscodeMode()) { progress, total ->
                task.progressPercent = if (total > 0) (progress * 100L / total).toInt() else 50
                task.downloadedBytes = progress
            }
            val ext = effectiveSubsonicSuffix(song)
            val fileName = safeFileName("${song.title}.$ext")
            val taggedBytes = tagSubsonicDownloadBytes(fileName, bytes, song, albumOverride, coverPath)
            val targetAlbum = albumOverride?.title ?: song.album.ifBlank { server.displayName }
            val uri = writeStreamDownloadBytes("subsonic", targetAlbum, fileName, effectiveSubsonicMimeType(song), taggedBytes)
            subsonicSongToTrack(song, server).copy(
                id = "download:subsonic:${uri.hashCode()}",
                uri = uri,
                album = targetAlbum,
                sourcePath = localPathForUriString(uri).ifBlank { fileName },
                artworkPath = coverPath.ifBlank { cachedRemoteArtworkPath(coverUrl).ifBlank { coverUrl } }
            )
        }.getOrNull()
    }

    private fun tagSubsonicDownloadBytes(
        fileName: String,
        bytes: ByteArray,
        song: SubsonicSong,
        albumOverride: SubsonicAlbum?,
        coverPath: String
    ): ByteArray {
        return tagCachedDownloadBytes(fileName,bytes,AudioMetadataUpdate(
                    title = song.title,
                    artist = song.artist,
                    composer = song.artist,
                    year = song.year.takeIf { it > 0 }?.toString().orEmpty(),
                    album = albumOverride?.title ?: song.album,
                    coverBytes = coverBytesAtPath(coverPath)
                ))
    }

    private fun addDownloadedSubsonicTracksToLibrary(downloaded: List<Track>) {
        if (downloaded.isEmpty()) return
        val normalized = downloaded.map { track ->
            if (track.artworkPath.isBlank()) {
                val sibling = siblingArtworkFile(track)?.absolutePath.orEmpty()
                if (sibling.isNotBlank()) track.copy(artworkPath = sibling) else track
            } else track
        }
        val sourcePaths = normalized.map { it.sourcePath }.toSet()
        allTracks = (allTracks.filter { it.sourcePath !in sourcePaths } + normalized).sortedWith(MusicScanner.trackComparator)
        store.saveTracks(allTracks)
        store.createAlbumPlaylistsIfNeeded(allTracks)
        renderIfLibraryVisible()
    }

    private fun updateSubsonicStar(song: SubsonicSong, star: Boolean) {
        val server = selectedSubsonicServer() ?: return
        Thread {
            val result = runCatching { subsonicClient(server).starSong(song.id, star) }
            runOnUiThread {
                result.onSuccess {
                    Toast.makeText(this, if (star) "已加入 Subsonic 喜爱" else "已取消 Subsonic 喜爱", Toast.LENGTH_SHORT).show()
                    if (subsonicCurrentPlaylist?.id == "__favorites__") openSubsonicFavoritesPlaylist()
                }.onFailure {
                    Toast.makeText(this, "Subsonic 喜爱更新失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun renderStreamingPage() {
        content.addView(actionButton(Lang.get("back_to_library")) {
            streamSource = null
            render(Page.PLAYLISTS)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        content.addView(sectionTitle(Lang.get("streaming_mode")))
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (streamSource == StreamSource.WEBDAV && webDavCurrentServer != null) renderWebDavBrowser(box)
        else box.addView(streamSourceCard(Lang.get("webdav_card"),
            if (store.webDavServers.isEmpty()) "未添加服务器，请在设置中添加" else "已添加 ${store.webDavServers.size} 台服务器", R.drawable.ic_dav) {
            if (store.webDavServers.isEmpty()) showWebDavManagementDialog() else showWebDavServerPicker()
        })
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setStatus(Lang.get("streaming_mode"))
    }

    private fun renderDizzylabAlbum(box: LinearLayout, details: StreamAlbumDetails) {
        box.addView(actionButton(if (dizzyAlbumReturnToAccount) "返回关注动态" else "返回专辑列表") {
            openedDizzylabAlbum = null
            if (dizzyAlbumReturnToAccount) { dizzyAlbumReturnToAccount = false; render(Page.DIZZYLAB_ACCOUNT) }
            else render(Page.ONLINE)
        })
        box.addView(streamAlbumDetailsCard(details) { showDizzylabAlbumMenu(details) })
        val controls = LinearLayout(this)
        controls.addView(actionButton("播放全部") { details.tracks.firstOrNull()?.let { playTrack(it, details.tracks) } }, LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(actionButton(if (details.liked) "取消喜欢" else "喜欢专辑") {
            performDizzylabAction({ it.setAlbumLike(details.album.id) }) { liked ->
                openedDizzylabAlbum = details.copy(liked = liked)
                if (dizzySection == DizzylabSection.LIKED) dizzyMusicLoaded = false
                render(Page.ONLINE)
            }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        box.addView(controls)
        when {
            details.purchased -> box.addView(actionButton("下载专辑 · MP3 $dizzyDownloadBitrate kbps") { downloadStreamAlbum(details) })
            details.album.price == 0.0 && details.album.onSale && !details.album.preSelling -> box.addView(actionButton("免费领取专辑") {
                dialogBuilder().setTitle("免费领取").setMessage("将《${details.album.title}》加入你的 Dizzylab 已购音乐？")
                    .setPositiveButton("领取") { _, _ ->
                        performDizzylabAction({ it.claimFree(details.album.id) }) {
                            dizzyMusicLoaded = false
                            loadDizzylabAlbum(details.album, dizzyAlbumReturnToAccount)
                        }
                    }.setNegativeButton("取消", null).show()
            })
            else -> box.addView(actionButton("前往网页查看或购买") { showDizzylabWebPage(details.album.url, details.album.title) })
        }
        if (details.tracks.isEmpty()) { box.addView(settingCard("暂无可播放曲目", "该商品可能是特典、下载商品或尚未发布。")); return }
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
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            tag = "album_card:${album.id}"
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
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
            bodyStyle(14f)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun streamAlbumDetailsCard(details: StreamAlbumDetails, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
                topMargin = dp(8)
            }
        }
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
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
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textBox.addView(TextView(this).apply {
            text = listOf(
                "社团：${details.circle.ifBlank { "未知" }}",
                "发布日期：${details.releaseDate.ifBlank { "未知" }}",
                "标签：${details.tags.joinToString(" / ").ifBlank { "无" }}",
                details.description.ifBlank { "暂无简介" }
            ).joinToString("\n")
            bodyStyle(14f)
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
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
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
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
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
            bodyStyle(14f)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun streamTextCard(title: String, body: String, onClick: () -> Unit): View {
        return settingCard(title, body, onClick)
    }

    private fun renderNowPlayingPage() {
        val track = player.currentTrack
        content.addView(sectionTitle(Lang.get("playing")))
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
            setPadding(dp(4), dp(4), dp(4), dp(16))
        }
        val artFrame = FrameLayout(this).apply {
            background = panelDrawable(Palette.PANEL_ALT, 24, this@MainActivity)
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
        val heightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val widePlayer = resources.configuration.screenWidthDp >= 600 && (heightDp < 500 || resources.configuration.screenWidthDp >= 1000)
        val artHeight = ((heightDp - 260f) * 0.40f).toInt().coerceIn(120, 280)
        artFrame.clipToOutline = true
        box.addView(artFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(artHeight)))
        val titleView = TextView(this).apply {
            text = displayTitle(track)
            titleStyle(22f)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            setHorizontallyScrolling(true)
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = android.animation.ValueAnimator.areAnimatorsEnabled() &&
                !(getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager).isTouchExplorationEnabled
            setPadding(dp(8), dp(18), dp(8), dp(6))
        }
        attachNowPlayingBlankGestures(titleView, track)
        box.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val metaRow = LinearLayout(this).apply {
            orientation = if (widePlayer) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val artistLink = nowPlayingMetaText(track.displayArtist) { openTrackArtist(track) }
        val albumLink = nowPlayingMetaText(track.displayAlbum) { openTrackAlbum(track) }
        if (widePlayer) {
            metaRow.addView(artistLink, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            metaRow.addView(albumLink, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else { metaRow.addView(artistLink); metaRow.addView(albumLink) }
        box.addView(metaRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val blankGestureArea = View(this).apply {
            background = ColorDrawable(Color.TRANSPARENT)
        }
        attachNowPlayingBlankGestures(blankGestureArea, track)
        box.addView(blankGestureArea, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)))

        nowPageSeek = buildSeekBar().also { com.supermite.smp.ui.stylePlayerSeekBar(this, it) }
        nowPageTime = com.supermite.smp.ui.PlaybackTimeView(this).apply {
            bodyStyle(12f)
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        box.addView(nowPageSeek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        box.addView(nowPageTime, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        controls.addView(playerControlButton(playbackModeIcon(playbackMode)) { showPlaybackModeDialog() }.apply {
            contentDescription = "播放模式：${playbackMode.label}"
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(playerControlButton(R.drawable.ic_previous_track) { playPrevious() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        nowPagePlayButton = playerControlButton(if (player.isPlaying()) R.drawable.ic_pause_circle else R.drawable.ic_play_block, primary = true) {
            toggleOrPlayFirst()
        }
        controls.addView(nowPagePlayButton, LinearLayout.LayoutParams(dp(72), dp(72)).apply { setMargins(dp(8), 0, dp(8), 0) })
        controls.addView(playerControlButton(R.drawable.ic_next_track) { playNext() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        controls.addView(playerControlButton(R.drawable.ic_menu) { showNowPlayingMenu(track) }, LinearLayout.LayoutParams(0, dp(56), 1f))
        box.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val bottomBlankGestureArea = View(this).apply {
        background = ColorDrawable(Color.TRANSPARENT)
        }
        attachNowPlayingBlankGestures(bottomBlankGestureArea, track)
        box.addView(bottomBlankGestureArea, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(16)
        ))

        if (widePlayer) {
            val rest = (1 until box.childCount).map { box.getChildAt(it) }
            box.removeAllViews()
            box.orientation = LinearLayout.HORIZONTAL
            box.gravity = Gravity.CENTER_VERTICAL
            box.addView(artFrame, LinearLayout.LayoutParams(0, dp(if (heightDp < 500) 208 else 280), 0.42f))
            val detailsColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, 0, 0) }
            rest.forEach { detailsColumn.addView(it) }
            box.addView(detailsColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f))
        }
        scroll.addView(box)
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        updateLyricView(track)
        updateSeek(nowPageSeek, nowPageTime)
        setStatus("${Lang.get("playing")}：${displayTitle(track)}")
    }

    private fun renderSettingsPage() {
        content.addView(sectionTitle(Lang.get("nav_settings")))
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(profileHeaderCard())
        box.addView(settingsGroup(if (language == "zh") "账户与外观" else "Account & appearance",
            settingsRow(Lang.get("account"), Lang.get("account_desc"), R.drawable.ic_account_person) { showAccountDialog() },
            settingsRow(Lang.get("appearance"), Lang.get("color_dark_bg"), R.drawable.ic_palette) { showAppearanceSettingsDialog() }))
        box.addView(settingsGroup(if (language == "zh") "播放与体验" else "Playback & experience",
            settingsRow(Lang.get("playback_settings"), "${if (restorePlaybackOnLaunch) "启动时继续播放" else "启动时保持暂停"} · ${audioFocusBehavior.label}", R.drawable.ic_headphones) { showPlaybackSettingsDialog() },
            settingsRow(Lang.get("permission_card"), permissionSummary(), R.drawable.ic_shield_check) { showPermissionSettingsDialog() }))
        box.addView(settingsGroup(if (language == "zh") "文件与下载" else "Files & downloads",
            settingsRow(Lang.get("storage"), Lang.get("storage_desc"), R.drawable.ic_storage) { showStorageSettingsDialog() },
            settingsRow("缓存管理", "统一空间上限 · 分类别管理与清理", R.drawable.ic_storage) { showCacheSettingsDialog() },
            settingsRow(Lang.get("download_list"), if (downloadQueue.isEmpty()) Lang.get("no_download") else "${downloadQueue.size} 个任务" + if (downloadNotifyTotal > 0) " ($downloadNotifyDone/$downloadNotifyTotal)" else "", R.drawable.ic_download_queue) { showDownloadListDialog() }))
        box.addView(settingsGroup(if (language == "zh") "关于与支持" else "About & support",
            settingsRow("运行日志与诊断", "${if (AppLogger.isRecording) "记录中" else "已暂停"} · ${AppLogger.level.label} · 保留 ${AppLogger.retentionDays} 天", R.drawable.ic_info_outline) { showDiagnosticsDialog() },
            settingsRow(Lang.get("about"), Lang.get("about_desc"), R.drawable.ic_info_outline) { showSoftwareDetailsDialog() }))
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
        var automatic = autoCheckUpdates
        var preview = includeBetaUpdates
        box.addView(settingsSwitchRow("启动时自动检查更新", "仅提示当前频道中较新的安装包", automatic) { automatic = it })
        box.addView(settingsDropdownRow("更新频道", arrayOf("正式版", "预发行"), if (preview) 1 else 0) { preview = it == 1 })
        box.addView(TextView(this).apply {
            text = "按 GitHub 发布日期选择本频道最新的 APK，忽略草稿、没有安装包的记录与未签名包。两个频道使用同一包名与发布签名。"
            bodyStyle(14f); setPadding(dp(8), dp(12), dp(8), dp(8))
        })
        fun saveChoice() { autoCheckUpdates = automatic; includeBetaUpdates = preview; saveSettings() }
        dialogBuilder()
            .setTitle("更新设置")
            .setView(box)
            .setPositiveButton("保存") { _, _ -> saveChoice() }
            .setNeutralButton("检查更新") { _, _ -> saveChoice(); checkForUpdates(silent = false) }
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
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener { showAccountDialog() }
        }
        row.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            fun defaultAvatar() {
                setImageResource(R.drawable.ic_music_disc)
                imageTintList = android.content.res.ColorStateList.valueOf(Palette.ON_SELECTED)
                background = panelDrawable(Palette.SELECTED, 16, this@MainActivity)
            }
            if (profileAvatarUri.isBlank()) {
                defaultAvatar()
            } else {
                runCatching {
                    contentResolver.openInputStream(Uri.parse(profileAvatarUri))?.use { input ->
                        setImageBitmap(BitmapFactory.decodeStream(input))
                    }
                }.onFailure { defaultAvatar() }
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
            bodyStyle(14f)
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
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        var dialog: AlertDialog? = null
        box.addView(streamSourceCard("Dizzylab", "账户、消息、关注动态与兑换", R.drawable.ic_dizzylab) {
            dialog?.dismiss()
            dizzyAccountSection = "home"
            render(Page.DIZZYLAB_ACCOUNT)
        })
        box.addView(streamSourceCard("Subsonic 服务器", "支持 Navidrome 等兼容 Subsonic API 的服务器", R.drawable.ic_navidrome) { showSubsonicManagementDialog() })
        box.addView(streamSourceCard("WebDav 服务器", if (store.webDavServers.isEmpty()) "未添加服务器" else "已添加 ${store.webDavServers.size} 台服务器", R.drawable.ic_dav) { showWebDavManagementDialog() })
        scroll.addView(box)
        dialog = dialogBuilder().setTitle("流媒体账号凭证").setView(scroll).setPositiveButton("关闭", null).show()
    }

    private fun confirmClearDizzylabCookie() {
        dialogBuilder().setTitle("退出 Dizzylab").setMessage("清除本机的 Dizzylab 登录状态？已下载的音乐会保留。")
            .setPositiveButton("退出") { _, _ ->
                dizzySessionRevision++
                dizzyMusicRequest++; dizzyAccountRequest++; playbackRequestGeneration++
                dizzylabCookie = ""; dizzylabToken = ""; dizzylabUserId = ""; dizzyUser = null
                dizzylabAlbums = emptyList(); openedDizzylabAlbum = null
                dizzyFeed = emptyList(); dizzyFollowing = emptyList(); dizzyCircles = emptyList()
                dizzyMusicLoaded = false; dizzyMusicLoading = false; dizzyAccountLoading = false
                dizzyMusicError = ""; dizzyAccountError = ""; dizzyAccountSection = "home"
                // Clear only this site's cookies, not every WebView provider's account.
                val manager = CookieManager.getInstance()
                listOf("https://www.dizzylab.net/", "https://dizzylab.net/").forEach { url ->
                    manager.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.forEach { name ->
                        manager.setCookie(url, "$name=; Max-Age=0; Path=/")
                        manager.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.dizzylab.net")
                    }
                }
                manager.flush()
                saveSettings()
                render(Page.DIZZYLAB_ACCOUNT)
            }.setNegativeButton("取消", null).show()
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
        val slider = materialSeekBar(this).apply {
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
        val mobileCheck = materialCheckBox(this).apply {
            text = "允许使用流量下载（关闭后下载前会询问）"
            isChecked = allowMobileDataDownload
            setTextColor(Palette.TEXT)
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(mobileCheck)
        val weakNetworkCheck = materialCheckBox(this).apply {
            text = "弱网模式（更宽松超时与重试，并强制使用 96 kbps 串流）"
            isChecked = weakNetworkMode
            setTextColor(Palette.TEXT)
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(weakNetworkCheck)
        val weakNetworkHint = TextView(this).apply {
            text = if (weakNetworkCheck.isChecked) "当前已开启：Subsonic/Navidrome 将优先保证可播放性。" else "关闭时将按下方转码选项正常工作。"
            bodyStyle(12f, Palette.MUTED)
            setPadding(0, dp(4), 0, 0)
        }
        weakNetworkCheck.setOnCheckedChangeListener { _, checked ->
            weakNetworkHint.text = if (checked) "当前已开启：Subsonic/Navidrome 将优先保证可播放性。" else "关闭时将按下方转码选项正常工作。"
        }
        box.addView(weakNetworkHint)
        box.addView(TextView(this).apply {
            text = "Subsonic 串流转码"
            titleStyle(15f)
            setPadding(0, dp(12), 0, dp(6))
        })
        val transcodeSpinner = androidx.appcompat.widget.AppCompatSpinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, SubsonicTranscodeMode.entries.map { it.label }).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(subsonicTranscodeMode.ordinal)
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
        }
        box.addView(transcodeSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        dialogBuilder()
            .setTitle("串流预加载")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                allowMobileDataDownload = mobileCheck.isChecked
                weakNetworkMode = weakNetworkCheck.isChecked
                subsonicTranscodeMode = SubsonicTranscodeMode.entries.getOrElse(transcodeSpinner.selectedItemPosition) { SubsonicTranscodeMode.LOSSLESS }
                saveSettings()
                render(Page.SETTINGS)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCacheSettingsDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        val usage = cacheManager.usage()
        box.addView(settingsGroup("统一缓存空间",
            settingsRow("总空间上限", "${formatFileSize(usage.sumOf { it.bytes })} / ${cacheManager.totalLimitMb} MiB", R.drawable.ic_storage) { showCacheBudgetDialog(null) },
            settingsRow("存储位置", "应用私有目录 / managed-cache\n缓存、下载目录与播放记录分别管理", R.drawable.ic_folder)))
        usage.forEach { item ->
            box.addView(settingsGroup(item.category.label,
                settingsSwitchRow("保留${item.category.label}", "${formatFileSize(item.bytes)} · 上限 ${cacheManager.limitMb(item.category)} MiB", item.enabled) { enabled ->
                    cacheManager.setEnabled(item.category, enabled)
                    if (item.category == CacheCategory.INDEX) webDavCache.clear()
                    if (item.category == CacheCategory.METADATA && !enabled) store.pruneUnreferencedMetadata()
                    AppLogger.refreshCapture()
                },
                settingsRow("分类上限", "${cacheManager.limitMb(item.category)} MiB · 同时受总上限约束", R.drawable.ic_storage) { showCacheBudgetDialog(item.category) },
                settingsRow("清理${item.category.label}", "正在使用的文件会保留至播放或任务结束", R.drawable.ic_refresh) {
                    val removed = cacheManager.clear(item.category)
                    if (item.category == CacheCategory.INDEX) webDavCache.clear()
                    if (item.category == CacheCategory.METADATA) store.pruneUnreferencedMetadata()
                    AppLogger.refreshCapture()
                    Toast.makeText(this, "已清理 ${formatFileSize(removed)}", Toast.LENGTH_SHORT).show()
                }))
        }
        box.addView(TextView(this).apply {
            text = "关闭音频缓存后，可用的临时空间仍可支持本次播放，并在换曲后删除。关闭临时文件会暂停需要暂存的转换。下载音乐、账号和长期播放记录不属于缓存。日志保留天数还受分类与总空间上限限制。"
            bodyStyle(14f); setPadding(dp(16), dp(12), dp(16), dp(8))
        })
        dialogBuilder().setTitle("缓存管理").setView(box).setPositiveButton("关闭", null).show()
    }

    private fun showCacheBudgetDialog(category: CacheCategory?) {
        val value = category?.let(cacheManager::limitMb) ?: cacheManager.totalLimitMb
        val input = dialogInput("空间上限（MiB）", value.toString()).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val dialog = dialogBuilder().setTitle(category?.label ?: "缓存总空间上限").setView(input)
            .setPositiveButton("保存", null).setNegativeButton("取消", null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val limit = input.text.toString().toLongOrNull()
            val minimum = if (category == null) 16L else 1L
            if (limit == null || limit !in minimum..32768L) { input.error = "请输入 $minimum–32768 MiB"; return@setOnClickListener }
            if (category == null) cacheManager.totalLimitMb = limit else cacheManager.setLimitMb(category, limit)
            streamCacheLimitGb = cacheManager.totalLimitMb / 1024f
            AppLogger.refreshCapture(); saveSettings(); dialog.dismiss()
            Toast.makeText(this, "缓存上限已更新，正在使用的文件将在任务结束后回收", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDiagnosticsDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        box.addView(settingsGroup("运行记录",
            settingsSwitchRow("记录运行日志", "默认开启；应用事件、同 UID 运行日志及崩溃，敏感字段自动脱敏", AppLogger.enabled) {
                AppLogger.enabled = it; debugMode = it; saveSettings()
            },
            settingsDropdownRow("最低日志等级", LogLevel.entries.map { "${it.name} · ${it.label}" }.toTypedArray(), AppLogger.level.ordinal) { AppLogger.level = LogLevel.entries[it] },
            settingsRow("保存时长", "${AppLogger.retentionDays} 天 · 默认 7 天", R.drawable.ic_info_outline) { showLogRetentionDialog() }))
        box.addView(settingsGroup("查看与导出",
            settingsRow("日志预览", "查看脱敏后的近期记录，最多 80,000 字符", R.drawable.ic_message) { showLogPreviewDialog(emptySet()) },
            settingsRow("选择日志导出", "按文件多选，导出为 ZIP", R.drawable.ic_download_queue) { showLogSelectionDialog() },
            settingsRow("日志缓存限制", "${if (cacheManager.isEnabled(CacheCategory.LOGS)) "允许记录" else "缓存已关闭，停止记录"} · ${cacheManager.limitMb(CacheCategory.LOGS)} MiB", R.drawable.ic_storage) { showCacheBudgetDialog(CacheCategory.LOGS) }))
        box.addView(TextView(this).apply {
            text = "保存天数是时间上限；缓存不足时会先回收旧文件。应用不读取其他应用的日志。预览与导出也会再次脱敏，请确认后再分享。"
            bodyStyle(14f); setPadding(dp(16), dp(12), dp(16), dp(8))
        })
        dialogBuilder().setTitle("运行日志与诊断").setView(box).setPositiveButton("关闭", null).show()
    }

    private fun showLogRetentionDialog() {
        val input = dialogInput("保留天数（1–365）", AppLogger.retentionDays.toString()).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val dialog = dialogBuilder().setTitle("日志保存时长").setView(input).setPositiveButton("保存", null).setNegativeButton("取消", null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val days = input.text.toString().toIntOrNull()
            if (days == null || days !in 1..365) { input.error = "请输入 1–365 天"; return@setOnClickListener }
            AppLogger.retentionDays = days; dialog.dismiss()
        }
    }

    private fun showLogPreviewDialog(selectedIds: Set<String> = emptySet()) {
        val preview = TextView(this).apply {
            text = AppLogger.preview(selectedIds).ifBlank { "暂无日志。请检查日志开关、最低等级与日志缓存设置。" }
            bodyStyle(12f, Palette.TEXT); typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true); setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        dialogBuilder().setTitle("日志预览 · 已脱敏").setView(preview).setPositiveButton("关闭", null).show()
    }

    private fun showLogSelectionDialog() {
        val files = AppLogger.files()
        if (files.isEmpty()) { Toast.makeText(this, "暂无可导出的日志", Toast.LENGTH_SHORT).show(); return }
        val selected = files.map { it.id }.toMutableSet()
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        files.forEach { file ->
            box.addView(settingsSwitchRow(file.id.substringAfterLast('/'), "${date.format(Date(file.modifiedAt))} · ${formatFileSize(file.bytes)}", true) {
                if (it) selected.add(file.id) else selected.remove(file.id)
            })
        }
        dialogBuilder().setTitle("选择日志文件").setView(box)
            .setNeutralButton("预览") { _, _ -> if (selected.isNotEmpty()) showLogPreviewDialog(selected.toSet()) }
            .setPositiveButton("导出 ZIP") { _, _ ->
                if (selected.isEmpty()) Toast.makeText(this, "请至少选择一个日志文件", Toast.LENGTH_SHORT).show()
                else {
                    selectedLogExportIds = selected.toSet()
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_TITLE, "SMP-logs-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.zip")
                    }, REQUEST_LOG_EXPORT)
                }
            }.setNegativeButton("取消", null).show()
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
        box.addView(settingCard("输出文件目录", outputDirectoryMode.label) {
            showOutputDirectoryDialog()
        })
        box.addView(settingCard("配置文件", "将当前设置、用户歌单和我喜欢的音乐导出为 JSON，或从 JSON 导入。") {
            showConfigDialog()
        })
        box.addView(settingCard("音乐格式转换", "选择 NCM 文件并转换为 MP3/FLAC。转换核心移植自 NCMConverter4a。") {
            openNcmConvertPicker()
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
        showCacheBudgetDialog(null)
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
        box.addView(settingsGroup("主题与显示", settingsRow("软件配色", "预设主题色或调色盘自定义颜色", R.drawable.ic_palette) {
            showThemeSettingsDialog()
        }, settingsRow("深色模式", darkMode.label, R.drawable.ic_moon) {
            showDarkModeDialog()
        }, settingsRow("背景图片", "透明度 ${(backgroundAlpha * 100).toInt()}% · ${if (backgroundImageUri.isBlank()) "未设置" else "已设置"}", R.drawable.ic_image_outline) {
            showBackgroundSettingsDialog()
        }))
        box.addView(settingsGroup("语言", settingsRow("语言", if (language == "zh") "中文" else "English", R.drawable.ic_language) {
            language = if (language == "zh") "en" else "zh"
            Lang.language = language
            saveSettings()
            rebuildShell(page)
            Toast.makeText(this, if (language == "zh") "已切换为中文" else "Switched to English", Toast.LENGTH_SHORT).show()
        }))
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
        val originalSeed = themeColor
        var previewSeed = themeColor
        preview.setTextColor(contrastTextColor(previewSeed))
        var red = Color.red(themeColor)
        var green = Color.green(themeColor)
        var blue = Color.blue(themeColor)
        fun applyPreview() {
            previewSeed = Color.rgb(red, green, blue)
            preview.background = panelDrawable(previewSeed, 12, this@MainActivity)
            preview.setTextColor(contrastTextColor(previewSeed))
        }
        val presets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val colors = listOf(
            "青绿" to 0xFF35C2A1.toInt(), "琥珀" to 0xFFF2B84B.toInt(),
            "蓝色" to 0xFF4A90E2.toInt(), "紫色" to 0xFF9B6CFF.toInt()
        )
        colors.chunked(if (resources.configuration.fontScale > 1.3f) 2 else 4).forEach { group ->
            val row = LinearLayout(this)
            group.forEach { (label, color) ->
                row.addView(actionButton(label) {
                    red = Color.red(color); green = Color.green(color); blue = Color.blue(color)
                    applyPreview()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            presets.addView(row)
        }
        fun colorSlider(label: String, initial: Int, onChanged: (Int) -> Unit): View {
            val boxRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            val text = TextView(this).apply {
                this.text = "$label: $initial"
                bodyStyle(14f)
            }
            val slider = materialSeekBar(this).apply {
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
        box.addView(presets, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        box.addView(colorSlider("R", red) { red = it })
        box.addView(colorSlider("G", green) { green = it })
        box.addView(colorSlider("B", blue) { blue = it })
        dialogBuilder()
            .setTitle("软件配色")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                themeColor = previewSeed
                applyAppPalette()
                saveSettings()
                render(Page.SETTINGS)
                updateFloatingLyrics()
            }
            .setNegativeButton("取消") { _, _ -> themeColor = originalSeed }
            .show()
    }

    private fun showNotificationSettingsDialog() {
        fun updateNotifications() {
            saveSettings()
            if ((notificationEnabled || lockscreenNotificationEnabled) && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
            updatePlaybackNotification()
        }
        val box = settingsGroup("播放控制",
            settingsSwitchRow("状态栏播放开关", "在通知栏控制当前音乐", notificationEnabled) { notificationEnabled = it; updateNotifications() },
            settingsSwitchRow("锁屏显示播放控件", "在锁屏查看封面与播放进度", lockscreenNotificationEnabled) { lockscreenNotificationEnabled = it; updateNotifications() })
        dialogBuilder()
            .setTitle("通知")
            .setView(box)
            .setPositiveButton("完成") { _, _ -> render(Page.SETTINGS) }
            .show()
    }

    private fun showFloatingLyricsSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(2))
        }
        val enabledBox = materialCheckBox(this).apply {
            text = "开启悬浮歌词"
            setTextColor(Palette.TEXT)
            isChecked = floatingLyricsEnabled
        }
        val hideBox = materialCheckBox(this).apply {
            text = "在软件中隐藏悬浮歌词"
            setTextColor(Palette.TEXT)
            isChecked = floatingLyricsHideInApp
        }
        val strokeBox = materialCheckBox(this).apply {
            text = "开启文字描边"
            setTextColor(Palette.TEXT)
            isChecked = floatingLyricsStrokeEnabled
        }
        val alphaLabel = TextView(this).apply {
            text = "文本透明度：${(floatingLyricsAlpha * 100).toInt()}%"
            bodyStyle(14f)
        }
        val alphaSlider = materialSeekBar(this).apply {
            max = 100
            progress = (floatingLyricsAlpha * 100).toInt().coerceIn(20, 100)
        }
        val sizeLabel = TextView(this).apply {
            text = "字号大小：${floatingLyricsTextSizeSp.toInt()}sp"
            bodyStyle(14f)
        }
        val sizeSlider = materialSeekBar(this).apply {
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
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
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
        val positionSpinner = androidx.appcompat.widget.AppCompatSpinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, positionLabels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(floatingLyricsPosition.ordinal)
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
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
            bodyStyle(14f)
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
        box.addView(settingsGroup("启动与音频",
            settingsDropdownRow(Lang.get("restore_playback"), restoreLabels, selectedRestore) { selectedRestore = it },
            settingsDropdownRow(Lang.get("audio_focus"), focusLabels, selectedFocus) { selectedFocus = it }))
        box.addView(settingsGroup("播放体验",
            settingsRow(Lang.get("stream_preload"), "$streamPreloadCount 首", R.drawable.ic_download_queue) { showStreamingSettingsDialog() },
            settingsRow("悬浮歌词", "${if (floatingLyricsEnabled) "开启" else "关闭"} · ${floatingLyricsPosition.label} · ${(floatingLyricsAlpha * 100).toInt()}%", R.drawable.ic_song) { showFloatingLyricsSettingsDialog() },
            settingsRow(Lang.get("eq_preset"), equalizerPreset, R.drawable.ic_volume) { showEqualizerDialog() },
            settingsRow("通知与锁屏", "播放控制与通知显示", R.drawable.ic_notifications) { showNotificationSettingsDialog() }))
        dialogBuilder()
            .setTitle(Lang.get("playback_settings"))
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
        val presetSpinner = androidx.appcompat.widget.AppCompatSpinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, names).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selectedPreset)
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
        }
        box.addView(presetSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        val labels = mutableListOf<TextView>()
        frequencies.forEachIndexed { index, frequency ->
            val label = TextView(this).apply {
                text = "$frequency：${working[index]} mB"
                bodyStyle(14f)
            }
            labels += label
            box.addView(label)
            box.addView(materialSeekBar(this).apply {
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
        val noMedia = materialCheckBox(this).apply {
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
        box.addView(settingsGroup("背景显示",
            settingsRow("背景图片", if (backgroundImageUri.isBlank()) "当前未设置背景图片" else "已设置背景图片", R.drawable.ic_image_outline),
            settingsSliderRow("背景透明度", (backgroundAlpha * 100).toInt().coerceIn(0, 100)) { backgroundAlpha = it / 100f }))
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
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
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
        return android.widget.HorizontalScrollView(this).apply {
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            addView(bar, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
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

    private fun streamHeadersForTrack(track: Track): Map<String, String> {
        val target = Uri.parse(track.uri)
        if (target.scheme !in setOf("http", "https")) return emptyMap()
        if (track.id.startsWith("stream:dizzylab:")) {
            return if (target.scheme == "https" && target.host in setOf("streaming.dizzylab.net", "cdn.dizzylab.net", "albumsbase.dizzylab.net"))
                DizzylabClient("").streamHeaders() else emptyMap()
        }
        if (track.id.startsWith("webdav:")) {
            val identifier = track.id.removePrefix("webdav:")
            val marker = identifier.takeIf { ':' in it }?.substringBefore(':')
            val candidates = store.webDavServers.filter { server ->
                if (marker != null && server.id != marker) return@filter false
                val base = Uri.parse(WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert).resourceUrl(""))
                target.scheme == base.scheme && target.host == base.host && target.port == base.port &&
                    (target.path.orEmpty() == base.path.orEmpty().trimEnd('/') || target.path.orEmpty().startsWith(base.path.orEmpty().trimEnd('/') + "/"))
            }
            val server = if (marker != null) candidates.singleOrNull() else {
                val longest = candidates.maxOfOrNull { Uri.parse(it.url).path.orEmpty().length }
                candidates.filter { Uri.parse(it.url).path.orEmpty().length == longest }.singleOrNull()
            }
            return server?.let { WebDavClient(it.url, it.username, it.password, it.port, it.ignoreCert).streamHeaders() }.orEmpty()
        }
        return emptyMap() // Subsonic has per-request URL authentication; local files need no headers.
    }

    private fun playTrack(track: Track, queue: List<Track>, countPlay: Boolean = true, resolveRemote: Boolean = true) {
        if (resolveRemote) {
            val requestId = ++playbackRequestGeneration
            if (track.id.startsWith("webdav:") && Uri.parse(track.uri).scheme in setOf("http", "https")) {
                if (prepareWebDavRecordedTrack(track, queue, countPlay)) return
            }
            if (track.id.startsWith("stream:subsonic:") && Uri.parse(track.uri).scheme !in setOf("file", "content")) {
                val info = subsonicInfo(track)
                val serverId = info.getOrNull(1) ?: track.id.removePrefix("stream:subsonic:").substringBefore(':')
                val songId = info.getOrNull(2) ?: track.id.removePrefix("stream:subsonic:$serverId:")
                val server = store.subsonicServers.firstOrNull { it.id == serverId }
                if (server == null) { Toast.makeText(this, "该歌曲的 Subsonic 服务器已移除，请重新配置", Toast.LENGTH_LONG).show(); return }
                val client = subsonicClient(server)
                val coverId = track.artworkReference.takeIf { it.startsWith("subsonic|$serverId|") }?.substringAfter("subsonic|$serverId|").orEmpty()
                val coverUrl = if (coverId.isNotBlank()) client.coverArtUrl(coverId) else ""
                val fresh = track.copy(uri = client.streamUrl(songId, effectiveSubsonicTranscodeMode()),
                    artworkPath = if (coverUrl.isNotBlank()) cachedRemoteArtworkPath(coverUrl).ifBlank { coverUrl } else track.artworkPath)
                playTrack(fresh, queue.map { if (it.id == track.id) fresh else it }, countPlay, resolveRemote = false)
                return
            }
            val ref = DizzylabTrackRef.from(track)
            if (ref != null && track.uri.startsWith("https://")) {
                val revision = dizzySessionRevision
                setStatus("正在准备 ${track.displayTitle}…")
                Thread {
                    val result = runCatching { dizzylabApi(false).details(ref.discId).tracks.first { it.id == track.id } }
                    runOnUiThread {
                        if (isDestroyed || requestId != playbackRequestGeneration || revision != dizzySessionRevision) return@runOnUiThread
                        result.onSuccess { fresh ->
                            val updated = track.copy(uri = fresh.uri, artworkPath = fresh.artworkPath)
                            playTrack(updated, queue.map { if (it.id == track.id) updated else it }, countPlay, resolveRemote = false)
                        }.onFailure { Toast.makeText(this, it.message ?: "Dizzylab 播放准备失败", Toast.LENGTH_LONG).show() }
                    }
                }.start()
                return
            }
        }
        if (!restorePreparingPaused && !requestPlaybackFocus()) return
        val baseQueue = queue.ifEmpty { listOf(track) }
        store.rememberTracks(baseQueue + track)
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
        player.headersForTrack = ::streamHeadersForTrack
        releasePlaybackCacheLease(track.uri)
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
        if (track.id.startsWith("stream:subsonic:")) {
            val songId = subsonicInfo(track).getOrNull(2).orEmpty()
            dialogBuilder()
                .setTitle(displayTitle(track))
                .setItems(arrayOf("播放", "下载", "编辑音乐信息", "歌曲详细信息")) { _, which ->
                    when (which) {
                        0 -> playTrack(track, queueForCurrentView())
                        1 -> {
                            val song = subsonicSongs.firstOrNull { it.id == songId } ?: subsonicRandomSongs.firstOrNull { it.id == songId }
                            if (song != null) downloadSubsonicSong(song) else Toast.makeText(this, "当前 Subsonic 歌曲不在列表中，无法下载", Toast.LENGTH_SHORT).show()
                        }
                        2 -> showEditDialog(track)
                        3 -> showTrackDetailsDialog(track)
                    }
                }
                .show()
            return
        }
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

    private fun writeDownloadOutputBytes(folder: String, fileName: String, mimeType: String, bytes: ByteArray): String =
        writeDownloadOutputData(folder, fileName, mimeType) { it.write(bytes) }

    private fun writeDownloadOutputData(folder: String, fileName: String, mimeType: String, write: (java.io.OutputStream) -> Unit): String {
        if (streamDownloadFolderUri.isNotBlank() && hasPersistedWritePermission(streamDownloadFolderUri)) {
            var created: Uri? = null
            runCatching {
                val root = Uri.parse(streamDownloadFolderUri)
                val folderDir = findOrCreateDocumentDir(root, folder)
                val fileUri = DocumentsContract.createDocument(contentResolver, folderDir, mimeType, fileName)
                    ?: error("无法创建输出文件")
                created = fileUri
                contentResolver.openOutputStream(fileUri)?.use(write) ?: error("无法写入输出文件")
                return fileUri.toString()
            }.onFailure { error ->
                created?.let { runCatching { DocumentsContract.deleteDocument(contentResolver, it) } }
                AppLogger.warn("OUTPUT", "Configured output folder unavailable; using default destination", error)
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            var created: Uri? = null
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/SMP/$folder")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建下载目录输出文件")
                created = uri
                contentResolver.openOutputStream(uri)?.use(write) ?: error("无法写入下载目录输出文件")
                check(contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) > 0)
                return uri.toString()
            }.onFailure { error ->
                created?.let { runCatching { contentResolver.delete(it, null, null) } }
                AppLogger.warn("OUTPUT", "MediaStore output failed; using app output directory", error)
            }
        }
        val baseDir = if (hasAllFilesAccess()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SMP")
        } else {
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: getExternalFilesDir(null) ?: filesDir
        }
        val dir = File(baseDir, folder).apply { mkdirs() }
        val file = File(dir, fileName)
        val temporary = File(dir, ".${UUID.randomUUID()}.part")
        try {
            temporary.outputStream().use(write)
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return Uri.fromFile(file).toString()
        } finally { temporary.delete() }
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
            titleStyle(22f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        val slider = materialSeekBar(this).apply {
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
        val slider = materialSeekBar(this).apply {
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
            openTrackArtist(track)
        })
        box.addView(detailRow("专辑", track.displayAlbum) {
            openTrackAlbum(track)
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
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun settingCard(title: String, body: String = "", onClick: (() -> Unit)? = null): View {
        return settingsRow(title, body, settingsIcon(title), onClick).apply {
            background = panelDrawable(Palette.PANEL, 20, this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun settingsIcon(title: String): Int = when {
        title.contains("收件") || title.contains("消息") -> R.drawable.ic_message
        title.contains("动态") -> R.drawable.ic_feed
        title.contains("社团") || title.contains("关注") -> R.drawable.ic_groups
        title.contains("兑换") || title.contains("CDK") -> R.drawable.ic_redeem
        title.contains("订单") -> R.drawable.ic_receipt
        title.contains("退出") -> R.drawable.ic_sign_out
        title.contains("登录") -> R.drawable.ic_sign_in
        title.contains("账户") || title.contains("作者") -> R.drawable.ic_account_person
        title.contains("音质") || title.contains("播放") -> R.drawable.ic_headphones
        title.contains("下载") -> R.drawable.ic_download_queue
        title.contains("缓存") || title.contains("存储") -> R.drawable.ic_storage
        title.contains("配色") || title.contains("外观") -> R.drawable.ic_palette
        title.contains("深色") -> R.drawable.ic_moon
        title.contains("背景") -> R.drawable.ic_image_outline
        title.contains("语言") -> R.drawable.ic_language
        title.contains("权限") -> R.drawable.ic_shield_check
        title.contains("通知") -> R.drawable.ic_notifications
        title.contains("版本") || title.contains("更新") -> R.drawable.ic_refresh
        else -> R.drawable.ic_info_outline
    }

    private fun settingsGroup(title: String, vararg rows: View): View {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        group.addView(TextView(this).apply {
            text = title; bodyStyle(14f, Palette.ACCENT)
            setPadding(dp(16), dp(8), dp(16), dp(10))
        })
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelDrawable(Palette.PANEL, 24, this@MainActivity)
            clipToOutline = true
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) container.addView(View(this).apply { background = ColorDrawable(Palette.OUTLINE_VARIANT) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { leftMargin = dp(64); rightMargin = dp(16) })
            container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        group.addView(container)
        return group
    }

    private fun settingsRow(title: String, body: String = "", iconRes: Int = R.drawable.ic_settings, onClick: (() -> Unit)? = null): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(tintedDrawable(iconRes, Palette.ACCENT, 24))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(16) })
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(TextView(this).apply { text = title; titleStyle(16f) })
        if (body.isNotBlank()) textColumn.addView(TextView(this).apply {
            text = body; bodyStyle(14f); setPadding(0, dp(4), 0, 0)
        })
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (onClick != null) {
            row.addView(ImageView(this).apply {
                setImageDrawable(tintedDrawable(R.drawable.ic_chevron_right, Palette.MUTED, 20))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(20), dp(24)).apply { leftMargin = dp(12) })
            row.setOnClickListener { onClick() }
            applyCardPressEffect(row)
            row.contentDescription = listOf(title, body).filter { it.isNotBlank() }.joinToString("，")
            textColumn.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        return row
    }

    private fun settingsSwitchRow(title: String, body: String, checked: Boolean, onChanged: (Boolean) -> Unit): View {
        val row = settingsRow(title, body, settingsIcon(title)) as LinearLayout
        val toggle = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
            isChecked = checked
            contentDescription = listOf(title, body).filter { it.isNotBlank() }.joinToString("，")
            minimumWidth = dp(52); minimumHeight = dp(48)
            thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Palette.ON_ACCENT, Palette.OUTLINE))
            trackTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Palette.ACCENT, Palette.PANEL_ALT))
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        row.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(12) })
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        applyCardPressEffect(row)
        row.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.getChildAt(1).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        return row
    }

    private fun settingsDropdownRow(title: String, labels: Array<String>, selected: Int, onSelected: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        box.addView(TextView(this).apply { text = title; titleStyle(16f); setPadding(0, 0, 0, dp(8)) })
        box.addView(androidx.appcompat.widget.AppCompatSpinner(this).apply {
            contentDescription = title
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, labels).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0)))
            minimumHeight = dp(56)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { onSelected(position) }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        return box
    }

    private fun settingsSliderRow(title: String, value: Int, maxValue: Int = 100, suffix: String = "%", onChanged: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(8)) }
        val label = TextView(this).apply { text = "$title · $value$suffix"; titleStyle(16f) }
        box.addView(label)
        box.addView(materialSeekBar(this).apply {
            max = maxValue; progress = value.coerceIn(0, maxValue); contentDescription = title
            com.supermite.smp.ui.stylePlayerSeekBar(this@MainActivity, this)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = "$title · $progress$suffix"
                    if (fromUser) onChanged(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        return box
    }

    private fun jumpToLibrary(query: String = "", tag: String = "") {
        libraryQuery = query
        selectedTag = tag
        libraryFiltersExpanded = true
        openedPlaylist = null
        render(Page.LIBRARY)
    }

    private fun openTrackArtist(track: Track) {
        val info = subsonicInfo(track)
        val artistId = info.getOrNull(3).orEmpty()
        if (artistId.isNotBlank()) {
            onlineMusicSource = OnlineMusicSource.SUBSONIC
            subsonicBackStack.clear()
            subsonicScreen = SubsonicScreen.HOME
            render(Page.ONLINE)
            openSubsonicArtist(artistId, track.displayArtist)
        } else {
            jumpToLibrary(query = track.displayArtist)
        }
    }

    private fun openTrackAlbum(track: Track) {
        val info = subsonicInfo(track)
        val albumId = info.getOrNull(4).orEmpty()
        if (albumId.isNotBlank()) {
            onlineMusicSource = OnlineMusicSource.SUBSONIC
            subsonicBackStack.clear()
            subsonicScreen = SubsonicScreen.HOME
            render(Page.ONLINE)
            val album = subsonicAlbums.firstOrNull { it.id == albumId }
                ?: SubsonicAlbum(id = albumId, title = track.displayAlbum, artist = track.displayArtist)
            openSubsonicAlbum(album)
        } else {
            jumpToLibrary(query = track.displayAlbum)
        }
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
        box.addView(rating, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
                submitSubsonicEdit(track, selectedRating, tags.text.toString().split(Regex("[,，]")).map { it.trim() }.filter { it.isNotBlank() })
                trackAdapter.notifyDataSetChanged()
                if (page == Page.LIBRARY) render(Page.LIBRARY) else renderIfPlaylistChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun submitSubsonicEdit(track: Track, rating: Int, tags: List<String>) {
        val info = subsonicInfo(track)
        val serverId = info.getOrNull(1).orEmpty()
        val songId = info.getOrNull(2).orEmpty()
        if (songId.isBlank()) return
        val server = store.subsonicServers.firstOrNull { it.id == serverId } ?: return
        Thread {
            val result = runCatching {
                val client = subsonicClient(server)
                client.setRating(songId, rating)
                val wantsFavorite = tags.any { tag ->
                    tag.equals("喜爱", ignoreCase = true) ||
                        tag.equals("favorite", ignoreCase = true) ||
                        tag.equals("starred", ignoreCase = true)
                }
                if (wantsFavorite) client.starSong(songId, true)
            }
            runOnUiThread {
                result.onFailure {
                    Toast.makeText(this, "Subsonic 信息提交失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun submitSubsonicScrobble(track: Track) {
        val info = subsonicInfo(track)
        val serverId = info.getOrNull(1).orEmpty()
        val songId = info.getOrNull(2).orEmpty()
        if (songId.isBlank()) return
        val server = store.subsonicServers.firstOrNull { it.id == serverId } ?: return
        Thread {
            runCatching { subsonicClient(server).scrobble(songId, submission = true) }
        }.start()
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
        nowPagePlayButton?.text = ""
        nowPagePlayButton?.let { setButtonIcon(it, icon) }
        updatePlaybackNotification()
        updateMediaSessionState()
        broadcastPlaybackState("com.android.music.playstatechanged")
    }

    private fun buildSeekBar(): SeekBar {
        return materialSeekBar(this).apply {
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
        target.tag=track.id
        val remote=freshArtworkUrl(track)
        if (remote.isNotBlank()) {
            val cached = cachedRemoteArtworkPath(remote)
            if (cached.isNotBlank()) {
                decodeArtworkPath(cached)?.let {
                    target.setImageBitmap(it)
                    return
                }
            }
            target.setImageResource(R.drawable.ic_cover_placeholder)
            val local=decodeArtworkPath(track.artworkPath)
            if(local!=null) {target.setImageBitmap(local);return}
            loadRemoteImage(remote, target)
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

    private fun freshArtworkUrl(track:Track):String {
        if(track.artworkReference.startsWith("subsonic|")) {
            val ref=track.artworkReference.split('|',limit=3)
            val server=store.subsonicServers.firstOrNull {it.id==ref.getOrNull(1)}
            if(server!=null)return subsonicClient(server).coverArtUrl(ref.getOrNull(2).orEmpty())
        }
        if(track.artworkReference.startsWith("https://") || track.artworkReference.startsWith("http://"))return track.artworkReference
        return track.artworkPath.takeIf {it.startsWith("https://") || it.startsWith("http://")}.orEmpty()
    }

    private fun resolvedArtworkPath(track: Track): String {
        val explicit = track.artworkPath.takeIf { it.isNotBlank() }
        if (explicit != null && explicit.startsWith("http")) {
            val cached = cachedRemoteArtworkPath(explicit)
            if (cached.isNotBlank()) return cached
        }
        if (explicit != null && explicit.startsWith("content://")) return explicit
        if (explicit != null) cacheManager.resolveLegacyPath(explicit.removePrefix("file://")).takeIf { it.isFile }?.let { return it.absolutePath }
        freshArtworkUrl(track).takeIf { it.isNotBlank() }?.let { return cachedRemoteArtworkPath(it).ifBlank { it } }
        return siblingArtworkFile(track)?.absolutePath.orEmpty()
    }

    private fun decodeArtworkPath(path: String): android.graphics.Bitmap? {
        if (path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) return null
        return runCatching {
            if (path.startsWith("content://")) {
                contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
            } else {
                val file=cacheManager.resolveLegacyPath(path.removePrefix("file://"))
                val managedArtwork=file.canonicalPath.startsWith(cacheManager.directory(CacheCategory.ARTWORK).canonicalPath+File.separator)
                if(managedArtwork && !cacheManager.isEnabled(CacheCategory.ARTWORK))null
                else file.takeIf {it.isFile}?.let {cacheManager.storage.touch(it);BitmapFactory.decodeFile(it.absolutePath)}
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
        val local=cachedRemoteArtworkPath(expected)
        if(local.isNotBlank()) {
            val bitmap=decodeArtworkPath(local)
            if(bitmap!=null) {target.setImageBitmap(bitmap);return}
            cacheManager.artwork.invalidate(expected)
        }
        imageExecutor.execute {
            val bitmap = runCatching {
                var fetched:ByteArray?=null
                val path=if(cacheManager.isEnabled(CacheCategory.ARTWORK))cacheManager.artwork.getOrFetch(expected) {remoteImageBytes(expected).also {fetched=it}} else ""
                val bytes=path.takeIf {it.isNotBlank()}?.let {runCatching {File(it).readBytes()}.getOrNull()} ?: fetched ?: remoteImageBytes(expected)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.onFailure {
                Log.w(TAG, "Remote image failed: $expected", it)
            }.getOrNull()
            if (bitmap != null) {
                runOnUiThread {
                    if (target.tag == expected) target.setImageBitmap(bitmap)
                }
            } else cacheManager.artwork.invalidate(expected)
        }
    }

    private fun cachedRemoteArtworkPath(url: String): String {
        if(url.isBlank() || !cacheManager.isEnabled(CacheCategory.ARTWORK))return ""
        cacheManager.artwork.cachedPath(url).takeIf {it.isNotBlank()}?.let {return it}
        val ext=url.substringBefore('?').substringAfterLast('.',"img").lowercase(Locale.ROOT).takeIf {it.length in 2..5} ?: "img"
        val relative="remote_artwork/${url.hashCode()}.$ext"
        val legacy=cacheManager.legacyStreamFile(relative).takeIf {it.isFile}
            ?: File(getExternalFilesDir(null) ?: filesDir,"StreamCache/$relative").takeIf {it.isFile}
        return legacy?.let {runCatching {cacheManager.artwork.put(url,it.readBytes())}.getOrDefault("")}.orEmpty()
    }

    private fun cacheRemoteArtwork(url: String, allowNetwork: Boolean): String {
        if (url.isBlank() || !cacheManager.isEnabled(CacheCategory.ARTWORK)) return ""
        val cached = cachedRemoteArtworkPath(url)
        if (cached.isNotBlank()) return cached
        if (!allowNetwork) return ""
        return runCatching {cacheManager.artwork.getOrFetch(url) {remoteImageBytes(url)}}.getOrDefault("")
    }

    private fun remoteArtworkCacheFile(url: String): File {
        val cached=cacheManager.artwork.cachedPath(url)
        return if(cached.isNotBlank())File(cached) else cacheManager.keyedFile(CacheCategory.ARTWORK,com.supermite.smp.cache.ArtworkCache.canonicalKey(url),"img")
    }

    private fun remoteImageBytes(url: String): ByteArray {
        return if (url.contains("dizzylab.net", ignoreCase = true) && dizzylabCookie.isNotBlank()) {
            DizzylabClient(dizzylabCookie).imageBytes(url)
        } else if (url.contains("/rest/getCoverArt.view", ignoreCase = true)) {
            val server = selectedSubsonicServer()
            if (server != null) {
                runCatching { subsonicClient(server).fetchBytes(url) }.getOrElse {
                    fetchRemoteBytesWithTimeout(url)
                }
            } else {
                fetchRemoteBytesWithTimeout(url)
            }
        } else {
            fetchRemoteBytesWithTimeout(url)
        }
    }

    private fun fetchRemoteBytesWithTimeout(url: String): ByteArray {
        var lastError: Throwable? = null
        val attempts = if (weakNetworkMode) 2 else 1
        repeat(attempts) { attempt ->
            try {
                val connection = URL(url).openConnection().apply {
                    connectTimeout = if (weakNetworkMode) 30_000 else 10_000
                    readTimeout = if (weakNetworkMode) 180_000 else 30_000
                }
                connection.getInputStream().use { return it.readBytes() }
            } catch (error: Throwable) {
                lastError = error
                if (attempt + 1 < attempts) Thread.sleep(800)
            }
        }
        throw (lastError ?: IllegalStateException("Failed to fetch remote bytes"))
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
                child.setTextColor(Palette.ACCENT)
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
        themeColor = prefs.getInt("themeColor", Palette.DEFAULT_SEED)
        darkMode = DarkMode.entries.getOrElse(prefs.getInt("darkMode", DarkMode.SYSTEM.ordinal)) { DarkMode.SYSTEM }
        language = prefs.getString("language", "system") ?: "system"
        Lang.language = language
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
        debugMode = AppLogger.enabled
        autoWriteMetadata = prefs.getBoolean("autoWriteMetadata", false)
        allowMobileDataDownload = prefs.getBoolean("allowMobileDataDownload", true)
        extraScanFolderUris.replaceAllFromJson(prefs.getString("extraScanFolderUris", "") ?: "")
        skippedScanFolderUris.replaceAllFromJson(prefs.getString("skippedScanFolderUris", "") ?: "")
        playbackMode = PlaybackMode.entries.getOrElse(prefs.getInt("playbackMode", 0)) { PlaybackMode.SEQUENTIAL }
        profileName = prefs.getString("profileName", "profile")?.ifBlank { "profile" } ?: "profile"
        profileAvatarUri = prefs.getString("profileAvatarUri", "") ?: ""
        val savedDizzy = dizzylabSessionStore.read()
        dizzylabCookie = savedDizzy?.cookie ?: prefs.getString("dizzylabCookie", "").orEmpty()
        dizzylabToken = savedDizzy?.token.orEmpty()
        dizzylabUserId = savedDizzy?.uid ?: prefs.getString("dizzylabUserId", "").orEmpty()
        onlineMusicSource = OnlineMusicSource.fromKey(prefs.getString("onlineMusicSource", null))
        dizzyDownloadBitrate = prefs.getInt("dizzyDownloadBitrate", 320).let { if (it == 128) 128 else 320 }
        if (savedDizzy == null && dizzylabCookie.isNotBlank()) {
            runCatching { dizzylabSessionStore.save(dizzylabCookie, "", dizzylabUserId) }.onSuccess {
                prefs.edit().remove("dizzylabCookie").apply()
            }
        }
        streamPreloadCount = prefs.getInt("streamPreloadCount", 1).coerceIn(0, 5)
        streamDownloadFolderUri = prefs.getString("streamDownloadFolderUri", "") ?: ""
        streamCacheLimitGb = prefs.getFloat("streamCacheLimitGb", 2.0f).coerceAtLeast(0.1f)
        streamCacheFolderUri = prefs.getString("streamCacheFolderUri", "") ?: ""
        weakNetworkMode = prefs.getBoolean("weakNetworkMode", false)
        subsonicTranscodeMode = SubsonicTranscodeMode.entries.getOrElse(prefs.getInt("subsonicTranscodeMode", SubsonicTranscodeMode.LOSSLESS.ordinal)) { SubsonicTranscodeMode.LOSSLESS }
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
        debugMode = AppLogger.enabled
        val sessionSaved = runCatching {
            synchronized(dizzySessionLock) { dizzylabSessionStore.save(dizzylabCookie, dizzylabToken, dizzylabUserId) }
        }.isSuccess
        if (!sessionSaved) runOnUiThread { Toast.makeText(this, "Dizzylab 登录状态未能保存，请重新登录", Toast.LENGTH_SHORT).show() }
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("themeColor", themeColor)
            .putInt("darkMode", darkMode.ordinal)
            .putString("language", language)
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
            .apply { if (sessionSaved) remove("dizzylabCookie") }
            .putString("onlineMusicSource", onlineMusicSource.key)
            .putInt("dizzyDownloadBitrate", dizzyDownloadBitrate)
            .putString("dizzylabUserId", dizzylabUserId)
            .putInt("streamPreloadCount", streamPreloadCount.coerceIn(0, 5))
            .putString("streamDownloadFolderUri", streamDownloadFolderUri)
            .putFloat("streamCacheLimitGb", streamCacheLimitGb)
            .putString("streamCacheFolderUri", streamCacheFolderUri)
            .putBoolean("weakNetworkMode", weakNetworkMode)
            .putInt("subsonicTranscodeMode", subsonicTranscodeMode.ordinal)
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
            .mapNotNull { index -> store.trackForRecord(queueIds.optString(index)) }
            .ifEmpty { allTracks.sortedWith(MusicScanner.trackComparator) }
        val track = queue.firstOrNull { it.id == trackId } ?: store.trackForRecord(trackId) ?: return
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
            .put("cachePolicy", JSONObject().put("totalLimitMb", cacheManager.totalLimitMb).apply {
                CacheCategory.entries.forEach { category -> put(category.name, JSONObject().put("enabled", cacheManager.isEnabled(category)).put("limitMb", cacheManager.limitMb(category))) }
            })
            .put("loggingPolicy", JSONObject().put("enabled", AppLogger.enabled).put("level", AppLogger.level.name).put("retentionDays", AppLogger.retentionDays))
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
            .put("onlineMusicSource", onlineMusicSource.key)
            .put("dizzyDownloadBitrate", dizzyDownloadBitrate)
            .put("dizzylabUserId", dizzylabUserId)
            .put(
                "streamingAccounts",
                JSONObject().put(
                    "dizzylab",
                    JSONObject()
                        .put("userId", dizzylabUserId)
                        .put("loggedIn", dizzylabCookie.isNotBlank())
                )
            )
            .put("streamPreloadCount", streamPreloadCount)
            .put("streamDownloadFolderUri", streamDownloadFolderUri)
            .put("streamCacheLimitGb", streamCacheLimitGb)
            .put("streamCacheFolderUri", streamCacheFolderUri)
            .put("weakNetworkMode", weakNetworkMode)
            .put("subsonicTranscodeMode", subsonicTranscodeMode.ordinal)
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
        val previousDizzyCookie = dizzylabCookie
        onlineMusicSource = OnlineMusicSource.fromKey(settings.optString("onlineMusicSource", onlineMusicSource.key))
        dizzyDownloadBitrate = settings.optInt("dizzyDownloadBitrate", dizzyDownloadBitrate).let { if (it == 128) 128 else 320 }
        dizzylabCookie = settings.optString("dizzylabCookie", dizzylabCookie)
        dizzylabUserId = settings.optString("dizzylabUserId", dizzylabUserId)
        settings.optJSONObject("streamingAccounts")
            ?.optJSONObject("dizzylab")
            ?.let { dizzylab ->
                dizzylabCookie = dizzylab.optString("cookie", dizzylabCookie)
                dizzylabUserId = dizzylab.optString("userId", dizzylabUserId)
            }
        if (previousDizzyCookie != dizzylabCookie) {
            dizzySessionRevision++; dizzyMusicRequest++; dizzyAccountRequest++
            dizzylabToken = ""; dizzyUser = null; openedDizzylabAlbum = null
            dizzylabAlbums = emptyList(); dizzyFeed = emptyList(); dizzyFollowing = emptyList()
            dizzyMusicLoaded = false; dizzyMusicLoading = false; dizzyAccountLoading = false
        }
        streamPreloadCount = settings.optInt("streamPreloadCount", streamPreloadCount).coerceIn(0, 5)
        streamDownloadFolderUri = settings.optString("streamDownloadFolderUri", streamDownloadFolderUri)
        streamCacheLimitGb = settings.optDouble("streamCacheLimitGb", streamCacheLimitGb.toDouble()).toFloat().coerceAtLeast(0.1f)
        streamCacheFolderUri = settings.optString("streamCacheFolderUri", streamCacheFolderUri)
        settings.optJSONObject("cachePolicy")?.let { policy ->
            cacheManager.totalLimitMb = policy.optLong("totalLimitMb", cacheManager.totalLimitMb)
            CacheCategory.entries.forEach { category -> policy.optJSONObject(category.name)?.let {
                cacheManager.setLimitMb(category, it.optLong("limitMb", cacheManager.limitMb(category)))
                cacheManager.setEnabled(category, it.optBoolean("enabled", cacheManager.isEnabled(category)))
            } }
            store.pruneUnreferencedMetadata()
        }
        settings.optJSONObject("loggingPolicy")?.let { policy ->
            AppLogger.enabled = policy.optBoolean("enabled", AppLogger.enabled)
            AppLogger.level = LogLevel.entries.firstOrNull { it.name == policy.optString("level") } ?: AppLogger.level
            AppLogger.retentionDays = policy.optInt("retentionDays", AppLogger.retentionDays)
        }
        AppLogger.refreshCapture()
        weakNetworkMode = settings.optBoolean("weakNetworkMode", weakNetworkMode)
        subsonicTranscodeMode = SubsonicTranscodeMode.entries.getOrElse(settings.optInt("subsonicTranscodeMode", subsonicTranscodeMode.ordinal)) { subsonicTranscodeMode }
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
        val dark = isDarkModeActive(darkMode)
        setTheme(if (dark) R.style.AppTheme_Dark else R.style.AppTheme_Light)
        Palette.apply(dark, themeColor)
        window.setBackgroundDrawable(ColorDrawable(Palette.BG))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Palette.BG
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        refreshShellColors()
    }

    private fun refreshShellColors() {
        rootLayout?.setBackgroundColor(contentOverlayColor())
        headerPanel?.background = ColorDrawable(Palette.BG)
        if (::headerTitle.isInitialized) headerTitle.setTextColor(Palette.TEXT)
        if (::statusText.isInitialized) {
            statusText.setTextColor(Palette.TEXT)
            statusText.background = rippleDrawable(this, Palette.CONTAINER, 16)
            statusText.setCompoundDrawablesRelative(null, null, tintedDrawable(R.drawable.ic_expand_more, Palette.ACCENT, 28), null)
        }
        if (::miniPlayerPanel.isInitialized) miniPlayerPanel.background = ColorDrawable(Color.TRANSPARENT)
        if (::miniTitle.isInitialized) miniTitle.setTextColor(Palette.TEXT)
        if (::miniMeta.isInitialized) miniMeta.setTextColor(Palette.MUTED)
        if (::miniTime.isInitialized) miniTime.setTextColor(Palette.MUTED)
        if (::miniThumb.isInitialized) miniThumb.background = panelDrawable(Palette.PANEL_ALT, 16, this)
        bottomNavigationPanel?.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(Palette.CONTAINER)
            itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(Palette.SELECTED)
            itemRippleColor = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(Palette.TEXT, 31))
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            itemIconTintList = android.content.res.ColorStateList(states, intArrayOf(Palette.ON_SELECTED, Palette.MUTED))
            itemTextColor = android.content.res.ColorStateList(states, intArrayOf(Palette.TEXT, Palette.MUTED))
        }
        refreshThemedViews(rootLayout)
    }

    private fun refreshThemedViews(view: View?) {
        if (view is MaterialButton) {
            val role = view.tag?.toString()
            if (role in setOf("playerPrimary", "playerControl", "miniPlayerControl")) {
                val primary = role == "playerPrimary"
                view.backgroundTintList = stateColors(if (primary) Palette.ACCENT else Color.TRANSPARENT)
                view.setTextColor(stateColors(if (primary) Palette.ON_ACCENT else Palette.TEXT))
                view.iconTint = view.textColors
                view.rippleColor = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(view.currentTextColor, 31))
            } else view.setEmphasis(role == "md3Filled")
        }
        if (view is SeekBar) {
            if (view.tag == "playerProgress" || view.tag == "miniPlayerProgress") {
                com.supermite.smp.ui.stylePlayerSeekBar(this, view, compact = view.tag == "miniPlayerProgress")
            } else {
                view.progressTintList = android.content.res.ColorStateList.valueOf(Palette.ACCENT)
                view.thumbTintList = android.content.res.ColorStateList.valueOf(Palette.ACCENT)
                view.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Palette.SELECTED)
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) refreshThemedViews(view.getChildAt(index))
    }

    private fun buttonTextColor(): Int = Palette.ON_ACCENT_CONTAINER

    private fun dialogBuilder(): MaterialAlertDialogBuilder = RoundedDialogBuilder()

    private inner class RoundedDialogBuilder : MaterialAlertDialogBuilder(this@MainActivity) {
        init { setBackground(panelDrawable(Palette.DIALOG, 28, this@MainActivity)) }
        override fun setView(view: View?): MaterialAlertDialogBuilder {
            if (view == null) return super.setView(view)
            styleMaterialTree(view)
            val scroll = if (view is ScrollView) view else ScrollView(this@MainActivity).apply {
                isFillViewport = false
                addView(view)
            }
            scroll.clipToPadding = false
            return super.setView(scroll)
        }
        override fun show(): AlertDialog = super.show().also { styleNativeDialog(it) }
        override fun create(): AlertDialog = super.create().also { dialog ->
            dialog.setOnShowListener { styleNativeDialog(dialog) }
        }
    }

    private fun styleNativeDialog(dialog: AlertDialog) {
        val decor = dialog.window?.decorView ?: return
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (resources.configuration.screenWidthDp >= 600) dialog.window?.setLayout(dp(608), ViewGroup.LayoutParams.WRAP_CONTENT)
        styleMaterialTree(decor)
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            dialog.getButton(which)?.apply { setTextColor(Palette.ACCENT); minimumHeight = dp(48); isAllCaps = false }
        }
        // Cap modals to the available window; custom forms keep their own scroll container.
        decor.post {
            dialog.listView?.let { styleMaterialTree(it) }
            val available = (resources.displayMetrics.heightPixels - dp(96)).coerceAtLeast(dp(160))
            if (decor.height > available) dialog.window?.setLayout(dialog.window?.attributes?.width ?: -2, available)
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
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onSuccess { release ->
                    if (ReleasePolicy.isNewer(release, APP_VERSION, BuildConfig.VERSION_CODE, BuildConfig.RELEASE_BUILT_AT) || !silent) {
                        showUpdateDialog(release)
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Update check failed", error)
                    if (!silent) Toast.makeText(this, "检查更新失败，请稍后再试", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun fetchLatestRelease(): PublishedRelease {
        fun read(url: String): String {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SMP/$APP_VERSION")
            }
            return try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
        }
        val candidates = mutableListOf<PublishedRelease>()
        for (index in 1..10) {
            val page = JSONArray(read("$RELEASES_API&page=$index"))
            candidates += ReleasePolicy.parse(page)
            if (page.length() < 100) break
        }
        var release = ReleasePolicy.latest(candidates, includeBetaUpdates)
            ?: error("该${if (includeBetaUpdates) "预发行" else "正式"}频道没有已发布且附带 APK 的版本")
        if (release.metadataUrl.isNotBlank()) {
            runCatching { ReleasePolicy.withMetadata(release, JSONObject(read(release.metadataUrl))) }.onSuccess { release = it }
        }
        return release
    }

    private fun showUpdateDialog(release: PublishedRelease) {
        if (isFinishing || isDestroyed) return
        val newer = ReleasePolicy.isNewer(release, APP_VERSION, BuildConfig.VERSION_CODE, BuildConfig.RELEASE_BUILT_AT)
        val channel = if (release.prerelease) "预发行" else "正式版"
        val same = release.version == APP_VERSION && (release.versionCode == null || release.versionCode == BuildConfig.VERSION_CODE)
        dialogBuilder()
            .setTitle(if (newer) "发现新版本" else if (same) "已是该频道最新版本" else "该频道最新安装包")
            .setMessage("$channel：${release.version}\n发布日期：${release.publishedAt}\n安装包：${release.apkName}\n当前版本：$APP_VERSION\n\n" +
                if (!newer && !same) "该频道的安装包早于当前构建。Android 不允许直接安装较低 versionCode；不同旧签名也不能覆盖安装。" else "下载后由 Android 系统验证签名和安装。")
            .setPositiveButton("下载 APK") { _, _ -> openExternalUrl(release.apkUrl) }
            .setNeutralButton("发行说明") { _, _ -> openExternalUrl(release.pageUrl) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initDebugLogger() {
        AppLogger.initialize(this, cacheManager)
        debugMode = AppLogger.enabled
        WebDavClient.debugLogger = { msg -> writeDebugLog("WEBDAV", msg) }
    }

    private fun writeDebugLog(tag: String, msg: String) {
        AppLogger.debug(tag, msg)
    }

    private fun writeMetaDebugLog(msg: String) {
        AppLogger.debug("METADATA", msg)
    }

    private data class DownloadTask(
        val id: String = UUID.randomUUID().toString(),
        val source: String,
        val title: String,
        var totalBytes: Long = 0,
        var downloadedBytes: Long = 0,
        var progressPercent: Int = 0,
        var status: String = "排队中",
        val timestamp: Long = System.currentTimeMillis()
    )

    private fun enqueueDownload(source: String, title: String, totalBytes: Long = 0, block: (DownloadTask) -> Unit) {
        synchronized(downloadQueue) { downloadQueue.removeAll { it.status == "完成" } }
        downloadNotifyDone = 0
        val task = DownloadTask(source = source, title = title, totalBytes = totalBytes)
        synchronized(downloadQueue) { downloadQueue.add(0, task) }
        startDownloadRefresh()
        val executor = downloadExecutor ?: Executors.newSingleThreadExecutor().also { downloadExecutor = it }
        executor.execute {
            task.status = "下载中"
            try { block(task) } catch (_: Exception) { task.status = "失败" }
            val succeeded = task.status == "下载中" || task.status == "完成"
            if (succeeded) {
                task.status = "完成"
                synchronized(downloadQueue) { downloadQueue.remove(task) }
            }
            runOnUiThread {
                if (succeeded) downloadNotifyDone++
                updateDownloadNotificationFromQueue()
                val active = synchronized(downloadQueue) { downloadQueue.any { it.status == "下载中" || it.status == "排队中" } }
                if (!active) {
                    stopDownloadRefresh()
                    val failed = synchronized(downloadQueue) { downloadQueue.any { it.status == "失败" } }
                    finishDownloadNotification(if (failed) "下载结束，部分任务失败" else "下载完成", downloadNotifyDone, downloadNotifyTotal)
                }
                refreshDownloadListDialog()
            }
        }
    }

    private fun startDownloadRefresh() {
        if (downloadRefreshRunnable != null) return
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        downloadRefreshHandler = handler
        downloadRefreshRunnable = object : Runnable {
            override fun run() {
                updateDownloadNotificationFromQueue()
                refreshDownloadListDialog()
                if (downloadQueue.any { it.status != "完成" }) handler.postDelayed(this, 1000)
                else stopDownloadRefresh()
            }
        }
        handler.postDelayed(downloadRefreshRunnable!!, 1000)
    }

    private fun stopDownloadRefresh() {
        downloadRefreshHandler?.removeCallbacks(downloadRefreshRunnable ?: return)
        downloadRefreshRunnable = null
        downloadRefreshHandler = null
    }

    private fun updateDownloadNotificationFromQueue() {
        if (downloadNotifyTotal <= 0) return
        updateDownloadNotification("下载队列 $downloadNotifyDone/$downloadNotifyTotal", downloadNotifyDone, downloadNotifyTotal)
    }

    private fun getDownloadQueueSnapshot(): List<DownloadTask> = synchronized(downloadQueue) { downloadQueue.toList() }

    private fun showDownloadListDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        downloadListDialogBox = box
        buildDownloadListContent(box)
        scroll.addView(box)
        dialogBuilder().setTitle("下载列表").setView(scroll).setOnDismissListener { downloadListDialogBox = null }.setPositiveButton("关闭", null).show()
    }

    private fun refreshDownloadListDialog() {
        val box = downloadListDialogBox ?: return
        box.removeAllViews()
        buildDownloadListContent(box)
    }

    private fun buildDownloadListContent(box: LinearLayout) {
        val tasks = getDownloadQueueSnapshot().sortedByDescending { it.timestamp }
        if (tasks.isEmpty()) {
            box.addView(TextView(this).apply { text = "暂无下载任务"; bodyStyle(14f, Palette.MUTED); setPadding(dp(8), dp(16), dp(8), dp(16)) })
        } else {
            tasks.forEach { task ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
                }
                row.addView(TextView(this).apply { text = "${task.title} (${task.progressPercent}%)"; titleStyle(14f); maxLines = 1 })
                row.addView(TextView(this).apply { text = "[${task.source}] ${task.status}" + if (task.totalBytes > 0) " · ${formatFileSize(task.totalBytes)}" else ""; bodyStyle(14f, Palette.MUTED) })
                if (task.progressPercent in 1..99) {
                    val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = task.progressPercent }
                    row.addView(pb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(4) })
                }
                box.addView(row)
            }
        }
    }

    private fun mimeForName(name: String): String {
        val lower = name.lowercase()
        return when { lower.endsWith(".png") -> "image/png"; lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"; else -> "text/plain" }
    }

    private fun loadWebDavCache(serverId: String): MutableMap<String, List<WebDavItem>> {
        try {
            if(!cacheManager.isEnabled(CacheCategory.INDEX))return mutableMapOf()
            val file=cacheManager.keyedFile(CacheCategory.INDEX,serverId,"json").takeIf {it.isFile}
                ?: cacheManager.legacyStreamFile("webdav_index/${serverId}.json").takeIf {it.isFile} ?: return mutableMapOf()
            val envelope=JSONObject(file.readText())
            val savedAt=envelope.optLong("savedAt",file.lastModified())
            val ageHours = (System.currentTimeMillis() - savedAt) / 3600000L
            if (ageHours > 24) { runCatching { file.delete() }; return mutableMapOf() }
            val root = envelope.optJSONObject("directories") ?: envelope
            cacheManager.storage.touch(file)
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
            if(!cacheManager.isEnabled(CacheCategory.INDEX))return
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
            val target=cacheManager.keyedFile(CacheCategory.INDEX,serverId,"json")
            cacheManager.storage.write(CacheCategory.INDEX,target.name,JSONObject().put("savedAt",System.currentTimeMillis()).put("directories",root).toString().toByteArray())
        } catch (_: Exception) {}
    }

    private fun showSubsonicManagementDialog() {
        subsonicDialog?.dismiss()
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        store.subsonicServers.forEach { server ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    store.selectedSubsonicServerId = if (store.selectedSubsonicServerId == server.id) "" else server.id
                    clearSubsonicPageState()
                    store.save()
                    showSubsonicManagementDialog()
                }
            }
            val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val isSelected = server.id == store.selectedSubsonicServerId
            textBox.addView(TextView(this).apply {
                text = server.displayName; titleStyle(15f); maxLines = 1
                setTextColor(if (isSelected) Palette.ACCENT else Palette.TEXT)
            })
            textBox.addView(TextView(this).apply { text = server.url; bodyStyle(12f); maxLines = 1 })
            row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(actionButton(Lang.get("edit")) { showSubsonicEditDialog(server) }, LinearLayout.LayoutParams(dp(60), dp(38)).apply { rightMargin = dp(4) })
            row.setOnLongClickListener {
                dialogBuilder().setTitle(Lang.get("delete_server")).setMessage(Lang.get("confirm_delete").replace("%s", server.displayName))
                    .setPositiveButton(Lang.get("delete")) { _, _ ->
                        store.subsonicServers.removeAll { it.id == server.id }
                        if (store.selectedSubsonicServerId == server.id) store.selectedSubsonicServerId = ""
                        clearSubsonicPageState()
                        store.save()
                        showSubsonicManagementDialog()
                    }
                    .setNegativeButton(Lang.get("cancel"), null).show()
                true
            }
            box.addView(row)
        }
        box.addView(actionButton(Lang.get("add_server")) { showSubsonicEditDialog(null) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(4) })
        scroll.addView(box)
        subsonicDialog = dialogBuilder().setTitle(Lang.get("subsonic_mgmt")).setView(scroll).setPositiveButton(Lang.get("close")) { d, _ -> subsonicDialog = null }.show()
    }

    private fun showSubsonicEditDialog(existing: SubsonicServer?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(2)) }
        val urlInput = dialogInput("${Lang.get("domain")} (https://...)", existing?.url.orEmpty())
        box.addView(TextView(this).apply { text = Lang.get("domain"); bodyStyle(12f, Palette.MUTED) })
        box.addView(urlInput)
        val portInput = dialogInput(Lang.get("port"), if (existing?.port ?: 0 > 0) existing!!.port.toString() else "")
        box.addView(TextView(this).apply { text = Lang.get("port"); bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(portInput)
        val userInput = dialogInput(Lang.get("username"), existing?.username.orEmpty())
        box.addView(TextView(this).apply { text = Lang.get("username"); bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(userInput)
        val passInput = dialogInput(Lang.get("password"), existing?.password.orEmpty())
        box.addView(TextView(this).apply { text = Lang.get("password"); bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(passInput)
        val nameInput = dialogInput(Lang.get("server_note"), existing?.name.orEmpty())
        box.addView(TextView(this).apply { text = Lang.get("server_note"); bodyStyle(12f, Palette.MUTED); setPadding(0, dp(8), 0, 0) })
        box.addView(nameInput)
        val certCheck = materialCheckBox(this).apply { text = Lang.get("ignore_cert"); isChecked = existing?.ignoreCert ?: false; setTextColor(Palette.TEXT); setPadding(0, dp(8), 0, 0) }
        box.addView(certCheck)
        dialogBuilder()
            .setTitle(if (existing == null) Lang.get("subsonic_add") else Lang.get("subsonic_edit"))
            .setView(box)
            .setPositiveButton(Lang.get("save")) { _, _ ->
                val url = urlInput.text.toString().trim().trimEnd('/')
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(this, "Subsonic 地址必须以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val server = SubsonicServer(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = nameInput.text.toString().trim(),
                    url = url,
                    username = userInput.text.toString().trim(),
                    password = passInput.text.toString().trim(),
                    port = portInput.text.toString().trim().toIntOrNull() ?: 0,
                    ignoreCert = certCheck.isChecked
                )
                setStatus("正在验证 Subsonic 服务器...")
                Thread {
                    val result = subsonicClient(server).ping()
                    runOnUiThread {
                        result.onSuccess { msg ->
                            if (existing != null) store.subsonicServers.removeAll { it.id == server.id }
                            store.subsonicServers.add(server)
                            if (store.selectedSubsonicServerId.isBlank()) store.selectedSubsonicServerId = server.id
                            clearSubsonicPageState()
                            store.save()
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                            setStatus("Subsonic 服务器验证成功")
                            showSubsonicManagementDialog()
                        }.onFailure { error ->
                            Toast.makeText(this, "Subsonic 验证失败：${error.message}", Toast.LENGTH_SHORT).show()
                            setStatus("Subsonic 服务器验证失败")
                        }
                    }
                }.start()
            }
            .setNegativeButton(Lang.get("cancel"), null).show()
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
                background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
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
        val certCheck = materialCheckBox(this).apply { text = "忽略证书问题"; isChecked = existing?.ignoreCert ?: false; setTextColor(Palette.TEXT); setPadding(0, dp(8), 0, 0) }
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
                writeDebugLog("TEST", "验证 WebDAV 服务器，port=${server.port}, ignoreCert=${server.ignoreCert}")
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
            val parentPath = webDavParentPath(webDavCurrentPath)
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

    private fun webDavParentPath(path: String): String {
        val normalized = path.trim('/').trim()
        if (normalized.isBlank()) return ""
        return normalized.substringBeforeLast('/', "")
    }

    private fun isMusicFile(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return listOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".wma", ".opus").any { lower.endsWith(it) }
    }

    private fun isLyricsFile(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT); return lower.endsWith(".lrc") || lower.endsWith(".txt")
    }

    private fun webDavItemToTrack(item: WebDavItem, server: WebDavServer): Track {
        val fullUrl = WebDavClient(server.url,server.username,server.password,server.port,server.ignoreCert).resourceUrl(item.path)
        val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = when (ext) { "flac" -> "audio/flac"; "wav" -> "audio/wav"; "m4a" -> "audio/mp4"; "ogg" -> "audio/ogg"; "aac" -> "audio/aac"; "opus" -> "audio/ogg"; else -> "audio/mpeg" }
        return Track(id = "webdav:${server.id}:${com.supermite.smp.cache.CacheStorage.sha256(item.path.toByteArray())}", uri = fullUrl, title = item.name.substringBeforeLast('.'), artist = server.displayName, album = webDavCurrentPath.substringAfterLast('/').ifBlank { server.displayName }, durationMs = 0L, mimeType = mimeType, sourcePath = fullUrl,sourceReference=fullUrl)
    }

    private fun fetchWebDavTrackMetadata(item: WebDavItem, server: WebDavServer): Track? {
        if(!cacheManager.isEnabled(CacheCategory.TEMP))return null
        val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert)
        val headBytes = client.fetchRangeBytes(item.path, 0, 131072) ?: return null
        return runCatching {
            val work=cacheManager.createWorkFile(item.name.substringAfterLast('.',"tmp")) ?: return null
            work.use {
            work.outputStream().use {it.write(headBytes)}
            val retriever = MediaMetadataRetriever()
            try {
            retriever.setDataSource(work.file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (title.isBlank() && artist.isBlank()) return null
            val fullUrl = client.resourceUrl(item.path)
            val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mimeType = when (ext) { "flac" -> "audio/flac"; "wav" -> "audio/wav"; "m4a" -> "audio/mp4"; "ogg" -> "audio/ogg"; "aac" -> "audio/aac"; else -> "audio/mpeg" }
            Track(id = "webdav:${server.id}:${com.supermite.smp.cache.CacheStorage.sha256(item.path.toByteArray())}", uri = fullUrl, title = title.ifBlank { item.name.substringBeforeLast('.') }, artist = artist.ifBlank { server.displayName }, album = album.ifBlank { webDavCurrentPath.substringAfterLast('/').ifBlank { server.displayName } }, durationMs = duration, mimeType = mimeType, sourcePath = fullUrl,sourceReference=fullUrl)
            } finally {retriever.release()}
            }
        }.getOrNull()
    }

    private fun webDavItemCard(item: WebDavItem, isDir: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); background = panelDrawable(Palette.PANEL, 12, this@MainActivity); setOnClickListener { onClick() }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }; tag = "dav_item" }
        row.addView(ImageView(this).apply { setImageResource(if (isDir) R.drawable.ic_folder else R.drawable.ic_song); imageTintList = android.content.res.ColorStateList.valueOf(Palette.MUTED); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(16) })
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply { text = item.name; titleStyle(15f); maxLines = 2 })
        if (!isDir && item.size > 0) { textBox.addView(TextView(this).apply { text = formatFileSize(item.size); bodyStyle(12f, Palette.MUTED) }) }
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); return row
    }

    private fun webDavTrackCard(track: Track, item: WebDavItem): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)); background = panelDrawable(Palette.PANEL, 12, this@MainActivity); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }; setOnClickListener { playWebDavTrack(track) }; setOnLongClickListener { dialogBuilder().setTitle(track.displayTitle).setItems(arrayOf("下载")) { _, _ -> downloadWebDavTrack(track, item) }.show(); true }; tag = "dav_item" }
        row.addView(ImageView(this).apply { setImageResource(R.drawable.ic_song); imageTintList = android.content.res.ColorStateList.valueOf(Palette.MUTED); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(16) })
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply { text = track.displayTitle; titleStyle(15f); maxLines = 2 })
        textBox.addView(TextView(this).apply { text = listOfNotNull(if (item.size > 0) formatFileSize(item.size) else null, item.lastModified.ifBlank { null }).joinToString(" \u00B7 "); bodyStyle(14f, Palette.MUTED) })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); return row
    }

    private fun formatFileSize(bytes: Long): String {
        return when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0); bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0)); else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0)) }
    }

    private fun playWebDavTrack(track: Track) {
        val server = webDavCurrentServer ?: return
        val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert,server.id)
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
        if (item != null) {
            startWebDavPlayback(track,item,server,currentQueue,true)
            preloadWebDavTracks(client,allMusic,allTracks,item,cacheManager.directory(CacheCategory.AUDIO))
            return
        }
        playTrack(track, currentQueue)
    }

    private fun webDavCacheRelative(serverId: String, item: WebDavItem): String =
        "webdav/${com.supermite.smp.cache.CacheStorage.sha256("$serverId|${item.path}".toByteArray())}.${item.name.substringAfterLast('.',"mp3").filter(Char::isLetterOrDigit).take(8)}"

    private fun prepareWebDavRecordedTrack(track: Track, queue: List<Track>, countPlay: Boolean = true): Boolean {
        if(!track.id.startsWith("webdav:"))return false
        val source=track.sourceReference.takeIf {it.startsWith("http://") || it.startsWith("https://")}
            ?: track.sourcePath.takeIf {it.startsWith("http://") || it.startsWith("https://")} ?: track.uri
        val target=Uri.parse(source)
        if(target.scheme !in setOf("http","https"))return false
        fun port(uri:Uri)=if(uri.port>=0)uri.port else if(uri.scheme=="https")443 else 80
        val candidates=store.webDavServers.mapNotNull {server ->
            val base=Uri.parse(WebDavClient(server.url,server.username,server.password,server.port,server.ignoreCert,server.id).resourceUrl(""))
            val prefix=base.encodedPath.orEmpty().trimEnd('/')+"/"
            if(target.scheme==base.scheme && target.host==base.host && port(target)==port(base) && target.encodedPath.orEmpty().startsWith(prefix))Triple(server,base,prefix) else null
        }
        val hasServerMarker=track.id.removePrefix("webdav:").contains(':')
        val matched=if(hasServerMarker)candidates.filter {track.id.startsWith("webdav:${it.first.id}:")} else candidates
        val longest=matched.maxOfOrNull {it.third.length}
        val chosen=matched.filter {it.third.length==longest}.singleOrNull()
        if(chosen==null) {Toast.makeText(this,"此歌曲的 WebDAV 服务器已移除，请重新添加账号。",Toast.LENGTH_LONG).show();return true}
        val path=Uri.decode(target.encodedPath.orEmpty().removePrefix(chosen.third)).trimStart('/')
        val item=WebDavItem(target.lastPathSegment.orEmpty().ifBlank {track.title},path,false)
        startWebDavPlayback(track,item,chosen.first,queue.ifEmpty {listOf(track)},countPlay)
        return true
    }

    private fun startWebDavPlayback(track: Track, item: WebDavItem, server: WebDavServer, queue: List<Track>, countPlay: Boolean) {
        val request=++playbackRequestGeneration
        val client=WebDavClient(server.url,server.username,server.password,server.port,server.ignoreCert,server.id)
        val relative=webDavCacheRelative(server.id,item)
        val direct=track.copy(uri=client.resourceUrl(item.path),sourcePath=client.resourceUrl(item.path),sourceReference=client.resourceUrl(item.path))
        cacheManager.leaseAudio(relative)?.let {playCachedTrack(it,direct,queue,countPlay);return}
        if(!cacheManager.isEnabled(CacheCategory.AUDIO) && !cacheManager.isEnabled(CacheCategory.TEMP)) {
            Toast.makeText(this,"音频及临时缓存均已关闭，正在尝试服务器直连。Digest 服务可能需要开启临时缓存。",Toast.LENGTH_LONG).show()
            playTrack(direct,queue,countPlay=countPlay,resolveRemote=false)
            return
        }
        setStatus("正在缓冲 ${track.displayTitle}…")
        Thread {
            val result=runCatching {synchronized(audioCacheLock(relative)) {
                cacheManager.leaseAudio(relative) ?: cacheManager.storePlayback(relative,client.download(item.path))
            }}
            runOnUiThread {
                val lease=result.getOrNull()
                if(isDestroyed || request!=playbackRequestGeneration) {lease?.close();return@runOnUiThread}
                if(result.isFailure) {AppLogger.warn("PLAYBACK","WebDAV buffering failed",result.exceptionOrNull());Toast.makeText(this,"WebDAV 缓冲失败，请检查连接。",Toast.LENGTH_LONG).show();return@runOnUiThread}
                if(lease==null) {
                    Toast.makeText(this,"缓存预算不足，正在尝试直连；可在缓存设置中提高音频或临时缓存上限。",Toast.LENGTH_LONG).show()
                    playTrack(direct,queue,countPlay=countPlay,resolveRemote=false)
                } else playCachedTrack(lease,direct,queue,countPlay)
            }
        }.start()
    }

    private fun preloadWebDavTracks(client: WebDavClient, allItems: List<WebDavItem>, allTracks: List<Track>, current: WebDavItem, cacheDir: File) {
        if(!cacheManager.isEnabled(CacheCategory.AUDIO))return
        val preloadCount = streamPreloadCount.coerceIn(0, 5)
        if (preloadCount <= 0) return
        val startIdx = allItems.indexOf(current)
        if (startIdx < 0) return
        allItems.drop(startIdx + 1).take(preloadCount).forEach { item ->
            val relative=webDavCacheRelative(client.cacheIdentity,item)
            if(cacheManager.storage.find(CacheCategory.AUDIO,relative)==null && audioCacheFlights.add(relative)) {
                Thread {
                    try {synchronized(audioCacheLock(relative)) {
                        if(cacheManager.storage.find(CacheCategory.AUDIO,relative)==null && cacheManager.isEnabled(CacheCategory.AUDIO))
                            cacheManager.storage.write(CacheCategory.AUDIO,relative,client.download(item.path))
                    }} catch(failure:Exception) {AppLogger.warn("CACHE","WebDAV preload failed",failure)}
                    finally {audioCacheFlights.remove(relative)}
                }.start()
            }
        }
    }

    private fun downloadWebDavTrack(track: Track, item: WebDavItem) {
        val server = webDavCurrentServer ?: return
        if (!ensureStreamDownloadFolderReady { downloadWebDavTrack(track, item) }) return
        checkMobileDataForDownload {
        val folderName = item.path.substringBeforeLast('/').substringAfterLast('/').ifBlank {
            webDavCurrentPath.substringAfterLast('/').ifBlank { server.name.ifBlank { server.url.substringAfterLast('/').trimEnd('/').ifBlank { "webdav" } } }
        }
        writeDebugLog("DOWNLOAD", "下载文件夹: $folderName, 文件: ${item.name}")
        downloadNotifyTotal = 1; downloadNotifyDone = 0
        setStatus("已加入下载队列...")
        updateDownloadNotification("下载队列 0/1", 0, 1)
        enqueueDownload("webdav", track.displayTitle, item.size) { task ->
            downloadWebDavTrackInternal(track, item, server, folderName, skipExtra = false, onProgress = { progress, totalB ->
                task.progressPercent = if (totalB > 0) (progress * 100L / totalB).toInt() else 50
                task.downloadedBytes = progress
            })
        }
        }
    }

    private fun downloadWebDavExtraFiles(client: WebDavClient, item: WebDavItem, folderName: String) {
        val dirPath = item.path.substringBeforeLast('/')
        if (dirPath.isBlank()) return
        runCatching {
            val items = client.listDirectory(dirPath)
            items.filter { it.name.endsWith(".cue", ignoreCase = true) || it.name.endsWith(".jpg", ignoreCase = true) || it.name.endsWith(".jpeg", ignoreCase = true) || it.name.endsWith(".png", ignoreCase = true) }.distinctBy { it.name.lowercase() }.forEach { extra ->
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
            try {
            retriever.setDataSource(this, Uri.parse(uri))
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.ifBlank { null }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.ifBlank { null }
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.ifBlank { null }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val coverBytes = retriever.embeddedPicture
            val artworkPath = if (coverBytes != null) {
                cacheManager.artwork.put("embedded:${fallback.id}",coverBytes).ifBlank {fallback.artworkPath}
            } else fallback.artworkPath
            fallback.copy(
                title = title ?: fallback.title,
                artist = artist ?: fallback.artist,
                album = album ?: fallback.album,
                durationMs = duration ?: fallback.durationMs,
                artworkPath = artworkPath
            )
            } finally {retriever.release()}
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
        val total = allFiles.size
        downloadNotifyTotal = total
        downloadNotifyDone = 0
        setStatus("已加入下载队列 ($total 首)...")
        updateDownloadNotification("下载队列 0/$total", 0, total)
        var albumFolder: String? = null
        allFiles.forEachIndexed { index, item ->
            val track = webDavItemToTrack(item, server)
            val isFirst = index == 0
            enqueueDownload("webdav", track.displayTitle, item.size) { task ->
                val folder = albumFolder ?: item.path.substringBeforeLast('/').substringAfterLast('/').ifBlank {
                    webDavCurrentPath.substringAfterLast('/').ifBlank { server.name.ifBlank { "webdav" } }
                }
                albumFolder = folder
                downloadWebDavTrackInternal(track, item, server, folder, skipExtra = !isFirst, onProgress = { progress, totalB ->
                    task.progressPercent = if (totalB > 0) (progress * 100L / totalB).toInt() else 50
                    task.downloadedBytes = progress
                })
            }
        }
        }
    }

    private fun downloadWebDavTrackInternal(track: Track, item: WebDavItem, server: WebDavServer, folderName: String, skipExtra: Boolean = false, onProgress: (progress: Long, total: Long) -> Unit) {
        val client = WebDavClient(server.url, server.username, server.password, server.port, server.ignoreCert,server.id)
        val totalBytes = item.size.coerceAtLeast(1)
        runCatching {
            val ext = item.name.substringAfterLast('.', "mp3")
            val relative=webDavCacheRelative(server.id,item)
            val bytes=synchronized(audioCacheLock(relative)) {
                cacheManager.leaseAudio(relative)?.use {it.file.readBytes()} ?: client.download(item.path).also {
                    cacheManager.storage.write(CacheCategory.AUDIO,relative,it)
                }
            }
            onProgress(totalBytes / 2, totalBytes)
            val fileName = safeFileName("${track.displayTitle}.$ext")
            val lyricsBytes = tryDownloadWebDavLyrics(client, item)
            val lyricsUri = if (lyricsBytes != null) { writeStreamDownloadBytes("webdav", folderName, safeFileName("${track.displayTitle}.lrc"), "text/plain", lyricsBytes) } else ""
            val uri = writeStreamDownloadBytes("webdav", folderName, fileName, track.mimeType.ifBlank { "audio/mpeg" }, bytes)
            val scanned = rescanMetadata(uri, track)
            val downloaded = scanned.copy(id = "download:webdav:${uri.hashCode()}", uri = uri, lyricsUri = lyricsUri, sourcePath = localPathForUriString(uri).ifBlank { fileName })
            if (!skipExtra) downloadWebDavExtraFiles(client, item, folderName)
            onProgress(totalBytes, totalBytes)
            runOnUiThread {
                allTracks = (allTracks.filter { it.sourcePath != downloaded.sourcePath } + downloaded).distinctBy { it.id }.sortedWith(MusicScanner.trackComparator)
                store.saveTracks(allTracks); store.createAlbumPlaylistsIfNeeded(allTracks); renderIfLibraryVisible()
            }
        }.onFailure { error -> runOnUiThread { writeDebugLog("DOWNLOAD", "下载失败: ${track.displayTitle} - ${error.message}") } }
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

    private fun showDizzylabLogin(onSuccess: (() -> Unit)? = null) {
        showDizzylabWebPage(DIZZYLAB_LOGIN_URL, "登录 Dizzylab") {
            dizzyAccountRequest++
            dizzyAccountLoading = false
            dizzylabToken = ""
            dizzyUser = null
            dizzyAccountError = ""
            loadDizzylabAccount {
                dizzyMusicLoaded = false
                onSuccess?.invoke()
            }
        }
    }

    private fun showDizzylabWebPage(url: String, title: String, onClosed: (() -> Unit)? = null) {
        val returnPage = page
        val albumToRefresh = openedDizzylabAlbum?.takeIf { page == Page.ONLINE && onlineMusicSource == OnlineMusicSource.DIZZYLAB }
        val returnToAccount = dizzyAlbumReturnToAccount
        val address = Uri.parse(url)
        if (address.scheme != "https" || address.host !in setOf("www.dizzylab.net", "dizzylab.net")) return
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            val manager = CookieManager.getInstance()
            manager.setAcceptCookie(true)
            if (manager.getCookie(DizzylabApiClient.BASE).orEmpty().split(';').none { it.trim().startsWith("sessionid=") }) {
                dizzylabCookie.split(';').map { it.trim() }.filter { it.startsWith("sessionid=") || it.startsWith("csrftoken=") }.forEach { pair ->
                    manager.setCookie(DizzylabApiClient.BASE, pair + "; Path=/; Secure" + if (pair.startsWith("sessionid=")) "; HttpOnly" else "")
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    if (!request.isForMainFrame) return false
                    if (isDizzylabPackageUrl(uri)) { queueDizzylabWebPackage(uri.toString(), title); return true }
                    if (uri.scheme == "https" && uri.host in setOf("www.dizzylab.net", "dizzylab.net")) return false
                    // Payment pages finish in the browser/payment app; no native checkout or SDK credentials.
                    if (uri.scheme == "https" || uri.scheme == "alipays") {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            .onFailure { Toast.makeText(this@MainActivity, "无法打开该网页或支付应用", Toast.LENGTH_SHORT).show() }
                    }
                    return true
                }
                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    saveDizzylabCookieFromWeb(loadedUrl.orEmpty())
                }
            }
            setDownloadListener { downloadUrl, _, _, _, _ ->
                if (isDizzylabPackageUrl(Uri.parse(downloadUrl))) queueDizzylabWebPackage(downloadUrl, title)
                else Toast.makeText(this@MainActivity, "请在系统浏览器中下载该类型的文件", Toast.LENGTH_LONG).show()
            }
            loadUrl(url)
        }
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Palette.BG)
        }
        wrapper.addView(TextView(this).apply {
            text = title; titleStyle(22f); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(8))
        })
        val toolbar = LinearLayout(this)
        toolbar.addView(actionButton("网页返回") { if (webView.canGoBack()) webView.goBack() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        toolbar.addView(actionButton("关闭网页") { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        wrapper.addView(toolbar)
        wrapper.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        wrapper.addView(actionButton("完成并刷新") { dialog.dismiss() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        styleMaterialTree(wrapper)
        dialog.setContentView(wrapper)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && webView.canGoBack()) { webView.goBack(); true } else false
        }
        dialog.setOnDismissListener {
            saveDizzylabCookieFromWeb(webView.url.orEmpty())
            webView.stopLoading(); webView.destroy()
            if (onClosed != null) onClosed() else if (hasDizzylabSession()) {
                dizzyMusicLoaded = false
                loadDizzylabAccount {
                    if (page == returnPage) albumToRefresh?.album?.let { loadDizzylabAlbum(it, returnToAccount) }
                }
            } else if (page == Page.DIZZYLAB_ACCOUNT) render(Page.DIZZYLAB_ACCOUNT)
        }
        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(Palette.BG))
        }
    }

    private fun saveDizzylabCookieFromWeb(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" || uri.host !in setOf("www.dizzylab.net", "dizzylab.net")) return
        val cookie = CookieManager.getInstance().getCookie("https://www.dizzylab.net/").orEmpty()
        fun sessionId(value: String) = value.split(';').map { it.trim() }.firstOrNull { it.startsWith("sessionid=") }.orEmpty()
        val changedAccount = sessionId(cookie) != sessionId(dizzylabCookie)
        if (sessionId(cookie).isBlank() && !changedAccount) return
        if (cookie != dizzylabCookie) {
            synchronized(dizzySessionLock) {
                dizzylabCookie = if (sessionId(cookie).isBlank()) "" else cookie
                if (changedAccount) {
                    dizzySessionRevision++; dizzyMusicRequest++; dizzyAccountRequest++
                    dizzylabToken = ""; dizzylabUserId = ""; dizzyUser = null
                    dizzylabAlbums = emptyList(); openedDizzylabAlbum = null
                    dizzyFeed = emptyList(); dizzyFollowing = emptyList()
                    dizzyMusicLoaded = false; dizzyMusicLoading = false; dizzyAccountLoading = false
                    dizzyMusicError = ""; dizzyAccountError = ""
                }
            }
            saveSettings()
        }
        CookieManager.getInstance().flush()
    }

    private fun loadDizzylabAlbum(album: StreamAlbum, fromAccount: Boolean = false) {
        rememberDizzylabListPosition()
        dizzyMusicLoading = false
        val request = ++dizzyMusicRequest
        val revision = dizzySessionRevision
        val discId = if (album.id.startsWith("http")) Uri.parse(album.url).lastPathSegment.orEmpty() else album.id
        setStatus("正在加载专辑：${album.title}")
        showLoading()
        Thread {
            val result = runCatching { dizzylabApi(false).details(discId) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                hideLoading()
                if (request != dizzyMusicRequest || revision != dizzySessionRevision) return@runOnUiThread
                result.onSuccess { details ->
                    store.rememberTracks(details.tracks)
                    onlineMusicSource = OnlineMusicSource.DIZZYLAB
                    openedDizzylabAlbum = details.copy(description = plainDizzylabText(details.description))
                    dizzyAlbumReturnToAccount = fromAccount
                    render(Page.ONLINE)
                }.onFailure { Toast.makeText(this, it.message ?: "专辑加载失败", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showDizzylabAlbumMenu(details: StreamAlbumDetails) {
        val actions = if (details.purchased) arrayOf("下载整张专辑", "在网页查看") else arrayOf("在网页查看或购买")
        dialogBuilder().setTitle(details.album.title).setItems(actions) { _, index ->
            if (details.purchased && index == 0) downloadStreamAlbum(details)
            else showDizzylabWebPage(details.album.url, details.album.title)
        }.show()
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

    private fun downloadStreamAlbum(details: StreamAlbumDetails, checkMobile: Boolean = true) {
        if (checkMobile) { checkMobileDataForDownload { downloadStreamAlbum(details, false) }; return }
        if (!details.purchased) { Toast.makeText(this, "请先领取或在网页购买该专辑", Toast.LENGTH_LONG).show(); return }
        if (details.tracks.isEmpty()) {
            Toast.makeText(this, "没有可下载的音频", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ensureStreamDownloadFolderReady { downloadStreamAlbum(details, false) }) return
        val total = details.tracks.size
        val bitrate = dizzyDownloadBitrate
        val revision = dizzySessionRevision
        downloadNotifyTotal = total; downloadNotifyDone = 0
        setStatus("已加入下载队列 ($total 首)...")
        updateDownloadNotification("下载队列 0/$total", 0, total)
        Thread {
            val coverPath = downloadStreamCover(details)
            details.tracks.forEach { track ->
                enqueueDownload("dizzylab", track.displayTitle) { task ->
                    runCatching {
                        val result = downloadStreamTrackSync(track, details, coverPath, bitrate, revision)
                        checkNotNull(result) { "下载失败" }
                        runOnUiThread { addDownloadedTracksToLibrary(listOf(result), details) }
                        task.progressPercent = 100
                    }.onFailure { task.status = "失败" }
                }
            }
        }.start()
    }

    private fun downloadStreamTrack(track: Track, checkMobile: Boolean = true) {
        if (checkMobile) { checkMobileDataForDownload { downloadStreamTrack(track, false) }; return }
        if (!hasDizzylabSession()) { showDizzylabLogin(); return }
        val ref = DizzylabTrackRef.from(track)
        if (ref == null) { Toast.makeText(this, "请重新打开 Dizzylab 专辑后下载", Toast.LENGTH_LONG).show(); return }
        if (!ensureStreamDownloadFolderReady { downloadStreamTrack(track, false) }) return
        val details = openedDizzylabAlbum?.takeIf { it.album.id == ref.discId } ?: StreamAlbumDetails(
            album = StreamAlbum(ref.discId, track.displayAlbum, "${DizzylabApiClient.BASE}/d/${DizzylabApiClient.encode(ref.discId)}/", track.artworkPath),
            tracks = listOf(track), circle = track.displayArtist
        )
        val bitrate = dizzyDownloadBitrate
        val revision = dizzySessionRevision
        downloadNotifyTotal = 1; downloadNotifyDone = 0
        updateDownloadNotification("下载队列 0/1", 0, 1)
        Thread {
            val coverPath = downloadStreamCover(details)
            enqueueDownload("dizzylab", track.displayTitle) { task ->
                runCatching {
                    val result = downloadStreamTrackSync(track, details, coverPath, bitrate, revision)
                    checkNotNull(result) { "下载失败" }
                    runOnUiThread { addDownloadedTracksToLibrary(listOf(result), details) }
                    task.progressPercent = 100
                }.onFailure { task.status = "失败" }
            }
        }.start()
    }

    private fun downloadStreamTrackSync(track: Track, details: StreamAlbumDetails, coverPath: String, bitrate: Int, revision: Int): Track? {
        return runCatching {
            val ref = DizzylabTrackRef.from(track) ?: error("请重新打开 Dizzylab 专辑后下载")
            check(revision == dizzySessionRevision) { "账号已切换，下载已取消" }
            val downloadUrl = dizzylabApi(true).downloadUrl(ref, bitrate)
            val bytes = DizzylabClient(dizzylabCookie).download(downloadUrl, DizzylabApiClient.BASE)
            check(revision == dizzySessionRevision) { "账号已切换，下载已取消" }
            val fileName = safeFileName("${track.displayTitle}.mp3")
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
            Log.w(TAG, "Dizzylab track download failed")
        }.getOrNull()
    }

    private fun tagDownloadedStreamBytes(
        fileName: String,
        bytes: ByteArray,
        track: Track,
        details: StreamAlbumDetails,
        coverPath: String
    ): ByteArray {
        return tagCachedDownloadBytes(fileName,bytes,AudioMetadataUpdate(
                    title = track.displayTitle,
                    artist = track.artist.ifBlank { details.circle },
                    composer = track.composer,
                    year = track.date.take(4).ifBlank { details.releaseDate.take(4) },
                    album = details.album.title,
                    coverBytes = coverBytesAtPath(coverPath)
                ))
    }

    private fun coverBytesAtPath(path: String):ByteArray? = runCatching {
        if(path.isBlank())null else if(path.startsWith("content://"))contentResolver.openInputStream(Uri.parse(path))?.use {it.readBytes()}
        else cacheManager.resolveLegacyPath(path.removePrefix("file://")).takeIf {it.isFile}?.readBytes()
    }.getOrNull()

    private fun tagCachedDownloadBytes(fileName:String,bytes:ByteArray,update:AudioMetadataUpdate):ByteArray {
        val work=cacheManager.createWorkFile(fileName.substringAfterLast('.',"tmp")) ?: return bytes
        return work.use {
            runCatching {
                work.outputStream().use {it.write(bytes)}
                val result=AudioMetadataWriter().write(work.file,update)
                work.validate()
                if(result.success)work.file.readBytes() else bytes
            }.onFailure {AppLogger.warn("METADATA","Download tagging skipped; temporary cache policy or writer rejected the operation",it)}.getOrDefault(bytes)
        }
    }

    private fun downloadStreamCover(details: StreamAlbumDetails): String {
        val url = details.album.coverUrl.ifBlank { details.tracks.firstOrNull()?.artworkPath.orEmpty() }
        if (url.isBlank()) return ""
        return runCatching {
            var fetched:ByteArray?=null
            fun fetch():ByteArray = if(url.contains("dizzylab.net",true) && dizzylabCookie.isNotBlank())DizzylabClient(dizzylabCookie).imageBytes(url,details.album.url) else remoteImageBytes(url)
            val cached=if(cacheManager.isEnabled(CacheCategory.ARTWORK))cacheManager.artwork.getOrFetch(url) {fetch().also {fetched=it}} else ""
            val bytes=cached.takeIf {it.isNotBlank()}?.let {runCatching {File(it).readBytes()}.getOrNull()} ?: fetched ?: fetch()
            val coverName = safeFileName("${details.album.title}.jpg")
            // This explicit user download is a durable music artifact, outside disposable cache.
            writeStreamDownloadBytes("dizzylab", details.album.title, coverName, "image/jpeg", bytes).ifBlank {cached}
        }.onFailure {
            Log.w(TAG, "DizzyLab cover download failed: $url", it)
        }.getOrDefault("")
    }

    private fun isDizzylabPackageUrl(uri: Uri): Boolean = uri.scheme == "https" &&
        (uri.host == "albumsbase.dizzylab.net" || (uri.host in setOf("www.dizzylab.net", "dizzylab.net") && uri.path.orEmpty().startsWith("/albums/download/")))

    private fun queueDizzylabWebPackage(url: String, title: String) {
        checkMobileDataForDownload {
            if (!ensureStreamDownloadFolderReady { queueDizzylabWebPackage(url, title) }) return@checkMobileDataForDownload
            val revision = dizzySessionRevision
            val cookie = dizzylabCookie
            val type = Uri.parse(url).getQueryParameter("tp").orEmpty().takeIf { it in setOf("128", "MP3", "FLAC") }.orEmpty()
            val fileName = safeFileName(listOf(title, type).filter { it.isNotBlank() }.joinToString(" - ") + ".zip")
            enqueueDownload("dizzylab", fileName) { task ->
                check(revision == dizzySessionRevision) { "账号已切换，下载已取消" }
                DizzylabClient(cookie).withDownloadStream(url, DizzylabApiClient.BASE) { input, length, _ ->
                    val stream = java.io.PushbackInputStream(input, 4)
                    val signature = ByteArray(4)
                    var count = 0
                    while (count < signature.size) {
                        val n = stream.read(signature, count, signature.size - count)
                        if (n < 0) break
                        count += n
                    }
                    check(count == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()) { "下载未返回有效的 ZIP 文件包" }
                    stream.unread(signature)
                    task.totalBytes = length.coerceAtLeast(0)
                    writeStreamDownloadData("dizzylab", title, fileName, "application/zip") { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            check(revision == dizzySessionRevision) { "账号已切换，下载已取消" }
                            val n = stream.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            task.downloadedBytes += n
                            if (length > 0) task.progressPercent = (task.downloadedBytes * 100 / length).toInt().coerceAtMost(99)
                        }
                        check(length <= 0 || task.downloadedBytes == length) { "文件包下载不完整" }
                    }
                    task.progressPercent = 100
                }
            }
            Toast.makeText(this, "已加入原下载队列；文件包将保存为 ZIP", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeStreamDownloadBytes(source: String, album: String, fileName: String, mimeType: String, bytes: ByteArray): String =
        writeStreamDownloadData(source, album, fileName, mimeType) { it.write(bytes) }

    /** Common destination/commit path for all providers, including streamed ZIP packages. */
    private fun writeStreamDownloadData(source: String, album: String, fileName: String, mimeType: String, write: (java.io.OutputStream) -> Unit): String {
        var document: Uri? = null
        if (streamDownloadFolderUri.isNotBlank() && hasPersistedWritePermission(streamDownloadFolderUri)) {
            val destination = runCatching {
                val root = Uri.parse(streamDownloadFolderUri)
                val sourceDir = findOrCreateDocumentDir(root, source)
                val albumDir = findOrCreateDocumentDir(root, safeFileName(album), sourceDir)
                val uri = DocumentsContract.createDocument(contentResolver, albumDir, mimeType, fileName) ?: error("无法创建下载文件")
                document = uri
                contentResolver.openOutputStream(uri) ?: error("无法写入下载文件")
            }.getOrNull()
            if (destination != null) {
                try { destination.use(write); return document.toString() }
                catch (error: Exception) { runCatching { DocumentsContract.deleteDocument(contentResolver, document!!) }; throw error }
            }
            document?.let { runCatching { DocumentsContract.deleteDocument(contentResolver, it) } }
        }
        if (streamDownloadFolderUri.isNotBlank()) {
            streamDownloadFolderUri = ""
            runOnUiThread {
                saveSettings()
                Toast.makeText(this, "下载目录无法访问，已使用默认目录", Toast.LENGTH_LONG).show()
            }
        }
        val baseDir = if (hasAllFilesAccess()) File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SMP")
            else getExternalFilesDir(null) ?: filesDir
        val dir = File(baseDir, "StreamDownloads/$source/${safeFileName(album)}").apply { mkdirs() }
        val file = File(dir, fileName)
        val temporary = File(dir, ".${UUID.randomUUID()}.part")
        try {
            temporary.outputStream().use(write)
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return Uri.fromFile(file).toString()
        } finally { temporary.delete() }
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
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingNcmConversionUris = uris
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_NCM_STORAGE)
            return
        }
        setStatus("正在转换 ${uris.size} 个音乐文件...")
        Thread {
            val converter = NcmConverter(this)
            val results = mutableListOf<String>()
            var success = 0
            uris.forEach { uri ->
                runCatching {
                    val source = if (outputDirectoryMode == OutputDirectoryMode.SOURCE) localFileFromUri(uri)?.parentFile?.takeIf {
                        it.canWrite() && !it.path.contains("/Android/data/") && !it.path.startsWith(filesDir.path) && !it.path.startsWith(cacheManager.root.path)
                    } else null
                    val converted = if (source == null) converter.convertTemporary(uri) else converter.convert(uri, source)
                    converted.use { result ->
                        val mimeType = if (result.format == "flac") "audio/flac" else "audio/mpeg"
                        val destination = when {
                            source != null -> Uri.fromFile(result.outputFile)
                            outputDirectoryMode == OutputDirectoryMode.DOWNLOAD -> Uri.parse(writeDownloadOutputData("ConvertedMusic", result.fileName, mimeType) { out -> result.outputFile.inputStream().use { it.copyTo(out) } })
                            else -> ConvertedMediaPublisher.publish(this, result.outputFile, result.title)
                        }
                        val scan = ConvertedMediaPublisher.requestScan(this, destination, mimeType)
                        val saved = scanner.scanDocumentFiles(listOf(destination))
                        if (saved.isNotEmpty()) runOnUiThread {
                            allTracks = (allTracks.filterNot { old -> saved.any { it.uri == old.uri } } + saved).sortedWith(MusicScanner.trackComparator)
                            store.saveTracks(allTracks); renderIfLibraryVisible()
                        }
                        "$destination\n${scan.message}"
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
                val message = "转换完成：$success / ${uris.size}\n输出位置：${if (outputDirectoryMode == OutputDirectoryMode.INTERNAL) "系统音乐目录 Music/SMP/ConvertedMusic" else outputDirectoryMode.label}\n各文件的系统媒体更新状态见下方。"
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
        AppLogger.info("STATUS", text)
        statusText.text = if (page == Page.ONLINE) onlineSourceLabel() else text
        statusText.contentDescription = if (page == Page.ONLINE) "切换音乐来源，${onlineSourceLabel()}" else text
        statusText.isClickable = page == Page.ONLINE
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
        // A stable surface scrim keeps text readable even over a bright/high-contrast user image.
        val overlayAlpha = (244 - (backgroundAlpha.coerceIn(0f, 1f) * if (isDarkModeActive(darkMode)) 24 else 16)).toInt()
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
        return cacheManager.root
    }

    private fun enforceStreamCacheLimit() {
        cacheManager.enforce()
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            titleStyle(24f)
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
            titleStyle(24f)
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
            isSelected = selected
            background = rippleDrawable(this@MainActivity, if (selected) Palette.SELECTED else Color.TRANSPARENT, 24)
            setTextColor(if (selected) Palette.ON_SELECTED else Palette.MUTED)
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
                    view.parent?.requestDisallowInterceptTouchEvent(true)
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
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                MotionEvent.ACTION_CANCEL -> { view.parent?.requestDisallowInterceptTouchEvent(false); true }
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
            background = panelDrawable(Palette.PANEL, 12, this@MainActivity)
            setOnClickListener { onClick() }
            applyCardPressEffect(this)
        }
        row.addView(TextView(this).apply {
            text = title
            titleStyle(17f)
        })
        row.addView(TextView(this).apply {
            text = subtitle
            bodyStyle(14f)
        })
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
        return row
    }

    private fun editText(hintText: String, value: String = "", onChanged: (String) -> Unit): EditText {
        return com.supermite.smp.ui.Md3TextInput(this).apply {
            hint = hintText
            setHintTextColor(Palette.MUTED)
            setTextColor(Palette.TEXT)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
            setText(value)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged(s?.toString().orEmpty())
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
    }

    private fun dialogInput(hintText: String, value: String): EditText {
        return com.supermite.smp.ui.Md3TextInput(this).apply {
            hint = hintText
            setText(value)
            setSingleLine(true)
            setTextColor(Palette.TEXT)
            setHintTextColor(Palette.MUTED)
            textSize = 15f
            background = panelDrawable(Palette.PANEL_ALT, 12, this@MainActivity)
            setPadding(dp(12), 0, dp(12), 0)
        }
    }

    private fun actionButton(text: String, onClick: () -> Unit): Button {
        return materialButton(this, text).apply {
            setOnClickListener { onClick() }
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

    private fun playerControlButton(iconRes: Int, primary: Boolean = false, compact: Boolean = false, onClick: () -> Unit): Button {
        return actionButton("", onClick).apply {
            tag = if (primary) "playerPrimary" else if (compact) "miniPlayerControl" else "playerControl"
            setButtonIcon(this, iconRes)
        }
    }

    private fun setButtonIcon(button: Button, iconRes: Int) {
        if (button is MaterialButton) {
            val playerRole = button.tag?.toString()?.takeIf { it in setOf("playerPrimary", "playerControl", "miniPlayerControl") }
            button.icon = tintedDrawable(iconRes, button.currentTextColor, if (playerRole == "playerPrimary") 36 else if (playerRole == "playerControl") 28 else 24)
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            button.iconPadding = if (button.text.isNullOrBlank()) 0 else dp(8)
            if (!button.text.isNullOrBlank()) {
                button.setPadding(dp(8), dp(8), dp(8), dp(8))
                if (resources.configuration.fontScale > 1.3f) button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_TOP
            }
            if (button.text.isNullOrBlank()) button.setPadding(dp(12), dp(12), dp(12), dp(12))
            if (playerRole == null) button.setEmphasis(iconRes == R.drawable.ic_play_block || iconRes == R.drawable.ic_pause_circle)
            else {
                val isPrimary = playerRole == "playerPrimary"
                button.backgroundTintList = stateColors(if (isPrimary) Palette.ACCENT else Color.TRANSPARENT)
                button.setTextColor(stateColors(if (isPrimary) Palette.ON_ACCENT else Palette.TEXT))
                button.iconTint = button.textColors
                button.iconSize = dp(if (isPrimary) 36 else if (playerRole == "playerControl") 28 else 26)
                button.cornerRadius = dp(if (isPrimary) 36 else 28)
                button.strokeWidth = 0
                button.minimumHeight = dp(if (isPrimary) 72 else if (playerRole == "playerControl") 56 else 48)
                button.minHeight = button.minimumHeight
                button.elevation = 0f
                button.rippleColor = android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(if (isPrimary) Palette.ON_ACCENT else Palette.TEXT, 31))
                button.tag = playerRole
            }
            button.contentDescription = button.text.takeIf { !it.isNullOrBlank() } ?: when (iconRes) {
                R.drawable.ic_play_block -> "播放"
                R.drawable.ic_pause_circle -> "暂停"
                R.drawable.ic_previous_track -> "上一首"
                R.drawable.ic_next_track -> "下一首"
                R.drawable.ic_loop -> "顺序播放"
                R.drawable.ic_singlecycle -> "单曲循环"
                R.drawable.ic_random -> "随机播放"
                R.drawable.ic_menu -> "播放选项"
                else -> "更多操作"
            }
        } else button.setCompoundDrawables(tintedDrawable(iconRes, button.currentTextColor, 24), null, null, null)
    }

    private fun tintedDrawable(iconRes: Int, color: Int, sizeDp: Int): Drawable? {
        return getDrawable(iconRes)?.mutate()?.apply {
            val size = dp(sizeDp)
            setBounds(0, 0, size, size)
            if (iconRes !in setOf(R.drawable.ic_dizzylab, R.drawable.ic_navidrome, R.drawable.ic_dav, R.drawable.ic_lastfm, R.drawable.ic_music_disc)) setTint(color)
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


    private enum class Page {
        SCAN,
        LIBRARY,
        PLAYLISTS,
        NOW_PLAYING,
        ONLINE,
        DIZZYLAB_ACCOUNT,
        SETTINGS,
        STREAMING
    }

    private enum class StreamSource {
        WEBDAV
    }

    private enum class SubsonicScreen {
        HOME, SEARCH, ARTISTS, SONGS, ALBUMS, PLAYLISTS, ARTIST_DETAIL, ALBUM_DETAIL, PLAYLIST_DETAIL
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
        private const val REQUEST_LOG_EXPORT = 1015
        private const val REQUEST_NCM_STORAGE = 1016
        private const val NOTIFICATION_CHANNEL_ID = "playback"
        private const val DOWNLOAD_CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 11
        private const val DOWNLOAD_NOTIFICATION_ID = 21
        private const val LYRICS_NOTIFICATION_ID = 31
        private const val FLOATING_LYRICS_NOTIFICATION_ID = 41
        private const val TAG = "SMP"
        private const val SUBSONIC_PAGE_SIZE = 40
        private const val APP_VERSION = BuildConfig.VERSION_NAME
        private const val REPO_URL = "https://github.com/SuperMite233/Simple-Music-Player"
        private const val ISSUES_URL = "$REPO_URL/issues"
        private const val AUTHOR_URL = "https://space.bilibili.com/287415007"
        private const val RELEASES_URL = "$REPO_URL/releases"
        private const val RELEASES_API = "https://api.github.com/repos/SuperMite233/Simple-Music-Player/releases?per_page=100"
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
private class StrokeTextView(context: android.content.Context) : androidx.appcompat.widget.AppCompatTextView(context) {
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
        val textLayout = layout
        if (textLayout != null) {
            val originalColor = paint.color
            val originalStyle = paint.style
            val available = height - compoundPaddingTop - compoundPaddingBottom - textLayout.height
            val verticalOffset = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.CENTER_VERTICAL -> available / 2f
                Gravity.BOTTOM -> available.toFloat()
                else -> 0f
            }
            val save = canvas.save()
            canvas.translate(compoundPaddingLeft.toFloat(), compoundPaddingTop + verticalOffset)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.BLACK
            textLayout.draw(canvas)
            paint.style = originalStyle
            paint.color = originalColor
            canvas.restoreToCount(save)
        }
        // Drawing must not call setTextColor(): it invalidates the view on every frame.
        super.onDraw(canvas)
    }
}
