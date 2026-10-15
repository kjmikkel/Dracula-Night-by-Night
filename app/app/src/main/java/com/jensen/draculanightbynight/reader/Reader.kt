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
