package com.jensen.draculanightbynight.reader

import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.UnlockSchedule
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.ZoneId

/** How an entry's heading is framed; the body always uses the same reading font. */
enum class HeaderStyle { DIARY, LETTER, TELEGRAM, NEWSPAPER, MEMO, LOG }

fun headerStyle(type: DocumentType): HeaderStyle = when (type) {
    DocumentType.DIARY, DocumentType.PHONOGRAPH -> HeaderStyle.DIARY
    DocumentType.LETTER -> HeaderStyle.LETTER
    DocumentType.TELEGRAM -> HeaderStyle.TELEGRAM
    DocumentType.NEWSPAPER -> HeaderStyle.NEWSPAPER
    DocumentType.MEMO -> HeaderStyle.MEMO
    DocumentType.LOG -> HeaderStyle.LOG
}

data class ReaderState(
    val entry: Entry,
    val authorName: String,
    val recipientName: String?,
    val style: HeaderStyle,
    val previousId: String?,
    val nextId: String?,
    val startBlock: Int,
    val startOffset: Int,
    val bookmarked: Boolean,
)

/** The reader for entry [id], or null if it does not exist or is still locked. */
fun buildReader(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId, id: String): ReaderState? {
    val unlocked = story.entries.filter { UnlockSchedule.isUnlocked(it, settings, now, zone) }
    val index = unlocked.indexOfFirst { it.id == id }
    if (index < 0) return null
    val entry = unlocked[index]
    val progress = user.progressOf(id)
    return ReaderState(
        entry = entry,
        authorName = story.nameOf(entry.author) ?: entry.author,
        recipientName = story.nameOf(entry.recipient),
        style = headerStyle(entry.type),
        previousId = unlocked.getOrNull(index - 1)?.id,
        nextId = unlocked.getOrNull(index + 1)?.id,
        startBlock = progress.blockIndex.coerceIn(0, (entry.body.size - 1).coerceAtLeast(0)),
        startOffset = progress.blockOffset,
        bookmarked = progress.bookmarked,
    )
}

// The reader's list shows the header as item 0 and body block i as item i + 1.

/**
 * The (block, offset) to store for a list scrolled to [firstVisibleItem] at [offset] pixels.
 * Anywhere in the header is the top of the entry: (0, 0), which [listPosition] opens at the header.
 */
fun savedPosition(firstVisibleItem: Int, offset: Int): Pair<Int, Int> =
    if (firstVisibleItem == 0) 0 to 0 else (firstVisibleItem - 1) to offset

/** The list item and offset to open at for a stored (block, offset). */
fun listPosition(block: Int, offset: Int): Pair<Int, Int> = (block + if (block > 0 || offset > 0) 1 else 0) to offset
