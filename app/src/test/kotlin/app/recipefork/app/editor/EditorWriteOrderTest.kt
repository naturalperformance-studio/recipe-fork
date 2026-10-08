package app.recipefork.app.editor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.concurrent.thread

/**
 * FLOW-024: the book receives the editor's changes in the order they were made, whatever runs its writes. A change
 * made inside a coroutine of the writing scope — as a photograph prepared there is placed — is queued at once, not
 * once that coroutine is done, so a change made after it elsewhere is never written before it and then overwritten.
 */
@RunWith(AndroidJUnit4::class)
class EditorWriteOrderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, database))
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    @After
    fun close() = database.close()

    @Test
    fun aChangeMadeInsideTheWritingScopeIsWrittenBeforeALaterOne() = runBlocking {
        val recipe = Recipe.create(now, null)
        recipes.save(recipe)
        // Unconfined runs a launch made inside one of its own coroutines only once that coroutine is done.
        val saving = CoroutineScope(Dispatchers.Unconfined)
        val editor = RecipeEditor(recipes, recipe.id, { now }, saving).also { it.load() }

        saving.launch {
            editor.setTitle("First")
            // A later change, made on another thread while the first's coroutine is still running.
            thread { editor.setTitle("Second") }.join()
        }.join()
        editor.written()

        assertEquals("Second", recipes.get(recipe.id)!!.title)
    }
}
