package app.recipefork.app.file

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.RecipeFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * A collection file (v0.36, T-256) as the platform sees it: offered to the application by its extension whatever type
 * the sender declares — tapped in Telegram or a file manager, or opened by its path — or by the product's collection
 * type, and handed on to MainActivity to be imported as a collection; a recipe file still to be imported as a recipe,
 * and a saved book to be restored. No extension takes another's file.
 */
@RunWith(AndroidJUnit4::class)
class CollectionFileRoutingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opener = OpenRecipeFileActivity::class.java.name

    private fun offered(intent: Intent): List<String> =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName == context.packageName }
            .map { it.activityInfo.name }

    private fun view(uri: Uri, type: String?) = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)

    private fun send(uri: Uri, type: String?) = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri)

    private fun assertOffered(intent: Intent) = assertEquals("$intent", listOf(opener), offered(intent))

    private fun assertNotOffered(intent: Intent) = assertEquals("$intent", emptyList<String>(), offered(intent))

    private val declared = listOf(
        CollectionFile.MIME_TYPE, "application/zip", "application/octet-stream", "text/plain", "*/*", null,
    )

    private val telegram =
        "content://org.telegram.messenger.provider/media/Android/data/org.telegram.messenger/files/Telegram/Telegram Files/"
    private val telegramEncoded =
        "content://org.telegram.messenger.provider/media/Android/data/org.telegram.messenger/files/Telegram/Telegram%20Files/"
    private val fileManager = "content://com.sec.android.app.myfiles.FileProvider/device_storage/0/Download/"
    private val fileAddress = "file:///storage/emulated/0/Download/"

    private val names = listOf("New Year's menu.recipefork-collection", "menu.v2.final.recipefork-collection", "MENU.RECIPEFORK-COLLECTION")

    // Scenario 5: a collection file tapped in Telegram or a file manager is offered whatever type it declares, and
    // handed on to be imported as a collection — never as a recipe, nor as a book.
    @Test
    fun aCollectionFileIsOfferedAndHandedOnToItsImport() {
        for (place in listOf(telegram, telegramEncoded, fileManager, fileAddress)) {
            for (name in names) {
                for (type in declared) {
                    val intent = view(Uri.parse(place + name), type)
                    assertOffered(intent)
                    val forwarded = RecipeFileIntents.forward(context, intent)!!
                    assertEquals(CollectionFileIntents.ACTION_IMPORT, forwarded.action)
                    assertEquals(ComponentName(context, MainActivity::class.java), forwarded.component)
                    assertEquals(intent.data, CollectionFileIntents.toImport(forwarded))
                    assertNull(RecipeFileIntents.fileOf(forwarded))
                    assertNull(BookFileIntents.toRestore(forwarded))
                    assertTrue(forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                    assertEquals(intent.data, forwarded.clipData!!.getItemAt(0).uri)
                }
            }
        }
    }

    // A file declared with the collection's own type is offered and imported as one whatever its address, opened or shared.
    @Test
    fun theCollectionsTypeIsOfferedWhateverTheAddress() {
        val nameless = Uri.parse("content://com.android.providers.downloads.documents/document/1234")
        for (intent in listOf(view(nameless, CollectionFile.MIME_TYPE), send(nameless, CollectionFile.MIME_TYPE))) {
            assertOffered(intent)
            assertEquals(nameless, CollectionFileIntents.toImport(RecipeFileIntents.forward(context, intent)))
        }
        // Shared with its address in the intent's data, by its name.
        val named = Uri.parse(telegram + "menu.recipefork-collection")
        val shared = Intent(Intent.ACTION_SEND).setDataAndType(named, "application/octet-stream").putExtra(Intent.EXTRA_STREAM, named)
        assertOffered(shared)
        assertEquals(named, CollectionFileIntents.toImport(RecipeFileIntents.forward(context, shared)))
    }

    // Which handler each file reaches, through the one activity that takes them all.
    @Test
    fun eachFileReachesItsOwnHandler() {
        fun forwarded(name: String): Intent {
            val intent = view(Uri.parse(telegram + name), "text/plain")
            val activity = Robolectric.buildActivity(OpenRecipeFileActivity::class.java, intent).create().get()
            return shadowOf(activity).nextStartedActivity
        }
        val collection = forwarded("menu.recipefork-collection")
        assertEquals(CollectionFileIntents.ACTION_IMPORT, collection.action)
        assertTrue(CollectionFileIntents.toImport(collection) != null)
        assertNull(RecipeFileIntents.fileOf(collection))

        val recipe = forwarded("lemon-cake.recipefork")
        assertEquals(Intent.ACTION_VIEW, recipe.action)
        assertTrue(RecipeFileIntents.fileOf(recipe) != null)
        assertNull(CollectionFileIntents.toImport(recipe))

        val book = forwarded("Recipe book.recipefork-book")
        assertEquals(BookFileIntents.ACTION_RESTORE, book.action)
        assertNull(CollectionFileIntents.toImport(book))
        assertNull(RecipeFileIntents.fileOf(book))

        // A view of a collection reaching the recipe's import some other way is no recipe file to it.
        assertNull(RecipeFileIntents.fileOf(view(Uri.parse(fileManager + "menu.recipefork-collection"), null)))
        assertNull(RecipeFileIntents.fileOf(view(Uri.parse("content://media/external/file/77"), CollectionFile.MIME_TYPE)))
        // A recipe file's own type is still a recipe's.
        assertEquals(Intent.ACTION_VIEW, RecipeFileIntents.forward(context, view(Uri.parse("content://media/external/file/77"), RecipeFile.MIME_TYPE))!!.action)
    }

    // A pattern matches the whole path: ".recipefork" does not take a collection file as a recipe, nor the other way.
    @Test
    fun aNameMerelyLikeACollectionFileIsNotOffered() {
        for (type in listOf("application/octet-stream", "application/zip", null)) {
            assertNotOffered(view(Uri.parse(fileManager + "menu.recipefork-collections"), type))
            assertNotOffered(view(Uri.parse(fileManager + "menu.recipefork-collection.zip"), type))
            assertNotOffered(view(Uri.parse(telegram + "menu.recipeforkcollection"), type))
            assertNotOffered(view(Uri.parse(telegram + "recipefork-collection"), type))
        }
        assertNotOffered(send(Uri.parse(fileManager + "menu.recipefork-collection"), "application/zip"))
    }

    // What arrives that is no collection file is no collection to import.
    @Test
    fun anIntentWithoutACollectionIsNoneToImport() {
        assertNull(CollectionFileIntents.arriving(view(Uri.parse("https://example.com/menu.recipefork-collection"), null)))
        assertNull(CollectionFileIntents.arriving(view(Uri.parse(fileManager + "lemon-cake.recipefork"), null)))
        assertNull(CollectionFileIntents.arriving(null))
        assertNull(CollectionFileIntents.toImport(view(Uri.parse(fileManager + "menu.recipefork-collection"), null)))
        assertNull(CollectionFileIntents.toImport(Intent(CollectionFileIntents.ACTION_IMPORT).setData(Uri.parse("https://example.com/x"))))
    }
}
