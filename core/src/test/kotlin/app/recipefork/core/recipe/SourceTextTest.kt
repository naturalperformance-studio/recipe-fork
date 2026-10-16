package app.recipefork.core.recipe

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * v0.39, T-279: a recipe keeps the text it was made from — exactly as given, up to 20,000 characters, longer cut at the
 * limit — and the owner's fork keeps it; anyone else's leaves it behind, as it leaves the description (FORK-002).
 */
class SourceTextTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val later = Instant.parse("2026-10-16T12:00:00Z")
    private val pasted = "Блины\n\n  Мука — 200 г\r\nМолоко 500 мл\t(тёплое)  \n\nСмешать. Жарить 2 мин.\n"

    @Test
    fun `a recipe holds no source text unless it is given one`() {
        assertNull(Recipe.create(now, null).sourceText)
    }

    @Test
    fun `a source text is kept exactly as given`() {
        assertEquals(pasted, Recipe.create(now, null).withSourceText(pasted).sourceText)
    }

    @Test
    fun `a source text of 20,000 characters is kept whole and a longer one is cut at the limit`() {
        assertEquals(20_000, RecipeBounds.MAX_SOURCE_TEXT)
        val atLimit = "а".repeat(20_000)
        assertEquals(atLimit, Recipe.sourceTextOf(atLimit))
        val longer = "b".repeat(19_999) + "cdef"
        assertEquals("b".repeat(19_999) + "c", Recipe.sourceTextOf(longer))
        assertEquals(20_000, Recipe.create(now, null).withSourceText("x".repeat(25_000)).sourceText!!.length)
    }

    @Test
    fun `a cut never splits a character in two`() {
        val emoji = "🥞"
        val text = "a".repeat(19_999) + emoji + "tail"
        assertEquals("a".repeat(19_999), Recipe.sourceTextOf(text))
    }

    @Test
    fun `an empty or blank text is no source text`() {
        assertNull(Recipe.sourceTextOf(null))
        assertNull(Recipe.sourceTextOf(""))
        assertNull(Recipe.sourceTextOf(" \n\t "))
    }

    @Test
    fun `an edit keeps the source text`() {
        val recipe = Recipe.create(now, null).withSourceText(pasted)
        assertEquals(pasted, recipe.edited(later) { it.copy(title = "Pancakes") }.sourceText)
    }

    @Test
    fun `the owner's fork keeps the source text`() {
        val recipe = Recipe.create(now, AccountId("me")).copy(title = "Блины").withSourceText(pasted)
        assertEquals(pasted, recipe.fork(later, AccountId("me")).sourceText)
    }

    @Test
    fun `anyone else's fork leaves the source text behind, as it leaves the description`() {
        val recipe = Recipe.create(now, null).copy(title = "Блины", description = "Мамины").withSourceText(pasted)
        val fork = recipe.fork(later, null, byAuthor = false)
        assertNull(fork.sourceText)
        assertEquals("", fork.description)
    }
}
