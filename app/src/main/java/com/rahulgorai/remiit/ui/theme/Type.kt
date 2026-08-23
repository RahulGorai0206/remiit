package com.rahulgorai.remiit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val expressiveLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Expressive typography: heavier display and headline weights with tighter
 * tracking than the baseline Material scale.
 *
 * The weight contrast is doing real work here rather than being decoration —
 * the reminder overlay has to be legible in a glance from across a room, and
 * the rule list leans on type weight instead of colour to separate a rule's
 * title from its trigger summary.
 */
internal val RemiitTypography = Typography().let { base ->
    Typography(
        displayLarge = TextStyle(
            fontWeight = FontWeight.Black,
            fontSize = 57.sp,
            lineHeight = 60.sp,
            letterSpacing = (-1.0).sp,
            lineHeightStyle = expressiveLineHeight,
        ),
        displayMedium = TextStyle(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 45.sp,
            lineHeight = 50.sp,
            letterSpacing = (-0.5).sp,
            lineHeightStyle = expressiveLineHeight,
        ),
        displaySmall = TextStyle(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            letterSpacing = (-0.25).sp,
            lineHeightStyle = expressiveLineHeight,
        ),
        headlineLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.25).sp,
            lineHeightStyle = expressiveLineHeight,
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = expressiveLineHeight,
        ),
        headlineSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = expressiveLineHeight,
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = expressiveLineHeight,
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
        ),
        titleSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = base.bodyLarge,
        bodyMedium = base.bodyMedium,
        bodySmall = base.bodySmall,
        labelLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = base.labelSmall,
    )
}
