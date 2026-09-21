package com.rahulgorai.remiit.ui.components

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The alarm tone, chosen through the system's own ringtone picker.
 *
 * The platform picker rather than a list of our own, for reasons that are not
 * just effort. It already knows every tone on the device — the bundled ones,
 * anything the user has added, and whatever the OEM ships — it plays each one
 * as you scroll, and it is the screen people already know from the Clock app.
 * A hand-rolled list would have to enumerate `RingtoneManager` itself, get
 * storage access right for user-added files, and build its own preview, to
 * arrive somewhere worse.
 *
 * Only shown for [com.rahulgorai.remiit.data.model.DeliveryMode.ALARM].
 * The other two modes are notifications, and a notification's sound belongs to
 * its channel — fixed when the channel is created and changeable only in system
 * settings after that. Offering a per-rule tone there would be a control that
 * silently does nothing.
 */
@Composable
fun RingtoneRow(
    /** Current selection, or null for the device's default alarm sound. */
    uri: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = rememberRingtoneTitle(uri)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.getParcelableExtra(
            RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
            Uri::class.java,
        )
        // A null pick is the "Default" entry — stored as null rather than as the
        // resolved URI so the rule keeps following the device default if the
        // user later changes it.
        onPick(picked?.toString())
    }

    // Built once and shared by the row and its icon: two copies of an eight-extra
    // Intent is two chances for them to drift apart.
    val open = {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            // No Silent entry. Null already means "the device default", so a
            // silent pick — which the picker also returns as null — would be
            // indistinguishable from it. An alarm nobody can hear is also not
            // what this mode is for; the Vibrate switch is the quiet option.
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri?.toUri())
        }
        // Guarded: a device with no ringtone picker is rare but real (minimal
        // or headless builds), and a crash from tapping a settings row is a
        // poor way to find that out.
        runCatching { launcher.launch(intent) }
        Unit
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = open)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Alarm sound",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(12.dp))
        // Bordered rather than a bare chevron, so the row reads as pressable at
        // a glance like every other control in the app. Pointed at the same
        // action as the row, so a tap on the icon does not fall through.
        BorderedIconButton(
            icon = Icons.Outlined.MusicNote,
            contentDescription = "Choose alarm sound",
            onClick = open,
        )
    }
}

/**
 * The tone's display name.
 *
 * Resolving a URI to a title can touch the media store, so it is remembered
 * against the URI rather than recomputed on every recomposition of the builder.
 * A tone that has since been deleted — an SD card removed, a file cleaned up —
 * resolves to nothing, and saying so is better than showing a raw content URI
 * or an empty row.
 */
@Composable
private fun rememberRingtoneTitle(uri: String?): String {
    val context = LocalContext.current
    return remember(uri) {
        if (uri == null) {
            "Default alarm sound"
        } else {
            runCatching {
                RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context)
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "Unavailable — tap to choose another"
        }
    }
}
