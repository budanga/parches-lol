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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.budanga.parcheslol.R
import com.budanga.parcheslol.data.Catalog
import com.budanga.parcheslol.data.Entity
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.data.valueOrNull
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import com.budanga.parcheslol.domain.isDefaultAdcItem
import com.budanga.parcheslol.domain.favoritePickerRows
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repo: Repository,
    prefs: Prefs,
    onBack: () -> Unit,
    onAnimations: () -> Unit = {},
    onFavorites: (kind: String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    // Saved catalog first (instant, so the lists appear with the options), then refresh it.
    LaunchedEffect(Unit) {
        catalog = repo.cachedCatalog()
        repo.catalog().valueOrNull?.let { catalog = it }
    }
    val myAdcs by prefs.myAdcs.collectAsState(initial = DEFAULT_ADCS)
    val myItems by prefs.myItems.collectAsState(initial = null)
    val newsAlerts by prefs.newsAlerts.collectAsState(initial = false)
    val theme by prefs.theme.collectAsState(initial = "system")

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        DetailBar(stringResource(R.string.settings), null, onBack, titleContent = { Text(stringResource(R.string.settings), style = Type.serif(24)) })
        // Without the catalog (offline on first run) the news switch and reset still show.
        val data = catalog
        val defaultItems = data?.items.orEmpty().filter { isDefaultAdcItem(it.tags) }.map { it.id }.toSet()
        val itemsChecked = myItems ?: defaultItems
        var confirmReset by remember { mutableStateOf(false) }
        if (confirmReset) AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Ink.Card,
            text = { Text(stringResource(R.string.reset_confirm), style = Type.Body) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; scope.launch { prefs.reset() } }) {
                    Text(stringResource(R.string.reset), style = Type.Body.copy(color = Ink.Accent))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel), style = Type.BodyMuted) }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.news_alerts), style = Type.Body, modifier = Modifier.weight(1f))
                    Switch(
                        checked = newsAlerts,
                        onCheckedChange = { scope.launch { prefs.setNewsAlerts(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Ink.Accent, uncheckedTrackColor = Ink.Input, uncheckedBorderColor = Ink.Line),
                    )
                }
                // Theme: follow the phone, or force light / dark.
                Text(stringResource(R.string.theme), style = Type.Body, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                val modes = listOf("system", "light", "dark")
                Segmented(
                    listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                    modes.indexOf(theme).coerceAtLeast(0),
                ) { i -> scope.launch { prefs.setTheme(modes[i]) } }
                Spacer(Modifier.height(8.dp))
                // Each list lives on its own screen: favorites first, a search to add more.
                LinkRow(stringResource(R.string.my_adcs), myAdcs.size.toString()) { onFavorites("champion") }
                LinkRow(stringResource(R.string.my_items), data?.let { itemsChecked.size.toString() }) { onFavorites("item") }
                LinkRow(stringResource(R.string.animations), null, onAnimations)
                RowDivider()
                Text(
                    stringResource(R.string.reset),
                    style = Type.Body.copy(color = Ink.Accent),
                    modifier = Modifier.clickable { confirmReset = true }.padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** A settings row that opens another screen, with an optional count before the arrow. */
@Composable
private fun LinkRow(label: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.Body, modifier = Modifier.weight(1f))
        detail?.let { Text(it, style = Type.Date, modifier = Modifier.padding(end = 4.dp)) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Ink.Faint)
    }
}

/**
 * Ajustes → Favoritos / Objetos favoritos ([kind] "champion" or "item").
 * With the search empty only your picks show; typing lists every match to add.
 * Rows you touch stay on screen until you leave, so an accidental tap is easy to undo.
 */
@Composable
fun FavoritesScreen(kind: String, repo: Repository, prefs: Prefs, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    LaunchedEffect(Unit) {
        catalog = repo.cachedCatalog()
        repo.catalog().valueOrNull?.let { catalog = it }
    }
    val myAdcs by prefs.myAdcs.collectAsState(initial = DEFAULT_ADCS)
    val myItems by prefs.myItems.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val items = kind == "item"
    val title = stringResource(if (items) R.string.my_items else R.string.my_adcs)

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        DetailBar(title, null, onBack, titleContent = { Text(title, style = Type.serif(24)) })
        SearchField(query, { query = it }, stringResource(if (items) R.string.add_item else R.string.add_champion))
        val data = catalog ?: return@Column
        val defaultItems = data.items.filter { isDefaultAdcItem(it.tags) }.map { it.id }.toSet()
        val selected = if (items) myItems ?: defaultItems else myAdcs
        val entries = if (items) data.items else data.champions
        val rows = favoritePickerRows(entries, selected + touched, query)
        LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
            if (rows.isEmpty()) item {
                Text(
                    stringResource(if (query.isBlank()) R.string.no_favorites else R.string.no_results),
                    style = Type.BodyMuted, modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            items(rows, key = { it.id }) { e ->
                val checked = e.id in selected
                val toggle: (Boolean) -> Unit = { on ->
                    touched = touched + e.id
                    scope.launch { if (items) prefs.toggleItem(e.id, on, defaultItems) else prefs.toggleAdc(e.id, on) }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { toggle(!checked) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = toggle,
                        colors = CheckboxDefaults.colors(checkedColor = Ink.Accent, uncheckedColor = Ink.Faint, checkmarkColor = Color.White),
                    )
                    Avatar(Entity(kind, e.id, e.name), e.name, data.ddragonVersion, 28.dp)
                    Text(e.name, style = Type.Body)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Ajustes → Animaciones: one switch per animation. */
@Composable
fun AnimationsScreen(prefs: Prefs, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val title by prefs.animTitle.collectAsState(initial = true)
    val transition by prefs.animTransition.collectAsState(initial = true)
    val numbers by prefs.animNumbers.collectAsState(initial = true)
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        DetailBar(stringResource(R.string.animations), null, onBack, titleContent = { Text(stringResource(R.string.animations), style = Type.serif(24)) })
        SwitchRow(stringResource(R.string.anim_title), title) { scope.launch { prefs.setAnimTitle(it) } }
        SwitchRow(stringResource(R.string.anim_transition), transition) { scope.launch { prefs.setAnimTransition(it) } }
        SwitchRow(stringResource(R.string.anim_numbers), numbers) { scope.launch { prefs.setAnimNumbers(it) } }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.Body, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Ink.Accent, uncheckedTrackColor = Ink.Input, uncheckedBorderColor = Ink.Line),
        )
    }
}
