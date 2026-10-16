package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.ZoneId

/**
 * The entries a reader can step through, in story order: everything readable, narrowed by [filter] when it is
 * active. [keep] stays in its story position even if it does not match, so switching the reader back to the
 * filter never moves them off the page they are on. The timeline and the reader both use this order.
 */
fun readingOrder(
    story: Story,
    user: UserState,
    settings: ReadingSettings,
    now: Instant,
    zone: ZoneId,
    filter: EntryFilter? = null,
    keep: String? = null,
): List<Entry> = story.entries.filter { entry ->
    isReadable(entry, settings, user, now, zone) && (filter == null || !filter.isActive || entry.id == keep || filter.matches(entry))
}
