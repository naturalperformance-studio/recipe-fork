package app.recipefork.app.card

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.RecipeForkApplication
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-259: the card's environment built from the application's container carries the book's collections, so that the
 * card's ⋮ offers "Add to collection" in the app (a card given none offers no such item).
 */
@RunWith(AndroidJUnit4::class)
class CardEnvironmentOfTest {
    @Test
    fun theEnvironmentFromTheAppContainerCarriesTheBooksCollections() {
        val container = (ApplicationProvider.getApplicationContext<Context>() as RecipeForkApplication).container
        val environment = cardEnvironmentOf(container)
        assertNotNull(environment.collections)
        assertSame(container.collections, environment.collections)
    }
}
