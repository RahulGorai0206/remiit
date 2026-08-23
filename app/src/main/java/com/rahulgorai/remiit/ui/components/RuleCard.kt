package com.rahulgorai.remiit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.data.model.TriggerKind
import com.rahulgorai.remiit.data.model.kind
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.ui.theme.RemiitMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun iconFor(kind: TriggerKind): ImageVector = when (kind) {
    TriggerKind.TIME -> Icons.Filled.Schedule
    TriggerKind.WIFI -> Icons.Filled.Wifi
    TriggerKind.LOCATION -> Icons.Filled.Place
    TriggerKind.APP_LAUNCH -> Icons.Filled.Apps
}

fun iconFor(mode: DeliveryMode): ImageVector = when (mode) {
    DeliveryMode.NOTIFICATION -> Icons.Filled.NotificationsNone
    DeliveryMode.FULLSCREEN_BANNER -> Icons.Outlined.OpenInFull
    DeliveryMode.ALARM -> Icons.Filled.Alarm
}

@Composable
fun RuleCard(
    rule: ReminderRule,
    nextFire: Instant?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Disabled rules recede rather than disappear, so the list still reads as
    // "these are my rules" with the inactive ones visibly dimmed.
    val containerColor by animateColorAsState(
        targetValue = if (rule.isEnabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = RemiitMotion.effects(),
        label = "card-color",
    )

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = rule.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (rule.isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rule.body.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = rule.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(checked = rule.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rule.triggers.forEach { trigger ->
                    TriggerChip(trigger)
                }
                AssistChip(
                    onClick = onClick,
                    label = { Text(rule.delivery.mode.label()) },
                    leadingIcon = {
                        Icon(
                            iconFor(rule.delivery.mode),
                            contentDescription = null,
                            Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }

            AnimatedVisibility(visible = rule.isEnabled && nextFire != null) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = nextFire?.let { "Next: ${formatNextFire(it)}" }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TriggerChip(trigger: Trigger) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(trigger.shortSummary(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                iconFor(trigger.kind),
                contentDescription = null,
                Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

fun DeliveryMode.label(): String = when (this) {
    DeliveryMode.NOTIFICATION -> "Notification"
    DeliveryMode.FULLSCREEN_BANNER -> "Full screen"
    DeliveryMode.ALARM -> "Alarm"
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/**
 * Relative for anything imminent, absolute beyond that. "in 20 min" is more
 * useful than a clock time for the next hour; "3 Sep, 09:00" is more useful
 * than "in 240 hours".
 */
fun formatNextFire(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(now, at)
    val local = at.atZone(zone)
    return when {
        minutes < 1 -> "in under a minute"
        minutes < 60 -> "in $minutes min"
        minutes < 60 * 12 -> "${timeFormat.format(local)} (in ${minutes / 60}h)"
        local.toLocalDate() == now.atZone(zone).toLocalDate() -> timeFormat.format(local)
        else -> dateTimeFormat.format(local)
    }
}
