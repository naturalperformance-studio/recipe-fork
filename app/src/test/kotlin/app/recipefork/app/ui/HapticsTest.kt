package app.recipefork.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.provider.Settings
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
 * The device's feedback each haptic plays. A tick — servings −/+, a dragged row passing to a new place — is a 50 ms
 * one-shot at the default amplitude, a confirmation — a timer button — an 80 ms one (the predefined tick and click were
 * too weak to feel on the owner's Samsung). Both go straight to the device's default
 * vibrator as touch vibrations (touch usage from API 33, sonification audio usage below). The system's touch-feedback
 * switch silences them: from API 33 the system applies it to touch vibrations, below it the app reads it at each play.
 * A device without a vibrator feels nothing.
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

    /**
     * From API 31 the shadow records the segments of the last effect played rather than its effect id. The list is a
     * static of ShadowVibrator, shared by every instance and reset between tests, so a fresh instance reads it.
     */
    // Relies on Robolectric internals: ShadowVibrator's static vibrationEffectSegments and the hidden VibrationEffect.getSegments().
    private class PlayedSegments : ShadowVibrator() {
        val last: List<Any> get() = vibrationEffectSegments.toList()
    }

    /** The segments of a one-shot of [millis] at the default amplitude, as the device's vibrator receives them. */
    private fun oneShotOf(millis: Long): List<Any> {
        val effect = VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
        return (effect.javaClass.getMethod("getSegments").invoke(effect) as List<*>).map { it!! }
    }

    @Test
    @Config(sdk = [36])
    fun fromApi33ATickIsA50MsOneShotAsATouchVibration() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        perform(haptics, Haptic.TICK)
        assertEquals(oneShotOf(50), PlayedSegments().last)
        val attributes = vibrator().vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
    }

    @Test
    @Config(sdk = [36])
    fun fromApi33AConfirmationIsAn80MsOneShotAsATouchVibration() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        perform(haptics, Haptic.TICK)
        perform(haptics, Haptic.CONFIRM)
        assertEquals(oneShotOf(80), PlayedSegments().last)
        val attributes = vibrator().vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
    }

    @Test
    @Config(sdk = [29])
    fun belowApi33ATickIsA50MsOneShotWithSonificationAudioUsage() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        perform(haptics, Haptic.TICK)
        assertEquals(50L, vibrator().milliseconds)
        assertEquals(
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            vibrator().audioAttributesFromLastVibration!!.usage,
        )
    }

    @Test
    @Config(sdk = [29])
    fun belowApi33AConfirmationIsAn80MsOneShotWithSonificationAudioUsage() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        perform(haptics, Haptic.TICK)
        perform(haptics, Haptic.CONFIRM)
        assertEquals(80L, vibrator().milliseconds)
        assertEquals(
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            vibrator().audioAttributesFromLastVibration!!.usage,
        )
    }

    @Test
    @Config(sdk = [31])
    fun onApi31TheDefaultVibratorPlaysTheTickAsTouchFeedbackUnlessTheSwitchIsOff() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        touchFeedback(on = false)
        perform(haptics, Haptic.TICK)
        assertNull(vibrator().vibrationAttributesFromLastVibration)

        touchFeedback(on = true)
        perform(haptics, Haptic.TICK)
        assertEquals(oneShotOf(50), PlayedSegments().last)
        // The sonification audio usage reaches the vibrator service as a touch vibration.
        val attributes = vibrator().vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
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

    /**
     * The activity, but offering no system service at all: no vibrator, and no vibrator manager. It stands for the
     * application too, since the device's feedback asks the application's context.
     */
    private fun withoutServices(): Context = object : ContextWrapper(compose.activity) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): Context = this
    }

    @Test
    @Config(sdk = [36])
    fun fromApi33ADeviceOfferingNoVibratorFeelsNothing() {
        vibrator().setHasVibrator(true)
        compose.runOnIdle {
            VibratorHaptics(withoutServices()).perform(Haptic.TICK)
            VibratorHaptics(withoutServices()).perform(Haptic.CONFIRM)
        }
        assertNull(vibrator().vibrationAttributesFromLastVibration)
        assertNull(vibrator().audioAttributesFromLastVibration)
    }

    @Test
    @Config(sdk = [29])
    fun belowApi33ADeviceOfferingNoVibratorFeelsNothing() {
        vibrator().setHasVibrator(true)
        compose.runOnIdle {
            VibratorHaptics(withoutServices()).perform(Haptic.TICK)
            VibratorHaptics(withoutServices()).perform(Haptic.CONFIRM)
        }
        assertNull(vibrator().audioAttributesFromLastVibration)
    }

    /** A screen's context that records each thing asked of it besides its application's context. */
    private class ScreenContext(activity: Context) : ContextWrapper(activity) {
        val asked = mutableListOf<String>()
        override fun getSystemService(name: String): Any? = super.getSystemService(name).also { asked += "service $name" }
        override fun getContentResolver(): android.content.ContentResolver = super.getContentResolver().also { asked += "content resolver" }
        override fun getBaseContext(): Context = super.getBaseContext().also { asked += "base context" }
    }

    @Test
    @Config(sdk = [29])
    fun theDevicesFeedbackHoldsTheApplicationsContextNotTheScreens() {
        val screen = ScreenContext(compose.activity)
        val haptics = compose.runOnIdle { VibratorHaptics(screen) }
        val application = shadowOf(compose.activity.application.vibrator()!!)
        application.setHasVibrator(true)
        touchFeedback(on = true)
        perform(haptics, Haptic.TICK)
        assertEquals(50L, application.milliseconds)
        assertEquals(emptyList<String>(), screen.asked)
        val held = VibratorHaptics::class.java.declaredFields.filter { Context::class.java.isAssignableFrom(it.type) }
            .map { field -> field.isAccessible = true; field.get(haptics) }
        assertEquals(listOf(compose.activity.applicationContext), held)
    }

    private fun touchFeedback(on: Boolean) = Settings.System.putInt(
        compose.activity.contentResolver,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        if (on) 1 else 0,
    )

    @Test
    @Config(sdk = [29])
    fun belowApi33TouchFeedbackOffPlaysNothingAndOnPlaysTheTick() {
        val haptics = deviceHaptics()
        vibrator().setHasVibrator(true)
        touchFeedback(on = false)
        perform(haptics, Haptic.TICK)
        perform(haptics, Haptic.CONFIRM)
        assertNull(vibrator().audioAttributesFromLastVibration)

        touchFeedback(on = true)
        perform(haptics, Haptic.TICK)
        assertEquals(50L, vibrator().milliseconds)
        assertEquals(
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            vibrator().audioAttributesFromLastVibration!!.usage,
        )
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
