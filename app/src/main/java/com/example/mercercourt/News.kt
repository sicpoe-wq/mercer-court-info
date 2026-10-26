package com.example.mercercourt

import java.text.SimpleDateFormat
import java.util.Locale

/** defaultPlace: used when a story names no place (e.g. hometown papers whose headlines omit the town). */
data class Feed(val name: String, val url: String, val aggregator: Boolean = false, val defaultPlace: String? = null)

data class Story(
    val title: String, val link: String, val time: Long, val source: String,
    val text: String, val police: Boolean, val defaultPlace: String? = null, var places: List<Place> = emptyList(),
)

object Feeds {
    private const val NO_SOCIAL = "+-site:facebook.com+-site:instagram.com+-site:x.com+-site:youtube.com+-site:tiktok.com"
    private const val G = "https://news.google.com/rss/search?hl=en-US&gl=US&ceid=US:en&q="
    /** Each feed was verified to load (HTTP 200 with items) from a test machine on Oct 26, 2026. */
    val ALL = listOf(
        Feed("WKBN", "https://www.wkbn.com/feed/"),
        Feed("WKBN", "https://www.wkbn.com/news/local-news/feed/"),
        Feed("WYTV", "https://www.wytv.com/feed/"),
        Feed("WFMJ", "https://www.wfmj.com/search/?f=rss"),
        Feed("The Vindicator", "https://www.vindy.com/news/local-news/feed/"),
        Feed("Tribune Chronicle", "https://www.tribtoday.com/news/local-news/feed/"),
        Feed("YourErie", "https://www.yourerie.com/news/local-news/feed/"),
        Feed("YourErie", "https://www.yourerie.com/news/crime/feed/"),
        Feed("ButlerRadio.com", "https://www.butlerradio.com/feed/"),
        Feed("Sharon Herald", G + "site:sharonherald.com+(police+OR+arrested+OR+charged+OR+court+OR+sentenced+OR+trooper+OR+jail)+when:30d" + NO_SOCIAL, true, "Shenango Valley"),
        Feed("Allied News", G + "site:alliednews.com+(police+OR+arrested+OR+charged+OR+court+OR+sentenced+OR+trooper+OR+jail)+when:30d" + NO_SOCIAL, true, "Grove City"),
        Feed("Google News", G + "%22Mercer+County%22+Pennsylvania+(police+OR+arrested+OR+charged+OR+sentenced)+when:30d" +
            "+-site:patch.com+-site:nj.com+-site:lehighvalleylive.com+-site:fox29.com+-site:trentonian.com" + NO_SOCIAL, true),
    )
}

object NewsParser {
    private val policeRe = Regex(
        "\\b(arrest(s|ed|ing)?|charg(e|ed|es|ing)|police|policing|troopers?|state police|PSP|sheriff'?s?|deput(y|ies)|" +
        "DUI|OVI|warrants?|jail(ed)?|court|sentenc(ed|ing|e)|guilty|plead(s|ed)?|pleas?|indict(ed|ment)|arraign(ed|ment)|" +
        "homicide|murder(ed)?|manslaughter|shooting|stabb(ed|ing)|robbery|robbed|burglar(y|ies)|theft|stol(e|en)|" +
        "assault(ed)?|drugs?|fentanyl|meth|cocaine|heroin|crimes?|criminal|custody|suspects?|prison|probation|parole|" +
        "district attorney|prosecutors?|felony|misdemeanor|chase|pursuit|fugitive|wanted|inmates?|investigat(e|ed|ion|ors?)|" +
        "kidnap(ped|ping)?|carjack(ed|ing)?|gun(fire|man|point)?|firearms?|weapons?|abuse|trafficking|accused|bail|" +
        "detectives?|SWAT|standoff|search warrant|seized|lockdown)\\b",
        RegexOption.IGNORE_CASE)

    private val SOCIAL = Regex("(?i)facebook|instagram|twitter|\\bX\\b|youtube|tiktok|linkedin|reddit|nextdoor")
    fun isPolice(s: String) = policeRe.containsMatchIn(s)

    private fun clean(s: String) = s.replace("<![CDATA[", "").replace("]]>", "")
        .replace(Regex("<[^>]+>"), " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'")
        .replace("&#8217;", "’").replace("&#8216;", "‘").replace("&#8220;", "“").replace("&#8221;", "”")
        .replace("&#8211;", "–").replace("&#8212;", "—").replace("&#8230;", "…").replace("&#038;", "&")
        .replace(Regex("&#(\\d+);")) { runCatching { String(Character.toChars(it.groupValues[1].toInt())) }.getOrDefault(" ") }
        .replace(Regex("\\s+"), " ").trim()

