package app.recipefork.app.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import app.recipefork.app.timer.TimerChannels
import app.recipefork.core.recipe.RecipeLanguage
import java.util.Locale

/** The interface language the reader chose (L10N-002): one of the two of L10N-001, or the device's. */
enum class LanguageChoice(val language: RecipeLanguage?) {
    ENGLISH(RecipeLanguage.EN),
    RUSSIAN(RecipeLanguage.RU),
    DEVICE(null),
    ;

    /** The locale this choice puts in force, or null where the device's stands. */
    val locale: Locale? get() = language?.let { Locale.forLanguageTag(it.name.lowercase()) }

    internal companion object {
        /** The choice a held language code stands for; one the application does not speak reads as the device's. */
        fun of(code: String?): LanguageChoice = entries.firstOrNull { it.language?.name?.lowercase() == code } ?: DEVICE
    }
}

/**
 * The application's interface language (L10N-002): where the reader's choice is kept and how it is put in force.
 *
 * On Android 13 and later the platform keeps it (the per-app language of LocaleManager), so the system's own per-app
 * language settings show and change the same choice; earlier, the device's small preferences file holds it. Either
 * way the Activity lays it over its configuration as it is created ([overrideFor]), so its Configuration — the one
 * every screen reads its language and its numbers from — is the application's language, not the system's.
 */
object AppLanguage {
    /** The choice in force, the device's while the reader has made none. */
    fun chosen(context: Context): LanguageChoice =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            LanguageChoice.of(if (locales.isEmpty) null else locales[0].language)
        } else {
            LanguageChoice.of(preferences(context).all[LANGUAGE_KEY] as? String)
        }

    /** Keeps [choice] and shows [activity] again in it at once; the same choice again changes nothing. */
    fun choose(activity: Activity, choice: LanguageChoice) {
        if (choice == chosen(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                choice.locale?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            preferences(activity).edit().apply {
                val code = choice.language?.name?.lowercase()
                if (code == null) remove(LANGUAGE_KEY) else putString(LANGUAGE_KEY, code)
            }.commit()
        }
        applyToApplication(activity.application)
        // The timers' channels are named in the language taken now, not at the next start: in system settings too.
        TimerChannels.create(localized(activity.application, choice))
        activity.recreate()
    }

    /**
     * On Android 13 and later, a choice an earlier Android kept in the preferences file — the device was upgraded
     * since — moves to the platform once, unless the platform already holds one, which then stands.
     */
    fun moveIntoPlatform(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val preferences = preferences(context)
        val kept = LanguageChoice.of(preferences.all[LANGUAGE_KEY] as? String ?: return)
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager.applicationLocales.isEmpty) kept.locale?.let { manager.applicationLocales = LocaleList(it) }
        preferences.edit().remove(LANGUAGE_KEY).commit()
    }

    /** [context] as it reads in the language [choice] puts in force, before the platform's resources follow it. */
    fun localized(context: Context, choice: LanguageChoice): Context {
        val locales = choice.locale?.let { LocaleList(it) } ?: deviceLocales(context)
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(locales) })
    }

    private fun deviceLocales(context: Context): LocaleList =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }

    /** The configuration an Activity created over [base] lays over its own, or null while the device's language stands. */
    fun overrideFor(base: Context): Configuration? =
        chosen(base).locale?.let { locale -> Configuration().apply { setLocales(LocaleList(locale)) } }

    /**
     * Before Android 13 the platform knows nothing of the choice, so the application's own resources — those a
     * notification is written from — are set to it here; from 13 on the platform does it itself.
     */
    @Suppress("DEPRECATION")
    fun applyToApplication(application: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val resources = application.resources
        val locales = chosen(application).locale?.let { LocaleList(it) } ?: deviceLocales(application)
        if (resources.configuration.locales == locales) return
        resources.updateConfiguration(Configuration(resources.configuration).apply { setLocales(locales) }, resources.displayMetrics)
    }

    private fun preferences(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val FILE = "reading"
    private const val LANGUAGE_KEY = "interface-language"
}
