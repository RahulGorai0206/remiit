package com.rahulgorai.remiit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Expressive shape scale — noticeably rounder than the Material baseline.
 *
 * The large end is where this matters: rule cards and the reminder overlay use
 * `extraLarge`, and the generous radius is what makes a screen full of stacked
 * cards read as soft, distinct objects rather than a table.
 */
internal val RemiitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
