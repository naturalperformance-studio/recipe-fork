package app.recipefork.app.dictionary

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import app.recipefork.core.api.EquipmentEntryDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.NutritionDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** What a dictionary search reads of an entry, whichever dictionary holds it ([app.recipefork.core.search.DictionaryEntry]). */
typealias DictionaryEntry = app.recipefork.core.search.DictionaryEntry

/**
 * An ingredient dictionary entry as the device holds it (FLOW-001, DOM-010), with its divisibility, its comparison
 * factors (DOM-011, CAT-009 — never shown) and whether it is [removed] upstream: a removed entry is held so that the
 * recipes naming it keep its name (DOM-009), and is offered to nobody. [synonymsRu] and [synonymsEn] are the other
 * words the entry may be found by in each language. [nutrition] is what 100 g of the raw product holds, null when the
 * dictionary does not say; [noNutrition] marks a child known to have no values of its own, which does not take its
 * parent's.
 */
@Entity(tableName = "ingredient_entry")
data class IngredientEntryEntity(
    @PrimaryKey override val id: Long,
    override val labelRu: String,
    override val labelEn: String,
    override val parentId: Long?,
    val divisible: Boolean? = null,
    val factors: Map<String, Double> = emptyMap(),
    override val removed: Boolean = false,
    override val synonymsRu: List<String> = emptyList(),
    override val synonymsEn: List<String> = emptyList(),
    val nutrition: NutritionDto? = null,
    val noNutrition: Boolean = false,
) : DictionaryEntry {
    fun toDto(): IngredientEntryDto =
        IngredientEntryDto(id, labelRu, labelEn, parentId, divisible, factors, removed, synonymsRu, synonymsEn, nutrition, noNutrition)

    companion object {
        fun of(dto: IngredientEntryDto): IngredientEntryEntity =
            IngredientEntryEntity(
                dto.id, dto.labelRu, dto.labelEn, dto.parentId, dto.divisible, dto.factors, dto.removed,
                dto.synonymsRu, dto.synonymsEn, dto.nutrition, dto.noNutrition,
            )
    }
}

/**
 * An equipment dictionary entry as the device holds it: a top-level entry is a group, its leaves the items. A
 * [removed] entry is held, as an ingredient entry is, and offered to nobody.
 */
@Entity(tableName = "equipment_entry")
data class EquipmentEntryEntity(
    @PrimaryKey override val id: Long,
    override val labelRu: String,
    override val labelEn: String,
    override val parentId: Long? = null,
    override val removed: Boolean = false,
    override val synonymsRu: List<String> = emptyList(),
    override val synonymsEn: List<String> = emptyList(),
) : DictionaryEntry {
    fun toDto(): EquipmentEntryDto = EquipmentEntryDto(id, labelRu, labelEn, parentId, removed, synonymsRu, synonymsEn)

    companion object {
        fun of(dto: EquipmentEntryDto): EquipmentEntryEntity =
            EquipmentEntryEntity(dto.id, dto.labelRu, dto.labelEn, dto.parentId, dto.removed, dto.synonymsRu, dto.synonymsEn)
    }
}

/**
 * How the device database holds an entry's factors, synonyms and nutrition: as the JSON objects and array the
 * dictionary carries them in.
 */
class FactorsConverter {
    private val serializer = MapSerializer(String.serializer(), Double.serializer())
    private val words = ListSerializer(String.serializer())

    @TypeConverter
    fun toText(factors: Map<String, Double>): String = Json.encodeToString(serializer, factors)

    @TypeConverter
    fun fromText(text: String): Map<String, Double> = Json.decodeFromString(serializer, text)

    @TypeConverter
    fun wordsToText(list: List<String>): String = Json.encodeToString(words, list)

    @TypeConverter
    fun wordsFromText(text: String): List<String> = Json.decodeFromString(words, text)

    @TypeConverter
    fun nutritionToText(nutrition: NutritionDto?): String? = nutrition?.let { Json.encodeToString(NutritionDto.serializer(), it) }

    @TypeConverter
    fun nutritionFromText(text: String?): NutritionDto? = text?.let { Json.decodeFromString(NutritionDto.serializer(), it) }
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
