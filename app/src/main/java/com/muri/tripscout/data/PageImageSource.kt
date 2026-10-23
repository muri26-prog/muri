package com.muri.tripscout.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reads the preview picture a web page declares for link sharing (og:image / twitter:image).
 * Used to show a real photo for events found by Claude, instead of trusting a model-written image URL.
 */
class PageImageSource(private val http: Http) {

    suspend fun imageFor(pageUrl: String): String? {
        val url = pageUrl.toHttpUrlOrNull() ?: return null
        return extract(http.get(url).take(MAX_HTML_CHARS), pageUrl)
    }

    companion object {
        private const val MAX_HTML_CHARS = 300_000
        private val META = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
        private val KEY = Regex("(?:property|name)\\s*=\\s*[\"'](og:image(?::secure_url)?|og:image:url|twitter:image)[\"']", RegexOption.IGNORE_CASE)
        private val CONTENT = Regex("content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        fun extract(html: String, pageUrl: String): String? {
            val raw = META.findAll(html)
                .filter { KEY.containsMatchIn(it.value) }
                .mapNotNull { CONTENT.find(it.value)?.groupValues?.get(1) }
                .firstOrNull() ?: return null
            // Relative image paths are resolved against the page address; only https images are kept.
            val resolved = pageUrl.toHttpUrl().resolve(raw.replace("&amp;", "&")) ?: return null
            return resolved.toString().takeIf { resolved.isHttps }
        }
    }
}
