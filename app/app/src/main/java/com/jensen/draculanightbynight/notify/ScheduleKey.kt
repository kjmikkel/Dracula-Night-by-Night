package com.jensen.draculanightbynight.notify

import com.jensen.draculanightbynight.user.UserState

/**
 * What decides the next wake: the schedule, the notification switch and which entries have been read. Display
 * choices, the filter, bookmarks, scroll positions and the notification baseline do not; the notifier reschedules
 * itself after every run that moves the baseline.
 */
data class ScheduleKey(
    val mode: String?,
    val seasonYear: Int?,
    val tempoStart: String?,
    val skipAheadMinutes: Long,
    val notify: Boolean,
    val onboarded: Boolean,
    val read: Set<String>,
)

fun scheduleKey(state: UserState): ScheduleKey = ScheduleKey(
    mode = state.mode,
    seasonYear = state.seasonYear,
    tempoStart = state.tempoStart,
    skipAheadMinutes = state.skipAheadMinutes,
    notify = state.notify,
    onboarded = state.onboarded,
    read = state.progress.filterValues { it.readAtMillis != null }.keys,
)
