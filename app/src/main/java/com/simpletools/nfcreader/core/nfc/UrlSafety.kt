package com.simpletools.nfcreader.core.nfc

import java.net.URI

enum class UrlWarning { NOT_HTTPS, IP_HOST, PUNYCODE, USER_INFO, NON_STANDARD_PORT, VERY_LONG }

data class UrlInfo(
    val raw: String,
    val scheme: String,
    val host: String,
    val warnings: List<UrlWarning>,
) {
    val isHttps: Boolean get() = scheme == "https"
}

/**
 * Analyse prudente d'une URL lue sur une puce (entrée non fiable). Seuls http/https sont
 * « ouvrables » et uniquement après confirmation explicite de l'utilisateur.
 */
object UrlSafety {
    fun parse(raw: String): UrlInfo? {
        val url = raw.trim()
        if (url.isEmpty() || url.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = try { URI(url) } catch (e: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val warnings = buildList {
            if (scheme != "https") add(UrlWarning.NOT_HTTPS)
            if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) || host.contains(':')) add(UrlWarning.IP_HOST)
            if (host.split('.').any { it.startsWith("xn--") }) add(UrlWarning.PUNYCODE)
            if (uri.userInfo != null) add(UrlWarning.USER_INFO)
            if (uri.port != -1 && uri.port != 80 && uri.port != 443) add(UrlWarning.NON_STANDARD_PORT)
            if (url.length > 300) add(UrlWarning.VERY_LONG)
        }
        return UrlInfo(url, scheme, host, warnings)
    }

    private val urlInText = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    fun findUrlsInText(text: String): List<String> = urlInText.findAll(text).map { it.value.trimEnd('.', ',', ';', ')') }.toList()
}
