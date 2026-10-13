package app.recipefork.app.collections

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.book.CollectionRecipeRow
import app.recipefork.app.book.CollectionRow
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeCollection
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The book's collections (v0.36): own ones hold any recipe of the book, own or found, and change as the reader asks;
 * imported ones are frozen; a recipe deleted from the book leaves every collection, and a deleted collection takes no
 * recipe with it.
 */
@RunWith(AndroidJUnit4::class)
class CollectionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
    private val book = ActiveBook(BookName.Pool, database)
    private val recipes = RecipeRepository(book)
    private val start = Instant.parse("2026-10-12T10:00:00Z")
    private var ticks = 0L
    private val collections = CollectionRepository(book) { start.plusSeconds(ticks++) }

    @After
    fun close() = database.close()

    private fun own(title: String, cover: String? = null): RecipeId = runBlocking {
        val media = listOfNotNull(cover?.let { Media(it, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE) })
        Recipe.create(start, null).copy(title = title, media = media).also { recipes.save(it) }.id
    }

    private fun found(title: String, author: String): RecipeId = runBlocking {
        Recipe.create(start, null).copy(title = title, statedAuthor = author).also { assertTrue(recipes.addFound(it, start)) }.id
    }

    private fun create(name: String): CollectionId = runBlocking { collections.create(name)!! }

    private fun order(id: CollectionId): List<RecipeId> = runBlocking { collections.recipes(id).first().map { RecipeId(it.recipeId) } }

    private fun imported(name: String, author: String?, order: List<RecipeId>, id: CollectionId = CollectionId.mint()): CollectionId =
        runBlocking {
            assertEquals(ImportedCollection.CREATED, collections.putImported(id, name, author, order))
            id
        }

    @Test
    fun theCollectionsOfEachOriginAreListedTheLastMadeFirstWithTheirCounts() = runBlocking {
        val soup = own("Soup")
        val pie = found("Pie", "anna")
        val salads = create("Salads")
        val menu = create("  New Year's menu ")
        val annas = imported("Anna's pies", "anna", listOf(pie, soup))
        collections.addTo(soup, listOf(menu))
        collections.addTo(pie, listOf(menu))

        assertEquals(
            listOf(
                CollectionRow(menu.value, "New Year's menu", CollectionOrigin.OWN, null, start.plusSeconds(1), 2),
                CollectionRow(salads.value, "Salads", CollectionOrigin.OWN, null, start, 0),
            ),
            collections.listed(CollectionOrigin.OWN).first(),
        )
        assertEquals(
            listOf(CollectionRow(annas.value, "Anna's pies", CollectionOrigin.IMPORTED, "anna", start.plusSeconds(2), 2)),
            collections.listed(CollectionOrigin.IMPORTED).first(),
        )
        assertEquals(
            CollectionRow(menu.value, "New Year's menu", CollectionOrigin.OWN, null, start.plusSeconds(1), 2),
            collections.observe(menu).first(),
        )
        assertNull(collections.observe(CollectionId.mint()).first())
    }

    @Test
    fun aCollectionIsCreatedOwnAndEmptyUnderATrimmedNameAndABadNameMakesNone() = runBlocking {
        val id = collections.create("  Salads\n")!!
        assertEquals(RecipeCollection(id, "Salads", CollectionOrigin.OWN, null, start, emptyList()), collections.get(id))
        assertNull(collections.create("   "))
        assertNull(collections.create("x".repeat(RecipeCollection.MAX_NAME + 1)))
        assertEquals(listOf(id.value), collections.listed(CollectionOrigin.OWN).first().map { it.collectionId })
    }

    @Test
    fun anOwnCollectionIsRenamedAndAnImportedOneRefusesIt() = runBlocking {
        val salads = create("Salads")
        val annas = imported("Anna's pies", "anna", emptyList())

        assertEquals(CollectionChange.DONE, collections.rename(salads, "  Summer salads "))
        assertEquals("Summer salads", collections.get(salads)!!.name)
        assertEquals(CollectionChange.INVALID_NAME, collections.rename(salads, " "))
        assertEquals("Summer salads", collections.get(salads)!!.name)
        assertEquals(CollectionChange.FROZEN, collections.rename(annas, "Mine now"))
        assertEquals("Anna's pies", collections.get(annas)!!.name)
        assertEquals(CollectionChange.MISSING, collections.rename(CollectionId.mint(), "Nothing"))
    }

    @Test
    fun aRecipeIsAddedToSeveralCollectionsAtTheirEnds() = runBlocking {
        val soup = own("Soup")
        val pie = found("Pie", "anna")
        val menu = create("New Year's menu")
        val salads = create("Salads")
        val other = create("Other")
        val annas = imported("Anna's pies", "anna", listOf(pie))
        assertEquals(setOf(menu), collections.addTo(pie, listOf(menu)))

        // A found recipe goes in as an own one does; an imported collection, one it is in already, and one the book
        // does not hold take nothing.
        assertEquals(setOf(menu, salads), collections.addTo(soup, listOf(menu, salads, annas, CollectionId.mint())))
        assertEquals(setOf(salads), collections.addTo(pie, listOf(menu, salads)))

        assertEquals(listOf(pie, soup), order(menu))
        assertEquals(listOf(soup, pie), order(salads))
        assertEquals(listOf(pie), order(annas))
        assertEquals(emptyList<RecipeId>(), order(other))
        assertEquals(setOf(menu, salads, annas), collections.holding(pie).first())
        assertEquals(setOf(menu, salads), collections.holding(soup).first())
        // A recipe the book does not hold goes nowhere.
        assertEquals(emptySet<CollectionId>(), collections.addTo(RecipeId.mint(), listOf(other)))
        assertEquals(emptyList<RecipeId>(), order(other))
    }

    @Test
    fun aCollectionsRecipesAreListedInOrderWithWhatTheirEntriesShow() = runBlocking {
        val soup = own("Soup", cover = "m-soup")
        val pie = found("Pie", "anna")
        val menu = create("Menu")
        collections.addTo(soup, listOf(menu))
        collections.addTo(pie, listOf(menu))

        assertEquals(
            listOf(
                CollectionRecipeRow(soup.value, "Soup", coverId = "m-soup", statedAuthor = null, found = false),
                CollectionRecipeRow(pie.value, "Pie", coverId = null, statedAuthor = "anna", found = true),
            ),
            collections.recipes(menu).first(),
        )
        assertEquals(emptyList<CollectionRecipeRow>(), collections.recipes(CollectionId.mint()).first())
    }

    @Test
    fun aRecipeIsRemovedFromAnOwnCollectionAndStaysInTheBook() = runBlocking {
        val soup = own("Soup")
        val pie = own("Pie")
        val menu = create("Menu")
        val salads = create("Salads")
        val annas = imported("Anna's", null, listOf(soup))
        collections.addTo(soup, listOf(menu, salads))
        collections.addTo(pie, listOf(menu))

        assertEquals(CollectionChange.DONE, collections.remove(menu, soup))
        assertEquals(listOf(pie), order(menu))
        assertEquals(listOf(soup), order(salads))
        assertNotNull(recipes.get(soup))
        assertEquals(CollectionChange.MISSING, collections.remove(menu, soup))
        assertEquals(CollectionChange.MISSING, collections.remove(CollectionId.mint(), soup))
        assertEquals(CollectionChange.FROZEN, collections.remove(annas, soup))
        assertEquals(listOf(soup), order(annas))
    }

    @Test
    fun anOwnCollectionIsReorderedAndAnImportedOneRefusesIt() = runBlocking {
        val a = own("A")
        val b = own("B")
        val c = own("C")
        val menu = create("Menu")
        listOf(a, b, c).forEach { collections.addTo(it, listOf(menu)) }
        val annas = imported("Anna's", "anna", listOf(a, b, c))

        assertEquals(CollectionChange.DONE, collections.reorder(menu, listOf(c, a, b)))
        assertEquals(listOf(c, a, b), order(menu))
        assertEquals(listOf(c, a, b), collections.get(menu)!!.recipes)
        // A recipe it does not hold is not added; one left out keeps its place after the rest.
        assertEquals(CollectionChange.DONE, collections.reorder(menu, listOf(b, RecipeId.mint(), c)))
        assertEquals(listOf(b, c, a), order(menu))
        assertEquals(CollectionChange.FROZEN, collections.reorder(annas, listOf(c, b, a)))
        assertEquals(listOf(a, b, c), order(annas))
        assertEquals(CollectionChange.MISSING, collections.reorder(CollectionId.mint(), listOf(a)))
    }

    @Test
    fun deletingACollectionOwnOrImportedLeavesItsRecipesInTheBook() = runBlocking {
        val soup = own("Soup")
        val pie = found("Pie", "anna")
        val menu = create("Menu")
        val salads = create("Salads")
        val annas = imported("Anna's", "anna", listOf(pie, soup))
        collections.addTo(soup, listOf(menu, salads))
        collections.addTo(pie, listOf(menu))

        assertTrue(collections.delete(menu))
        assertTrue(collections.delete(annas))
        assertFalse(collections.delete(menu))

        assertNull(collections.get(menu))
        assertNull(collections.get(annas))
        assertEquals(listOf(salads.value), collections.listed(CollectionOrigin.OWN).first().map { it.collectionId })
        assertEquals(emptyList<CollectionRow>(), collections.listed(CollectionOrigin.IMPORTED).first())
        assertEquals(listOf(soup), order(salads))
        assertEquals(setOf(salads), collections.holding(soup).first())
        assertEquals(emptySet<CollectionId>(), collections.holding(pie).first())
        assertNotNull(recipes.get(soup))
        assertNotNull(recipes.get(pie))
        assertTrue(recipes.isMarked(pie))
        assertEquals(2, recipes.count())
        assertEquals(0, rows("select count(*) from collection_recipe where collection_id in (?, ?)", menu.value, annas.value))
    }

    @Test
    fun aRecipeDeletedFromTheBookLeavesEveryCollection() = runBlocking {
        val soup = own("Soup")
        val pie = own("Pie")
        val menu = create("Menu")
        val salads = create("Salads")
        val annas = imported("Anna's", null, listOf(soup, pie))
        collections.addTo(soup, listOf(menu, salads))
        collections.addTo(pie, listOf(menu))

        assertTrue(recipes.delete(soup))

        assertEquals(listOf(pie), order(menu))
        assertEquals(emptyList<RecipeId>(), order(salads))
        assertEquals(listOf(pie), order(annas))
        // Salads, the last made, first.
        assertEquals(listOf(0, 1), collections.listed(CollectionOrigin.OWN).first().map { it.count })
        assertEquals(0, rows("select count(*) from collection_recipe where recipe_id = ?", soup.value))
    }

    // The book's delete takes the recipe out of its collections itself, whatever the connection's foreign-key setting.
    @Test
    fun aRecipeDeletedWithForeignKeysOffLeavesEveryCollectionToo() = runBlocking {
        val soup = own("Soup")
        val menu = create("Menu")
        collections.addTo(soup, listOf(menu))
        database.openHelper.writableDatabase.execSQL("pragma foreign_keys = off")
        assertEquals(0, rows("pragma foreign_keys"))

        assertTrue(recipes.delete(soup))

        assertEquals(0, rows("select count(*) from collection_recipe where recipe_id = ?", soup.value))
    }

    // The schema itself takes a deleted recipe's places, and a deleted collection's, with them.
    @Test
    fun theBooksTablesCascadeFromARecipeAndFromACollection() = runBlocking {
        val soup = own("Soup")
        val pie = own("Pie")
        val menu = create("Menu")
        val salads = create("Salads")
        collections.addTo(soup, listOf(menu, salads))
        collections.addTo(pie, listOf(menu))
        val db = database.openHelper.writableDatabase

        db.execSQL("delete from recipe where recipe_id = ?", arrayOf(soup.value))
        assertEquals(listOf(pie), order(menu))
        assertEquals(emptyList<RecipeId>(), order(salads))
        db.execSQL("delete from collection where collection_id = ?", arrayOf(menu.value))
        assertEquals(0, rows("select count(*) from collection_recipe where collection_id = ?", menu.value))
        assertNotNull(recipes.get(pie))
    }

    @Test
    fun aRecipeTakenOutAndPutBackReturnsToItsPlacesInTheCollectionsStillHeld() = runBlocking {
        val a = own("A")
        val soup = found("Soup", "anna")
        val b = own("B")
        val menu = create("Menu")
        val salads = create("Salads")
        val gone = create("Gone")
        listOf(a, soup, b).forEach { collections.addTo(it, listOf(menu)) }
        collections.addTo(soup, listOf(salads, gone))

        val taken = recipes.take(soup)!!
        assertEquals(listOf(a, b), order(menu))
        assertEquals(emptySet<CollectionId>(), collections.holding(soup).first())
        assertTrue(collections.delete(gone))

        assertTrue(taken.putBack())
        assertEquals(listOf(a, soup, b), order(menu))
        assertEquals(listOf(soup), order(salads))
        assertEquals(setOf(menu, salads), collections.holding(soup).first())
    }

    @Test
    fun aFoundRecipeRemovedFromTheBookLeavesEveryCollection() = runBlocking {
        val pie = found("Pie", "anna")
        val menu = create("Menu")
        collections.addTo(pie, listOf(menu))

        assertTrue(recipes.removeMarked(pie))

        assertEquals(emptyList<RecipeId>(), order(menu))
        assertNotNull(collections.get(menu))
    }

    @Test
    fun anImportedCollectionIsCreatedWithTheRecipesTheBookHoldsInTheFilesOrder() = runBlocking {
        val soup = own("Soup")
        val pie = found("Pie", "anna")
        val id = CollectionId.mint()

        val result = collections.putImported(id, " Anna's pies ", " anna ", listOf(pie, RecipeId.mint(), soup, pie))

        assertEquals(ImportedCollection.CREATED, result)
        assertEquals(RecipeCollection(id, "Anna's pies", CollectionOrigin.IMPORTED, "anna", start, listOf(pie, soup)), collections.get(id))
        assertEquals(ImportedCollection.INVALID_NAME, collections.putImported(CollectionId.mint(), "  ", null, listOf(soup)))
        assertEquals(1, collections.listed(CollectionOrigin.IMPORTED).first().size)
        // A blank stated author is none.
        val unsigned = CollectionId.mint()
        collections.putImported(unsigned, "Unsigned", "  ", emptyList())
        assertNull(collections.get(unsigned)!!.statedAuthor)
    }

    @Test
    fun anImportedCollectionHeldTakesTheFilesNameAndOrderAndGainsWhatItLacks() = runBlocking {
        val a = own("A")
        val b = own("B")
        val c = own("C")
        val d = own("D")
        val id = imported("Anna's pies", "anna", listOf(a, b))

        val result = collections.putImported(id, "Anna's best pies", "anna k", listOf(c, b, d))

        assertEquals(ImportedCollection.UPDATED, result)
        // The file's order, then what the book's copy held that the file leaves out.
        assertEquals(
            RecipeCollection(id, "Anna's best pies", CollectionOrigin.IMPORTED, "anna k", start, listOf(c, b, d, a)),
            collections.get(id),
        )
    }

    @Test
    fun anOwnCollectionOfTheSameIdentityIsLeftAsItIs() = runBlocking {
        val a = own("A")
        val b = own("B")
        val menu = create("Menu")
        collections.addTo(a, listOf(menu))
        val before = collections.get(menu)

        assertEquals(ImportedCollection.OWN_HELD, collections.putImported(menu, "Menu from a file", "anna", listOf(b, a)))

        assertEquals(before, collections.get(menu))
    }

    /** The one number [sql] reads from the book. */
    private fun rows(sql: String, vararg args: String): Int =
        database.openHelper.writableDatabase.query(sql, args).use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun aDeletedCollectionIsPutBackWholeWithTheRecipesTheBookStillHolds() = runBlocking {
        val soup = own("Soup")
        val pie = found("Pie", "anna")
        val bread = own("Bread")
        val menu = create("New Year's menu")
        listOf(pie, soup, bread).forEach { collections.addTo(it, listOf(menu)) }
        val annas = imported("Anna's pies", "anna", listOf(pie, soup))
        val before = collections.get(menu)!!
        val annasBefore = collections.get(annas)!!

        assertTrue(collections.delete(menu))
        assertTrue(collections.delete(annas))
        // A recipe deleted from the book meanwhile does not come back with it.
        recipes.delete(bread)

        assertTrue(collections.restore(before))
        assertTrue(collections.restore(annasBefore))
        assertEquals(before.copy(recipes = listOf(pie, soup)), collections.get(menu))
        assertEquals(annasBefore, collections.get(annas))
        assertEquals(listOf(menu.value), collections.listed(CollectionOrigin.OWN).first().map { it.collectionId })
        // A collection the book holds is not put back over.
        assertFalse(collections.restore(before.copy(name = "Other")))
        assertEquals("New Year's menu", collections.get(menu)!!.name)
    }

    @Test
    fun aRemovedRecipeIsPutBackAtItsPlace() = runBlocking {
        val a = own("A")
        val b = own("B")
        val c = own("C")
        val menu = create("Menu")
        listOf(a, b, c).forEach { collections.addTo(it, listOf(menu)) }
        val annas = imported("Anna's", "anna", listOf(a))

        assertEquals(CollectionChange.DONE, collections.remove(menu, b))
        assertEquals(CollectionChange.DONE, collections.putBack(menu, b, 1))
        assertEquals(listOf(a, b, c), order(menu))
        // Past the end is the end; one it holds, or one the book does not, changes nothing.
        assertEquals(CollectionChange.DONE, collections.remove(menu, a))
        assertEquals(CollectionChange.DONE, collections.putBack(menu, a, 9))
        assertEquals(listOf(b, c, a), order(menu))
        assertEquals(CollectionChange.MISSING, collections.putBack(menu, a, 0))
        assertEquals(CollectionChange.MISSING, collections.putBack(menu, RecipeId.mint(), 0))
        assertEquals(listOf(b, c, a), order(menu))
        assertEquals(CollectionChange.FROZEN, collections.putBack(annas, b, 0))
        assertEquals(listOf(a), order(annas))
    }
}
