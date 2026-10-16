package com.jensen.draculanightbynight

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.content.decodeStory
import com.jensen.draculanightbynight.domain.ShiftableClock
import com.jensen.draculanightbynight.domain.TimeSource
import com.jensen.draculanightbynight.notify.Notifications
import com.jensen.draculanightbynight.notify.Notifier
import com.jensen.draculanightbynight.notify.scheduleKey
import com.jensen.draculanightbynight.user.UserRepository
import com.jensen.draculanightbynight.user.UserState
import com.jensen.draculanightbynight.user.UserStateSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock

/** Hand-wired dependencies; one per process. */
class AppContainer(context: Context) {
    /** The device clock; debug builds can move it (time travel). */
    val clock = ShiftableClock(Clock.systemUTC())

    /** The zone is read afresh on every use, so travelling or a zone change takes effect at once. */
    val time = TimeSource(clock)

    /** Outlives every screen: writes started here finish even if the screen that asked is closed. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val stories = StoryRepository {
        withContext(Dispatchers.IO) {
            val assets = context.assets
            decodeStory(
                assets.open("entries.json").bufferedReader().use { it.readText() },
                assets.open("characters.json").bufferedReader().use { it.readText() },
            )
        }
    }

    val users = UserRepository(
        DataStoreFactory.create(
            serializer = UserStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserState() },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(context.filesDir, "user_state.json") },
        ),
        time,
    )

    val notifier = Notifier(context, stories, users, time)

    init {
        Notifications.createChannel(context)
        // Reset the alarm on app start and whenever something that decides the next wake changes: the schedule,
        // the notification switch and baseline, or which entries have been read. Display choices and scroll
        // positions do not count.
        scope.launch {
            users.state
                .map(::scheduleKey)
                .distinctUntilChanged()
                .collect { notifier.reschedule() }
        }
    }

    /**
     * A debug build travelled in time: open screens look again at once and the alarm is set again. (Device clock
     * and zone changes arrive through [com.jensen.draculanightbynight.notify.SystemTimeReceiver].)
     */
    fun onTimeChanged() {
        time.timeChanged()
        scope.launch { notifier.reschedule() }
    }
}

class DraculaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
