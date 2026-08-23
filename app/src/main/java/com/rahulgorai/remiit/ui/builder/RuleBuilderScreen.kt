package com.rahulgorai.remiit.ui.builder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.MatchMode
import com.rahulgorai.remiit.data.model.QuietHours
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.data.model.TriggerKind
import com.rahulgorai.remiit.data.model.kind
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.ui.components.iconFor
import com.rahulgorai.remiit.ui.components.label
import org.koin.androidx.compose.koinViewModel

private sealed interface SheetTarget {
    data class Time(val existing: Trigger.Time?) : SheetTarget
    data class Wifi(val existing: Trigger.Wifi?) : SheetTarget
    data class Location(val existing: Trigger.Location?) : SheetTarget
    data class AppLaunch(val existing: Trigger.AppLaunch?) : SheetTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleBuilderScreen(
    ruleId: String?,
    onDone: () -> Unit,
    viewModel: RuleBuilderViewModel = koinViewModel(),
) {
    LaunchedEffect(ruleId) { viewModel.load(ruleId) }

    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId == null) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (ruleId != null) {
                        IconButton(onClick = { viewModel.delete(onDone) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Task") },
                placeholder = { Text("Drink water") },
                isError = draft.title.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.body,
                onValueChange = viewModel::setBody,
                label = { Text("Details (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            SectionHeader("Triggers")

            draft.triggers.forEach { trigger ->
                TriggerRow(
                    trigger = trigger,
                    onEdit = {
                        sheet = when (trigger) {
                            is Trigger.Time -> SheetTarget.Time(trigger)
                            is Trigger.Wifi -> SheetTarget.Wifi(trigger)
                            is Trigger.Location -> SheetTarget.Location(trigger)
                            is Trigger.AppLaunch -> SheetTarget.AppLaunch(trigger)
                        }
                    },
                    onRemove = { viewModel.removeTrigger(trigger.id) },
                )
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerKind.entries.forEach { kind ->
                    AssistChip(
                        onClick = {
                            sheet = when (kind) {
                                TriggerKind.TIME -> SheetTarget.Time(null)
                                TriggerKind.WIFI -> SheetTarget.Wifi(null)
                                TriggerKind.LOCATION -> SheetTarget.Location(null)
                                TriggerKind.APP_LAUNCH -> SheetTarget.AppLaunch(null)
                            }
                        },
                        label = { Text(kind.label()) },
                        leadingIcon = {
                            Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                        },
                    )
                }
            }

            // Only meaningful with more than one trigger — with one, ANY and ALL
            // are the same thing.
            AnimatedVisibility(visible = draft.triggers.size > 1) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text("Fire when", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        MatchMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = draft.match == mode,
                                onClick = { viewModel.setMatch(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index, MatchMode.entries.size
                                ),
                                label = {
                                    Text(if (mode == MatchMode.ANY) "Any trigger" else "All triggers")
                                },
                            )
                        }
                    }
                    if (draft.match == MatchMode.ALL) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "All triggers must happen within " +
                                "${draft.constraints.matchWindowMinutes} minutes of each other.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("How it reaches you")

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DeliveryMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = draft.delivery.mode == mode,
                        onClick = { viewModel.setDelivery(draft.delivery.copy(mode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, DeliveryMode.entries.size),
                        icon = { Icon(iconFor(mode), contentDescription = null, Modifier.size(18.dp)) },
                        label = { Text(mode.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SwitchRow(
                        title = "Complete / Not done buttons",
                        subtitle = "Log whether you actually did it",
                        checked = draft.delivery.showCompleteIncomplete,
                        onCheckedChange = {
                            viewModel.setDelivery(draft.delivery.copy(showCompleteIncomplete = it))
                        },
                    )
                    SwitchRow(
                        title = "Vibrate",
                        checked = draft.delivery.vibrate,
                        onCheckedChange = {
                            viewModel.setDelivery(draft.delivery.copy(vibrate = it))
                        },
                    )
                    if (draft.delivery.mode == DeliveryMode.ALARM) {
                        SwitchRow(
                            title = "Fade in volume",
                            subtitle = "Start quiet and ramp up",
                            checked = draft.delivery.escalateVolume,
                            onCheckedChange = {
                                viewModel.setDelivery(draft.delivery.copy(escalateVolume = it))
                            },
                        )
                    }
                    SliderRow(
                        label = "Snooze: ${draft.delivery.snoozeMinutes} min",
                        value = draft.delivery.snoozeMinutes.toFloat(),
                        range = 0f..60f,
                        onValueChange = {
                            viewModel.setDelivery(draft.delivery.copy(snoozeMinutes = it.toInt()))
                        },
                    )
                    SliderRow(
                        label = draft.delivery.autoDismissSeconds.let {
                            if (it == 0) "Auto-dismiss: never" else "Auto-dismiss: ${it}s"
                        },
                        value = draft.delivery.autoDismissSeconds.toFloat(),
                        range = 0f..300f,
                        onValueChange = {
                            viewModel.setDelivery(draft.delivery.copy(autoDismissSeconds = it.toInt()))
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide limits" else "Limits & quiet hours")
            }

            AnimatedVisibility(visible = showAdvanced) {
                ConstraintsSection(
                    constraints = draft.constraints,
                    onChange = viewModel::setConstraints,
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = viewModel::preview,
                    enabled = draft.isValid,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Preview")
                }
                androidx.compose.material3.Button(
                    onClick = { viewModel.save(onDone) },
                    enabled = draft.isValid,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }

            if (!draft.isValid) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (draft.title.isBlank()) "Give the rule a task name."
                    else "Add at least one trigger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    sheet?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = sheetState,
        ) {
            val confirm: (Trigger) -> Unit = { trigger ->
                viewModel.upsertTrigger(trigger)
                sheet = null
            }
            when (target) {
                is SheetTarget.Time -> TimeTriggerEditor(
                    initial = target.existing,
                    newId = viewModel::newTriggerId,
                    onConfirm = confirm,
                )
                is SheetTarget.Wifi -> {
                    val known by viewModel.let { vm ->
                        // Known SSIDs come from settings; collected here so the
                        // sheet has them without the editor knowing about DI.
                        remember { vm }
                        rememberKnownSsids()
                    }
                    WifiTriggerEditor(
                        initial = target.existing,
                        knownSsids = known,
                        newId = viewModel::newTriggerId,
                        onConfirm = confirm,
                    )
                }
                is SheetTarget.Location -> LocationTriggerEditor(
                    initial = target.existing,
                    newId = viewModel::newTriggerId,
                    onConfirm = confirm,
                )
                is SheetTarget.AppLaunch -> AppLaunchTriggerEditor(
                    initial = target.existing,
                    newId = viewModel::newTriggerId,
                    onConfirm = confirm,
                )
            }
        }
    }
}

@Composable
private fun rememberKnownSsids(): androidx.compose.runtime.State<Set<String>> {
    val settings: com.rahulgorai.remiit.data.prefs.SettingsStore = org.koin.compose.koinInject()
    return settings.knownSsids.collectAsStateWithLifecycle(emptySet())
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun TriggerRow(trigger: Trigger, onEdit: () -> Unit, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(trigger.shortSummary()) },
        supportingContent = { Text(trigger.kind.label()) },
        leadingContent = { Icon(iconFor(trigger.kind), contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove trigger")
            }
        },
        modifier = Modifier.clickable(onClick = onEdit),
    )
}


@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun ConstraintsSection(
    constraints: com.rahulgorai.remiit.data.model.RuleConstraints,
    onChange: (com.rahulgorai.remiit.data.model.RuleConstraints) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SliderRow(
                label = constraints.cooldownMinutes.let {
                    if (it == 0) "Cooldown: none" else "Cooldown: ${formatMinutes(it)}"
                },
                value = constraints.cooldownMinutes.toFloat(),
                range = 0f..240f,
                onValueChange = { onChange(constraints.copy(cooldownMinutes = it.toInt())) },
            )
            SliderRow(
                label = constraints.maxFiresPerDay.let {
                    if (it == 0) "Max per day: unlimited" else "Max per day: $it"
                },
                value = constraints.maxFiresPerDay.toFloat(),
                range = 0f..48f,
                onValueChange = { onChange(constraints.copy(maxFiresPerDay = it.toInt())) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            val quiet = constraints.quietHours
            SwitchRow(
                title = "Quiet hours",
                subtitle = quiet?.let {
                    "Silent from ${clock(it.startMinuteOfDay)} to ${clock(it.endMinuteOfDay)}"
                } ?: "Never silent",
                checked = quiet != null,
                onCheckedChange = { enabled ->
                    onChange(
                        constraints.copy(
                            quietHours = if (enabled) QuietHours(22 * 60, 7 * 60) else null
                        )
                    )
                },
            )

            if (quiet != null) {
                SliderRow(
                    label = "Quiet from ${clock(quiet.startMinuteOfDay)}",
                    value = quiet.startMinuteOfDay.toFloat(),
                    range = 0f..1439f,
                    onValueChange = {
                        onChange(constraints.copy(quietHours = quiet.copy(startMinuteOfDay = it.toInt())))
                    },
                )
                SliderRow(
                    label = "Quiet until ${clock(quiet.endMinuteOfDay)}",
                    value = quiet.endMinuteOfDay.toFloat(),
                    range = 0f..1439f,
                    onValueChange = {
                        onChange(constraints.copy(quietHours = quiet.copy(endMinuteOfDay = it.toInt())))
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                "Active days",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                    val iso = index + 1
                    // An empty set means every day, so an all-selected UI state
                    // maps back to empty rather than to all seven.
                    val active = constraints.activeDays.isEmpty() || iso in constraints.activeDays
                    FilterChip(
                        selected = active,
                        onClick = {
                            val current = constraints.activeDays.ifEmpty { (1..7).toSet() }
                            val next = if (iso in current) current - iso else current + iso
                            onChange(
                                constraints.copy(
                                    activeDays = if (next.size == 7) emptySet() else next
                                )
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun TriggerKind.label(): String = when (this) {
    TriggerKind.TIME -> "Time"
    TriggerKind.WIFI -> "Wi-Fi"
    TriggerKind.LOCATION -> "Place"
    TriggerKind.APP_LAUNCH -> "App launch"
}
