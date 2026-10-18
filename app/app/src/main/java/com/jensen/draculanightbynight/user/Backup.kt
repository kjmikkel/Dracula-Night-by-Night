package com.jensen.draculanightbynight.user

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Marks a file as this app's backup, so any other JSON is refused rather than loaded as an empty reader. */
private const val BACKUP_APP = "dracula-night-by-night"
private const val BACKUP_VERSION = 1

@Serializable
private data class BackupFile(val app: String, val version: Int, val state: JsonElement)

/** Everything the app remembers, as a backup file's text (#205). */
fun encodeBackup(state: UserState): String =
    userStateJson.encodeToString(BackupFile.serializer(), BackupFile(BACKUP_APP, BACKUP_VERSION, userStateJson.encodeToJsonElement(UserState.serializer(), state)))

/**
 * The state in a backup file, or null when it isn't one of this app's or can't be read. A file from a newer version
 * loads what this one understands, as the app's own file does.
 */
fun decodeBackup(text: String): UserState? = try {
    userStateJson.decodeFromString(BackupFile.serializer(), text)
        .takeIf { it.app == BACKUP_APP }
        ?.let { userStateJson.decodeFromJsonElement(UserState.serializer(), it.state) }
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/** Something read or bookmarked: an import over it asks first. */
val UserState.hasProgress: Boolean
    get() = progress.values.any { it.readAtMillis != null || it.bookmarked }

/**
 * Loading a backup replaces everything with [backup]. The reader has begun (the file may predate Welcome), and the
 * notification baseline is [nowMillis], so nothing that has already arrived is announced.
 */
fun restored(backup: UserState, nowMillis: Long): UserState = backup.copy(onboarded = true, lastNotifiedAt = nowMillis)
