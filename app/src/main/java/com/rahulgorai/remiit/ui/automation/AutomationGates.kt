package com.rahulgorai.remiit.ui.automation

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rahulgorai.remiit.automation.requiresDndAccess
import com.rahulgorai.remiit.data.model.AutomationActions
import com.rahulgorai.remiit.data.model.AutomationTriggerKind
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.ui.builder.FeatureGate
import com.rahulgorai.remiit.util.Permissions
import com.rahulgorai.remiit.util.openSettings

/**
 * Whether an automation can actually do what it says, right now.
 *
 * The same idea as the rule builder's [FeatureGate]s and for the same reason,
 * but the stakes are different enough to be worth stating. A reminder that
 * fails is a reminder the user notices they never got. An automation that
 * fails is silent in both directions: the phone simply stays loud, and there is
 * nothing to notice. So every action here is gated at the moment it is chosen,
 * and the record on the card is what catches the rest.
 */

/** Re-reads grants on resume — all of these are granted in Settings, not by a dialog. */
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

/** The gate for what this automation's actions need, or null if nothing is missing. */
@Composable
fun rememberActionGate(actions: AutomationActions): FeatureGate? {
    val context = LocalContext.current
    val epoch = rememberGrantEpoch()
    return remember(actions, epoch) { actionGate(context, actions) }
}

private fun actionGate(context: Context, actions: AutomationActions): FeatureGate? {
    val sound = actions.sound
    if (sound != null && sound.requiresDndAccess() && !Permissions.hasDndAccess(context)) {
        return FeatureGate(
            satisfied = false,
            problem = when (sound) {
                SoundSetting.SILENT ->
                    "Silencing the phone counts as a Do Not Disturb change, so Android " +
                        "needs Do Not Disturb access. Without it this automation will run " +
                        "and change nothing."

                else ->
                    "Changing Do Not Disturb needs Do Not Disturb access. Without it this " +
                        "automation will run and change nothing."
            },
            actionLabel = "Grant Do Not Disturb access",
            onFix = { context.openSettings(Permissions.dndAccessSettings()) },
        )
    }

    if (actions.autoBrightness != null && !Permissions.canWriteSystemSettings(context)) {
        return FeatureGate(
            satisfied = false,
            problem = "Changing adaptive brightness needs the \"Modify system settings\" " +
                "permission. Without it this automation will run and change nothing.",
            actionLabel = "Allow modifying settings",
            onFix = { context.openSettings(Permissions.writeSettingsSettings(context)) },
        )
    }

    return null
}

/**
 * The gate for noticing the trigger in the first place.
 *
 * Location and Wi-Fi share the reminder side's requirements exactly — reading
 * an SSID needs location permission, and a geofence needs it in the background
 * — so the wording here says what it costs an automation rather than repeating
 * the API's terms.
 */
@Composable
fun rememberAutomationTriggerGate(kind: AutomationTriggerKind): FeatureGate? {
    val context = LocalContext.current
    val epoch = rememberGrantEpoch()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    return remember(kind, epoch) { triggerGate(context, kind, permissionLauncher::launch) }
}

private fun triggerGate(
    context: Context,
    kind: AutomationTriggerKind,
    request: (Array<String>) -> Unit,
): FeatureGate? = when (kind) {
    AutomationTriggerKind.WIFI -> when {
        !Permissions.hasFineLocation(context) -> FeatureGate(
            satisfied = false,
            problem = "Reading a Wi-Fi network name needs location permission. Without it " +
                "this automation can never match.",
            actionLabel = "Grant location",
            onFix = {
                request(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
        )

        !Permissions.areLocationServicesEnabled(context) -> FeatureGate(
            satisfied = false,
            problem = "Location services are off. Android redacts Wi-Fi network names " +
                "while they are, so this automation cannot match.",
            actionLabel = "Open location settings",
            onFix = { context.openSettings(Permissions.locationSettings()) },
        )

        else -> null
    }

    AutomationTriggerKind.BLUETOOTH -> when {
        !Permissions.hasBluetoothConnect(context) -> FeatureGate(
            satisfied = false,
            problem = "Listing your paired devices needs the Nearby devices permission.",
            actionLabel = "Grant Bluetooth",
            onFix = { request(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) },
        )

        else -> null
    }

    AutomationTriggerKind.LOCATION -> when {
        !Permissions.hasFineLocation(context) -> FeatureGate(
            satisfied = false,
            problem = "A place-based automation needs location permission.",
            actionLabel = "Grant location",
            onFix = {
                request(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
        )

        !Permissions.hasBackgroundLocation(context) -> FeatureGate(
            satisfied = false,
            problem = "Android only runs a geofence while the app has background location. " +
                "Without it this automation works only while Remiit is open — which is " +
                "never, for an automation.",
            actionLabel = "Allow all the time",
            onFix = { request(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) },
        )

        else -> null
    }
}
