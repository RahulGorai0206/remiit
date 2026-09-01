package com.rahulgorai.remiit.ui.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.DisposableEffect
import com.rahulgorai.remiit.ui.components.BorderedIconButton
import com.rahulgorai.remiit.ui.components.SecondaryButton
import com.rahulgorai.remiit.ui.theme.RemiitBorders
import com.rahulgorai.remiit.util.Permissions
import com.rahulgorai.remiit.util.openSettings

private data class PermissionRow(
    val title: String,
    /** What stops working without it — the reason to care, not the API name. */
    val whyItMatters: String,
    val granted: Boolean,
    val onFix: () -> Unit,
)

/**
 * Live grant status for everything the app needs.
 *
 * This screen exists because every missing grant in this app fails silently:
 * the rule looks saved and enabled, and simply never fires. Listing them with
 * the consequence spelled out is the only way a user can tell a broken setup
 * from a working one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Most of these are granted in system Settings, not by a dialog, so the
    // screen has to re-read them on resume or it shows stale state after the
    // user comes back.
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    val rows = remember(refreshKey) {
        listOf(
            PermissionRow(
                title = "Notifications",
                whyItMatters = "Without this, no reminder can be shown at all.",
                granted = Permissions.hasNotifications(context),
                onFix = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ),
            PermissionRow(
                title = "Exact alarms",
                whyItMatters = "Time reminders still arrive without it, but the system " +
                    "may delay them by minutes to save battery.",
                granted = Permissions.canScheduleExactAlarms(context),
                onFix = { context.openSettings(Permissions.exactAlarmSettings(context)) },
            ),
            PermissionRow(
                title = "Full-screen reminders",
                whyItMatters = "Required for banner and alarm modes. Without it they " +
                    "quietly become ordinary notifications.",
                granted = Permissions.canUseFullScreenIntent(context),
                onFix = { context.openSettings(Permissions.fullScreenIntentSettings(context)) },
            ),
            PermissionRow(
                title = "Location",
                whyItMatters = "Needed for place rules — and also to read a Wi-Fi " +
                    "network name, so Wi-Fi rules need it too.",
                granted = Permissions.hasFineLocation(context),
                onFix = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
            ),
            PermissionRow(
                title = "Background location",
                whyItMatters = "Lets place rules fire while the app is closed. " +
                    "Must be granted after ordinary location, as \"Allow all the time\".",
                granted = Permissions.hasBackgroundLocation(context),
                onFix = {
                    // Android requires foreground location first; requesting
                    // background before it is granted is an immediate denial.
                    if (Permissions.hasFineLocation(context)) {
                        backgroundLocationLauncher
                            .launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        locationLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        )
                    }
                },
            ),
            PermissionRow(
                title = "Location services on",
                whyItMatters = "A device-wide switch. With it off, both place and " +
                    "Wi-Fi rules stop matching regardless of permissions.",
                granted = Permissions.areLocationServicesEnabled(context),
                onFix = { context.openSettings(Permissions.locationSettings()) },
            ),
            PermissionRow(
                title = "Usage access",
                whyItMatters = "The only way Android lets an app notice another app " +
                    "opening. Without it, app-launch rules never fire.",
                granted = Permissions.hasUsageAccess(context),
                onFix = { context.openSettings(Permissions.usageAccessSettings()) },
            ),
            PermissionRow(
                title = "Unrestricted battery",
                whyItMatters = "Battery optimisation is the usual reason reminders " +
                    "work for a day and then stop.",
                granted = Permissions.isIgnoringBatteryOptimizations(context),
                onFix = { context.openSettings(Permissions.batteryOptimizationSettings(context)) },
            ),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    BorderedIconButton(
                        icon = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Every missing grant below makes some rules fail silently " +
                        "rather than showing an error.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(items = rows, key = { it.title }) { row ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (row.granted) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    // Only the failures are outlined. Ringing every card would
                    // make the outline mean nothing; here it means "this one".
                    border = if (row.granted) null else RemiitBorders.error(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (row.granted) Icons.Filled.CheckCircle
                                else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = if (row.granted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.fillMaxWidth(0.04f))
                            Text(
                                text = row.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = row.whyItMatters,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.granted) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onErrorContainer,
                        )
                        if (!row.granted) {
                            Spacer(Modifier.height(14.dp))
                            SecondaryButton(
                                text = "Grant",
                                onClick = row.onFix,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
