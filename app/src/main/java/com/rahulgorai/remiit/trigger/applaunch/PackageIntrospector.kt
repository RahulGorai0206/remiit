package com.rahulgorai.remiit.trigger.applaunch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * The two things [AppLaunchDispatcher] needs to ask PackageManager.
 *
 * Extracted as an interface purely for testability. The dispatcher's job is
 * deciding which foreground events count as a launch, and that decision has been
 * wrong twice — once firing on the launcher, once re-firing forever after a
 * reminder was dismissed. Both are cheap to write a test for and impossible to
 * write one for while the logic is welded to a real PackageManager.
 */
interface PackageIntrospector {

    /** The app's own package, which is never a launch worth reminding about. */
    val ownPackage: String

    /** The current home-screen package, or null if it cannot be resolved. */
    fun launcherPackage(): String?

    /** The user-visible app name, for the reminder's summary line. */
    fun label(packageName: String): String?
}

/** The real implementation, backed by [PackageManager]. */
class DefaultPackageIntrospector(private val context: Context) : PackageIntrospector {

    private val packageManager: PackageManager get() = context.packageManager

    override val ownPackage: String get() = context.packageName

    override fun launcherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()
    }

    override fun label(packageName: String): String? = runCatching {
        packageManager
            .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
            .toString()
    }.getOrNull()
}
