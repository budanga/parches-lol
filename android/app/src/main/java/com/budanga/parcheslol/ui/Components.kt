package com.budanga.parcheslol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.budanga.parcheslol.R
import com.budanga.parcheslol.data.Change
import com.budanga.parcheslol.data.Entity
import com.budanga.parcheslol.domain.Segment
import com.budanga.parcheslol.domain.highlightAfter
import com.budanga.parcheslol.domain.mismatchText
import com.budanga.parcheslol.domain.verificationAltText

private const val CHECK_ID = "check"

/** The official "after" line with the changed numbers in terracotta and the verification icon inline. */
fun afterLine(segments: List<Segment>, verification: String): AnnotatedString = buildAnnotatedString {
    append("+ ")
    for (s in segments) {
        if (s.changed) withStyle(SpanStyle(color = Ink.Accent, fontWeight = FontWeight.Medium)) { append(s.text) }
        else append(s.text)
    }
    if (verification == "verified" || verification == "mismatch") { append(" "); appendInlineContent(CHECK_ID, verificationAltText(verification)) }
}

@Composable
private fun verificationInline(verification: String): Map<String, InlineTextContent> = mapOf(
    CHECK_ID to InlineTextContent(Placeholder(14.sp, 14.sp, PlaceholderVerticalAlign.TextCenter)) {
        if (verification == "verified")
            Icon(Icons.Outlined.Check, stringResource(R.string.verified), tint = Ink.Success, modifier = Modifier.fillMaxSize())
        else
            Icon(Icons.Outlined.WarningAmber, stringResource(R.string.mismatch), tint = Ink.Warning, modifier = Modifier.fillMaxSize())
    },
)

/** Left column: ability letter in mono terracotta, a chart icon for base stats, or nothing. */
@Composable
private fun PartMark(part: String?) {
    Box(Modifier.width(18.dp).padding(top = 2.dp)) {
        when {
            part == "base" -> Icon(Icons.Outlined.BarChart, stringResource(R.string.base_stats), tint = Ink.Accent, modifier = Modifier.size(14.dp))
            part != null && part.length == 1 && part in "PQWER" -> Text(part, style = Type.Data.copy(color = Ink.Accent, fontWeight = FontWeight.Medium))
        }
    }
}

/**
 * One change: attribute, then the two official lines ("−" before, faint; "+" after, with changed numbers marked).
 * [afterContent] lets the caller replace the after line (the decipher animation draws it).
 */
@Composable
fun ChangeLine(
    change: Change,
    modifier: Modifier = Modifier,
    afterContent: (@Composable (AnnotatedString, Map<String, InlineTextContent>) -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        PartMark(change.part)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val before = change.before
            val after = change.after
            if (change.kind == "stat" && before != null && after != null) {
                change.attribute?.let { Text(it, style = Type.Attribute) }
                Text("− $before", style = Type.Data.copy(color = Ink.Faint))
                val line = afterLine(highlightAfter(before, after), change.verification)
                val inline = verificationInline(change.verification)
                if (afterContent != null) afterContent(line, inline)
                else Text(line, style = Type.Data, inlineContent = inline)
                mismatchText(change)?.let { Text(it, style = Type.Body.copy(fontSize = 12.sp, color = Ink.Warning)) }
            } else {
                Text(change.text, style = Type.BodyMuted)
            }
        }
    }
}

fun portraitUrl(entity: Entity?, ddragonVersion: String): String? {
    val id = entity?.id ?: return null
    return when (entity.kind) {
        "champion" -> "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/champion/$id.png"
        "item" -> "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/item/$id.png"
        else -> null
    }
}

/** Round portrait from Data Dragon; the serif initial shows underneath while loading or if it fails. */
@Composable
fun Avatar(entity: Entity?, name: String, ddragonVersion: String, size: Dp = 24.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Ink.Input).border(1.dp, Ink.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), style = Type.serif((size.value * 0.5f).toInt(), 600).copy(color = Ink.Text2))
        portraitUrl(entity, ddragonVersion)?.let {
            AsyncImage(model = it, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Portrait + serif name with the ink rule underneath, as in the "Registro" design. */
@Composable
fun EntityHeader(entity: Entity?, title: String, ddragonVersion: String, trailing: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (entity != null) Avatar(entity, title, ddragonVersion)
            Text(title, style = Type.serif(18), modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(7.dp))
        HorizontalDivider(thickness = 1.dp, color = Ink.Text)
    }
}

@Composable
fun RowDivider() = HorizontalDivider(thickness = 1.dp, color = Ink.Line)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink.Accent, contentColor = Color.White),
        modifier = Modifier.height(46.dp),
    ) { Text(text, style = Type.Body.copy(fontSize = 15.sp, fontWeight = FontWeight(600), color = Color.White)) }
}

@Composable
fun OfflineMessage(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.offline), style = Type.BodyMuted)
        PrimaryButton(stringResource(R.string.retry), onRetry)
    }
}

/** "Para ADC" / "Todo" selector: input fill, active option with the selection tint. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink.Input).padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (active) Ink.SelectTint else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.Body.copy(
                    fontSize = 13.sp,
                    color = if (active) Ink.Accent else Ink.Text2,
                    fontWeight = if (active) FontWeight(600) else FontWeight(400),
                ))
            }
        }
    }
}
