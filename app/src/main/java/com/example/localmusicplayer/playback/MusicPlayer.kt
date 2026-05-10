package com.supermite.smp.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.PlaybackParams
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.supermite.smp.data.Track

class MusicPlayer(private val context: Context) {
    interface Listener {
        fun onPrepared(track: Track)
        fun onProgress(positionMs: Long, durationMs: Long)
        fun onCompleted()
        fun onError(message: String)
    }

    var listener: Listener? = null
    var headersForTrack: ((Track) -> Map<String, String>)? = null
    var currentTrack: Track? = null
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var speed = 1.0f
    private var volume = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            val track = currentTrack
            if (player != null && track != null) {
                val absolutePosition = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
                val displayPosition = (absolutePosition - track.cueStartMs).coerceAtLeast(0L)
                val displayDuration = displayDuration(track, player)
                listener?.onProgress(displayPosition.coerceAtMost(displayDuration), displayDuration)
                if (track.cueEndMs > track.cueStartMs && absolutePosition >= track.cueEndMs - 250L) {
                    listener?.onCompleted()
                    return
                }
            }
            handler.postDelayed(this, 500L)
        }
    }

    fun play(track: Track, startWhenReady: Boolean = true) {
        stopProgress()
        releasePlayer()
        currentTrack = track

        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            val uri = Uri.parse(track.uri)
            val headers = headersForTrack?.invoke(track).orEmpty()
            if (headers.isEmpty()) {
                player.setDataSource(context, uri)
            } else {
                player.setDataSource(context, uri, headers)
            }
            player.setOnPreparedListener {
                if (track.cueStartMs > 0L) it.seekTo(track.cueStartMs.toInt())
                applySpeed(it)
                it.setVolume(volume, volume)
                if (startWhenReady) it.start()
                listener?.onPrepared(track)
                startProgress()
            }
            player.setOnCompletionListener { listener?.onCompleted() }
            player.setOnErrorListener { _, what, extra ->
                listener?.onError("播放失败：$what/$extra")
                true
            }
            player.prepareAsync()
        }.onFailure {
            listener?.onError(it.message ?: "无法播放该音乐")
        }
    }

    fun toggle() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause() else player.start()
    }

    fun resume() {
        mediaPlayer?.takeIf { !it.isPlaying }?.start()
    }

    fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun seekTo(positionMs: Long) {
        val track = currentTrack ?: return
        val absolute = (track.cueStartMs + positionMs).coerceAtLeast(0L)
        mediaPlayer?.seekTo(absolute.toInt())
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 2.0f)
        mediaPlayer?.let { applySpeed(it) }
    }

    fun speed(): Float = speed

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(volume, volume)
    }

    fun volume(): Float = volume

    fun release() {
        stopProgress()
        releasePlayer()
    }

    private fun applySpeed(player: MediaPlayer) {
        runCatching {
            val wasPlaying = player.isPlaying
            player.playbackParams = (player.playbackParams ?: PlaybackParams()).setSpeed(speed)
            if (!wasPlaying) player.pause()
        }
    }

    private fun startProgress() {
        stopProgress()
        handler.post(progressRunnable)
    }

    private fun stopProgress() {
        handler.removeCallbacks(progressRunnable)
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun displayDuration(track: Track, player: MediaPlayer): Long {
        if (track.cueEndMs > track.cueStartMs) return track.cueEndMs - track.cueStartMs
        if (track.durationMs > 0L) return track.durationMs
        return runCatching { player.duration.toLong() }.getOrDefault(0L).coerceAtLeast(0L)
    }
}

