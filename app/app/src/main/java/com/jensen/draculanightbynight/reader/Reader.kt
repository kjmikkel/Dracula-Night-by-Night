package com.jensen.draculanightbynight.reader

import com.jensen.draculanightbynight.content.Block
import com.jensen.draculanightbynight.content.BlockRole
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.domain.ReadingSettings
import com.jensen.draculanightbynight.domain.isReadable
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
    val startBlock: Int,
    val startOffset: Int,
    /** The share of the block to open at, in thousandths of its height; null for a place saved only in pixels. */
    val startPermille: Int? = null,
    val bookmarked: Boolean,
    /** The reader's choices for what repeats the header (#177); [skipRepeatedDates] null follows TalkBack. */
    val hidePartiesLine: Boolean = false,
    val skipRepeatedDates: Boolean? = null,
)

/** The reader for entry [id], or null if it does not exist or is not readable (locked and never read). */
fun buildReader(story: Story, user: UserState, settings: ReadingSettings, now: Instant, zone: ZoneId, id: String): ReaderState? =
    story.entry(id)?.takeIf { isReadable(it, settings, user, now, zone) }?.let { readerFor(story, user, it) }

/** The reader for an entry already known to be readable (a page of the pager). */
fun readerFor(story: Story, user: UserState, entry: Entry): ReaderState {
    val progress = user.progressOf(entry.id)
    return ReaderState(
        entry = entry,
        authorName = story.nameOf(entry.author) ?: entry.author,
        recipientName = story.nameOf(entry.recipient),
        style = headerStyle(entry.type),
        startBlock = progress.blockIndex.coerceIn(0, (entry.body.size - 1).coerceAtLeast(0)),
        startOffset = progress.blockOffset,
        startPermille = progress.blockPermille,
        bookmarked = progress.bookmarked,
        hidePartiesLine = user.hidePartiesLine,
        skipRepeatedDates = user.skipRepeatedDates,
    )
}

/** A newspaper header as its masthead (first non-blank line) and the headline under it, if any. */
fun mastheadOf(entry: Entry): Pair<String, String?> {
    val lines = entry.header?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val masthead = lines.firstOrNull() ?: return "Newspaper" to null
    return masthead to lines.drop(1).joinToString(" ").ifEmpty { null }
}

// The reader's list shows the header as item 0 and body block i as item i + 1.

/** A place in an entry: [block], [offset] pixels into it, and the same as [permille] thousandths of its height (#200). */
data class SavedPlace(val block: Int, val offset: Int, val permille: Int)

/**
 * The place to store for a list scrolled to [firstVisibleItem] at [offset] pixels, that item being [itemHeight] tall.
 * Anywhere in the header is the top of the entry: (0, 0), which [listPosition] opens at the header.
 */
fun savedPosition(firstVisibleItem: Int, offset: Int, itemHeight: Int): SavedPlace = when {
    firstVisibleItem == 0 -> SavedPlace(0, 0, 0)
    itemHeight <= 0 -> SavedPlace(firstVisibleItem - 1, offset, 0)
    else -> SavedPlace(firstVisibleItem - 1, offset, (offset.toLong() * 1000 / itemHeight).toInt().coerceIn(0, 999))
}

/** The pixels into an item [itemHeight] tall that a saved [permille] stands for. */
fun offsetInto(itemHeight: Int, permille: Int): Int = (itemHeight.toLong() * permille / 1000).toInt()

/** The list item and offset to open at for a stored (block, offset). */
fun listPosition(block: Int, offset: Int): Pair<Int, Int> = (block + if (block > 0 || offset > 0) 1 else 0) to offset

/**
 * Whether block [index] of [body] starts with an indent (#179): a paragraph straight after another, as in print. The
 * first paragraph, and one after a dateline, salutation, verse, note or break, starts flush.
 */
fun indentsFirstLine(body: List<Block>, index: Int): Boolean =
    body.getOrNull(index)?.role == BlockRole.PARA && body.getOrNull(index - 1)?.role == BlockRole.PARA
