package com.bobby.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small hand-drawn icons, because the bundled icon set has no media or chart icons. All sized on a 24 unit grid. */
enum class Glyph { PLAY, PAUSE, STOP, SKIP, HOURGLASS, TIMER, CHART, TUNE, FOCUS }

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = (w / 11f).coerceAtLeast(1.5f)
        val line = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * w, y * h)
        when (glyph) {
            Glyph.PLAY -> drawPath(
                Path().apply { moveTo(w * 0.32f, h * 0.2f); lineTo(w * 0.8f, h * 0.5f); lineTo(w * 0.32f, h * 0.8f); close() },
                tint,
            )
            Glyph.PAUSE -> {
                drawRoundRect(tint, p(0.27f, 0.2f), Size(w * 0.17f, h * 0.6f), CornerRadius(w * 0.06f))
                drawRoundRect(tint, p(0.56f, 0.2f), Size(w * 0.17f, h * 0.6f), CornerRadius(w * 0.06f))
            }
            Glyph.STOP -> drawRoundRect(tint, p(0.24f, 0.24f), Size(w * 0.52f, h * 0.52f), CornerRadius(w * 0.1f))
            Glyph.SKIP -> {
                drawPath(
                    Path().apply { moveTo(w * 0.22f, h * 0.22f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.22f, h * 0.78f); close() },
                    tint,
                )
                drawRoundRect(tint, p(0.7f, 0.22f), Size(w * 0.1f, h * 0.56f), CornerRadius(w * 0.04f))
            }
            Glyph.HOURGLASS -> {
                val glass = Path().apply {
                    moveTo(w * 0.28f, h * 0.1f); lineTo(w * 0.72f, h * 0.1f); lineTo(w * 0.72f, h * 0.26f)
                    lineTo(w * 0.52f, h * 0.5f); lineTo(w * 0.72f, h * 0.74f); lineTo(w * 0.72f, h * 0.9f)
                    lineTo(w * 0.28f, h * 0.9f); lineTo(w * 0.28f, h * 0.74f); lineTo(w * 0.48f, h * 0.5f)
                    lineTo(w * 0.28f, h * 0.26f); close()
                }
                drawPath(glass, tint, style = line)
                drawPath(
                    Path().apply { moveTo(w * 0.36f, h * 0.83f); lineTo(w * 0.64f, h * 0.83f); lineTo(w * 0.5f, h * 0.64f); close() },
                    tint,
                )
                drawLine(tint, p(0.5f, 0.46f), p(0.5f, 0.64f), sw * 0.6f, StrokeCap.Round)
            }
            Glyph.TIMER -> {
                drawCircle(tint, radius = w * 0.34f, center = p(0.5f, 0.56f), style = line)
                drawLine(tint, p(0.5f, 0.56f), p(0.5f, 0.38f), sw, StrokeCap.Round)
                drawLine(tint, p(0.4f, 0.1f), p(0.6f, 0.1f), sw, StrokeCap.Round)
                drawLine(tint, p(0.74f, 0.3f), p(0.8f, 0.24f), sw, StrokeCap.Round)
            }
            Glyph.CHART -> {
                drawRoundRect(tint, p(0.16f, 0.52f), Size(w * 0.17f, h * 0.34f), CornerRadius(w * 0.05f))
                drawRoundRect(tint, p(0.415f, 0.28f), Size(w * 0.17f, h * 0.58f), CornerRadius(w * 0.05f))
                drawRoundRect(tint, p(0.67f, 0.14f), Size(w * 0.17f, h * 0.72f), CornerRadius(w * 0.05f))
            }
            Glyph.TUNE -> {
                drawLine(tint, p(0.14f, 0.3f), p(0.86f, 0.3f), sw, StrokeCap.Round)
                drawLine(tint, p(0.14f, 0.7f), p(0.86f, 0.7f), sw, StrokeCap.Round)
                drawCircle(tint, w * 0.11f, p(0.34f, 0.3f))
                drawCircle(tint, w * 0.11f, p(0.66f, 0.7f))
            }
            Glyph.FOCUS -> {
                drawCircle(tint, radius = w * 0.4f, center = p(0.5f, 0.5f), style = line)
                drawCircle(tint, radius = w * 0.15f, center = p(0.5f, 0.5f))
            }
        }
    }
}
