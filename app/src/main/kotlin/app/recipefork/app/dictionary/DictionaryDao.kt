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

    @Query("select * from equipment_entry order by id")
    abstract suspend fun equipment(): List<EquipmentEntryEntity>

    @Query("select version from dictionary_version where id = 0")
    abstract suspend fun version(): String?

    @Query("select version from dictionary_version where id = 0")
    abstract fun versionUpdates(): Flow<String?>

    /**
     * Replaces the whole held copy of the dictionaries with [dictionaries], atomically. An ingredient or equipment
     * entry removed upstream is held removed rather than dropped — whether [dictionaries] still carry it marked so, or
     * no longer carry it — so that the card, the editor and search still name every recipe's entry (DOM-009, FLOW-001).
     * [dictionaries] that carry no equipment at all — a server need not serve it, as it is maintained in the repository
     * — leave the held equipment as it is.
     */
    @Transaction
    open suspend fun replace(dictionaries: DictionariesDto) {
        val arrived = dictionaries.ingredients.map(IngredientEntryEntity::of)
        val arrivedIds = arrived.mapTo(HashSet()) { it.id }
        val gone = ingredients().filter { it.id !in arrivedIds }.map { it.copy(removed = true) }
        deleteIngredients()
        deleteTags()
        insertIngredients(arrived + gone)
        insertTags(dictionaries.tags.map { TagEntity(it.id, it.labelRu, it.labelEn) })
        if (dictionaries.equipment.isNotEmpty()) {
            val arrivedEquipment = dictionaries.equipment.map(EquipmentEntryEntity::of)
            val arrivedEquipmentIds = arrivedEquipment.mapTo(HashSet()) { it.id }
            val goneEquipment = equipment().filter { it.id !in arrivedEquipmentIds }.map { it.copy(removed = true) }
            deleteEquipment()
            insertEquipment(arrivedEquipment + goneEquipment)
        }
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

    @Query("delete from equipment_entry")
    protected abstract suspend fun deleteEquipment()

    @Insert
    protected abstract suspend fun insertIngredients(entries: List<IngredientEntryEntity>)

    @Insert
    protected abstract suspend fun insertTags(tags: List<TagEntity>)

    @Insert
    protected abstract suspend fun insertEquipment(entries: List<EquipmentEntryEntity>)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    protected abstract suspend fun setVersion(version: DictionaryVersionEntity)
}
