package app.recipefork.app.collections

import androidx.room.withTransaction
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.CollectionDao
import app.recipefork.app.book.CollectionEntity
import app.recipefork.app.book.CollectionRecipeRow
import app.recipefork.app.book.CollectionRow
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrder
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.RecipeCollection
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant

/** What a change the reader asked of a collection came to. */
enum class CollectionChange {
    DONE,

    /** Refused: the collection is an imported one, which is never renamed, reordered or changed in its recipes. */
    FROZEN,

    /** The book holds no such collection, or the collection no such recipe. */
    MISSING,

    /** Refused: the name is blank, or longer than [RecipeCollection.MAX_NAME]. */
    INVALID_NAME,
}

/** What putting a collection of a file into the book ([CollectionRepository.putImported]) came to. */
enum class ImportedCollection {
    /** The book held no collection of its identity; it holds it now. */
    CREATED,

    /** The book held it as an imported one; it has the file's name and order now, and every recipe of it the book holds. */
    UPDATED,

    /** The book holds a collection of its identity as its own — the reader's, shared and come back: it is left as it is. */
    OWN_HELD,

    /** Refused: the file's name is blank, or longer than [RecipeCollection.MAX_NAME]. */
    INVALID_NAME,
}

/**
 * The collections of the active book (v0.36), kept in its database: the reader's own, which hold any recipe of the
 * book, own or found, and imported ones, which are frozen. Each change is one transaction of the book's database, in
 * the book active when it was asked; a collection is never what deletes a recipe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionRepository(
    private val book: ActiveBook,
    private val clock: () -> Instant = Instant::now,
) {
    /** The collections of [origin] with their counts of recipes, the last made first, as they change. */
    fun listed(origin: CollectionOrigin): Flow<List<CollectionRow>> =
        book.open.flatMapLatest { it.database.collections().listed(origin) }

    /** The collection [id] with its count, as it changes; null while the book holds none such. */
    fun observe(id: CollectionId): Flow<CollectionRow?> =
        book.open.flatMapLatest { it.database.collections().observe(id.value) }

    /** The recipes of the collection [id] in its order, as they change; empty for a collection the book does not hold. */
    fun recipes(id: CollectionId): Flow<List<CollectionRecipeRow>> =
        book.open.flatMapLatest { it.database.collections().recipes(id.value) }

    /** The collections the recipe [recipe] is in, as they change. */
    fun holding(recipe: RecipeId): Flow<Set<CollectionId>> =
        book.open.flatMapLatest { it.database.collections().holding(recipe.value) }.map { ids -> ids.map(::CollectionId).toSet() }

    /** The collection [id] whole, its recipes in order; null when the book holds none such. */
    suspend fun get(id: CollectionId): RecipeCollection? = transaction { dao ->
        dao.get(id.value)?.let { it.toCollection(dao.members(id.value).map(::RecipeId)) }
    }

    /** Makes an own collection of no recipes named [name] (trimmed); null, making nothing, when the name is refused. */
    suspend fun create(name: String): CollectionId? {
        val named = RecipeCollection.nameOf(name) ?: return null
        val id = CollectionId.mint()
        transaction { it.insert(CollectionEntity(id.value, named, CollectionOrigin.OWN, null, clock())) }
        return id
    }

    /** Renames the own collection [id] to [name] (trimmed). */
    suspend fun rename(id: CollectionId, name: String): CollectionChange {
        val named = RecipeCollection.nameOf(name) ?: return CollectionChange.INVALID_NAME
        return editing(id) { dao -> dao.rename(id.value, named); CollectionChange.DONE }
    }

    /** Deletes the collection [id], own or imported; every recipe it held stays in the book. Returns whether there was one. */
    suspend fun delete(id: CollectionId): Boolean = transaction { it.delete(id.value) }

    /**
     * Puts the recipe [recipe] of the book at the end of each own collection of [collections] that does not hold it
     * yet. Returns the collections it was added to: none for a recipe the book does not hold, and none of those
     * imported or not held.
     */
    suspend fun addTo(recipe: RecipeId, collections: Collection<CollectionId>): Set<CollectionId> = transaction { dao ->
        if (dao.heldRecipes(listOf(recipe.value)).isEmpty()) return@transaction emptySet()
        collections.distinct().filter { id ->
            val collection = dao.get(id.value)
            if (collection == null || !collection.origin.editable) return@filter false
            val members = dao.members(id.value)
            if (recipe.value in members) return@filter false
            dao.setMembers(id.value, members + recipe.value)
            true
        }.toSet()
    }

    /** Takes the recipe [recipe] out of the own collection [id]; the recipe stays in the book. */
    suspend fun remove(id: CollectionId, recipe: RecipeId): CollectionChange = editing(id) { dao ->
        if (dao.removeMember(id.value, recipe.value) > 0) CollectionChange.DONE else CollectionChange.MISSING
    }

    /**
     * Orders the own collection [id] as [order] has it ([CollectionOrder.reordered]): a recipe it does not hold is not
     * added, and one [order] leaves out keeps its order after the rest.
     */
    suspend fun reorder(id: CollectionId, order: List<RecipeId>): CollectionChange = editing(id) { dao ->
        val current = dao.members(id.value).map(::RecipeId)
        dao.setMembers(id.value, CollectionOrder.reordered(current, order).map { it.value })
        CollectionChange.DONE
    }

    /**
     * Puts the collection a file carries into the book, for its import: [id], named [name] (trimmed), the author's
     * nickname the file states ([statedAuthor], blank for none) and its recipes in the file's [order]; of those, only
     * the ones the book holds — the import has written them first — go in. A collection the book does not hold is made
     * an imported one; an imported one it holds takes the file's name, stated author and order
     * ([CollectionOrder.imported]) and gains what it lacks; an own one of that identity is left as it is.
     */
    suspend fun putImported(id: CollectionId, name: String, statedAuthor: String?, order: List<RecipeId>): ImportedCollection {
        val named = RecipeCollection.nameOf(name) ?: return ImportedCollection.INVALID_NAME
        val author = statedAuthor?.trim()?.takeIf { it.isNotEmpty() }
        return transaction { dao ->
            val held = order.map { it.value }.distinct().chunked(MAX_ARGUMENTS).flatMap { dao.heldRecipes(it) }.toSet()
            val file = order.filter { it.value in held }
            val existing = dao.get(id.value)
            when {
                existing == null -> {
                    dao.insert(CollectionEntity(id.value, named, CollectionOrigin.IMPORTED, author, clock()))
                    dao.setMembers(id.value, file.distinct().map { it.value })
                    ImportedCollection.CREATED
                }
                existing.origin == CollectionOrigin.OWN -> ImportedCollection.OWN_HELD
                else -> {
                    dao.restate(id.value, named, author)
                    val current = dao.members(id.value).map(::RecipeId)
                    dao.setMembers(id.value, CollectionOrder.imported(current, file).map { it.value })
                    ImportedCollection.UPDATED
                }
            }
        }
    }

    /** Runs [change] on the collection [id] when it is an own one, in one transaction. */
    private suspend fun editing(
        id: CollectionId,
        change: suspend (CollectionDao) -> CollectionChange,
    ): CollectionChange = transaction { dao ->
        val collection = dao.get(id.value)
        when {
            collection == null -> CollectionChange.MISSING
            !collection.origin.editable -> CollectionChange.FROZEN
            else -> change(dao)
        }
    }

    private suspend fun <T> transaction(work: suspend (CollectionDao) -> T): T {
        val database: BookDatabase = book.open.value.database
        return database.withTransaction { work(database.collections()) }
    }
}

/** The collection this row is, with its [recipes] in order. */
private fun CollectionEntity.toCollection(recipes: List<RecipeId>) =
    RecipeCollection(CollectionId(collectionId), name, origin, statedAuthor, createdAt, recipes)

/** How many identities one query is asked about at most: SQLite's own limit on its arguments is 999. */
private const val MAX_ARGUMENTS = 500
