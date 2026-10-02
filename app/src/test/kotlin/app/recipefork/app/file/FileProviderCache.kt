package app.recipefork.app.file

import androidx.core.content.FileProvider

/**
 * FileProvider keeps the directories it shares in a static cache, read once per process. Robolectric gives each test
 * a data directory of its own in one process, so a test that shares a file forgets the previous test's directories.
 */
object FileProviderCache {
    fun clear() {
        val field = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        val cache = field.get(null) as HashMap<*, *>
        synchronized(cache) { cache.clear() }
    }
}
