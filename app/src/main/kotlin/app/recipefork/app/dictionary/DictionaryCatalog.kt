package app.recipefork.app.dictionary

/** The tag dictionary as the editor reads it: from the device alone, with or without a connection (FLOW-001). */
interface TagSource {
    suspend fun tags(): List<TagEntity>
}

/** The ingredient dictionary as the editor's ingredient field reads it, from the device alone (FLOW-057). */
fun interface IngredientSource {
    suspend fun ingredients(): List<IngredientEntryEntity>
}

/** The equipment dictionary, from the device alone: groups and their items, removed entries included. */
fun interface EquipmentSource {
    suspend fun equipment(): List<EquipmentEntryEntity>
}

/**
 * The held dictionaries, seeded from the shipped snapshot before they are first read, so that a clean
 * installation with no connection offers the same entries as one that has reached the server. The application holds
 * one ([app.recipefork.app.AppContainer.dictionaries]), which the editor and the card both read, so the shipped
 * snapshot is checked once for all of them.
 */
class DictionaryCatalog(
    private val dao: DictionaryDao,
    private val seeding: DictionarySeeding,
) : TagSource, IngredientSource, EquipmentSource {
    override suspend fun tags(): List<TagEntity> {
        seeding.seedIfNeeded()
        return dao.tags()
    }

    override suspend fun ingredients(): List<IngredientEntryEntity> {
        seeding.seedIfNeeded()
        return dao.ingredients()
    }

    override suspend fun equipment(): List<EquipmentEntryEntity> {
        seeding.seedIfNeeded()
        return dao.equipment()
    }
}
