package app.recipefork.app.paste

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.BookFileIntents
import app.recipefork.app.file.CollectionFileIntents
import app.recipefork.app.file.FileMessageTags
import app.recipefork.app.file.OpenRecipeFileActivity
import app.recipefork.app.file.RecipeFileIntents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * T-280, scenario 4: a recipe's text selected in a browser or Telegram and shared to the application opens the paste
 * screen holding that text — its subject, when one is given, as a first line — as pasting does.
 */
@RunWith(AndroidJUnit4::class)
class ShareTextTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var opened: ActivityController<MainActivity>? = null

    private val shared = "Блины\n\nИнгредиенты\n- 200 г муки\n\nПриготовление\n1. Смешать."

    @After
    fun close() {
        opened?.pause()?.stop()?.destroy()
    }

    private fun send(text: String?, subject: String? = null, type: String = "text/plain") = Intent(Intent.ACTION_SEND).apply {
        setType(type)
        text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }

    private fun fieldText(): String =
        compose.onNodeWithTag(PasteTags.FIELD).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun awaitTheField(text: String) = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(hasTestTag(PasteTags.FIELD)).fetchSemanticsNodes().size == 1 && fieldText() == text
    }

    private fun offered(intent: Intent): List<String> =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName == context.packageName }
            .map { it.activityInfo.name }

    /** What the activity that takes shares hands [intent] on as, in the application's own task. */
    private fun handedOn(intent: Intent): Intent? {
        val activity = Robolectric.buildActivity(OpenRecipeFileActivity::class.java, intent).create().get()
        return shadowOf(activity).nextStartedActivity
    }

    // The manifest offers the application once for a shared text — through the activity that takes shared files — and
    // still once for a file shared with its address as text.
    @Test
    fun aSharedTextIsOfferedToTheApplicationOnce() {
        val opener = OpenRecipeFileActivity::class.java.name
        assertEquals(listOf(opener), offered(send(shared)))
        assertEquals(listOf(opener), offered(send(shared, subject = "Блины")))
        val book = Uri.parse("content://org.telegram.messenger.provider/media/Recipe book.recipefork-book")
        assertEquals(listOf(opener), offered(Intent(Intent.ACTION_SEND).setDataAndType(book, "text/plain").putExtra(Intent.EXTRA_STREAM, book)))
    }

    // It is handed on to MainActivity, in the application's own task, holding the text — its subject first.
    @Test
    fun aSharedTextIsHandedOnToTheApplicationsOwnTask() {
        val forwarded = requireNotNull(handedOn(send("- 200 г муки", subject = "Блины")))
        assertEquals(ComponentName(context, MainActivity::class.java), forwarded.component)
        assertTrue(forwarded.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("Блины\n- 200 г муки", PasteIntents.textOf(forwarded))
        // A share holding no text, nor any file, hands nothing on.
        assertNull(handedOn(send(null)))
    }

    // A shared text opens the paste screen holding it, from a closed application and from an open one.
    @Test
    fun aSharedTextOpensThePasteScreenWithIt() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, handedOn(send(shared))).setup()
        opened = controller
        awaitTheField(shared)

        val again = "Pancakes\n\nIngredients\n- 2 cups flour\n\nMethod\n1. Mix."
        controller.newIntent(handedOn(send(again)))
        awaitTheField(again)
    }

    // EXTRA_SUBJECT is put before the text as its first line, unless the text begins with it already.
    @Test
    fun theSubjectIsTheFirstLineUnlessTheTextBeginsWithIt() {
        assertEquals("Блины\n- 200 г муки", PasteIntents.textOf(send("- 200 г муки", subject = "Блины")))
        assertEquals(shared, PasteIntents.textOf(send(shared, subject = "Блины")))
        // Begun with, though its first line says more.
        assertEquals("Блины на молоке\n- 200 г муки", PasteIntents.textOf(send("Блины на молоке\n- 200 г муки", subject = "Блины")))
        assertEquals("  Блины — рецепт\n- 200 г муки", PasteIntents.textOf(send("  Блины — рецепт\n- 200 г муки", subject = "Блины — рецепт")))
        assertEquals(shared, PasteIntents.textOf(send(shared, subject = "  ")))
        assertEquals(shared, PasteIntents.textOf(send(shared, type = "text/*")))
    }

    private fun sendStream(uri: Uri, type: String) = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri)

    private val telegram = "content://org.telegram.messenger.provider/media/Android/data/org.telegram.messenger/files/Telegram/Telegram Files/"

    // A recipe file shared as text — as Telegram shares a file, its address in EXTRA_STREAM alone — is handed on as the
    // file it is, to be imported, as when it is opened; so are a book and a collection.
    @Test
    fun aRecipeFileSharedAsTextIsHandedOnAsAFile() {
        for (name in listOf("lemon-cake.recipefork", "LEMON CAKE.RECIPEFORK")) {
            val file = Uri.parse(telegram + name)
            val shared = sendStream(file, "text/plain")
            assertEquals(listOf(OpenRecipeFileActivity::class.java.name), offered(shared))
            val forwarded = requireNotNull(handedOn(shared))
            assertEquals(Intent.ACTION_VIEW, forwarded.action)
            assertEquals(ComponentName(context, MainActivity::class.java), forwarded.component)
            assertEquals(file, RecipeFileIntents.fileOf(forwarded))
        }
        val book = Uri.parse(telegram + "Recipe book.recipefork-book")
        assertEquals(book, BookFileIntents.toRestore(handedOn(sendStream(book, "text/plain"))))
        val collection = Uri.parse(telegram + "menu.recipefork-collection")
        assertEquals(collection, CollectionFileIntents.toImport(handedOn(sendStream(collection, "text/plain"))))
    }

    // A file of another kind shared as text says it is no recipe file: nothing is silent.
    @Test
    fun anotherFileSharedAsTextSaysItIsNoRecipeFile() {
        val forwarded = requireNotNull(handedOn(sendStream(Uri.parse(telegram + "notes.txt"), "text/plain")))
        assertEquals(RecipeFileIntents.ACTION_NOT_OURS, forwarded.action)
        assertEquals(ComponentName(context, MainActivity::class.java), forwarded.component)
        assertNull(RecipeFileIntents.fileOf(forwarded))
        assertNull(PasteIntents.textOf(forwarded))

        opened = Robolectric.buildActivity(MainActivity::class.java, forwarded).setup()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(FileMessageTags.TEXT)).fetchSemanticsNodes().size == 1 }
        assertEquals(listOf("This file is not a recipe file, so nothing was added"), compose.textsOf(FileMessageTags.TEXT))
    }

    // What shares no text opens nothing.
    @Test
    fun anIntentWithNoTextIsNoneToPaste() {
        assertNull(PasteIntents.textOf(send(null)))
        assertNull(PasteIntents.textOf(send("   ")))
        assertNull(PasteIntents.textOf(send(shared, type = "image/png")))
        assertNull(PasteIntents.textOf(Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, shared)))
        assertNull(PasteIntents.textOf(null))
    }
}
