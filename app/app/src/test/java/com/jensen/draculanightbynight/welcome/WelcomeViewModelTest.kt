package com.jensen.draculanightbynight.welcome

import com.jensen.draculanightbynight.a11y.testApp
import com.jensen.draculanightbynight.domain.ReadingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WelcomeViewModelTest {
    private lateinit var viewModel: WelcomeViewModel
    private var done = 0

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = WelcomeViewModel(testApp())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun tapBegin() = viewModel.begin(ReadingMode.Free, notify = false) { done++ }

    private fun awaitDone(count: Int) = runBlocking { withTimeout(10_000) { while (done < count) delay(10) } }

    @Test fun aDoubleTapBeginsOnce() {
        tapBegin()
        tapBegin()
        awaitDone(1)
        runBlocking { delay(200) }
        assertEquals(1, done)
    }

    @Test fun beginWorksAgainWhenTheScreenDidNotLeave() {
        // The app left the screen just as Begin finished, so MainActivity skipped the navigation (#110).
        tapBegin()
        awaitDone(1)
        tapBegin()
        awaitDone(2)
        assertEquals(2, done)
    }
}
