package com.rahulgorai.remiit.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import java.time.Clock
import java.time.ZoneId

/**
 * Reading and writing the backup through the Storage Access Framework.
 *
 * SAF rather than a path, so the app needs no storage permission at all: the
 * user picks the destination in the system file picker and the app is handed a
 * URI for that one file. It also means the backup can land in Drive, or on a
 * USB stick, without this code knowing the difference — which matters for a
 * file whose whole purpose is surviving the phone it was made on.
 */
object BackupFiles {

    /**
     * What the picker filters on when saving.
     *
     * Not used when opening. A JSON file arrives back as `application/json`,
     * `text/plain`, `application/octet-stream` or something else entirely
     * depending on which app or cloud provider wrote it, and a filter that
     * guesses wrong hides the user's own backup from them. Opening therefore
     * filters nothing and validates the contents instead — see
     * [BackupManager.import], which has to reject nonsense anyway.
     */
    const val MIME_TYPE = "application/json"

    /** `remiit-backup-2026-09-21.json`. Dated so successive backups do not collide. */
    fun suggestedName(clock: Clock = Clock.systemDefaultZone()): String {
        // atZone().toLocalDate() rather than LocalDate.ofInstant, which is
        // API 34 — this app runs on 33.
        val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
        return "remiit-backup-$today.json"
    }

    /** Returns false if the file could not be written; the caller tells the user. */
    fun write(context: Context, uri: Uri, contents: String): Boolean = runCatching {
        // "wt" truncates. Plain "w" leaves any bytes past the new content in
        // place, so overwriting a larger previous backup would produce a file
        // with valid JSON followed by the tail of the old one — which then
        // fails to parse on import, long after the mistake.
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(contents.toByteArray())
        } ?: return false
        true
    }.onFailure { Log.e(TAG, "Could not write backup", it) }.getOrDefault(false)

    /** Returns null if the file could not be read. */
    fun read(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.onFailure { Log.e(TAG, "Could not read backup", it) }.getOrNull()

    private const val TAG = "BackupFiles"
}
