package com.jensen.draculanightbynight.bookmarks

import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.content.typeLabel
import com.jensen.draculanightbynight.domain.Arrivals
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.isReadable
import com.jensen.draculanightbynight.timeline.TimelineRow
import com.jensen.draculanightbynight.timeline.timelineRow
import com.jensen.draculanightbynight.user.BookmarkSort
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A bookmarked entry. [arrivesAt] is null when it can be read now; otherwise (after Start over, which keeps
 * bookmarks but clears read state) it is when the schedule brings it back.
 */
data class BookmarkRow(
    val row: TimelineRow,
    val date: LocalDate,
    val bookmarkedAt: Instant?,
    val arrivesAt: ZonedDateTime?,
    /** The reader's own day the bookmark was made, in their zone. */
    val bookmarkedOn: LocalDate? = null,
)

/** Every bookmarked entry still in the story, in story order or newest bookmark first (undated ones last). */
fun bookmarks(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId, sort: BookmarkSort): List<BookmarkRow> {
    val rows = story.entries
        .filter { user.progressOf(it.id).bookmarked }
        .map { entry ->
            BookmarkRow(
                row = timelineRow(story, user, entry),
                date = entry.date,
                bookmarkedAt = user.progressOf(entry.id).bookmarkedAtMillis?.let(Instant::ofEpochMilli),
                arrivesAt = if (isReadable(entry, settings, user, now, zone)) null else Arrivals.arrivesAt(entry, settings, zone)?.atZone(zone),
                bookmarkedOn = user.progressOf(entry.id).bookmarkedAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
            )
        }
    return when (sort) {
        BookmarkSort.STORY -> rows
        // Stable sort: rows that tie (no time stamp) keep story order.
        BookmarkSort.RECENT -> rows.sortedWith(compareByDescending<BookmarkRow> { it.bookmarkedAt != null }.thenByDescending { it.bookmarkedAt })
    }
}

/**
 * What TalkBack says for a locked row: that it is locked and when it comes back, then which entry it is (a
 * description replaces the row's own text, so it has to name the entry). Null for a readable row.
 */
fun lockedDescription(row: BookmarkRow): String? = row.arrivesAt?.let { at ->
    "Locked, arrives again ${at.format(Dates.WEEKDAY_TIME)}. ${row.date.format(Dates.DAY)}, ${row.row.timeLabel}, " +
        "${row.row.authorName}, ${typeLabel(row.row.type)}"
}
