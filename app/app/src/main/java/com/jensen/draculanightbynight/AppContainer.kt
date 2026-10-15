package com.jensen.draculanightbynight

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.jensen.draculanightbynight.content.StoryRepository
import com.jensen.draculanightbynight.content.decodeStory
import com.jensen.draculanightbynight.user.UserRepository
import com.jensen.draculanightbynight.user.UserState
import com.jensen.draculanightbynight.user.UserStateSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant

/** Hand-wired dependencies; one per process. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()

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
        clock,
    )

    /** The current instant now and at the start of every following minute, so locked entries open on time. */
    fun minuteTicks(): Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(MINUTE_MILLIS - clock.millis() % MINUTE_MILLIS)
        }
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
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
