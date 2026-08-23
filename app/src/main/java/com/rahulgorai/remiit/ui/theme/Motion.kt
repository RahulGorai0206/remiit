package com.rahulgorai.remiit.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Accessors for the theme's motion scheme.
 *
 * Custom animations read their specs from here instead of hardcoding a
 * `spring()` or `tween()`, so they move on the same curves as the built-in
 * components. Getting this wrong is what makes an app feel like two apps —
 * Material's own transitions springing while hand-written ones ease.
 *
 * Spatial specs are for anything that moves or resizes; effects specs are for
 * properties that do not occupy space, like alpha and colour.
 */
object RemiitMotion {

    /** Default spatial spring. Use for position, size and layout changes. */
    @Composable
    @ReadOnlyComposable
    fun <T> spatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    /** Snappier spatial spring, for small or frequent movements. */
    @Composable
    @ReadOnlyComposable
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

    /** Deliberate spatial spring, for large surfaces entering or leaving. */
    @Composable
    @ReadOnlyComposable
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

    /** Default effects spec. Use for alpha, colour and elevation. */
    @Composable
    @ReadOnlyComposable
    fun <T> effects(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

    @Composable
    @ReadOnlyComposable
    fun <T> fastEffects(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()
}
