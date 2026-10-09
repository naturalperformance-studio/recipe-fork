package app.recipefork.app

import android.content.Context
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.data.AndroidConnectivity
import app.recipefork.app.data.AppDatabase
import app.recipefork.app.data.DragHandleSide
import app.recipefork.app.data.LastBookView
import app.recipefork.app.data.PreferencesLastBookView
import app.recipefork.app.data.Network
import app.recipefork.app.data.PreferencesDragHandleSide
import app.recipefork.app.data.PreferencesThemeSetting
import app.recipefork.app.data.ThemeSetting
import app.recipefork.app.dictionary.PreferencesSeededVersion
import app.recipefork.app.data.PreferencesUsualServings
import app.recipefork.app.data.UsualServings
import app.recipefork.app.dictionary.DictionaryCatalog
import app.recipefork.app.dictionary.DictionaryRepository
import app.recipefork.app.dictionary.DictionarySeeding
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.DictionarySync
import app.recipefork.app.media.AnimatedImages
import app.recipefork.app.media.AnimationClips
import app.recipefork.app.media.ClipConverter
import app.recipefork.app.media.ClipSourceDuration
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.ClipTransformer
import app.recipefork.app.media.MediaSweep
import app.recipefork.app.media.ResolverAnimatedImages
import app.recipefork.app.media.RetrieverClipSourceDuration
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.AndroidRingTimeouts
import app.recipefork.app.timer.AndroidRingingSound
import app.recipefork.app.timer.AndroidTimerNotices
import app.recipefork.app.timer.DeviceTimerAnnouncements
import app.recipefork.app.timer.RunningTimers
import app.recipefork.app.timer.SystemTimerClock
import app.recipefork.app.timer.TimerAnnouncer
import app.recipefork.app.timer.TimerDatabase
import app.recipefork.app.timer.TimerRinging
import app.recipefork.app.timer.applicationIsOpen
import app.recipefork.core.api.DictionariesDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * What a release is built as (FLOW-061): with the product's server, or without it for good. It is fixed by the build
 * and read once, by [MainActivity]; every screen that FLOW-062 changes takes the mode it is given.
 */
enum class Mode { Standard, Serverless }

/** The composition root: every long-lived object of the application, wired by hand, for the [mode] it is built in. */
class AppContainer(
    context: Context,
    val mode: Mode,
    /** How the dictionaries the application ships are read; replaced in a test to count the reads. */
    readSnapshot: (Context) -> DictionariesDto = DictionarySnapshot::read,
    /** How the client of the product's server is made; called in [Mode.Standard] only. */
    httpClientOf: () -> HttpClient = { Network.client(Android.create(), BuildConfig.SERVER_URL) },
) {
    val database: AppDatabase = AppDatabase.open(context)

    /**
     * The held dictionaries as every screen reads them — the editor's tags and ingredients, the card's ingredient
     * names — one for the application, seeded from the shipped snapshot, whose asset is parsed once per installation.
     */
    val dictionaries: DictionaryCatalog = database.dictionaryDao().let { dao ->
        DictionaryCatalog(
            dao,
            DictionarySeeding(
                dao,
                PreferencesSeededVersion(context),
                installed = DictionarySnapshot.installedKey(context),
                checked = PreferencesSeededVersion(context, PreferencesSeededVersion.CHECKED),
            ) { readSnapshot(context) },
        )
    }

    /**
     * The client of the product's server; none at all in [Mode.Serverless], which makes no request to it from the
     * moment it is installed (FLOW-061 (2), decisions/20260928-serverless-mode-is-a-build-flag.md).
     */
    val httpClient: HttpClient? = if (mode == Mode.Standard) httpClientOf() else null
    val connectivity = AndroidConnectivity(context)

    /** The serving count the reader usually cooks for, one for the device and kept across restarts (FLOW-017). */
    val usualServings: UsualServings = PreferencesUsualServings(context)

    /** Which side of the editor's blocks their drag column stands on, one for the device and kept across restarts. */
    val dragHandleSide: DragHandleSide = PreferencesDragHandleSide(context)

    /** The theme the reader chose — light, dark or the device's — one for the device and kept across restarts. */
    val themeSetting: ThemeSetting = PreferencesThemeSetting(context)

    /** The view of the recipe book last shown, one for the device and kept across restarts: the book opens on it. */
    val lastBookView: LastBookView = PreferencesLastBookView(context)

    /** The reader's done marks on cards, kept on the device per book and recipe across restarts (FLOW-068). */
    val doneMarks: DoneMarks = DoneMarks.preferences(context)

    /**
     * Keeps the held dictionaries in step with the server while a connection is available (FLOW-001); none in
     * [Mode.Serverless], where the dictionaries stay the snapshot the application ships.
     */
    val dictionarySync: DictionarySync? =
        httpClient?.let { DictionarySync(connectivity, DictionaryRepository(it, database.dictionaryDao())) }

    /** Starts the container's background work in [scope]; returns what it started, nothing in serverless mode. */
    fun start(scope: CoroutineScope): List<Job> = listOfNotNull(dictionarySync?.start(scope))

    /** The pool is the only recipe book so far (AUTH-026). */
    val activeBook = ActiveBook(BookName.Pool, BookDatabase.open(context, BookName.Pool))
    val recipes = RecipeRepository(activeBook, context.filesDir)

    /** Converts a step's clip on the device (MEDIA-004), one for the application. */
    val clipConverter: ClipConverter = ClipTransformer(context)

    /** Reads how long a clip offered to a step lasts, before it is converted (MEDIA-007). */
    val clipDuration: ClipSourceDuration = RetrieverClipSourceDuration(context)

    /** Where steps' clips are converted and kept, in the books under [context]'s files directory. */
    fun clipStore(context: Context): ClipStore = ClipStore(context.filesDir, clipConverter, clipDuration)

    /** Reads whether an image offered to a step is an animated GIF or WebP, to be attached as a clip (scenario 3). */
    val animatedImages: AnimatedImages = ResolverAnimatedImages(context)

    /**
     * Where steps' animated images are turned into clips and kept beside them, refused as a clip is when they last
     * too long or weigh too much converted (MEDIA-004, MEDIA-006, MEDIA-007).
     */
    fun animationStore(context: Context): ClipStore = AnimationClips(animatedImages).let { ClipStore(context.filesDir, it, it) }

    /** Clears each book's media directory of files no recipe holds, once as the book is opened. */
    val mediaSweep = MediaSweep(context.filesDir)

    /** Starts the sweep of every book opened from now on, the active one first, in [scope]. */
    fun sweepMedia(scope: CoroutineScope): Job = mediaSweep.everyOpened(activeBook.open, scope)

    /** The notifications of timers' ends. */
    val timerNotices = AndroidTimerNotices(context)

    /** Ended timers ringing like an alarm until they are stopped, or for two minutes at most (FLOW-013). */
    val timerRinging = TimerRinging(
        SystemTimerClock,
        AndroidRingingSound(context),
        timerNotices,
        timeouts = AndroidRingTimeouts(context, CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    )

    /** Announces timers as they end, with the application open or closed (FLOW-013), and rings them. */
    val timerAnnouncer = TimerAnnouncer(timerNotices, timerRinging, ::applicationIsOpen)

    /** The device's running timers, of every book; held by the moment each ends (FLOW-016). */
    val timers = RunningTimers(
        TimerDatabase.open(context).timers(),
        SystemTimerClock,
        DeviceTimerAnnouncements(context, timerAnnouncer),
    )

    /** Where timers are started and cancelled: it outlives every screen, so each start and cancel is finished. */
    val timerWork = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
