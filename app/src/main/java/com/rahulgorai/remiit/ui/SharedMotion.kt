package com.rahulgorai.remiit.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale

/**
 * Plumbing for the app's connected transitions.
 *
 * The rule everywhere in Remiit is that a thing does not vanish here and appear
 * there — it travels. Tapping a rule grows that exact card into the editor;
 * pressing back shrinks the editor back into the card it came from; the "new
 * rule" button expands into an empty editor and collapses back into the button.
 * Nothing cross-fades between two unrelated positions.
 *
 * Compose's shared-element API needs two scopes to do that, and they are
 * created in different places: the [SharedTransitionScope] wraps the whole
 * NavHost, while each destination gets its own [AnimatedVisibilityScope]. Rather
 * than thread both through every composable signature, they are published here
 * and read by the [sharedContainer] and [sharedTitle] modifiers.
 *
 * Both locals default to null so any composable still renders correctly outside
 * a navigation graph — previews, the reminder overlay activity, and tests get a
 * plain modifier instead of a crash.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Keys naming the things that travel between screens. */
object SharedKeys {
    /** The card/editor surface for one rule. */
    fun ruleContainer(ruleId: String): String = "rule-container-$ruleId"

    /** The rule's title text, so the words themselves move rather than re-flow. */
    fun ruleTitle(ruleId: String): String = "rule-title-$ruleId"

    /**
     * The "new rule" button. A new rule has no id, so the button and the empty
     * editor agree on this constant instead.
     */
    const val NEW_RULE = "rule-container-new"
}

/**
 * Marks a surface as the same object across a navigation change.
 *
 * Scales the content to the travelling bounds rather than remeasuring it.
 * Remeasuring keeps type at its true size the whole way, which is technically
 * more correct, but it relays out the entire subtree on every frame — and the
 * subtree here is a whole scrolling screen. Scaling animates a matrix instead,
 * which is what keeps this at frame rate on a mid-range phone.
 *
 * Anchored to the top-left and fitted to width, so the content grows from the
 * card's corner the way the container does rather than drifting from its
 * centre.
 */
@Composable
fun Modifier.sharedContainer(
    key: String,
    shape: Shape,
): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            // The container's own bounds carry the motion, so the content
            // inside it only needs to fade — a second spatial animation here
            // would fight the bounds transform.
            enter = fadeIn(RemiitBoundsMotion.contentSpec()),
            exit = fadeOut(RemiitBoundsMotion.contentSpec()),
            boundsTransform = RemiitBoundsMotion.boundsTransform(),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
            // Without this the travelling surface is drawn square-cornered in
            // the overlay and snaps to its rounded shape on arrival.
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/**
 * Marks a piece of text as the same text across a navigation change.
 *
 * Distinct from [sharedContainer] because a matched pair of text elements is
 * visually identical at both ends — only its position and size change — so it
 * uses `sharedElement`, which renders one copy and moves it, rather than
 * cross-fading two.
 */
@Composable
fun Modifier.sharedTitle(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedTitle.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = RemiitBoundsMotion.boundsTransform(),
        )
    }
}

/**
 * The specs the connected transitions run on.
 *
 * Read from the theme's motion scheme rather than hardcoded, so a shared
 * element travels on the same spring as everything Material animates by itself.
 * Mixing a hand-written tween with Material's springs is what makes an app feel
 * like two apps stitched together.
 */
object RemiitBoundsMotion {

    /** Spring used for the travelling bounds. */
    @Composable
    fun boundsTransform(): BoundsTransform {
        val spec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.geometry.Rect>()
        return BoundsTransform { _, _ -> spec }
    }

    /** Fade used for the content inside a travelling container. */
    @Composable
    fun contentSpec() = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
}
