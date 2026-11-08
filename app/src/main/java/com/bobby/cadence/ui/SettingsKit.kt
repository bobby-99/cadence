package com.bobby.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Building blocks for the settings pages. A page is a SettingsPage holding SGroups; a group is a card of
 * rows separated by hairlines. Rows never draw their own capsule buttons: choices open a list dialog,
 * on/off is a switch, and actions are plain text.
 */

/** Clickable row surface: no ripple, a soft highlight while pressed. */
@Composable
private fun rowModifier(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return Modifier
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return Modifier
        .background(if (pressed) C.SurfaceHi else Color.Transparent)
        .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
}

class GroupScope {
    internal val rows = ArrayList<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) { rows += content }
}

@Composable
fun SGroup(title: String? = null, footer: String? = null, build: GroupScope.() -> Unit) {
    val scope = GroupScope().apply(build)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), color = C.Faint, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface)
                .border(1.dp, C.Stroke, RoundedCornerShape(18.dp)),
        ) {
            scope.rows.forEachIndexed { i, r ->
                r()
                if (i < scope.rows.lastIndex) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(C.Stroke))
            }
        }
        if (footer != null) {
            Text(footer, color = C.Faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

/** A full-screen settings page with a back arrow and a large title. */
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 20.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        }
        Text(
            title, color = C.Text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 16.dp),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            content = content,
        )
    }
}

@Composable
private fun Chevron(tint: Color = C.Faint) {
    Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
        val s = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.08f)
            lineTo(size.width * 0.9f, size.height * 0.5f)
            lineTo(size.width * 0.15f, size.height * 0.92f)
        }
        drawPath(p, tint, style = s)
    }
}

/** Rounded amber tile that carries a row's icon. */
@Composable
fun IconTile(icon: ImageVector? = null, text: String? = null, tint: Color = C.Accent) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        else if (text != null) Text(text, color = tint, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun RowShell(onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Row(
        rowModifier(onClick).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun TitleHint(title: String, hint: String?, modifier: Modifier = Modifier, titleColor: Color = C.Text) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = titleColor, style = MaterialTheme.typography.titleSmall)
        if (!hint.isNullOrBlank()) Text(hint, color = C.Faint, style = MaterialTheme.typography.bodySmall)
    }
}

/** Opens another page. Shows the current value on the right. */
@Composable
fun NavRow(
    title: String, value: String? = null, hint: String? = null, icon: ImageVector? = null, iconText: String? = null,
    valueColor: Color = C.Dim, onClick: () -> Unit,
) {
    RowShell(onClick) {
        if (icon != null || iconText != null) IconTile(icon, iconText)
        TitleHint(title, hint, Modifier.weight(1f))
        if (value != null) Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = ValueMaxWidth))
        Chevron()
    }
}

private val ValueMaxWidth = 170.dp

@Composable
fun SwitchRow(title: String, hint: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell({ onChange(!checked) }) {
        TitleHint(title, hint, Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.AccentOn, checkedTrackColor = C.Accent,
                uncheckedThumbColor = C.Dim, uncheckedTrackColor = C.SurfaceHi, uncheckedBorderColor = C.Stroke,
            ),
        )
    }
}

/** A row that shows the current choice and opens a list dialog to change it. */
@Composable
fun <T> ChoiceRow(title: String, hint: String? = null, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: ""
    RowShell({ open = true }) {
        TitleHint(title, hint, Modifier.weight(1f))
        Text(label, color = C.Accent, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = ValueMaxWidth), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Chevron()
    }
    if (open) ChoiceDialog(title, options, selected, onDismiss = { open = false }) { onSelect(it); open = false }
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHi,
        title = { Text(title, color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (v, l) ->
                    val on = v == selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(v) }.padding(horizontal = 6.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Canvas(Modifier.size(20.dp)) {
                            drawCircle(if (on) C.Accent else C.Faint, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                            if (on) drawCircle(C.Accent, radius = size.minDimension / 4)
                        }
                        Text(l, color = if (on) C.Text else C.Dim, style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Dim) } },
    )
}

/** Two or three mutually exclusive options shown as one segmented control under the title. */
@Composable
fun <T> SegRow(title: String, hint: String? = null, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitleHint(title, hint)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Bg).border(1.dp, C.Stroke, RoundedCornerShape(14.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { (v, l) ->
                val on = v == selected
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp))
                        .background(if (on) C.Accent else Color.Transparent).clickable { onSelect(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        l, color = if (on) C.AccentOn else C.Dim, maxLines = 1,
                        style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** A plain text action, e.g. Export. */
@Composable
fun ActionRow(title: String, hint: String? = null, color: Color = C.Text, onClick: () -> Unit) {
    RowShell(onClick) { TitleHint(title, hint, Modifier.weight(1f), titleColor = color) }
}

@Composable
fun InfoRow(title: String, value: String, valueColor: Color = C.Dim) {
    RowShell(null) {
        Text(title, color = C.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = ValueMaxWidth), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** A permission or capability with a status dot and, when it needs attention, a text action. */
@Composable
fun StatusRow(title: String, ok: Boolean, okText: String, badText: String, action: String? = "Allow", onAction: () -> Unit) {
    RowShell(if (!ok && action != null) onAction else null) {
        Dot(if (ok) C.Done else C.Medium, 10.dp)
        TitleHint(title, if (ok) okText else badText, Modifier.weight(1f))
        if (!ok && action != null) Text(action, color = C.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** A row that carries free content, e.g. a paragraph. */
@Composable
fun BodyRow(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}


