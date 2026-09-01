package com.rahulgorai.remiit.ui.builder

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rahulgorai.remiit.data.model.LocationEvent
import com.rahulgorai.remiit.data.model.MINUTES_PER_DAY
import com.rahulgorai.remiit.data.model.Recurrence
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.data.model.WifiEvent
import com.rahulgorai.remiit.trigger.location.LocationTriggerMonitor
import com.rahulgorai.remiit.trigger.wifi.WifiNetworks
import com.rahulgorai.remiit.ui.components.PrimaryButton
import com.rahulgorai.remiit.ui.components.SecondaryButton
import com.rahulgorai.remiit.ui.components.TertiaryButton
import com.rahulgorai.remiit.ui.theme.RemiitBorders
import com.rahulgorai.remiit.util.Permissions
import com.rahulgorai.remiit.util.openSettings
import kotlinx.coroutines.tasks.await

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

private enum class RecurrenceKind(val label: String) {
    DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), INTERVAL("Repeat")
}

/** Editor for a [Trigger.Time]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTriggerEditor(
    initial: Trigger.Time?,
    newId: () -> String,
    onConfirm: (Trigger.Time) -> Unit,
) {
    val existing = initial?.recurrence
    var kind by remember {
        mutableStateOf(
            when (existing) {
                is Recurrence.Weekly -> RecurrenceKind.WEEKLY
                is Recurrence.Monthly -> RecurrenceKind.MONTHLY
                is Recurrence.Interval -> RecurrenceKind.INTERVAL
                else -> RecurrenceKind.DAILY
            }
        )
    }

    val initialMinute = when (existing) {
        is Recurrence.Daily -> existing.minuteOfDay
        is Recurrence.Weekly -> existing.minuteOfDay
        is Recurrence.Monthly -> existing.minuteOfDay
        else -> 9 * 60
    }
    val timeState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = false,
    )

    var days by remember {
        mutableStateOf((existing as? Recurrence.Weekly)?.daysOfWeek ?: setOf(1, 2, 3, 4, 5))
    }
    var dayOfMonth by remember {
        mutableStateOf(((existing as? Recurrence.Monthly)?.dayOfMonth ?: 1).toString())
    }
    val interval = existing as? Recurrence.Interval
    var everyMinutes by remember { mutableStateOf((interval?.everyMinutes ?: 60).toFloat()) }
    var windowStart by remember { mutableStateOf((interval?.startMinuteOfDay ?: 9 * 60).toFloat()) }
    var windowEnd by remember { mutableStateOf((interval?.endMinuteOfDay ?: 18 * 60).toFloat()) }

    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("When", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurrenceKind.entries.forEach { option ->
                FilterChip(
                    selected = kind == option,
                    onClick = { kind = option },
                    label = { Text(option.label) },
                    border = RemiitBorders.interactive(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when (kind) {
            RecurrenceKind.DAILY -> TimePicker(state = timeState)

            RecurrenceKind.WEEKLY -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        val iso = index + 1
                        FilterChip(
                            selected = iso in days,
                            onClick = {
                                days = if (iso in days) days - iso else days + iso
                            },
                            label = { Text(label) },
                            border = RemiitBorders.interactive(),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TimePicker(state = timeState)
            }

            RecurrenceKind.MONTHLY -> {
                OutlinedTextField(
                    value = dayOfMonth,
                    onValueChange = { dayOfMonth = it.filter(Char::isDigit).take(2) },
                    label = { Text("Day of month") },
                    supportingText = {
                        Text("31 still fires in February — it clamps to the last day.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                TimePicker(state = timeState)
            }

            RecurrenceKind.INTERVAL -> {
                Text(
                    "Every ${formatMinutes(everyMinutes.toInt())}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = everyMinutes,
                    onValueChange = { everyMinutes = it },
                    valueRange = 5f..480f,
                    steps = 0,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Between ${clock(windowStart.toInt())} and ${clock(windowEnd.toInt())}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = windowStart,
                    onValueChange = { windowStart = it.coerceAtMost(windowEnd - 30f) },
                    valueRange = 0f..(MINUTES_PER_DAY - 1).toFloat(),
                )
                Slider(
                    value = windowEnd,
                    onValueChange = { windowEnd = it.coerceAtLeast(windowStart + 30f) },
                    valueRange = 0f..(MINUTES_PER_DAY - 1).toFloat(),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = if (initial == null) "Add trigger" else "Update trigger",
            onClick = {
                val minuteOfDay = timeState.hour * 60 + timeState.minute
                val recurrence = when (kind) {
                    RecurrenceKind.DAILY -> Recurrence.Daily(minuteOfDay)
                    RecurrenceKind.WEEKLY -> Recurrence.Weekly(days, minuteOfDay)
                    RecurrenceKind.MONTHLY -> Recurrence.Monthly(
                        dayOfMonth = dayOfMonth.toIntOrNull()?.coerceIn(1, 31) ?: 1,
                        minuteOfDay = minuteOfDay,
                    )
                    RecurrenceKind.INTERVAL -> Recurrence.Interval(
                        everyMinutes = everyMinutes.toInt().coerceAtLeast(1),
                        startMinuteOfDay = windowStart.toInt(),
                        endMinuteOfDay = windowEnd.toInt(),
                    )
                }
                onConfirm(
                    Trigger.Time(id = initial?.id ?: newId(), recurrence = recurrence)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Editor for a [Trigger.Wifi].
 *
 * The SSID is still free text — a rule can name a network that is nowhere near
 * you — but typing one exactly is error-prone, so the networks in range are
 * offered as a picker. Android has not let an app read the phone's *saved*
 * networks since version 10, so "in range now" plus "already used in a rule" is
 * as close to that list as it is possible to get.
 */
