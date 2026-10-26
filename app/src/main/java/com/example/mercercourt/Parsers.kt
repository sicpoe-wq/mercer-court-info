package com.example.mercercourt

data class Inmate(val name: String, val commit: String, val admission: String, val authority: String, val section: String)
data class Hearing(val date: String, val courtroom: String, val time: String, val type: String, val caption: String, val docket: String)
data class NewsItem(val title: String, val link: String, val date: String, val town: String)

private fun clean(s: String) = s.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace("&nbsp;", " ")
    .replace("&#039;", "'").replace("&#8217;", "’").replace(Regex("\\s+"), " ").trim()

object Parsers {
    fun jail(html: String): List<Inmate> {
        val out = mutableListOf<Inmate>()
        val tables = Regex("(?is)<h4[^>]*>\\s*(MALES|FEMALES|TRANSFERS)\\s*</h4>(.*?)</table>").findAll(html)
        for (t in tables) {
            val section = t.groupValues[1].uppercase().let { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            for (r in Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(t.groupValues[2])) {
                val cells = Regex("(?is)<td[^>]*>(.*?)</td>").findAll(r.groupValues[1]).map { clean(it.groupValues[1]) }.toList()
                    .filter { it.isNotEmpty() }
                if (cells.size < 2) continue
                out += Inmate(cells[0], cells.getOrElse(1) { "" }, cells.getOrElse(2) { "" }, cells.getOrElse(3) { "" }, section)
            }
        }
        return out
    }

    private val timeRe = Regex("^(\\d{1,2}:\\d{2}\\s*[AP]M)\\s+(.*)$")
    private val headerRe = Regex("(Mon|Tues|Wednes|Thurs|Fri|Satur|Sun)day,\\s+\\w+\\s+\\d{1,2},\\s+\\d{4}")
    private val courtRe = Regex("(Courtroom\\s*\\w+)\\s*-\\s*(.*)")
    private val docketRe = Regex("^((CP|MJ|CR|SA|NT)-\\d+-[A-Z]{2}-\\d+-\\d+|NO\\.?\\s*[\\d-]+.*)$")

    fun hearings(text: String): List<Hearing> {
        val out = mutableListOf<Hearing>()
        var date = ""; var court = ""
        var time: String? = null; var type = ""; var cap = mutableListOf<String>(); var docket = ""
        fun flush() {
            if (time != null) out += Hearing(date, court, time!!, type, cap.joinToString(" "), docket)
            time = null; cap = mutableListOf(); docket = ""; type = ""
        }
        for (raw in text.lines()) {
            var line = raw.replace(Regex("\\s{3,}(DA|Defense):.*"), "").replace(Regex("^\\s*(DA|Defense):.*"), "").trim()
            if (line.isEmpty()) continue
            if (line.startsWith("Printed ") || line.startsWith("Case ") || line.startsWith("IN THE COURT")) { if (line.startsWith("Printed")) flush(); continue }
            if (line.startsWith("End of Listing")) { flush(); continue }
            val hm = headerRe.find(line)
            if (hm != null) { date = hm.value; continue }
            if (line.contains("Honorable") && line.contains("Courtroom")) { flush(); court = courtRe.find(line)?.value ?: line; continue }
            val m = timeRe.find(line)
            if (m != null) {
                flush(); time = m.groupValues[1]
                type = m.groupValues[2].split(Regex("\\s{2,}")).first().trim()
                continue
            }
            if (time == null) continue
            if (line == "Attorney's" || line.startsWith("Attorney")) continue
            if (docketRe.matches(line)) docket = line else if (docket.isEmpty()) cap += line
        }
        flush()
        return out
    }

    val towns = listOf("Sharon", "Farrell", "Hermitage", "Sharpsville", "Greenville", "Grove City", "Mercer", "Jamestown",
        "Sandy Lake", "Stoneboro", "Shenango", "Pymatuning", "Hempfield")

    fun news(xml: String): List<NewsItem> {
        val out = mutableListOf<NewsItem>()
        for (m in Regex("(?is)<item>(.*?)</item>").findAll(xml)) {
            val b = m.groupValues[1]
            fun tag(n: String) = Regex("(?is)<$n>(.*?)</$n>").find(b)?.groupValues?.get(1)
                ?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim() ?: ""
            val title = clean(tag("title")); val link = tag("link")
            val cats = Regex("(?is)<category>(.*?)</category>").findAll(b).joinToString(" ") { it.groupValues[1].replace("<![CDATA[", "").replace("]]>", "") }
            val hay = "$title $cats ${clean(tag("description"))}"
            val town = towns.firstOrNull { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(hay) }
                ?: if (Regex("\\bMercer County\\b", RegexOption.IGNORE_CASE).containsMatchIn(hay)) "Mercer County" else null
            if (town != null) out += NewsItem(title, link, tag("pubDate"), town)
        }
        return out
    }
}
