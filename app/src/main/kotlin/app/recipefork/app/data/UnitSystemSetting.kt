package app.recipefork.app.data

import android.content.Context
import android.content.res.Resources
import app.recipefork.core.recipe.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The system of units the reader chose (v0.35): Metric or US, one for the device, kept across restarts. Until one is
 * chosen it is the device region's ([UnitSystemSetting.ofRegion]).
 */
interface UnitSystemSetting {
    val system: StateFlow<UnitSystem>

    fun set(system: UnitSystem)

    companion object {
        /** The regions that weigh and measure in US customary units: the United States, Liberia and Myanmar. */
        private val US_REGIONS = setOf("US", "LR", "MM")

        /** The system a device in [region] (an ISO 3166 country code, possibly empty) starts on: US there, else Metric. */
        fun ofRegion(region: String): UnitSystem = if (region.uppercase() in US_REGIONS) UnitSystem.US else UnitSystem.METRIC

        /** The device's own region, from its first system locale — not the language the application is shown in. */
        fun deviceRegion(): String = Resources.getSystem().configuration.locales[0]?.country.orEmpty()
    }
}

/** The system held in memory alone, as long as the object lives. */
class HeldUnitSystemSetting(initial: UnitSystem = UnitSystem.METRIC) : UnitSystemSetting {
    private val held = MutableStateFlow(initial)
    override val system: StateFlow<UnitSystem> = held.asStateFlow()

    override fun set(system: UnitSystem) {
        held.value = system
    }
}

/**
 * The system kept in the device's small preferences file, beside the other reading settings. Nothing is written until
 * the reader chooses: till then it follows [region], read once as the object is made.
 */
class PreferencesUnitSystemSetting(
    context: Context,
    region: () -> String = UnitSystemSetting::deviceRegion,
) : UnitSystemSetting {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    // Whatever the file holds under the key that names no system reads as not chosen, never fails.
    private val held = MutableStateFlow(
        UnitSystem.entries.firstOrNull { it.name.lowercase() == preferences.all[KEY] } ?: UnitSystemSetting.ofRegion(region()),
    )
    override val system: StateFlow<UnitSystem> = held.asStateFlow()

    override fun set(system: UnitSystem) {
        preferences.edit().putString(KEY, system.name.lowercase()).commit()
        held.value = system
    }

    internal companion object {
        const val FILE = "reading"
        const val KEY = "unit-system"
    }
}
