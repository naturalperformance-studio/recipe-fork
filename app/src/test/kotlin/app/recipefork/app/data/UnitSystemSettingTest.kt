package app.recipefork.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.core.recipe.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v0.35 scenario 1: the device's system of units — the region's until chosen, then the choice, kept across restarts. */
@RunWith(AndroidJUnit4::class)
class UnitSystemSettingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(PreferencesUnitSystemSetting.FILE, Context.MODE_PRIVATE)

    @After
    fun clear() {
        preferences.edit().clear().commit()
    }

    @Test
    fun theUnitedStatesLiberiaAndMyanmarStartOnUsAnyOtherRegionOnMetric() {
        listOf("US", "LR", "MM", "us").forEach { assertEquals(it, UnitSystem.US, UnitSystemSetting.ofRegion(it)) }
        listOf("RU", "GB", "DE", "CA", "").forEach { assertEquals(it, UnitSystem.METRIC, UnitSystemSetting.ofRegion(it)) }
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun aDeviceInTheUnitedStatesStartsOnUs() {
        assertEquals("US", UnitSystemSetting.deviceRegion())
        assertEquals(UnitSystem.US, PreferencesUnitSystemSetting(context).system.value)
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun aDeviceInRussiaStartsOnMetric() {
        assertEquals("RU", UnitSystemSetting.deviceRegion())
        assertEquals(UnitSystem.METRIC, PreferencesUnitSystemSetting(context).system.value)
    }

    @Test
    fun aChoiceIsKeptAcrossRestartsWhateverTheRegion() {
        val setting = PreferencesUnitSystemSetting(context) { "US" }
        assertEquals(UnitSystem.US, setting.system.value)
        setting.set(UnitSystem.METRIC)
        assertEquals(UnitSystem.METRIC, setting.system.value)
        assertEquals(UnitSystem.METRIC, PreferencesUnitSystemSetting(context) { "US" }.system.value)
        PreferencesUnitSystemSetting(context) { "DE" }.set(UnitSystem.US)
        assertEquals(UnitSystem.US, PreferencesUnitSystemSetting(context) { "DE" }.system.value)
    }

    @Test
    fun whatTheFileHoldsThatNamesNoSystemReadsAsTheRegions() {
        preferences.edit().putString(PreferencesUnitSystemSetting.KEY, "imperial").commit()
        assertEquals(UnitSystem.US, PreferencesUnitSystemSetting(context) { "US" }.system.value)
        preferences.edit().putInt(PreferencesUnitSystemSetting.KEY, 1).commit()
        assertEquals(UnitSystem.METRIC, PreferencesUnitSystemSetting(context) { "RU" }.system.value)
    }
}
