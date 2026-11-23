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
import androidx.compose.material3.Checkbox
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
import com.budanga.parcheslol.data.CatalogEntry
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.data.valueOrNull
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import com.budanga.parcheslol.domain.isDefaultAdcItem
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repo: Repository, prefs: Prefs, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    LaunchedEffect(Unit) { catalog = repo.catalog().valueOrNull }
    val myAdcs by prefs.myAdcs.collectAsState(initial = DEFAULT_ADCS)
    val myItems by prefs.myItems.collectAsState(initial = null)
    val newsAlerts by prefs.newsAlerts.collectAsState(initial = false)

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        DetailBar(stringResource(R.string.settings), null, onBack) { Text(stringResource(R.string.settings), style = Type.serif(24)) }
        val data = catalog ?: return@Column
        val defaultItems = data.items.filter { isDefaultAdcItem(it.tags) }.map { it.id }.toSet()
        val itemsChecked = myItems ?: defaultItems
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
                Text(
                    stringResource(R.string.reset),
                    style = Type.Body.copy(color = Ink.Accent),
                    modifier = Modifier.clickable { scope.launch { prefs.reset() } }.padding(vertical = 8.dp),
                )
            }
            item { EntityHeader(null, stringResource(R.string.my_adcs), "") }
            items(data.champions, key = { "c" + it.id }) { c ->
                CheckRow(c, c.id in myAdcs) { on -> scope.launch { prefs.toggleAdc(c.id, on) } }
            }
            item { EntityHeader(null, stringResource(R.string.my_items), "") }
            items(data.items, key = { "i" + it.id }) { i ->
                CheckRow(i, i.id in itemsChecked) { on -> scope.launch { prefs.toggleItem(i.id, on, defaultItems) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CheckRow(entry: CatalogEntry, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = Ink.Accent, uncheckedColor = Ink.Faint, checkmarkColor = Color.White),
        )
        Text(entry.name, style = Type.Body)
    }
}
