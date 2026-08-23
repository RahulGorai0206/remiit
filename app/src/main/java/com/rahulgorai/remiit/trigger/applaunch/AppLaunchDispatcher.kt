package com.rahulgorai.remiit.trigger.applaunch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.engine.TriggerEvent
import com.rahulgorai.remiit.engine.TriggerSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Shared matching logic for both app-launch detectors.
 *
 * The two detectors differ only in how they notice a foreground change; what to
 * do about it is identical, so it lives here rather than being duplicated (and
 * drifting) between an AccessibilityService and a polling loop.
 */
class AppLaunchDispatcher(
    private val context: Context,
    private val sink: TriggerSink,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    @Volatile
    private var rules: List<ReminderRule> = emptyList()

    @Volatile
    private var lastPackage: String? = null

    @Volatile
    private var lastAtMillis: Long = 0L

    /**
     * Packages never treated as an app launch, whatever the rule says.
     *
     * Without this, "remind me on any app launch" fires every time you touch
     * the home button or pull down the shade, which makes the feature unusable
     * rather than merely noisy. Resolved lazily because the default launcher
     * can change.
     */
    private val implicitExcludes: Set<String> by lazy {
        buildSet {
            add(context.packageName)
            add("com.android.systemui")
            defaultLauncherPackage()?.let(::add)
        }
    }

    fun updateRules(rules: List<ReminderRule>) {
        this.rules = rules.filter { it.appLaunchTriggers.isNotEmpty() }
    }

    /** True when at least one enabled rule cares about app launches. */
    fun hasWork(): Boolean = rules.isNotEmpty()

    /**
     * Called by whichever detector is active. Safe to call repeatedly for the
     * same launch: both detectors emit duplicates (accessibility fires per
     * window, usage-stats polling re-reads the same event), so repeats of the
     * same package inside [DEDUP_WINDOW_MILLIS] are dropped.
     */
    fun onAppForegrounded(packageName: String) {
        if (packageName.isBlank() || packageName in implicitExcludes) return

        val now = clock.millis()
        if (packageName == lastPackage && now - lastAtMillis < DEDUP_WINDOW_MILLIS) return
        lastPackage = packageName
        lastAtMillis = now

        val matches = rules.flatMap { rule ->
            rule.appLaunchTriggers
                .filter { it.matches(packageName) }
                .map { rule to it }
        }
        if (matches.isEmpty()) return

        val firedAt = clock.instant()
        scope.launch {
            matches.forEach { (rule, trigger) ->
                sink.onTriggerFired(
                    TriggerEvent(
                        ruleId = rule.id,
                        triggerId = trigger.id,
                        summary = appLabel(packageName)?.let { "$it opened" }
                            ?: trigger.shortSummary(),
                        firedAt = firedAt,
                    )
                )
            }
        }
    }

    private fun defaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()
    }

    private fun appLabel(packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    private companion object {
        const val DEDUP_WINDOW_MILLIS = 2_000L
    }
}