    private val fmts = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss z")
    fun parseDate(s: String): Long {
        for (f in fmts) runCatching { return SimpleDateFormat(f, Locale.US).parse(s.trim())!!.time }
        return 0L
    }

    /** Parse one RSS document. */
    fun parse(xml: String, feed: Feed): List<Story> {
        val out = mutableListOf<Story>()
        for (m in Regex("(?is)<item>(.*?)</item>").findAll(xml)) {
            val b = m.groupValues[1]
            fun tag(n: String) = Regex("(?is)<$n(?:\\s[^>]*)?>(.*?)</$n>").find(b)?.groupValues?.get(1) ?: ""
            var title = clean(tag("title"))
            val link = clean(tag("link"))
            if (title.isBlank() || link.isBlank()) continue
            var source = feed.name
            var text: String
            if (feed.aggregator) {
                val src = clean(tag("source"))
                if (SOCIAL.containsMatchIn(src)) continue // never surface social-media posts
                if (src.isNotBlank()) { source = "$src (via Google News)"; title = title.removeSuffix(" - $src").trim() }
                text = title
            } else {
                val cats = Regex("(?is)<category>(.*?)</category>").findAll(b).joinToString(" ") { clean(it.groupValues[1]) }
                text = "$title ${clean(tag("description"))} $cats"
            }
            if (title.endsWith(".jpg", true) || title.endsWith(".png", true)) continue // photo assets in WFMJ search feed
            out += Story(title, link, parseDate(clean(tag("pubDate"))), source, text, isPolice("$title ${if (feed.aggregator) "" else text}"), feed.defaultPlace)
        }
        return out
    }

    fun key(title: String) = title.lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim().take(70)

    /** Combined cache format: sections separated by marker lines. */
    const val MARK = "\n<!--MCI-FEED "
    fun bundle(results: List<Pair<Feed, String?>>): String = buildString {
        results.forEachIndexed { i, (f, body) ->
            append(MARK).append(i).append(if (body == null) " FAILED" else " OK").append("-->\n")
            if (body != null) append(body)
        }
    }

    data class Result(val stories: List<Story>, val okFeeds: List<String>, val failedFeeds: List<String>)

    fun parseBundle(raw: String, feeds: List<Feed> = Feeds.ALL, geo: Geo? = null): Result {
        val all = mutableListOf<Story>(); val ok = linkedSetOf<String>(); val bad = linkedSetOf<String>()
        val parts = Regex("<!--MCI-FEED (\\d+) (OK|FAILED)-->").let { re ->
            val ms = re.findAll(raw).toList()
            ms.mapIndexed { i, m -> Triple(m.groupValues[1].toInt(), m.groupValues[2], raw.substring(m.range.last + 1, if (i + 1 < ms.size) ms[i + 1].range.first else raw.length)) }
        }
        for ((idx, st, body) in parts) {
            val f = feeds.getOrNull(idx) ?: continue
            if (st == "FAILED") { bad += f.name; continue }
            val items = runCatching { parse(body, f) }.getOrDefault(emptyList())
            ok += f.name; all += items
        }
        return Result(dedupe(all, geo), ok.toList(), (bad - ok).toList())
    }

    /** Remove duplicates across feeds (same link or same normalized headline); prefer direct-source items over Google News. */
    fun dedupe(items: List<Story>, geo: Geo? = null): List<Story> {
        val byKey = LinkedHashMap<String, Story>(); val links = HashSet<String>()
        for (s in items.sortedBy { if (it.source.contains("Google News")) 1 else 0 }) {
            val k = key(s.title)
            if (k in byKey || s.link in links) {
                val prev = byKey[k]
                if (prev != null && s.police && !prev.police) byKey[k] = prev.copy(police = true)
                continue
            }
            byKey[k] = s; links += s.link
        }
        val out = byKey.values.sortedByDescending { it.time }
        if (geo != null) out.forEach { st ->
            st.places = geo.detect(st.text).ifEmpty { listOfNotNull(st.defaultPlace?.let { geo.places[it] }) }
        }
        return out
    }
}
