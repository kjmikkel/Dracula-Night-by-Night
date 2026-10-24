package com.jensen.draculanightbynight.user

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.free
import com.jensen.draculanightbynight.a11y.testApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Duration

/** The copy kept up to date, through Android's content resolver (#205). */
@RunWith(RobolectricTestRunner::class)
class BackupsTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var app: AppContainer
    private lateinit var backups: Backups

    @Before fun setUp() {
        app = testApp()
        runBlocking { app.free() }
        backups = Backups(ApplicationProvider.getApplicationContext(), app.users, app.time, app.scope, copyDelay = Duration.ofMillis(10))
    }

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
    }

    private fun eventually(check: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!check()) {
            check(System.currentTimeMillis() < until) { "timed out" }
            Thread.sleep(20)
        }
    }

    private fun saved(file: File): UserState? = file.takeIf { it.exists() }?.let { decodeBackup(it.readText()) }

    @Test
    fun choosingAFileWritesTheReadingAtOnce() = runBlocking {
        val file = tmp.newFile("copy.json")
        val before = app.time.millis()
        backups.keepCopy(Uri.fromFile(file))
        assertEquals(app.users.state.first(), saved(file))
        val target = backups.target.value!!
        assertEquals(Uri.fromFile(file).toString(), target.uri)
        assertFalse(target.failed)
        // The app's clock keeps running in tests: saved now, not at some fixed instant.
        assertTrue(target.savedAtMillis!! in before..app.time.millis())
    }

    @Test
    fun theCopyFollowsTheReading() = runBlocking {
        val file = tmp.newFile("copy.json")
        backups.keepCopy(Uri.fromFile(file))
        app.users.markRead(ENTRY)
        eventually { saved(file)?.progressOf(ENTRY)?.readAtMillis != null }
    }

    @Test
    fun aFileThatCanNoLongerBeWrittenIsReported() = runBlocking {
        backups.keepCopy(Uri.fromFile(File(tmp.root, "gone/copy.json")))
        assertTrue(backups.target.value!!.failed)
        assertNull(backups.target.value!!.savedAtMillis)
    }

    @Test
    fun stoppingLeavesTheFileAndWritesNoMore() = runBlocking {
        val file = tmp.newFile("copy.json")
        backups.keepCopy(Uri.fromFile(file))
        backups.stopCopy()
        assertNull(backups.target.value)
        val before = file.readText()
        app.users.markRead(ENTRY)
        Thread.sleep(300)
        assertEquals(before, file.readText())
    }

    @Test
    fun theChoiceIsRememberedAcrossRestarts() = runBlocking {
        // A scope that never runs: the kept-copy collector would otherwise store a newer time between the two reads.
        val stopped = CoroutineScope(Job().apply { cancel() })
        val before = Backups(ApplicationProvider.getApplicationContext(), app.users, app.time, stopped)
        before.keepCopy(Uri.fromFile(tmp.newFile("copy.json")))
        val again = Backups(ApplicationProvider.getApplicationContext(), app.users, app.time, stopped)
        assertEquals(before.target.value, again.target.value)
    }

    @Test
    fun aSavedFileLoadsBack() = runBlocking {
        val file = tmp.newFile("saved.json")
        assertTrue(backups.save(Uri.fromFile(file)))
        assertEquals(app.users.state.first(), decodeBackup(backups.read(Uri.fromFile(file))!!))
    }

    private companion object {
        const val ENTRY = "1893-05-03-jonathan_harker-01"
    }
}
