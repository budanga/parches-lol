package com.budanga.parcheslol.ui

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.data.valueOrNull
import com.budanga.parcheslol.domain.prefetchOrder
import kotlinx.coroutines.flow.first

/**
 * Loads every champion and item portrait into the image cache in the background, so a list never
 * shows an empty circle while scrolling. Requests match [Avatar]'s exactly (same URL, original size),
 * so they land on the same memory-cache entry. Queued in order; the image loader runs a few at a time.
 */
suspend fun prefetchPortraits(context: Context, repo: Repository, prefs: Prefs) {
    val catalog = repo.cachedCatalog() ?: repo.catalog().valueOrNull ?: return
    val loader = context.imageLoader
    for (e in prefetchOrder(catalog, prefs.myAdcs.first(), prefs.myItems.first())) {
        val url = portraitUrl(e, catalog.ddragonVersion) ?: continue
        loader.enqueue(ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).build())
    }
}
