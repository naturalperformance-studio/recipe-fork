package app.recipefork.app.file

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.ServerlessTestApplication
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.core.file.RecipeFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * FLOW-064 through the application itself, in serverless mode: a recipe file opened from another application passes
 * through [OpenRecipeFileActivity] to [MainActivity], which imports it and says so; a second one opened while the
 * application runs, or one shared to it, is followed the same way.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = ServerlessTestApplication::class)
class OpenedFileInTheAppTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
    private val serverless = application.container
    private val incoming = TestRecipeFiles.incoming()
    private var opened: ActivityController<MainActivity>? = null

    @After
    fun close() {
        opened?.pause()?.stop()?.destroy()
        incoming.deleteRecursively()
    }

    private fun fileOf(id: String, name: String): Uri =
        Uri.fromFile(TestRecipeFiles.write(incoming, name, TestRecipeFiles.file(TestRecipeFiles.soup(id))))

    /** What the opener hands on to MainActivity for [intent], as the platform would deliver it. */
    private fun handedOn(intent: Intent): Intent {
        val opener = Robolectric.buildActivity(OpenRecipeFileActivity::class.java, intent).create().get()
        return shadowOf(opener).nextStartedActivity!!
    }

    private fun marked(): Int = runBlocking { serverless.recipes.marked().first().size }

    private fun awaitLooping(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + PATIENCE_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "the condition was never met" }
            compose.waitForIdle()
            Thread.sleep(10)
        }
    }

    private fun awaitText(text: String) =
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun anOpenedFileIsImportedAndASecondOneWhileTheApplicationRunsToo() {
        val first = fileOf("0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10", "lemon-cake.recipefork")
        opened = Robolectric.buildActivity(MainActivity::class.java, handedOn(
            Intent(Intent.ACTION_VIEW).setDataAndType(first, "text/plain"),
        )).setup()
        awaitLooping { marked() == 1 }
        awaitText("“Bean soup” is in your recipe book now")

        // The application open, a second file opened from the messenger reaches the same screen (onNewIntent).
        val second = fileOf("6f1d8a52-1b0e-4c07-8a3f-2d9e4c5b7a61", "lemon.cake.v2.recipefork")
        opened!!.newIntent(handedOn(Intent(Intent.ACTION_VIEW).setDataAndType(second, "application/octet-stream")))
        awaitLooping { marked() == 2 }

        // The first opened again: the book holds it, and says so.
        opened!!.newIntent(handedOn(Intent(Intent.ACTION_VIEW).setDataAndType(first, null)))
        awaitText("“Bean soup” is already in your recipe book, so nothing was added")
        assertEquals(2, marked())
    }

    @Test
    fun aSharedFileIsImportedAsAnOpenedOne() {
        val shared = fileOf("0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10", "lemon-cake.recipefork")
        opened = Robolectric.buildActivity(MainActivity::class.java, handedOn(
            Intent(Intent.ACTION_SEND).setType(RecipeFile.MIME_TYPE).putExtra(Intent.EXTRA_STREAM, shared),
        )).setup()
        awaitLooping { marked() == 1 }
        awaitText("“Bean soup” is in your recipe book now")
    }

    @Test
    fun aRefusedFileIsSaidToBe() {
        val garbage = Uri.fromFile(TestRecipeFiles.write(incoming, "other.recipefork", "garbage".toByteArray()))
        opened = Robolectric.buildActivity(MainActivity::class.java, handedOn(
            Intent(Intent.ACTION_VIEW).setDataAndType(garbage, "text/plain"),
        )).setup()
        awaitText("This file is not a recipe file, so nothing was added")
        assertEquals(0, marked())
    }

    // Reopened from the recent applications, the task's first intent is given again: its file was imported then, and
    // is not imported again — only the file opened afterwards is.
    @Test
    fun theIntentGivenAgainFromRecentsIsNotFollowed() {
        val file = fileOf("0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10", "lemon-cake.recipefork")
        val again = handedOn(Intent(Intent.ACTION_VIEW).setDataAndType(file, "text/plain"))
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        opened = Robolectric.buildActivity(MainActivity::class.java, again).setup()
        // Had the reopening been followed, the file opened next would be said to be in the book already.
        opened!!.newIntent(handedOn(Intent(Intent.ACTION_VIEW).setDataAndType(file, "text/plain")))
        awaitText("“Bean soup” is in your recipe book now")
        assertEquals(1, marked())
    }
}