@Composable
fun WifiTriggerEditor(
    initial: Trigger.Wifi?,
    knownSsids: Set<String>,
    scan: WifiScanState,
    newId: () -> String,
    onScan: () -> Unit,
    onConfirm: (Trigger.Wifi) -> Unit,
) {
    val context = LocalContext.current
    var ssid by remember { mutableStateOf(initial?.ssid.orEmpty()) }
    var event by remember { mutableStateOf(initial?.event ?: WifiEvent.CONNECTED) }

    val currentSsid = remember { WifiNetworks.currentSsid(context) }
    val canList = remember(scan.canList) { WifiNetworks.canListNetworks(context) }

    // Requested here as well as on the permissions screen, because this is the
    // one moment the user can see what it buys them. Location is genuinely what
    // Wi-Fi scanning needs: NEARBY_WIFI_DEVICES covers Aware, P2P, RTT and
    // hotspot, and is not accepted by startScan or getScanResults.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.any { it }) onScan() }

    // Scan on open: an empty picker that needs a button press first reads as
    // broken rather than as merely idle.
    LaunchedEffect(Unit) { if (canList) onScan() }

    val choices = remember(currentSsid, knownSsids, scan.ssids) {
        (listOfNotNull(currentSsid) + scan.ssids + knownSsids).distinct()
    }

    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("Wi-Fi network", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("Network name (SSID)") },
            supportingText = {
                if (!Permissions.hasFineLocation(context)) {
                    // Not a warning about the picker — a warning that the rule
                    // will never fire, which is far from obvious.
                    Text("Location permission is required to detect Wi-Fi networks.")
                } else if (!Permissions.areLocationServicesEnabled(context)) {
                    Text("Turn location services on, or Wi-Fi rules cannot match.")
                }
            },
            isError = ssid.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Networks in range", style = MaterialTheme.typography.titleMedium)
            TertiaryButton(
                text = if (scan.scanning) "Scanning…" else "Rescan",
                onClick = onScan,
                enabled = canList && !scan.scanning,
            )
        }

        if (scan.scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }

        if (choices.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { candidate ->
                    FilterChip(
                        selected = ssid == candidate,
                        onClick = { ssid = candidate },
                        label = { Text(candidate, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        border = RemiitBorders.interactive(),
                    )
                }
            }
        }

        val locationMissing = !Permissions.hasFineLocation(context)
        val hint = when {
            locationMissing -> "Listing networks needs location permission — the same one " +
                "the rule itself needs to read a network name."
            !Permissions.areLocationServicesEnabled(context) ->
                "Turn location services on to list networks."
            !scan.wifiEnabled -> "Wi-Fi is off, so no networks can be listed. " +
                "You can still type a name."
            scan.scanned && choices.isEmpty() -> "No networks found. Android rate-limits " +
                "scans to four every two minutes, so try again shortly."
            else -> null
        }
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (locationMissing) {
                SecondaryButton(
                    text = "Grant location",
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!Permissions.areLocationServicesEnabled(context)) {
                SecondaryButton(
                    text = "Open location settings",
                    onClick = { context.openSettings(Permissions.locationSettings()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Android does not let apps read your phone's saved networks, so this " +
                "lists what is in range plus networks you have used in a rule before.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Text("Trigger on", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiEvent.entries.forEach { option ->
                FilterChip(
                    selected = event == option,
                    onClick = { event = option },
                    label = {
                        Text(if (option == WifiEvent.CONNECTED) "Connecting" else "Disconnecting")
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = if (initial == null) "Add trigger" else "Update trigger",
            onClick = {
                onConfirm(
                    Trigger.Wifi(id = initial?.id ?: newId(), ssid = ssid.trim(), event = event)
                )
            },
            enabled = ssid.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Editor for a [Trigger.Location]. */
@SuppressLint("MissingPermission")
@Composable
fun LocationTriggerEditor(
    initial: Trigger.Location?,
    newId: () -> String,
    onConfirm: (Trigger.Location) -> Unit,
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var latitude by remember { mutableStateOf(initial?.latitude) }
    var longitude by remember { mutableStateOf(initial?.longitude) }
    var radius by remember {
        mutableStateOf(initial?.radiusMeters ?: LocationTriggerMonitor.MIN_RADIUS_METERS * 1.5f)
    }
    var event by remember { mutableStateOf(initial?.event ?: LocationEvent.ENTER) }
    var dwellMinutes by remember { mutableStateOf((initial?.dwellMinutes ?: 5).toString()) }
    var locating by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("Place", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Name (e.g. Office)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = latitude?.let { lat ->
                "%.5f, %.5f".format(lat, longitude ?: 0.0)
            } ?: "No location set",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        SecondaryButton(
            text = if (locating) "Getting location…" else "Use my current location",
            onClick = { locating = true },
            enabled = !locating && Permissions.hasFineLocation(context),
            modifier = Modifier.fillMaxWidth(),
        )

        if (locating) {
            LaunchedEffect(Unit) {
                // A single high-accuracy fix rather than a location subscription:
                // this is a one-off "where am I" for rule setup, and holding a
                // subscription open would be a needless battery cost.
                runCatching {
                    LocationServices.getFusedLocationProviderClient(context)
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .await()
                }.getOrNull()?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                }
                locating = false
            }
        }

        if (!Permissions.hasBackgroundLocation(context)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Background location is required for place rules to fire when the " +
                    "app is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = radius,
            onValueChange = { radius = it },
            // Floor at the platform's practical minimum: below this, fused
            // location accuracy alone produces constant false transitions.
            valueRange = LocationTriggerMonitor.MIN_RADIUS_METERS..1_000f,
        )

        Spacer(Modifier.height(8.dp))
        Text("Trigger on", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocationEvent.entries.forEach { option ->
                FilterChip(
                    selected = event == option,
                    onClick = { event = option },
                    label = {
                        Text(
                            when (option) {
                                LocationEvent.ENTER -> "Arriving"
                                LocationEvent.EXIT -> "Leaving"
                                LocationEvent.DWELL -> "Staying"
                            }
                        )
                    },
                )
            }
        }

        if (event == LocationEvent.DWELL) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dwellMinutes,
                onValueChange = { dwellMinutes = it.filter(Char::isDigit).take(3) },
                label = { Text("Minutes before firing") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = if (initial == null) "Add trigger" else "Update trigger",
            onClick = {
                val lat = latitude ?: return@PrimaryButton
                val lng = longitude ?: return@PrimaryButton
                onConfirm(
                    Trigger.Location(
                        id = initial?.id ?: newId(),
                        latitude = lat,
                        longitude = lng,
                        radiusMeters = radius,
                        event = event,
                        label = label.trim(),
                        dwellMinutes = dwellMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                    )
                )
            },
            enabled = latitude != null && longitude != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class InstalledApp(val packageName: String, val label: String)

/** Editor for a [Trigger.AppLaunch]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLaunchTriggerEditor(
    initial: Trigger.AppLaunch?,
    newId: () -> String,
    onConfirm: (Trigger.AppLaunch) -> Unit,
) {
    val context = LocalContext.current
    var anyApp by remember { mutableStateOf(initial?.packages.isNullOrEmpty()) }
    var selected by remember { mutableStateOf(initial?.packages ?: emptySet()) }
    var query by remember { mutableStateOf("") }

    // Only launchable apps: matching against system packages the user can never
    // "open" would make the picker useless.
    val apps = remember {
        val pm = context.packageManager
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { info: ApplicationInfo ->
                    info.packageName != context.packageName &&
                        pm.getLaunchIntentForPackage(info.packageName) != null
                }
                .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("Apps", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = anyApp, onCheckedChange = { anyApp = it })
            Column {
                Text("Any app", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Pair this with a cooldown, or it fires constantly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!anyApp) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(items = filtered, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = { Text(app.label) },
                        leadingContent = {
                            Checkbox(
                                checked = app.packageName in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + app.packageName
                                    else selected - app.packageName
                                },
                            )
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = if (initial == null) "Add trigger" else "Update trigger",
            onClick = {
                onConfirm(
                    Trigger.AppLaunch(
                        id = initial?.id ?: newId(),
                        packages = if (anyApp) emptySet() else selected,
                        excludes = initial?.excludes ?: emptySet(),
                    )
                )
            },
            enabled = anyApp || selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun clock(minuteOfDay: Int): String {
    val m = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    return "%02d:%02d".format(m / 60, m % 60)
}

internal fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
