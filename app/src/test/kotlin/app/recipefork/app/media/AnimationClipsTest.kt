package app.recipefork.app.media

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * A step's animations kept as clips (scenario 3): each file read and parsed once per clip, one too long refused before
 * any encoding (MEDIA-007), and an encoder that fails — out of memory included — a refusal, never a crash (MEDIA-008).
 */
@RunWith(AndroidJUnit4::class)
class AnimationClipsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.Pool
    private val source = Uri.parse("content://media/external/images/media/9")

    private fun fixture(name: String): AnimatedImage =
        AnimatedImage.parse(checkNotNull(javaClass.getResourceAsStream("/animation/$name")).use { it.readBytes() })!!

    /** Hands out [animation] for any source, counting the reads. */
    private class Images(private val animation: AnimatedImage?) : AnimatedImages {
        var reads = 0

        override fun read(source: Uri): AnimatedImage? {
            reads++
            return animation
        }
    }

    private fun left(): List<String> = PhotoStore(context.filesDir).directory(book).list()?.toList().orEmpty()

    @Test
    fun anAnimationIsReadOnceAndTheOneReadIsEncoded() = runBlocking {
        val gif = fixture("animated.gif")
        val images = Images(gif)
        val encoded = mutableListOf<AnimatedImage>()
        val clips = AnimationClips(images) { animation, target ->
            encoded += animation
            target.writeBytes(ByteArray(100))
        }

        val outcome = ClipStore(context.filesDir, clips, clips).add(book, source)

        assertTrue(outcome is ClipOutcome.Stored)
        assertEquals(1, images.reads)
        assertEquals(listOf(gif), encoded)
    }

    @Test
    fun anAnimationTooLongIsRefusedAndNotHeld() = runBlocking {
        val images = Images(fixture("long.gif"))
        var encodings = 0
        val clips = AnimationClips(images) { _, _ -> encodings++ }

        assertEquals(ClipOutcome.Refused(ClipRefusal.TOO_LONG), ClipStore(context.filesDir, clips, clips).add(book, source))
        assertEquals(0, encodings)
        // Not held: converted anyway, it is read again rather than taken from the refused one.
        runCatching { clips.convert(source, File(context.cacheDir, "long.mp4")) }
        assertEquals(2, images.reads)
    }

    @Test
    fun anEncoderOutOfMemoryIsAnUnreadableAnimation() = runBlocking {
        listOf(OutOfMemoryError("hostile"), IllegalStateException("codec")).forEach { failure ->
            val clips = AnimationClips(Images(fixture("animated.gif"))) { _, _ -> throw failure }
            assertEquals(ClipOutcome.Refused(ClipRefusal.UNREADABLE), ClipStore(context.filesDir, clips, clips).add(book, source))
        }
        assertEquals(emptyList<String>(), left())
    }

    @Test
    fun aConverterOutOfMemoryIsAnUnreadableClip() = runBlocking {
        val store = ClipStore(context.filesDir, { _, _ -> throw OutOfMemoryError("hostile") }, { 2.seconds })
        assertEquals(ClipOutcome.Refused(ClipRefusal.UNREADABLE), store.add(book, source))
    }

    @Test
    fun noAnimationIsUnreadable() = runBlocking {
        val clips = AnimationClips(Images(null)) { _, _ -> error("not reached") }
        assertEquals(ClipOutcome.Refused(ClipRefusal.UNREADABLE), ClipStore(context.filesDir, clips, clips).add(book, source))
    }

    @Test
    fun theEncoderIsToldTheAnimationsOwnFrameRate() {
        assertEquals(6, AnimationEncoder.frameRate(fixture("animated.gif"))) // 10 frames in 1.7 s
        assertEquals(7, AnimationEncoder.frameRate(fixture("animated.webp"))) // 10 frames in 1.4 s
        assertEquals(1, AnimationEncoder.frameRate(fixture("long.gif"))) // 2 frames in 16 s
    }
}
