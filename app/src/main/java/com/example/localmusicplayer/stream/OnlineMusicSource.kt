package com.supermite.smp.stream

/** Source identity is independent of the screen and of configured server accounts. */
enum class OnlineMusicSource(val key: String, val title: String) {
    SUBSONIC("subsonic", "Subsonic"),
    DIZZYLAB("dizzylab", "Dizzylab");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SUBSONIC
    }
}
