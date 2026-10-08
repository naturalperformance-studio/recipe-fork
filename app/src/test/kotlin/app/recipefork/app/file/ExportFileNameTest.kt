package app.recipefork.app.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * FLOW-063: the exported file's suggested name is ASCII — Telegram drops the extension from a Cyrillic name, and the
 * receiving phone then cannot recognise the file — with the product's extension kept; a file so named imports as
 * before.
 */
@RunWith(AndroidJUnit4::class)
class ExportFileNameTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val friendsBook = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database), context.filesDir)
    private val friendsRecipes = RecipeRepository(ActiveBook(BookName.OfAccount("file-name-friend"), friendsBook.database), context.filesDir)
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    @Before
    fun forgetEarlierDirectories() = FileProviderCache.clear()

    @After
    fun close() {
        book.close()
        friendsBook.close()
    }

    private fun assertAscii(name: String) = assertTrue(name, name.all { it.code in 0x21..0x7E })

    @Test
    fun aRussianTitleIsSpeltInLatinLetters() {
        assertEquals("Borshch-s-pampushkami.recipefork", RecipeExport.fileName("Борщ с пампушками"))
        assertEquals("Yozhiki-v-smetane.recipefork", RecipeExport.fileName("Ёжики в сметане"))
        assertEquals("Shchi-i-kasha-2.recipefork", RecipeExport.fileName("Щи и каша №2"))
    }

    @Test
    fun anEnglishTitleKeepsItsWordsJoinedByHyphens() {
        assertEquals("Shakshuka.recipefork", RecipeExport.fileName("Shakshuka"))
        assertEquals("Mom-s-apple-pie-v2.recipefork", RecipeExport.fileName("  Mom's apple pie: v2!  "))
        assertEquals("Creme-brulee.recipefork", RecipeExport.fileName("Crème brûlée"))
        assertEquals("Soup-stew.recipefork", RecipeExport.fileName("Soup / stew"))
    }

    @Test
    fun aTitleWithNothingToSpellGetsTheFixedName() {
        assertEquals("recipe.recipefork", RecipeExport.fileName("?!… — «»"))
        assertEquals("recipe.recipefork", RecipeExport.fileName(""))
        assertEquals("recipe.recipefork", RecipeExport.fileName("   "))
        assertEquals("recipe.recipefork", RecipeExport.fileName("麻婆豆腐"))
    }

    @Test
    fun aVeryLongTitleIsCutToABoundedName() {
        val name = RecipeExport.fileName("Очень длинное название рецепта ".repeat(20))
        assertAscii(name)
        assertTrue(name, name.endsWith(".recipefork"))
        val stem = name.removeSuffix(".recipefork")
        assertTrue(name, stem.length <= 80)
        assertTrue(name, stem.startsWith("Ochen-dlinnoe-nazvanie-retsepta-"))
        assertTrue("no trailing hyphen: $name", !stem.endsWith("-"))
    }

    @Test
    fun aRecipeWithARussianTitleExportsUnderAnAsciiNameAndImportsAsBefore() = runBlocking {
        val recipe = Recipe.create(now, owner = null).copy(title = "Сырники", steps = listOf(Step(0, "Смешайте творог с яйцом")))
        recipes.save(recipe)
        val uri = RecipeExport.write(context, recipes, recipe.id)!!
        assertEquals("Syrniki.recipefork", uri.lastPathSegment)
        assertAscii(uri.toString())
        val outcome = RecipeImport(friendsRecipes, context.filesDir, clock = { now }).import(context, uri)
        assertEquals(ImportOutcome.Added(recipe.id, "Сырники"), outcome)
    }
}
