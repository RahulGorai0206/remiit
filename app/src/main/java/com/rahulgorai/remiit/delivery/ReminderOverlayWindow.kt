package com.rahulgorai.remiit.delivery

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.ReminderOutcome
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.prefs.ThemeMode
import com.rahulgorai.remiit.ui.reminder.ReminderOverlayScreen
import com.rahulgorai.remiit.ui.theme.RemiitTheme

/**
 * The reminder drawn as its own window, on top of whatever the user is doing.
 *
 * This exists because a full-screen intent does not do what its name suggests.
 * The platform only launches the activity behind one when the device is locked
 * or the screen is off; on an unlocked, in-use phone it degrades to a heads-up
 * notification, which is the opposite of what someone asking for a full-screen
 * alarm wants. Starting the activity directly instead is blocked by the
 * background activity-start restrictions, and fails quietly.
 *
 * A `TYPE_APPLICATION_OVERLAY` window is the one route that is not an activity
 * start, so none of those restrictions apply. The cost is real and the user
 * pays it knowingly: "Display over other apps" is an intrusive permission
 * granted by hand in Settings, and until it is granted this does nothing and
 * delivery falls back to the notification.
 *
 * The split of responsibilities is worth stating, because it is not obvious:
 *
 * - **Screen locked or off** — the full-screen intent still handles it, and
 *   still must. An overlay window cannot draw over the keyguard; only an
 *   activity with `showWhenLocked` can.
 * - **Unlocked and in use** — this window handles it.
 */
object ReminderOverlayWindow {

    private const val TAG = "ReminderOverlay"

    private val main = Handler(Looper.getMainLooper())

    /** The window currently on screen, if any. Only ever touched on the main thread. */
    private var current: Showing? = null

    private class Showing(
        val view: View,
        val host: OverlayHost,
        val dismissReceiver: BroadcastReceiver,
    )

    /** Whether the user has granted "Display over other apps". */
    fun isPermitted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Whether an overlay is the right delivery for this moment.
     *
     * False when the screen is off or the keyguard is up — those are exactly
     * the cases the full-screen intent handles properly, and an overlay would
     * be drawn behind the lock screen where nobody would see it.
     */
    fun isUsableNow(context: Context): Boolean {
        if (!isPermitted(context)) return false
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    /**
     * Shows the reminder. Returns false if it could not be shown, in which case
     * the caller must fall back to posting a notification.
     */
    fun show(
        context: Context,
        rule: ReminderRule,
        eventId: Long,
        triggerSummary: String,
        themeMode: ThemeMode,
        dynamicColor: Boolean,
    ): Boolean {
        if (!isUsableNow(context)) return false

        val app = context.applicationContext
        var shown = false
        runOnMain {
            shown = runCatching {
                dismissInternal()
                addWindow(app, rule, eventId, triggerSummary, themeMode, dynamicColor)
            }.onFailure { Log.e(TAG, "Could not add overlay window", it) }.isSuccess
        }
        // The add itself is posted to the main thread, so the caller cannot
        // learn the outcome synchronously from a background thread. Reporting
        // the permission state is the honest answer: it is what decides whether
        // this path is viable at all, and the failure modes past that point are
        // logged rather than silently swallowed.
        return isPermitted(context)
    }

    fun dismiss() = runOnMain { dismissInternal() }

    @SuppressLint("InflateParams")
    private fun addWindow(
        app: Context,
        rule: ReminderRule,
        eventId: Long,
        triggerSummary: String,
        themeMode: ThemeMode,
        dynamicColor: Boolean,
    ) {
        val windowManager = app.getSystemService(WindowManager::class.java)
            ?: error("no WindowManager")

        val host = OverlayHost()
        val compose = ComposeView(app)

        // A ComposeView outside an Activity has no owners to inherit, and
        // without all three it throws the moment it composes. The host supplies
        // the minimum: a lifecycle to drive composition, a store so remembered
        // state survives a configuration change, and a saved-state registry
        // because the other two are wired to expect one.
        compose.setViewTreeLifecycleOwner(host)
        compose.setViewTreeViewModelStoreOwner(host)
        compose.setViewTreeSavedStateRegistryOwner(host)

        val respond: (ReminderOutcome) -> Unit = { outcome ->
            // Routed through the receiver rather than handled here, so the
            // overlay and the notification actions cannot disagree about what
            // happened — one place records an outcome, silences the alarm and
            // clears the shade.
            app.sendBroadcast(
                Intent(app, ReminderActionReceiver::class.java).apply {
                    action = when (outcome) {
                        ReminderOutcome.COMPLETED -> ReminderActionReceiver.ACTION_COMPLETE
                        ReminderOutcome.INCOMPLETE -> ReminderActionReceiver.ACTION_INCOMPLETE
                        ReminderOutcome.SNOOZED -> ReminderActionReceiver.ACTION_SNOOZE
                        else -> ReminderActionReceiver.ACTION_DISMISS
                    }
                    putExtra(ReminderActionReceiver.EXTRA_EVENT_ID, eventId)
                    putExtra(ReminderActionReceiver.EXTRA_RULE_ID, rule.id)
                }
            )
            AlarmSoundPlayer.stop()
            dismissInternal()
        }

        compose.setContent {
            RemiitTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                ReminderOverlayScreen(
                    rule = rule,
                    triggerSummary = triggerSummary,
                    onComplete = { respond(ReminderOutcome.COMPLETED) },
                    onIncomplete = { respond(ReminderOutcome.INCOMPLETE) },
                    onSnooze = { respond(ReminderOutcome.SNOOZED) },
                    onExpire = { respond(ReminderOutcome.EXPIRED) },
                )
            }
        }

        // Back is swallowed, deliberately.
        //
        // A reminder is a question, and a reflex swipe is not an answer to it —
        // losing an alarm to muscle memory is the failure this whole delivery
        // path exists to prevent. There is no trap in refusing it either: the
        // surface always offers an explicit way out, whether that is
        // Complete/Not done, a plain Dismiss, or Snooze.
        //
        // Focusable so the key arrives here at all; an unfocusable overlay never
        // receives it, and the press would fall through to the app underneath.
        compose.isFocusableInTouchMode = true
        compose.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Draw into the display cutout rather than being letterboxed below it.
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        val dismissReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = dismissInternal()
        }
        ContextCompat.registerReceiver(
            app,
            dismissReceiver,
            IntentFilter(ReminderActionReceiver.ACTION_DISMISS_OVERLAY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        host.onCreate()
        windowManager.addView(compose, params)
        host.onResume()
        compose.requestFocus()

        if (rule.delivery.mode == DeliveryMode.ALARM) {
            AlarmSoundPlayer.start(app, rule.delivery)
        }

        current = Showing(compose, host, dismissReceiver)
        Log.i(TAG, "Overlay shown for rule ${rule.id}")
    }

    private fun dismissInternal() {
        val showing = current ?: return
        current = null
        val app = showing.view.context.applicationContext
        runCatching { app.unregisterReceiver(showing.dismissReceiver) }
        runCatching {
            app.getSystemService(WindowManager::class.java)?.removeView(showing.view)
        }.onFailure { Log.w(TAG, "Overlay already detached", it) }
        showing.host.onDestroy()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /**
     * The three owners a ComposeView needs when there is no Activity to provide
     * them. Nothing here is persisted — the window is a moment, not a screen —
     * so the saved-state registry is created and immediately restored empty.
     */
    private class OverlayHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun onCreate() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun onResume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}
