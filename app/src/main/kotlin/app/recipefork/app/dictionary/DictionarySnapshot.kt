package app.recipefork.app.dictionary

import android.content.Context
import app.recipefork.core.api.DictionariesDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Names the snapshot this installation carries without reading it: the application's version code and the moment
     * the package was last installed or updated. The asset changes only with the package, so a new key is the only
     * way a new snapshot can arrive.
     */
    fun installedKey(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${info.longVersionCode}:${info.lastUpdateTime}"
    }
}

/** Which snapshot version, if any, the held copy was last seeded from. */
interface SeededVersion {
    fun get(): String?
    fun set(version: String)
}

/** [SeededVersion] kept in the device's preferences, beside the device database, under [key]. */
class PreferencesSeededVersion(context: Context, private val key: String = SEEDED) : SeededVersion {
    private val preferences = context.getSharedPreferences("dictionary-snapshot", Context.MODE_PRIVATE)

    override fun get(): String? = preferences.getString(key, null)

    override fun set(version: String) {
        preferences.edit().putString(key, version).commit()
    }

    companion object {
        /** The snapshot version the held copy was last seeded from. */
        const val SEEDED = "seeded-version"

        /** The [DictionarySeeding] key of the installation whose snapshot was last checked against the held copy. */
        const val CHECKED = "checked-installation"
    }
}

/** A [SeededVersion] held for the life of the process alone. */
class HeldSeededVersion : SeededVersion {
    @Volatile private var version: String? = null
    override fun get(): String? = version
    override fun set(version: String) {
        this.version = version
    }
}

/**
 * Seeds the held dictionaries from the shipped snapshot, so that a device that has never reached the server
 * offers them (FLOW-001, FLOW-002). It seeds when the device holds no dictionaries, or when the held copy came
 * from an earlier snapshot and this one has another version; a copy fetched from the server is replaced only
 * by the server. Seeding goes through the same replacement as a refresh from the server ([DictionaryDao.replace]).
 *
 * The snapshot is read — the whole asset parsed — only when it may have something to say: once per [installed] key,
 * which names the snapshot without reading it ([DictionarySnapshot.installedKey]) and is remembered in [checked]
 * outside the database. A key already checked is trusted only while the device still holds a copy, so a database
 * rebuilt empty (a destructive migration) is seeded again. With no key the snapshot is read on every call.
 */
class DictionarySeeding(
    private val dao: DictionaryDao,
    private val seeded: SeededVersion,
    private val installed: String? = null,
    private val checked: SeededVersion = HeldSeededVersion(),
    private val snapshot: () -> DictionariesDto,
) {
    // Readers that ask at once (the editor's tags and ingredients) wait for one check rather than each parsing.
    private val lock = Mutex()

    /** True when the held copy was replaced by the snapshot. */
    suspend fun seedIfNeeded(): Boolean = lock.withLock {
        if (installed != null && checked.get() == installed && dao.version() != null) return@withLock false
        val shipped = snapshot()
        val replaced = dao.seed(shipped, seeded.get())
        if (replaced) seeded.set(shipped.version)
        installed?.let(checked::set)
        replaced
    }
}
