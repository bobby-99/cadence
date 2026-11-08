package com.bobby.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bobby.cadence.HomeState
import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.SortMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawerContent(
    filter: UiFilter,
    home: HomeState,
    settings: AppSettings,
    onView: (ViewKind) -> Unit,
    onPriority: (Int?) -> Unit,
    onQuery: (String) -> Unit,
    onSort: (SortMode) -> Unit,
    onShowCompleted: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 20.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(C.AccentSoft))
                Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(C.Accent))
            }
            Text("Cadence", color = C.Text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }

        // search
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(C.SurfaceHi)
                .border(1.dp, C.Stroke, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Search, null, tint = C.Faint, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f)) {
                if (filter.query.isEmpty()) Text("Search tasks", color = C.Faint, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = filter.query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.Text),
                    cursorBrush = SolidColor(C.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (filter.query.isNotEmpty()) {
                Icon(Icons.Default.Close, "Clear", tint = C.Dim, modifier = Modifier.size(18.dp).tap { onQuery("") })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("Views", modifier = Modifier.padding(bottom = 6.dp))
            ViewKind.entries.forEach { v ->
                val selected = filter.day == null && filter.view == v
                val count = home.counts[v] ?: 0
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (selected) C.AccentCard else androidx.compose.ui.graphics.Color.Transparent)
                        .tap { onView(v) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Dot(
                        when {
                            selected -> C.Accent
                            v == ViewKind.OVERDUE && count > 0 -> C.Danger
                            else -> C.Rail
                        },
                        8.dp,
                    )
                    Text(
                        v.label,
                        color = if (selected) androidx.compose.ui.graphics.Color(0xFFF4D9A0) else C.Text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    val danger = v == ViewKind.OVERDUE && count > 0
                    Text(
                        count.toString(),
                        color = if (danger) C.Danger else if (selected) C.Accent else C.Faint,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Priority")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Any", selected = filter.priority == null) { onPriority(null) }
                listOf(3, 2, 1, 0).forEach { p ->
                    Pill(
                        priorityLabel(p),
                        selected = filter.priority == p,
                        color = if (p == 0) C.Accent else priorityColor(p),
                    ) { onPriority(if (filter.priority == p) null else p) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Sort by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { m -> Pill(m.label, selected = settings.sort == m) { onSort(m) } }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Show completed in All", color = C.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.showCompleted,
                onCheckedChange = onShowCompleted,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = C.AccentOn, checkedTrackColor = C.Accent,
                    uncheckedThumbColor = C.Dim, uncheckedTrackColor = C.SurfaceHi, uncheckedBorderColor = C.Stroke,
                ),
            )
        }

        if (!filter.isDefault) {
            Pill("Clear all filters", color = C.Danger, selected = true, modifier = Modifier.fillMaxWidth(), onClick = onReset)
        }
        Spacer(Modifier.height(8.dp))
    }
}
