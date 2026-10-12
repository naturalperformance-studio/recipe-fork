package app.recipefork.core.file

import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.RecipeCollection
import app.recipefork.core.recipe.RecipeId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The collection file's document, `collection.json` (v0.36): its format version, the collection's identity, name and
 * stated author, and its recipes' identities in order; the folder each recipe lies in.
 */
class CollectionFileTest {
    private val made = Instant.parse("2026-10-12T10:00:00Z")
    private val soup = RecipeId("r-soup")
    private val pie = RecipeId("r-pie")
    private val salad = RecipeId("r-salad")

    private fun collection(origin: CollectionOrigin, statedAuthor: String?) =
        RecipeCollection(CollectionId("c-1"), "New Year's menu", origin, statedAuthor, made, listOf(pie, soup, salad))

    @Test
    fun theFileIsNamedAndTypedAsACollectionFile() {
        assertEquals("recipefork-collection", CollectionFile.EXTENSION)
        assertEquals("application/vnd.recipefork.collection+zip", CollectionFile.MIME_TYPE)
        assertEquals("collection.json", CollectionFile.DOCUMENT)
        assertEquals(1, CollectionFile.FORMAT_VERSION)
    }

    // The document is written exactly so: a reader of format 1 relies on each name.
    @Test
    fun theDocumentWritesItsFormatVersionIdentityNameRecipesInOrderAndAuthor() {
        val content = CollectionFileContent(CollectionId("c-1"), "New Year's menu", "anna", listOf(pie, soup, salad))
        assertEquals(
            """
            {
                "format_version": 1,
                "collection": {
                    "id": "c-1",
                    "name": "New Year's menu",
                    "recipes": [
                        "r-pie",
                        "r-soup",
                        "r-salad"
                    ]
                },
                "author": {
                    "nickname": "anna"
                }
            }
            """.trimIndent(),
            CollectionFile.encode(content),
        )
    }

    @Test
    fun aCollectionStatingNoAuthorWritesNone() {
        val content = CollectionFileContent(CollectionId("c-2"), "Salads", null, emptyList())
        assertEquals(
            """
            {
                "format_version": 1,
                "collection": {
                    "id": "c-2",
                    "name": "Salads",
                    "recipes": []
                }
            }
            """.trimIndent(),
            CollectionFile.encode(content),
        )
    }

    @Test
    fun theDocumentReadsBackAsItWasWritten() {
        val stated = CollectionFileContent(CollectionId("c-1"), "Пироги Анны", "anna", listOf(pie, soup, salad))
        assertEquals(stated, CollectionFile.decode(CollectionFile.encode(stated)))
        val none = CollectionFileContent(CollectionId("c-2"), "Salads", null, emptyList())
        assertEquals(none, CollectionFile.decode(CollectionFile.encode(none)))
    }

    // An imported collection shared onward keeps the author its own file stated.
    @Test
    fun anImportedCollectionNamesItsStatedAuthor() {
        val content = CollectionFileContent.of(collection(CollectionOrigin.IMPORTED, "anna"), ownerNickname = "me")
        assertEquals(CollectionFileContent(CollectionId("c-1"), "New Year's menu", "anna", listOf(pie, soup, salad)), content)
        assertNull(CollectionFileContent.of(collection(CollectionOrigin.IMPORTED, null), ownerNickname = "me").author)
        assertNull(CollectionFileContent.of(collection(CollectionOrigin.IMPORTED, "  "), ownerNickname = "me").author)
        assertEquals("anna", CollectionFileContent.of(collection(CollectionOrigin.IMPORTED, " anna "), ownerNickname = null).author)
    }

    // An own collection names the reader as the reader's recipe export does: no account yet, so no author.
    @Test
    fun anOwnCollectionNamesTheOwnerAsARecipeExportDoes() {
        val content = CollectionFileContent.of(collection(CollectionOrigin.OWN, null), ownerNickname = null)
        assertEquals(CollectionFileContent(CollectionId("c-1"), "New Year's menu", null, listOf(pie, soup, salad)), content)
        assertEquals("ivan", CollectionFileContent.of(collection(CollectionOrigin.OWN, "anna"), ownerNickname = "ivan").author)
        assertNull(CollectionFileContent.of(collection(CollectionOrigin.OWN, "anna"), ownerNickname = null).author)
    }

    @Test
    fun theFileListsTheRecipesItHoldsInTheCollectionsOrder() {
        val content = CollectionFileContent.of(collection(CollectionOrigin.OWN, null), null, recipes = listOf(pie, salad))
        assertEquals(listOf(pie, salad), content.recipes)
    }

    @Test
    fun eachRecipeLiesInTheFolderOfItsPlaceCountedFromOne() {
        assertEquals("recipes/1/", CollectionFile.recipeFolder(0))
        assertEquals("recipes/2/", CollectionFile.recipeFolder(1))
        assertEquals("recipes/12/", CollectionFile.recipeFolder(11))
        assertFailsWith<IllegalArgumentException> { CollectionFile.recipeFolder(-1) }
    }

    @Test
    fun aDocumentOfALaterFormatIsRefusedAsLater() {
        val later = assertFailsWith<LaterCollectionFile> {
            CollectionFile.decode("""{"format_version": 2, "collection": {"id": "c", "name": "n", "recipes": []}}""")
        }
        assertEquals(2, later.formatVersion)
    }

    @Test
    fun aDocumentThatIsNoCollectionIsRefusedAsUnreadable() {
        listOf(
            "",
            "[]",
            """{"collection": {"id": "c", "name": "n", "recipes": []}}""",
            """{"format_version": 0, "collection": {"id": "c", "name": "n", "recipes": []}}""",
            """{"format_version": 1}""",
            """{"format_version": 1, "collection": {"id": "c", "name": "n", "recipes": [1, {}]}}""",
        ).forEach { text ->
            val refused = assertFailsWith<UnreadableRecipeFile>(text) { CollectionFile.decode(text) }
            assertEquals(false, refused is LaterCollectionFile, text)
        }
    }
}
