package com.rahulgorai.remiit.trigger.wifi

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** What the network picker knows about the airwaves right now. */
data class WifiScanState(
    val ssids: List<String> = emptyList(),
    val scanning: Boolean = false,
    /** False until a scan has completed, so "nothing found" is not shown too early. */
    val scanned: Boolean = false,
    val wifiEnabled: Boolean = true,
    val canList: Boolean = true,
)

/**
 * One scan, as two states: what we already know, then what we found.
 *
 * A flow rather than a suspend function returning the result, because the
 * cached results matter as much as the fresh ones — the platform scans on its
 * own schedule, so there is usually something to show immediately, and a picker
 * that sits empty for eight seconds reads as broken rather than as busy.
 *
 * Shared by the rule builder and the automation editor. Both offer the same
 * choice against the same radio, and two copies of this would eventually
 * disagree about what an empty list means.
 */
fun WifiNetworks.scan(context: Context): Flow<WifiScanState> = flow {
    emit(WifiScanState(scanning = true, ssids = cachedSsids(context)))
    val ssids = refreshSsids(context)
    emit(
        WifiScanState(
            ssids = ssids,
            scanning = false,
            scanned = true,
            wifiEnabled = isWifiEnabled(context),
            canList = canListNetworks(context),
        )
    )
}.flowOn(Dispatchers.IO)
