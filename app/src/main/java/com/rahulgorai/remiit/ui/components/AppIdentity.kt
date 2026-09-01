package com.rahulgorai.remiit.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.data.model.shortSummary

/**
 * What a package name looks like to a person.
 *
 * `com.google.android.youtube` is the identifier the rule stores and the only
 * thing the trigger model knows about. It is not what the rule *means*, and
 * showing it raw makes the app look like a debug build. Everything the user
 * reads goes through here instead.
 */
data class AppIdentity(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/**
 * Resolves a package to its label and launcher icon.
 *
 * Keyed on the package so the PackageManager lookup and the icon rasterisation
 * happen once per app rather than on every recomposition of a list.
 *
 * Falls back to the package name when the app has been uninstalled since the
 * rule was written — a rule pointing at a missing app is worth still showing,
 * because the user is the one who has to decide whether to delete it.
 */
@Composable
fun rememberAppIdentity(packageName: String): AppIdentity {
    val context = LocalContext.current
    return remember(packageName) { loadAppIdentity(context, packageName) }
}

private fun loadAppIdentity(context: Context, packageName: String): AppIdentity {
    val pm = context.packageManager
    val label = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
    val icon = runCatching { pm.getApplicationIcon(packageName).toImageBitmap() }.getOrNull()
    return AppIdentity(
        packageName = packageName,
        label = label ?: packageName,
        icon = icon,
    )
}

/**
 * Adaptive icons report no intrinsic size, so a target has to be supplied or
 * `toBitmap` throws. 48dp at xxhdpi is the largest a launcher icon is drawn
 * here, and rasterising once at that size beats scaling a drawable per frame.
 */
private fun Drawable.toImageBitmap(sizePx: Int = 144): ImageBitmap =
    toBitmap(width = sizePx, height = sizePx).asImageBitmap()

/**
 * The human-readable form of a trigger, and the app icon to lead it with.
 *
 * Only app-launch triggers carry an icon; the rest describe themselves fully in
 * words and use their kind's glyph, which the caller already has.
 */
data class TriggerDisplay(
    val text: String,
    val appIcon: ImageBitmap? = null,
)

@Composable
fun triggerDisplay(trigger: Trigger): TriggerDisplay {
    if (trigger !is Trigger.AppLaunch) {
        return TriggerDisplay(trigger.shortSummary())
    }
    return when (trigger.packages.size) {
        0 -> TriggerDisplay("Any app opens")
        1 -> {
            val app = rememberAppIdentity(trigger.packages.first())
            TriggerDisplay("${app.label} opens", app.icon)
        }
        // Naming three apps in a chip truncates to uselessness; the count is the
        // honest summary and the editor is one tap away.
        else -> TriggerDisplay("${trigger.packages.size} apps open")
    }
}
