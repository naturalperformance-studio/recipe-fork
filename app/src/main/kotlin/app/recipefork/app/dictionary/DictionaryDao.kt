package app.recipefork.app.dictionary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.recipefork.core.api.DictionariesDto
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DictionaryDao {
    @Query("select * from ingredient_entry order by id")
    abstract suspend fun ingredients(): List<IngredientEntryEntity>

    @Query("select * from tag order by id")
    abstract suspend fun tags(): List<TagEntity>

    @Query("select version from dictionary_version where id = 0")
    abstract suspend fun version(): String?

    @Query("select version from dictionary_version where id = 0")
    abstract fun versionUpdates(): Flow<String?>

    /** Replaces the whole held copy of both dictionaries with [dictionaries], atomically. */
    @Transaction
    open suspend fun replace(dictionaries: DictionariesDto) {
        deleteIngredients()
        deleteTags()
        insertIngredients(dictionaries.ingredients.map { IngredientEntryEntity(it.id, it.labelRu, it.labelEn, it.parentId) })
        insertTags(dictionaries.tags.map { TagEntity(it.id, it.labelRu, it.labelEn) })
        setVersion(DictionaryVersionEntity(version = dictionaries.version))
    }

    /**
     * Replaces the held copy with the shipped [snapshot] when the device holds none, or when the held copy is the
     * earlier snapshot [seededVersion] and [snapshot] has another version; returns whether it did. The check and
     * the replacement are one transaction, so a refresh from the server is never overwritten by a snapshot.
     */
    @Transaction
    open suspend fun seed(snapshot: DictionariesDto, seededVersion: String?): Boolean {
        val held = version()
        val fromSnapshot = held != null && held == seededVersion
        if (held != null && !(fromSnapshot && snapshot.version != held)) return false
        replace(snapshot)
        return true
    }

    @Query("delete from ingredient_entry")
    protected abstract suspend fun deleteIngredients()

    @Query("delete from tag")
    protected abstract suspend fun deleteTags()

    @Insert
    protected abstract suspend fun insertIngredients(entries: List<IngredientEntryEntity>)

    @Insert
    protected abstract suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    protected abstract suspend fun setVersion(version: DictionaryVersionEntity)
}
