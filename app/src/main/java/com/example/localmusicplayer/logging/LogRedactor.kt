package com.supermite.smp.logging

/** Sanitizes before a message reaches disk, previews, exports, or legacy import. */
object LogRedactor {
    private const val HIDDEN = "[REDACTED]"
    private val headers = Regex("(?im)\\b(?:set-cookie|cookie|authorization|proxy-authorization)\\s*[:=]\\s*[^\\r\\n]+")
    private val jsonSecrets = Regex("(?i)([\"'](?:password|passwd|pwd|token|access_token|refresh_token|id_token|secret|client_secret|api_key|apikey|sessionid|session_id|csrftoken|csrfmiddlewaretoken)[\"']\\s*:\\s*)([\"'])(?:\\\\.|(?!\\2).)*?\\2")
    private val fields = Regex("(?i)\\b(password|passwd|pwd|token|access_token|refresh_token|id_token|secret|client_secret|api_key|apikey|sessionid|session_id|csrftoken|csrfmiddlewaretoken)\\b\\s*[:=]\\s*[^\\r\\n,;]+")
    private val querySecrets = Regex("(?i)([?&](?:u|p|t|s|password|pwd|token|access_token|refresh_token|auth|authorization|username|sessionid|csrf|csrftoken|key|signature|sig)=)[^&\\s]+")
    private val userInfo = Regex("(?i)(https?://)[^/@\\s]+:[^/@\\s]+@")
    private val urlQueries = Regex("(?i)(https?://[^\\s<>\"?]+)\\?[^\\s<>\"]+")
    private val bearer = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b")

    /** Scan around actual @ characters; an unanchored email regex is quadratic on long log blobs. */
    private fun redactEmails(text: String): String {
        fun local(c: Char) = c.isLetterOrDigit() || c in "._%+-"
        fun domain(c: Char) = c.isLetterOrDigit() || c == '.' || c == '-'
        val output = StringBuilder(text.length)
        var copied = 0
        var cursor = 0
        while (true) {
            val at = text.indexOf('@', cursor)
            if (at < 0) break
            var start = at
            while (start > copied && local(text[start - 1])) start--
            var end = at + 1
            while (end < text.length && domain(text[end])) end++
            var domainEnd = end
            while (domainEnd > at + 1 && text[domainEnd - 1] in ".-") domainEnd--
            var dot = domainEnd - 1
            while (dot > at && text[dot] != '.') dot--
            if (start < at && dot > at + 1 && domainEnd - dot >= 3 && (dot + 1 until domainEnd).all { text[it].isLetter() }) {
                output.append(text, copied, start).append("[EMAIL]")
                copied = domainEnd
            }
            cursor = end
        }
        return output.append(text, copied, text.length).toString()
    }

    fun redact(text: String): String {
        var result = text.replace(userInfo) { "${it.groupValues[1]}$HIDDEN@" }
        result = result.replace(urlQueries) { "${it.groupValues[1]}?[QUERY REDACTED]" }
        result = result.replace(jsonSecrets) { "${it.groupValues[1]}\"$HIDDEN\"" }
        result = result.replace(headers, HIDDEN)
        result = result.replace(querySecrets) { "${it.groupValues[1]}$HIDDEN" }
        result = result.replace(fields) { "${it.groupValues[1]}=$HIDDEN" }
        result = redactEmails(result.replace(bearer, "Bearer $HIDDEN").replace(jwt, HIDDEN))
        return result
    }
}
