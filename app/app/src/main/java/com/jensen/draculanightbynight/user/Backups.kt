package com.jensen.draculanightbynight.user

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.domain.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration

/**
 * The file the app keeps up to date (#205). It belongs to this phone: it is never in a backup, and loading one
 * leaves it as it is. [failed] when the last update could not be written (the file was moved, or access withdrawn).
 */
data class CopyTarget(val uri: String, val name: String?, val savedAtMillis: Long?, val failed: Boolean = false)

/**
 * Backup files (#205): saving one where the reader chooses, sharing one, loading one, and keeping one chosen file up
 * to date. Everything goes through Android's own file pickers and share sheet, so the app never goes online itself; a
 * cloud app the reader picks does the uploading.
 *
 * @param copyDelay how long the state must stay unchanged before the kept copy is rewritten (scrolling saves often).
 */
@OptIn(FlowPreview::class)
class Backups(
    private val context: Context,
    private val users: UserRepository,
    private val time: TimeSource,
    scope: CoroutineScope,
    copyDelay: Duration = Duration.ofSeconds(5),
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _target = MutableStateFlow(readTarget())

    /** The file kept up to date, or null when none is. */
    val target: StateFlow<CopyTarget?> = _target.asStateFlow()

    init {
        scope.launch {
            users.state.map(::encodeBackup).distinctUntilChanged().debounce(copyDelay.toMillis()).collect { text ->
                _target.value?.let { updateCopy(it, text) }
            }
        }
    }

    /** "Dracula Night by Night 2026-10-18.json": what the save picker suggests. */
    fun suggestedName(): String = "Dracula Night by Night ${time.today().format(Dates.ISO_DAY)}.json"

    /** Writes the reader's state to [uri]; false if it could not be written. */
    suspend fun save(uri: Uri): Boolean = write(uri, encodeBackup(users.state.first()))

    /** The text of the file at [uri], or null if it could not be read. */
    suspend fun read(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } catch (_: Exception) {
            null
        }
    }

    /** A share sheet with the reader's state attached as a file: email, messages, a cloud app. */
    suspend fun shareIntent(): Intent {
        val name = suggestedName()
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, SHARE_DIR).apply { mkdirs() }.listFiles()?.forEach { it.delete() }
            File(File(context.cacheDir, SHARE_DIR), name).apply { writeText(encodeBackup(users.state.first())) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name.removeSuffix(".json"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share your reading")
    }

    /** Keeps [uri] (a file the reader just created) up to date from now on, starting with a copy now. */
    suspend fun keepCopy(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Without a lasting grant the copy would stop working at the next restart; a provider may not offer one.
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        _target.value?.takeIf { it.uri != uri.toString() }?.let(::release)
        val target = CopyTarget(uri.toString(), displayName(uri), savedAtMillis = null)
        storeTarget(target)
        updateCopy(target, encodeBackup(users.state.first()))
    }

    /** No longer keeps a copy; the file itself stays where it is. */
    fun stopCopy() {
        _target.value?.let(::release)
        storeTarget(null)
    }

    private suspend fun updateCopy(target: CopyTarget, text: String) {
        val ok = write(Uri.parse(target.uri), text)
        // The reader may have stopped or chosen another file while this was writing.
        if (_target.value?.uri == target.uri) {
            storeTarget(target.copy(savedAtMillis = if (ok) time.millis() else target.savedAtMillis, failed = !ok))
        }
    }

    private suspend fun write(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // "wt" truncates, so a shorter state never leaves the end of a longer one behind.
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } != null
        } catch (_: Exception) {
            false
        }
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    } ?: uri.lastPathSegment

    private fun release(target: CopyTarget) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(target.uri), flags) }
    }

    private fun readTarget(): CopyTarget? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        return CopyTarget(
            uri = uri,
            name = prefs.getString(KEY_NAME, null),
            savedAtMillis = prefs.getLong(KEY_SAVED_AT, -1).takeIf { it >= 0 },
            failed = prefs.getBoolean(KEY_FAILED, false),
        )
    }

    private fun storeTarget(target: CopyTarget?) {
        prefs.edit().apply {
            if (target == null) {
                clear()
            } else {
                putString(KEY_URI, target.uri)
                putString(KEY_NAME, target.name)
                putLong(KEY_SAVED_AT, target.savedAtMillis ?: -1)
                putBoolean(KEY_FAILED, target.failed)
            }
        }.apply()
        _target.value = target
    }

    companion object {
        /** What the pickers and the share sheet are told the file is. */
        const val MIME = "application/json"

        /** The FileProvider's authority, after the package name (see the manifest). */
        const val AUTHORITY_SUFFIX = ".backups"

        private const val SHARE_DIR = "backups"
        private const val PREFS = "backup_copy"
        private const val KEY_URI = "uri"
        private const val KEY_NAME = "name"
        private const val KEY_SAVED_AT = "savedAt"
        private const val KEY_FAILED = "failed"
    }
}
