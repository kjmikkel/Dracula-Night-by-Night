package com.jensen.draculanightbynight.content

import com.jensen.draculanightbynight.TestStory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StoryRepositoryTest {
    @Test
    fun loadsOnceEvenWhenAskedConcurrently() = runTest {
        var loads = 0
        val repo = StoryRepository { loads++; TestStory.small }
        val stories = List(5) { async { repo.story() } }.awaitAll()
        assertEquals(1, loads)
        stories.forEach { assertSame(TestStory.small, it) }
    }
}
