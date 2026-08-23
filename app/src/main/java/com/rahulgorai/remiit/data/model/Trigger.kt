package com.rahulgorai.remiit.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A condition that can cause a [ReminderRule] to fire.
 *
 * Triggers are stored as polymorphic JSON inside the rule row rather than in
 * separate tables. That keeps a rule a single self-contained document, which
 * matters for two reasons: adding a new trigger kind needs no schema
 * migration, and the planned on-device AI can emit a complete rule as one JSON
 * object without knowing anything about the database.
 *
 * The [SerialName] discriminators are part of the persisted format — renaming
 * one silently orphans every saved rule that used it.
 */
@Serializable
sealed interface Trigger {

    /** Stable identity so a trigger can be tracked across edits and in match state. */
    val id: String

    /** Time or date based. Covers one-shot, daily, weekly, monthly and interval. */
    @Serializable
    @SerialName("time")
    data class Time(
        override val id: String,
        val recurrence: Recurrence,
        /** IANA zone id, or null to follow the device's current zone. */
        val timeZoneId: String? = null,
    ) : Trigger

    /** Joining or leaving a named Wi-Fi network. */
    @Serializable
    @SerialName("wifi")
    data class Wifi(
        override val id: String,
        val ssid: String,
        val event: WifiEvent,
    ) : Trigger

    /** Crossing the boundary of a circular geofence. */
    @Serializable
    @SerialName("location")
    data class Location(
        override val id: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val event: LocationEvent,
        /** Human-readable name shown in the UI, e.g. "Office". */
        val label: String = "",
        /** Only meaningful for [LocationEvent.DWELL]. */
        val dwellMinutes: Int = 5,
    ) : Trigger

    /**
     * Another app being brought to the foreground.
     *
     * An empty [packages] means *any* app, which is the "remind me whenever I
     * open my phone" case. [excludes] then carves out the apps that would make
     * that unbearable — the launcher, the dialer, Remiit itself.
     */
    @Serializable
    @SerialName("app_launch")
    data class AppLaunch(
        override val id: String,
        val packages: Set<String> = emptySet(),
        val excludes: Set<String> = emptySet(),
    ) : Trigger {
        fun matches(packageName: String): Boolean =
            packageName !in excludes && (packages.isEmpty() || packageName in packages)
    }
}

@Serializable
enum class WifiEvent { CONNECTED, DISCONNECTED }

@Serializable
enum class LocationEvent { ENTER, EXIT, DWELL }

/** How the triggers on a rule combine. */
@Serializable
enum class MatchMode {
    /** Any single trigger firing is enough. */
    ANY,

    /**
     * Every trigger must have fired within
     * [RuleConstraints.matchWindowMinutes] of each other. This is what makes
     * "on office Wi-Fi *and* after 3pm" expressible.
     */
    ALL,
}

/** Coarse grouping used for UI sectioning and icon choice. */
enum class TriggerKind { TIME, WIFI, LOCATION, APP_LAUNCH }

val Trigger.kind: TriggerKind
    get() = when (this) {
        is Trigger.Time -> TriggerKind.TIME
        is Trigger.Wifi -> TriggerKind.WIFI
        is Trigger.Location -> TriggerKind.LOCATION
        is Trigger.AppLaunch -> TriggerKind.APP_LAUNCH
    }
