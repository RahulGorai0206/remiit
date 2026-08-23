package com.rahulgorai.remiit.ui.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.ReminderOutcome
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.delivery.AlarmSoundPlayer
import com.rahulgorai.remiit.delivery.ReminderActionReceiver
import com.rahulgorai.remiit.ui.theme.RemiitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The full-screen reminder surface, shared by
 * [DeliveryMode.FULLSCREEN_BANNER] and [DeliveryMode.ALARM].
 *
 * One activity for both modes because the UI is the same thing — a big task and
 * two big answers. Only the sound behaviour differs, so splitting them would
 * duplicate the layout and the response wiring for no gain.
 *
 * Launched either by the notification's full-screen intent (screen off or
 * locked) or directly by [com.rahulgorai.remiit.delivery.ReminderDispatcher]
 * (screen already on, where the OS shows a heads-up notification instead of
 * launching anything).
 */
class ReminderOverlayActivity : ComponentActivity() {

    private val repository: RuleRepository by inject()
    private val settings: SettingsStore by inject()

    private var eventId: Long = -1L
    private var soundStarted = false

    /**
     * Closes the overlay when the reminder is answered somewhere else — a
     * notification action, or another instance of this activity.
     */
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and wake the display. Both are required for
        // an alarm to be any use — without them the reminder sits behind the
        // keyguard on a dark screen.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(ReminderActionReceiver.ACTION_DISMISS_OVERLAY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        render(intent)
    }

    /**
     * A second reminder arriving while one is showing reuses this instance
     * (singleTask in the manifest), so the new extras have to replace the old.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AlarmSoundPlayer.stop()
        soundStarted = false
        render(intent)
    }

    private fun render(intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val triggerSummary = intent.getStringExtra(EXTRA_TRIGGER_SUMMARY).orEmpty()

        if (ruleId == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val rule = repository.rule(ruleId)
            if (rule == null) {
                finish()
                return@launch
            }

            if (rule.delivery.mode == DeliveryMode.ALARM && !soundStarted) {
                AlarmSoundPlayer.start(this@ReminderOverlayActivity, rule.delivery)
                soundStarted = true
            }

            val themeMode = settings.themeMode.first()
            val dynamicColor = settings.dynamicColor.first()

            setContent {
                RemiitTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                    ReminderOverlayScreen(
                        rule = rule,
                        triggerSummary = triggerSummary,
                        onComplete = { respond(rule, ReminderOutcome.COMPLETED) },
                        onIncomplete = { respond(rule, ReminderOutcome.INCOMPLETE) },
                        onSnooze = { respond(rule, ReminderOutcome.SNOOZED) },
                        onExpire = { respond(rule, ReminderOutcome.EXPIRED) },
                    )
                }
            }
        }
    }

    /**
     * Routes the response through [ReminderActionReceiver] rather than writing
     * it here, so the overlay and the notification actions cannot disagree about
     * what happened — there is exactly one place that records an outcome,
     * silences the alarm and clears the shade.
     */
    private fun respond(rule: ReminderRule, outcome: ReminderOutcome) {
        val action = when (outcome) {
            ReminderOutcome.COMPLETED -> ReminderActionReceiver.ACTION_COMPLETE
            ReminderOutcome.INCOMPLETE -> ReminderActionReceiver.ACTION_INCOMPLETE
            ReminderOutcome.SNOOZED -> ReminderActionReceiver.ACTION_SNOOZE
            else -> ReminderActionReceiver.ACTION_DISMISS
        }

        sendBroadcast(
            Intent(this, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(ReminderActionReceiver.EXTRA_EVENT_ID, eventId)
                putExtra(ReminderActionReceiver.EXTRA_RULE_ID, rule.id)
            }
        )
        AlarmSoundPlayer.stop()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismissReceiver) }
        // Only the alarm sound this instance started is stopped here. A stray
        // stop would silence a reminder that a newer overlay is still showing.
        if (soundStarted) AlarmSoundPlayer.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TRIGGER_SUMMARY = "trigger_summary"
    }
}
