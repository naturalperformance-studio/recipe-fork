package app.recipefork.app.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The files the phone's own camera application writes its shots into (FLOW-026): one new file per shot in a cache
 * directory of their own, shared with the camera application through [CaptureProvider] for that shot alone, and
 * deleted once the shot has been cropped, cancelled or retaken. What a process ended mid-way leaves is cleared at the
 * application's next start, once a day old.
 */
internal object Captures {
    /** The directory the shots are written into; [CaptureProvider] shares nothing else. */
    fun directory(context: Context) = File(context.cacheDir, DIRECTORY)

    /** A new, empty file for one shot. Throws [IOException] where none can be made. */
    fun create(context: Context): File {
        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("no directory for the shot")
        return File.createTempFile("shot-", ".jpg", directory)
    }

    /** The content address of [file] the camera application writes the shot through. */
    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    fun authority(context: Context) = context.packageName + ".capture"

    /**
     * Deletes the shots older than [age] — left by a process ended between the shot and its crop — and whatever the
     * camera of earlier versions left behind. A shot younger than that may still be on its way back from the camera.
     */
    fun clearLeftovers(context: Context, now: Long = System.currentTimeMillis(), age: Duration = LEFTOVER_AGE) {
        legacyDirectory(context).deleteRecursively()
        val cutoff = now - age.inWholeMilliseconds
        directory(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    /** Where the system camera of earlier versions wrote its pictures. */
    fun legacyDirectory(context: Context) = File(context.cacheDir, "camera")

    val LEFTOVER_AGE = 1.days
    private const val DIRECTORY = "capture"
}

/** Shares the shot files of [Captures] with the camera application, and nothing else (res/xml/capture_paths.xml). */
class CaptureProvider : FileProvider()
