package com.jensen.draculanightbynight.notify

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jensen.draculanightbynight.MainActivity
import com.jensen.draculanightbynight.R

/** The "New entries" channel and the two notifications the app posts. Silent by default (importance Low). */
object Notifications {
    const val CHANNEL_NEW_ENTRIES = "new_entries"
    const val EXTRA_ENTRY_ID = "com.jensen.draculanightbynight.ENTRY_ID"
    private const val ARRIVALS_ID = 1
    private const val SEASON_ID = 2

    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_NEW_ENTRIES, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("New entries")
            .setDescription("When diary entries, letters and telegrams arrive")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Android lets the app post: permission granted (Android 13+), and neither the app nor the channel blocked. */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        val channel = manager.getNotificationChannelCompat(CHANNEL_NEW_ENTRIES)
        return manager.areNotificationsEnabled() && channel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    fun post(context: Context, plan: NotificationPlan) {
        if (!allowed(context)) return
        val manager = NotificationManagerCompat.from(context)
        try {
            plan.arrivals?.let { manager.notify(ARRIVALS_ID, build(context, it)) }
            plan.seasonPrompt?.let { manager.notify(SEASON_ID, build(context, it)) }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: nothing to show.
        }
    }

    /** The reader is looking at the app: the "new entries" notification has done its job. */
    fun clearArrivals(context: Context) {
        NotificationManagerCompat.from(context).cancel(ARRIVALS_ID)
    }

    private fun build(context: Context, content: NotificationContent): Notification {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { content.openEntryId?.let { putExtra(EXTRA_ENTRY_ID, it) } }
        val tap = PendingIntent.getActivity(
            context, content.openEntryId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_NEW_ENTRIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(tap)
            .setAutoCancel(true)
        if (content.lines.isNotEmpty()) {
            builder.setStyle(NotificationCompat.InboxStyle().also { style -> content.lines.forEach(style::addLine) })
        }
        return builder.build()
    }
}
