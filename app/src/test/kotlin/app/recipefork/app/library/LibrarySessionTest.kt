package app.recipefork.app.library

import android.os.Bundle
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/** FLOW-058: the view chosen in the recipe book lasts exactly as long as the session. */
@RunWith(AndroidJUnit4::class)
class LibrarySessionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun launch(saved: Bundle? = null): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java)
            .let { if (saved == null) it.setup() else it.setup(saved) }.also { compose.waitForIdle() }

    private fun chooseOwn() {
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun assertView(view: LibraryView) {
        compose.onNodeWithTag(LibraryTags.tab(view)).assertIsSelected()
    }

    /** The reader leaves and the Activity is torn down with its task: nothing is kept for it. */
    private fun ActivityController<MainActivity>.closeTask() {
        get().finish()
        pause().stop().destroy()
    }

    // FLOW-034 (6) at the application's start.
    @Test
    fun theApplicationOpensOnTheMarkedRecipes() {
        launch()
        assertView(LibraryView.MARKED)
    }

    // FLOW-058 (1)
    @Test
    fun theHomeActionAndReturnKeepTheView() {
        val activity = launch()
        chooseOwn()
        activity.pause().stop()
        activity.restart().start().resume()
        compose.waitForIdle()
        assertView(LibraryView.OWN)
    }

    // FLOW-058 (1): a configuration change on the way is the same session too.
    @Test
    fun aRecreatedActivityKeepsTheView() {
        val activity = launch()
        chooseOwn()
        activity.recreate()
        compose.waitForIdle()
        assertView(LibraryView.OWN)
    }

    // FLOW-058 (2): the process ended while away, and the reader returns — restored from saved state.
    @Test
    fun theProcessEndedWhileAwayKeepsTheView() {
        val activity = launch()
        chooseOwn()
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        // The process dies: the Activity and every object in memory are gone, only the saved state remains.
        activity.destroy()
        launch(saved)
        assertView(LibraryView.OWN)
    }

    // FLOW-058 (3): removed from the recent applications, the task starts anew with nothing to restore.
    @Test
    fun removalFromRecentsStartsANewSessionOnTheMarkedRecipes() {
        val activity = launch()
        chooseOwn()
        activity.closeTask()
        launch()
        assertView(LibraryView.MARKED)
    }

    // FLOW-058 (4): after a restart of the device the application starts with nothing to restore.
    @Test
    fun aDeviceRestartStartsANewSession() {
        val activity = launch()
        chooseOwn()
        val saved = Bundle()
        activity.pause().stop().saveInstanceState(saved)
        activity.destroy()
        // A restart keeps no task, so the platform hands the new Activity no saved state.
        launch(saved = null)
        assertView(LibraryView.MARKED)
    }
}
