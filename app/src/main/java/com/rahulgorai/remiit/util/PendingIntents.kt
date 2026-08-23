package com.rahulgorai.remiit.util

import android.app.PendingIntent

/**
 * Flags for a PendingIntent whose extras must be read.
 *
 * FLAG_IMMUTABLE is required from Android 12 onward for any PendingIntent we do
 * not intend a third party to fill in, and every one in this app falls in that
 * category.
 */
const val PENDING_INTENT_FLAGS: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

/**
 * Stable request code for a (rule, trigger) pair.
 *
 * PendingIntent equality ignores extras, so two alarms that differ only by
 * their ruleId/triggerId extras would collide and overwrite each other unless
 * they carry distinct request codes. Derived rather than stored so cancelling
 * works without a lookup table.
 */
fun requestCodeFor(vararg parts: String): Int =
    parts.joinToString("|").hashCode() and 0x7FFFFFFF
