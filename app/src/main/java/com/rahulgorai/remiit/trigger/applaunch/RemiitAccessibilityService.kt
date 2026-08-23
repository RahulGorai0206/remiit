package com.rahulgorai.remiit.trigger.applaunch

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.rahulgorai.remiit.data.prefs.AppLaunchDetectorKind
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * App-launch detection via accessibility events.
 *
 * The instant, battery-cheap option: the OS pushes a window-state change the
 * moment an app comes forward, so nothing polls and nothing has to stay awake.
 *
 * It reads only [AccessibilityEvent.getPackageName]. Node contents are never
 * inspected, which is why the service declares no `canRetrieveWindowContent`
 * and requests no event types beyond window state.
 *
 * The cost is that this counts as an accessibility grant the user has to enable
 * by hand, and Google Play prohibits using accessibility for this purpose —
 * which is fine here because the app ships as a sideloaded APK, but is exactly
 * why [AppLaunchDetectorKind.USAGE_STATS] exists as the alternative.
 */
class RemiitAccessibilityService : AccessibilityService(), KoinComponent {

    private val dispatcher: AppLaunchDispatcher by inject()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        dispatcher.onAppForegrounded(packageName)
    }

    override fun onInterrupt() = Unit

    companion object {
        /**
         * Whether the user has enabled this service.
         *
         * Read from the secure setting rather than tracked in our own
         * preferences: the user can turn it off in system Settings at any time
         * without the app being told, and a stale "enabled" flag would make the
         * app claim app-launch rules work when they silently do not.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${RemiitAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()

            if (TextUtils.isEmpty(enabled)) return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
