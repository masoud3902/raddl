package com.example
import org.junit.Test
import java.net.URL
import org.jsoup.Jsoup

class FetchTest {
    @Test
    fun testFetch() {
        var out = ""
        try {
            val res = URL("https://api.rss2json.com/v1/api.json?rss_url=https%3A%2F%2Fwww.radiojavan.com%2Fmp3s%2Frss").readText()
            out += "RSS2JSON OK!\n" + res.take(500)
        } catch(e: Exception) {
            out += "RSS2JSON ERROR: " + e.message + "\n"
        }
        java.io.File("rj_test_output.txt").writeText(out)
    }
}
