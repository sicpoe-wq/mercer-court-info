package com.example.mercercourt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF263B64))) { App() } }
    }
}

fun open(c: Context, url: String) = c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Jail" to Icons.Default.Lock, "Hearings" to Icons.Default.DateRange,
        "Dockets" to Icons.Default.Search, "News" to Icons.Default.Newspaper)
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Mercer County Court Info") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = androidx.compose.ui.graphics.Color.White))
                Disclaimer()
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (n, ic) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, n) }, label = { Text(n) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) { 0 -> JailTab(); 1 -> HearingsTab(); 2 -> DocketTab(); else -> NewsTab() }
        }
    }
}

@Composable
fun Disclaimer() {
    Surface(color = androidx.compose.ui.graphics.Color(0xFFFFF4CE), modifier = Modifier.fillMaxWidth()) {
        Text("A listing or charge is not a conviction. Data comes from public official sources and may be incomplete or out of date. " +
            "This app does not scrape the PA UJS portal.", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Refreshable(l: Loader<T>, header: @Composable () -> Unit = {}, content: @Composable (List<T>) -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { l.loadCache(c); l.refresh(c) }
    PullToRefreshBox(isRefreshing = l.loading, onRefresh = { scope.launch { l.refresh(c) } }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            header()
            val st = buildString {
                l.updated?.let { append("Updated ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}") }
            }
            if (st.isNotEmpty()) Text(st, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
            l.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            content(l.items)
        }
    }
}

val hearingLoader = Loader<Hearing>("hearings", { c -> Repo.pdfText(c, Repo.getBytes(Urls.DAILY)) }, { _, s -> Parsers.hearings(s) })

@Composable
fun HearingsTab() {
    val c = LocalContext.current
    var q by remember { mutableStateOf("") }
    Refreshable(hearingLoader, header = {
        OutlinedTextField(q, { q = it }, label = { Text("Search case or docket") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open(c, Urls.DAILY) }) { Text("Daily PDF") }
            OutlinedButton(onClick = { open(c, Urls.ANNUAL) }) { Text("Annual calendar") }
        }
    }) { all ->
        val list = all.filter { q.isBlank() || it.caption.contains(q, true) || it.docket.contains(q, true) }
        LazyColumn(Modifier.fillMaxSize()) {
            if (all.isEmpty()) item { Text("No hearings loaded. Pull down to refresh, or open the Daily PDF.", Modifier.padding(12.dp)) }
            var last = ""
            items(list) { h ->
                val head = "${h.date} · ${h.courtroom}"
                Column(Modifier.fillMaxWidth()) {
                    if (head != last) { Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(head, Modifier.padding(12.dp, 6.dp), style = MaterialTheme.typography.labelLarge) } }
                    last = head
                    Column(Modifier.padding(12.dp, 6.dp)) {
                        Text("${h.time} · ${h.type}", fontWeight = FontWeight.Bold)
                        if (h.caption.isNotBlank()) Text(h.caption)
                        Text(h.docket, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun DocketTab() {
    val c = LocalContext.current
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Docket & inmate lookups", style = MaterialTheme.typography.titleLarge)
        Text("These open in your browser. The app does not scrape or automate these sites.")
        Button(onClick = { open(c, Urls.UJS) }, modifier = Modifier.fillMaxWidth()) { Text("PA Courts docket search (UJS portal)") }
        Button(onClick = { open(c, Urls.DOC) }, modifier = Modifier.fillMaxWidth()) { Text("PA DOC inmate locator") }
        OutlinedButton(onClick = { open(c, Urls.JAIL_MENU) }, modifier = Modifier.fillMaxWidth()) { Text("Mercer County jail reports (official)") }
        Text("Tip: in the UJS portal search by participant name and choose county “Mercer”.", style = MaterialTheme.typography.bodySmall)
    }
}

