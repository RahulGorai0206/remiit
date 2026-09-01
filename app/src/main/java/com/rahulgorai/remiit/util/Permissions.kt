package com.rahulgorai.remiit.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.rahulgorai.remiit.trigger.applaunch.UsageStatsAppLaunchPoller

/**
 * Grant checks for everything the app needs, in one place.
 *
 * This app needs an unusually wide set of permissions, and the common failure
 * mode is not a crash but silence: a rule that never fires because one grant is
 * missing. Every check here has a matching card in the permissions screen so the
 * user can see which triggers are actually live.
 */
object Permissions {

    fun hasNotifications(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Required for geofences, and *also* required to read a Wi-Fi SSID. Without
     * it the Wi-Fi trigger gets a redacted SSID and never matches, which is not
     * obvious from the Wi-Fi settings screen.
     */
    fun hasBackgroundLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun canScheduleExactAlarms(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    /**
     * On Android 14+ a full-screen intent is only honoured for apps the user has
     * allowed, and the default for a non-calling app is *denied*. Without this,
     * both the banner and alarm modes quietly collapse into ordinary heads-up
     * notifications.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

    /**
     * The only app-launch detector. A special-access grant made through
     * Settings > Usage access, never a runtime prompt.
     */
    fun hasUsageAccess(context: Context): Boolean =
        UsageStatsAppLaunchPoller.hasUsageAccess(context)

    /**
     * Manufacturer battery managers are the most common cause of "it worked for
     * a day and then stopped": they kill the monitor service and drop alarms.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun areLocationServicesEnabled(context: Context): Boolean {
        val manager = context.getSystemService(android.location.LocationManager::class.java)
            ?: return false
        return manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    // ---- Intents to the relevant settings screens --------------------------

    fun exactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri(context))

    /**
     * The per-app full-screen-intent toggle. The Settings constant only exists
     * from API 34, so the action string is inlined — referencing the constant
     * would compile it in as a literal anyway, but only after a lint warning.
     * On API 33 the restriction does not exist and this screen is never reached.
     */
    fun fullScreenIntentSettings(context: Context): Intent =
        Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", appUri(context))

    fun usageAccessSettings(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(context))

    fun locationSettings(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    fun batteryOptimizationSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri(context))

    private fun appUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}

/**
 * Launches a settings screen from a non-activity context.
 *
 * The NEW_TASK flag is required when the caller is an application context, and
 * the runCatching guards the real case of an OEM build that simply does not ship
 * the target screen — better a no-op than a crash from a settings row.
 */
fun android.content.Context.openSettings(intent: Intent) {
    runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching {
            startActivity(
                Permissions.appDetailsSettings(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
