package com.rahulgorai.remiit.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A change to the phone's settings, made automatically when the surroundings
 * change.
 *
 * The sibling of [ReminderRule], and deliberately not the same thing. A rule
 * asks the user to do something; an automation does something *to the device*
 * and expects no response. That difference runs all the way down — there is no
 * delivery mode, no history of answered/unanswered, no snooze — so folding the
 * two together would have meant a rule type where most of a rule is meaningless.
 *
 * What they do share is the environment, and that is shared at the source: the
 * Wi-Fi monitor and the geofence layer feed both.
 *
 * One trigger, not a list. "On office Wi-Fi *and* after 3pm, go silent" is a
 * genuinely useful thing to want, but it needs the match-window machinery
 * [RuleEngine] has and a story for what happens when one half stops being true.
 * A single edge — this happened, do this — has no such ambiguity, and it is
 * what every one of these automations is actually for.
 */
@Entity(tableName = "automations")
@Serializable
data class Automation(
    @PrimaryKey
    val id: String,

    val name: String,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    val trigger: AutomationTrigger,

    val actions: AutomationActions = AutomationActions(),

    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long = 0L,

    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long = 0L,

    /**
     * When this last did something, and how it went.
     *
     * Persisted rather than kept in memory because the whole point of an
     * automation is that it runs while nobody is looking. Without a record, a
     * silent failure — a revoked permission, an OEM that refuses the write —
     * is indistinguishable from an automation that has simply not been
     * triggered yet, and the user has no way to tell which.
     */
    @ColumnInfo(name = "last_run_at")
    val lastRunAtEpochMillis: Long = 0L,

    @ColumnInfo(name = "last_result")
    val lastResult: String = "",

    /**
     * Whether that last run did what it was asked.
     *
     * Stored rather than inferred by comparing [lastResult] against what the
     * actions describe today — those two stop matching the moment the user
     * edits the automation, which would relabel a perfectly good run as a
     * failure for no reason the user could see.
     */
    @ColumnInfo(name = "last_run_ok")
    val lastRunSucceeded: Boolean = true,
) {
    /** An automation with nothing to do is saveable but pointless; the editor blocks it. */
    val isValid: Boolean get() = name.isNotBlank() && actions.isNotEmpty
}

/** Which way the environment changed. Worded per trigger kind in the UI. */
@Serializable
enum class AutomationEdge {
    /** Connected to the network or device, or arrived at the place. */
    ENTER,

    /** Disconnected from it, or left. */
    EXIT,
}

/**
 * What the phone has to notice for an automation to run.
 *
 * Separate from [Trigger] on purpose. The overlap is real but partial — there
 * is no time or app-launch automation here, and no Bluetooth reminder — and a
 * shared sealed type would have meant every consumer of either handling cases
 * that cannot occur for it. The serial names are still the persisted contract.
 */
@Serializable
sealed interface AutomationTrigger {

    val edge: AutomationEdge

    @Serializable
    @SerialName("wifi")
    data class Wifi(
        val ssid: String,
        override val edge: AutomationEdge,
    ) : AutomationTrigger

    /**
     * A specific paired Bluetooth device connecting or disconnecting.
     *
     * Matched on [address] rather than name: the name is user-editable and
     * locale-dependent, and on Android 12+ reading it at all needs
     * BLUETOOTH_CONNECT — so an automation keyed on the name would break the
     * moment that permission was revoked. [deviceName] is a cached label for
     * the UI and nothing more.
     */
    @Serializable
    @SerialName("bluetooth")
    data class Bluetooth(
        val address: String,
        val deviceName: String = "",
        override val edge: AutomationEdge,
    ) : AutomationTrigger

    @Serializable
    @SerialName("location")
    data class Location(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val label: String = "",
        override val edge: AutomationEdge,
    ) : AutomationTrigger
}

/** Coarse grouping, for icons and for deciding what has to be registered. */
enum class AutomationTriggerKind { WIFI, BLUETOOTH, LOCATION }

val AutomationTrigger.kind: AutomationTriggerKind
    get() = when (this) {
        is AutomationTrigger.Wifi -> AutomationTriggerKind.WIFI
        is AutomationTrigger.Bluetooth -> AutomationTriggerKind.BLUETOOTH
        is AutomationTrigger.Location -> AutomationTriggerKind.LOCATION
    }

/**
 * What to change when the trigger fires.
 *
 * A record of optional settings rather than a list of action objects. Each
 * setting can be changed at most once per automation — "go silent and also go
 * loud" is not a thing anyone means — and null is the honest way to say "leave
 * this one alone", which a list cannot express without a removal action.
 */
@Serializable
data class AutomationActions(
    val sound: SoundSetting? = null,

    /**
     * True turns adaptive brightness on, false off, null leaves it untouched.
     */
    val autoBrightness: Boolean? = null,
) {
    val isEmpty: Boolean get() = sound == null && autoBrightness == null
    val isNotEmpty: Boolean get() = !isEmpty
}

/**
 * The ringer/interruption state to put the phone into.
 *
 * Silent and the two Do Not Disturb settings are one list rather than two
 * because they are one decision from the user's side — "how quiet should this
 * place be" — even though underneath they are the ringer and the interruption
 * filter, which are different subsystems with different permissions.
 */
@Serializable
enum class SoundSetting {
    /** Ringer off entirely. */
    SILENT,

    /** Ringer off, vibration on. */
    VIBRATE,

    /** Ringer on. Also the way back from [SILENT]. */
    RING,

    /** Do Not Disturb on, priority-only — the same state as the quick tile. */
    DND_ON,

    /** Do Not Disturb off, leaving the ringer as it was. */
    DND_OFF,
}
