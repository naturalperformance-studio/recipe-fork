package app.recipefork.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreSmokeTest {
    @Test
    fun `the test command reaches the core module`() {
        assertEquals("core", Core.MODULE)
    }
}
