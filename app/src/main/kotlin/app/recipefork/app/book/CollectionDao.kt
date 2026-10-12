package app.recipefork.app.book

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.recipefork.core.recipe.CollectionOrigin
import kotlinx.coroutines.flow.Flow

/** What a collection row reads as, with its count of recipes. */
private const val COLLECTION_ROW =
    "select c.collection_id as collection_id, c.name as name, c.origin as origin, c.stated_author as stated_author, " +
        "c.created_at as created_at, " +
        "(select count(*) from collection_recipe m where m.collection_id = c.collection_id) as count from collection c"

/**
 * The book's collections and the places of its recipes in them (v0.36). The rules of what may change — an imported
 * collection is frozen — are [app.recipefork.app.collections.CollectionRepository]'s; this reads and writes rows.
 */
@Dao
abstract class CollectionDao {
    /** The collections of [origin], the last made first. */
    @Query("$COLLECTION_ROW where c.origin = :origin order by c.created_at desc, c.collection_id")
    abstract fun listed(origin: CollectionOrigin): Flow<List<CollectionRow>>

    /** The collection [id] as it is now and after every change; null while the book holds none such. */
    @Query("$COLLECTION_ROW where c.collection_id = :id")
    abstract fun observe(id: String): Flow<CollectionRow?>

    /** The recipes of the collection [id], in its order. */
    @Query(
        "select r.recipe_id as recipe_id, r.title as title, $COVER_OF_R as cover_id, r.stated_author as stated_author, " +
            "exists(select 1 from favourite f where f.recipe_id = r.recipe_id) as found " +
            "from collection_recipe m join recipe r on r.recipe_id = m.recipe_id " +
            "where m.collection_id = :id order by m.position, m.recipe_id",
    )
    abstract fun recipes(id: String): Flow<List<CollectionRecipeRow>>

    /** The identities of the collections the recipe [recipeId] is in, as they change. */
    @Query("select collection_id from collection_recipe where recipe_id = :recipeId order by collection_id")
    abstract fun holding(recipeId: String): Flow<List<String>>

    @Query("select * from collection where collection_id = :id")
    abstract suspend fun get(id: String): CollectionEntity?

    /** The recipes of the collection [id], in its order. */
    @Query("select recipe_id from collection_recipe where collection_id = :id order by position, recipe_id")
    abstract suspend fun members(id: String): List<String>

    /** Every collection of the book. */
    @Query("select * from collection order by collection_id")
    abstract suspend fun all(): List<CollectionEntity>

    /** Every place of a recipe in a collection, each collection's in its order. */
    @Query("select * from collection_recipe order by collection_id, position, recipe_id")
    abstract suspend fun allMembers(): List<CollectionRecipeEntity>

    /** Of the recipe identities [ids], those the book holds a recipe of. */
    @Query("select recipe_id from recipe where recipe_id in (:ids)")
    abstract suspend fun heldRecipes(ids: List<String>): List<String>

    @Insert
    abstract suspend fun insert(collection: CollectionEntity)

    @Query("update collection set name = :name where collection_id = :id")
    abstract suspend fun rename(id: String, name: String)

    @Query("update collection set name = :name, stated_author = :statedAuthor where collection_id = :id")
    abstract suspend fun restate(id: String, name: String, statedAuthor: String?)

    /** Deletes the collection [id] and its places, never a recipe; returns whether there was one. */
    @Transaction
    open suspend fun delete(id: String): Boolean {
        deleteMembers(id)
        return deleteRow(id) > 0
    }

    @Query("delete from collection where collection_id = :id")
    protected abstract suspend fun deleteRow(id: String): Int

    @Query("delete from collection_recipe where collection_id = :id")
    abstract suspend fun deleteMembers(id: String)

    @Query("delete from collection_recipe where collection_id = :id and recipe_id = :recipeId")
    abstract suspend fun removeMember(id: String, recipeId: String): Int

    @Insert
    abstract suspend fun insertMembers(rows: List<CollectionRecipeEntity>)

    /** Gives the collection [id] exactly the recipes [order], in that order. */
    @Transaction
    open suspend fun setMembers(id: String, order: List<String>) {
        deleteMembers(id)
        insertMembers(order.mapIndexed { position, recipe -> CollectionRecipeEntity(id, recipe, position) })
    }

    /** Deletes every collection and every place in one. */
    @Transaction
    open suspend fun clear() {
        clearMembers()
        clearCollections()
    }

    @Query("delete from collection_recipe")
    protected abstract suspend fun clearMembers()

    @Query("delete from collection")
    protected abstract suspend fun clearCollections()
}
