package app.recipefork.app.dictionary

import android.content.Context
import app.recipefork.core.api.DictionariesDto
import kotlinx.serialization.json.Json

/**
 * The dictionaries shipped inside the application (decisions/20260928-dictionaries-ship-with-the-application.md):
 * `assets/dictionaries.json`, written by `scripts/dictionary-snapshot.sh` from the production server.
 */
object DictionarySnapshot {
    const val ASSET = "dictionaries.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): DictionariesDto = json.decodeFromString(DictionariesDto.serializer(), text)

    /** The snapshot this application carries. */
    fun read(context: Context): DictionariesDto =
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
}

/** Which snapshot version, if any, the held copy was last seeded from. */
interface SeededVersion {
    fun get(): String?
    fun set(version: String)
}

/** [SeededVersion] kept in the device's preferences, beside the device database. */
class PreferencesSeededVersion(context: Context) : SeededVersion {
    private val preferences = context.getSharedPreferences("dictionary-snapshot", Context.MODE_PRIVATE)

    override fun get(): String? = preferences.getString(KEY, null)

    override fun set(version: String) {
        preferences.edit().putString(KEY, version).commit()
    }

    private companion object {
        const val KEY = "seeded-version"
    }
}

/**
 * Seeds the held dictionaries from the shipped snapshot, so that a device that has never reached the server
 * offers them (FLOW-001, FLOW-002). It seeds when the device holds no dictionaries, or when the held copy came
 * from an earlier snapshot and this one has another version; a copy fetched from the server is replaced only
 * by the server. Seeding goes through the same replacement as a refresh from the server ([DictionaryDao.replace]).
 */
class DictionarySeeding(
    private val dao: DictionaryDao,
    private val seeded: SeededVersion,
    private val snapshot: () -> DictionariesDto,
) {
    /** True when the held copy was replaced by the snapshot. */
    suspend fun seedIfNeeded(): Boolean {
        val shipped = snapshot()
        val replaced = dao.seed(shipped, seeded.get())
        if (replaced) seeded.set(shipped.version)
        return replaced
    }
}
