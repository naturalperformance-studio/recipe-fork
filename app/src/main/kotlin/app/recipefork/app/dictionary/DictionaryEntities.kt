package app.recipefork.app.dictionary

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import app.recipefork.core.api.IngredientEntryDto
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * An ingredient dictionary entry as the device holds it (FLOW-001, DOM-010), with its divisibility, its comparison
 * factors (DOM-011, CAT-009 — never shown) and whether it is [removed] upstream: a removed entry is held so that the
 * recipes naming it keep its name (DOM-009), and is offered to nobody.
 */
@Entity(tableName = "ingredient_entry")
data class IngredientEntryEntity(
    @PrimaryKey val id: Long,
    val labelRu: String,
    val labelEn: String,
    val parentId: Long?,
    val divisible: Boolean? = null,
    val factors: Map<String, Double> = emptyMap(),
    val removed: Boolean = false,
) {
    fun toDto(): IngredientEntryDto = IngredientEntryDto(id, labelRu, labelEn, parentId, divisible, factors, removed)

    companion object {
        fun of(dto: IngredientEntryDto): IngredientEntryEntity =
            IngredientEntryEntity(dto.id, dto.labelRu, dto.labelEn, dto.parentId, dto.divisible, dto.factors, dto.removed)
    }
}

/** How the device database holds an entry's factors: as the JSON object the dictionary carries them in. */
class FactorsConverter {
    private val serializer = MapSerializer(String.serializer(), Double.serializer())

    @TypeConverter
    fun toText(factors: Map<String, Double>): String = Json.encodeToString(serializer, factors)

    @TypeConverter
    fun fromText(text: String): Map<String, Double> = Json.decodeFromString(serializer, text)
}

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
