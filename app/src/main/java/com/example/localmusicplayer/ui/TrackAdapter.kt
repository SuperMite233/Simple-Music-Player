package com.supermite.smp.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.supermite.smp.R
import com.supermite.smp.data.Track
import com.supermite.smp.data.TrackEdit
import java.io.File
import java.net.URL
import java.util.concurrent.Executors

class TrackAdapter(
    private val context: Context,
    private val edits: Map<String, TrackEdit>
) : BaseAdapter() {
    private val imageExecutor = Executors.newFixedThreadPool(3)
    var tracks: List<Track> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var selectedIds: Set<String> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var selectionMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var currentTrackId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var onMoreClick: ((Track) -> Unit)? = null
    var onTrackClick: ((Track) -> Unit)? = null
    var onTrackLongClick: ((Track, Int) -> Unit)? = null

    override fun getCount(): Int = tracks.size
    override fun getItem(position: Int): Track = tracks[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder: Holder
        val view: LinearLayout
        if (convertView == null) {
            view = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isLongClickable = true
                setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
                background = panelDrawable(Palette.PANEL, 8, context)
            }
            val thumb = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = panelDrawable(Palette.PANEL_ALT, 8, context)
            }
            view.addView(thumb, LinearLayout.LayoutParams(context.dp(56), context.dp(56)))

            val textBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(12), 0, 0, 0)
            }
            holder = Holder(
                thumb = thumb,
                title = TextView(context).apply {
                    titleStyle(16f)
                    maxLines = 1
                },
                meta = TextView(context).apply {
                    bodyStyle(12.5f)
                    maxLines = 1
                },
                tags = TextView(context).apply {
                    bodyStyle(12f, Palette.ACCENT_ALT)
                    maxLines = 1
                }
            )
            textBox.addView(holder.title)
            textBox.addView(holder.meta)
            textBox.addView(holder.tags)
            view.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val more = ImageButton(context).apply {
                setImageResource(R.drawable.ic_menu)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                isFocusable = false
                isFocusableInTouchMode = false
                setPadding(context.dp(9), context.dp(9), context.dp(9), context.dp(9))
                background = panelDrawable(Palette.PANEL_ALT, 8, context)
            }
            view.addView(more, LinearLayout.LayoutParams(context.dp(42), context.dp(42)))
            view.tag = holder
        } else {
            view = convertView as LinearLayout
            holder = view.tag as Holder
        }

        val track = tracks[position]
        val edit = edits[track.id]
        view.setOnClickListener { onTrackClick?.invoke(track) }
        view.background = panelDrawable(if (track.id in selectedIds) Palette.ACCENT else Palette.PANEL, 8, context)
        val more = view.getChildAt(view.childCount - 1) as ImageButton
        more.visibility = if (selectionMode) View.GONE else View.VISIBLE
        more.setOnClickListener { onMoreClick?.invoke(track) }
        val isCurrent = track.id == currentTrackId
        holder.title.setTextColor(if (isCurrent) Palette.ACCENT else Palette.TEXT)
        holder.title.text = edit?.alias?.takeIf { it.isNotBlank() } ?: track.displayTitle
        view.setOnLongClickListener {
            onTrackLongClick?.invoke(track, position)
            true
        }
        val cue = if (track.isCueTrack) "CUE #${track.cueIndex}" else track.mimeType.substringAfterLast('/').uppercase()
        holder.meta.text = listOf(
            track.displayArtist,
            track.displayAlbum,
            formatDuration(track.durationMs),
            cue,
            if (track.hasLyrics) "歌词" else ""
        ).filter { it.isNotBlank() }.joinToString("  ·  ")
        holder.tags.text = when {
            edit == null -> ""
            edit.tags.isEmpty() && edit.rating <= 0 -> ""
            else -> listOf(
                stars(edit.rating),
                edit.tags.joinToString("  #", prefix = if (edit.tags.isEmpty()) "" else "#")
            ).filter { it.isNotBlank() }.joinToString("   ")
        }
        loadArtwork(track, holder.thumb)
        return view
    }

    private fun loadArtwork(track: Track, imageView: ImageView) {
        if (track.artworkPath.startsWith("http://") || track.artworkPath.startsWith("https://")) {
            imageView.setImageResource(R.drawable.ic_cover_placeholder)
            val expected = track.artworkPath
            imageView.tag = expected
            imageExecutor.execute {
                val bitmap = runCatching {
                    URL(expected).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bitmap != null) {
                    Handler(Looper.getMainLooper()).post {
                        if (imageView.tag == expected) imageView.setImageBitmap(bitmap)
                    }
                }
            }
            return
        }
        val bitmap = decodeArtworkPath(track.artworkPath) ?: siblingArtworkFile(track)?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageResource(R.drawable.ic_cover_placeholder)
            imageView.setBackgroundColor(Palette.PANEL_ALT)
        }
    }

    private fun decodeArtworkPath(path: String): android.graphics.Bitmap? {
        if (path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) return null
        return runCatching {
            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
            } else {
                File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
        }.getOrNull()
    }

    private fun siblingArtworkFile(track: Track): File? {
        val dir = localTrackFile(track)?.parentFile ?: return null
        val images = dir.listFiles { file ->
            file.isFile && file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }?.toList().orEmpty()
        if (images.isEmpty()) return null
        val preferredNames = listOf("cover", "folder", "album", "front")
        return images.minWithOrNull(
            compareBy<File> { file ->
                val name = file.nameWithoutExtension.lowercase()
                preferredNames.indexOfFirst { name.contains(it) }.takeIf { it >= 0 } ?: preferredNames.size
            }.thenBy { it.name.lowercase() }
        )
    }

    private fun localTrackFile(track: Track): File? {
        val candidates = mutableListOf<String>()
        if (track.uri.startsWith("file://")) Uri.parse(track.uri).path?.let { candidates += it }
        if (track.sourcePath.startsWith("file://")) Uri.parse(track.sourcePath).path?.let { candidates += it }
        if (track.sourcePath.startsWith("/") || track.sourcePath.matches(Regex("""^[A-Za-z]:[\\/].*"""))) candidates += track.sourcePath
        return candidates.asSequence().map { File(it) }.firstOrNull { it.exists() }
    }

    private data class Holder(
        val thumb: ImageView,
        val title: TextView,
        val meta: TextView,
        val tags: TextView
    )
}

