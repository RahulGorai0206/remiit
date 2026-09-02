package com.rahulgorai.remiit.ui.builder

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.TriggerKind
import com.rahulgorai.remiit.ui.components.TertiaryButton
import com.rahulgorai.remiit.ui.theme.RemiitBorders
import com.rahulgorai.remiit.util.Permissions
import com.rahulgorai.remiit.util.openSettings

/**
 * Whether one feature of a rule can actually work right now.
 *
 * Every trigger kind and every delivery mode depends on a grant that the user
 * makes somewhere else, and the failure mode without one is the worst kind:
 * the rule saves, looks enabled, and silently never fires. Stating the
 * requirement at the moment the choice is made is the only place it can be
 * acted on while it still means something.
 */
data class FeatureGate(
    val satisfied: Boolean,
    /** What stops working, in terms of the rule rather than the API. */
    val problem: String,
    /** Label for the action that resolves it. */
    val actionLabel: String,
    val onFix: () -> Unit,
    /**
     * True when the feature still works in a reduced form. A time rule without
     * exact alarms still fires, just late; an app-launch rule without usage
     * access does nothing at all. The two deserve different words.
     */
    val degradedOnly: Boolean = false,
)

/**
 * Re-reads grants whenever the screen comes back.
 *
 * Almost every one of these is granted in system Settings rather than by a
 * dialog, so nothing tells the app it changed — without this the builder keeps
 * showing an error for a permission the user just granted.
 */
@Composable
private fun rememberGrantEpoch(): Int {
    var epoch by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) epoch++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return epoch
}

@Composable
fun rememberTriggerGate(kind: TriggerKind): FeatureGate {
    val context = LocalContext.current
    val epoch = rememberGrantEpoch()

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    return remember(kind, epoch) { triggerGate(context, kind, locationLauncher::launch) }
}

private fun triggerGate(
    context: Context,
    kind: TriggerKind,
    requestLocation: (Array<String>) -> Unit,
): FeatureGate = when (kind) {
    TriggerKind.TIME -> FeatureGate(
        satisfied = Permissions.canScheduleExactAlarms(context),
        problem = "Without exact alarms Android may delay this by several minutes " +
            "to save battery.",
        actionLabel = "Allow exact alarms",
        onFix = { context.openSettings(Permissions.exactAlarmSettings(context)) },
        // The rule still fires, just not on the minute.
        degradedOnly = true,
    )

    TriggerKind.WIFI -> when {
        !Permissions.hasFineLocation(context) -> FeatureGate(
            satisfied = false,
            problem = "Reading a Wi-Fi network name needs location permission. " +
                "Without it this rule can never match.",
            actionLabel = "Grant location",
            onFix = {
                requestLocation(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
        )

        !Permissions.areLocationServicesEnabled(context) -> FeatureGate(
            satisfied = false,
            problem = "Location services are off, so Wi-Fi network names are hidden " +
                "and this rule can never match.",
            actionLabel = "Turn on location",
            onFix = { context.openSettings(Permissions.locationSettings()) },
        )

        else -> Satisfied
    }

    TriggerKind.LOCATION -> when {
        !Permissions.hasFineLocation(context) -> FeatureGate(
            satisfied = false,
            problem = "Place rules need location permission.",
            actionLabel = "Grant location",
            onFix = {
                requestLocation(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
        )

        !Permissions.hasBackgroundLocation(context) -> FeatureGate(
            satisfied = false,
            problem = "Without \"Allow all the time\" this only works while Remiit is " +
                "open, which is not what a place rule is for.",
            actionLabel = "Allow all the time",
            onFix = { context.openSettings(Permissions.appDetailsSettings(context)) },
        )

        !Permissions.areLocationServicesEnabled(context) -> FeatureGate(
            satisfied = false,
            problem = "Location services are off, so no place rule can match.",
            actionLabel = "Turn on location",
            onFix = { context.openSettings(Permissions.locationSettings()) },
        )

        else -> Satisfied
    }

    TriggerKind.APP_LAUNCH -> FeatureGate(
        satisfied = Permissions.hasUsageAccess(context),
        problem = "Usage access is the only way Android lets an app notice another " +
            "app opening. Without it this rule never fires.",
        actionLabel = "Grant usage access",
        onFix = { context.openSettings(Permissions.usageAccessSettings()) },
    )
}

@Composable
fun rememberDeliveryGate(mode: DeliveryMode): FeatureGate {
    val context = LocalContext.current
    val epoch = rememberGrantEpoch()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    return remember(mode, epoch) {
        when {
            !Permissions.hasNotifications(context) -> FeatureGate(
                satisfied = false,
                problem = "Without notification permission nothing can be shown at all.",
                actionLabel = "Allow notifications",
                onFix = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )

            mode == DeliveryMode.NOTIFICATION -> Satisfied

            !Permissions.canUseFullScreenIntent(context) -> FeatureGate(
                satisfied = false,
                problem = "Full-screen reminders are blocked for this app, so this " +
                    "arrives as an ordinary notification.",
                actionLabel = "Allow full screen",
                onFix = { context.openSettings(Permissions.fullScreenIntentSettings(context)) },
            )

            // Not a hard failure: locked-screen delivery works either way. What
            // is missing is the takeover on a phone already in use, which is
            // usually the reason someone picked this mode.
            !Permissions.canDrawOverlays(context) -> FeatureGate(
                satisfied = false,
                problem = "Without \"Display over other apps\" this only takes over the " +
                    "screen when the phone is locked. Unlocked, it arrives as a " +
                    "notification you have to tap.",
                actionLabel = "Allow display over apps",
                onFix = { context.openSettings(Permissions.overlaySettings(context)) },
            )

            else -> Satisfied
        }
    }
}

private val Satisfied = FeatureGate(
    satisfied = true,
    problem = "",
    actionLabel = "",
    onFix = {},
)

/**
 * The inline error a gate produces.
 *
 * Bordered and error-toned rather than a quiet hint, because it is describing a
 * rule that will not do what it says. Carries its own fix so the user never has
 * to go hunting for which of eight permissions this was.
 */
@Composable
fun GateWarning(gate: FeatureGate, modifier: Modifier = Modifier) {
    if (gate.satisfied) return

    val tone = if (gate.degradedOnly) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.medium,
            )
            .border(
                RemiitBorders.CONTAINER_WIDTH,
                tone.copy(alpha = 0.6f),
                MaterialTheme.shapes.medium,
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = gate.problem,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        TertiaryButton(text = gate.actionLabel, onClick = gate.onFix)
    }
}
