package com.rahulgorai.remiit.automation

import com.rahulgorai.remiit.data.model.AutomationEdge

/**
 * Something changing in the phone's surroundings, before anything has been
 * matched against it.
 *
 * The counterpart of [com.rahulgorai.remiit.engine.TriggerEvent], and
 * deliberately shaped the other way round. A TriggerEvent already names the
 * rule it belongs to, because the Wi-Fi monitor does that matching itself. A
 * signal names only what happened, and [AutomationSink] works out what it
 * means — which is what lets one Wi-Fi callback feed both systems without the
 * monitor knowing anything about automations.
 */
sealed interface AutomationSignal {

    data class Wifi(val ssid: String, val edge: AutomationEdge) : AutomationSignal

    data class Bluetooth(val address: String, val edge: AutomationEdge) : AutomationSignal

    /**
     * Already attributed, unlike the other two.
     *
     * Geofences are registered per automation and come back carrying the
     * request id they were registered under, so the automation is known before
     * the engine sees it. Matching by coordinates instead would mean deciding
     * whether two automations 40 metres apart are the same place.
     */
    data class Geofence(val automationId: String, val edge: AutomationEdge) : AutomationSignal
}

/** Receives [AutomationSignal]s. Implemented by [AutomationEngine]. */
interface AutomationSink {
    suspend fun onSignal(signal: AutomationSignal)
}
