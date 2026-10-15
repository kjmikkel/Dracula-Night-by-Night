package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.Arrivals
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.isReadable
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
)

private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")

/** What the timeline shows at [now]: readable entries grouped by in-story day, plus the next one to unlock. */
fun buildTimeline(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId): TimelineState {
    val (unlocked, locked) = story.entries.partition { isReadable(it, settings, user, now, zone) }
    val rows = unlocked.map { e ->
        val progress = user.progressOf(e.id)
        e.date to TimelineRow(
            id = e.id,
            authorName = story.nameOf(e.author) ?: e.author,
            type = e.type,
            timeLabel = e.time.format(HOUR_MINUTE),
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
    )
}
