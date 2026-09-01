package com.rahulgorai.remiit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.OpenInFull
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.TriggerKind
import com.rahulgorai.remiit.data.model.kind
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.ui.SharedKeys
import com.rahulgorai.remiit.ui.sharedContainer
import com.rahulgorai.remiit.ui.sharedTitle
import com.rahulgorai.remiit.ui.theme.RemiitBorders
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

/**
 * One rule in the list.
 *
 * The card is also the start of the editor's opening transition — it carries a
 * [sharedContainer] keyed on the rule id, so tapping it grows this exact
 * surface into the builder rather than pushing a new screen over the top.
 */
@Composable
fun RuleCard(
    rule: ReminderRule,
    nextFire: Instant?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraLarge

    // Disabled rules recede rather than disappear, so the list still reads as
    // "these are my rules" with the inactive ones visibly dimmed. Animated so
    // the toggle has a settling motion instead of a hard cut.
    val containerColor by animateColorAsState(
        targetValue = if (rule.isEnabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = RemiitMotion.effects(),
        label = "card-color",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (rule.isEnabled) 1f else 0.55f,
        animationSpec = RemiitMotion.effects(),
        label = "card-alpha",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .sharedContainer(SharedKeys.ruleContainer(rule.id), shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = RemiitBorders.container(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .alpha(contentAlpha)
                ) {
                    Text(
                        text = rule.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sharedTitle(SharedKeys.ruleTitle(rule.id)),
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
                Spacer(Modifier.size(12.dp))
                Switch(checked = rule.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(contentAlpha),
            ) {
                rule.triggers.forEach { trigger ->
                    MetaChip(
                        icon = iconFor(trigger.kind),
                        text = trigger.shortSummary(),
                    )
                }
                MetaChip(
                    icon = iconFor(rule.delivery.mode),
                    text = rule.delivery.mode.label(),
                    accent = true,
                )
            }

            AnimatedVisibility(
                visible = rule.isEnabled && nextFire != null,
                enter = fadeIn(RemiitMotion.effects()) + expandVertically(RemiitMotion.spatial()),
                exit = fadeOut(RemiitMotion.fastEffects()) +
                    shrinkVertically(RemiitMotion.fastSpatial()),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = nextFire?.let { "Next · ${formatNextFire(it)}" }.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * A read-only fact about a rule.
 *
 * Not an [androidx.compose.material3.AssistChip]: these are labels, not
 * controls, and a disabled chip that looks pressable but is not is worse than
 * either. Bordered so it still reads as a distinct object.
 */
@Composable
fun MetaChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val contentColor = if (accent) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .background(
                color = if (accent) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = CircleShape,
            )
            .border(RemiitBorders.container(), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
