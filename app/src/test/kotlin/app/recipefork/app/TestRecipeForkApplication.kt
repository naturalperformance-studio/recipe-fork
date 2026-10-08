package app.recipefork.app

/**
 * The application every unit test runs in (robolectric.properties): the real one, but it leaves the books' media
 * directories unswept, so that its sweep never deletes a file a test writes with an old modification time, and it
 * starts no background work, so that no test's application reaches for a server while later tests run.
 */
open class TestRecipeForkApplication : RecipeForkApplication() {
    override val sweepsMedia: Boolean = false
    override val startsBackgroundWork: Boolean = false
}

/** The test application built in serverless mode, for a test of the application as that build runs it. */
class ServerlessTestApplication : TestRecipeForkApplication() {
    override val mode: Mode get() = Mode.Serverless
}
