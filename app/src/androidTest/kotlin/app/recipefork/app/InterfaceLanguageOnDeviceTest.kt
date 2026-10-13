package app.recipefork.app

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.data.LanguageChoice
import app.recipefork.app.timer.TimerChannels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * L10N-002 below Android 13, where the platform knows nothing of the application's language: the choice is kept in the
 * device's preferences file, shown at once, the application's own resources and the timers' channels follow it, and a
 * new process takes it up again — the Activity, the application's resources and the channels' names.
 *
 * The test runs in the application's own process, so the process itself cannot be ended. What a new process starts
 * from is laid out instead: nothing of the choice in memory but the file on disk, the application's resources as the
 * system gives them and the channels named in the device's language; then the application's own start does its part.
 *
 * Whatever the device speaks — English on the emulator, Russian on the owner's phone (T-243) — the language chosen is
 * the other one of the two, and "As on the device" gives the device's back. From Android 13 the platform keeps the
 * choice, and the test is left out there, not skipped.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = Build.VERSION_CODES.S_V2)
class InterfaceLanguageOnDeviceTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RecipeForkApplication
    private val channels = application.getSystemService(NotificationManager::class.java)
    private val kept = File(application.dataDir, "shared_prefs/reading.xml")

    /** The interface's words the test reads, in one of the application's two languages. */
    private class Words(val code: String, val choice: LanguageChoice, val title: String, val channels: Set<String>)

    private val english = Words("en", LanguageChoice.ENGLISH, "My recipe book", setOf("Timer ended", "Running timers"))
    private val russian = Words("ru", LanguageChoice.RUSSIAN, "Моя книга рецептов", setOf("Таймер закончился", "Идущие таймеры"))

    /** The device's own language as the application speaks it — any but Russian reads as English — and the other. */
    private val device = if (Resources.getSystem().configuration.locales[0].language == "ru") russian else english
    private val other = if (device === russian) english else russian

    @Before
    fun setUp() = forget()

    @After
    fun forget() {
        application.getSharedPreferences("reading", Context.MODE_PRIVATE).edit().remove("interface-language").commit()
        asTheSystemGivesIt()
        application.startLanguage()
    }

    /** The application's resources as a new process gets them, and the channels named in the device's language. */
    @Suppress("DEPRECATION")
    private fun asTheSystemGivesIt() {
        val resources = application.resources
        val system = Configuration(resources.configuration).apply { setLocales(Resources.getSystem().configuration.locales) }
        resources.updateConfiguration(system, resources.displayMetrics)
        TimerChannels.create(application)
    }

    private fun channelNames(): Set<String> =
        setOf(TimerChannels.ENDED, TimerChannels.RUNNING).map { channels.getNotificationChannel(it).name.toString() }.toSet()

    private fun titleOf(scenario: ActivityScenario<MainActivity>): String {
        var title = ""
        scenario.onActivity { title = it.getString(R.string.library_title) }
        return title
    }

    private fun awaitTitle(scenario: ActivityScenario<MainActivity>, title: String) {
        val deadline = System.currentTimeMillis() + 30_000
        while (titleOf(scenario) != title) {
            check(System.currentTimeMillis() < deadline) { "the Activity reads ${titleOf(scenario)}" }
            Thread.sleep(200)
        }
    }

    private fun assertSpeaks(words: Words) {
        assertEquals(words.title, application.getString(R.string.library_title))
        assertEquals(words.channels, channelNames())
    }

    @Test
    fun theChoiceIsKeptOnTheDeviceAndTakenUpByANewProcess() {
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitTitle(scenario, device.title)
        assertEquals(device.channels, channelNames())

        scenario.onActivity { activity: Activity -> AppLanguage.choose(activity, other.choice) }
        // At once: the Activity, the application's own resources and the channels' names.
        awaitTitle(scenario, other.title)
        assertSpeaks(other)
        // Kept in the preferences file on disk, which outlives the process.
        assertEquals(true, kept.readText().contains("""<string name="interface-language">${other.code}</string>"""))
        scenario.close()

        // A new process: the file alone remembers the choice.
        asTheSystemGivesIt()
        assertSpeaks(device)
        application.startLanguage()
        assertSpeaks(other)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitTitle(scenario, other.title)

        // "As on the device" gives the device's language back, and is kept as no choice at all.
        scenario.onActivity { activity: Activity -> AppLanguage.choose(activity, LanguageChoice.DEVICE) }
        awaitTitle(scenario, device.title)
        assertSpeaks(device)
        assertEquals(false, kept.readText().contains("interface-language"))
        scenario.close()
    }
}
