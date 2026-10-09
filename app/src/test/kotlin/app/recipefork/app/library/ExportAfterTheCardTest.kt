package app.recipefork.app.library

import app.recipefork.app.card.CardMode
import app.recipefork.app.ui.AppNavHost
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.Mode
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.toRecipe
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.minutes

/**
 * T-092: whatever the card shows — a fixing, another count, the usual count — the recipe's file carries the recipe as
 * written: its serving count and every ingredient's authored quantity; and nothing the card's scaling does is ever
 * written to the book. Driven as the owner did: the card opened from the book, scaled, left; the book's Export; and
 * the file opened again once the recipe is deleted, which brings the recipe back as written — not at the scale the
 * session still held for the deleted card of the same identity, which is what read as a recalculated export.
 */
@RunWith(AndroidJUnit4::class)
class ExportAfterTheCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = ApplicationProvider.getApplicationContext<RecipeForkApplication>().container
    // The application's own book, on disk, so that what the book holds can be read again from its file.
    private val recipes = container.recipes
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    /** For 4: chicken and rice by weight, indivisible eggs, salt by the teaspoon, pepper to taste, flour unfinished. */
    private val pilaf = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Rice", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(400)),
            Ingredient(2, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
            Ingredient(3, null, freeTextName = "Salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(3, 2)),
            Ingredient(4, null, freeTextName = "Pepper", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
            Ingredient(5, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = null, enteredQuantity = "0"),
        ),
        steps = listOf(
            Step(0, "Fry the chicken", listOf(IngredientReference(0, 8)), Timer(10.minutes)),
            Step(1, "Add the rice and simmer", listOf(IngredientReference(1, 8))),
        ),
    )

    @Before
    fun setUp() {
        FileProviderCache.clear()
        runBlocking { recipes.save(pilaf) }
        // Amounts are fixed on the card's Cooking tab: the card opens there.
        container.lastCardTab.keep(CardMode.COOKING)
    }

    @After
    fun tearDown() {
        container.usualServings.set(null)
        runBlocking {
            recipes.delete(pilaf.id)
            recipes.removeMarked(pilaf.id)
        }
    }

    private fun show() {
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, Mode.Serverless) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(pilaf.id.value)) }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun openCard() {
        val entry = LibraryTags.entry(pilaf.id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(entry))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(entry).performClick()
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.ingredient(0)) }
    }

    private fun back() {
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.OWN_LIST) }
    }

    private fun more(times: Int) = repeat(times) {
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun fix(position: Int, amount: String) {
        compose.onNodeWithTag(CardTags.fixAmount(position), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX) }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement(amount)
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.FIX) }
    }

    private fun line(position: Int) = compose.textsOf(CardTags.ingredient(position)).joinToString(" ")
    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()

    /** The book's Export, then the document of the file handed to the share sheet. */
    private fun export(): RecipeFileContent = document(exportedFile())

    private fun document(uri: Uri) = RecipeFile.decode(
        ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == RecipeFile.DOCUMENT }
            zip.readBytes().toString(Charsets.UTF_8)
        },
    )

    /** The book's Export: the address of the file handed to the share sheet. */
    private fun exportedFile(): Uri {
        val entry = LibraryTags.actions(pilaf.id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(entry))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        val activity = shadowOf(compose.activity)
        // Anything an earlier export started is taken first, so that the sheet read below is this one's.
        while (activity.nextStartedActivity != null) continue
        compose.chooseEntryAction(pilaf.id.value, LibraryTags.export(pilaf.id.value))
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivity() != null }
        val sheet = activity.nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, sheet.action)
        @Suppress("DEPRECATION")
        return sheet.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
    }

    /** The recipe as the book's file holds it, read through a database opened afresh on that file. */
    private fun fromDisk(): Recipe {
        val reopened = BookDatabase.open(context, BookName.Pool)
        try {
            return runBlocking { reopened.recipes().get(pilaf.id.value) }!!.toRecipe()
        } finally {
            reopened.close()
        }
    }

    /** The file carries the recipe exactly as authored, and the book holds it unchanged. */
    private fun assertAsWritten(read: RecipeFileContent) {
        assertEquals("the file's serving count", pilaf.servings, read.servings)
        assertEquals("the file's ingredients", pilaf.ingredients, read.ingredients)
        assertEquals("the file's steps", pilaf.steps, read.steps)
        assertEquals("the file's document", RecipeFileContent.of(pilaf, null, null), read.copy(media = emptyList()))
        assertEquals("the book, read from disk", pilaf, fromDisk())
        assertEquals("the book, through the repository", pilaf, runBlocking { recipes.get(pilaf.id) })
    }

    // The owner's report: fixing an amount on the card, then exporting from the book.
    @Test
    fun afterFixingAnAmountTheFileCarriesTheRecipeAsWritten() {
        show()
        openCard()
        fix(1, "200")
        assertTrue(line(1), "fixed" in line(1))
        assertTrue(line(0), "250" in line(0))
        back()
        assertAsWritten(export())
        // And the card still shows the fixing it was left at: export took nothing from it.
        openCard()
        assertTrue(line(1), "fixed" in line(1))
    }

    @Test
    fun afterChangingTheCountTheFileCarriesTheRecipeAsWritten() {
        show()
        openCard()
        more(2)
        assertTrue(servings(), "6" in servings())
        back()
        assertAsWritten(export())
    }

    @Test
    fun withTheUsualCountSetTheFileCarriesTheRecipeAsWritten() {
        container.usualServings.set(Rational.of(2))
        show()
        openCard()
        assertTrue(servings(), "2" in servings())
        assertTrue(line(0), "250" in line(0))
        back()
        assertAsWritten(export())
    }

    @Test
    fun afterAFixingAndAChangedCountWithTheUsualCountSetTheFileCarriesTheRecipeAsWritten() {
        container.usualServings.set(Rational.of(2))
        show()
        openCard()
        more(1)
        fix(0, "1")
        back()
        assertAsWritten(export())
        // Exported again, after the card was read once more, the file is the same.
        openCard()
        back()
        assertAsWritten(export())
    }

    // The owner's report as they saw it: the recipe fixed on its card and exported, then deleted from the book and its
    // file opened again in the same session. The file holds the recipe as written, and the recipe it brings back opens
    // as written too — not at the count and fixing the session held for the deleted recipe of the same identity.
    @Test
    fun theFileOpenedAgainBringsTheRecipeAsWrittenNotTheDeletedCardsScale() {
        show()
        openCard()
        fix(1, "200")
        back()
        val file = exportedFile()
        assertAsWritten(document(file))
        runBlocking { recipes.delete(pilaf.id) }
        val opened = MainActivity.openRecipeFile(context, Mode.Serverless, RecipeImport(recipes, context.filesDir), session, file)!!
        // The import runs on the session's scope; the main looper and the book's threads are let run to its end.
        while (!opened.isCompleted) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(5)
        }
        ShadowLooper.idleMainLooper()
        assertEquals(FileMessage.Kind.ADDED, session.message.value?.kind)
        session.told()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(pilaf.id.value)) }
        compose.onNodeWithTag(LibraryTags.entry(pilaf.id.value)).performClick()
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.ingredient(1)) }
        assertTrue(servings(), servings().endsWith(" 4"))
        assertFalse(line(1), "fixed" in line(1))
        assertTrue(line(0), "500 g" in line(0))
        assertTrue(line(1), "400 g" in line(1))
        val arrived = runBlocking { recipes.get(pilaf.id) }!!
        assertEquals(pilaf.servings, arrived.servings)
        assertEquals(pilaf.ingredients, arrived.ingredients)
    }
}
