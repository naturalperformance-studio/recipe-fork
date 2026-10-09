package app.recipefork.app.media

import android.content.Context
import android.net.Uri
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.time.Duration

/**
 * Reads an image offered to a step as an animation (scenario 3): an animated GIF or WebP, which is attached as a
 * silent clip; anything else — a still GIF or WebP, a PNG, a JPEG — stays a photograph.
 */
fun interface AnimatedImages {
    /** The animation [source] holds; null when it is no animated GIF or WebP, or cannot be read ([AnimatedImage.read]). */
    fun read(source: Uri): AnimatedImage?

    /** Whether [source] is an animated GIF or WebP; a JPEG or a PNG is told still from its first bytes. */
    fun isAnimated(source: Uri): Boolean = read(source) != null
}

/** Reads animations offered by another app — a picked image — through [context]'s resolver. */
class ResolverAnimatedImages(context: Context) : AnimatedImages {
    private val context = context.applicationContext

    override fun read(source: Uri): AnimatedImage? = try {
        context.contentResolver.openInputStream(source)?.use { AnimatedImage.read(it) }
    } catch (e: Exception) {
        // A source that cannot be opened holds no animation to read.
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

/**
 * A step's animations as clips, for a [ClipStore]: how long one lasts (MEDIA-007) and its conversion by [encode]
 * (MEDIA-004). The animation read for its duration is the one converted, so a file is read and parsed once per clip;
 * one that may not be attached — too long, or none — is not held.
 */
class AnimationClips(
    private val images: AnimatedImages,
    private val encode: suspend (AnimatedImage, File) -> Unit = AnimationEncoder::encode,
) : ClipSourceDuration, ClipConverter {
    private var held: Pair<Uri, AnimatedImage>? = null

    override fun of(source: Uri): Duration? {
        val animation = images.read(source)
        val duration = animation?.duration
        synchronized(this) { held = if (animation != null && VideoLimits.accepts(duration)) source to animation else null }
        return duration
    }

    override suspend fun convert(source: Uri, target: File) {
        val animation = synchronized(this) { held?.takeIf { it.first == source }?.second.also { held = null } }
            ?: images.read(source)
            ?: throw ClipConversionFailed("The image is no animation")
        try {
            encode(animation, target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClipConversionFailed) {
            throw e
        } catch (e: Exception) {
            throw ClipConversionFailed("The animation could not be encoded", e)
        } catch (e: OutOfMemoryError) {
            // An animation too large for the memory left is one this device cannot convert, never a crash.
            throw ClipConversionFailed("The animation is too large to encode", e)
        }
    }
}
