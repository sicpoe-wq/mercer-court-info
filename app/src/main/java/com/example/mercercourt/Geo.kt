package com.example.mercercourt

import java.io.InputStream
import kotlin.math.*

data class Place(val name: String, val lat: Double, val lon: Double, val state: String, val kind: String)

/** Offline geography: US ZIP (ZCTA) centroids + places/townships/counties around Mercer County (Census 2023 Gazetteer). */
class Geo(placesTsv: InputStream, zipsCsv: InputStream) {
    val zips: Map<String, Pair<Double, Double>>
    val places: Map<String, Place>
    /** Distinctive local township names that stories often use without the word "Township". */
    val aliases: Map<String, Place>
    private val matcher: Regex
    private val upper: Map<String, Place>

    init {
        zips = HashMap<String, Pair<Double, Double>>(40000).also { m ->
            zipsCsv.bufferedReader().forEachLine { l ->
                val p = l.split(','); if (p.size == 3) m[p[0]] = p[1].toDouble() to p[2].toDouble()
            }
        }
        val pl = LinkedHashMap<String, Place>()
        placesTsv.bufferedReader().forEachLine { l ->
            val p = l.split('\t')
            if (p.size >= 5 && p[0] !in STOP) pl.putIfAbsent(p[0], Place(p[0], p[1].toDouble(), p[2].toDouble(), p[3], p[4]))
        }
        EXTRA.forEach { pl.putIfAbsent(it.name, it) }
        places = pl
        aliases = BARE_TWP.mapNotNull { n -> pl["$n Township"]?.let { n to it } }.toMap()
        // Longest names first so "Mercer County" / "Grove City" win over "Mercer" / "Grove".
        val names = (pl.keys + aliases.keys).flatMap { listOf(it, it.uppercase()) }.distinct().sortedByDescending { it.length }
        upper = (pl.values + aliases.map { (k, v) -> v.copy(name = k) }).associateBy { it.name.uppercase() }
            .mapValues { (_, v) -> pl[v.name] ?: aliases[v.name] ?: v }
        matcher = Regex("(?<![A-Za-z])(" + names.joinToString("|") { Regex.escape(it) } + ")(?![A-Za-z])")
    }

    fun zip(z: String): Pair<Double, Double>? = zips[z.trim().take(5)]

    /** Places mentioned in the text, in order of first appearance. */
    fun detect(text: String): List<Place> {
        val out = LinkedHashMap<String, Place>()
        val t = text.replace(Regex("\\b([A-Z][a-z]+) Co\\.(?=\\s)"), "$1 County").replace(Regex("\\b([A-Z][a-z]+) Twp\\.?"), "$1 Township")
        for (m in matcher.findAll(t)) {
            val hit = m.value
            val p = places[hit] ?: aliases[hit] ?: upper[hit] ?: continue
            out.putIfAbsent(p.name, p)
        }
        return out.values.toList()
    }

    companion object {
        fun miles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 3958.8
            val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }

        val EXTRA = listOf(
            Place("Shenango Valley", 41.2300, -80.4700, "PA", "region"),
            Place("Mahoning Valley", 41.1000, -80.6500, "OH", "region"),
        )

        private val AMBIGUOUS = setOf("Worth", "Perry", "Delaware", "Salem", "Jefferson", "Liberty", "Hubbard", "Vernon", "Greene", "Pine",
            "Lake", "Springfield", "Jackson", "Wilmington", "Fairview", "Wayne", "Hickory", "Findlay", "Wheatland", "Pulaski", "Mahoning",
            "Taylor", "Union", "Scott", "Sugar Creek")

        val BARE_TWP = listOf("Boardman", "Howland", "Bazetta", "Braceville", "Champion", "Coitsville", "Coolspring", "Cussewago",
            "Hempfield", "Kinsman", "Lackawannock", "East Lackawannock", "Neshannock", "Pymatuning", "South Pymatuning", "Shenango",
            "West Shenango", "North Shenango", "South Shenango", "Scrubgrass", "Sandy Creek", "Sandycreek", "Wolf Creek", "Findley",
            "Weathersfield", "Williamsfield", "Southington", "Mecca", "Mesopotamia", "Gustavus", "Fowler", "Brookfield",
            "Plain Grove", "Deer Creek", "French Creek", "Otter Creek", "Hartsgrove", "Cherrytree", "Mill Creek", "Little Beaver",
            "North Beaver", "South Beaver", "Slippery Rock", "Worth", "Perry", "Delaware", "Salem", "Jefferson", "Liberty",
            "Hubbard", "Vernon", "Greene", "Pine", "Lake", "Springfield", "Jackson", "Wilmington", "Fairview", "Sugar Grove",
            "Sugar Creek", "Wayne", "Hickory", "Findlay", "Wheatland", "Pulaski", "Mahoning", "Taylor", "Union", "Scott").filter { it !in AMBIGUOUS }
        /** Place names that are also common words, people, or far-away famous places. */
        val STOP = setOf(
            "Columbus", "Washington", "California", "Indiana", "Oklahoma", "Boston", "Paris", "Berlin", "Lincoln", "Russell",
            "Kennedy", "Jefferson", "Madison", "Clinton", "Houston", "Republic", "Independence", "Liberty", "Economy", "Industry",
            "Force", "Crown", "Distant", "Brave", "Green", "Gray", "Orange", "Penn", "Plum", "Wall", "Sandy", "Dale", "Mars",
            "Coral", "Seneca", "Sterling", "Prospect", "Imperial", "Rogers", "Robinson", "Wilson", "Perry", "Spencer", "Sherman",
            "Webster", "Chester", "Bethlehem", "Harrisburg", "Dayton", "Brooklyn", "Georgetown", "Richmond", "Nashville",
            "Fresno", "Burbank", "Glendale", "Bridgeport", "Birmingham", "Glasgow", "Versailles", "Panama", "Toronto", "Utica",
            "Cameron", "Sullivan", "Lawrence", "McDonald", "Clark", "Allison", "Elizabeth", "Oliver", "Kent", "Wilcox",
            "Atlantic", "Congress", "Laboratory", "Hickory", "Avalon", "Empire", "Delmont", "Polk", "Knox", "Hudson",
            "Blaine", "Ernest", "Naomi", "Hyde", "Jerome", "Plainfield", "Bolton", "Salix", "Dover", "Medina", "Lodi",
            "Smock", "Muse", "Roots", "Paint", "Gray", "Commodore", "Twilight", "Midway", "Riverside", "Homestead", "Avon",
            "Manor", "Hastings", "Randolph", "Sheffield", "Bedford", "Freedom", "Harmony", "Mammoth", "Magnolia", "Rew",
            "Conway", "Collins", "Westland", "Wellington", "Jacksonville", "Elgin", "Aurora", "Portage", "Calumet", "Grafton",
            "Bainbridge", "Edgewood", "Lakewood", "Fairview", "Clearview", "Hilltop", "Brookside", "Westwood", "Oakland",
            "Elim", "Strabane", "Jerusalem", "Damascus", "Cleveland Heights", "S.N.P.J.",
        )
    }
}
