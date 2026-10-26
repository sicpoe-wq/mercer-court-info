package com.example.mercercourt

/**
 * Remembers which jail roster entries (name + commit date) have been seen before.
 * First run seeds silently (firstSeen = 0 means "already there when tracking started").
 */
object JailTracker {
    const val NEW_WINDOW_MS = 48L * 3600 * 1000
    private const val KEEP_GONE_MS = 60L * 24 * 3600 * 1000

    data class Gone(val key: String, val label: String, val at: Long)
    data class State(
        val seeded: Boolean = false,
        val firstSeen: Map<String, Long> = emptyMap(),
        val lastKeys: Set<String> = emptySet(),
        val gone: Map<String, Gone> = emptyMap(),
    )

    fun key(p: Inmate) = p.name.uppercase().replace(Regex("\\s+"), " ").trim() + "|" + p.commit.trim()
    private fun label(p: Inmate) = "${p.name}\u001F${p.section}\u001F${p.commit}"

    fun merge(s: State, current: List<Inmate>, now: Long): State {
        if (current.isEmpty()) return s // failed/blank page: don't mark everyone as gone/new
        val keys = current.map(::key).toSet()
        if (!s.seeded) return State(true, keys.associateWith { 0L }, keys, emptyMap())
        val first = s.firstSeen.toMutableMap()
        keys.forEach { if (it !in first) first[it] = now }
        val gone = s.gone.toMutableMap()
        keys.forEach { gone.remove(it) }
        val byKey = current.associateBy(::key)
        (s.lastKeys - keys).forEach { k ->
            val lbl = s.gone[k]?.label ?: k.substringBefore('|') + "\u001F\u001F" + k.substringAfter('|')
            gone[k] = Gone(k, lbl, now)
        }
        gone.entries.removeAll { now - it.value.at > KEEP_GONE_MS }
        first.keys.retainAll(keys + gone.keys)
        return State(true, first, keys, gone)
    }

    fun isNew(s: State, p: Inmate, now: Long): Boolean {
        val t = s.firstSeen[key(p)] ?: return false
        return t > 0 && now - t < NEW_WINDOW_MS
    }

    fun recentlyGone(s: State, now: Long) = s.gone.values.filter { now - it.at < NEW_WINDOW_MS }.sortedByDescending { it.at }

    // --- persistence as plain text (one record per line, tab separated) ---
    fun encode(s: State): String = buildString {
        append("v1\t").append(if (s.seeded) 1 else 0).append('\n')
        s.firstSeen.forEach { (k, t) -> append("F\t").append(k).append('\t').append(t).append('\n') }
        s.lastKeys.forEach { append("L\t").append(it).append('\n') }
        s.gone.values.forEach { append("G\t").append(it.key).append('\t').append(it.at).append('\t').append(it.label).append('\n') }
    }

    fun decode(txt: String?): State {
        if (txt.isNullOrBlank()) return State()
        var seeded = false; val f = HashMap<String, Long>(); val l = HashSet<String>(); val g = HashMap<String, Gone>()
        for (line in txt.lines()) {
            val p = line.split('\t')
            when (p[0]) {
                "v1" -> seeded = p.getOrNull(1) == "1"
                "F" -> if (p.size >= 3) f[p[1]] = p[2].toLongOrNull() ?: 0L
                "L" -> if (p.size >= 2) l += p[1]
                "G" -> if (p.size >= 4) g[p[1]] = Gone(p[1], p.drop(3).joinToString("\t"), p[2].toLongOrNull() ?: 0L)
            }
        }
        return State(seeded, f, l, g)
    }
}
