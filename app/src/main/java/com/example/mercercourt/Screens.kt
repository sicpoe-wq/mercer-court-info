package com.example.mercercourt

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Generic loader: show cache immediately, refresh from network, report errors. */
class Loader<T>(val key: String, val fetch: suspend (Context) -> String, val parse: (Context, String) -> List<T>,
                val onFresh: (Context, List<T>) -> Unit = { _, _ -> }) {
    var items by mutableStateOf<List<T>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var updated by mutableStateOf<Long?>(null)
    suspend fun loadCache(c: Context) {
        val cached = withContext(Dispatchers.IO) { Repo.cached(c, key) } ?: return
        runCatching { withContext(Dispatchers.Default) { parse(c, cached.first) } }
            .onSuccess { if (items.isEmpty()) { items = it; updated = cached.second } }
    }
    suspend fun refresh(c: Context) {
        loading = true; error = null
        try {
            val raw = withContext(Dispatchers.IO) { fetch(c) }
            val parsed = withContext(Dispatchers.Default) { parse(c, raw) }
            withContext(Dispatchers.IO) { onFresh(c, parsed) }
            items = parsed; Repo.save(c, key, raw); updated = System.currentTimeMillis()
        } catch (e: Exception) {
            error = "Couldn't refresh (${e.javaClass.simpleName}). " + if (items.isNotEmpty()) "Showing last saved data." else ""
        }
        loading = false
    }
}

// ---------------- Jail ----------------

object JailState {
    var state by mutableStateOf(JailTracker.State())
    fun load(c: Context) { state = JailTracker.decode(Repo.settings(c).getString("jail.seen", null)) }
    fun update(c: Context, list: List<Inmate>) {
        val prev = JailTracker.decode(Repo.settings(c).getString("jail.seen", null))
        val next = JailTracker.merge(prev, list, System.currentTimeMillis())
        Repo.settings(c).edit().putString("jail.seen", JailTracker.encode(next)).apply()
        state = next
    }
}

val jailLoader = Loader("jail", { Repo.getText(Urls.JAIL) }, { _, s -> Parsers.jail(s) }, JailState::update)

