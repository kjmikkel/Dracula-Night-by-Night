package com.jensen.draculanightbynight

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.content.decodeStory
import com.jensen.draculanightbynight.domain.TimeSource
import com.jensen.draculanightbynight.user.UserRepository
import com.jensen.draculanightbynight.user.UserState
import com.jensen.draculanightbynight.user.UserStateSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock

/** Hand-wired dependencies; one per process. */
class AppContainer(context: Context) {
    /** The zone is read afresh on every use, so travelling or a zone change takes effect at once. */
    val time = TimeSource(Clock.systemUTC())

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

    init {
        // Re-evaluate unlocks as soon as the device's zone or clock changes, not at the next minute.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = time.timeChanged()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
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
