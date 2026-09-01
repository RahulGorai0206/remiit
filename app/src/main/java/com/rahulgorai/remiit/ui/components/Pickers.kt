package com.rahulgorai.remiit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rahulgorai.remiit.ui.theme.RemiitBorders

/** One choice in an [OptionRow]. */
data class Option<T>(val value: T, val label: String)

/**
 * A setting chosen from a handful of sensible values.
 *
 * This replaces the sliders these settings used to use, and the sliders were
 * wrong for three separate reasons. Every one of them treats zero as "off", and
 * a slider parked at zero draws as a bare thumb against an empty track — it
 * reads as broken rather than as a deliberate setting. Dragging cannot reliably
 * land on a round number, so "cooldown: 37 minutes" was easier to get than 30.
 * And a continuous control implies the whole range is meaningful, when in truth
 * nobody wants a 43-minute snooze.
 *
 * A short list of real choices is smaller, precise, readable at a glance, and
 * says what the options actually are.
 */
@Composable
fun <T> OptionRow(
    title: String,
    options: List<Option<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                    border = RemiitBorders.interactive(),
                )
            }
        }
    }
}

/**
 * A row of day toggles.
 *
 * [FlowRow] rather than [Row] deliberately: seven chips plus spacing overflow a
 * narrow screen, and a Row silently clips the overflow — which is why Sunday
 * used to be sliced in half at the right edge. Flowing wraps it onto a second
 * line instead of hiding it.
 */
@Composable
fun DayToggles(
    /** ISO day numbers, 1 = Monday. Empty means every day. */
    activeDays: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DAY_LABELS.forEachIndexed { index, label ->
            val iso = index + 1
            // An empty set means every day, so an all-selected UI state maps
            // back to empty rather than to all seven.
            val active = activeDays.isEmpty() || iso in activeDays
            FilterChip(
                selected = active,
                onClick = {
                    val current = activeDays.ifEmpty { ALL_DAYS }
                    val next = if (iso in current) current - iso else current + iso
                    // Refuse to clear the last day: a rule active on no days can
                    // never fire, which looks identical to a broken rule.
                    if (next.isEmpty()) return@FilterChip
                    onChange(if (next.size == 7) emptySet() else next)
                },
                label = { Text(label) },
                border = RemiitBorders.interactive(),
            )
        }
    }
}

/**
 * A labelled time, tapped to open a clock.
 *
 * The alternative was a slider across 1440 minutes, where one pixel is roughly
 * four minutes and hitting 22:00 exactly is luck. A time is a time; it deserves
 * a clock face.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    minuteOfDay: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        TertiaryButton(
            text = formatClock(minuteOfDay),
            onClick = { showPicker = true },
        )
    }

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TertiaryButton(
                    text = "Set",
                    onClick = {
                        onPick(state.hour * 60 + state.minute)
                        showPicker = false
                    },
                )
            },
            dismissButton = {
                TertiaryButton(text = "Cancel", onClick = { showPicker = false })
            },
            title = { Text(label) },
            text = { TimePicker(state = state) },
        )
    }
}

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val ALL_DAYS = (1..7).toSet()

private fun formatClock(minuteOfDay: Int): String {
    val m = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return "%02d:%02d".format(m / 60, m % 60)
}
