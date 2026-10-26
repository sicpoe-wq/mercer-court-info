package com.example.mercercourt

/**
 * Custody status derived only from the roster's "Admission Type" column.
 * It never says what offense someone was charged with or convicted of.
 */
enum class Status(val label: String, val color: Long, val explain: String) {
    SENTENCED("Sentenced", 0xFFB71C1C,
        "Convicted and serving a sentence (admission type \"Sentenced by a court of record\" or \"Sentenced by minor judiciary\"). The roster does not show the offense."),
    AWAITING("Awaiting trial", 0xFF1565C0,
        "Held for trial, hearing, or as a witness. Not convicted of the matter they are held on."),
    OTHER_AGENCY("Held for other agency", 0xFF6A1B9A,
        "Held for a federal, state, or police agency (admission type \"Held for various fed/state/police\"), e.g. a parole board (PBPP) or state police detainer."),
    OTHER("Other", 0xFF546E7A,
        "Temporarily at another institution, out on furlough/hospital/transfer, or an admission type this app doesn't recognize.");

    companion object {
        fun of(p: Inmate): Status = of(p.admission, p.section)
        fun of(admission: String, section: String = ""): Status {
            if (section.equals("Transfers", true)) return OTHER
            val a = admission.uppercase()
            return when {
                "SENTENCED" in a -> SENTENCED
                "HELD FOR TRIAL" in a || ("HELD" in a && ("HEARING" in a || "WITNESS" in a)) -> AWAITING
                "FED" in a || "STATE" in a || "POLICE" in a || "PAROLE" in a || "PBPP" in a || "DETAIN" in a -> OTHER_AGENCY
                else -> OTHER
            }
        }
    }
}

/** Committing authority codes seen on the roster that are worth spelling out. */
fun authorityNote(auth: String): String? = when (auth.trim().uppercase()) {
    "PBPP" -> "PA Parole Board"
    "PSP" -> "PA State Police"
    "MCSO" -> "Mercer Co. Sheriff"
    "LOCAL" -> "local police"
    else -> null
}

private val SUFFIXES = setOf("JR", "SR", "II", "III", "IV", "V")

/** Roster names are "LAST FIRST [MIDDLE]" (no comma). Returns (last, first) for the UJS participant search. */
fun splitRosterName(raw: String): Pair<String, String> {
    val s = raw.trim().replace(Regex("\\s+"), " ")
    if (',' in s) {
        val last = s.substringBefore(',').trim(); val rest = s.substringAfter(',').trim().split(' ').filter { it.isNotEmpty() }
        return last to (rest.firstOrNull { it.uppercase().trimEnd('.') !in SUFFIXES } ?: "")
    }
    val t = s.split(' ').filter { it.isNotEmpty() }.toMutableList()
    if (t.isEmpty()) return "" to ""
    val last = t.removeAt(0)
    while (t.size > 1 && t[0].uppercase().trimEnd('.') in SUFFIXES) t.removeAt(0)
    return last to (t.firstOrNull() ?: "")
}

fun ujsQuery(raw: String): String = splitRosterName(raw).let { (l, f) -> if (f.isEmpty()) l else "$l, $f" }
