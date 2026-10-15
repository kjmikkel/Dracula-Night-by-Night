package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.Arrivals
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.SeasonPrompt
import com.jensen.draculanightbynight.domain.isReadable
import com.jensen.draculanightbynight.user.UserState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class TimelineRow(
    val id: String,
    val authorName: String,
    val type: DocumentType,
    val timeLabel: String,
    val timeEstimated: Boolean,
    val place: String?,
    val preview: String,
    val unread: Boolean,
    val bookmarked: Boolean,
)

data class TimelineDay(val date: LocalDate, val rows: List<TimelineRow>)

/** The next entry to arrive: who writes, what kind of document, and when it really arrives (no text). */
data class NextTeaser(val authorName: String, val type: DocumentType, val unlocksAt: ZonedDateTime)

data class TimelineState(
    val days: List<TimelineDay>,
    val next: NextTeaser?,
    val firstUnreadId: String?,
    val unlockedCount: Int,
    val total: Int,
    /** How far ahead of real time the reader is (zero in real time). */
    val skipAhead: Duration = Duration.ZERO,
    /** Nothing is left to arrive: the end card replaces the Next card. */
    val finished: Boolean = false,
    /** The new-season card shows at the top. */
    val seasonPromptDue: Boolean = false,
)

/** What the timeline shows at [now]: readable entries grouped by in-story day, plus the next one to unlock. */
fun buildTimeline(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId): TimelineState {
    val (unlocked, locked) = story.entries.partition { isReadable(it, settings, user, now, zone) }
    val rows = unlocked.map { e ->
        val progress = user.progressOf(e.id)
        e.date to TimelineRow(
            id = e.id,
            authorName = story.nameOf(e.author) ?: e.author,
            type = e.type,
            timeLabel = e.time.format(Dates.TIME),
            timeEstimated = e.timeEstimated,
            place = e.place,
            preview = e.preview,
            unread = progress.readAtMillis == null,
            bookmarked = progress.bookmarked,
        )
    }
    val days = rows.groupBy({ it.first }, { it.second }).map { (date, dayRows) -> TimelineDay(date, dayRows) }
    val next = locked
        .mapNotNull { e -> Arrivals.arrivesAt(e, settings, zone)?.let { e to it } }
        .minByOrNull { it.second }
        ?.let { (e, at) -> NextTeaser(story.nameOf(e.author) ?: e.author, e.type, at.atZone(zone)) }
    return TimelineState(
        days = days,
        next = next,
        firstUnreadId = rows.firstOrNull { it.second.unread }?.second?.id,
        unlockedCount = unlocked.size,
        total = story.entries.size,
        skipAhead = settings.skipAhead,
        finished = next == null,
        seasonPromptDue = SeasonPrompt.isDue(user, now.atZone(zone).toLocalDate()),
    )
}
