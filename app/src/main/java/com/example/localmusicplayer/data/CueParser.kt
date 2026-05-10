package com.supermite.smp.data

object CueParser {
    private val fileRegex = Regex("""^\s*FILE\s+"?(.+?)"?\s+\w+\s*$""", RegexOption.IGNORE_CASE)
    private val trackRegex = Regex("""^\s*TRACK\s+(\d+)\s+\w+\s*$""", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("""^\s*TITLE\s+"?(.*?)"?\s*$""", RegexOption.IGNORE_CASE)
    private val performerRegex = Regex("""^\s*PERFORMER\s+"?(.*?)"?\s*$""", RegexOption.IGNORE_CASE)
    private val indexRegex = Regex("""^\s*INDEX\s+01\s+(\d{2,3}):(\d{2}):(\d{2})\s*$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<CueTrack> {
        val result = mutableListOf<CueTrack>()
        var currentFile = ""
        var albumPerformer = ""
        var inTrack = false
        var number = 0
        var title = ""
        var performer = ""
        var startMs: Long? = null

        fun flush() {
            val start = startMs ?: return
            if (number <= 0 || currentFile.isBlank()) return
            result += CueTrack(
                number = number,
                title = title.ifBlank { "Track $number" },
                performer = performer.ifBlank { albumPerformer },
                fileName = currentFile,
                startMs = start
            )
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            fileRegex.matchEntire(line)?.let {
                flush()
                currentFile = it.groupValues[1].trim()
                inTrack = false
                number = 0
                title = ""
                performer = ""
                startMs = null
                return@forEach
            }
            trackRegex.matchEntire(line)?.let {
                flush()
                inTrack = true
                number = it.groupValues[1].toIntOrNull() ?: 0
                title = ""
                performer = ""
                startMs = null
                return@forEach
            }
            titleRegex.matchEntire(line)?.let {
                if (inTrack) title = it.groupValues[1].trim()
                return@forEach
            }
            performerRegex.matchEntire(line)?.let {
                if (inTrack) {
                    performer = it.groupValues[1].trim()
                } else {
                    albumPerformer = it.groupValues[1].trim()
                }
                return@forEach
            }
            indexRegex.matchEntire(line)?.let {
                val minutes = it.groupValues[1].toLongOrNull() ?: 0L
                val seconds = it.groupValues[2].toLongOrNull() ?: 0L
                val frames = it.groupValues[3].toLongOrNull() ?: 0L
                startMs = ((minutes * 60L + seconds) * 1000L) + (frames * 1000L / 75L)
            }
        }

        flush()
        return result.sortedBy { it.startMs }
    }
}

