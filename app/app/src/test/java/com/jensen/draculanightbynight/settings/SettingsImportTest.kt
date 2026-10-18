package com.jensen.draculanightbynight.settings

import android.net.Uri
import com.jensen.draculanightbynight.AppContainer
import com.jensen.draculanightbynight.a11y.free
import com.jensen.draculanightbynight.a11y.testApp
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.user.EntryProgress
import com.jensen.draculanightbynight.user.ThemeChoice
import com.jensen.draculanightbynight.user.UserState
import com.jensen.draculanightbynight.user.encodeBackup
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate

/** Load from a file: replaces everything, asking first when this phone has progress (#205). */
@RunWith(RobolectricTestRunner::class)
class SettingsImportTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var app: AppContainer
    private lateinit var viewModel: SettingsViewModel

    private val backup = UserState(onboarded = true, theme = ThemeChoice.DARK, progress = mapOf("there" to EntryProgress(readAtMillis = 1)))
        .withMode(ReadingMode.SameTempo(LocalDate.of(2026, 9, 1)))

    @Before fun setUp() {
        app = testApp()
        runBlocking { app.free() }
        viewModel = SettingsViewModel(app)
    }

    @After fun tearDown() {
        runBlocking { app.scope.coroutineContext.job.cancelAndJoin() }
    }

    private fun file(text: String): Uri = Uri.fromFile(tmp.newFile().apply { writeText(text) })

    /** The view model's work runs on the main looper and hops to IO for the file: let both finish. */
    private fun settle() {
        repeat(20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
    }

    private fun state() = runBlocking { app.users.state.first() }

    /** The file's state became the phone's, with the notification baseline moved to when it loaded (after [since]). */
    private fun assertLoaded(since: Long) {
        val now = state()
        assertEquals(backup, now.copy(lastNotifiedAt = null))
        assertTrue(now.lastNotifiedAt!! in since..app.time.millis())
    }

    @Test
    fun withNothingReadItLoadsAtOnce() {
        val since = app.time.millis()
        viewModel.load(file(encodeBackup(backup)))
        settle()
        assertLoaded(since)
        assertNull(viewModel.pendingImport.value)
    }

    @Test
    fun overProgressItAsksAndCancelLeavesEverything() {
        runBlocking { app.users.markRead("here") }
        val before = state()
        viewModel.load(file(encodeBackup(backup)))
        settle()
        assertEquals(backup, viewModel.pendingImport.value)
        assertEquals(before, state())
        viewModel.cancelImport()
        assertNull(viewModel.pendingImport.value)
        assertEquals(before, state())
    }

    @Test
    fun overProgressReplaceTakesTheFile() {
        runBlocking { app.users.markRead("here") }
        viewModel.load(file(encodeBackup(backup)))
        settle()
        val since = app.time.millis()
        viewModel.confirmImport()
        settle()
        assertLoaded(since)
        assertNull(viewModel.pendingImport.value)
    }

    @Test
    fun aFileThatIsNotABackupChangesNothing() {
        val before = state()
        viewModel.load(file("""{"hello": "world"}"""))
        settle()
        assertEquals(before, state())
        assertNull(viewModel.pendingImport.value)
    }
}
