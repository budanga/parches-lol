package com.budanga.parcheslol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.budanga.parcheslol.R
import com.budanga.parcheslol.anim.DecipherChangeLine
import com.budanga.parcheslol.anim.PlayOnce
import com.budanga.parcheslol.data.Loaded
import com.budanga.parcheslol.data.PatchFile
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.data.valueOrNull
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import com.budanga.parcheslol.domain.Group
import com.budanga.parcheslol.domain.RowItem
import com.budanga.parcheslol.domain.rowsFor
import com.budanga.parcheslol.domain.adcGroups
import com.budanga.parcheslol.domain.allGroups
import com.budanga.parcheslol.domain.shortDate
import kotlinx.coroutines.launch

/** Top bar shared by the detail screens: back arrow, serif title, optional mono date. */
@Composable
fun DetailBar(title: String, date: String?, onBack: () -> Unit, titleContent: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = Ink.Text2)
        }
        if (titleContent != null) titleContent() else Text(title, style = Type.serif(28))
        Spacer(Modifier.weight(1f))
        date?.let { Text(it, style = Type.Date, modifier = Modifier.padding(end = 4.dp)) }
    }
}

@Composable
fun PatchScreen(
    patchId: String,
    repo: Repository,
    prefs: Prefs,
    onBack: () -> Unit,
    animateNumbers: Boolean = false,
    onLoaded: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loaded<PatchFile>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(patchId, reload) {
        // Saved copy first, then the network: a weak signal never hides data already on the phone.
        if (state == null) repo.cachedPatch(patchId)?.let { state = Loaded.Cached(it) }
        val fresh = repo.patch(patchId)
        if (fresh != Loaded.Offline || state == null) state = fresh
        if (state?.valueOrNull != null) onLoaded()
    }
    val myAdcs by prefs.myAdcs.collectAsState(initial = DEFAULT_ADCS)
    val myItems by prefs.myItems.collectAsState(initial = null)
    // Names of the checked items, so an item whose id changed between versions still matches.
    var itemNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(myItems) {
        val ids = myItems
        itemNames = if (ids == null) emptySet()
        else repo.catalog().valueOrNull?.items.orEmpty().filter { it.id in ids }.map { it.name }.toSet()
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val once = remember(patchId) { PlayOnce() }
    val patch = state?.valueOrNull

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        DetailBar(patchId, patch?.let { shortDate(it.publishedAt) }, onBack)
        Segmented(
            listOf(stringResource(R.string.for_adc), stringResource(R.string.all)),
            if (showAll) 1 else 0,
        ) { showAll = it == 1 }
        when {
            state == Loaded.Offline -> OfflineMessage { scope.launch { reload++ } }
            state == Loaded.Missing -> Text(stringResource(R.string.patch_missing), style = Type.BodyMuted, modifier = Modifier.padding(vertical = 16.dp))
            patch != null -> {
                val groups = if (showAll) allGroups(patch) else adcGroups(patch, myAdcs, myItems, itemNames)
                LazyColumn(Modifier.fillMaxSize()) {
                    if (!showAll && groups.none { it.entity != null }) {
                        item { Text(stringResource(R.string.no_adc_changes), style = Type.BodyMuted, modifier = Modifier.padding(vertical = 16.dp)) }
                    }
                    items(groups, key = { it.key }) { group ->
                        GroupBlock(
                            group = group,
                            ddragonVersion = patch.ddragonVersion,
                            open = !group.collapsed || expanded[group.key] == true,
                            onToggle = { expanded[group.key] = expanded[group.key] != true },
                            animateNumbers = animateNumbers,
                            once = once,
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun GroupBlock(group: Group, ddragonVersion: String, open: Boolean, onToggle: () -> Unit, animateNumbers: Boolean, once: PlayOnce) {
    Column {
        val title = if (group.key == "global") stringResource(R.string.global) else group.title
        if (group.collapsed) {
            Column(Modifier.clickable(onClick = onToggle)) {
                EntityHeader(null, title, ddragonVersion) {
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Ink.Faint)
                }
            }
        } else {
            EntityHeader(group.entity, title, ddragonVersion)
        }
        if (open) {
            var previousWasLine = false
            rowsFor(group).forEach { row ->
                when (row) {
                    is RowItem.Header -> { SubHeader(row, ddragonVersion); previousWasLine = false }
                    is RowItem.Line -> {
                        if (previousWasLine) RowDivider()
                        val change = row.change
                        if (animateNumbers && change.kind == "stat") DecipherChangeLine(change, once) else ChangeLine(change)
                        previousWasLine = true
                    }
                }
            }
        }
    }
}


/** Section, block and sub-part labels inside mixed groups (Global, Otros modos, Cosméticos). */
@Composable
fun SubHeader(row: RowItem.Header, ddragonVersion: String) {
    when (row.level) {
        1 -> Text(row.text, style = Type.serif(15), modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
        2 -> Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            if (row.entity != null) Avatar(row.entity, row.text, ddragonVersion, 20.dp)
            Text(row.text, style = Type.Attribute)
        }
        else -> Text(row.text, style = Type.Label, modifier = Modifier.padding(top = 6.dp))
    }
}
