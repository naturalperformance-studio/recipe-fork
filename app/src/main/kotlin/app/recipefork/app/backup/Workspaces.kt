package app.recipefork.app.backup

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Directories of the application's own that saving and opening book files work in: each save and each open takes one
 * of its own under a root, so that two at once never touch each other's files. Those under a root that nothing in this
 * process uses any more — left by a process since ended — are deleted as the next one is taken.
 */
internal object Workspaces {
    /** The directories taken and not yet released, in this process. */
    private val inUse = HashSet<File>()

    /** A new, empty directory under [root]; throws [IOException] when it cannot be made. */
    fun take(root: File): File = synchronized(inUse) {
        root.listFiles().orEmpty().filter { it !in inUse }.forEach { it.deleteRecursively() }
        File(root, UUID.randomUUID().toString()).also {
            if (!it.mkdirs()) throw IOException("cannot make $it")
            inUse += it
        }
    }

    /** Deletes [directory], taken by [take], with everything in it. */
    fun release(directory: File) {
        directory.deleteRecursively()
        synchronized(inUse) { inUse -= directory }
    }
}
