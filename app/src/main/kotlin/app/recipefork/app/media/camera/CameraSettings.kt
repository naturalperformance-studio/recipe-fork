package app.recipefork.app.media.camera

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** The system's page of this application, where its permissions are changed. */
fun applicationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** Opens [applicationSettings], or the system's settings where a phone has no such page. */
internal fun openApplicationSettings(context: Context) {
    try {
        context.startActivity(applicationSettings(context))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            // A phone with no settings to open: the explanation was all there was to give.
        }
    }
}
