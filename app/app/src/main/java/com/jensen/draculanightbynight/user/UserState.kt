package com.jensen.draculanightbynight.user

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDate

private const val MODE_CALENDAR = "calendar"
private const val MODE_TEMPO = "tempo"
private const val MODE_FREE = "free"

/** Per-entry reading progress, keyed by the dataset's stable entry id. */
@Serializable
data class EntryProgress(
    val readAtMillis: Long? = null,
    val blockIndex: Int = 0,
    val blockOffset: Int = 0,
    val bookmarked: Boolean = false,
)

/**
 * Everything the app remembers about its reader. Stored as JSON, separately from the story,
 * so a dataset update never touches it; progress for ids that no longer exist is simply ignored.
 * Every field has a default, so files written by older versions load unchanged.
 */
@Serializable
data class UserState(
    val mode: String? = null,
    val seasonYear: Int? = null,
    val tempoStart: String? = null,
    val skipAheadMinutes: Long = 0,
    val progress: Map<String, EntryProgress> = emptyMap(),
    val onboarded: Boolean = false,
    /** The reader wants notifications (Android may still block them). */
    val notify: Boolean = true,
    /** Notifications cover entries scheduled after this instant (epoch milliseconds). */
    val lastNotifiedAt: Long? = null,
    val textSize: TextSize = TextSize.NORMAL,
    val readingFont: ReadingFont = ReadingFont.SERIF,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** null follows Android's contrast setting; true or false once the reader has chosen. */
    val highContrast: Boolean? = null,
    /** The year whose new-season prompt the reader has answered. */
    val seasonPromptYear: Int? = null,
) {
    /** Readers from before the Welcome screen already chose a mode, so they count as onboarded. */
    val isOnboarded: Boolean
        get() = onboarded || readingMode != null

    val readingMode: ReadingMode?
        get() = when (mode) {
            MODE_CALENDAR -> seasonYear?.let { ReadingMode.Calendar(it) }
            // An unreadable date counts as no mode, so ensureMode() repairs it instead of every launch crashing.
            MODE_TEMPO -> tempoStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { ReadingMode.SameTempo(it) }
            MODE_FREE -> ReadingMode.Free
            else -> null
        }

    val settings: ReadingSettings?
        get() = readingMode?.let { ReadingSettings(it, Duration.ofMinutes(skipAheadMinutes)) }

    fun withMode(newMode: ReadingMode): UserState = when (newMode) {
        is ReadingMode.Calendar -> copy(mode = MODE_CALENDAR, seasonYear = newMode.seasonYear, skipAheadMinutes = 0)
        is ReadingMode.SameTempo -> copy(mode = MODE_TEMPO, tempoStart = newMode.startDate.toString(), skipAheadMinutes = 0)
        ReadingMode.Free -> copy(mode = MODE_FREE, skipAheadMinutes = 0)
    }

    fun progressOf(id: String): EntryProgress = progress[id] ?: EntryProgress()

    fun updateProgress(id: String, change: (EntryProgress) -> EntryProgress): UserState =
        copy(progress = progress + (id to change(progressOf(id))))
}

object UserStateSerializer : Serializer<UserState> {
    // Unknown fields (from a newer version) are skipped; an unknown choice such as "textSize": "huge" falls back to the default.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override val defaultValue: UserState = UserState()

    override suspend fun readFrom(input: InputStream): UserState = try {
        json.decodeFromString(UserState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable user state", e)
    }

    override suspend fun writeTo(t: UserState, output: OutputStream) {
        output.write(json.encodeToString(UserState.serializer(), t).encodeToByteArray())
    }
}
