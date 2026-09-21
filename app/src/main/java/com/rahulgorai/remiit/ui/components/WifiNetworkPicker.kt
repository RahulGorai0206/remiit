package com.rahulgorai.remiit.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rahulgorai.remiit.trigger.wifi.WifiNetworks
import com.rahulgorai.remiit.trigger.wifi.WifiScanState
import com.rahulgorai.remiit.ui.theme.RemiitBorders
import com.rahulgorai.remiit.util.Permissions
import com.rahulgorai.remiit.util.openSettings

/**
 * Choosing a Wi-Fi network, for a rule or for an automation.
 *
 * Shared by both editors deliberately. They are asking the identical question
 * of the identical radio, and when this lived only in the rule builder the
 * automation editor grew a lesser version of it — a text field and the
 * remembered list, with no way to see what was actually in range. Two
 * implementations of one picker is how that happens.
 *
 * Free text underneath, always: an automation can name a network nowhere near
 * you, which is exactly what someone setting up "silent at the office" from
 * home is doing. The scan is a convenience over typing, not a constraint.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WifiNetworkPicker(
    ssid: String,
    onSsidChange: (String) -> Unit,
    scan: WifiScanState,
    knownSsids: Set<String>,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val currentSsid = remember(scan) { WifiNetworks.currentSsid(context) }
    val canList = remember(scan.canList) { WifiNetworks.canListNetworks(context) }

    // Requested here as well as on the permissions screen, because this is the
    // one moment the user can see what it buys them. Location is genuinely what
    // Wi-Fi scanning needs: NEARBY_WIFI_DEVICES covers Aware, P2P, RTT and
    // hotspot, and is not accepted by startScan or getScanResults.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.any { it }) onScan() }

    // Scan on open: an empty picker that needs a button press first reads as
    // broken rather than as merely idle.
    LaunchedEffect(Unit) { if (canList) onScan() }

    // The network you are on first — it is the single most likely answer — then
    // what is in range, then what you have named before.
    val choices = remember(currentSsid, knownSsids, scan.ssids) {
        (listOfNotNull(currentSsid) + scan.ssids + knownSsids).distinct()
    }

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChange,
            label = { Text("Network name (SSID)") },
            supportingText = {
                if (!Permissions.hasFineLocation(context)) {
                    // Not a warning about the picker — a warning that this will
                    // never match, which is far from obvious.
                    Text("Location permission is required to detect Wi-Fi networks.")
                } else if (!Permissions.areLocationServicesEnabled(context)) {
                    Text("Turn location services on, or Wi-Fi triggers cannot match.")
                }
            },
            singleLine = true,
            isError = ssid.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Networks in range", style = MaterialTheme.typography.titleSmall)
            TertiaryButton(
                text = if (scan.scanning) "Scanning…" else "Rescan",
                onClick = onScan,
                enabled = canList && !scan.scanning,
            )
        }

        if (scan.scanning) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (choices.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                choices.forEach { candidate ->
                    FilterChip(
                        selected = ssid == candidate,
                        onClick = { onSsidChange(candidate) },
                        label = { Text(candidate, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        border = RemiitBorders.interactive(),
                    )
                }
            }
        }

        val locationMissing = !Permissions.hasFineLocation(context)
        val hint = when {
            locationMissing -> "Listing networks needs location permission — the same one " +
                "needed to read a network name at all."
            !Permissions.areLocationServicesEnabled(context) ->
                "Turn location services on to list networks."
            !scan.wifiEnabled -> "Wi-Fi is off, so no networks can be listed. " +
                "You can still type a name."
            scan.scanned && choices.isEmpty() -> "No networks found. Android rate-limits " +
                "scans to four every two minutes, so try again shortly."
            else -> null
        }

        if (hint != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (locationMissing) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Grant location",
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!Permissions.areLocationServicesEnabled(context)) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Open location settings",
                    onClick = { context.openSettings(Permissions.locationSettings()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Android does not let apps read your phone's saved networks, so this " +
                "lists what is in range plus networks you have used before.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
