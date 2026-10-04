package app.recipefork.app.data

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The scheme the reader chose (ADR 20261004): the light one, the dark one, or whichever the device's mode is. */
enum class ThemeChoice {
    LIGHT,
    DARK,
    DEVICE,
    ;

    /** True when this choice shows the dark scheme, the device being in its dark mode when [systemDark]. */
    fun dark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        DEVICE -> systemDark
    }

    /** True when this choice shows the dark scheme under [configuration], the one the device's mode gives. */
    fun dark(configuration: Configuration): Boolean =
        dark((configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)

    internal companion object {
        /** The choice a held name stands for; anything else reads as the device's, never fails. */
        fun of(name: Any?): ThemeChoice = entries.firstOrNull { it.name.lowercase() == name } ?: DEVICE
    }
}

/** The theme the reader chose: one for the device, kept across restarts; the device's until one is made. */
interface ThemeSetting {
    val choice: StateFlow<ThemeChoice>

    fun set(choice: ThemeChoice)
}

/** The choice held in memory alone, as long as the object lives. */
class HeldThemeSetting(initial: ThemeChoice = ThemeChoice.DEVICE) : ThemeSetting {
    private val held = MutableStateFlow(initial)
    override val choice: StateFlow<ThemeChoice> = held.asStateFlow()

    override fun set(choice: ThemeChoice) {
        held.value = choice
    }
}

/** The choice kept in the device's small preferences file, beside the other reading settings. */
class PreferencesThemeSetting(context: Context) : ThemeSetting {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val held = MutableStateFlow(ThemeChoice.of(preferences.all[KEY]))
    override val choice: StateFlow<ThemeChoice> = held.asStateFlow()

    override fun set(choice: ThemeChoice) {
        preferences.edit().putString(KEY, choice.name.lowercase()).commit()
        held.value = choice
    }

    internal companion object {
        const val FILE = "reading"
        const val KEY = "theme"
    }
}
