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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.budanga.parcheslol.R
import com.budanga.parcheslol.data.Catalog
import com.budanga.parcheslol.data.CatalogEntry
import com.budanga.parcheslol.domain.isDefaultAdcItem
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
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, autoFocus: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Opened by the search button: the cursor is already in the field, keyboard up.
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Type.Body,
        cursorBrush = SolidColor(Ink.Accent),
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).background(Ink.Input, shape)
            .border(1.dp, if (focused) Ink.Accent else Ink.Line, shape).padding(horizontal = 12.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box { if (value.isEmpty()) Text(placeholder, style = Type.Body.copy(color = Ink.Faint)); inner() }
        },
    )
}

@Composable
/** Champions and items, each opening its change history. */
fun ChampionsScreen(repo: Repository, prefs: Prefs, onOpen: (String) -> Unit, onOpenItem: (String) -> Unit = {}) {
    var catalog by remember { mutableStateOf<Loaded<Catalog>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        // Saved catalog first, then refresh: the list shows at once.
        if (catalog == null) repo.cachedCatalog()?.let { catalog = Loaded.Cached(it) }
        val fresh = repo.catalog()
        if (fresh != Loaded.Offline || catalog == null) catalog = fresh
    }
    val myAdcs by prefs.myAdcs.collectAsState(initial = DEFAULT_ADCS)
    var query by rememberSaveable { mutableStateOf("") }
    var showItems by rememberSaveable { mutableStateOf(false) }
    // Campeones and Objetos each keep their own place in the list.
    val championsState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
    val itemsState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
    val myItems by prefs.myItems.collectAsState(initial = null)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.tab_champions), style = Type.serif(20), modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
        Segmented(listOf(stringResource(R.string.tab_champions), stringResource(R.string.items)), if (showItems) 1 else 0) { showItems = it == 1 }
        Spacer(Modifier.height(10.dp))
        SearchField(query, { query = it }, stringResource(if (showItems) R.string.search_item else R.string.search_champion))
        val data = catalog?.valueOrNull
        when {
            catalog == null -> Unit
            data == null -> OfflineMessage { reload++ }
            else -> {
                val q = normalizeForSearch(query)
                // Your ADCs (or your ADC items) first, then the rest by name.
                val mine: (CatalogEntry) -> Boolean = if (showItems) { e -> myItems?.contains(e.id) ?: isDefaultAdcItem(e.tags) } else { e -> e.id in myAdcs }
                val (favorites, others) = (if (showItems) data.items else data.champions)
                    .filter { normalizeForSearch(it.name).contains(q) }
                    .partition(mine)
                val kind = if (showItems) "item" else "champion"
                LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), state = if (showItems) itemsState else championsState) {
                    // "Favoritos" and "Todos", each with its ink rule; a heading only shows when it has rows.
                    for ((title, rows) in listOf(R.string.my_adcs to favorites, R.string.everyone to others)) {
                        if (rows.isEmpty()) continue
                        item(key = "h$title") { EntityHeader(null, stringResource(title), "") }
                        items(rows, key = { kind + it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { if (showItems) onOpenItem(c.id) else onOpen(c.id) }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Avatar(Entity(kind, c.id, c.name), c.name, data.ddragonVersion, 28.dp)
                                Text(c.name, style = Type.Body)
                            }
                            RowDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChampionScreen(id: String, repo: Repository, onBack: () -> Unit, kind: String = "champion") {
    var history by remember { mutableStateOf<Loaded<ChampionHistory>?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    var catalogName by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, reload) {
        val catalog = repo.catalog().valueOrNull
        version = catalog?.ddragonVersion
        catalogName = (if (kind == "item") catalog?.items else catalog?.champions)?.firstOrNull { it.id == id }?.name
        history = if (kind == "item") repo.item(id) else repo.champion(id)
    }
    val data = history?.valueOrNull
    // The name comes from the history, or from the catalog when there is no history.
    val name = data?.name ?: catalogName ?: id
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        DetailBar(name, null, onBack, titleContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Avatar(Entity(kind, id, name), name, version ?: "", 34.dp)
                Text(name, style = Type.serif(24))
            }
        })
        when {
            history == null -> Unit
            history == Loaded.Missing -> Text(stringResource(R.string.no_history), style = Type.BodyMuted, modifier = Modifier.padding(16.dp))
            data == null -> OfflineMessage { reload++ }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(data.entries, key = { it.patch }) { entry ->
                    Column {
                        val tag = entry.changes.firstNotNullOfOrNull { it.tag }
                        EntityHeader(null, entry.patch, version ?: "") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tag?.let { TagBadge(it) }
                                Text(shortDate(entry.publishedAt), style = Type.Date)
                            }
                        }
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
