package app.recipefork.core.file

import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.RecipeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What an importer believes of a collection file's document (v0.36, T-256): untrusted identities, name and author. */
class CollectionFileCheckTest {
    private val good = CollectionFileContent(
        CollectionId("3c1f9a52-7d0e-4b8a-9f61-2e5d8c4b7a10"), "New Year's menu", "anna", listOf(RecipeId("r-1"), RecipeId("r-2")),
    )

    private fun refused(content: CollectionFileContent) = assertTrue(CollectionFileCheck.problems(content).isNotEmpty(), "$content")

    @Test
    fun aWellMadeDocumentPasses() {
        assertEquals(emptyList(), CollectionFileCheck.problems(good))
        assertEquals(emptyList(), CollectionFileCheck.problems(good.copy(author = null, recipes = emptyList())))
        assertEquals(emptyList(), CollectionFileCheck.problems(good.copy(recipes = (1..CollectionFileCheck.MAX_RECIPES).map { RecipeId("r-$it") })))
    }

    @Test
    fun anIdentityThatIsNoneIsRefused() {
        for (id in listOf("", "../evil", "a/b", "/abs", "a b", "x".repeat(65), "c:evil", "a\u0000b")) {
            refused(good.copy(id = CollectionId(id)))
            refused(good.copy(recipes = listOf(RecipeId("r-1"), RecipeId(id))))
        }
    }

    @Test
    fun aNameTheBookDoesNotTakeIsRefused() {
        refused(good.copy(name = ""))
        refused(good.copy(name = "   "))
        refused(good.copy(name = "n".repeat(61)))
        assertEquals(emptyList(), CollectionFileCheck.problems(good.copy(name = "n".repeat(60))))
    }

    @Test
    fun aNicknameThatIsNoneIsRefused() {
        refused(good.copy(author = " "))
        refused(good.copy(author = "a".repeat(101)))
    }

    @Test
    fun aRecipeListedTwiceOrTooManyAreRefused() {
        refused(good.copy(recipes = listOf(RecipeId("r-1"), RecipeId("r-1"))))
        refused(good.copy(recipes = (0..CollectionFileCheck.MAX_RECIPES).map { RecipeId("r-$it") }))
    }
}
