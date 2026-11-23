package com.budanga.parcheslol.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.budanga.parcheslol.R

/** "The Quiet Archive" palette (spec 6.4). Warm tones only. */
object Ink {
    val Paper = Color(0xFFF5F2EB)
    val Surface2 = Color(0xFFFAF8F3)
    val Card = Color(0xFFFFFFFF)
    val Input = Color(0xFFEFECE4)
    val Accent = Color(0xFFBA5A3A)
    val AccentSoft = Color(0xFFD47758)
    val SelectTint = Color(0xFFF3E5E0)
    val Text = Color(0xFF22201D)
    val Text2 = Color(0xFF767169)
    val Faint = Color(0xFFA5A097)
    val Line = Color(0xFFDDD9D0)
    val Success = Color(0xFF3A604A)
    val Warning = Color(0xFFD97706)
    val Error = Color(0xFFC0392B)
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
    val Body = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, color = Ink.Text)
    val BodyMuted = Body.copy(color = Ink.Text2)
    val Attribute = Body.copy(fontWeight = FontWeight(500))
    val Data = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 18.sp, color = Ink.Text)
    val Date = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = Ink.Faint)
    val Label = TextStyle(fontFamily = Sans, fontSize = 11.sp, letterSpacing = 0.5.sp, color = Ink.Faint)
    fun serif(size: Int, weight: Int = 500) =
        TextStyle(fontFamily = Serif, fontWeight = FontWeight(weight), fontSize = size.sp, color = Ink.Text)
}

@Composable
fun ArchiveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink.Accent, onPrimary = Color.White,
            background = Ink.Paper, onBackground = Ink.Text,
            surface = Ink.Paper, onSurface = Ink.Text,
            surfaceVariant = Ink.Input, outline = Ink.Line,
            error = Ink.Error,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink.Paper, contentColor = Ink.Text, content = content)
    }
}
