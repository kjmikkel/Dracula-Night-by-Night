package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.content.storyTime
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.Arrivals
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.SeasonPrompt
import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.domain.metVoices
import com.jensen.draculanightbynight.domain.readingOrder
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
    /** The saved filter; it narrows [days] only. */
    val filter: EntryFilter = EntryFilter(),
    /** Readable entries the filter hides. */
    val hiddenCount: Int = 0,
    /** The card that leads straight into what has arrived; null when nothing unread is waiting. */
    val tonight: TonightCard? = null,
    /** Entries in the whole story not yet read, whatever the filter shows. */
    val unreadCount: Int = 0,
    /** The voices the filter sheet offers: only those the reader has met (#220). */
    val voices: List<Voice> = listOf(Voice.OTHERS),
) {
    /** Every entry has been read: the only time the end card says so (#189). */
    val allRead: Boolean get() = unreadCount == 0
}

/** One entry as a list row; the timeline and the bookmarks list build rows the same way. */
fun timelineRow(story: Story, user: UserState, entry: Entry): TimelineRow {
    val progress = user.progressOf(entry.id)
    return TimelineRow(
        id = entry.id,
        authorName = story.nameOf(entry.author) ?: entry.author,
        type = entry.type,
        timeLabel = entry.storyTime.orEmpty(),
        timeEstimated = entry.timeEstimated && !entry.undated,
        place = entry.place,
        preview = entry.preview,
        unread = progress.readAtMillis == null,
        bookmarked = progress.bookmarked,
    )
}

/**
 * What the timeline shows at [now]: readable entries matching [filter], grouped by in-story day, plus the next one
 * to unlock. Everything but the list (Next card, end, skip ahead, season card) uses the whole story.
 */
fun buildTimeline(
    story: Story,
    user: UserState,
    settings: ReadingSettings,
    now: Instant,
    zone: ZoneId,
    filter: EntryFilter = EntryFilter(),
): TimelineState {
    val (unlocked, locked) = story.entries.partition { isReadable(it, settings, user, now, zone) }
    val shown = readingOrder(story, user, settings, now, zone, filter)
    val rows = shown.map { e -> e.date to timelineRow(story, user, e) }
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
        filter = filter,
        hiddenCount = unlocked.size - shown.size,
        tonight = tonightCard(story, user, settings, now, zone, filter),
        unreadCount = story.entries.count { user.progressOf(it.id).readAtMillis == null },
        voices = metVoices(story, user),
    )
}

/**
 * The list item the timeline opens at: the item just above the first unread entry (its day's header when it is the
 * day's first row), or the top when nothing is unread. The list has one header item per day, then its rows.
 */
fun openingItem(state: TimelineState): Int {
    var index = 0
    for (day in state.days) {
        index++
        for (row in day.rows) {
            if (row.id == state.firstUnreadId) return index - 1
            index++
        }
    }
    return 0
}

/**
 * The list item of the row for [id], counted like [openingItem] (one header per day, then its rows, without the
 * optional cards item); null when the list does not show it.
 */
fun rowItem(state: TimelineState, id: String): Int? {
    var index = 0
    for (day in state.days) {
        index++
        for (row in day.rows) {
            if (row.id == id) return index
            index++
        }
    }
    return null
}
