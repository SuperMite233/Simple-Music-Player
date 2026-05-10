package com.supermite.smp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

object PlaybackControlBus {
    var handler: ((String) -> Unit)? = null
}

class PlaybackActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
            if (event.action != KeyEvent.ACTION_UP) return
            val action = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> "com.supermite.smp.PLAY_PAUSE"
                KeyEvent.KEYCODE_MEDIA_NEXT -> "com.supermite.smp.NEXT"
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "com.supermite.smp.PREV"
                KeyEvent.KEYCODE_MEDIA_REWIND -> "com.supermite.smp.REWIND"
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "com.supermite.smp.FORWARD"
                else -> return
            }
            PlaybackControlBus.handler?.invoke(action)
            return
        }
        intent.action?.let { PlaybackControlBus.handler?.invoke(it) }
    }
}

