package app.recipefork.app.file

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.core.file.RecipeFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * FLOW-064 as the platform sees it: which files the merged manifest offers the application for — a recipe file, by its
 * extension whatever type the sender declares or by the product's own type, and nothing else — and how the one
 * activity that takes them hands them on to [MainActivity] in the application's own task.
 */
@RunWith(AndroidJUnit4::class)
class RecipeFileRoutingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opener = OpenRecipeFileActivity::class.java.name

    /** The activities of this application [intent] is offered to, as a chooser would list them. */
    private fun offered(intent: Intent): List<String> =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName == context.packageName }
            .map { it.activityInfo.name }

    private fun view(uri: Uri, type: String?) = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)

    private fun send(uri: Uri, type: String?) =
        Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri)

    private fun assertOffered(intent: Intent) = assertEquals("$intent", listOf(opener), offered(intent))

    private fun assertNotOffered(intent: Intent) = assertEquals("$intent", emptyList<String>(), offered(intent))

    /** The types a sender may declare a recipe file with: Telegram's guess, a generic one, a wildcard, or none. */
    private val declared = listOf(
        RecipeFile.MIME_TYPE, "application/zip", "application/octet-stream", "text/plain", "*/*", null,
    )

    private val telegram =
        "content://org.telegram.messenger.provider/media/Android/data/org.telegram.messenger/files/Telegram/Telegram Files/"
    private val telegramEncoded =
        "content://org.telegram.messenger.provider/media/Android/data/org.telegram.messenger/files/Telegram/Telegram%20Files/"
    private val fileManager = "content://com.sec.android.app.myfiles.FileProvider/device_storage/0/Download/"
    private val fileAddress = "file:///storage/emulated/0/Download/"

    // Scenario 6, 7: a recipe file tapped in Telegram or a file manager, or opened by its path, is offered to the
    // application whatever type it comes declared with — and its name may hold further dots.
    @Test
    fun aRecipeFileIsOfferedWhateverTypeItDeclares() {
        for (place in listOf(telegram, telegramEncoded, fileManager, fileAddress)) {
            for (name in listOf("lemon-cake.recipefork", "lemon.cake.v2.recipefork")) {
                for (type in declared) assertOffered(view(Uri.parse(place + name), type))
            }
        }
    }

    // The path's spaces are matched as the name holds them, spelt plainly or percent-encoded.
    @Test
    fun telegramsAddressIsMatchedSpeltEitherWay() {
        assertEquals(
            Uri.parse(telegram + "lemon-cake.recipefork").path,
            Uri.parse(telegramEncoded + "lemon-cake.recipefork").path,
        )
        assertOffered(view(Uri.parse(telegram + "lemon-cake.recipefork"), "text/plain"))
        assertOffered(view(Uri.parse(telegramEncoded + "lemon-cake.recipefork"), "text/plain"))
    }

    // A file declared with the product's own type is offered to the application whatever its address: no name in it.
    @Test
    fun theProductsTypeIsOfferedWhateverTheAddress() {
        assertOffered(view(Uri.parse("content://com.android.providers.downloads.documents/document/1234"), RecipeFile.MIME_TYPE))
        assertOffered(view(Uri.parse("content://media/external/file/77"), RecipeFile.MIME_TYPE))
        assertOffered(view(Uri.parse("file:///storage/emulated/0/Download/cake"), RecipeFile.MIME_TYPE))
    }

    // A recipe file shared to the application by the product's type is offered too.
    @Test
    fun aRecipeFileSharedByTheProductsTypeIsOffered() {
        assertOffered(send(Uri.parse("content://com.android.providers.downloads.documents/document/1234"), RecipeFile.MIME_TYPE))
        assertNotOffered(send(Uri.parse(fileManager + "lemon-cake.recipefork"), "application/zip"))
        assertNotOffered(send(Uri.parse(fileManager + "photo.jpg"), "image/jpeg"))
    }

    // Scenario 8 — the owner's rule: the application is offered only for recipe files, never for any other file.
    @Test
    fun aPhotoIsNotOffered() {
        assertNotOffered(view(Uri.parse(fileManager + "IMG_2041.jpg"), "image/jpeg"))
        assertNotOffered(view(Uri.parse(telegram + "IMG_2041.jpg"), "image/jpeg"))
        assertNotOffered(view(Uri.parse("content://media/external/images/media/41"), "image/jpeg"))
    }

    @Test
    fun aPdfIsNotOffered() {
        assertNotOffered(view(Uri.parse(fileManager + "menu.pdf"), "application/pdf"))
        assertNotOffered(view(Uri.parse(telegram + "menu.v2.pdf"), "application/pdf"))
    }

    @Test
    fun aZipIsNotOffered() {
        assertNotOffered(view(Uri.parse(fileManager + "photos.zip"), "application/zip"))
        assertNotOffered(view(Uri.parse(telegram + "lemon-cake.recipefork.zip"), "application/zip"))
        assertNotOffered(view(Uri.parse("content://com.android.providers.downloads.documents/document/1234"), "application/zip"))
    }

    @Test
    fun aFileOfUnknownTypeIsNotOffered() {
        assertNotOffered(view(Uri.parse(fileManager + "notes.bin"), "application/octet-stream"))
        assertNotOffered(view(Uri.parse(telegram + "recipefork"), "application/octet-stream"))
        assertNotOffered(view(Uri.parse(fileManager + "cake.recipeforks"), "application/octet-stream"))
        assertNotOffered(view(Uri.parse("content://com.android.providers.downloads.documents/document/1234"), "application/octet-stream"))
        assertNotOffered(view(Uri.parse("content://com.android.providers.downloads.documents/document/1234"), null))
        assertNotOffered(view(Uri.parse(fileManager + "notes.txt"), "text/plain"))
        assertNotOffered(view(Uri.parse(fileManager + "notes.txt"), null))
    }

    // The launcher's activity takes no file itself: every file passes through the one that hands it on.
    @Test
    fun theLaunchersActivityTakesNoFile() {
        val main = MainActivity::class.java.name
        assertTrue(main !in offered(view(Uri.parse(fileManager + "lemon-cake.recipefork"), RecipeFile.MIME_TYPE)))
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        assertEquals(listOf(main), context.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo.name })
    }

    // An opened file is handed on to MainActivity in the application's own task — never stacked in the sender's —
    // reaching the one already open, with the reading of the file granted on; the opener shows nothing and is gone.
    @Test
    fun anOpenedFileIsHandedOnToTheApplicationsOwnTask() {
        val uri = Uri.parse(telegram + "lemon-cake.recipefork")
        val opener = Robolectric.buildActivity(OpenRecipeFileActivity::class.java, view(uri, "text/plain")).create().get()
        val forwarded = shadowOf(opener).nextStartedActivity
        assertEquals(ComponentName(context, MainActivity::class.java), forwarded.component)
        assertEquals(Intent.ACTION_VIEW, forwarded.action)
        assertEquals(uri, forwarded.data)
        assertEquals(uri, forwarded.clipData!!.getItemAt(0).uri)
        for (flag in listOf(
            Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_CLEAR_TOP, Intent.FLAG_ACTIVITY_SINGLE_TOP,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )) {
            assertTrue("flag $flag", forwarded.flags and flag != 0)
        }
        assertEquals(0, forwarded.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(uri, RecipeFileIntents.fileOf(forwarded))
        assertTrue(opener.isFinishing)
    }

    // A shared recipe file is handed on as a view of the file it carries: imported exactly as an opened one.
    @Test
    fun aSharedFileIsHandedOnAsAView() {
        val uri = Uri.parse("content://com.android.providers.downloads.documents/document/1234")
        val opener = Robolectric.buildActivity(OpenRecipeFileActivity::class.java, send(uri, RecipeFile.MIME_TYPE)).create().get()
        val forwarded = shadowOf(opener).nextStartedActivity
        assertEquals(ComponentName(context, MainActivity::class.java), forwarded.component)
        assertEquals(Intent.ACTION_VIEW, forwarded.action)
        assertEquals(uri, forwarded.data)
        assertTrue(forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(opener.isFinishing)
    }

    // What holds no recipe file is handed on nowhere.
    @Test
    fun anIntentWithoutAFileIsHandedOnNowhere() {
        for (intent in listOf(
            send(Uri.parse(fileManager + "IMG_2041.jpg"), "image/jpeg"),
            Intent(Intent.ACTION_SEND).setType(RecipeFile.MIME_TYPE),
            Intent(Intent.ACTION_VIEW),
            view(Uri.parse("https://example.com/lemon-cake.recipefork"), null),
        )) {
            val opener = Robolectric.buildActivity(OpenRecipeFileActivity::class.java, intent).create().get()
            assertNull(shadowOf(opener).nextStartedActivity)
            assertTrue(opener.isFinishing)
        }
    }

    // The application's own task keeps one MainActivity: the launcher, a timer's notification and an opened file all
    // reach the one on top rather than stacking another; the opener belongs to no task of the application's.
    @Test
    fun theApplicationKeepsOneScreenInItsTask() {
        val packageManager = context.packageManager
        val main = packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, main.launchMode)
        val opener = packageManager.getActivityInfo(ComponentName(context, OpenRecipeFileActivity::class.java), 0)
        assertTrue(opener.exported)
        assertTrue(opener.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        assertTrue(opener.taskAffinity.isNullOrEmpty())
    }

    // The extension is taken in upper case too, as some phones and senders spell a file's name.
    @Test
    fun anUpperCaseExtensionIsOffered() {
        for (place in listOf(telegram, telegramEncoded, fileManager, fileAddress)) {
            for (name in listOf("LEMON-CAKE.RECIPEFORK", "lemon.cake.v2.RECIPEFORK")) {
                for (type in declared) assertOffered(view(Uri.parse(place + name), type))
            }
        }
        assertNotOffered(view(Uri.parse(fileManager + "PHOTO.JPG"), "image/jpeg"))
        assertNotOffered(view(Uri.parse(fileManager + "NOTES.BIN"), "application/octet-stream"))
    }

    // An address the application holds no reading of cannot have its reading granted on: the platform refuses that
    // start, and the file is handed on bare, so that the import says it cannot be read rather than the application
    // stopping.
    @Test
    fun aFileWhoseReadingCannotBeGrantedOnIsHandedOnBare() {
        val uri = Uri.parse("content://media/external/downloads/1234")
        val started = mutableListOf<Intent>()
        RecipeFileIntents.handOn(context, view(uri, RecipeFile.MIME_TYPE)) { intent ->
            started += intent
            if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) throw SecurityException("no grant to pass on")
        }
        assertEquals(2, started.size)
        val bare = started.last()
        assertEquals(ComponentName(context, MainActivity::class.java), bare.component)
        assertEquals(uri, bare.data)
        assertEquals(0, bare.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertNull(bare.clipData)
        assertTrue(bare.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(uri, RecipeFileIntents.fileOf(bare))
    }

    // A file whose reading is granted is handed on once, with its grant.
    @Test
    fun aGrantedFileIsHandedOnOnce() {
        val uri = Uri.parse(fileManager + "lemon-cake.recipefork")
        val started = mutableListOf<Intent>()
        RecipeFileIntents.handOn(context, view(uri, "text/plain")) { started += it }
        assertEquals(1, started.size)
        assertTrue(started.single().flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        RecipeFileIntents.handOn(context, view(Uri.parse("https://example.com/x.recipefork"), null)) { started += it }
        assertEquals(1, started.size)
    }

    // The intent a task was opened with, given again as it is reopened from the recent applications, is not followed:
    // neither its file nor its timer's card.
    @Test
    fun anIntentGivenAgainFromRecentsIsNotFollowed() {
        val file = view(Uri.parse(fileManager + "lemon-cake.recipefork"), "text/plain")
        val timer = app.recipefork.app.timer.TimerIntents.open(context, app.recipefork.app.timer.TimerKey("book-pool.db", "r1", 0))
        assertTrue(MainActivity.follows(file))
        assertTrue(MainActivity.follows(timer))
        assertEquals(false, MainActivity.follows(Intent(file).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
        assertEquals(false, MainActivity.follows(Intent(timer).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
        assertEquals(false, MainActivity.follows(null))
    }

    // T-242, scenario 4: a saved book tapped in Telegram or a file manager, or opened by its path, is offered to the
    // application whatever type it comes declared with, whatever dots its name holds, in either case.
    @Test
    fun aBookFileIsOfferedWhateverTypeItDeclares() {
        for (place in listOf(telegram, telegramEncoded, fileManager, fileAddress)) {
            for (name in listOf("Recipe book 2026-10-12.recipefork-book", "book.v2.old.recipefork-book", "BOOK.RECIPEFORK-BOOK")) {
                for (type in declared) assertOffered(view(Uri.parse(place + name), type))
            }
        }
    }

    // A share names the file to the platform only where the sender puts its address in the intent's data too; one in
    // EXTRA_STREAM alone, of a generic type, is not offered — as for any other file.
    @Test
    fun aBookFileSharedWithItsAddressIsOffered() {
        val book = Uri.parse(telegram + "Recipe book 2026-10-12.recipefork-book")
        for (type in declared) assertOffered(Intent(Intent.ACTION_SEND).setDataAndType(book, type).putExtra(Intent.EXTRA_STREAM, book))
        assertNotOffered(send(book, "application/octet-stream"))
    }

    // Each extension takes its own files only: a pattern matches the whole path.
    @Test
    fun aNameMerelyLikeABookFileIsNotOffered() {
        for (type in listOf("application/octet-stream", "application/zip", null)) {
            assertNotOffered(view(Uri.parse(fileManager + "book.recipefork-books"), type))
            assertNotOffered(view(Uri.parse(fileManager + "book.recipefork-book.zip"), type))
            assertNotOffered(view(Uri.parse(telegram + "book.recipeforkbook"), type))
            assertNotOffered(view(Uri.parse(telegram + "recipefork-book"), type))
        }
    }

    // A book is handed on to be restored, never viewed — which would import it as a recipe; a recipe file is still
    // handed on as a view, to be imported. Both with the reading of the file granted on.
    @Test
    fun aBookFileIsHandedOnToBeRestoredAndARecipeFileToBeImported() {
        for (intent in listOf(
            view(Uri.parse(telegram + "Recipe book 2026-10-12.recipefork-book"), "text/plain"),
            view(Uri.parse(fileManager + "Recipe book.RECIPEFORK-BOOK"), "application/octet-stream"),
            Intent(Intent.ACTION_SEND).setDataAndType(Uri.parse(fileManager + "b.recipefork-book"), "application/octet-stream"),
            send(Uri.parse(fileAddress + "b.recipefork-book"), RecipeFile.MIME_TYPE),
        )) {
            val forwarded = RecipeFileIntents.forward(context, intent)!!
            val uri = intent.data ?: RecipeFileIntents.streamOf(intent)
            assertEquals(BookFileIntents.ACTION_RESTORE, forwarded.action)
            assertEquals(ComponentName(context, MainActivity::class.java), forwarded.component)
            assertEquals(uri, BookFileIntents.toRestore(forwarded))
            assertNull(RecipeFileIntents.fileOf(forwarded))
            assertTrue(forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri, forwarded.clipData!!.getItemAt(0).uri)
        }
        val recipe = Uri.parse(telegram + "lemon-cake.recipefork")
        val forwarded = RecipeFileIntents.forward(context, view(recipe, "text/plain"))!!
        assertEquals(Intent.ACTION_VIEW, forwarded.action)
        assertEquals(recipe, RecipeFileIntents.fileOf(forwarded))
        assertNull(BookFileIntents.toRestore(forwarded))
        // A view of a book reaching the import some other way is no recipe file to it.
        assertNull(RecipeFileIntents.fileOf(view(Uri.parse(fileManager + "b.recipefork-book"), null)))
    }
}
