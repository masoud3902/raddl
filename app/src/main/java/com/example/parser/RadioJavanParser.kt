package com.example.parser

import android.util.Log
import com.example.database.ContentEntity
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

object RadioJavanParser {
    private const val TAG = "RadioJavanParser"

    fun fetchFeaturedSongs(customUrl: String? = null): List<ContentEntity> {
        val list = mutableListOf<ContentEntity>()
        try {
            var url = if (!customUrl.isNullOrBlank()) customUrl else "https://www.radiojavan.com/mp3s/browse/featured"
            
            // Check if the user pasted a single song link
            if (url.contains("radiojavan.com/song/") || url.contains("radiojavan.com/mp3s/mp3/")) {
                val parts = url.substringAfterLast("/").split("-")
                if (parts.size >= 2) {
                    val artist = parts.take(2).joinToString(" ")
                    val title = parts.drop(2).joinToString(" ")
                    val formattedArtist = artist.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") // Simple camel case split
                    val formattedTitle = title.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
                    return listOf(
                        ContentEntity(
                            id = url,
                            title = formattedTitle.ifBlank { "Unknown Title" },
                            artist = formattedArtist.ifBlank { "Unknown Artist" },
                            coverUrl = "https://assets.rjassets.com/static/app/assets/default-local-file-2.jpg",
                            type = "song",
                            rjUrl = url,
                            publishDate = ""
                        )
                    )
                }
            }

            // Rewrite NextJS play.radiojavan.com URLs to the classic SSR ones for scraping
            if (url.contains("play.radiojavan.com")) {
                url = url.replace("play.radiojavan.com/browse/songs", "www.radiojavan.com/mp3s/browse/featured")
                url = url.replace("play.radiojavan.com/playlist/", "www.radiojavan.com/playlists/playlist/")
                url = url.replace("play.radiojavan.com", "www.radiojavan.com")
            }

            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .timeout(8000)
                .get()

            // Find elements that represent a song item (broad selector)
            val items = doc.select(".item, .songInfo, li, a[href*=/mp3s/mp3/], a[href*=/song/], a.media-item")
            
            for (item in items) {
                val anchor = if (item.tagName() == "a") item else item.select("a[href*=/mp3s/mp3/], a[href*=/song/]").first()
                if (anchor == null) continue
                
                val href = anchor.attr("href")
                if (!href.contains("/mp3s/mp3/") && !href.contains("/song/")) continue
                
                val rjUrl = if (href.startsWith("http")) href else "https://www.radiojavan.com$href"
                val id = rjUrl.substringAfterLast("/").substringBefore("?")
                if (id.isBlank() || list.any { it.id == id }) continue

                // Find artist & song title from the item container or the anchor
                val container = if (item.tagName() == "a") item.parent() ?: item else item
                var artist = container.select(".artist, .artistName, .primary-text, .secondary-text, h3, h4, span").firstOrNull { it.hasClass("artist") || it.hasClass("artistName") || it.hasClass("secondary-text") }?.text()?.trim() ?: ""
                var title = container.select(".song, .songName, .title, .primary-text, h2, h3, span").firstOrNull { it.hasClass("song") || it.hasClass("songName") || it.hasClass("title") || it.hasClass("primary-text") }?.text()?.trim() ?: ""
                
                // Fallbacks if classes don't match
                if (artist.isEmpty() || title.isEmpty()) {
                    val decoded = java.net.URLDecoder.decode(id.replace("-", " "), "UTF-8")
                    val parts = decoded.split(" ")
                    if (parts.size >= 2) {
                        artist = parts.take(parts.size / 2).joinToString(" ")
                        title = parts.drop(parts.size / 2).joinToString(" ")
                    } else {
                        artist = "Unknown Artist"
                        title = decoded.ifBlank { "Unknown Title" }
                    }
                }

                // Find cover URL
                val img = container.select("img").first()
                val coverUrl = img?.attr("data-src")?.ifBlank { img.attr("src") } ?: "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400"

                list.add(
                    ContentEntity(
                        id = id,
                        type = "song",
                        title = title.capitalizeWords(),
                        artist = artist.capitalizeWords(),
                        coverUrl = if (coverUrl.startsWith("http")) coverUrl else "https://www.radiojavan.com$coverUrl",
                        rjUrl = rjUrl,
                        publishDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                        searched = false
                    )
                )
            }
            
            // Regex Fallback if standard HTML parsing failed (useful for NextJS SPA states)
            if (list.isEmpty()) {
                val html = doc.html()
                val songRegex = """href="([^"]*/(mp3s/mp3|song)/([^"]+))"""".toRegex()
                val matches = songRegex.findAll(html)
                for (match in matches) {
                    val href = match.groupValues[1]
                    val rjUrl = if (href.startsWith("http")) href else "https://www.radiojavan.com$href"
                    val id = rjUrl.substringAfterLast("/").substringBefore("?")
                    if (id.isBlank() || list.any { it.id == id }) continue
                    
                    val decoded = java.net.URLDecoder.decode(id.replace("-", " "), "UTF-8")
                    var artist = "Unknown"
                    var title = decoded
                    val parts = decoded.split(" ")
                    if (parts.size >= 2) {
                        artist = parts.take(parts.size / 2).joinToString(" ")
                        title = parts.drop(parts.size / 2).joinToString(" ")
                    }
                    
                    list.add(
                        ContentEntity(
                            id = id,
                            type = "song",
                            title = title.capitalizeWords(),
                            artist = artist.capitalizeWords(),
                            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400",
                            rjUrl = rjUrl,
                            publishDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            searched = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping live RJ songs, using seeded fallbacks", e)
        }

        // If scraping failed or returned nothing, return premium seeded data
        if (list.isEmpty() && customUrl.isNullOrBlank()) {
            list.addAll(getSeededSongs())
        }
        return list
    }

    fun fetchFeaturedPodcasts(customUrl: String? = null): List<ContentEntity> {
        val list = mutableListOf<ContentEntity>()
        try {
            var url = if (!customUrl.isNullOrBlank()) customUrl else "https://www.radiojavan.com/podcasts/browse/featured"
            
            // Check if user pasted a single podcast link
            if (url.contains("radiojavan.com/podcast/")) {
                val parts = url.substringAfterLast("/").split("-")
                if (parts.isNotEmpty()) {
                    val formattedTitle = parts.joinToString(" ").replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
                    return listOf(
                        ContentEntity(
                            id = url,
                            type = "podcast",
                            title = formattedTitle.ifBlank { "Unknown Podcast" },
                            artist = "Radio Javan Podcast",
                            coverUrl = "https://assets.rjassets.com/static/app/assets/default-local-file-2.jpg",
                            rjUrl = url,
                            publishDate = ""
                        )
                    )
                }
            }

            if (url.contains("play.radiojavan.com")) {
                url = url.replace("play.radiojavan.com/browse/podcasts", "www.radiojavan.com/podcasts/browse/featured")
                url = url.replace("play.radiojavan.com/podcasts", "www.radiojavan.com/podcasts/browse/featured")
                url = url.replace("play.radiojavan.com", "www.radiojavan.com")
            }

            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .timeout(8000)
                .get()

            val items = doc.select(".item, .songInfo, li, a[href*=/podcasts/podcast/], a[href*=/podcast/], a.media-item")
            for (item in items) {
                val anchor = if (item.tagName() == "a") item else item.select("a[href*=/podcasts/podcast/], a[href*=/podcast/]").first()
                if (anchor == null) continue
                
                val href = anchor.attr("href")
                if (!href.contains("/podcasts/podcast/") && !href.contains("/podcast/")) continue

                val rjUrl = if (href.startsWith("http")) href else "https://www.radiojavan.com$href"
                val id = rjUrl.substringAfterLast("/").substringBefore("?")
                if (id.isBlank() || list.any { it.id == id }) continue

                val container = if (item.tagName() == "a") item.parent() ?: item else item
                var artist = container.select(".host, .artist, .artistName, .secondary-text, h3, h4, span").firstOrNull { it.hasClass("host") || it.hasClass("artist") || it.hasClass("artistName") || it.hasClass("secondary-text") }?.text()?.trim() ?: ""
                var title = container.select(".podcastName, .title, .song, .primary-text, h2, h3, span").firstOrNull { it.hasClass("podcastName") || it.hasClass("title") || it.hasClass("song") || it.hasClass("primary-text") }?.text()?.trim() ?: ""

                if (artist.isEmpty()) artist = "Radio Javan"
                if (title.isEmpty()) {
                    title = java.net.URLDecoder.decode(id.replace("-", " "), "UTF-8")
                }

                val img = container.select("img").first()
                val coverUrl = img?.attr("data-src")?.ifBlank { img.attr("src") } ?: "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=400"

                list.add(
                    ContentEntity(
                        id = id,
                        type = "podcast",
                        title = title.capitalizeWords(),
                        artist = artist.capitalizeWords(),
                        coverUrl = if (coverUrl.startsWith("http")) coverUrl else "https://www.radiojavan.com$coverUrl",
                        rjUrl = rjUrl,
                        publishDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                        searched = false
                    )
                )
            }
            
            if (list.isEmpty()) {
                val html = doc.html()
                val podcastRegex = """href="([^"]*/(podcasts/podcast|podcast)/([^"]+))"""".toRegex()
                val matches = podcastRegex.findAll(html)
                for (match in matches) {
                    val href = match.groupValues[1]
                    val rjUrl = if (href.startsWith("http")) href else "https://www.radiojavan.com$href"
                    val id = rjUrl.substringAfterLast("/").substringBefore("?")
                    if (id.isBlank() || list.any { it.id == id }) continue
                    
                    val decoded = java.net.URLDecoder.decode(id.replace("-", " "), "UTF-8")
                    var artist = "Radio Javan"
                    var title = decoded
                    val parts = decoded.split(" ")
                    if (parts.size >= 2) {
                        artist = parts.take(parts.size / 2).joinToString(" ")
                        title = parts.drop(parts.size / 2).joinToString(" ")
                    }
                    
                    list.add(
                        ContentEntity(
                            id = id,
                            type = "podcast",
                            title = title.capitalizeWords(),
                            artist = artist.capitalizeWords(),
                            coverUrl = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=400",
                            rjUrl = rjUrl,
                            publishDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            searched = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping live RJ podcasts, using seeded fallbacks", e)
        }

        if (list.isEmpty() && customUrl.isNullOrBlank()) {
            list.addAll(getSeededPodcasts())
        }
        return list
    }

    private fun String.capitalizeWords(): String {
        return this.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun getSeededSongs(): List<ContentEntity> {
        return listOf(
            ContentEntity(
                id = "Behzad-Leito-Bezar-Beri-Yadam",
                type = "song",
                title = "Bezar Beri Yadam",
                artist = "Behzad Leito",
                coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400",
                rjUrl = "https://www.radiojavan.com/mp3s/mp3/Behzad-Leito-Bezar-Beri-Yadam",
                publishDate = "2026-06-25",
                searched = false
            ),
            ContentEntity(
                id = "Sohrab-MJ-Yaghi",
                type = "song",
                title = "Yaghi",
                artist = "Sohrab MJ",
                coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400",
                rjUrl = "https://www.radiojavan.com/mp3s/mp3/Sohrab-MJ-Yaghi",
                publishDate = "2026-06-24",
                searched = false
            ),
            ContentEntity(
                id = "Shadmehr-Aghili-Baroon",
                type = "song",
                title = "Baroon",
                artist = "Shadmehr Aghili",
                coverUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=400",
                rjUrl = "https://www.radiojavan.com/mp3s/mp3/Shadmehr-Aghili-Baroon",
                publishDate = "2026-06-23",
                searched = false
            ),
            ContentEntity(
                id = "Yas-Boghz-Yani",
                type = "song",
                title = "Boghz Yani",
                artist = "Yas",
                coverUrl = "https://images.unsplash.com/photo-1487180142328-054b783fc471?w=400",
                rjUrl = "https://www.radiojavan.com/mp3s/mp3/Yas-Boghz-Yani",
                publishDate = "2026-06-20",
                searched = false
            ),
            ContentEntity(
                id = "Homayoun-Shajarian-Zolf-Preeshon",
                type = "song",
                title = "Zolf Preeshon",
                artist = "Homayoun Shajarian",
                coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=400",
                rjUrl = "https://www.radiojavan.com/mp3s/mp3/Homayoun-Shajarian-Zolf-Preeshon",
                publishDate = "2026-06-18",
                searched = false
            )
        )
    }

    private fun getSeededPodcasts(): List<ContentEntity> {
        return listOf(
            ContentEntity(
                id = "Aboon-Episode-45",
                type = "podcast",
                title = "Aboon Episode 45",
                artist = "DJ Aboon",
                coverUrl = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=400",
                rjUrl = "https://www.radiojavan.com/podcasts/podcast/Aboon-Episode-45",
                publishDate = "2026-06-26",
                searched = false
            ),
            ContentEntity(
                id = "Antrakt-Episode-12",
                type = "podcast",
                title = "Antrakt Episode 12",
                artist = "Antrakt Podcast",
                coverUrl = "https://images.unsplash.com/photo-1589903308904-1010c2294adc?w=400",
                rjUrl = "https://www.radiojavan.com/podcasts/podcast/Antrakt-Episode-12",
                publishDate = "2026-06-24",
                searched = false
            ),
            ContentEntity(
                id = "Dubways-Episode-88",
                type = "podcast",
                title = "Dubways Episode 88",
                artist = "DJ Dubways",
                coverUrl = "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?w=400",
                rjUrl = "https://www.radiojavan.com/podcasts/podcast/Dubways-Episode-88",
                publishDate = "2026-06-22",
                searched = false
            )
        )
    }
}
