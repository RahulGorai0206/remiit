package com.rahulgorai.remiit.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulgorai.remiit.ui.components.BorderedIconButton
import com.rahulgorai.remiit.ui.components.RuleCard
import com.rahulgorai.remiit.ui.theme.RemiitBorders
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddRule: () -> Unit,
    onEditRule: (ruleId: String, title: String) -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)

    val fabShape = MaterialTheme.shapes.large

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Rules") },
                actions = {
                    BorderedIconButton(
                        icon = Icons.Outlined.Shield,
                        contentDescription = "Permissions",
                        onClick = onOpenPermissions,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRule,
                shape = fabShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.border(RemiitBorders.interactive(), fabShape),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("New rule", style = MaterialTheme.typography.titleSmall)
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = rules.isEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut(),
            ) {
                EmptyState(modifier = Modifier.fillMaxSize())
            }

            AnimatedVisibility(visible = rules.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        // Clears the FAB. The bottom bar is already accounted
                        // for by the inset this screen is laid out inside.
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = rules, key = { it.rule.id }) { state ->
                        RuleCard(
                            rule = state.rule,
                            nextFire = state.nextFire,
                            onClick = { onEditRule(state.rule.id, state.rule.title) },
                            onToggle = { enabled -> viewModel.setEnabled(state.rule, enabled) },
                            // animateItem gives reorder and removal the spring
                            // motion the rest of the app uses.
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The app's own mark, drawn rather than imported: an open orbit with a
        // bead in the gap. The same shape as the launcher icon, so an empty
        // first run still shows the user something that belongs to this app.
        Box(
            modifier = Modifier
                .size(120.dp)
                .drawBehind {
                    val stroke = 10.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2f - 12.dp.toPx()
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    drawArc(
                        color = outline,
                        startAngle = -3f,
                        sweepAngle = 276f,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius, centre.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                    val beadAngle = Math.toRadians(-45.0)
                    drawCircle(
                        color = accent,
                        radius = stroke * 0.75f,
                        center = Offset(
                            centre.x + (radius * kotlin.math.cos(beadAngle)).toFloat(),
                            centre.y + (radius * kotlin.math.sin(beadAngle)).toFloat(),
                        ),
                    )
                }
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Nothing scheduled",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "A rule pairs a task with what should trigger it — a time, " +
                "a Wi-Fi network, arriving somewhere, or opening an app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
