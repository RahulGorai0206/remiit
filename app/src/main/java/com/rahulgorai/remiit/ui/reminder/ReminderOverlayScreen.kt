package com.rahulgorai.remiit.ui.reminder

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rahulgorai.remiit.R
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.ReminderRule
import kotlinx.coroutines.delay

/**
 * The reminder itself: what to do, and two unmissable ways to answer.
 *
 * Sized for a glance rather than for density. The buttons are deliberately
 * oversized — this screen gets used half-asleep, at arm's length, or while
 * walking, and a mis-tap logs the wrong outcome.
 */
@Composable
fun ReminderOverlayScreen(
    rule: ReminderRule,
    triggerSummary: String,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit,
    onSnooze: () -> Unit,
    onExpire: () -> Unit,
) {
    val isAlarm = rule.delivery.mode == DeliveryMode.ALARM

    // Auto-dismiss countdown. 0 means wait indefinitely, which is the default
    // for anything that is not an alarm.
    val autoDismissSeconds = rule.delivery.autoDismissSeconds
    var remaining by remember { mutableIntStateOf(autoDismissSeconds) }
    if (autoDismissSeconds > 0) {
        LaunchedEffect(rule.id, autoDismissSeconds) {
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
            }
            onExpire()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PulsingHalo(active = isAlarm)

                Spacer(Modifier.height(32.dp))

                Text(
                    text = triggerSummary.ifBlank { rule.title },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = rule.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                if (rule.body.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = rule.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                if (remaining > 0) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Closing in ${remaining}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (rule.delivery.showCompleteIncomplete) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringRes(R.string.action_complete),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onIncomplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringRes(R.string.action_incomplete),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            text = stringRes(R.string.action_dismiss),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                if (rule.delivery.snoozeMinutes > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onSnooze) {
                            Icon(Icons.Default.Snooze, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "${stringRes(R.string.action_snooze)} " +
                                    "${rule.delivery.snoozeMinutes} min",
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Breathing accent ring behind the icon.
 *
 * Animated only in alarm mode. A banner is a glance; an alarm has to keep
 * signalling that it is still waiting for an answer, and continuous motion does
 * that without adding noise.
 */
@Composable
private fun PulsingHalo(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "halo")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-scale",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (active) 0.05f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-alpha",
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .scale(scale)
                .alpha(glow)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun stringRes(id: Int): String =
    androidx.compose.ui.platform.LocalContext.current.getString(id)
