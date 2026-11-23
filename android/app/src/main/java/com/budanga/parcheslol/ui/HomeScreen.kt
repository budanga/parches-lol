package com.budanga.parcheslol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.budanga.parcheslol.R
import com.budanga.parcheslol.data.IndexFile
import com.budanga.parcheslol.data.Loaded
import com.budanga.parcheslol.data.NewsItem
import com.budanga.parcheslol.data.PatchFile
import com.budanga.parcheslol.data.PatchSummary
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.data.valueOrNull
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import com.budanga.parcheslol.domain.adcGroups
import com.budanga.parcheslol.domain.shortDate
import com.budanga.parcheslol.domain.updatedText
import java.time.Instant

/** Wraps a home row; the animations task replaces it with the dither reveal. */
typealias RowWrapper = @Composable (patch: String, content: @Composable () -> Unit) -> Unit

@Composable
fun HomeScreen(
    repo: Repository,
    prefs: Prefs,
    onOpenPatch: (String) -> Unit,
    onSettings: () -> Unit,
    title: @Composable () -> Unit = { Text(stringResource(R.string.archive_title), style = Type.serif(20)) },
    loading: @Composable () -> Unit = {},
    wrapRow: RowWrapper = { _, content -> content() },
    onIndex: (List<String>) -> Unit = {},
) {
    var index by remember { mutableStateOf<Loaded<IndexFile>?>(null) }
    var news by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var latest by remember { mutableStateOf<PatchFile?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val myAdcs by prefs.myAdcs.collectAsState(initial = DEFAULT_ADCS)
    val myItems by prefs.myItems.collectAsState(initial = null)
    val uri = LocalUriHandler.current

    LaunchedEffect(reload) {
        // Saved copy first (instant, also offline), then refresh from the network.
        if (index == null) repo.cachedIndex()?.let { cached ->
            index = Loaded.Cached(cached)
            cached.patches.firstOrNull()?.let { latest = repo.cachedPatch(it.patch) }
            news = repo.cachedNews().orEmpty()
            onIndex(cached.patches.map { p -> p.patch })
        }
        val loaded = repo.index()
        if (loaded != Loaded.Offline || index == null) index = loaded
        loaded.valueOrNull?.let { onIndex(it.patches.map { p -> p.patch }) }
        loaded.valueOrNull?.patches?.firstOrNull()?.let { latest = repo.patch(it.patch).valueOrNull }
        news = repo.news().valueOrNull.orEmpty()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { title() }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = Ink.Faint) }
        }
        val data = index?.valueOrNull
        when {
            index == null -> loading()
            data == null -> OfflineMessage { reload++ }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                val first = data.patches.firstOrNull() ?: return@LazyColumn
                item { LatestPatch(first, data, latest, myAdcs, myItems, onOpenPatch, wrapRow) }
                items(data.patches.drop(1), key = { it.patch }) { p ->
                    wrapRow(p.patch) { PatchRow(p) { onOpenPatch(p.patch) } }
                }
                if (news.isNotEmpty()) {
                    item { Text(stringResource(R.string.news), style = Type.serif(16), modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) }
                    items(news.take(3), key = { it.url }) { n ->
                        RowDivider()
                        NewsRow(n) { uri.openUri(n.url) }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LatestPatch(
    summary: PatchSummary,
    index: IndexFile,
    latest: PatchFile?,
    myAdcs: Set<String>,
    myItems: Set<String>?,
    onOpenPatch: (String) -> Unit,
    wrapRow: RowWrapper,
) {
    wrapRow(summary.patch) {
        Column(Modifier.fillMaxWidth().clickable { onOpenPatch(summary.patch) }.padding(top = 14.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.latest_patch), style = Type.Label)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(summary.patch, style = Type.serif(40), modifier = Modifier.weight(1f))
                Text(shortDate(summary.publishedAt), style = Type.Date, modifier = Modifier.padding(bottom = 8.dp))
            }
            val champions = latest?.let { p -> adcGroups(p, myAdcs, myItems).filter { it.entity?.kind == "champion" } }.orEmpty()
            if (champions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    champions.forEach { g -> Avatar(g.entity, g.title, latest!!.ddragonVersion) }
                }
            }
            runCatching { updatedText(Instant.parse(index.updatedAt), Instant.now()) }.getOrNull()?.let {
                Text(it, style = Type.Label, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun PatchRow(summary: PatchSummary, onClick: () -> Unit) {
    Column {
        RowDivider()
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(summary.patch, style = Type.serif(17), modifier = Modifier.weight(1f))
            Text(shortDate(summary.publishedAt), style = Type.Date)
        }
    }
}

@Composable
fun NewsRow(item: NewsItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(item.title, style = Type.Body)
        Text(shortDate(item.publishedAt), style = Type.Date, modifier = Modifier.padding(top = 2.dp))
    }
}
