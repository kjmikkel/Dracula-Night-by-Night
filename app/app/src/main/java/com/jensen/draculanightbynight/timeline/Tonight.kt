package com.jensen.draculanightbynight.timeline

import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.Arrivals
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.readingOrder
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Where the parts of the day begin (hours). The day itself runs from morning to morning, so the small hours
// belong to the night that began the evening before.
const val MORNING_STARTS = 6
const val AFTERNOON_STARTS = 12
const val EVENING_STARTS = 18
const val NIGHT_STARTS = 21

private const val READ_NOW = "Read now"
private const val WEEK_DAYS = 6

/** The card at the top of the timeline that leads straight into what has arrived. */
data class TonightCard(
    val heading: String,
    val waiting: Int,
    val entryId: String,
    val authorName: String,
    val type: DocumentType,
    val preview: String,
)

private enum class PartOfDay(val today: String, val yesterday: String, val word: String) {
    MORNING("This Morning", "Yesterday morning", "morning"),
    AFTERNOON("This Afternoon", "Yesterday afternoon", "afternoon"),
    EVENING("This Evening", "Yesterday evening", "evening"),
    NIGHT("Tonight", "Last night", "night"),
}

private fun partOf(hour: Int): PartOfDay = when {
    hour < MORNING_STARTS || hour >= NIGHT_STARTS -> PartOfDay.NIGHT
    hour < AFTERNOON_STARTS -> PartOfDay.MORNING
    hour < EVENING_STARTS -> PartOfDay.AFTERNOON
    else -> PartOfDay.EVENING
}

/**
 * The day a moment belongs to when days run from [MORNING_STARTS] to [MORNING_STARTS] on the wall clock. Read the
 * local hour rather than subtracting six hours, which counts elapsed time and so moves the boundary to 05:00 or
 * 07:00 on daylight-saving days.
 */
private fun dayOf(time: ZonedDateTime): LocalDate =
    if (time.hour < MORNING_STARTS) time.toLocalDate().minusDays(1) else time.toLocalDate()

/** When an entry arrived, said the way a reader would: "Tonight", "Yesterday evening", "Thursday night", "12 October, evening". */
fun arrivalHeading(arrivedAt: ZonedDateTime, now: ZonedDateTime): String {
    val arrived = arrivedAt.withZoneSameInstant(now.zone)
    val part = partOf(arrived.hour)
    val day = dayOf(arrived)
    val daysAgo = ChronoUnit.DAYS.between(day, dayOf(now))
    return when {
        daysAgo <= 0 -> part.today
        daysAgo == 1L -> part.yesterday
        daysAgo <= WEEK_DAYS -> "${day.format(Dates.WEEKDAY)} ${part.word}"
        else -> "${day.format(Dates.DAY)}, ${part.word}"
    }
}

/**
 * The Tonight card, or null when nothing unread is waiting. Only entries the [filter] shows count. One waiting
 * entry is named by when it arrived; several (or Read freely, where nothing "arrives") say "Read now" and open the
 * oldest, so the story is read in order.
 */
fun tonightCard(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId, filter: EntryFilter): TonightCard? {
    val unread = readingOrder(story, user, settings, now, zone, filter).filter { user.progressOf(it.id).readAtMillis == null }
    val first = unread.firstOrNull() ?: return null
    val arrived = Arrivals.arrivesAt(first, settings, zone).takeIf { unread.size == 1 && settings.mode != ReadingMode.Free }
    return TonightCard(
        heading = arrived?.let { arrivalHeading(it.atZone(zone), now.atZone(zone)) } ?: READ_NOW,
        waiting = unread.size,
        entryId = first.id,
        authorName = story.nameOf(first.author) ?: first.author,
        type = first.type,
        preview = first.preview,
    )
}
