package app.recipefork.app.ui

import android.media.AudioAttributes
import android.os.VibrationAttributes
import android.os.VibrationEffect
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

/**
 * The device's feedback each haptic plays. A tick — servings −/+, a dragged row passing to a new place — is the
 * vibrator's predefined tick, a confirmation — a timer button — its click. Both go straight to the device's default
 * vibrator as touch vibrations (touch usage from API 33, sonification audio usage below), so the system's
 * touch-feedback setting still silences them; a device without a vibrator feels nothing.
 */
@RunWith(AndroidJUnit4::class)
class HapticsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun deviceHaptics(): Haptics {
        var haptics: Haptics? = null
        compose.setContent { haptics = rememberHaptics() }
        compose.waitUntil { haptics != null }
        return haptics!!
    }

    private fun vibrator(): ShadowVibrator = shadowOf(compose.activity.vibrator()!!)

    private fun perform(haptics: Haptics, haptic: Haptic) = compose.runOnIdle { haptics.perform(haptic) }

    /** From API 31 the shadow records the segments of the last effect played rather than its effect id. */
    private class PlayedSegments : ShadowVibrator() {
        val last: List<Any> get() = vibrationEffectSegments.toList()
    }

    /** The segments of the predefined effect [id], as the device's vibrator receives them. */
    private fun segmentsOf(id: Int): List<Any> {
        val effect = VibrationEffect.createPredefined(id)
        return (effect.javaClass.getMethod("getSegments").invoke(effect) as List<*>).map { it!! }
    }

    @Test
    @Config(sdk = [36])
    fun fromApi33ATickIsThePredefinedTickAsATouchVibration() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        perform(haptics, Haptic.TICK)
        assertEquals(segmentsOf(VibrationEffect.EFFECT_TICK), PlayedSegments().last)
        val attributes = vibrator().vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
    }

    @Test
    @Config(sdk = [36])
    fun fromApi33AConfirmationIsThePredefinedClickAsATouchVibration() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        perform(haptics, Haptic.TICK)
        perform(haptics, Haptic.CONFIRM)
        assertEquals(segmentsOf(VibrationEffect.EFFECT_CLICK), PlayedSegments().last)
        val attributes = vibrator().vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
    }

    @Test
    @Config(sdk = [29])
    fun belowApi33ATickIsThePredefinedTickWithSonificationAudioUsage() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        perform(haptics, Haptic.TICK)
        assertEquals(VibrationEffect.EFFECT_TICK, vibrator().effectId)
        assertEquals(
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            vibrator().audioAttributesFromLastVibration!!.usage,
        )
    }

    @Test
    @Config(sdk = [29])
    fun belowApi33AConfirmationIsThePredefinedClickWithSonificationAudioUsage() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        perform(haptics, Haptic.TICK)
        perform(haptics, Haptic.CONFIRM)
        assertEquals(VibrationEffect.EFFECT_CLICK, vibrator().effectId)
        assertEquals(
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            vibrator().audioAttributesFromLastVibration!!.usage,
        )
    }

    @Test
    @Config(sdk = [36])
    fun fromApi33ADeviceWithoutAVibratorFeelsNothing() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(false)
        perform(haptics, Haptic.TICK)
        perform(haptics, Haptic.CONFIRM)
        assertNull(vibrator().vibrationAttributesFromLastVibration)
        assertNull(vibrator().audioAttributesFromLastVibration)
    }

    @Test
    @Config(sdk = [29])
    fun belowApi33ADeviceWithoutAVibratorFeelsNothing() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(false)
        perform(haptics, Haptic.TICK)
        perform(haptics, Haptic.CONFIRM)
        assertNull(vibrator().audioAttributesFromLastVibration)
    }

    @Test
    fun aDeviceOfferingNoVibratorFeelsNothing() {
        VibratorHaptics(null).perform(Haptic.TICK)
        VibratorHaptics(null).perform(Haptic.CONFIRM)
        assertNull(vibrator().vibrationAttributesFromLastVibration)
        assertNull(vibrator().audioAttributesFromLastVibration)
    }

    @Test
    fun theProvidedFeedbackIsGivenInsteadOfTheDevicesOwn() {
        val recording = RecordingHaptics()
        var haptics: Haptics? = null
        compose.setContent {
            CompositionLocalProvider(LocalHaptics provides recording) { haptics = rememberHaptics() }
        }
        compose.waitUntil { haptics != null }
        assertSame(recording, haptics)
    }
}
