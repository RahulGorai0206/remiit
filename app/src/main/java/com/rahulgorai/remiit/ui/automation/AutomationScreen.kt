package com.rahulgorai.remiit.ui.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationTriggerKind
import com.rahulgorai.remiit.data.model.kind
import com.rahulgorai.remiit.data.model.summary
import com.rahulgorai.remiit.ui.components.formatNextFire
import com.rahulgorai.remiit.ui.theme.RemiitBorders
import org.koin.androidx.compose.koinViewModel
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onAdd: () -> Unit,
    onEdit: (automationId: String, name: String) -> Unit,
    /** Footprint of the floating navigation pill this screen scrolls beneath. */
    bottomInset: Dp,
    viewModel: AutomationViewModel = koinViewModel(),
) {
    val automations by viewModel.automations.collectAsStateWithLifecycle()

    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
    val fabShape = MaterialTheme.shapes.large

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Automation", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                shape = fabShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(bottom = bottomInset)
                    .border(RemiitBorders.interactive(), fabShape),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("New automation", style = MaterialTheme.typography.titleSmall)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = automations.isEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut(),
            ) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(bottom = bottomInset)
                )
            }

            AnimatedVisibility(
                visible = automations.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = padding.calculateBottomPadding() + bottomInset + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "summary") {
                        Summary(
                            total = automations.size,
                            active = automations.count { it.isEnabled },
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    items(items = automations, key = { it.id }) { automation ->
                        AutomationCard(
                            automation = automation,
                            onClick = { onEdit(automation.id, automation.name) },
                            onToggle = { enabled -> viewModel.setEnabled(automation, enabled) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(total: Int, active: Int, modifier: Modifier = Modifier) {
    val word = if (total == 1) "automation" else "automations"
    Column(modifier) {
        Text(
            text = when {
                active == total -> "$total $word, all on"
                active == 0 -> "$total $word, all paused"
                else -> "$active of $total on"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "These change the phone's settings for you. Nothing is announced.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun iconFor(kind: AutomationTriggerKind): ImageVector = when (kind) {
    AutomationTriggerKind.WIFI -> Icons.Outlined.Wifi
    AutomationTriggerKind.BLUETOOTH -> Icons.Outlined.Bluetooth
    AutomationTriggerKind.LOCATION -> Icons.Outlined.Place
}

/**
 * One automation, as a card.
 *
 * Reads as a sentence in two halves — what happens, then what it does — because
 * that is the whole of an automation and anything more would be padding. The
 * third line is the one that earns its place: what happened last time it ran.
 */
@Composable
private fun AutomationCard(
    automation: Automation,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val faded = if (automation.isEnabled) 1f else 0.55f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = RemiitBorders.container(),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .border(RemiitBorders.container(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconFor(automation.trigger.kind),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = automation.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = faded),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = automation.trigger.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = faded),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = automation.isEnabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = automation.actions.summary().ifBlank { "Does nothing yet" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = faded),
            )

            if (automation.lastRunAtEpochMillis > 0L) {
                Spacer(Modifier.height(10.dp))
                LastRun(
                    at = Instant.ofEpochMilli(automation.lastRunAtEpochMillis),
                    result = automation.lastResult,
                    failed = !automation.lastRunSucceeded,
                )
            }
        }
    }
}

/**
 * The last run.
 *
 * The only feedback an automation ever gives. Without it a revoked permission
 * looks exactly like a trigger that has not happened yet, and the user has no
 * way to tell which — so a failure is called out in the error tone rather than
 * folded into the same grey line as a success.
 */
@Composable
private fun LastRun(at: Instant, result: String, failed: Boolean) {
    val tone = if (failed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        if (failed) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = if (failed) "$result — ${formatNextFire(at)}" else "Ran ${formatNextFire(at)}",
            style = MaterialTheme.typography.bodySmall,
            color = tone,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .border(RemiitBorders.container(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "No automations",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Change the phone to suit where you are — silent on the office " +
                "Wi-Fi, adaptive brightness off in the car, Do Not Disturb at home.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
