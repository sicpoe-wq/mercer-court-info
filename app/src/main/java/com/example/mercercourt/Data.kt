package com.example.mercercourt

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL

object Urls {
    const val JAIL = "https://www.mercercountypa.gov/jail/jailreport/Alphabetical.cfm"
    const val JAIL_MENU = "https://www.mercercountypa.gov/jail/jailreport/default.htm"
    const val DAILY = "https://www.mercercountypa.gov/Court_Schedule/dailylist.pdf"
    const val ANNUAL = "https://www.mercercountypa.gov/court_schedule/Annual.Court.Calendar.pdf"
    const val UJS = "https://ujsportal.pacourts.us/CaseSearch"
    const val DOC = "https://inmatelocator.cor.pa.gov/"
    const val NEWS = "https://www.wkbn.com/feed/"
}

object Repo {
    private fun conn(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MercerCountyCourtInfo/1.2 (personal use)")
        instanceFollowRedirects = true
    }
    fun getText(url: String): String = conn(url).inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    fun getBytes(url: String): ByteArray = conn(url).inputStream.use { it.readBytes() }

    private fun prefs(c: Context) = c.getSharedPreferences("cache", Context.MODE_PRIVATE)
    fun cached(c: Context, key: String): Pair<String, Long>? =
        prefs(c).getString(key, null)?.let { it to prefs(c).getLong("$key.ts", 0) }
    fun save(c: Context, key: String, v: String) =
        prefs(c).edit().putString(key, v).putLong("$key.ts", System.currentTimeMillis()).apply()

    /** Download every news feed in parallel; failures are recorded, not fatal. */
    suspend fun newsBundle(): String = coroutineScope {
        val res = Feeds.ALL.map { f -> async(Dispatchers.IO) { f to runCatching { getText(f.url).takeIf { "<item" in it } }.getOrNull() } }.awaitAll()
        if (res.all { it.second == null }) throw java.io.IOException("No news feeds reachable")
        NewsParser.bundle(res)
    }

    @Volatile private var geo: Geo? = null
    fun geo(c: Context): Geo = geo ?: synchronized(this) {
        geo ?: Geo(c.assets.open("places.tsv"), c.assets.open("zips.csv")).also { geo = it }
    }

    fun settings(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun pdfText(c: Context, bytes: ByteArray): String {
        PDFBoxResourceLoader.init(c.applicationContext)
        val f = File(c.cacheDir, "daily.pdf").apply { writeBytes(bytes) }
        return PDDocument.load(f).use { PDFTextStripper().apply { sortByPosition = true }.getText(it) }
    }
}
