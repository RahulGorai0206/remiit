package com.rahulgorai.remiit.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rahulgorai.remiit.ui.theme.RemiitBorders

/**
 * The app's buttons.
 *
 * Three levels of emphasis, and every one of them carries a visible border.
 * Material's stock buttons lean on fill alone to say "pressable", which reads
 * fine in a design tool and poorly on a real screen in real light — a tonal
 * button on a tonal card can be a two-percent luminance step. Drawing the edge
 * makes the hit target unambiguous at any brightness.
 *
 * Screens should use these rather than [Button] and friends directly, so the
 * border treatment stays consistent instead of being remembered case by case.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        // The rim reads against the filled primary in both light and dark:
        // `outline` sits between the two, so it never matches the fill.
        border = if (enabled) RemiitBorders.interactive() else RemiitBorders.disabled(),
        contentPadding = ButtonContentPadding,
    ) { ButtonContent(text, icon) }
}

/**
 * Secondary emphasis. Outlined rather than tonal, because an outline is the
 * clearest way to say "pressable but not the main thing".
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = if (enabled) RemiitBorders.interactive() else RemiitBorders.disabled(),
        contentPadding = ButtonContentPadding,
    ) { ButtonContent(text, icon) }
}

/**
 * Lowest emphasis — an inline action inside a row or a supporting line of text.
 *
 * Still bordered. This is where Material would reach for a text button, and a
 * text button is exactly the control people fail to notice is a control.
 */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = if (enabled) RemiitBorders.interactive() else RemiitBorders.disabled(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = CompactContentPadding,
    ) { ButtonContent(text, icon, compact = true) }
}

/**
 * A bordered icon-only button.
 *
 * Bare [IconButton]s are the worst offenders for invisible affordances — a
 * glyph floating in space with a 48dp hit target nobody can see. The ring makes
 * the target explicit.
 */
@Composable
fun BorderedIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = tint),
        border = if (enabled) RemiitBorders.interactive() else RemiitBorders.disabled(),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/**
 * A tonal button for a selected or "already done" state, still bordered so it
 * sits in the same visual family as the rest.
 */
@Composable
fun ConfirmedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        border = if (enabled) RemiitBorders.accent() else RemiitBorders.disabled(),
        contentPadding = ButtonContentPadding,
    ) { ButtonContent(text, icon) }
}

@Composable
private fun RowScope.ButtonContent(
    text: String,
    icon: ImageVector?,
    compact: Boolean = false,
) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.width(if (compact) 16.dp else 18.dp),
        )
        Spacer(Modifier.width(8.dp))
    }
    Text(
        text = text,
        style = if (compact) {
            MaterialTheme.typography.labelLarge
        } else {
            MaterialTheme.typography.titleSmall
        },
    )
}

/** Roomier than Material's default: a bordered button needs air inside its edge. */
private val ButtonContentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
private val CompactContentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
