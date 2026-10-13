package app.recipefork.app

import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.data.LanguageChoice
import org.junit.rules.ExternalResource

/**
 * The application under test shown in English, whatever the device's language — the owner's phone speaks Russian
 * (T-243) — by its own per-app language (L10N-002), chosen before the test opens a screen; the reader's choice before
 * it is given back after. A test reading the interface's words reads them in English; the system's own dialogs stay in
 * the device's language, and a test reading them reads them from the system ([SystemWords]).
 */
class EnglishInterface : ExternalResource() {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private var before: LanguageChoice? = null

    override fun before() {
        before = AppLanguage.chosen(application)
        AppLanguage.keep(application, LanguageChoice.ENGLISH)
    }

    override fun after() {
        before?.let { AppLanguage.keep(application, it) }
    }
}
