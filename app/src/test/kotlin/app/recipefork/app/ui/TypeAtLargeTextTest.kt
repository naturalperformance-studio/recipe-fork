package app.recipefork.app.ui

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.assertWhole
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.chooseCardTab
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * T-297, scenario 6 of v0.41: in the bundled fonts, at twice the font scale on a 360 dp phone, in English and in
 * Russian, a book entry's title, the card's title and Cooking's current step each read whole — no word broken inside,
 * nothing cut — and each is set in the reading family, Literata, which is what the text is measured in.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp", fontScale = 2f)
class TypeAtLargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun assertReadsWhole(title: String, step: String, language: RecipeLanguage) {
        val recipe = Recipe.create(now, owner = null).copy(title = title, language = language, steps = listOf(Step(0, step), Step(1, step)))
        runBlocking { recipes.save(recipe) }
        session.choose(LibraryView.OWN)
        val card = CardEnvironment(HeldUsualServings(), lastTab = HeldLastCardTab(CardMode.VIEW)) { emptyList() }
        compose.setContent {
            AppTheme { AppNavHost(session, recipes, card = card) }
        }
        val id = recipe.id.value
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.entryTitle(id)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertInTheReadingFamily(LibraryTags.entryTitle(id))
        compose.assertWhole(LibraryTags.entryTitle(id))

        compose.onNodeWithTag(LibraryTags.entry(id)).performClick()
        compose.awaitCard()
        compose.chooseCardTab(CardMode.VIEW)
        assertInTheReadingFamily(CardTags.TITLE)
        compose.assertWhole(CardTags.TITLE)

        compose.chooseCardTab(CardMode.COOKING)
        compose.onNodeWithTag(CardTags.step(0)).performScrollTo()
        compose.waitForIdle()
        assertInTheReadingFamily(STEP_TEXT)
        compose.assertWhole(STEP_TEXT)
    }

    /** The text tagged [tag] is laid out in Literata, and Literata measures otherwise than the platform's own font. */
    private fun assertInTheReadingFamily(tag: String) {
        val layout = WholeWords.layouts(compose, hasTestTag(tag)).single()
        assertEquals("$tag in the reading family", ReadingFamily, layout.layoutInput.style.fontFamily)
        val input = layout.layoutInput
        val measurer = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
        fun width(family: FontFamily) = measurer.measure(input.text, input.style.merge(TextStyle(fontFamily = family))).size.width
        assertNotEquals("$tag measured in the bundled font, not the platform's", width(FontFamily.Default), width(ReadingFamily))
    }

    @Test
    fun inEnglishTheBookTheCardAndCookingReadWhole() = assertReadsWhole(
        title = "Chicken with potatoes, lemon and rosemary, roasted slowly in the oven",
        step = "Brown the chicken over a high heat until golden, then move it to the dish and roast, basting " +
            "occasionally, until the juices run clear",
        language = RecipeLanguage.EN,
    )

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianTheBookTheCardAndCookingReadWhole() = assertReadsWhole(
        title = "Курица с картофелем, лимоном и розмарином, медленно запечённая в духовке",
        step = "Обжарьте курицу на сильном огне до золотистой корочки, затем переложите в форму и запекайте, " +
            "время от времени поливая соком, до прозрачного сока",
        language = RecipeLanguage.RU,
    )

    private companion object {
        /** Cooking's current step: the first, open as the card turns to Cooking. */
        const val STEP_TEXT = "card-step-0-text"
    }
}
