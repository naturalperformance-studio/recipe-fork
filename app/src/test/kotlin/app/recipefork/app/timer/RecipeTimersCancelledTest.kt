package app.recipefork.app.timer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * A recipe deleted from its book takes its timers with it: every one held for it in that book, running or ended and
 * ringing, is cancelled — it falls silent, its notice is taken back, and none announces itself afterwards — while the
 * timers of other recipes, and of the same recipe in another book, run on.
 */
@RunWith(AndroidJUnit4::class)
class RecipeTimersCancelledTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestTimerClock()
    private val notices = RecordedNotices()
    private val sound = RecordedSound()
    private val ringing = TimerRinging(clock, sound, notices)
    private val announcer = TimerAnnouncer(notices, ringing) { true }
    private val database = Room.inMemoryDatabaseBuilder(context, TimerDatabase::class.java).build()
    private val store = RunningTimers(database.timers(), clock, object : TimerAnnouncements {
        override suspend fun started(timer: RunningTimer) = announcer.withdraw(timer.key)
        override suspend fun cancelled(key: TimerKey) = announcer.withdraw(key)
    })
    private val soup = RecipeId("soup")
    private val bread = RecipeId("bread")
    private val other = BookName.OfAccount("other")

    @After
    fun close() = database.close()

    @Test
    fun everyTimerOfTheRecipeInItsBookIsCancelledAndNoneRingsOrAnnouncesAfterwards() = runBlocking {
        val boil = TimerKey(BookName.Pool, soup, 0)
        val simmer = TimerKey(BookName.Pool, soup, 3)
        val rise = TimerKey(BookName.Pool, bread, 0)
        val elsewhere = TimerKey(other, soup, 0)
        store.start(boil, "Boil", "Soup", 10.minutes)
        store.start(simmer, "Simmer", "Soup", 1.hours)
        store.start(rise, "Rise", "Bread", 20.minutes)
        store.start(elsewhere, "Boil", "Soup", 30.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        assertEquals(setOf(boil), ringing.ringing.value)
        assertTrue(sound.ringing)

        store.cancelRecipe(BookName.Pool, soup)

        assertEquals(emptySet<TimerKey>(), ringing.ringing.value)
        assertFalse(sound.ringing)
        assertTrue(notices.withdrawn.containsAll(listOf(boil, simmer)))
        assertEquals(listOf(rise, elsewhere), store.all.first().map { it.key })

        clock.advance(2.hours)
        announcer.announceEnded(store)
        assertEquals(listOf(boil, rise, elsewhere), notices.announced.map { it.key })
    }

    // DOM-024: every timer of each step goes, the ringing one and the running one of the same step alike.
    @Test
    fun everyTimerOfEachStepIsCancelled() = runBlocking {
        val first = TimerKey(BookName.Pool, soup, 1, 0)
        val second = TimerKey(BookName.Pool, soup, 1, 1)
        val rise = TimerKey(BookName.Pool, bread, 1, 1)
        store.start(first, "Boil", "Soup", 10.minutes)
        store.start(second, "Boil", "Soup", 1.hours)
        store.start(rise, "Rise", "Bread", 2.hours)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        assertEquals(setOf(first), ringing.ringing.value)

        store.cancelRecipe(BookName.Pool, soup)

        assertEquals(emptySet<TimerKey>(), ringing.ringing.value)
        assertTrue(notices.withdrawn.containsAll(listOf(first, second)))
        assertEquals(listOf(rise), store.all.first().map { it.key })
        clock.advance(1.hours)
        announcer.announceEnded(store)
        assertEquals(listOf(first), notices.announced.map { it.key })
    }

    @Test
    fun aRecipeWithNoTimersCancelsNothing() = runBlocking {
        val rise = store.start(TimerKey(BookName.Pool, bread, 0), "Rise", "Bread", 20.minutes)
        store.cancelRecipe(BookName.Pool, soup)
        assertEquals(listOf(rise), store.all.first())
        assertEquals(emptyList<TimerKey>(), notices.withdrawn.filter { it.recipeId == soup.value })
    }
}
