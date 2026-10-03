package app.recipefork.app

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.data.LanguageChoice
import app.recipefork.app.timer.TimerChannels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
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
 */
@RunWith(AndroidJUnit4::class)
class InterfaceLanguageOnDeviceTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RecipeForkApplication
    private val channels = application.getSystemService(NotificationManager::class.java)
    private val kept = File(application.dataDir, "shared_prefs/reading.xml")

    @Before
    fun belowAndroid13() {
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        assumeTrue("the device speaks English", Resources.getSystem().configuration.locales[0].language == "en")
        forget()
    }

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

    @Test
    fun theChoiceIsKeptOnTheDeviceAndTakenUpByANewProcess() {
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitTitle(scenario, "My recipe book")
        assertEquals(setOf("Timer ended", "Running timers"), channelNames())

        scenario.onActivity { activity: Activity -> AppLanguage.choose(activity, LanguageChoice.RUSSIAN) }
        // At once: the Activity, the application's own resources and the channels' names.
        awaitTitle(scenario, "Моя книга рецептов")
        assertEquals("Моя книга рецептов", application.getString(R.string.library_title))
        assertEquals(setOf("Таймер закончился", "Идущие таймеры"), channelNames())
        // Kept in the preferences file on disk, which outlives the process.
        assertEquals(true, kept.readText().contains("""<string name="interface-language">ru</string>"""))
        scenario.close()

        // A new process: the file alone remembers the choice.
        asTheSystemGivesIt()
        assertEquals("My recipe book", application.getString(R.string.library_title))
        assertEquals(setOf("Timer ended", "Running timers"), channelNames())
        application.startLanguage()
        assertEquals("Моя книга рецептов", application.getString(R.string.library_title))
        assertEquals(setOf("Таймер закончился", "Идущие таймеры"), channelNames())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitTitle(scenario, "Моя книга рецептов")

        // "As on the device" gives the device's English back, and is kept as no choice at all.
        scenario.onActivity { activity: Activity -> AppLanguage.choose(activity, LanguageChoice.DEVICE) }
        awaitTitle(scenario, "My recipe book")
        assertEquals("My recipe book", application.getString(R.string.library_title))
        assertEquals(setOf("Timer ended", "Running timers"), channelNames())
        assertEquals(false, kept.readText().contains("interface-language"))
        scenario.close()
    }
}
