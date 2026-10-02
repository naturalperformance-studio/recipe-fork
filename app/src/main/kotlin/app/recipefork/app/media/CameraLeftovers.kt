package app.recipefork.app.media

import android.content.Context
import java.io.File

/** Where the system camera of earlier versions wrote its pictures; the application's own camera writes nothing. */
internal fun legacyCameraDirectory(context: Context) = File(context.cacheDir, "camera")
