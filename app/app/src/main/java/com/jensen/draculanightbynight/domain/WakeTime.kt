package com.jensen.draculanightbynight.domain

import com.jensen.draculanightbynight.content.Story
import com.jensen.draculanightbynight.user.UserState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * When the app must wake next to notify: the next arrival or the new-season prompt, whichever comes first.
 * Null when the reader turned notifications off, Android blocks them, or nothing is left to announce.
 */
fun nextWake(story: Story, user: UserState, now: Instant, zone: ZoneId, notificationsAllowed: Boolean): Instant? {
    if (!user.notify || !notificationsAllowed) return null
    val settings = user.settings ?: return null
    val arrival = Arrivals.nextAfter(story, user, settings, now, zone)
    val prompt = SeasonPrompt.notifyAt(user, zone)?.takeIf { at -> user.lastNotifiedAt?.let { at.toEpochMilli() > it } ?: true }
    return listOfNotNull(arrival, prompt).minOrNull()
}

/** Within this lead time the alarm is aimed straight at the wake; Android's delay is then at most ~1.5 minutes. */
private val AIM_DIRECTLY: Duration = Duration.ofMinutes(2)

/**
 * Where to set the alarm for [wake]. Android may deliver an inexact alarm up to 75 % of its lead time late (at
 * most an hour), so a far wake is approached halfway: the alarm fires before the wake, the app aims again, and
 * only the last couple of minutes are aimed at directly. About ten short wakes a day.
 */
fun alarmFor(wake: Instant, now: Instant): Instant {
    val lead = Duration.between(now, wake)
    return if (lead <= AIM_DIRECTLY) wake else now.plus(lead.dividedBy(2))
}

/** True when the new-season prompt is due at [now] and has not been announced since it fell due. */
fun seasonPromptToNotify(user: UserState, now: Instant, zone: ZoneId): Boolean {
    val at = SeasonPrompt.notifyAt(user, zone) ?: return false
    return !at.isAfter(now) && (user.lastNotifiedAt?.let { at.toEpochMilli() > it } ?: true)
}
