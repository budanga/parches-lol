package com.budanga.parcheslol.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import com.budanga.parcheslol.MainActivity
import com.budanga.parcheslol.data.DATA_BASE_URL
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.domain.shortDate
import com.budanga.parcheslol.domain.widgetEntities
import com.budanga.parcheslol.ui.portraitUrl
import com.budanga.parcheslol.ui.Palette
import com.budanga.parcheslol.ui.LightPalette
import com.budanga.parcheslol.ui.DarkPalette
import java.io.File
import kotlinx.coroutines.flow.first

/** The widget's three colours, from the app's palettes. */
private class WidgetColors(val paper: ColorProvider, val ink: ColorProvider, val faint: ColorProvider)

/** Same theme as the app: "system" lets the launcher pick day or night, otherwise the forced palette. */
private fun widgetColors(mode: String): WidgetColors {
    fun pick(f: (Palette) -> Color) = when (mode) {
        "dark" -> ColorProvider(f(DarkPalette))
        "light" -> ColorProvider(f(LightPalette))
        else -> androidx.glance.color.ColorProvider(day = f(LightPalette), night = f(DarkPalette))
    }
    return WidgetColors(pick { it.paper }, pick { it.text }, pick { it.faint })
}

private data class WidgetData(val patch: String, val date: String, val portraits: List<Bitmap>)

/** Home-screen widget, in the app's theme: latest patch, its date and the portraits of your ADCs and ADC items it changed. */
class PatchWidget : GlanceAppWidget() {
    // Square ("chico") or a one-line strip ("tira"), depending on how the widget is sized.
    override val sizeMode = SizeMode.Responsive(setOf(SQUARE, STRIP))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        val colors = widgetColors(Prefs(context).theme.first())
        provideContent { Content(data, colors) }
    }

    /** Saved data only: the widget never waits on the network (the app and the worker refresh it). */
    private suspend fun load(context: Context): WidgetData? {
        val repo = Repository(DATA_BASE_URL, File(context.filesDir, "data"))
        val prefs = Prefs(context)
        val summary = repo.cachedIndex()?.patches?.firstOrNull() ?: return null
        val patch = repo.cachedPatch(summary.patch)
        val myItems = prefs.myItems.first()
        val itemNames = myItems?.let { ids -> repo.cachedCatalog()?.items?.filter { it.id in ids }?.map { it.name }?.toSet() }.orEmpty()
        val entities = patch?.let { widgetEntities(it, prefs.myAdcs.first(), myItems, itemNames) }.orEmpty().take(MAX_PORTRAITS)
        val loader = ImageLoader(context)
        val portraits = entities.mapNotNull { e ->
            val url = portraitUrl(e, patch!!.ddragonVersion) ?: return@mapNotNull null
            val request = ImageRequest.Builder(context).data(url).size(96).allowHardware(false)
                .transformations(CircleCropTransformation()).build()
            (loader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        }
        return WidgetData(summary.patch, shortDate(summary.publishedAt), portraits)
    }

    @Composable
    private fun Content(data: WidgetData?, c: WidgetColors) {
        val open = Intent(androidx.glance.LocalContext.current, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PATCH, data?.patch)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val base = GlanceModifier.fillMaxSize().background(c.paper).cornerRadius(20.dp).clickable(actionStartActivity(open))
        if (data == null) {
            Column(base.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Abrí la app para cargar los parches.", style = TextStyle(color = c.faint, fontSize = 12.sp))
            }
            return
        }
        if (LocalSize.current.height < SQUARE.height) Strip(data, base, c) else Square(data, base, c)
    }

    @Composable
    private fun Square(data: WidgetData, modifier: GlanceModifier, c: WidgetColors) {
        Column(modifier.padding(14.dp)) {
            Text("Último parche", style = TextStyle(color = c.faint, fontSize = 10.sp))
            Text(data.patch, style = TextStyle(color = c.ink, fontSize = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium))
            Text(data.date, style = TextStyle(color = c.faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace))
            Spacer(GlanceModifier.defaultWeight())
            Portraits(data.portraits)
        }
    }

    @Composable
    private fun Strip(data: WidgetData, modifier: GlanceModifier, c: WidgetColors) {
        Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(data.patch, style = TextStyle(color = c.ink, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.width(10.dp))
            Text(data.date, style = TextStyle(color = c.faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace))
            Spacer(GlanceModifier.defaultWeight())
            Portraits(data.portraits)
        }
    }

    @Composable
    private fun Portraits(bitmaps: List<Bitmap>) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            bitmaps.forEachIndexed { i, b ->
                if (i > 0) Spacer(GlanceModifier.width(4.dp))
                Image(ImageProvider(b), contentDescription = null, modifier = GlanceModifier.size(22.dp))
            }
        }
    }

    companion object {
        val SQUARE = DpSize(110.dp, 110.dp)
        val STRIP = DpSize(250.dp, 50.dp)
        const val MAX_PORTRAITS = 6
    }
}

class PatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PatchWidget()
}
