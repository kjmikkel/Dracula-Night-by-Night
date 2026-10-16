package com.jensen.draculanightbynight.reader

import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.readingOrder
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.ZoneId

/** Which order Previous, Next and swipe follow: the timeline's filter, or the whole story. */
enum class Following { FILTER, ALL }

/**
 * The entries the reader pages through and where it is. [filterLabel] names the filter for the switch; null
 * hides the switch (no filter, or the reader was not opened from a filtered timeline).
 */
data class ReaderPager(val pages: List<String>, val index: Int, val following: Following, val filterLabel: String?)

/**
 * The pager around [current], or null if [current] cannot be read. Following the filter keeps [current] in its
 * place even when it does not match, so switching never moves the reader off the page they are on.
 * [switchable] is true when the reader was opened from a filtered timeline.
 */
fun buildPager(
    story: Story,
    user: UserState,
    settings: ReadingSettings,
    now: Instant,
    zone: ZoneId,
    current: String,
    following: Following,
    switchable: Boolean,
): ReaderPager? {
    val filter = user.filter.takeIf { switchable && it.isActive }
    val follows = if (filter != null) following else Following.ALL
    val pages = readingOrder(story, user, settings, now, zone, filter.takeIf { follows == Following.FILTER }, keep = current).map { it.id }
    val index = pages.indexOf(current)
    if (index < 0) return null
    return ReaderPager(pages, index, follows, filter?.label)
}
