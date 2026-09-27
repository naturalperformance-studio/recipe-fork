package app.recipefork.app.dictionary

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An ingredient dictionary entry as the device holds it (FLOW-001, DOM-010). */
@Entity(tableName = "ingredient_entry")
data class IngredientEntryEntity(
    @PrimaryKey val id: Long,
    val labelRu: String,
    val labelEn: String,
    val parentId: Long?,
)

/** A tag as the device holds it (FLOW-001). */
@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey val id: Long,
    val labelRu: String,
    val labelEn: String,
)

/** The server's version of the held copy; a single row, absent until the first refresh. */
@Entity(tableName = "dictionary_version")
data class DictionaryVersionEntity(
    @PrimaryKey val id: Int = 0,
    val version: String,
)
