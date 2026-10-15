package com.jensen.draculanightbynight.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jensen.draculanightbynight.DraculaApp
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Debug builds only. Moves the app's clock so timed behaviour can be checked in seconds:
 *
 *     adb shell am broadcast -a com.jensen.draculanightbynight.TIME_TRAVEL --es at 2026-10-15T21:58   # local time
 *     adb shell am broadcast -a com.jensen.draculanightbynight.TIME_TRAVEL --el plusMinutes 90
 *     adb shell am broadcast -a com.jensen.draculanightbynight.TIME_TRAVEL --ez reset true
 *
 * The offset lives in memory: force-stopping the app returns it to real time. Logs the new time under "TimeTravel".
 */
class TimeTravelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = (context.applicationContext as DraculaApp).container
        when {
            intent.getBooleanExtra("reset", false) -> app.clock.reset()
            intent.hasExtra("at") -> app.clock.travelTo(LocalDateTime.parse(intent.getStringExtra("at")).atZone(ZoneId.systemDefault()).toInstant())
            intent.hasExtra("plusMinutes") -> app.clock.travelBy(Duration.ofMinutes(intent.getLongExtra("plusMinutes", 0)))
        }
        app.onTimeChanged()
        Log.i("TimeTravel", "now ${app.time.now()} (offset ${app.clock.offset})")
    }
}
