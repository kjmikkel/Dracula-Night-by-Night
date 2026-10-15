package com.jensen.draculanightbynight.content

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loads the bundled story once, on first use, and keeps it in memory (234 entries, about 1 MB). */
class StoryRepository(private val load: suspend () -> Story) {
    private val mutex = Mutex()
    private var cached: Story? = null

    suspend fun story(): Story = cached ?: mutex.withLock { cached ?: load().also { cached = it } }
}
