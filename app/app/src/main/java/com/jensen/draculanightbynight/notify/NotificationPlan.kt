package com.jensen.draculanightbynight.notify

import com.jensen.draculanightbynight.content.storyDayTime
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.content.Entry
import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.content.typeLabel
import com.jensen.draculanightbynight.domain.Arrivals
import com.jensen.draculanightbynight.domain.announceFrom
import com.jensen.draculanightbynight.domain.seasonPromptToNotify
import com.jensen.draculanightbynight.user.UserState
import java.time.Instant
import java.time.ZoneId

/** What one notification says. [openEntryId] is the entry a tap opens; null opens the timeline. */
data class NotificationContent(val title: String, val text: String, val lines: List<String> = emptyList(), val openEntryId: String? = null)

/** What an alarm run posts: the arrivals since the last run, and the new-season prompt if it fell due. */
data class NotificationPlan(val arrivals: NotificationContent?, val seasonPrompt: NotificationContent?)

/**
 * Decides what to post at [now]: entries that arrived since the reader's notification baseline
 * (`lastNotifiedAt`, at most a day back; without one nothing old is announced) and the season prompt. Pure, so the receiver
 * that runs it on the device only has to post the result.
 */
fun planNotifications(story: Story, user: UserState, now: Instant, zone: ZoneId): NotificationPlan {
    val settings = user.settings
    if (!user.notify || settings == null) return NotificationPlan(null, null)
    val from = announceFrom(user, now) ?: now
    val arrived = Arrivals.between(story, user, settings, from, now, zone)
    val prompt = if (seasonPromptToNotify(user, now, zone)) {
        NotificationContent("Dracula begins again on 3 May", "Start over and read it again this year?")
    } else {
        null
    }
    return NotificationPlan(arrivalsContent(story, arrived), prompt)
}

private fun arrivalsContent(story: Story, arrived: List<Entry>): NotificationContent? = when (arrived.size) {
    0 -> null
    1 -> arrived.single().let { e ->
        NotificationContent(arrivalTitle(e.type, story.nameOf(e.author) ?: e.author), e.storyDayTime ?: e.preview, openEntryId = e.id)
    }
    else -> NotificationContent(
        title = "${arrived.size} new entries",
        text = arrived.joinToString(", ") { story.nameOf(it.author) ?: it.author },
        lines = arrived.map { "${story.nameOf(it.author) ?: it.author} · ${typeLabel(it.type)}" },
    )
}

private fun arrivalTitle(type: DocumentType, name: String): String {
    val owner = if (name.endsWith("s")) "$name'" else "$name's"
    return when (type) {
        DocumentType.DIARY -> "$owner journal has arrived"
        DocumentType.PHONOGRAPH -> "$owner phonograph diary has arrived"
        DocumentType.LOG -> "$owner log has arrived"
        DocumentType.LETTER -> "A letter from $name has arrived"
        DocumentType.TELEGRAM -> "A telegram from $name has arrived"
        DocumentType.NEWSPAPER -> "A cutting from $name has arrived"
        DocumentType.MEMO -> "A memorandum from $name has arrived"
    }
}
