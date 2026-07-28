package com.sociallimiter.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

private val OpensColor = Color(0xFF2E9E5B)
private val BlockedColor = Color(0xFFD05555)
private val TimeColor = Color(0xFF4A82D6)

@Composable
fun DashboardScreen(
    state: DashboardState,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (!state.hasData) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "No usage recorded yet. Once you open, use, or get blocked from a " +
                            "monitored app, your activity will show up here.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item { TodayCard(state) }
        item { TimeTrendCard(state) }
        item { ActivityTrendCard(state) }
        item { PerAppCard(state) }

        if (state.hasData) {
            item {
                OutlinedButton(
                    onClick = onClearHistory,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear usage history") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TodayCard(state: DashboardState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell("Opens", state.todayOpens.toString(), OpensColor, Modifier.weight(1f))
                StatCell("Blocked", state.todayBlocked.toString(), BlockedColor, Modifier.weight(1f))
                StatCell("Time used", formatDuration(state.todayUsedMillis), TimeColor, Modifier.weight(1f))
            }
            Text(
                "Last 7 days: ${state.weekOpens} opens · ${state.weekBlocked} blocked attempts · " +
                    "${state.weekAbandoned} backed out at prompt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color,
            fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimeTrendCard(state: DashboardState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Time used per day", style = MaterialTheme.typography.titleMedium)
            val minutes = state.days.map { (it.usedMillis / 60_000L).toInt() }
            BarChart(
                values = minutes.map { it.toFloat() },
                labels = state.days.map { it.label },
                barColor = TimeColor,
                valueText = { v -> if (v <= 0f) "" else "${v.toInt()}m" },
            )
        }
    }
}

@Composable
private fun ActivityTrendCard(state: DashboardState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Opens vs blocked attempts", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(OpensColor, "Opens")
                LegendDot(BlockedColor, "Blocked")
            }
            GroupedBarChart(
                seriesA = state.days.map { it.opens.toFloat() },
                seriesB = state.days.map { it.blocked.toFloat() },
                labels = state.days.map { it.label },
                colorA = OpensColor,
                colorB = BlockedColor,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawRect(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PerAppCard(state: DashboardState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("By app (last 90 days)", style = MaterialTheme.typography.titleMedium)
            if (state.perApp.isEmpty()) {
                Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium)
            }
            state.perApp.forEach { app ->
                Column {
                    Text(app.appName, style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append("${app.opens} opens")
                            if (app.abandoned > 0) append(" · ${app.abandoned} backed out")
                            if (app.totalBlocked > 0) {
                                append(" · ${app.totalBlocked} blocked")
                                val parts = mutableListOf<String>()
                                if (app.blockedSchedule > 0) parts.add("${app.blockedSchedule} schedule")
                                if (app.blockedCooldown > 0) parts.add("${app.blockedCooldown} cooldown")
                                if (app.blockedBudget > 0) parts.add("${app.blockedBudget} budget")
                                if (parts.isNotEmpty()) append(" (${parts.joinToString(", ")})")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarChart(
    values: List<Float>,
    labels: List<String>,
    barColor: Color,
    valueText: (Float) -> String,
) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Row(
        Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(valueText(v), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, textAlign = TextAlign.Center)
                Canvas(
                    Modifier.fillMaxWidth().height((100f * (v / max)).coerceAtLeast(2f).dp),
                ) {
                    drawRoundedBar(barColor)
                }
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun GroupedBarChart(
    seriesA: List<Float>,
    seriesB: List<Float>,
    labels: List<String>,
    colorA: Color,
    colorB: Color,
) {
    val max = (seriesA + seriesB).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Row(
        Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        labels.indices.forEach { i ->
            val a = seriesA.getOrElse(i) { 0f }
            val b = seriesB.getOrElse(i) { 0f }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Row(
                    Modifier.fillMaxWidth().height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BarCell(a, max, colorA, Modifier.weight(1f))
                    BarCell(b, max, colorB, Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Text(labels[i], style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun BarCell(value: Float, max: Float, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom) {
        if (value > 0f) {
            Text(value.toInt().toString(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Canvas(
            Modifier.fillMaxWidth()
                .height((100f * (value / max)).coerceAtLeast(if (value > 0f) 4f else 1f).dp),
        ) { drawRoundedBar(color) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedBar(color: Color) {
    val radius = 6f
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, 0f),
        size = Size(size.width, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    )
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) String.format(Locale.US, "%dh %02dm", hours, minutes)
    else String.format(Locale.US, "%dm", minutes)
}
