package com.rahulgorai.remiit.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * The app's border scale.
 *
 * Every interactive surface in Remiit is drawn with a visible edge rather than
 * relying on fill alone. That is a deliberate departure from stock Material,
 * where tonal buttons and cards separate themselves from the background by a
 * few percent of luminance — which disappears outdoors, on a dimmed screen, and
 * for anyone who does not have perfect contrast sensitivity.
 *
 * Two weights, used consistently:
 *
 * - [container] for passive surfaces (cards, sheets, list groups). Quiet enough
 *   to define an edge without turning the screen into a grid of boxes.
 * - [interactive] for anything the user can press. Heavier and higher contrast,
 *   because the border is what says "this is a control".
 */
object RemiitBorders {

    /** Passive surfaces: cards, panels, grouped rows. */
    @Composable
    @ReadOnlyComposable
    fun container(): BorderStroke =
        BorderStroke(CONTAINER_WIDTH, MaterialTheme.colorScheme.outlineVariant)

    /** Anything pressable. Deliberately the stronger of the two. */
    @Composable
    @ReadOnlyComposable
    fun interactive(): BorderStroke =
        BorderStroke(INTERACTIVE_WIDTH, MaterialTheme.colorScheme.outline)

    /** A pressable surface that is currently selected or carries the primary action. */
    @Composable
    @ReadOnlyComposable
    fun accent(): BorderStroke =
        BorderStroke(INTERACTIVE_WIDTH, MaterialTheme.colorScheme.primary)

    /** For a control in an error state, so the border carries the meaning too. */
    @Composable
    @ReadOnlyComposable
    fun error(): BorderStroke =
        BorderStroke(INTERACTIVE_WIDTH, MaterialTheme.colorScheme.error)

    /** A disabled control still needs an edge, just a receding one. */
    @Composable
    @ReadOnlyComposable
    fun disabled(): BorderStroke = BorderStroke(
        INTERACTIVE_WIDTH,
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
    )

    val CONTAINER_WIDTH = 1.dp

    /**
     * 1.5dp rather than 1dp. At 1dp a border reads as a hairline seam between
     * two surfaces; at 1.5dp it reads as the outline of an object, which is the
     * difference between decoration and an affordance.
     */
    val INTERACTIVE_WIDTH = 1.5.dp

    private const val DISABLED_ALPHA = 0.22f
}
