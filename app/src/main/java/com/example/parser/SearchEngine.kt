package com.example.parser

import android.util.Log
import org.jsoup.Jsoup
import java.net.URL

data class SearchResult(
    val domain: String,
    val pageUrl: String,
    val title: String
)

object SearchEngine {
    private const val TAG = "SearchEngine"

    // List of popular Iranian music sites to recognize and prioritize
    val KNOWN_MUSIC_DOMAINS = listOf(
        "limusic.org",
        "musicdel.ir",
        "upmusics.com",
        "bia2music.com",
        "music-fa.com",
        "nex1music.ir",
        "golsarmusic.ir",
        "pop-music.ir"
    )

    fun searchForSong(artist: String, title: String, limit: Int = 5): List<SearchResult> {
        val query = "$artist $title دانلود"
        val results = mutableListOf<SearchResult>()

        try {
            // DuckDuckGo HTML search URL
            val searchUrl = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .timeout(8000)
                .get()

            // DuckDuckGo search result links are inside '.result__snippet' or '.result__url' or 'a.result__link'
            val links = doc.select("a.result__url, a.result__snippet, a.result__link")
            for (link in links) {
                var url = link.attr("href")
                // DuckDuckGo redirect url cleanup if present
                if (url.contains("/l/?kh=")) {
                    url = url.substringAfter("/l/?kh=").substringBefore("&")
                    url = java.net.URLDecoder.decode(url, "UTF-8")
                }
                if (!url.startsWith("http")) continue

                try {
                    val uri = URL(url)
                    val host = uri.host.lowercase().replace("www.", "")
                    
                    // Add if it is a known music domain or contains 'music' in host
                    if (KNOWN_MUSIC_DOMAINS.contains(host) || host.contains("music") || host.contains("ahang")) {
                        if (results.none { it.pageUrl == url }) {
                            val displayTitle = link.text().trim().ifBlank { "${host.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }} - Download Page" }
                            results.add(SearchResult(domain = host, pageUrl = url, title = displayTitle))
                        }
                    }
                } catch (e: Exception) {
                    // Ignore malformed URL
                }
                if (results.size >= limit) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search scraping failed, using fallback domains", e)
        }

        // Fill up remaining slots with fallbacks if we haven't reached the limit
        if (results.size < limit) {
            val formattedName = "$artist - $title".replace(" ", "-")
            val fallbackDomains = KNOWN_MUSIC_DOMAINS + listOf("nex1music.ir", "golsarmusic.ir", "pop-music.ir", "tabamusic.com", "rozmusic.com")
            
            for (domain in fallbackDomains.distinct()) {
                if (results.size >= limit) break
                if (results.none { it.domain == domain }) {
                    results.add(
                        SearchResult(
                            domain = domain,
                            pageUrl = "https://$domain/$formattedName",
                            title = "دانلود آهنگ $title از $artist در $domain"
                        )
                    )
                }
            }
        }

        return results
    }
}
