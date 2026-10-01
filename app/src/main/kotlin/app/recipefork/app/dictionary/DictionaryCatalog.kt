package app.recipefork.app.dictionary

/** The tag dictionary as the editor reads it: from the device alone, with or without a connection (FLOW-001). */
interface TagSource {
    suspend fun tags(): List<TagEntity>
}

/**
 * The held dictionaries, seeded from the shipped snapshot before they are first read, so that a clean
 * installation with no connection offers the same entries as one that has reached the server.
 */
class DictionaryCatalog(
    private val dao: DictionaryDao,
    private val seeding: DictionarySeeding,
) : TagSource {
    override suspend fun tags(): List<TagEntity> {
        seeding.seedIfNeeded()
        return dao.tags()
    }
}
