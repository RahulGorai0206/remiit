package com.rahulgorai.remiit.ui.reminder

import androidx.compose.animation.core.RepeatMode
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rahulgorai.remiit.R
import com.rahulgorai.remiit.ui.theme.RemiitBorders
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
    // Grows out of the middle of the screen rather than being switched on.
    //
    // The reminder arrives over whatever you were doing, so it needs a moment
    // that says "this came from somewhere" — a surface appearing fully formed
    // reads as a glitch. Scaling from the centre while the backdrop fades is
    // the cheapest motion that does that: one transform on one layer, which
    // matters because this is often the first thing drawn after a wake-up.
    val entrance = remember { MutableTransitionState(false) }
    LaunchedEffect(rule.id) { entrance.targetState = true }

    // Answering used to tear the window down in the same frame, so the exit
    // transition existed but never had time to run — the reminder simply
    // vanished. The response is held here instead and fired once the surface
    // has finished leaving, which is what lets the exit mirror the entrance.
    var pendingAnswer by remember { mutableStateOf<(() -> Unit)?>(null) }
    val leave: (() -> Unit) -> Unit = { answer ->
        // Guarded: a second tap during the exit must not queue a second answer.
        if (pendingAnswer == null) {
            pendingAnswer = answer
            entrance.targetState = false
        }
    }
    if (entrance.isIdle && !entrance.currentState) {
        LaunchedEffect(Unit) {
            pendingAnswer?.let { answer ->
                pendingAnswer = null
                answer()
            }
        }
    }

    // Matches the overlay window: back does not answer a reminder.
    //
    // Without this the activity path finished silently on back — no outcome
    // recorded, so the reminder sat in history as PENDING for ever. The two
    // paths disagreeing about something this basic is worse than either
    // behaviour on its own.
    BackHandler(enabled = true) { /* deliberately ignored */ }

    val complete = { leave(onComplete) }
    val incomplete = { leave(onIncomplete) }
    val snooze = { leave(onSnooze) }

    if (autoDismissSeconds > 0) {
        LaunchedEffect(rule.id, autoDismissSeconds) {
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
            }
            leave(onExpire)
        }
    }


    val scrim by animateFloatAsState(
        targetValue = if (entrance.targetState) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "overlay-scrim",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = scrim))
    ) {
    AnimatedVisibility(
        visibleState = entrance,
        enter = scaleIn(
            // Anchored to the middle, and starting close to full size — a small
            // start scale reads as a zoom rather than an arrival, and on an
            // alarm that is a second of unreadable text.
            transformOrigin = TransformOrigin.Center,
            initialScale = 0.88f,
            animationSpec = tween(durationMillis = 340, easing = OvershootEasing),
        ) + fadeIn(tween(durationMillis = 180)),
        exit = scaleOut(
            transformOrigin = TransformOrigin.Center,
            targetScale = 0.88f,
            animationSpec = tween(durationMillis = 340, easing = OvershootEasing),
        ) + fadeOut(tween(durationMillis = 180, delayMillis = 160)),
    ) {
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
                        onClick = complete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        // A reminder is answered half-awake, often in the dark.
                        // The border is what makes the two targets unmistakable
                        // when the eye has not adjusted yet.
                        border = RemiitBorders.interactive(),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.action_complete),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = incomplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = RemiitBorders.interactive(),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.action_incomplete),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    Button(
                        onClick = complete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        border = RemiitBorders.interactive(),
                    ) {
                        Text(
                            text = stringResource(R.string.action_dismiss),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                if (rule.delivery.snoozeMinutes > 0) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = snooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = BorderStroke(
                            RemiitBorders.CONTAINER_WIDTH,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Icon(Icons.Default.Snooze, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "${stringResource(R.string.action_snooze)} " +
                                "${rule.delivery.snoozeMinutes} min",
                        )
                    }
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

/**
 * A touch past the target and back, so the reminder lands rather than stopping.
 *
 * Deliberately gentle: enough to read as physical, not enough to wobble
 * something the user is meant to answer immediately.
 */
private val OvershootEasing = CubicBezierEasing(0.18f, 0.9f, 0.22f, 1.06f)
