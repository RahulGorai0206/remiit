package com.rahulgorai.remiit.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulgorai.remiit.BuildConfig
import com.rahulgorai.remiit.data.prefs.AppLaunchDetectorKind
import com.rahulgorai.remiit.data.prefs.ThemeMode
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

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionCard("Appearance") {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
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
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Use device accent colour") },
                    supportingContent = { Text("Material You — follows your wallpaper") },
                    trailingContent = {
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("App-launch detection") {
                Text(
                    "Android has no ordinary way to know another app opened. " +
                        "Both routes work; they trade off differently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))

                DetectorOption(
                    title = "Accessibility service",
                    subtitle = "Instant, almost no battery use. Needs an accessibility grant.",
                    selected = state.detector == AppLaunchDetectorKind.ACCESSIBILITY,
                    granted = Permissions.hasAccessibilityService(context),
                    onSelect = { viewModel.setDetector(AppLaunchDetectorKind.ACCESSIBILITY) },
                    onGrant = { context.openSettings(Permissions.accessibilitySettings()) },
                )
                DetectorOption(
                    title = "Usage access",
                    subtitle = "About a second slower and keeps a background notice, " +
                        "but needs no accessibility grant.",
                    selected = state.detector == AppLaunchDetectorKind.USAGE_STATS,
                    granted = Permissions.hasUsageAccess(context),
                    onSelect = { viewModel.setDetector(AppLaunchDetectorKind.USAGE_STATS) },
                    onGrant = { context.openSettings(Permissions.usageAccessSettings()) },
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("Permissions") {
                ListItem(
                    headlineContent = { Text("Review permissions") },
                    supportingContent = {
                        Text("See which triggers can actually fire right now")
                    },
                    modifier = Modifier.clickable(onClick = onOpenPermissions),
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("About") {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = {
                        Text(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                                BuildConfig.GIT_COMMIT_HASH.take(7)
                        )
                    },
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { content() }
        }
    }
}

@Composable
private fun DetectorOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    granted: Boolean,
    onSelect: () -> Unit,
    onGrant: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(subtitle)
                // Selecting a detector is not the same as it working. Surfacing
                // the grant state here is what stops a user believing their
                // app-launch rules are live when they are not.
                if (selected && !granted) {
                    TextButton(onClick = onGrant, contentPadding = androidx.compose.foundation.layout
                        .PaddingValues(0.dp)) {
                        Text("Not granted — open settings")
                    }
                }
            }
        },
        leadingContent = { RadioButton(selected = selected, onClick = onSelect) },
        trailingContent = {
            if (selected) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onSelect),
    )
}
