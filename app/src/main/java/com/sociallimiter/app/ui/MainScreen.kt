package com.sociallimiter.app.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import com.sociallimiter.app.data.Schedule
import com.sociallimiter.app.util.PermissionUtils
import com.sociallimiter.app.util.TimeUtils
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

    var selectedTab by remember { mutableIntStateOf(0) }
    val dashboard by viewModel.dashboardState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "SocialLimiter" else "Usage dashboard") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Settings") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Dashboard") })
            }
            if (selectedTab == 0) {
                SettingsList(state, context, permissionTick, onRequestNotifications, viewModel)
            } else {
                DashboardScreen(
                    state = dashboard,
                    onClearHistory = viewModel::clearUsageHistory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SettingsList(
    state: UiState,
    context: Context,
    permissionTick: Int,
    onRequestNotifications: () -> Unit,
    viewModel: MainViewModel,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        state.globalBlockLabel?.let { label ->
            item { GlobalBlockBanner(label) }
        }
        item { PauseCard(state, viewModel) }
        item { PermissionsCard(context, permissionTick, onRequestNotifications) }
        item { DailyBudgetCard(state, viewModel) }
        item { GlobalCooldownCard(state, viewModel) }
        item { SchedulesCard(state, viewModel) }
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

@Composable
private fun PauseCard(state: UiState, viewModel: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.enforcementPaused) "Protection paused" else "Protection active",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.enforcementPaused) Color(0xFFE0902B) else Color(0xFF2E9E5B),
                )
                Text(
                    "Pause stops all limits without touching your settings — everything " +
                        "resumes as configured. Also toggleable from the ongoing notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = !state.enforcementPaused,
                onCheckedChange = { viewModel.setEnforcementPaused(!it) },
            )
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
private fun GlobalBlockBanner(label: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFF7A1F2B),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFFD5DB))
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color(0xFFFFE6EA),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DailyBudgetCard(state: UiState, viewModel: MainViewModel) {
    var text by remember(state.dailyBudgetMinutes) {
        mutableStateOf(state.dailyBudgetMinutes.toString())
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Daily budget", style = MaterialTheme.typography.titleMedium)
            Text(
                "Total time across all monitored apps per day. Resets at midnight.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatRemaining(state.remainingTodayMillis)} left today",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.remainingTodayMillis <= 0L) Color(0xFFE0902B) else Color(0xFF2E9E5B),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutes/day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(160.dp),
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    text.toIntOrNull()?.takeIf { it >= 1 }?.let(viewModel::setDailyBudget)
                }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun SchedulesCard(state: UiState, viewModel: MainViewModel) {
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scheduled breaks", style = MaterialTheme.typography.titleMedium)
            Text(
                "Block all monitored apps during a recurring time window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.schedules.isEmpty()) {
                Text("No schedules yet.", style = MaterialTheme.typography.bodyMedium)
            }
            state.schedules.forEach { schedule ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(scheduleDaysLabel(schedule.daysMask),
                            style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${TimeUtils.formatMinuteOfDay(schedule.startMinuteOfDay)} – " +
                                TimeUtils.formatMinuteOfDay(schedule.endMinuteOfDay),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editing = schedule; showDialog = true }) { Text("Edit") }
                    Switch(
                        checked = schedule.isEnabled,
                        onCheckedChange = { viewModel.toggleSchedule(schedule, it) },
                    )
                }
            }
            OutlinedButton(
                onClick = { editing = null; showDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add schedule") }
        }
    }

    if (showDialog) {
        ScheduleDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onDelete = editing?.let { s -> { viewModel.deleteSchedule(s); showDialog = false } },
            onSave = { schedule -> viewModel.saveSchedule(schedule); showDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(
    initial: Schedule?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (Schedule) -> Unit,
) {
    val context = LocalContext.current
    var days by remember { mutableIntStateOf(initial?.daysMask ?: WEEKDAYS_MASK) }
    var start by remember { mutableIntStateOf(initial?.startMinuteOfDay ?: 10 * 60) }
    var end by remember { mutableIntStateOf(initial?.endMinuteOfDay ?: 20 * 60) }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            current / 60, current % 60, false,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New schedule" else "Edit schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Days", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        val selected = (days and (1 shl index)) != 0
                        FilterChip(
                            selected = selected,
                            onClick = {
                                days = if (selected) days and (1 shl index).inv()
                                else days or (1 shl index)
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { days = WEEKDAYS_MASK }) { Text("Weekdays") }
                    TextButton(onClick = { days = WEEKEND_MASK }) { Text("Weekend") }
                    TextButton(onClick = { days = ALL_DAYS_MASK }) { Text("Every day") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("From", Modifier.width(52.dp))
                    OutlinedButton(onClick = { pickTime(start) { start = it } }) {
                        Text(TimeUtils.formatMinuteOfDay(start))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("to", Modifier.width(28.dp))
                    OutlinedButton(onClick = { pickTime(end) { end = it } }) {
                        Text(TimeUtils.formatMinuteOfDay(end))
                    }
                }
                if (end <= start) {
                    Text(
                        "Ends next day (crosses midnight)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days != 0,
                onClick = {
                    onSave(
                        (initial ?: Schedule(daysMask = 0, startMinuteOfDay = 0, endMinuteOfDay = 0))
                            .copy(daysMask = days, startMinuteOfDay = start, endMinuteOfDay = end),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFD05555)) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private const val WEEKDAYS_MASK = 0b0011111   // Mon..Fri
private const val WEEKEND_MASK = 0b1100000    // Sat, Sun
private const val ALL_DAYS_MASK = 0b1111111

private fun scheduleDaysLabel(mask: Int): String = when (mask) {
    ALL_DAYS_MASK -> "Every day"
    WEEKDAYS_MASK -> "Weekdays"
    WEEKEND_MASK -> "Weekends"
    else -> DAY_LABELS.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }
        .joinToString(", ").ifEmpty { "No days" }
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
