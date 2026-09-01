package com.rahulgorai.remiit.trigger.applaunch

import android.util.Log
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.engine.TriggerEvent
import com.rahulgorai.remiit.engine.TriggerSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Decides which foreground changes actually count as an app launch.
 *
 * Fed by [UsageStatsAppLaunchPoller], which reports activity resumes. Kept
 * separate from the poller so the "is this actually a launch?" decision is
 * testable without a live UsageStatsManager.
 */
class AppLaunchDispatcher(
    private val packages: PackageIntrospector,
    private val sink: TriggerSink,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    @Volatile
    private var rules: List<ReminderRule> = emptyList()

    /** The app we currently believe the user is in. */
    @Volatile
    private var lastPackage: String? = null

    @Volatile
    private var lastFiredPackage: String? = null

    /** When [lastFiredPackage] fired, so a quick bounce cannot re-fire it. */
    @Volatile
    private var lastFiredAtMillis: Long = 0L

    /**
     * Packages whose windows are ignored entirely — they neither fire a trigger
     * nor count as leaving the app underneath.
     *
     * Remiit's own package is the important entry, and is what breaks the
     * re-fire loop. A banner or alarm reminder puts Remiit's overlay in front of
     * whatever you were using; treating that as an app switch meant dismissing
     * the reminder put YouTube back in the foreground, which looked like a fresh
     * launch, which fired the same reminder again — indefinitely.
     */
    private val transientPackages: Set<String> by lazy {
        setOf(packages.ownPackage) + SYSTEM_OVERLAY_PACKAGES
    }

    /**
     * Packages that update state but never fire triggers.
     *
     * The home screen: going home should not remind you of anything, but it
     * genuinely is leaving the app, so coming back afterwards is a new launch.
     */
    private val excludedPackages: Set<String> by lazy {
        buildSet { packages.launcherPackage()?.let(::add) }
    }

    fun updateRules(rules: List<ReminderRule>) {
        this.rules = rules.filter { it.appLaunchTriggers.isNotEmpty() }
    }

    /** True when at least one enabled rule cares about app launches. */
    fun hasWork(): Boolean = rules.isNotEmpty()

    /**
     * Called by the poller for each activity resume it sees.
     */
    fun onAppForegrounded(packageName: String) {
        // Deliberately returns *before* touching lastPackage: recording a
        // transient package would make the next event for the app underneath
        // look like a switch, which is the bug this guards against.
        if (packageName.isBlank() || packageName in transientPackages) return

        val changed = packageName != lastPackage
        lastPackage = packageName
        if (!changed) return

        // The launcher: we have recorded that the user left the previous app,
        // but going home is not itself something to remind anyone about.
        if (packageName in excludedPackages) return

        val now = clock.millis()
        if (packageName == lastFiredPackage && now - lastFiredAtMillis < RELAUNCH_GUARD_MILLIS) {
            // A task switch the OS reported twice, or a quick glance at another
            // app and straight back. Not a second launch.
            Log.d(TAG, "Suppressed re-launch of $packageName inside the guard window")
            return
        }

        val matches = rules.flatMap { rule ->
            rule.appLaunchTriggers
                .filter { it.matches(packageName) }
                .map { rule to it }
        }
        Log.d(TAG, "Foreground: $packageName — ${matches.size} match(es)")
        if (matches.isEmpty()) return

        lastFiredPackage = packageName
        lastFiredAtMillis = now

        val firedAt = clock.instant()
        scope.launch {
            matches.forEach { (rule, trigger) ->
                sink.onTriggerFired(
                    TriggerEvent(
                        ruleId = rule.id,
                        triggerId = trigger.id,
                        summary = packages.label(packageName)?.let { "$it opened" }
                            ?: trigger.shortSummary(),
                        firedAt = firedAt,
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "AppLaunchDispatcher"

        /**
         * Chrome that draws on top of the current app. The notification shade is
         * the classic case: pulling it down and pushing it back up is not
         * leaving and re-entering the app underneath.
         */
        val SYSTEM_OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "com.samsung.android.app.cocktailbarservice",
        )

        /**
         * A second launch of the same app this soon after the last one is a
         * bounce, not a new launch. Short enough that genuinely reopening an app
         * after glancing at another one still fires.
         */
        const val RELAUNCH_GUARD_MILLIS = 5_000L
    }
}
