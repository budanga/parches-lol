package com.budanga.parcheslol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.budanga.parcheslol.R
import com.budanga.parcheslol.data.Catalog
import com.budanga.parcheslol.data.ChampionHistory
import com.budanga.parcheslol.data.Entity
import com.budanga.parcheslol.data.Loaded
import com.budanga.parcheslol.data.NewsItem
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.data.valueOrNull
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import com.budanga.parcheslol.domain.normalizeForSearch
import com.budanga.parcheslol.domain.shortDate
import com.budanga.parcheslol.domain.splitModes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateMapOf

/** Search field: input fill, 1 dp border that turns terracotta on focus. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Type.Body,
        cursorBrush = SolidColor(Ink.Accent),
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().background(Ink.Input, shape)
            .border(1.dp, if (focused) Ink.Accent else Ink.Line, shape).padding(horizontal = 12.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box { if (value.isEmpty()) Text(placeholder, style = Type.Body.copy(color = Ink.Faint)); inner() }
        },
    )
}

@Composable
fun ChampionsScreen(repo: Repository, prefs: Prefs, onOpen: (String) -> Unit) {
    var catalog by remember { mutableStateOf<Loaded<Catalog>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { catalog = repo.catalog() }
    val myAdcs by prefs.myAdcs.collectAsState(initial = DEFAULT_ADCS)
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.tab_champions), style = Type.serif(20), modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
        SearchField(query, { query = it }, stringResource(R.string.search_champion))
        val data = catalog?.valueOrNull
        when {
            catalog == null -> Unit
            data == null -> OfflineMessage { reload++ }
            else -> {
                val q = normalizeForSearch(query)
                val shown = data.champions.filter { normalizeForSearch(it.name).contains(q) }
                    .sortedBy { if (it.id in myAdcs) 0 else 1 }
                LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(shown, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Avatar(Entity("champion", c.id, c.name), c.name, data.ddragonVersion, 28.dp)
                            Text(c.name, style = Type.Body)
                        }
                        RowDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun ChampionScreen(id: String, repo: Repository, onBack: () -> Unit) {
    var history by remember { mutableStateOf<Loaded<ChampionHistory>?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, reload) {
        version = repo.catalog().valueOrNull?.ddragonVersion
        history = repo.champion(id)
    }
    val data = history?.valueOrNull
    val name = data?.name ?: id
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        DetailBar(name, null, onBack) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Avatar(Entity("champion", id, name), name, version ?: "", 34.dp)
                Text(name, style = Type.serif(24))
            }
        }
        when {
            history == null -> Unit
            history == Loaded.Missing -> Text(stringResource(R.string.no_history), style = Type.BodyMuted, modifier = Modifier.padding(16.dp))
            data == null -> OfflineMessage { reload++ }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(data.entries, key = { it.patch }) { entry ->
                    Column {
                        EntityHeader(null, entry.patch, version ?: "") { Text(shortDate(entry.publishedAt), style = Type.Date) }
                        val view = splitModes(entry)
                        view.rift.forEachIndexed { i, change ->
                            if (i > 0) RowDivider()
                            ChangeLine(change)
                        }
                        if (view.modes.isNotEmpty()) {
                            val open = expanded[entry.patch] == true
                            Row(
                                Modifier.fillMaxWidth().clickable { expanded[entry.patch] = !open }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.other_modes), style = Type.Label, modifier = Modifier.weight(1f))
                                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Ink.Faint)
                            }
                            if (open) view.modes.groupBy { it.sectionTitle }.forEach { (mode, changes) ->
                                Text(mode, style = Type.serif(15), modifier = Modifier.padding(top = 6.dp))
                                changes.forEach { ChangeLine(it) }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun NewsScreen(repo: Repository) {
    var news by remember { mutableStateOf<Loaded<List<NewsItem>>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { news = repo.news() }
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.news), style = Type.serif(20), modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        val data = news?.valueOrNull
        when {
            news == null -> Unit
            data == null -> OfflineMessage { reload++ }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(data, key = { it.url }) { n ->
                    Column(Modifier.fillMaxWidth().clickable { uri.openUri(n.url) }.padding(vertical = 11.dp)) {
                        Text(n.title, style = Type.Attribute)
                        Text(shortDate(n.publishedAt), style = Type.Date, modifier = Modifier.padding(vertical = 2.dp))
                        n.description?.let { Text(it, style = Type.BodyMuted) }
                    }
                    RowDivider()
                }
            }
        }
    }
}
