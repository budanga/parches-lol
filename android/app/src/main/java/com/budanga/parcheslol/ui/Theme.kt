package com.budanga.parcheslol.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.budanga.parcheslol.R

/** One set of brand colours. Warm tones only, in both themes. */
data class Palette(
    val paper: Color, val surface2: Color, val card: Color, val input: Color,
    val accent: Color, val accentSoft: Color, val selectTint: Color,
    val text: Color, val text2: Color, val faint: Color, val line: Color,
    val success: Color, val warning: Color, val error: Color,
)

val LightPalette = Palette(
    paper = Color(0xFFF5F2EB), surface2 = Color(0xFFFAF8F3), card = Color(0xFFFFFFFF), input = Color(0xFFEFECE4),
    accent = Color(0xFFBA5A3A), accentSoft = Color(0xFFD47758), selectTint = Color(0xFFF3E5E0),
    text = Color(0xFF22201D), text2 = Color(0xFF767169), faint = Color(0xFFA5A097), line = Color(0xFFDDD9D0),
    success = Color(0xFF3A604A), warning = Color(0xFFD97706), error = Color(0xFFC0392B),
)

/** Dark archive: dark roasted paper, paper-coloured ink, a lighter terracotta for contrast. */
val DarkPalette = Palette(
    paper = Color(0xFF1B1916), surface2 = Color(0xFF211F1B), card = Color(0xFF26231F), input = Color(0xFF2C2924),
    accent = Color(0xFFD47758), accentSoft = Color(0xFFE09274), selectTint = Color(0xFF3A2721),
    text = Color(0xFFEDE8DF), text2 = Color(0xFFA9A297), faint = Color(0xFF7A746B), line = Color(0xFF3A3630),
    success = Color(0xFF7FB08F), warning = Color(0xFFE6A24A), error = Color(0xFFE07A6A),
)

fun isDarkTheme(mode: String, systemDark: Boolean): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> systemDark
}

/** Brand colours of the active theme. Reading them in a composable redraws it when the theme changes. */
object Ink {
    var palette by mutableStateOf(LightPalette)
    val Paper get() = palette.paper
    val Surface2 get() = palette.surface2
    val Card get() = palette.card
    val Input get() = palette.input
    val Accent get() = palette.accent
    val AccentSoft get() = palette.accentSoft
    val SelectTint get() = palette.selectTint
    val Text get() = palette.text
    val Text2 get() = palette.text2
    val Faint get() = palette.faint
    val Line get() = palette.line
    val Success get() = palette.success
    val Warning get() = palette.warning
    val Error get() = palette.error
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Serif = FontFamily(variable(R.font.newsreader, 500), variable(R.font.newsreader, 600))
val Sans = FontFamily(
    variable(R.font.plus_jakarta_sans, 400),
    variable(R.font.plus_jakarta_sans, 500),
    variable(R.font.plus_jakarta_sans, 600),
)
val Mono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

object Type {
    val Body get() = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, color = Ink.Text)
    val BodyMuted get() = Body.copy(color = Ink.Text2)
    val Attribute get() = Body.copy(fontWeight = FontWeight(500))
    val Data get() = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 18.sp, color = Ink.Text)
    val Date get() = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = Ink.Faint)
    val Label get() = TextStyle(fontFamily = Sans, fontSize = 11.sp, letterSpacing = 0.5.sp, color = Ink.Faint)
    fun serif(size: Int, weight: Int = 500) =
        TextStyle(fontFamily = Serif, fontWeight = FontWeight(weight), fontSize = size.sp, color = Ink.Text)
}

@Composable
fun ArchiveTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    Ink.palette = if (dark) DarkPalette else LightPalette
    MaterialTheme(
        colorScheme = (if (dark) androidx.compose.material3.darkColorScheme(
            primary = Ink.Accent, onPrimary = Color.White,
            background = Ink.Paper, onBackground = Ink.Text,
            surface = Ink.Paper, onSurface = Ink.Text,
            surfaceVariant = Ink.Input, outline = Ink.Line,
            error = Ink.Error,
        ) else lightColorScheme(
            primary = Ink.Accent, onPrimary = Color.White,
            background = Ink.Paper, onBackground = Ink.Text,
            surface = Ink.Paper, onSurface = Ink.Text,
            surfaceVariant = Ink.Input, outline = Ink.Line,
            error = Ink.Error,
        )),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink.Paper, contentColor = Ink.Text, content = content)
    }
}