@Composable
fun NewBadge() {
    Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(4.dp)) {
        Row(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FiberNew, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(2.dp))
            Text("NEW", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun JailTab() {
    val c = LocalContext.current
    var q by remember { mutableStateOf("") }
    var sec by remember { mutableStateOf("All") }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { JailState.load(c) } }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val now = System.currentTimeMillis()
    val st = JailState.state
    val newCount = jailLoader.items.count { JailTracker.isNew(st, it, now) }
    val gone = JailTracker.recentlyGone(st, now)
    Refreshable(jailLoader, header = {
        OutlinedTextField(q, { q = it }, label = { Text("Search name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sec == "New", onClick = { sec = if (sec == "New") "All" else "New" },
                label = { Text("New only ($newCount)") },
                leadingIcon = { Icon(Icons.Default.FiberNew, null, tint = Color(0xFFD32F2F)) })
            listOf("All", "Males", "Females", "Transfers").forEach {
                FilterChip(selected = sec == it, onClick = { sec = it }, label = { Text(it) })
            }
            FilterChip(selected = sec == "Left", onClick = { sec = if (sec == "Left") "All" else "Left" },
                label = { Text("Left roster (${gone.size})") })
        }
    }) { all ->
        if (sec == "Left") {
            Text("${gone.size} dropped off the roster in the last 48 hours (released, transferred, or no longer listed).",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(12.dp, 4.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(gone.filter { it.label.substringBefore('\u001F').contains(q.trim(), true) }) { g ->
                    val p = g.label.split('\u001F')
                    Column(Modifier.fillMaxWidth().padding(12.dp, 6.dp)) {
                        Text(p[0], fontWeight = FontWeight.Bold)
                        Text("Not on roster since ${fmt.format(Date(g.at))}" +
                            (p.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { " · committed $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
            return@Refreshable
        }
        val list = all.filter {
            (sec == "All" || (sec == "New" && JailTracker.isNew(st, it, now)) || it.section == sec) && it.name.contains(q.trim(), true)
        }.sortedByDescending { JailTracker.isNew(st, it, now) }
        Text("${list.size} of ${all.size} shown · $newCount new in last 48h · Mercer County Jail population report",
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(12.dp, 4.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (all.isNotEmpty() && st.firstSeen.values.all { it == 0L }) item {
                Text("Tracking started. Anyone who appears on a later refresh gets a NEW badge for 48 hours.",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
            }
            items(list) { p ->
                val isNew = JailTracker.isNew(st, p, now)
                Row(Modifier.fillMaxWidth().background(if (isNew) Color(0xFFFFEBEE) else Color.Transparent).padding(12.dp, 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) { NewBadge(); Spacer(Modifier.width(8.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text(if (p.section == "Transfers") "Transfer/furlough · ${p.commit} · ${p.admission}"
                             else "Committed ${p.commit}", style = MaterialTheme.typography.bodySmall)
                        if (p.section != "Transfers") Text("${p.admission} · Auth: ${p.authority}", style = MaterialTheme.typography.bodySmall)
                        if (isNew) st.firstSeen[JailTracker.key(p)]?.let {
                            Text("First seen by this app ${fmt.format(Date(it))}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB71C1C))
                        }
                    }
                }
                HorizontalDivider()
            }
            item { TextButton(onClick = {}, enabled = false) { Text("Source: mercercountypa.gov jail report (no charges listed)") } }
        }
    }
}

// ---------------- News ----------------

object NewsState {
    var ok by mutableStateOf<List<String>>(emptyList())
    var failed by mutableStateOf<List<String>>(emptyList())
}

val newsLoader = Loader<Story>("news.v2", { Repo.newsBundle() }, { c, raw ->
    val r = NewsParser.parseBundle(raw, geo = Repo.geo(c))
    NewsState.ok = r.okFeeds; NewsState.failed = r.failedFeeds
    r.stories
})

class LocSettings(c: Context) {
    private val p = Repo.settings(c)
    var zip by mutableStateOf(p.getString("zip", "") ?: "")
    var radius by mutableIntStateOf(p.getInt("radius", 0)) // 0 = any distance
    var showUnplaced by mutableStateOf(p.getBoolean("showUnplaced", false))
    var policeOnly by mutableStateOf(p.getBoolean("policeOnly", true))
    fun save() = p.edit().putString("zip", zip).putInt("radius", radius).putBoolean("showUnplaced", showUnplaced)
        .putBoolean("policeOnly", policeOnly).apply()
}

val RADII = listOf(0, 5, 10, 25, 50, 100)

@Composable
fun NewsTab() {
    val c = LocalContext.current
    val s = remember { LocSettings(c) }
    var geo by remember { mutableStateOf<Geo?>(null) }
    LaunchedEffect(Unit) { geo = withContext(Dispatchers.IO) { Repo.geo(c) } }
    var dialog by remember { mutableStateOf(false) }
    val home = geo?.zip(s.zip)
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Refreshable(newsLoader, header = {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp, 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = s.policeOnly, onClick = { s.policeOnly = true; s.save() }, label = { Text("Police & arrests") },
                leadingIcon = { Icon(Icons.Default.LocalPolice, null) })
            FilterChip(selected = !s.policeOnly, onClick = { s.policeOnly = false; s.save() }, label = { Text("All local news") })
            AssistChip(onClick = { dialog = true }, leadingIcon = { Icon(Icons.Default.LocationOn, null) }, label = {
                Text(when {
                    s.zip.isBlank() -> "Set ZIP"
                    home == null && geo != null -> "${s.zip} (unknown ZIP)"
                    s.radius == 0 -> "${s.zip} · any distance"
                    else -> "${s.zip} · within ${s.radius} mi"
                })
            })
        }
    }) { all ->
        val filtering = home != null && s.radius > 0
        data class Row2(val st: Story, val dist: Double?, val place: Place?)
        val rows = all.filter { !s.policeOnly || it.police }.map { st ->
            if (home == null || st.places.isEmpty()) Row2(st, null, st.places.firstOrNull())
            else {
                val best = st.places.minBy { Geo.miles(home.first, home.second, it.lat, it.lon) }
                Row2(st, Geo.miles(home.first, home.second, best.lat, best.lon), best)
            }
        }.filter { r ->
            if (r.st.places.isEmpty()) s.showUnplaced
            else !filtering || (r.dist ?: 0.0) <= s.radius
        }
        val hiddenUnplaced = all.count { (!s.policeOnly || it.police) && it.places.isEmpty() }
        Text(buildString {
            append("${rows.size} stories")
            if (filtering) append(" within ${s.radius} mi of ${s.zip}")
            if (!s.showUnplaced && hiddenUnplaced > 0) append(" · $hiddenUnplaced with no detected place hidden")
            if (NewsState.ok.isNotEmpty()) append(" · sources: ${NewsState.ok.joinToString(", ")}")
            if (NewsState.failed.isNotEmpty()) append(" · unavailable: ${NewsState.failed.joinToString(", ")}")
        }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (rows.isEmpty()) item {
                Text(if (all.isEmpty()) "No stories loaded yet. Pull down to refresh."
                     else "No stories match. Try a bigger radius, All local news, or showing stories with no detected place.",
                    Modifier.padding(12.dp))
            }
            items(rows, key = { it.st.link }) { r ->
                Column(Modifier.fillMaxWidth().clickable { open(c, r.st.link) }.padding(12.dp, 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.st.police) { Icon(Icons.Default.LocalPolice, "Police/crime", tint = Color(0xFF263B64), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
                        Text(r.st.title, fontWeight = FontWeight.Bold)
                    }
                    val loc = when {
                        r.place == null -> "No place detected"
                        r.dist != null -> "${r.place.name} · ${r.dist.roundToInt()} mi"
                        else -> r.place.name
                    } + if (r.st.places.size > 1) " (+${r.st.places.size - 1} more)" else ""
                    Text(loc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text((if (r.st.time > 0) fmt.format(Date(r.st.time)) + " · " else "") + r.st.source, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }

    if (dialog) LocationDialog(s, geo) { dialog = false }
}

@Composable
fun LocationDialog(s: LocSettings, geo: Geo?, close: () -> Unit) {
    var zip by remember { mutableStateOf(s.zip) }
    var radius by remember { mutableIntStateOf(s.radius) }
    var unplaced by remember { mutableStateOf(s.showUnplaced) }
    val known = zip.length == 5 && geo?.zip(zip) != null
    AlertDialog(onDismissRequest = close, title = { Text("Distance filter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(zip, { zip = it.filter(Char::isDigit).take(5) }, label = { Text("Your ZIP code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(when { zip.isEmpty() -> "Leave blank for no distance"; zip.length < 5 -> "5 digits"; known -> "ZIP found"; geo == null -> "Loading ZIP table…"; else -> "ZIP not in table" }) },
                    isError = zip.length == 5 && geo != null && !known)
                Text("Show stories within", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RADII.forEach { r -> FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text(if (r == 0) "Any" else "$r mi") }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(unplaced, { unplaced = it }); Spacer(Modifier.width(8.dp))
                    Text("Show stories with no detected place", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Distances are straight-line from your ZIP's center to the town named in the story, using an offline Census table. No location permission or internet lookup is used.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { s.zip = zip; s.radius = radius; s.showUnplaced = unplaced; s.save(); close() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
