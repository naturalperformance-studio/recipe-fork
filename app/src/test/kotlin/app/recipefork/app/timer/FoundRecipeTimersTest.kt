package app.recipefork.app.timer

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.app.card.CardTags
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.minutes

/** FLOW-064 (1), FLOW-009 (1): a found recipe's timers run as any recipe's do, keyed by its book, identity and step. */
@RunWith(AndroidJUnit4::class)
class FoundRecipeTimersTest : CardTimersKit() {
    @Test
    fun aFoundRecipesTimerRunsAndEnds() {
        val files = File(context.cacheDir, "timer-found-files")
        val content = TestRecipeFiles.soup()
        runBlocking { RecipeImport(recipes, files, clock = { now }).import { TestRecipeFiles.file(content).inputStream() } }
        val found = runBlocking { recipes.get(content.id) }!!
        assertTrue(!found.state.editable)
        show(found)
        assertTrue(!shown(CardTags.EDIT))
        start(0)
        awaitHeld { it.size == 1 }
        val held = held().single()
        assertEquals(TimerKey(BookName.Pool, RecipeId(content.id.value), 0), held.key)
        assertEquals("Bean soup", held.recipe)
        assertEquals(10.minutes, held.duration)
        pass(4.minutes)
        awaitStatus(0, "6:00")
        pass(6.minutes)
        awaitStatus(0, "Finished")
        assertTrue(timers.announcements.started.any { it.key == held.key })
        files.deleteRecursively()
    }
}
