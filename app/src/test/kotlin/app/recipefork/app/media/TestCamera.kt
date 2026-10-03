package app.recipefork.app.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.robolectric.shadows.ShadowActivity
import java.io.File

/**
 * The phone's own camera application, played by a test: it takes the capture intent the application started, writes
 * a shot through the content address it was handed — as a camera application does — and comes back.
 */
object TestCamera {
    /** Whether the application has started an activity for a result not yet taken. */
    fun launched(activity: ShadowActivity): Boolean = activity.peekNextStartedActivityForResult() != null

    /** Takes the capture intent started for a result off the activity's list; it must be one. */
    fun take(activity: ShadowActivity): Intent {
        val intent = activity.nextStartedActivityForResult.intent
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
        return intent
    }

    fun output(intent: Intent): Uri {
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)!!
    }

    /** The file behind the content address [intent] hands the camera. */
    fun file(context: Context, intent: Intent): File = File(Captures.directory(context), output(intent).lastPathSegment!!)

    /** Writes [shot] through the address [intent] hands the camera, as the camera application would. */
    fun write(context: Context, intent: Intent, shot: ByteArray) {
        context.contentResolver.openOutputStream(output(intent))!!.use { it.write(shot) }
    }

    /** The camera application comes back: with the shot saved ([saved]), or cancelled. */
    fun answer(activity: ShadowActivity, intent: Intent, saved: Boolean) {
        activity.receiveResult(intent, if (saved) Activity.RESULT_OK else Activity.RESULT_CANCELED, null)
    }

    /** What is left in the directory the shots are written into. */
    fun left(context: Context): List<String> = Captures.directory(context).list()?.sorted().orEmpty()
}
