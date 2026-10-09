package app.recipefork.app.editor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

/**
 * A write the book refuses: the editor knows it, writes the recipe once more when asked whether everything is written,
 * and never deletes the files of a removal's media while the book may still hold them.
 */
@RunWith(AndroidJUnit4::class)
class EditorRefusedWriteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).allowMainThreadQueries().build()
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private var refuse = false
    private val recipes = object : RecipeRepository(ActiveBook(BookName.Pool, database)) {
        override suspend fun save(recipe: Recipe) {
            if (refuse) throw IOException("the book refused the write")
            super.save(recipe)
        }
    }
    private val saving = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun close() = database.close()

    private fun open(): RecipeEditor = runBlocking {
        val recipe = Recipe.create(now, null).copy(
            title = "Pancakes",
            steps = listOf(Step(0, "Mix."), Step(1, "Fry.")),
            media = listOf(Media("fry.jpg", null, MediaKind.PHOTO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE)),
        )
        recipes.save(recipe)
        RecipeEditor(recipes, recipe.id, { now }, saving).also { it.load() }
    }

    private fun stored(editor: RecipeEditor) = runBlocking { recipes.get(editor.id)!! }

    // Asked whether everything is written after a refusal, with no change made since, the editor writes the recipe
    // once more: still refused, it says so; taken, the book holds the change.
    @Test
    fun writtenWritesARefusedRecipeOnceMore() = runBlocking {
        val editor = open()
        refuse = true
        editor.setTitle("Pancakes with jam")
        assertTrue(editor.refused.value)
        assertFalse(editor.written())
        assertEquals("Pancakes", stored(editor).title)

        refuse = false
        assertTrue(editor.written())
        assertFalse(editor.refused.value)
        assertEquals("Pancakes with jam", stored(editor).title)
    }

    // A removal ended while the book has refused the last write keeps its media's files: the book may still hold them.
    @Test
    fun aRemovalEndedWhileTheBookRefusesKeepsItsFiles() = runBlocking {
        val editor = open()
        var deleted: List<String>? = null
        refuse = true
        editor.removeStep(1) { deleted = it }
        editor.endRemoval(requireNotNull(editor.removal.value))
        assertFalse(editor.written())
        assertNull(editor.removal.value)
        assertNull(deleted)
        assertEquals(2, stored(editor).steps.size)
    }

    // The same removal ended once the book took it hands its files over to be deleted.
    @Test
    fun aRemovalEndedOnceTheBookTookItHandsItsFilesOver() = runBlocking {
        val editor = open()
        var deleted: List<String>? = null
        editor.removeStep(1) { deleted = it }
        editor.endRemoval(requireNotNull(editor.removal.value))
        assertTrue(editor.written())
        assertEquals(listOf("fry.jpg"), deleted)
        assertEquals(1, stored(editor).steps.size)
    }
}
