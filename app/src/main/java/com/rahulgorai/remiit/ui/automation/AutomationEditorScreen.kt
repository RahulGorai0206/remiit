package com.rahulgorai.remiit.ui.automation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rahulgorai.remiit.data.model.AutomationEdge
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.data.model.AutomationTriggerKind
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.data.model.kind
import com.rahulgorai.remiit.data.model.label
import com.rahulgorai.remiit.trigger.bluetooth.BluetoothDevices
import com.rahulgorai.remiit.trigger.location.LocationTriggerMonitor
import com.rahulgorai.remiit.trigger.wifi.WifiScanState
import com.rahulgorai.remiit.ui.builder.GateWarning
import com.rahulgorai.remiit.ui.components.BorderedIconButton
import com.rahulgorai.remiit.ui.components.Option
import com.rahulgorai.remiit.ui.components.OptionRow
import com.rahulgorai.remiit.ui.components.PrimaryButton
import com.rahulgorai.remiit.ui.components.SecondaryButton
import com.rahulgorai.remiit.ui.components.WifiNetworkPicker
import com.rahulgorai.remiit.util.Permissions
import com.rahulgorai.remiit.util.openSettings
import kotlinx.coroutines.tasks.await
import org.koin.androidx.compose.koinViewModel

/**
 * The automation editor.
 *
 * Laid out as the sentence the automation is: *when* this happens, *then* do
 * this. Both halves are on one screen rather than behind sheets — unlike a
 * rule, an automation has exactly one trigger and at most two settings, so
 * there is nothing here that needs hiding, and hiding it would only add taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationEditorScreen(
    automationId: String?,
    /** Passed through so the heading is right on the very first frame. */
    initialName: String?,
    onDone: () -> Unit,
    bottomInset: Dp,
    viewModel: AutomationEditorViewModel = koinViewModel(),
) {
    LaunchedEffect(automationId) { viewModel.load(automationId) }

    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val knownSsids by viewModel.knownSsids.collectAsStateWithLifecycle(emptySet())
    val wifiScan by viewModel.wifiScan.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isEdit = !automationId.isNullOrBlank()
    val heading = when {
        !isEdit -> "New automation"
        !initialName.isNullOrBlank() -> initialName
        draft.name.isNotBlank() -> draft.name
        else -> "Automation"
    }

    val triggerGate = rememberAutomationTriggerGate(draft.trigger.kind)
    val actionGate = rememberActionGate(draft.actions)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(heading, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    BorderedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onDone,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                },
                actions = {
                    if (isEdit) {
                        BorderedIconButton(
                            icon = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            onClick = { viewModel.delete(onDone) },
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                placeholder = { Text("Silent at the office") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Section("When") {
                OptionRow(
                    title = "Watch for",
                    options = listOf(
                        Option(AutomationTriggerKind.WIFI, "Wi-Fi"),
                        Option(AutomationTriggerKind.BLUETOOTH, "Bluetooth"),
                        Option(AutomationTriggerKind.LOCATION, "Place"),
                    ),
                    selected = draft.trigger.kind,
                    onSelect = viewModel::setTriggerKind,
                )

                Spacer(Modifier.height(18.dp))
                OptionRow(
                    title = "On",
                    options = AutomationEdge.entries.map {
                        Option(it, it.label(draft.trigger.kind))
                    },
                    selected = draft.trigger.edge,
                    onSelect = viewModel::setEdge,
                )

                Spacer(Modifier.height(18.dp))
                when (val trigger = draft.trigger) {
                    is AutomationTrigger.Wifi -> WifiPicker(
                        trigger = trigger,
                        knownSsids = knownSsids,
                        scan = wifiScan,
                        onScan = { viewModel.scanNearbyWifi(context) },
                        onChange = viewModel::setTrigger,
                    )

                    is AutomationTrigger.Bluetooth -> BluetoothPicker(
                        trigger = trigger,
                        onChange = viewModel::setTrigger,
                    )

                    is AutomationTrigger.Location -> PlacePicker(
                        trigger = trigger,
                        onChange = viewModel::setTrigger,
                    )
                }

                if (triggerGate != null) {
                    Spacer(Modifier.height(16.dp))
                    GateWarning(triggerGate)
                }
            }

            Section("Then") {
                OptionRow(
                    title = "Sound",
                    subtitle = "Leave as \"No change\" to keep the ringer as it is.",
                    options = listOf(Option<SoundSetting?>(null, "No change")) +
                        SoundSetting.entries.map { Option<SoundSetting?>(it, it.label()) },
                    selected = draft.actions.sound,
                    onSelect = viewModel::setSound,
                )

                Spacer(Modifier.height(18.dp))
                OptionRow(
                    title = "Adaptive brightness",
                    options = listOf(
                        Option<Boolean?>(null, "No change"),
                        Option<Boolean?>(true, "Turn on"),
                        Option<Boolean?>(false, "Turn off"),
                    ),
                    selected = draft.actions.autoBrightness,
                    onSelect = viewModel::setAutoBrightness,
                )

                if (actionGate != null) {
                    Spacer(Modifier.height(16.dp))
                    GateWarning(actionGate)
                }
            }

            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = "Save automation",
                onClick = { viewModel.save(onDone) },
                enabled = draft.isValid,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp + padding.calculateBottomPadding() + bottomInset))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(26.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
    )
    Column(content = content)
}

/**
 * The same network picker the rule builder uses — in-range networks, the one
 * you are on, and the ones you have named before — rather than a text field
 * and nothing else.
 */
@Composable
private fun WifiPicker(
    trigger: AutomationTrigger.Wifi,
    knownSsids: Set<String>,
    scan: WifiScanState,
    onScan: () -> Unit,
    onChange: (AutomationTrigger) -> Unit,
) {
    WifiNetworkPicker(
        ssid = trigger.ssid,
        onSsidChange = { onChange(trigger.copy(ssid = it)) },
        scan = scan,
        knownSsids = knownSsids,
        onScan = onScan,
    )
}

@Composable
private fun BluetoothPicker(
    trigger: AutomationTrigger.Bluetooth,
    onChange: (AutomationTrigger) -> Unit,
) {
    val context = LocalContext.current
    val hasPermission = Permissions.hasBluetoothConnect(context)
    val enabled = BluetoothDevices.isEnabled(context)
    // Keyed on both, so granting the permission or switching the radio on and
    // coming back re-reads the list instead of showing the empty one.
    val devices = remember(hasPermission, enabled) { BluetoothDevices.paired(context) }

    Column(Modifier.fillMaxWidth()) {
        when {
            !hasPermission -> Hint(
                "Grant the Nearby devices permission to choose from your paired devices."
            )

            !enabled -> Column {
                Hint("Bluetooth is off, so the paired-device list is unavailable.")
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Open Bluetooth settings",
                    onClick = { context.openSettings(Permissions.bluetoothSettings()) },
                )
            }

            devices.isEmpty() -> Hint(
                "No paired devices. Pair the device in Bluetooth settings first — car " +
                    "stereo, headphones, watch — then come back."
            )

            else -> OptionRow(
                title = "Device",
                options = devices.map { Option(it.address, it.name) },
                selected = trigger.address,
                onSelect = { address ->
                    onChange(
                        trigger.copy(
                            address = address,
                            // Cached now, because from Android 12 the name is
                            // unreadable without the permission — and the card
                            // still has to say which device this is about if
                            // that permission is later revoked.
                            deviceName = devices.first { it.address == address }.name,
                        )
                    )
                },
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun PlacePicker(
    trigger: AutomationTrigger.Location,
    onChange: (AutomationTrigger) -> Unit,
) {
    val context = LocalContext.current
    var locating by remember { mutableStateOf(false) }
    val isSet = trigger.latitude != 0.0 || trigger.longitude != 0.0

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = trigger.label,
            onValueChange = { onChange(trigger.copy(label = it)) },
            label = { Text("Place name (e.g. Home)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isSet) {
                "%.5f, %.5f".format(trigger.latitude, trigger.longitude)
            } else {
                "No location set"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = if (locating) "Getting location…" else "Use my current location",
            onClick = { locating = true },
            enabled = !locating && Permissions.hasFineLocation(context),
            modifier = Modifier.fillMaxWidth(),
        )

        if (locating) {
            LaunchedEffect(Unit) {
                // One high-accuracy fix, not a subscription: this is a one-off
                // "where am I" for setup, and a subscription would cost battery
                // for the whole time the screen is open.
                runCatching {
                    LocationServices.getFusedLocationProviderClient(context)
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .await()
                }.getOrNull()?.let {
                    onChange(trigger.copy(latitude = it.latitude, longitude = it.longitude))
                }
                locating = false
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Radius: ${trigger.radiusMeters.toInt()} m",
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = trigger.radiusMeters,
            onValueChange = { onChange(trigger.copy(radiusMeters = it)) },
            // The floor is not cosmetic: below it, fused-location accuracy
            // produces enter/exit transitions while the phone sits still, and
            // an automation on one of those would flip the ringer at random.
            valueRange = LocationTriggerMonitor.MIN_RADIUS_METERS..1_000f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
