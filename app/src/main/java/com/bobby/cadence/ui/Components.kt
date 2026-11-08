package com.bobby.cadence.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Clickable without the heavy default ripple. */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.965f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
}

@Composable
fun IconTap(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = C.Text,
    size: Dp = 44.dp,
    filled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) C.SurfaceHi else Color.Transparent)
            .border(1.dp, if (filled) C.Stroke else Color.Transparent, CircleShape)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = C.Accent,
    leading: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val bg by animateColorAsState(if (selected) color.copy(alpha = 0.16f) else C.SurfaceHi, tween(150), label = "pillbg")
    val stroke by animateColorAsState(if (selected) color.copy(alpha = 0.55f) else C.Stroke, tween(150), label = "pillstroke")
    val fg = if (selected) color else C.Dim
    val base = modifier
        .clip(RoundedCornerShape(50))
        .background(bg)
        .border(BorderStroke(1.dp, stroke), RoundedCornerShape(50))
    val m = if (onClick != null) base.tap(onClick = onClick) else base
    Row(
        m.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Text(leading, color = fg, style = MaterialTheme.typography.labelLarge)
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small read-only tag shown under a task title. */
@Composable
fun Tag(text: String, color: Color = C.Dim, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

@Composable
fun CheckCircle(checked: Boolean, ring: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "checkfill")
    Box(
        modifier
            .size(44.dp)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = 2.dp.toPx()
            val r = size.minDimension / 2 - w / 2
            if (fill > 0f) drawCircle(C.Done.copy(alpha = fill), radius = r + w / 2)
            drawCircle(if (checked) C.Done else ring, radius = r, style = Stroke(w))
            if (fill > 0f) {
                val s = size.minDimension
                val a = Offset(s * 0.28f, s * 0.52f)
                val b = Offset(s * 0.44f, s * 0.68f)
                val c = Offset(s * 0.74f, s * 0.34f)
                val sw = 2.4.dp.toPx()
                drawLine(C.AccentOn.copy(alpha = fill), a, b, sw, StrokeCap.Round)
                drawLine(C.AccentOn.copy(alpha = fill), b, c, sw, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, color: Color = C.Dim, trailing: (@Composable RowScope.() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(C.Stroke))
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Box(modifier.size(size).alpha(alpha).clip(CircleShape).background(color))
}

fun hapticTick(h: androidx.compose.ui.hapticfeedback.HapticFeedback, enabled: Boolean) {
    if (enabled) h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

@Composable
fun rememberHaptic() = LocalHapticFeedback.current

/** Standard microphone glyph (the core icon set does not include it). */
val MicIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Mic", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 14.99f, 12.66f, 14.99f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            verticalLineToRelative(6f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            moveTo(17.91f, 11f)
            curveToRelative(-0.49f, 0f, -0.9f, 0.36f, -0.98f, 0.85f)
            curveTo(16.52f, 14.2f, 14.47f, 16f, 12f, 16f)
            reflectiveCurveToRelative(-4.52f, -1.8f, -4.93f, -4.15f)
            curveToRelative(-0.08f, -0.49f, -0.49f, -0.85f, -0.98f, -0.85f)
            curveToRelative(-0.61f, 0f, -1.09f, 0.54f, -1f, 1.14f)
            curveToRelative(0.49f, 3f, 2.89f, 5.35f, 5.91f, 5.78f)
            verticalLineTo(20f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-2.08f)
            curveToRelative(3.02f, -0.43f, 5.42f, -2.78f, 5.91f, -5.78f)
            curveToRelative(0.1f, -0.6f, -0.39f, -1.14f, -1f, -1.14f)
            close()
        }
    }.build()
}
