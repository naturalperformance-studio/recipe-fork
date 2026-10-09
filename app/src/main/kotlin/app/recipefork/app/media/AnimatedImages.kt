package app.recipefork.app.media

import android.content.Context
import android.net.Uri
import kotlin.time.Duration

/**
 * Reads an image offered to a step as an animation (scenario 3): an animated GIF or WebP, which is attached as a
 * silent clip; anything else — a still GIF or WebP, a PNG, a JPEG — stays a photograph.
 */
fun interface AnimatedImages {
    /** The animation [source] holds; null when it is no animated GIF or WebP, or cannot be read. */
    fun read(source: Uri): AnimatedImage?

    /** Whether [source] is an animated GIF or WebP. */
    fun isAnimated(source: Uri): Boolean = read(source) != null
}

/** Reads animations offered by another app — a picked image — through [context]'s resolver. */
class ResolverAnimatedImages(context: Context) : AnimatedImages {
    private val context = context.applicationContext

    override fun read(source: Uri): AnimatedImage? = try {
        context.contentResolver.openInputStream(source)?.use { AnimatedImage.parse(it.readBytes()) }
    } catch (e: Exception) {
        // A source that cannot be opened holds no animation to read.
        null
    }
}

/** How long an animation offered to a step lasts, once through: the sum of its frames' delays (MEDIA-007). */
class AnimationDuration(private val images: AnimatedImages) : ClipSourceDuration {
    override fun of(source: Uri): Duration? = images.read(source)?.duration
}
