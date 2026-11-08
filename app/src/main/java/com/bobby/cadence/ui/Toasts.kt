package com.bobby.cadence.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class ToastItem(val id: Long, val message: String, val action: String?, val onAction: (() -> Unit)?)

private const val TOAST_MS = 4500L

/** Messages stack upwards: the newest one sits at the bottom. Each one disappears by itself after about 4.5 seconds. */
@Composable
fun ToastStack(items: List<ToastItem>, onRemove: (ToastItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> key(item.id) { ToastCard(item, onRemove) } }
    }
}

@Composable
private fun ToastCard(item: ToastItem, onRemove: (ToastItem) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        shown = true
        delay(TOAST_MS)
        onRemove(item)
    }
    AnimatedVisibility(shown, enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.SurfaceTop)
                .border(1.dp, C.Stroke, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.message, color = C.Text, style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            )
            if (item.action != null) {
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                        .tap { item.onAction?.invoke(); onRemove(item) }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.action, color = C.Accent, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
