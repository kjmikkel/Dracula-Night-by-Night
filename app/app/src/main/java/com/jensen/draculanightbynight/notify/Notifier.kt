package com.jensen.draculanightbynight.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.jensen.draculanightbynight.DraculaApp
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.domain.TimeSource
import com.jensen.draculanightbynight.domain.alarmFor
import com.jensen.draculanightbynight.domain.nextWake
import com.jensen.draculanightbynight.user.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * Keeps one inexact alarm (allowed in Doze, no exact-alarm permission) set for the next moment there is
 * something to announce, and posts the notifications when it fires.
 */
class Notifier(
    private val context: Context,
    private val stories: StoryRepository,
    private val users: UserRepository,
    private val time: TimeSource,
) {
    private val lock = Mutex()

    /** Sets the alarm for the next wake, or cancels it. A reader without a notification baseline gets one now. */
    suspend fun reschedule() = lock.withLock { rescheduleLocked() }

    /** The alarm fired: post what arrived since the baseline, move the baseline to now, set the next alarm. */
    suspend fun onAlarm() = lock.withLock {
        val now = time.now()
        Notifications.post(context, planNotifications(stories.story(), users.state.first(), now.toInstant(), now.zone))
        users.markNotified(now.toInstant().toEpochMilli())
        rescheduleLocked()
    }

    private suspend fun rescheduleLocked() {
        if (users.state.first().lastNotifiedAt == null) users.markNotified(time.millis())
        val now = time.now()
        val wake = nextWake(stories.story(), users.state.first(), now.toInstant(), now.zone, Notifications.allowed(context))
        setAlarm(wake?.let { alarmFor(it, now.toInstant()) }, now.toInstant())
    }

    /**
     * Sets the alarm to fire after the time between [now] and [at] on the app's clock. Counting down (elapsed
     * realtime) rather than naming a wall-clock time keeps the alarm right when a debug build travels in time,
     * and when the device clock is changed it is reset anyway (SystemTimeReceiver).
     */
    private fun setAlarm(at: Instant?, now: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, UnlockReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            alarms.cancel(intent)
        } else {
            val lead = Duration.between(now, at).toMillis().coerceAtLeast(0)
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + lead, intent)
        }
    }
}

/** The alarm: posts the notifications in the background (goAsync keeps the process alive meanwhile). */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = (context.applicationContext as DraculaApp).container
        val pending = goAsync()
        app.scope.launch {
            try {
                app.notifier.onAlarm()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Reboot, clock or zone change, app update, notifications blocked or unblocked: the next wake may have moved,
 * and an open timeline should look again at once.
 */
class SystemTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val app = (context.applicationContext as DraculaApp).container
        app.time.timeChanged()
        val pending = goAsync()
        app.scope.launch {
            try {
                app.notifier.reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // NotificationManager's block-state actions (Android 9+); Android 8 never sends them.
            "android.app.action.APP_BLOCK_STATE_CHANGED",
            "android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED",
        )
    }
}
