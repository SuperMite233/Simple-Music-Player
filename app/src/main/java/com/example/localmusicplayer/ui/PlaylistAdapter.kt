package com.supermite.smp.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.supermite.smp.R
import com.supermite.smp.data.Playlist
import com.supermite.smp.cache.CacheManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlaylistAdapter(
    private val context: Context,
    private val artworkProvider: (String) -> String?
) : BaseAdapter() {
    private val cache by lazy {CacheManager(context)}
    var artworkLoader: ((String,ImageView)->Unit)? = null
    var playlists: List<Playlist> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun getCount(): Int = playlists.size
    override fun getItem(position: Int): Playlist = playlists[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder: Holder
        val view: LinearLayout
        if (convertView == null) {
            view = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
                background = panelDrawable(Palette.PANEL, 12, context)
                foreground = rippleDrawable(context, android.graphics.Color.TRANSPARENT)
            }
            val thumb = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = panelDrawable(Palette.PANEL_ALT, 12, context)
            }
            view.addView(thumb, LinearLayout.LayoutParams(context.dp(56), context.dp(56)))
            val textBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(12), 0, 0, 0)
            }
            holder = Holder(
                thumb = thumb,
                title = TextView(context).apply { bodyStyle(16f, Palette.TEXT) },
                meta = TextView(context).apply { bodyStyle(14f) }
            )
            textBox.addView(holder.title)
            textBox.addView(holder.meta)
            view.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            view.tag = holder
        } else {
            view = convertView as LinearLayout
            holder = view.tag as Holder
        }

        val playlist = playlists[position]
        holder.title.text = if (playlist.isLocked) "★ ${playlist.name}" else playlist.name
        holder.meta.text = listOf(
            "${playlist.trackIds.distinct().size} 首",
            when (playlist.systemType) {
                "favorites" -> "置顶歌单"
                "history" -> "最近 100 条"
                "album" -> "自动专辑歌单"
                else -> dateFormat.format(Date(playlist.updatedAt))
            }
        ).joinToString(" · ")
        loadArtwork(playlist, holder.thumb)
        return view
    }

    private fun loadArtwork(playlist: Playlist, imageView: ImageView) {
        val firstTrackId = playlist.trackIds.firstOrNull()
        imageView.tag=firstTrackId
        if(firstTrackId!=null)artworkLoader?.let {it(firstTrackId,imageView);return}
        val path = firstTrackId?.let(artworkProvider).orEmpty()
        val bitmap = decodeArtworkPath(path)
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageResource(R.drawable.ic_cover_placeholder)
            imageView.background = panelDrawable(Palette.PANEL_ALT, 12, context)
        }
    }

    private fun decodeArtworkPath(path: String): android.graphics.Bitmap? {
        if (path.isBlank()) return null
        if(path.startsWith("https://") || path.startsWith("http://"))return decodeArtworkPath(cache.artwork.cachedPath(path))
        return runCatching {
            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
            } else {
                cache.resolveLegacyPath(path.removePrefix("file://")).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
        }.getOrNull()
    }

    private data class Holder(val thumb: ImageView, val title: TextView, val meta: TextView)
}

