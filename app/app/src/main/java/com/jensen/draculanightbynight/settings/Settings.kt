package com.jensen.draculanightbynight.settings

import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.calendarArrivals
import com.jensen.draculanightbynight.domain.firstEntryIsClose
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice
import com.jensen.draculanightbynight.user.UserState
import java.time.Duration
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.user.CopyTarget
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Everything the Settings screen shows. */
data class SettingsState(
    val mode: ReadingMode,
    val modeLabel: String,
    val today: LocalDate,
    /** Same tempo's day one, or null in the other modes. */
    val tempoStart: LocalDate?,
    val ahead: Duration,
    /** The story days "Skip to a day" offers: every date that has an entry, in story order. */
    val storyDays: List<LocalDate>,
    /** Same tempo started today with the first timed entry under an hour away (or past): offer tomorrow. */
    val offerStartTomorrow: Boolean,
    val firstEntryAuthor: String?,
    val firstEntryTime: LocalTime?,
    val bookmarks: Int,
    val notify: Boolean,
    val textSize: TextSize,
    val readingFont: ReadingFont,
    val theme: ThemeChoice,
    val highContrast: Boolean?,
    val hidePartiesLine: Boolean,
    /** null follows TalkBack. */
    val skipRepeatedDates: Boolean?,
    /** Entries On the actual days would unlock at once today, out of [total], for the mode dialog (#193). */
    val calendarArrived: Int = 0,
    val total: Int = 0,
)

/** What the "Keep a copy up to date" row says (#205). */
fun copySummary(target: CopyTarget?, zone: ZoneId): String {
    if (target == null) return "Off. Choose a file, and it is updated as you read"
    val name = target.name ?: "the file"
    if (target.failed) return "Couldn't update $name. Choose the file again"
    val saved = target.savedAtMillis?.let { " · last saved ${Instant.ofEpochMilli(it).atZone(zone).format(Dates.DAY_TIME)}" }.orEmpty()
    return "Updating $name$saved"
}

fun modeLabel(mode: ReadingMode): String = when (mode) {
    is ReadingMode.Calendar -> "On the actual days (${mode.seasonYear})"
    is ReadingMode.SameTempo -> "In the same tempo"
    ReadingMode.Free -> "Read freely"
}

fun buildSettings(story: Story, user: UserState, now: ZonedDateTime): SettingsState {
    val mode = user.readingMode ?: ReadingMode.Free
    val today = now.toLocalDate()
    val tempoStart = (mode as? ReadingMode.SameTempo)?.startDate
    val first = story.firstTimedEntry
    return SettingsState(
        mode = mode,
        modeLabel = modeLabel(mode),
        today = today,
        tempoStart = tempoStart,
        ahead = Duration.ofMinutes(user.skipAheadMinutes),
        storyDays = story.entries.map { it.date }.distinct(),
        offerStartTomorrow = tempoStart == today && firstEntryIsClose(story, today, now.toInstant(), now.zone),
        firstEntryAuthor = first?.let { story.nameOf(it.author) },
        firstEntryTime = first?.time,
        bookmarks = user.progress.values.count { it.bookmarked },
        notify = user.notify,
        textSize = user.textSize,
        readingFont = user.readingFont,
        theme = user.theme,
        highContrast = user.highContrast,
        hidePartiesLine = user.hidePartiesLine,
        skipRepeatedDates = user.skipRepeatedDates,
        calendarArrived = calendarArrivals(story.entries, now.toInstant(), now.zone),
        total = story.entries.size,
    )
}
