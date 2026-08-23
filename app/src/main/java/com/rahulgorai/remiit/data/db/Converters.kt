package com.rahulgorai.remiit.data.db

import androidx.room.TypeConverter
import com.rahulgorai.remiit.data.model.DeliveryConfig
import com.rahulgorai.remiit.data.model.ReminderOutcome
import com.rahulgorai.remiit.data.model.RuleConstraints
import com.rahulgorai.remiit.data.model.Trigger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Room <-> JSON for the composite rule fields.
 *
 * [RemiitJson] is lenient on purpose. Rules are long-lived documents: a build
 * that adds a field to [DeliveryConfig] must still be able to read rows written
 * by the previous build, and `ignoreUnknownKeys` plus `encodeDefaults` is what
 * makes that a non-event in both directions. Without it, adding one field turns
 * every saved rule into a crash on next launch.
 */
val RemiitJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

private val triggerListSerializer = ListSerializer(Trigger.serializer())

class Converters {

    @TypeConverter
    fun triggersToJson(value: List<Trigger>): String =
        RemiitJson.encodeToString(triggerListSerializer, value)

    @TypeConverter
    fun jsonToTriggers(value: String): List<Trigger> =
        if (value.isBlank()) emptyList()
        else RemiitJson.decodeFromString(triggerListSerializer, value)

    @TypeConverter
    fun deliveryToJson(value: DeliveryConfig): String = RemiitJson.encodeToString(value)

    @TypeConverter
    fun jsonToDelivery(value: String): DeliveryConfig =
        if (value.isBlank()) DeliveryConfig() else RemiitJson.decodeFromString(value)

    @TypeConverter
    fun constraintsToJson(value: RuleConstraints): String = RemiitJson.encodeToString(value)

    @TypeConverter
    fun jsonToConstraints(value: String): RuleConstraints =
        if (value.isBlank()) RuleConstraints() else RemiitJson.decodeFromString(value)

    @TypeConverter
    fun outcomeToName(value: ReminderOutcome): String = value.name

    @TypeConverter
    fun nameToOutcome(value: String): ReminderOutcome =
        runCatching { ReminderOutcome.valueOf(value) }.getOrDefault(ReminderOutcome.PENDING)
}
