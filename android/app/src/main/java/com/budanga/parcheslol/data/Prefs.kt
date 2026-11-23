package com.budanga.parcheslol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.budanga.parcheslol.domain.DEFAULT_ADCS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "prefs")

class Prefs(private val ds: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    private object K {
        val adcs = stringSetPreferencesKey("my_adcs")
        val items = stringSetPreferencesKey("my_items")
        val newsAlerts = booleanPreferencesKey("news_alerts")
        val opened = stringSetPreferencesKey("opened_patches")
        val revealed = stringSetPreferencesKey("revealed_patches")
        val notified = stringSetPreferencesKey("notified_patches")
        val lastNews = stringPreferencesKey("last_news_url")
    }

    val myAdcs: Flow<Set<String>> = ds.data.map { it[K.adcs] ?: DEFAULT_ADCS }
    /** null = the default rule ([com.budanga.parcheslol.domain.isDefaultAdcItem]). */
    val myItems: Flow<Set<String>?> = ds.data.map { it[K.items] }
    val newsAlerts: Flow<Boolean> = ds.data.map { it[K.newsAlerts] ?: false }
    val openedPatches: Flow<Set<String>> = ds.data.map { it[K.opened] ?: emptySet() }
    val revealedPatches: Flow<Set<String>> = ds.data.map { it[K.revealed] ?: emptySet() }
    val notifiedPatches: Flow<Set<String>> = ds.data.map { it[K.notified] ?: emptySet() }
    val lastNewsUrl: Flow<String?> = ds.data.map { it[K.lastNews] }

    /** Read-modify-write inside one edit, so quick consecutive taps never lose a change. */
    suspend fun toggleAdc(id: String, on: Boolean) {
        ds.edit { val cur = it[K.adcs] ?: DEFAULT_ADCS; it[K.adcs] = if (on) cur + id else cur - id }
    }
    suspend fun toggleItem(id: String, on: Boolean, defaults: Set<String>) {
        ds.edit { val cur = it[K.items] ?: defaults; it[K.items] = if (on) cur + id else cur - id }
    }
    suspend fun setNewsAlerts(on: Boolean) { ds.edit { it[K.newsAlerts] = on } }
    suspend fun addOpened(id: String) { ds.edit { it[K.opened] = (it[K.opened] ?: emptySet()) + id } }
    suspend fun addRevealed(ids: Collection<String>) { ds.edit { it[K.revealed] = (it[K.revealed] ?: emptySet()) + ids } }
    suspend fun addNotified(ids: Collection<String>) { ds.edit { it[K.notified] = (it[K.notified] ?: emptySet()) + ids } }
    suspend fun setLastNewsUrl(url: String) { ds.edit { it[K.lastNews] = url } }

    /** Back to the default ADC and item lists, news alerts off. */
    suspend fun reset() { ds.edit { it.remove(K.adcs); it.remove(K.items); it.remove(K.newsAlerts) } }
}
