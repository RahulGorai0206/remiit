package com.rahulgorai.remiit.data.model

/**
 * Plain-language descriptions of an automation.
 *
 * Kept here rather than in the screens because the same words are wanted in
 * three places — the list card, the editor's preview line and the "last run"
 * record — and three independent phrasings of the same automation is how a UI
 * starts contradicting itself.
 */

/** "Office Wi-Fi connects", "Car disconnects", "Arrive at Home". */
fun AutomationTrigger.summary(): String = when (this) {
    is AutomationTrigger.Wifi -> when (edge) {
        AutomationEdge.ENTER -> "Connect to $ssid"
        AutomationEdge.EXIT -> "Disconnect from $ssid"
    }

    is AutomationTrigger.Bluetooth -> {
        val device = deviceName.ifBlank { address }
        when (edge) {
            AutomationEdge.ENTER -> "Connect to $device"
            AutomationEdge.EXIT -> "Disconnect from $device"
        }
    }

    is AutomationTrigger.Location -> {
        val place = label.ifBlank { "%.4f, %.4f".format(latitude, longitude) }
        when (edge) {
            AutomationEdge.ENTER -> "Arrive at $place"
            AutomationEdge.EXIT -> "Leave $place"
        }
    }
}

/** The verb pair for a trigger kind, for the editor's two-way choice. */
fun AutomationEdge.label(kind: AutomationTriggerKind): String = when (kind) {
    AutomationTriggerKind.LOCATION ->
        if (this == AutomationEdge.ENTER) "Arriving" else "Leaving"

    else -> if (this == AutomationEdge.ENTER) "Connecting" else "Disconnecting"
}

fun SoundSetting.label(): String = when (this) {
    SoundSetting.SILENT -> "Silent"
    SoundSetting.VIBRATE -> "Vibrate"
    SoundSetting.RING -> "Ring"
    SoundSetting.DND_ON -> "Do Not Disturb on"
    SoundSetting.DND_OFF -> "Do Not Disturb off"
}

/** "Silent · Adaptive brightness off". Empty when the automation does nothing. */
fun AutomationActions.summary(): String = listOfNotNull(
    sound?.label(),
    autoBrightness?.let { if (it) "Adaptive brightness on" else "Adaptive brightness off" },
).joinToString(" · ")
