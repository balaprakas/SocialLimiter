package com.sociallimiter.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.sociallimiter.app.util.PermissionUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onRequestNotifications: () -> Unit,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current

    // Permission states aren't reactive; re-read them whenever we resume.
    var permissionTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SocialLimiter") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item { PermissionsCard(context, permissionTick, onRequestNotifications) }
            item { GlobalCooldownCard(state, viewModel) }
            item {
                SectionHeader("Monitored apps", "Active session, cooldown, or idle")
            }
            if (state.monitored.isEmpty()) {
                item {
                    Text(
                        "No apps monitored yet. Add some below.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(state.monitored, key = { it.packageName }) { row ->
                MonitoredAppCard(row, viewModel)
            }
            item { SectionHeader("Add apps", "Toggle any installed app into the monitored list") }
            item { AddAppsList(state, viewModel) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionsCard(context: Context, tick: Int, onRequestNotifications: () -> Unit) {
    // `tick` recomputes these on every resume.
    @Suppress("UNUSED_EXPRESSION") tick
    val accessibility = PermissionUtils.isAccessibilityEnabled(context)
    val overlay = PermissionUtils.canDrawOverlays(context)
    val usage = PermissionUtils.hasUsageAccess(context)
    val notifications = PermissionUtils.hasNotificationPermission(context)
    val battery = PermissionUtils.isIgnoringBatteryOptimizations(context)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            PermissionRow("Accessibility service", accessibility, "Enable") {
                context.startActivity(PermissionUtils.accessibilitySettingsIntent())
            }
            PermissionRow("Display over other apps", overlay, "Grant") {
                context.startActivity(PermissionUtils.overlaySettingsIntent(context))
            }
            PermissionRow("Usage access (fallback)", usage, "Grant") {
                context.startActivity(PermissionUtils.usageAccessSettingsIntent())
            }
            PermissionRow("Notifications", notifications, "Allow", onRequestNotifications)
            PermissionRow("Ignore battery optimization", battery, "Fix") {
                context.startActivity(PermissionUtils.batteryOptimizationIntent(context))
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) Color(0xFF2E9E5B) else Color(0xFFE0902B),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!granted) {
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun GlobalCooldownCard(state: UiState, viewModel: MainViewModel) {
    var text by remember(state.defaultCooldownMinutes) {
        mutableStateOf(state.defaultCooldownMinutes.toString())
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Default cooldown", style = MaterialTheme.typography.titleMedium)
            Text(
                "Applied when the timer ends, unless an app sets its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    text.toIntOrNull()?.takeIf { it >= 1 }?.let(viewModel::setDefaultCooldown)
                }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun MonitoredAppCard(row: MonitoredRow, viewModel: MainViewModel) {
    var editingCooldown by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.packageName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = row.isEnabled, onCheckedChange = { viewModel.setEnabled(row, it) })
            }
            Text(statusText(row), style = MaterialTheme.typography.bodyMedium,
                color = statusColor(row.status))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editingCooldown = !editingCooldown }) {
                    Text(
                        row.cooldownOverride?.let { "Cooldown: ${it}m" }
                            ?: "Cooldown: default",
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.removeMonitored(row) }) { Text("Remove") }
            }
            if (editingCooldown) {
                PerAppCooldownEditor(row, viewModel) { editingCooldown = false }
            }
        }
    }
}

@Composable
private fun PerAppCooldownEditor(row: MonitoredRow, viewModel: MainViewModel, onDone: () -> Unit) {
    var text by remember { mutableStateOf(row.cooldownOverride?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(4) },
            label = { Text("Override min") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            viewModel.setPerAppCooldown(row, text.toIntOrNull()?.takeIf { it >= 1 })
            onDone()
        }) { Text("Set") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = {
            viewModel.setPerAppCooldown(row, null)
            onDone()
        }) { Text("Clear") }
    }
}

@Composable
private fun AddAppsList(state: UiState, viewModel: MainViewModel) {
    var query by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val filtered = remember(query, state.installedApps) {
                if (query.isBlank()) state.installedApps
                else state.installedApps.filter {
                    it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
            }
            filtered.take(200).forEach { app ->
                val isMonitored = app.packageName in state.monitoredPackages
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(
                        checked = isMonitored,
                        onCheckedChange = { viewModel.setMonitored(app, it) },
                    )
                }
            }
        }
    }
}

private fun statusColor(status: AppStatus): Color = when (status) {
    AppStatus.COOLDOWN -> Color(0xFFE0902B)
    AppStatus.ACTIVE_SESSION -> Color(0xFF2E9E5B)
    AppStatus.IDLE -> Color(0xFF7C8A99)
}

private fun statusText(row: MonitoredRow): String = when (row.status) {
    AppStatus.IDLE -> if (row.isEnabled) "Idle" else "Disabled"
    AppStatus.ACTIVE_SESSION -> "Active session — ${formatRemaining(row.remainingMillis)} left"
    AppStatus.COOLDOWN -> "In cooldown — ${formatRemaining(row.remainingMillis)} remaining"
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
