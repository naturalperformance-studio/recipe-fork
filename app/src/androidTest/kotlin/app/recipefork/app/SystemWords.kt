package app.recipefork.app

import android.content.res.Resources
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The words of the system's own dialogs — another application not responding, the print dialog — in the device's
 * language, read from the system's resources rather than written out: the owner's phone speaks Russian (T-243), and
 * the application's own language, which a test may choose, is not theirs. Where a platform names a string otherwise,
 * the emulator's English stands.
 */
object SystemWords {
    private val PLACEHOLDER = Regex("%(\\d+)\\$[sd]")

    /** The platform's string [name], or [english] where this platform has none of that name. */
    private fun platform(name: String, english: String): String =
        string(Resources.getSystem(), "android", name) ?: english

    /** The string [name] of the system application [packageName], or [english] where it has none of that name. */
    private fun ofPackage(packageName: String, name: String, english: String): String = try {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.packageManager.getResourcesForApplication(packageName)
        string(resources, packageName, name) ?: english
    } catch (e: Exception) {
        english
    }

    private fun string(resources: Resources, packageName: String, name: String): String? =
        resources.getIdentifier(name, "string", packageName).takeIf { it != 0 }?.let { runCatching { resources.getString(it) }.getOrNull() }

    /** The words of a format between its placeholders, longest first, each trimmed to its letters. */
    private fun words(format: String): String =
        format.split(PLACEHOLDER).map { piece -> piece.trim { !it.isLetter() } }.maxBy { it.length }

    /** What the title of the dialog of an application not responding holds besides the application's name. */
    val notResponding: String by lazy { words(platform("anr_application_process", "%1\$s isn't responding")) }

    /** That dialog's button that waits. */
    val wait: String by lazy { platform("wait", "Wait") }

    /** The print dialog's count of the pages its preview holds, on its first page: "Page 1 of 3". */
    val pagesLaidOut: Regex by lazy {
        val format = ofPackage(PRINT_SPOOLER, "page_description_template", "Page %1\$d of %2\$d")
        val parts = mutableListOf<String>()
        var at = 0
        PLACEHOLDER.findAll(format).forEach { placeholder ->
            parts += Regex.escape(format.substring(at, placeholder.range.first))
            parts += if (placeholder.groupValues[1] == "1") "1" else "[1-9][0-9]*"
            at = placeholder.range.last + 1
        }
        parts += Regex.escape(format.substring(at))
        Regex(parts.joinToString(""))
    }

    /** The package of the system's print dialog. */
    const val PRINT_SPOOLER = "com.android.printspooler"
}
