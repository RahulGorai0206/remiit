package com.rahulgorai.remiit.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulgorai.remiit.BuildConfig
import com.rahulgorai.remiit.data.prefs.ThemeMode
import com.rahulgorai.remiit.ui.components.SecondaryButton
import com.rahulgorai.remiit.ui.theme.RemiitBorders
import com.rahulgorai.remiit.ui.theme.RemiitMotion
import com.rahulgorai.remiit.util.Permissions
import com.rahulgorai.remiit.util.openSettings
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Usage access is granted in system Settings rather than by a dialog, so the
    // status has to be re-read on resume or it shows stale after the trip out.
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val usageGranted = remember(refreshKey) { Permissions.hasUsageAccess(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
            Section("Appearance") {
                RowLabel("Theme")
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            // Segmented buttons ship with a border already; this
                            // just brings it up to the app's interactive weight.
                            border = RemiitBorders.interactive(),
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.AUTO -> "Auto"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    }
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                SettingRow(
                    title = "Device accent colour",
                    subtitle = "Material You — follows your wallpaper",
                    trailing = {
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    },
                )
            }

            Section("App-launch rules") {
                Text(
                    text = "Android exposes no ordinary way for an app to notice another " +
                        "app opening. Usage access is the only route, and it is what makes " +
                        "app-launch rules work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                StatusRow(
                    granted = usageGranted,
                    grantedText = "Usage access granted",
                    missingText = "Usage access not granted — app-launch rules cannot fire",
                )
                Spacer(Modifier.height(14.dp))
                SecondaryButton(
                    text = if (usageGranted) "Open usage access" else "Grant usage access",
                    onClick = { context.openSettings(Permissions.usageAccessSettings()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Permissions") {
                NavigationRow(
                    title = "Review permissions",
                    subtitle = "See which triggers can actually fire right now",
                    onClick = onOpenPermissions,
                )
            }

            Section("About") {
                SettingRow(
                    title = "Version",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                        BuildConfig.GIT_COMMIT_HASH.take(7),
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * A titled, bordered group.
 *
 * The border is doing the grouping rather than a tonal fill, which is what
 * keeps the screen readable at low brightness — a one-step surface tint is
 * exactly the sort of separation that vanishes there.
 */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.large,
            )
            .border(RemiitBorders.container(), MaterialTheme.shapes.large)
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            RowLabel(title)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(12.dp))
            trailing()
        }
    }
}

@Composable
private fun NavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RowLabel(title)
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Grant state as a bordered pill.
 *
 * Colour carries the meaning here, so the icon repeats it: a tick against a
 * cross rather than green against red alone.
 */
@Composable
private fun StatusRow(granted: Boolean, grantedText: String, missingText: String) {
    val accent by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        animationSpec = RemiitMotion.effects(),
        label = "status-color",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .border(RemiitBorders.CONTAINER_WIDTH, accent, CircleShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (granted) grantedText else missingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
